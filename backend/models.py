"""Modelo relacional da documentação 1.4; MySQL em execução, SQLite apenas nos testes."""
from datetime import datetime, timezone
from decimal import Decimal
from sqlalchemy import Boolean, CheckConstraint, Date, DateTime, ForeignKey, ForeignKeyConstraint, Integer, JSON, LargeBinary, Numeric, String, Text, UniqueConstraint
from sqlalchemy.dialects.mysql import MEDIUMBLOB
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

def now():
    return datetime.now(timezone.utc).replace(tzinfo=None)

class Base(DeclarativeBase):
    pass

class User(Base):
    __tablename__ = "usuario"
    id: Mapped[int] = mapped_column("pk_id_usuario", primary_key=True)
    name: Mapped[str] = mapped_column("nome", String(100))
    email: Mapped[str] = mapped_column(String(254), unique=True)
    password_hash: Mapped[str] = mapped_column("senha_hash", String(255))
    role: Mapped[str] = mapped_column(String(20))
    status: Mapped[str] = mapped_column("status_acesso", String(20), default="active")
    active: Mapped[bool] = mapped_column("ativo", Boolean, default=True)
    phone: Mapped[str] = mapped_column("telefone", String(40), default="")
    document: Mapped[str] = mapped_column("documento", String(30), default="")
    created_at: Mapped[datetime] = mapped_column("data_cadastro", DateTime, default=now)
    revision: Mapped[int] = mapped_column(default=1)
    profile_photo: Mapped[dict | None] = mapped_column(JSON, nullable=True)

class Citizen(Base):
    __tablename__ = "cidadao"
    id: Mapped[int] = mapped_column("pk_fk_id_usuario", ForeignKey("usuario.pk_id_usuario"), primary_key=True)

class Responsible(Base):
    __tablename__ = "responsavel_coleta"
    id: Mapped[int] = mapped_column("pk_fk_id_usuario", ForeignKey("usuario.pk_id_usuario"), primary_key=True)
    kind: Mapped[str] = mapped_column("tipo", String(20))

class Independent(Base):
    __tablename__ = "coletor_independente"
    id: Mapped[int] = mapped_column("pk_fk_id_usuario", ForeignKey("responsavel_coleta.pk_fk_id_usuario"), primary_key=True)
    photo_url: Mapped[str | None] = mapped_column("foto_url", String(500))

class Cooperative(Base):
    __tablename__ = "cooperativa"
    id: Mapped[int] = mapped_column("pk_fk_id_usuario", ForeignKey("responsavel_coleta.pk_fk_id_usuario"), primary_key=True)
    name: Mapped[str] = mapped_column("nome_cooperativa", String(120))
    cnpj: Mapped[str] = mapped_column(String(14), unique=True)
    legal_name: Mapped[str] = mapped_column("razao_social", String(150), default="")
    description: Mapped[str] = mapped_column("descricao", Text, default="")
    logo_url: Mapped[str | None] = mapped_column(String(500))

class Administrator(Base):
    __tablename__ = "administrador"
    id: Mapped[int] = mapped_column("pk_fk_id_usuario", ForeignKey("usuario.pk_id_usuario"), primary_key=True)

class Address(Base):
    __tablename__ = "endereco"
    id: Mapped[int] = mapped_column("pk_id_endereco", primary_key=True)
    owner_id: Mapped[int] = mapped_column("fk_id_cidadao", ForeignKey("cidadao.pk_fk_id_usuario"), index=True)
    nickname: Mapped[str] = mapped_column("apelido", String(50))
    street: Mapped[str] = mapped_column("logradouro", String(150))
    number: Mapped[str] = mapped_column("numero", String(20))
    complement: Mapped[str] = mapped_column("complemento", String(100), default="")
    district: Mapped[str] = mapped_column("bairro", String(100))
    city: Mapped[str] = mapped_column("cidade", String(100))
    state: Mapped[str] = mapped_column("estado", String(2))
    zip: Mapped[str] = mapped_column("cep", String(8))
    latitude: Mapped[float | None] = mapped_column(Numeric(10,7))
    longitude: Mapped[float | None] = mapped_column(Numeric(10,7))
    deleted: Mapped[bool] = mapped_column("excluido", Boolean, default=False)
    __table_args__ = (UniqueConstraint("fk_id_cidadao","pk_id_endereco"),CheckConstraint("latitude is null or latitude between -90 and 90"), CheckConstraint("longitude is null or longitude between -180 and 180"))

class Material(Base):
    __tablename__ = "tipo_material"
    id: Mapped[int] = mapped_column("pk_id_material", primary_key=True)
    name: Mapped[str] = mapped_column("nome", String(80), unique=True)
    description: Mapped[str] = mapped_column("descricao", String(255), default="")
    active: Mapped[bool] = mapped_column("ativo", Boolean, default=True)

class AcceptedMaterial(Base):
    __tablename__ = "responsavel_tipo_material"
    responsible_id: Mapped[int] = mapped_column("pk_fk_id_responsavel", ForeignKey("responsavel_coleta.pk_fk_id_usuario"), primary_key=True)
    material_id: Mapped[int] = mapped_column("pk_fk_id_material", ForeignKey("tipo_material.pk_id_material"), primary_key=True)

class Request(Base):
    __tablename__ = "solicitacao_coleta"
    id: Mapped[int] = mapped_column("pk_id_solicitacao", primary_key=True)
    citizen_id: Mapped[int] = mapped_column("fk_id_cidadao", ForeignKey("cidadao.pk_fk_id_usuario"), index=True)
    address_id: Mapped[int] = mapped_column("fk_id_endereco", ForeignKey("endereco.pk_id_endereco"))
    responsible_id: Mapped[int | None] = mapped_column("fk_id_responsavel_aceito", ForeignKey("responsavel_coleta.pk_fk_id_usuario"), index=True)
    desired_date: Mapped[datetime] = mapped_column("data_desejada", Date)
    status: Mapped[str] = mapped_column(String(20), default="aberta", index=True)
    description: Mapped[str] = mapped_column("descricao", Text, default="")
    notes: Mapped[str] = mapped_column("observacoes", Text, default="")
    created_at: Mapped[datetime] = mapped_column("data_criacao", DateTime, default=now)
    updated_at: Mapped[datetime] = mapped_column("data_atualizacao", DateTime, default=now)
    accepted_at: Mapped[datetime | None] = mapped_column("data_aceite", DateTime)
    going_at: Mapped[datetime | None] = mapped_column("data_deslocamento", DateTime)
    completed_at: Mapped[datetime | None] = mapped_column("data_conclusao", DateTime)
    proposal_price: Mapped[Decimal | None] = mapped_column(Numeric(10, 2))
    proposal_message: Mapped[str] = mapped_column(Text, default="")
    proposed_at: Mapped[datetime | None] = mapped_column(DateTime)
    arrived_at: Mapped[datetime | None] = mapped_column(DateTime)
    collected_at: Mapped[datetime | None] = mapped_column(DateTime)
    revision: Mapped[int] = mapped_column(default=1)
    deleted: Mapped[bool] = mapped_column("excluido", Boolean, default=False)
    rejected_by: Mapped[list] = mapped_column(JSON, default=list)
    __table_args__ = (ForeignKeyConstraint(["fk_id_cidadao","fk_id_endereco"],["endereco.fk_id_cidadao","endereco.pk_id_endereco"]),CheckConstraint("(status in ('negociando','proposta','aceita','a_caminho','coletando','descartando','concluida') and fk_id_responsavel_aceito is not null) or (status in ('aberta','editando','cancelada') and fk_id_responsavel_aceito is null)"),)

class Snapshot(Base):
    __tablename__ = "endereco_solicitacao"
    id: Mapped[int] = mapped_column("pk_fk_id_solicitacao", ForeignKey("solicitacao_coleta.pk_id_solicitacao"), primary_key=True)
    nickname: Mapped[str] = mapped_column("apelido", String(50))
    street: Mapped[str] = mapped_column("logradouro", String(150))
    number: Mapped[str] = mapped_column("numero", String(20))
    complement: Mapped[str] = mapped_column("complemento", String(100), default="")
    district: Mapped[str] = mapped_column("bairro", String(100))
    city: Mapped[str] = mapped_column("cidade", String(100))
    state: Mapped[str] = mapped_column("estado", String(2))
    zip: Mapped[str] = mapped_column("cep", String(8))
    latitude: Mapped[float | None] = mapped_column(Numeric(10,7))
    longitude: Mapped[float | None] = mapped_column(Numeric(10,7))

class UploadPhoto(Base):
    __tablename__ = "foto_pedido"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    owner_id: Mapped[int] = mapped_column(ForeignKey("usuario.pk_id_usuario"), index=True)
    data: Mapped[bytes] = mapped_column(LargeBinary().with_variant(MEDIUMBLOB(), "mysql"))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=now)

class RequestPhoto(Base):
    __tablename__ = "solicitacao_foto"
    request_id: Mapped[int] = mapped_column(ForeignKey("solicitacao_coleta.pk_id_solicitacao"), primary_key=True)
    position: Mapped[int] = mapped_column(Integer, primary_key=True)
    photo_id: Mapped[str] = mapped_column(ForeignKey("foto_pedido.id"), index=True)
    __table_args__ = (CheckConstraint("position >= 0 AND position < 3"), UniqueConstraint("request_id", "photo_id"))

class Item(Base):
    __tablename__ = "item_solicitacao"
    request_id: Mapped[int] = mapped_column("pk_fk_id_solicitacao", ForeignKey("solicitacao_coleta.pk_id_solicitacao"), primary_key=True)
    material_id: Mapped[int] = mapped_column("pk_fk_id_material", ForeignKey("tipo_material.pk_id_material"), primary_key=True)
    weight: Mapped[float] = mapped_column("peso_kg", Numeric(10,2))
    __table_args__ = (CheckConstraint("peso_kg > 0"),)

class Review(Base):
    __tablename__ = "avaliacao"
    id: Mapped[int] = mapped_column("pk_id_avaliacao", primary_key=True)
    request_id: Mapped[int] = mapped_column("fk_id_solicitacao", ForeignKey("solicitacao_coleta.pk_id_solicitacao"), unique=True)
    score: Mapped[int] = mapped_column("nota", Integer)
    comment: Mapped[str] = mapped_column("comentario", String(500), default="")
    created_at: Mapped[datetime] = mapped_column("data_avaliacao", DateTime, default=now)
    __table_args__ = (CheckConstraint("nota between 1 and 5"),)

class Notification(Base):
    __tablename__ = "notificacao"
    id: Mapped[int] = mapped_column("pk_id_notificacao", primary_key=True)
    owner_id: Mapped[int] = mapped_column("fk_id_usuario", ForeignKey("usuario.pk_id_usuario"), index=True)
    request_id: Mapped[int] = mapped_column("fk_id_solicitacao", ForeignKey("solicitacao_coleta.pk_id_solicitacao"))
    message: Mapped[str] = mapped_column("mensagem", String(500))
    kind: Mapped[str] = mapped_column("tipo", String(50), default="atualizacao")
    read: Mapped[bool] = mapped_column("lida", Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column("data_criacao", DateTime, default=now)

class Attendance(Base):
    __tablename__ = "historico_atendimento"
    id: Mapped[int] = mapped_column(primary_key=True)
    request_id: Mapped[int] = mapped_column(ForeignKey("solicitacao_coleta.pk_id_solicitacao"))
    responsible_id: Mapped[int] = mapped_column(ForeignKey("responsavel_coleta.pk_fk_id_usuario"))
    released_at: Mapped[datetime] = mapped_column(DateTime, default=now)

class Audit(Base):
    __tablename__ = "auditoria"
    id: Mapped[int] = mapped_column(primary_key=True)
    actor_id: Mapped[int] = mapped_column(ForeignKey("usuario.pk_id_usuario"))
    action: Mapped[str] = mapped_column(String(100))
    entity: Mapped[str] = mapped_column(String(60))
    target: Mapped[str] = mapped_column(String(80))
    before: Mapped[dict | None] = mapped_column(JSON)
    after: Mapped[dict | None] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=now)

class SessionToken(Base):
    __tablename__ = "sessao"
    token_hash: Mapped[str] = mapped_column(String(64), primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("usuario.pk_id_usuario"))
    expires_at: Mapped[datetime] = mapped_column(DateTime)

class PlatformInfo(Base):
    __tablename__ = "informacao_plataforma"
    id: Mapped[int] = mapped_column(primary_key=True)
    content: Mapped[dict] = mapped_column(JSON, default=dict)
