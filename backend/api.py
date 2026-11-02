"""API autoritativa HTTPS (terminação TLS no proxy), com transações MySQL."""
import hashlib
import hmac
import math
import os
import re
import secrets
import time
import uuid
from collections import defaultdict
from datetime import date, timedelta
from decimal import Decimal, InvalidOperation
from fastapi import FastAPI, Depends, Header, HTTPException, Request as HttpRequest
from sqlalchemy import create_engine, select, delete, event, func
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool
from models import *
from photos import clean_jpeg

def fail(message, status=400):
    raise HTTPException(status, message)

def record_id(value):
    if type(value) is int and 0 < value <= 9223372036854775807: return value
    if isinstance(value, str) and re.fullmatch(r"[0-9]+", value) and 0 < int(value) <= 9223372036854775807: return int(value)
    fail("Identificador inválido")

def password_hash(password):
    if not isinstance(password, str) or not 8 <= len(password) <= 128:
        fail("Use uma senha de 8 a 128 caracteres")
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 210000)
    return f"pbkdf2-sha256$210000${salt.hex()}${digest.hex()}"

def verify(password, encoded):
    try:
        algorithm, rounds, salt, digest = encoded.split("$")
        return algorithm == "pbkdf2-sha256" and hmac.compare_digest(bytes.fromhex(digest), hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt), int(rounds)))
    except (ValueError, AttributeError):
        return False

def millis(value):
    return int(value.replace(tzinfo=timezone.utc).timestamp() * 1000) if value else 0

def address_json(value):
    result = {key: getattr(value, key) for key in ("nickname", "street", "number", "complement", "district", "city", "state", "zip")}
    result["id"] = str(value.id)
    for key in ("latitude", "longitude"):
        if getattr(value, key) is not None:
            result[key] = float(getattr(value, key))
    if isinstance(value, Address):
        result.update(owner_id=str(value.owner_id), deleted=value.deleted)
    return result

def profile_json(db, user):
    coop = db.get(Cooperative, user.id) if user.role == "empresa" else None
    rating_count, rating_average = db.execute(select(func.count(Review.request_id), func.avg(Review.score)).join(
        Request, Request.id == Review.request_id).where(Request.responsible_id == user.id, Request.status == "concluida")).one()
    return dict(id=str(user.id), name=user.name, email=user.email, phone=user.phone, role=user.role, status=user.status,
                document=coop.cnpj if coop else user.document, company=coop.name if coop else "", legal_name=coop.legal_name if coop else "",
                description=coop.description if coop else "", revision=user.revision, created_at=millis(user.created_at),
                rating_count=rating_count, rating_average=float(rating_average) if rating_average is not None else 0)

def request_json(db, req, viewer=None):
    items = db.scalars(select(Item).where(Item.request_id == req.id)).all()
    responsible = db.get(User, req.responsible_id) if req.responsible_id else None
    review = db.scalar(select(Review).where(Review.request_id == req.id))
    result = dict(id=str(req.id), citizen_id=str(req.citizen_id), citizen_name=db.get(User, req.citizen_id).name,
                  responsible_id=str(req.responsible_id) if req.responsible_id else "", responsible_name=responsible.name if responsible else "",
                  address_id=str(req.address_id), address=address_json(db.get(Snapshot, req.id)), desired_date=req.desired_date.isoformat(),
                  status=req.status, description=req.description, notes=req.notes, revision=req.revision, deleted=req.deleted,
                  created_at=millis(req.created_at), updated_at=millis(req.updated_at), accepted_at=millis(req.accepted_at), completed_at=millis(req.completed_at),
                  rejected_by=req.rejected_by,
                  attendances=[dict(responsible_id=str(a.responsible_id), released_at=millis(a.released_at)) for a in db.scalars(select(Attendance).where(Attendance.request_id == req.id))],
                  items=[dict(material_id=str(i.material_id), name=db.get(Material, i.material_id).name, weight=str(i.weight)) for i in items])
    result["photos"] = [dict(id=photo.photo_id, source="remote") for photo in db.scalars(
        select(RequestPhoto).where(RequestPhoto.request_id == req.id).order_by(RequestPhoto.position))]
    if responsible and responsible.role == "empresa": result["responsible_name"] = db.get(Cooperative, responsible.id).name
    result.update(proposal_price=str(req.proposal_price) if req.proposal_price is not None else "",
                  proposal_message=req.proposal_message, proposed_at=millis(req.proposed_at),
                  going_at=millis(req.going_at), arrived_at=millis(req.arrived_at), collected_at=millis(req.collected_at))
    origin = db.get(Address, req.address_id)
    if origin and origin.deleted and origin.nickname == "__gps_location":
        result["address"].update(source="device_location", nickname="Localização atual")
    if viewer and viewer.role in ("ambulante", "empresa") and (req.responsible_id != viewer.id or req.status not in ("aceita", "a_caminho", "coletando", "descartando", "concluida")):
        result["address"] = {k: result["address"].get(k, "") for k in ("district", "city", "state")}
    if review: result["review"] = dict(score=review.score, comment=review.comment, created_at=millis(review.created_at))
    return result

def admin(user):
    if user.role != "admin": fail("Acesso administrativo necessário", 403)

def audit(db, user, action, entity, target, before, after):
    db.add(Audit(actor_id=user.id, action=action, entity=entity, target=str(target), before=before, after=after))

def accepted(db, user_id):
    return {a.material_id for a in db.scalars(select(AcceptedMaterial).where(AcceptedMaterial.responsible_id == user_id))}

def visible(db, user, req):
    if user.role == "admin": return True
    if req.deleted: return False
    if user.role == "cliente": return req.citizen_id == user.id
    if req.responsible_id == user.id: return True
    if db.scalar(select(Attendance).where(Attendance.request_id == req.id, Attendance.responsible_id == user.id)): return True
    item_ids = {i.material_id for i in db.scalars(select(Item).where(Item.request_id == req.id))}
    return req.status == "aberta" and bool(item_ids) and (not accepted(db, user.id) or item_ids <= accepted(db, user.id))

def state(db, user):
    users = db.scalars(select(User)).all() if user.role == "admin" else [user]
    requests = [request_json(db, req, user) for req in db.scalars(select(Request).order_by(Request.created_at.desc())) if visible(db, user, req)]
    notifications = [dict(id=str(n.id), owner_id=str(n.owner_id), request_id=str(n.request_id), message=n.message, type=n.kind, read=n.read, created_at=millis(n.created_at)) for n in db.scalars(select(Notification).where(Notification.owner_id == user.id))]
    info = db.get(PlatformInfo, 1)
    return dict(profiles=[profile_json(db, u) for u in users], requests=requests,
                addresses=[address_json(a) for a in db.scalars(select(Address).where(Address.owner_id == user.id))],
                materials=[dict(id=str(m.id), name=m.name, description=m.description, active=m.active) for m in db.scalars(select(Material)) if user.role == "admin" or m.active],
                notifications=notifications, accepted_materials=[str(i) for i in accepted(db, user.id)], info=info.content if info else {},
                audit=[dict(id=str(a.id), actor=str(a.actor_id), action=a.action, entity=a.entity, target=a.target, before=a.before, after=a.after, data=millis(a.created_at)) for a in db.scalars(select(Audit))] if user.role == "admin" else [])

def create_user(db, payload, actor=None, administrator=False):
    for field in ("role", "name", "email", "password", "phone", "document", "company", "legal_name", "description"):
        if field in payload and not isinstance(payload[field], str): fail("Dados de cadastro inválidos")
    role = payload.get("role")
    if role not in (("admin",) if administrator else ("cliente", "ambulante", "empresa")): fail("Tipo de cadastro inválido")
    name, email = str(payload.get("name", "")).strip(), str(payload.get("email", "")).strip().lower()
    if not name or len(name) > 100 or len(email)>254 or not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email): fail("Nome ou e-mail inválido")
    if len(payload.get("phone",""))>40 or len(payload.get("company",""))>120: fail("Telefone ou nome da cooperativa excede o limite")
    if len(payload.get("legal_name", "")) > 150: fail("Razão social deve ter até 150 caracteres")
    if db.scalar(select(User).where(User.email == email)): fail("E-mail já cadastrado", 409)
    document = re.sub(r"\D", "", str(payload.get("document", "")))
    if role == "empresa" and (len(document) != 14 or not payload.get("company")): fail("Informe cooperativa e CNPJ com 14 dígitos")
    user = User(name=name, email=email, password_hash=password_hash(payload.get("password")), role=role, phone=payload.get("phone", ""), document=document)
    db.add(user); db.flush()
    if role == "cliente": db.add(Citizen(id=user.id))
    elif role == "admin": db.add(Administrator(id=user.id))
    else:
        db.add(Responsible(id=user.id, kind="cooperativa" if role == "empresa" else "independente")); db.flush()
        if role == "empresa": db.add(Cooperative(id=user.id, name=payload["company"].strip(), cnpj=document, legal_name=payload.get("legal_name", ""), description=payload.get("description", "")))
        else: db.add(Independent(id=user.id))
    db.flush(); audit(db, actor or user, "Conta cadastrada", "profile", user.id, None, profile_json(db, user)); return user

def action(db, user, name, payload):
    if not isinstance(name, str) or not isinstance(payload, dict): fail("Operação ou dados inválidos")
    for field in ("id", "address_id"):
        if field in payload and payload[field] not in ("", None): record_id(payload[field])
    for field in ("name", "email", "phone", "document", "company", "legal_name", "description", "notes", "message", "comment", "password", "current_password", "desired_date", "support_email"):
        if field in payload and not isinstance(payload[field], str): fail("Texto inválido para " + field)
    for field in ("active", "deleted"):
        if field in payload and type(payload[field]) is not bool: fail("Opção inválida")
    if "revision" in payload and type(payload["revision"]) is not int: fail("Revisão inválida")
    if "items" in payload and (not isinstance(payload["items"], list) or any(not isinstance(i, dict) for i in payload["items"])): fail("Lista de materiais inválida")
    if "ids" in payload and not isinstance(payload["ids"], list): fail("Lista de materiais inválida")
    if name == "accepted.save":
        if user.role not in ("ambulante", "empresa"): fail("Apenas responsáveis selecionam materiais", 403)
        ids = {record_id(i) for i in payload.get("ids", [])}
        if any(not db.get(Material, i) or not db.get(Material, i).active for i in ids): fail("Material indisponível")
        db.execute(delete(AcceptedMaterial).where(AcceptedMaterial.responsible_id == user.id))
        for i in ids: db.add(AcceptedMaterial(responsible_id=user.id, material_id=i))
    elif name in ("address.save", "address.delete"):
        if user.role != "cliente": fail("Apenas cidadãos mantêm endereços", 403)
        address = db.get(Address, int(payload["id"])) if payload.get("id") else Address(owner_id=user.id)
        if not address or address.owner_id != user.id: fail("Endereço de outra conta", 403)
        if name == "address.delete": address.deleted = True
        else:
            for field, limit in (("nickname", 50), ("street", 150), ("number", 20), ("district", 100), ("city", 100), ("state", 2), ("zip", 10)):
                value = str(payload.get(field, "")).strip()
                if not isinstance(payload.get(field), str) or not value or len(value) > limit: fail("Preencha o endereço respeitando o limite de cada campo")
                setattr(address, field, value)
            if not re.fullmatch(r"\d{8}", re.sub(r"\D", "", address.zip)): fail("CEP inválido")
            address.zip = re.sub(r"\D", "", address.zip)
            if address.state.upper() not in "AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO".split(): fail("UF inválida")
            address.state = address.state.upper(); address.complement = payload.get("complement", "")
            if not isinstance(address.complement, str) or len(address.complement) > 100: fail("Complemento deve ter até 100 caracteres")
            if ("latitude" in payload) != ("longitude" in payload): fail("Informe latitude e longitude juntas")
            for field, limit in (("latitude", 90), ("longitude", 180)):
                if field in payload:
                    value = float(payload[field])
                    if isinstance(payload[field], bool) or not math.isfinite(value) or not -limit <= value <= limit: fail("Coordenada inválida")
                    setattr(address, field, value)
        db.add(address)
    elif name == "request.save":
        req = db.scalar(select(Request).where(Request.id == int(payload["id"])).with_for_update()) if payload.get("id") else None
        if req and not visible(db, user, req): fail("Solicitação de outra conta", 403)
        if req and user.role != "admin" and (req.citizen_id != user.id or req.status != "aberta"): fail("Somente solicitações abertas podem ser editadas", 403)
        if not req and user.role != "cliente": fail("Apenas cidadãos criam solicitações", 403)
        if req and payload.get("revision") != req.revision: fail("Solicitação alterada. Atualize a lista.", 409)
        if req and req.deleted: fail("Solicitação excluída")
        desired = date.fromisoformat(payload.get("desired_date", ""))
        if not req and desired < date.today(): fail("A data desejada não pode estar no passado")
        address = db.get(Address, int(payload["address_id"])) if payload.get("address_id") else None
        automatic = payload.get("current_location")
        if "current_location" in payload:
            if user.role != "cliente": fail("Apenas cidadãos escolhem a localização atual", 403)
            if not isinstance(automatic, dict): fail("Localização inválida")
            coordinates = {}
            for field, limit in (("latitude", 90), ("longitude", 180)):
                raw = automatic.get(field)
                try: value = float(raw)
                except (ValueError, TypeError, OverflowError): fail("Localização inválida")
                if isinstance(raw, bool) or not math.isfinite(value) or not -limit <= value <= limit: fail("Localização inválida")
                coordinates[field] = value
            # Keep the request's required foreign key without adding a saved-address choice.
            address = Address(owner_id=user.id, nickname="__gps_location", deleted=True, **coordinates)
            for field, limit in (("street", 150), ("number", 20), ("complement", 100), ("district", 100), ("city", 100), ("state", 2), ("zip", 8)):
                value = str(automatic.get(field, "")).strip()
                if field == "zip": value = re.sub(r"\D", "", value)
                if len(value) > limit: fail("Endereço muito longo")
                setattr(address, field, value)
            db.add(address); db.flush()
        elif req and (not payload.get("address_id") or str(req.address_id) == str(payload.get("address_id"))):
            # Editing text must retain the agreed pickup location, even if the
            # address book entry was changed or removed after placing the order.
            address = None
        elif not address or address.owner_id != (req.citizen_id if user.role == "admin" and req else user.id) or address.deleted:
            fail("Selecione um endereço próprio", 403)
        if not req: req = Request(citizen_id=user.id, address_id=address.id, desired_date=desired, status="aberta"); db.add(req); db.flush()
        before = request_json(db, req) if req.revision > 1 or db.get(Snapshot, req.id) else None
        req.desired_date = desired; req.description = payload.get("description", "")[:500]; req.notes = payload.get("notes", "")[:500]
        if address:
            req.address_id = address.id; snapshot = db.get(Snapshot, req.id) or Snapshot(id=req.id)
            for field in ("nickname", "street", "number", "complement", "district", "city", "state", "zip", "latitude", "longitude"): setattr(snapshot, field, getattr(address, field))
            if automatic is not None: snapshot.nickname = "Localização atual"
            db.add(snapshot)
        if not payload.get("items"): fail("Adicione pelo menos um material")
        if "photos" in payload:
            refs = payload["photos"]
            if not isinstance(refs, list) or len(refs) > 3: fail("Adicione até 3 fotos por pedido")
            used_photos = set()
            linked = {p.photo_id for p in db.scalars(select(RequestPhoto).where(RequestPhoto.request_id == req.id))}
            for ref in refs:
                if not isinstance(ref, dict) or ref.get("source") != "remote" or not isinstance(ref.get("id"), str): fail("Foto inválida")
                photo = db.get(UploadPhoto, ref["id"])
                if not photo or (photo.owner_id != user.id and not (user.role == "admin" and photo.id in linked)): fail("Foto não disponível para esta conta", 403)
                if photo.id in used_photos: fail("Foto repetida")
                used_photos.add(photo.id)
            db.execute(delete(RequestPhoto).where(RequestPhoto.request_id == req.id))
            for position, ref in enumerate(refs): db.add(RequestPhoto(request_id=req.id, position=position, photo_id=ref["id"]))
        previous_materials = {i.material_id for i in db.scalars(select(Item).where(Item.request_id == req.id))}
        db.execute(delete(Item).where(Item.request_id == req.id)); used = set()
        for item in payload["items"]:
            material = db.get(Material, record_id(item["material_id"])); weight = Decimal(str(item.get("weight", "")).replace(",", "."))
            if not material or (not material.active and material.id not in previous_materials) or material.id in used or not weight.is_finite() or not 0 < weight <= 1000000 or weight.as_tuple().exponent < -2: fail("Material ou peso inválido")
            used.add(material.id); db.add(Item(request_id=req.id, material_id=material.id, weight=weight))
        req.revision += 1; req.updated_at = now(); db.flush(); audit(db, user, "Solicitação atualizada" if before else "Solicitação criada", "order", req.id, before, request_json(db, req))
    elif name in ("request.transition", "request.review", "request.deleted"):
        req = db.scalar(select(Request).where(Request.id == int(payload["id"])).with_for_update())
        if not req or not visible(db, user, req): fail("Solicitação não disponível", 403)
        before = request_json(db, req)
        if name == "request.deleted":
            admin(user)
            if payload.get("deleted") and req.status not in ("concluida", "cancelada"): fail("Encerre a solicitação antes de excluir")
            req.deleted = bool(payload.get("deleted"))
        elif name == "request.review":
            if user.id != req.citizen_id or req.status != "concluida" or db.scalar(select(Review).where(Review.request_id == req.id)): fail("Avaliação não permitida", 403)
            score = payload.get("score", 0); comment = payload.get("comment", "")
            if type(score) is not int or score not in range(1, 6) or not isinstance(comment, str) or len(comment) > 500: fail("Nota deve ser de 1 a 5 e comentário de até 500 caracteres")
            db.add(Review(request_id=req.id, score=score, comment=comment.strip()))
        else:
            if req.deleted or req.revision != payload.get("revision"): fail("Solicitação alterada. Atualize a lista.", 409)
            transition = payload.get("action"); message = ""
            if transition == "accept":
                ids = {i.material_id for i in db.scalars(select(Item).where(Item.request_id == req.id))}
                if user.role not in ("ambulante", "empresa") or req.status != "aberta" or (accepted(db, user.id) and not ids <= accepted(db, user.id)) or str(user.id) in (req.rejected_by or []): fail("Aceite não permitido", 403)
                req.status = "negociando"; req.responsible_id = user.id
            elif transition == "propose":
                if req.responsible_id != user.id or req.status != "negociando": fail("Aceite o pedido antes de enviar proposta", 403)
                price = str(payload.get("price", "")).replace(",", ".")
                if not re.fullmatch(r"[0-9]+(?:\.[0-9]{1,2})?", price) or not 0 < Decimal(price) <= 1000000: fail("Informe um valor positivo com ate duas casas decimais")
                message_text = payload.get("message", "")
                if not isinstance(message_text, str) or len(message_text) > 500: fail("Mensagem de ate 500 caracteres")
                req.proposal_price = Decimal(price); req.proposal_message = message_text.strip(); req.proposed_at = now(); req.status = "proposta"
                message = f"Nova proposta de {user.name}."
            elif transition == "approve":
                if req.citizen_id != user.id or req.status != "proposta": fail("Apenas o cliente pode aprovar a proposta", 403)
                req.status = "aceita"; req.accepted_at = now(); message = "Proposta aceita. O coletor pode iniciar o deslocamento."
            elif transition == "decline":
                if req.citizen_id != user.id or req.status != "proposta": fail("Recusa de proposta nao permitida", 403)
                db.add(Attendance(request_id=req.id, responsible_id=req.responsible_id))
                req.status = "aberta"; req.responsible_id = None; req.proposal_price = None; req.proposal_message = ""; req.proposed_at = None
                message = "Continuamos procurando outro coletor para seu pedido."
            elif transition == "reject":
                if user.role not in ("ambulante", "empresa") or req.status != "aberta": fail("Recusa não permitida", 403)
                req.rejected_by = list(set((req.rejected_by or []) + [str(user.id)]))
            elif transition == "release":
                if req.responsible_id != user.id or req.status not in ("negociando", "proposta", "aceita", "a_caminho", "coletando", "descartando"): fail("Desistência não permitida", 403)
                db.add(Attendance(request_id=req.id, responsible_id=user.id)); req.status = "aberta"; req.responsible_id = None; req.accepted_at = None; req.going_at = None; req.proposal_price = None; req.proposal_message = ""; req.proposed_at = None; req.arrived_at = None; req.collected_at = None; message = "O atendimento foi cancelado pelo responsável. Sua solicitação está aberta novamente."
            elif transition == "cancel":
                if (req.citizen_id != user.id and user.role != "admin") or req.status in ("concluida", "cancelada"): fail("Cancelamento não permitido", 403)
                if req.responsible_id: db.add(Attendance(request_id=req.id, responsible_id=req.responsible_id))
                req.responsible_id = None; req.status = "cancelada"; message = "Sua solicitação foi cancelada."
            elif transition == "going":
                if req.responsible_id != user.id or req.status != "aceita": fail("Deslocamento não permitido", 403)
                req.status = "a_caminho"; req.going_at = now(); message = "O responsável está a caminho."
            elif transition == "arrive":
                if req.responsible_id != user.id or req.status != "a_caminho": fail("Chegada nao permitida", 403)
                req.status = "coletando"; req.arrived_at = now(); message = "O coletor chegou ao local."
            elif transition == "collect":
                if req.responsible_id != user.id or req.status != "coletando": fail("Coleta nao permitida", 403)
                req.status = "descartando"; req.collected_at = now(); message = "Materiais coletados. Aguardando descarte."
            elif transition == "complete":
                if req.responsible_id != user.id or req.status != "descartando": fail("Conclusão não permitida", 403)
                req.status = "concluida"; req.completed_at = now(); message = "Coleta concluída. Você já pode avaliar."
            else: fail("Transição desconhecida")
            if message: db.add(Notification(owner_id=req.citizen_id, request_id=req.id, message=message,kind={"aceita":"solicitacao_aceita","a_caminho":"responsavel_a_caminho","concluida":"coleta_concluida","cancelada":"solicitacao_cancelada","aberta":"solicitacao_reaberta"}.get(req.status,"atualizacao")))
        req.revision += 1; req.updated_at = now(); db.flush(); audit(db, user, "Solicitação: " + payload.get("action", name), "order", req.id, before, request_json(db, req))
    elif name in ("profile.save", "profile.status", "profile.password"):
        target = db.scalar(select(User).where(User.id == int(payload["id"])).with_for_update()) if payload.get("id") else None
        if payload.get("id") and target is None: fail("Cadastro não encontrado", 404)
        if name == "profile.save" and target is None:
            admin(user); create_user(db, payload, user); return
        if not target or (target.id != user.id and user.role != "admin"): fail("Cadastro de outra conta", 403)
        before = profile_json(db, target)
        if name == "profile.status":
            admin(user)
            if target.role == "admin" or payload.get("status") not in ("active", "blocked", "deleted"): fail("Situação não permitida")
            target.status = payload["status"]
            target.active = target.status == "active"
        elif name == "profile.password":
            if target.id != user.id or not verify(payload.get("current_password", ""), user.password_hash): fail("Senha atual incorreta", 403)
            target.password_hash = password_hash(payload.get("password"))
        else:
            if target.revision != payload.get("revision"): fail("Cadastro alterado. Reabra para editar.", 409)
            if not payload.get("name", "").strip() or len(payload.get("name",""))>100 or len(payload.get("email",""))>254 or not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", payload.get("email", "")): fail("Nome ou e-mail inválido")
            target.name = payload["name"].strip()[:100]; target.email = payload["email"].strip().lower(); target.phone = payload.get("phone", "")[:40]
            if "document" in payload:
                document = re.sub(r"\D", "", payload["document"])
                if len(document) > 30: fail("Documento muito longo")
                target.document = document
            if target.role == "empresa":
                coop = db.get(Cooperative, target.id)
                if not payload.get("company", "").strip(): fail("Informe o nome da cooperativa")
                coop.name = payload["company"][:120]; coop.legal_name = payload.get("legal_name", "")[:150]
                if "description" in payload: coop.description = payload["description"]
                if "document" in payload: coop.cnpj = re.sub(r"\D", "", payload["document"])
                if len(coop.cnpj) != 14: fail("CNPJ deve possuir 14 dígitos")
        target.revision += 1; db.flush(); audit(db, user, "Cadastro atualizado", "profile", target.id, before, profile_json(db, target))
    elif name == "material.save":
        admin(user); material = db.get(Material, int(payload["id"])) if payload.get("id") else Material()
        if not material or not payload.get("name", "").strip(): fail("Informe o nome do material")
        before=dict(id=str(material.id),name=material.name,description=material.description,active=material.active) if material.id else None
        material.name = payload["name"].strip()[:80]; material.description = payload.get("description", "")[:255]; material.active = bool(payload.get("active", True)); db.add(material)
        db.flush();audit(db,user,"Catálogo atualizado","material",material.id,before,dict(id=str(material.id),name=material.name,description=material.description,active=material.active))
    elif name == "notification.read":
        note = db.get(Notification, int(payload["id"]))
        if not note or note.owner_id != user.id: fail("Notificação de outra conta", 403)
        note.read = True
    elif name == "info.save":
        admin(user); info = db.get(PlatformInfo, 1) or PlatformInfo(id=1); before=info.content; info.content = {k: str(payload.get(k, ""))[:500] for k in ("name", "description", "support_email")}; db.add(info)
        audit(db,user,"Informações da plataforma atualizadas","information","platform",before,info.content)
    else: fail("Operação desconhecida", 404)

def create_app(database_url=None, test_mode=False):
    database_url = database_url or os.environ.get("COLETAI_DATABASE_URL", "")
    if not test_mode and not database_url.startswith("mysql+pymysql://"):
        raise RuntimeError("Configure COLETAI_DATABASE_URL=mysql+pymysql://usuario:senha@host/coletai")
    test_options = {"connect_args": {"check_same_thread": False}} if test_mode else {}
    if test_mode and database_url in ("sqlite://", "sqlite:///:memory:"): test_options["poolclass"] = StaticPool
    engine = create_engine(database_url, pool_pre_ping=True, **test_options)
    if test_mode:
        @event.listens_for(engine, "connect")
        def fk(connection, record): connection.execute("PRAGMA foreign_keys=ON")
    Base.metadata.create_all(engine)
    with Session(engine) as db, db.begin():
        if not db.scalar(select(Material)):
            db.add_all(Material(name=name) for name in ("Papel", "Papelão", "Plástico", "Vidro", "Metal", "Óleo de cozinha", "Eletrônicos"))
    app = FastAPI(title="Coletaí API", version="1.4", docs_url="/docs" if test_mode else None)
    app.state.engine = engine
    attempts = defaultdict(list)
    @app.middleware("http")
    async def limits(request: HttpRequest, call_next):
        from starlette.responses import JSONResponse
        if len(await request.body()) > 1024 * 1024:
            return JSONResponse({"detail": "Requisição muito grande"}, status_code=413)
        if request.url.path in ("/v1/login", "/v1/register"):
            key = request.client.host if request.client else "local"
            now_value = time.monotonic()
            attempts[key] = [t for t in attempts[key] if t > now_value - 60]
            if len(attempts[key]) >= (100 if test_mode else 20):
                return JSONResponse({"detail": "Aguarde antes de tentar novamente"}, status_code=429)
            attempts[key].append(now_value)
            if len(attempts) > 10000:
                stale = [k for k,v in attempts.items() if not v or v[-1] < now_value - 60]
                for k in stale: del attempts[k]
        return await call_next(request)
    def database():
        with Session(engine) as db:
            try: yield db; db.commit()
            except IntegrityError: db.rollback(); fail("Registro duplicado ou relacionamento inválido", 409)
            except Exception: db.rollback(); raise
    def current(authorization: str = Header(default=""), db=Depends(database)):
        if not authorization.startswith("Bearer "): fail("Autenticação necessária", 401)
        token = db.get(SessionToken, hashlib.sha256(authorization[7:].encode()).hexdigest())
        user = db.get(User, token.user_id) if token and token.expires_at > now() else None
        if not user or user.status != "active" or not user.active: fail("Sessão expirada ou conta indisponível", 401)
        return user
    @app.post("/v1/register", status_code=201)
    def register(payload: dict, db=Depends(database)):
        user = create_user(db, payload); return {"account_id": str(user.id)}
    @app.post("/v1/login")
    def login(payload: dict, db=Depends(database)):
        user = db.scalar(select(User).where(User.email == str(payload.get("email", "")).strip().lower()))
        if not user or user.status != "active" or not user.active or not verify(payload.get("password", ""), user.password_hash): fail("E-mail ou senha incorretos", 401)
        token = secrets.token_urlsafe(32); db.add(SessionToken(token_hash=hashlib.sha256(token.encode()).hexdigest(), user_id=user.id, expires_at=now()+timedelta(hours=12)))
        return {"token": token, "account_id": str(user.id), "state": state(db, user)}
    @app.get("/v1/state")
    def get_state(user=Depends(current), db=Depends(database)): return {"state": state(db, user)}
    @app.post("/v1/photos", status_code=201)
    async def upload_photo(request: HttpRequest, user=Depends(current), db=Depends(database)):
        if user.role != "cliente": fail("Apenas cidadãos adicionam fotos aos pedidos", 403)
        if request.headers.get("content-type", "").split(";")[0].strip() != "image/jpeg": fail("Envie uma foto JPEG", 415)
        try: image = clean_jpeg(await request.body())
        except ValueError as error: fail(str(error))
        photo = UploadPhoto(id=str(uuid.uuid4()), owner_id=user.id, data=image)
        db.add(photo)
        return dict(id=photo.id, source="remote")
    @app.get("/v1/photos/{photo_id}")
    def download_photo(photo_id: str, user=Depends(current), db=Depends(database)):
        photo = db.get(UploadPhoto, photo_id)
        if not photo: fail("Foto não encontrada", 404)
        allowed = photo.owner_id == user.id or user.role == "admin"
        if not allowed:
            requests = db.scalars(select(Request).join(RequestPhoto, RequestPhoto.request_id == Request.id).where(RequestPhoto.photo_id == photo.id)).all()
            allowed = any(visible(db, user, req) for req in requests)
        if not allowed: fail("Foto não disponível para esta conta", 403)
        from fastapi.responses import Response
        return Response(content=photo.data, media_type="image/jpeg", headers={"Cache-Control": "private, no-store", "X-Content-Type-Options": "nosniff"})
    @app.post("/v1/action")
    def perform(payload: dict, user=Depends(current), db=Depends(database)):
        try: action(db, user, payload.get("action"), payload.get("payload", {})); db.flush()
        except (ValueError, TypeError, KeyError, InvalidOperation): fail("Dados inválidos para esta operação")
        return {"state": state(db, user)}
    @app.post("/v1/logout")
    def logout(user=Depends(current), db=Depends(database), authorization: str = Header(default="")):
        db.execute(delete(SessionToken).where(SessionToken.token_hash == hashlib.sha256(authorization[7:].encode()).hexdigest())); return {"ok": True}
    return app

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(); parser.add_argument("--create-admin", action="store_true"); args = parser.parse_args()
    app = create_app()
    if args.create_admin:
        import getpass
        with Session(app.state.engine) as db, db.begin():
            create_user(db, dict(role="admin", name=input("Nome: "), email=input("E-mail: "), password=getpass.getpass("Senha: ")), administrator=True)
