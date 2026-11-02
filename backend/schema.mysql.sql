
CREATE TABLE IF NOT EXISTS informacao_plataforma (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	content JSON NOT NULL, 
	PRIMARY KEY (id)
)

;


CREATE TABLE IF NOT EXISTS tipo_material (
	pk_id_material INTEGER NOT NULL AUTO_INCREMENT, 
	nome VARCHAR(80) NOT NULL, 
	descricao VARCHAR(255) NOT NULL, 
	ativo BOOL NOT NULL, 
	PRIMARY KEY (pk_id_material), 
	UNIQUE (nome)
)

;


CREATE TABLE IF NOT EXISTS usuario (
	pk_id_usuario INTEGER NOT NULL AUTO_INCREMENT, 
	nome VARCHAR(100) NOT NULL, 
	email VARCHAR(254) NOT NULL, 
	senha_hash VARCHAR(255) NOT NULL, 
	`role` VARCHAR(20) NOT NULL, 
	status_acesso VARCHAR(20) NOT NULL, 
	ativo BOOL NOT NULL, 
	telefone VARCHAR(40) NOT NULL, 
	documento VARCHAR(30) NOT NULL, 
	data_cadastro DATETIME NOT NULL, 
	revision INTEGER NOT NULL, 
	PRIMARY KEY (pk_id_usuario), 
	UNIQUE (email)
)

;


CREATE TABLE IF NOT EXISTS administrador (
	pk_fk_id_usuario INTEGER NOT NULL, 
	PRIMARY KEY (pk_fk_id_usuario), 
	FOREIGN KEY(pk_fk_id_usuario) REFERENCES usuario (pk_id_usuario)
)

;


CREATE TABLE IF NOT EXISTS auditoria (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	actor_id INTEGER NOT NULL, 
	action VARCHAR(100) NOT NULL, 
	entity VARCHAR(60) NOT NULL, 
	target VARCHAR(80) NOT NULL, 
	`before` JSON, 
	after JSON, 
	created_at DATETIME NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(actor_id) REFERENCES usuario (pk_id_usuario)
)

;


CREATE TABLE IF NOT EXISTS cidadao (
	pk_fk_id_usuario INTEGER NOT NULL, 
	PRIMARY KEY (pk_fk_id_usuario), 
	FOREIGN KEY(pk_fk_id_usuario) REFERENCES usuario (pk_id_usuario)
)

;


CREATE TABLE IF NOT EXISTS responsavel_coleta (
	pk_fk_id_usuario INTEGER NOT NULL, 
	tipo VARCHAR(20) NOT NULL, 
	PRIMARY KEY (pk_fk_id_usuario), 
	FOREIGN KEY(pk_fk_id_usuario) REFERENCES usuario (pk_id_usuario)
)

;


CREATE TABLE IF NOT EXISTS sessao (
	token_hash VARCHAR(64) NOT NULL, 
	user_id INTEGER NOT NULL, 
	expires_at DATETIME NOT NULL, 
	PRIMARY KEY (token_hash), 
	FOREIGN KEY(user_id) REFERENCES usuario (pk_id_usuario)
)

;


CREATE TABLE IF NOT EXISTS coletor_independente (
	pk_fk_id_usuario INTEGER NOT NULL, 
	foto_url VARCHAR(500), 
	PRIMARY KEY (pk_fk_id_usuario), 
	FOREIGN KEY(pk_fk_id_usuario) REFERENCES responsavel_coleta (pk_fk_id_usuario)
)

;


CREATE TABLE IF NOT EXISTS cooperativa (
	pk_fk_id_usuario INTEGER NOT NULL, 
	nome_cooperativa VARCHAR(120) NOT NULL, 
	cnpj VARCHAR(14) NOT NULL, 
	razao_social VARCHAR(150) NOT NULL, 
	descricao TEXT NOT NULL, 
	logo_url VARCHAR(500), 
	PRIMARY KEY (pk_fk_id_usuario), 
	FOREIGN KEY(pk_fk_id_usuario) REFERENCES responsavel_coleta (pk_fk_id_usuario), 
	UNIQUE (cnpj)
)

;


CREATE TABLE IF NOT EXISTS endereco (
	pk_id_endereco INTEGER NOT NULL AUTO_INCREMENT, 
	fk_id_cidadao INTEGER NOT NULL, 
	apelido VARCHAR(50) NOT NULL, 
	logradouro VARCHAR(150) NOT NULL, 
	numero VARCHAR(20) NOT NULL, 
	complemento VARCHAR(100) NOT NULL, 
	bairro VARCHAR(100) NOT NULL, 
	cidade VARCHAR(100) NOT NULL, 
	estado VARCHAR(2) NOT NULL, 
	cep VARCHAR(8) NOT NULL, 
	latitude NUMERIC(10, 7), 
	longitude NUMERIC(10, 7), 
	excluido BOOL NOT NULL, 
	PRIMARY KEY (pk_id_endereco), 
	UNIQUE (fk_id_cidadao, pk_id_endereco), 
	CHECK (latitude is null or latitude between -90 and 90), 
	CHECK (longitude is null or longitude between -180 and 180), 
	FOREIGN KEY(fk_id_cidadao) REFERENCES cidadao (pk_fk_id_usuario)
)

;


CREATE TABLE IF NOT EXISTS responsavel_tipo_material (
	pk_fk_id_responsavel INTEGER NOT NULL, 
	pk_fk_id_material INTEGER NOT NULL, 
	PRIMARY KEY (pk_fk_id_responsavel, pk_fk_id_material), 
	FOREIGN KEY(pk_fk_id_responsavel) REFERENCES responsavel_coleta (pk_fk_id_usuario), 
	FOREIGN KEY(pk_fk_id_material) REFERENCES tipo_material (pk_id_material)
)

;


CREATE TABLE IF NOT EXISTS solicitacao_coleta (
	pk_id_solicitacao INTEGER NOT NULL AUTO_INCREMENT, 
	fk_id_cidadao INTEGER NOT NULL, 
	fk_id_endereco INTEGER NOT NULL, 
	fk_id_responsavel_aceito INTEGER, 
	data_desejada DATE NOT NULL, 
	status VARCHAR(20) NOT NULL, 
	descricao TEXT NOT NULL, 
	observacoes TEXT NOT NULL, 
	data_criacao DATETIME NOT NULL, 
	data_atualizacao DATETIME NOT NULL, 
	data_aceite DATETIME, 
	data_deslocamento DATETIME, 
	data_conclusao DATETIME, 
	proposal_price NUMERIC(10, 2), 
	proposal_message TEXT NOT NULL, 
	proposed_at DATETIME, 
	arrived_at DATETIME, 
	collected_at DATETIME, 
	revision INTEGER NOT NULL, 
	excluido BOOL NOT NULL, 
	rejected_by JSON NOT NULL, 
	PRIMARY KEY (pk_id_solicitacao), 
	FOREIGN KEY(fk_id_cidadao, fk_id_endereco) REFERENCES endereco (fk_id_cidadao, pk_id_endereco), 
	CHECK ((status in ('negociando','proposta','aceita','a_caminho','coletando','descartando','concluida') and fk_id_responsavel_aceito is not null) or (status in ('aberta','cancelada') and fk_id_responsavel_aceito is null)), 
	FOREIGN KEY(fk_id_cidadao) REFERENCES cidadao (pk_fk_id_usuario), 
	FOREIGN KEY(fk_id_endereco) REFERENCES endereco (pk_id_endereco), 
	FOREIGN KEY(fk_id_responsavel_aceito) REFERENCES responsavel_coleta (pk_fk_id_usuario)
)

;


CREATE TABLE IF NOT EXISTS avaliacao (
	pk_id_avaliacao INTEGER NOT NULL AUTO_INCREMENT, 
	fk_id_solicitacao INTEGER NOT NULL, 
	nota INTEGER NOT NULL, 
	comentario VARCHAR(500) NOT NULL, 
	data_avaliacao DATETIME NOT NULL, 
	PRIMARY KEY (pk_id_avaliacao), 
	CHECK (nota between 1 and 5), 
	UNIQUE (fk_id_solicitacao), 
	FOREIGN KEY(fk_id_solicitacao) REFERENCES solicitacao_coleta (pk_id_solicitacao)
)

;


CREATE TABLE IF NOT EXISTS endereco_solicitacao (
	pk_fk_id_solicitacao INTEGER NOT NULL, 
	apelido VARCHAR(50) NOT NULL, 
	logradouro VARCHAR(150) NOT NULL, 
	numero VARCHAR(20) NOT NULL, 
	complemento VARCHAR(100) NOT NULL, 
	bairro VARCHAR(100) NOT NULL, 
	cidade VARCHAR(100) NOT NULL, 
	estado VARCHAR(2) NOT NULL, 
	cep VARCHAR(8) NOT NULL, 
	latitude NUMERIC(10, 7), 
	longitude NUMERIC(10, 7), 
	PRIMARY KEY (pk_fk_id_solicitacao), 
	FOREIGN KEY(pk_fk_id_solicitacao) REFERENCES solicitacao_coleta (pk_id_solicitacao)
)

;


CREATE TABLE IF NOT EXISTS historico_atendimento (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	request_id INTEGER NOT NULL, 
	responsible_id INTEGER NOT NULL, 
	released_at DATETIME NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(request_id) REFERENCES solicitacao_coleta (pk_id_solicitacao), 
	FOREIGN KEY(responsible_id) REFERENCES responsavel_coleta (pk_fk_id_usuario)
)

;


CREATE TABLE IF NOT EXISTS item_solicitacao (
	pk_fk_id_solicitacao INTEGER NOT NULL, 
	pk_fk_id_material INTEGER NOT NULL, 
	peso_kg NUMERIC(10, 2) NOT NULL, 
	PRIMARY KEY (pk_fk_id_solicitacao, pk_fk_id_material), 
	CHECK (peso_kg > 0), 
	FOREIGN KEY(pk_fk_id_solicitacao) REFERENCES solicitacao_coleta (pk_id_solicitacao), 
	FOREIGN KEY(pk_fk_id_material) REFERENCES tipo_material (pk_id_material)
)

;


CREATE TABLE IF NOT EXISTS notificacao (
	pk_id_notificacao INTEGER NOT NULL AUTO_INCREMENT, 
	fk_id_usuario INTEGER NOT NULL, 
	fk_id_solicitacao INTEGER NOT NULL, 
	mensagem VARCHAR(500) NOT NULL, 
	tipo VARCHAR(50) NOT NULL, 
	lida BOOL NOT NULL, 
	data_criacao DATETIME NOT NULL, 
	PRIMARY KEY (pk_id_notificacao), 
	FOREIGN KEY(fk_id_usuario) REFERENCES usuario (pk_id_usuario), 
	FOREIGN KEY(fk_id_solicitacao) REFERENCES solicitacao_coleta (pk_id_solicitacao)
)

;


CREATE TABLE IF NOT EXISTS foto_pedido (
	id VARCHAR(36) NOT NULL, 
	owner_id INTEGER NOT NULL, 
	data MEDIUMBLOB NOT NULL, 
	created_at DATETIME NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(owner_id) REFERENCES usuario (pk_id_usuario)
)

;


CREATE TABLE IF NOT EXISTS solicitacao_foto (
	request_id INTEGER NOT NULL, 
	position INTEGER NOT NULL, 
	photo_id VARCHAR(36) NOT NULL, 
	PRIMARY KEY (request_id, position), 
	CHECK (position >= 0 AND position < 3), 
	UNIQUE (request_id, photo_id), 
	FOREIGN KEY(request_id) REFERENCES solicitacao_coleta (pk_id_solicitacao), 
	FOREIGN KEY(photo_id) REFERENCES foto_pedido (id)
)

;
