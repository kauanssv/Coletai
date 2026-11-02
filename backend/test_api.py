import unittest
from pathlib import Path
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session
from sqlalchemy import select
from sqlalchemy.schema import CreateTable
from sqlalchemy.dialects import mysql
from api import create_app, create_user
from models import User, Base

class ApiTests(unittest.TestCase):
    def tearDown(self):
        self.client.close()
        self.app.state.engine.dispose()
    def setUp(self):
        self.app = create_app("sqlite://", test_mode=True)
        self.client = TestClient(self.app)
        with Session(self.app.state.engine) as db, db.begin():
            for role, email in (("admin", "admin@test.com"), ("cliente", "c1@test.com"), ("cliente", "c2@test.com"), ("ambulante", "r1@test.com"), ("ambulante", "r2@test.com")):
                create_user(db, dict(name=email, email=email, password="senha-segura", role=role), administrator=role == "admin")
        self.tokens = {}
        for email in ("admin@test.com", "c1@test.com", "c2@test.com", "r1@test.com", "r2@test.com"):
            result = self.client.post("/v1/login", json=dict(email=email, password="senha-segura"))
            self.assertEqual(200, result.status_code, result.text)
            self.tokens[email] = result.json()["token"]
        self.address = self.perform("c1@test.com", "address.save", dict(nickname="Casa", street="Rua Um", number="10", district="Centro", city="São Paulo", state="SP", zip="01001000"))["addresses"][0]
        for collector in ("r1@test.com", "r2@test.com"):
            self.perform(collector, "accepted.save", dict(ids=["1", "2"]))

    def headers(self, email): return {"Authorization": "Bearer " + self.tokens[email]}
    def state(self, email):
        response = self.client.get("/v1/state", headers=self.headers(email)); self.assertEqual(200, response.status_code, response.text); return response.json()["state"]
    def perform(self, email, action, payload, status=200):
        response = self.client.post("/v1/action", headers=self.headers(email), json=dict(action=action, payload=payload))
        self.assertEqual(status, response.status_code, response.text)
        return response.json().get("state")
    def order(self):
        return self.perform("c1@test.com", "request.save", dict(address_id=self.address["id"], desired_date="2099-01-02", description="Papel e papelão", items=[dict(material_id="1", weight="2.50"), dict(material_id="2", weight="1.00")]))["requests"][0]
    def transition(self, email, request, action): return self.perform(email, "request.transition", dict(id=request["id"], revision=request["revision"], action=action))["requests"][0]

    def test_localizacao_atual_sem_cadastro_ou_geocodificacao(self):
        payload = dict(current_location=dict(latitude=-23.55, longitude=-46.63), desired_date="2099-01-02",
                       description="Pedido pelo GPS", items=[dict(material_id="1", weight="2.50")])
        state = self.perform("c1@test.com", "request.save", payload)
        req = state["requests"][0]
        self.assertEqual(-23.55, req["address"]["latitude"])
        self.assertEqual(-46.63, req["address"]["longitude"])
        self.assertEqual("device_location", req["address"]["source"])
        self.assertEqual(1, len([a for a in state["addresses"] if not a["deleted"]]))
        edit = dict(payload, id=req["id"], revision=req["revision"], description="Atualizado")
        req = self.perform("c1@test.com", "request.save", edit)["requests"][0]
        self.assertEqual("Atualizado", req["description"])
        collector = self.state("r1@test.com")["requests"][0]
        self.assertNotIn("latitude", collector["address"])
        self.assertNotIn("longitude", collector["address"])

    def test_localizacao_atual_rejeita_coordenadas_invalidas(self):
        for location in ({}, dict(latitude=91, longitude=0), dict(latitude=0, longitude=181), dict(latitude=True, longitude=0)):
            self.perform("c1@test.com", "request.save", dict(current_location=location, desired_date="2099-01-02",
                         items=[dict(material_id="1", weight="2.50")]), 400)
        self.assertEqual([], self.state("c1@test.com")["requests"])

    def test_proposta_aceite_coleta_descarte_avaliacao_e_notificacoes(self):
        req = self.order(); req = self.transition("r1@test.com", req, "accept")
        self.assertEqual("negociando", req["status"])
        self.assertNotIn("street", req["address"])
        edit = dict(req); edit["description"] = "Não permitido"
        self.perform("c1@test.com", "request.save", edit, 403)
        req = self.transition("r1@test.com", req, "release"); self.assertEqual("aberta", req["status"])
        req = self.transition("r2@test.com", req, "accept")
        req = self.perform("r2@test.com", "request.transition", dict(id=req["id"], revision=req["revision"], action="propose", price="45.50", message="Chego em 20 minutos"))["requests"][0]
        self.assertNotIn("street", req["address"])
        self.perform("r2@test.com", "request.transition", dict(id=req["id"], revision=req["revision"], action="going"), 403)
        self.perform("r2@test.com", "request.transition", dict(id=req["id"], revision=req["revision"], action="approve"), 403)
        req = self.transition("c1@test.com", req, "approve")
        self.assertEqual("Rua Um", self.state("r2@test.com")["requests"][0]["address"]["street"])
        for action in ("going", "arrive", "collect", "complete"): req = self.transition("r2@test.com", req, action)
        self.assertEqual("concluida", req["status"])
        self.perform("c1@test.com", "request.review", dict(id=req["id"], score=5, comment="Ótimo"))
        self.perform("c1@test.com", "request.review", dict(id=req["id"], score=4), 403)
        self.assertEqual(5, self.state("c1@test.com")["requests"][0]["review"]["score"])
        self.assertGreaterEqual(len(self.state("c1@test.com")["notifications"]), 5)
        self.assertEqual([], self.state("c2@test.com")["requests"])

    def test_endereco_preserva_snapshot_e_recusa_nao_cancela(self):
        req = self.order(); changed = dict(self.address); changed["street"] = "Rua alterada"
        self.perform("c1@test.com", "address.save", changed)
        self.assertEqual("Rua Um", self.state("c1@test.com")["requests"][0]["address"]["street"])
        req = self.transition("r1@test.com", req, "reject")
        self.assertEqual("aberta", req["status"])
        self.assertEqual(1, len(self.state("r2@test.com")["requests"]))
        self.perform("c2@test.com", "address.delete", dict(id=self.address["id"]), 403)

    def test_autorizacao_exclusao_logica_e_sessao(self):
        self.assertEqual(400, self.client.post("/v1/register", json=dict(role="admin", name="Invasor", email="x@test.com", password="senha-segura")).status_code)
        req = self.order(); req = self.transition("c1@test.com", req, "cancel")
        self.perform("admin@test.com", "request.deleted", dict(id=req["id"], deleted=True))
        self.assertEqual([], self.state("c1@test.com")["requests"])
        self.assertEqual(req["description"], self.state("admin@test.com")["requests"][0]["description"])
        self.perform("admin@test.com", "request.deleted", dict(id=req["id"], deleted=False))
        self.assertEqual(1, len(self.state("c1@test.com")["requests"]))
        profile = self.state("c2@test.com")["profiles"][0]
        self.perform("c1@test.com", "profile.save", profile, 403)
        self.perform("admin@test.com", "profile.status", dict(id=profile["id"], status="blocked"))
        self.assertEqual(401, self.client.get("/v1/state", headers=self.headers("c2@test.com")).status_code)
        self.client.post("/v1/logout", headers=self.headers("c1@test.com"))
        self.assertEqual(401, self.client.get("/v1/state", headers=self.headers("c1@test.com")).status_code)

    def test_revisoes_e_pesos_sao_validados(self):
        req = self.order(); self.transition("r1@test.com", req, "accept")
        response = self.client.post("/v1/action", headers=self.headers("r2@test.com"), json=dict(action="request.transition", payload=dict(id=req["id"], revision=req["revision"], action="accept")))
        self.assertIn(response.status_code, (403,409))
        for weight in ("0", "-1", "NaN", "1.001"):
            self.perform("c1@test.com", "request.save", dict(address_id=self.address["id"], desired_date="2099-01-02", items=[dict(material_id="1", weight=weight)]), 400)

    def test_recusa_de_proposta_preserva_cronometro_e_permite_nova_oferta(self):
        req = self.order(); created = req["created_at"]
        req = self.transition("r1@test.com", req, "accept")
        req = self.perform("r1@test.com", "request.transition", dict(id=req["id"], revision=req["revision"], action="propose", price="40.00", message="Primeira oferta"))["requests"][0]
        req = self.transition("c1@test.com", req, "decline")
        self.assertEqual("aberta", req["status"])
        self.assertEqual(created, req["created_at"])
        self.assertEqual("", req["proposal_price"])
        req = self.transition("r1@test.com", req, "accept")
        req = self.perform("r1@test.com", "request.transition", dict(id=req["id"], revision=req["revision"], action="propose", price="35.00", message="Nova oferta"))["requests"][0]
        self.assertEqual("35.00", req["proposal_price"])

    def test_cancelamento_impede_envio_de_proposta_com_tela_antiga(self):
        req = self.transition("r1@test.com", self.order(), "accept")
        self.transition("c1@test.com", req, "cancel")
        self.perform("r1@test.com", "request.transition", dict(id=req["id"], revision=req["revision"], action="propose", price="40", message=""), 409)

    def test_mysql_schema_compila_com_proposta_e_etapas(self):
        ddl = "\n".join(str(CreateTable(t).compile(dialect=mysql.dialect())) for t in Base.metadata.sorted_tables)
        self.assertIn("endereco_solicitacao", ddl)
        self.assertIn("responsavel_tipo_material", ddl)
        self.assertIn("proposal_price", ddl)
        self.assertIn("proposta", ddl)
        self.assertIn("descartando", ddl)

    def completed(self):
        req = self.transition("r1@test.com", self.order(), "accept")
        req = self.perform("r1@test.com", "request.transition", dict(id=req["id"], revision=req["revision"], action="propose", price="20", message=""))["requests"][0]
        req = self.transition("c1@test.com", req, "approve")
        for step in ("going", "arrive", "collect", "complete"): req = self.transition("r1@test.com", req, step)
        return req

    def test_media_no_perfil_e_uma_avaliacao_por_coleta(self):
        profile = self.state("r1@test.com")["profiles"][0]
        self.assertEqual(0, profile["rating_count"])
        self.assertEqual(0, profile["rating_average"])
        first = self.completed()
        self.perform("c1@test.com", "request.review", dict(id=first["id"], score=5, comment="Ótima coleta"))
        self.perform("c1@test.com", "request.review", dict(id=first["id"], score=1), 403)
        second = self.completed()
        self.perform("c1@test.com", "request.review", dict(id=second["id"], score=3))
        profile = self.state("r1@test.com")["profiles"][0]
        self.assertEqual(2, profile["rating_count"])
        self.assertEqual(4.0, profile["rating_average"])
        self.assertEqual(0, self.state("r2@test.com")["profiles"][0]["rating_count"])

    def test_avaliacao_exige_entrega_concluida_cliente_correto_e_nota_inteira(self):
        opened = self.order()
        self.perform("c1@test.com", "request.review", dict(id=opened["id"], score=5), 403)
        req = self.completed()
        for email in ("c2@test.com", "r1@test.com"):
            self.perform(email, "request.review", dict(id=req["id"], score=5), 403)
        for score in (0, 6, -1, 3.5, True, "5"):
            self.perform("c1@test.com", "request.review", dict(id=req["id"], score=score), 400)
        self.assertEqual(0, self.state("r1@test.com")["profiles"][0]["rating_count"])

    def upload(self, email="c1@test.com", data=None, content_type="image/jpeg", status=201):
        if data is None: data = Path(__file__).with_name("test_photo.jpg").read_bytes()
        response = self.client.post("/v1/photos", headers=dict(self.headers(email), **{"Content-Type": content_type}), content=data)
        self.assertEqual(status, response.status_code, response.text)
        return response.json() if status == 201 else None

    def test_fotos_upload_pedido_download_edicao_remocao_e_acesso(self):
        photo = self.upload()
        self.assertEqual(403, self.client.get("/v1/photos/" + photo["id"], headers=self.headers("r1@test.com")).status_code)
        payload = dict(address_id=self.address["id"], desired_date="2099-01-02", photos=[photo], items=[dict(material_id="1", weight="2.50")])
        req = self.perform("c1@test.com", "request.save", payload)["requests"][0]
        self.assertEqual([photo], req["photos"])
        result = self.client.get("/v1/photos/" + photo["id"], headers=self.headers("r1@test.com"))
        self.assertEqual(200, result.status_code)
        self.assertEqual("image/jpeg", result.headers["content-type"])
        self.assertTrue(result.content.startswith(b"\xff\xd8"))
        self.assertEqual(403, self.client.get("/v1/photos/" + photo["id"], headers=self.headers("c2@test.com")).status_code)
        self.assertEqual(401, self.client.get("/v1/photos/" + photo["id"]).status_code)
        edit = dict(req); edit.pop("photos")
        req = self.perform("c1@test.com", "request.save", edit)["requests"][0]
        self.assertEqual([photo], req["photos"])
        req = self.perform("c1@test.com", "request.save", dict(req, photos=[]))["requests"][0]
        self.assertEqual([], req["photos"])
        self.assertEqual(403, self.client.get("/v1/photos/" + photo["id"], headers=self.headers("r1@test.com")).status_code)

    def test_fotos_rejeitam_excesso_repeticao_e_imagem_de_outra_conta(self):
        photo = self.upload()
        foreign = self.upload("c2@test.com")
        payload = dict(address_id=self.address["id"], desired_date="2099-01-02", items=[dict(material_id="1", weight="2.50")])
        self.perform("c1@test.com", "request.save", dict(payload, photos=[foreign]), 403)
        self.perform("c1@test.com", "request.save", dict(payload, photos=[photo, photo]), 400)
        self.perform("c1@test.com", "request.save", dict(payload, photos=[photo] * 4), 400)
        self.assertEqual([], self.state("c1@test.com")["requests"])
        self.upload("r1@test.com", status=403)

    def test_fotos_validam_formato_tamanho_e_removem_metadados(self):
        self.upload(data=b"not an image", status=400)
        self.upload(content_type="text/html", status=415)
        self.upload(data=b"x" * (256 * 1024 + 1), status=400)
        original = Path(__file__).with_name("test_photo.jpg").read_bytes()
        metadata = b"Exif\0\0PRIVATE_GPS"
        image = original[:2] + b"\xff\xe1" + (len(metadata) + 2).to_bytes(2, "big") + metadata + original[2:]
        photo = self.upload(data=image)
        result = self.client.get("/v1/photos/" + photo["id"], headers=self.headers("c1@test.com"))
        self.assertEqual(200, result.status_code)
        self.assertNotIn(metadata, result.content)
        self.assertEqual(original, result.content)

if __name__ == "__main__": unittest.main()
