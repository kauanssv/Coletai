"""Print an additive MySQL migration; --apply executes it on the configured database."""
import argparse
import os
from sqlalchemy import create_engine, inspect, text


def plan(engine):
    if engine.dialect.name != "mysql":
        raise ValueError("Esta migracao e exclusiva para MySQL")
    inspector = inspect(engine)
    columns = {column["name"] for column in inspector.get_columns("solicitacao_coleta")}
    additions = {
        "proposal_price": "NUMERIC(10,2) NULL",
        "proposal_message": "TEXT NULL",
        "proposed_at": "DATETIME NULL",
        "arrived_at": "DATETIME NULL",
        "collected_at": "DATETIME NULL",
    }
    operations = [f"ADD COLUMN `{name}` {definition}" for name, definition in additions.items() if name not in columns]
    checks = inspector.get_check_constraints("solicitacao_coleta")
    quote = engine.dialect.identifier_preparer.quote
    for check in checks:
        if "fk_id_responsavel_aceito" in check["sqltext"] and "status" in check["sqltext"]:
            operations.append("DROP CHECK " + quote(check["name"]))
    operations.append("ADD CONSTRAINT ck_request_proposal_flow CHECK ((status IN ('negociando','proposta','aceita','a_caminho','coletando','descartando','concluida') AND fk_id_responsavel_aceito IS NOT NULL) OR (status IN ('aberta','editando','cancelada') AND fk_id_responsavel_aceito IS NULL))")
    return "ALTER TABLE solicitacao_coleta\n  " + ",\n  ".join(operations)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    url = os.environ.get("COLETAI_DATABASE_URL")
    if not url:
        parser.error("Configure COLETAI_DATABASE_URL para o banco a migrar")
    engine = create_engine(url)
    statement = plan(engine)
    if args.apply:
        with engine.begin() as connection:
            connection.execute(text(statement))
    else:
        print(statement + ";")
