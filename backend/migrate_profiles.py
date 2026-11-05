"""Print the MySQL profile-photo migration; --apply executes it."""
import argparse
import os
from sqlalchemy import create_engine, inspect, text


def plan(engine):
    if engine.dialect.name != "mysql":
        raise ValueError("Esta migracao e exclusiva para MySQL")
    columns = {column["name"] for column in inspect(engine).get_columns("usuario")}
    return None if "profile_photo" in columns else "ALTER TABLE usuario ADD COLUMN profile_photo JSON NULL"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    url = os.environ.get("COLETAI_DATABASE_URL")
    if not url:
        parser.error("Configure COLETAI_DATABASE_URL para o banco a migrar")
    engine = create_engine(url)
    statement = plan(engine)
    if statement and args.apply:
        with engine.begin() as connection:
            connection.execute(text(statement))
    elif statement:
        print(statement + ";")
    else:
        print("A coluna profile_photo ja existe.")
