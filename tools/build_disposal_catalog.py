"""Build the installed catalogue from the Prefeitura's Google My Maps KML and reviewed extras.

Download the KML linked in docs/data/disposal_catalog_sources.json, then run:
python tools/build_disposal_catalog.py path/to/ecopontos.kml
"""
import argparse
import json
import re
import xml.etree.ElementTree as ET
from html import unescape
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NS = {"k": "http://www.opengis.net/kml/2.2"}
SOURCE = "https://prefeitura.sp.gov.br/web/subprefeituras/w/ecoponto-esta%C3%A7%C3%A3o-de-entrega-volunt%C3%A1ria-de-inserv%C3%ADveis"
MAP = "https://www.google.com/maps/d/viewer?mid=1Kok7L5JyjXN758y3vy3kMsKYdJylrQE"


def catalogue(kml, verified_on):
    points = []
    for mark in ET.parse(kml).getroot().findall(".//k:Placemark", NS):
        coordinates = mark.findtext("k:Point/k:coordinates", "", NS).strip()
        if not coordinates:  # Ignore sweeping-lot polygons: they are not disposal facilities.
            continue
        name = " ".join(mark.findtext("k:name", "", NS).split())
        if "general flores" in name.lower():  # Current Prefeitura directory says out of operation.
            continue
        fields = {}
        for item in re.split(r"<br\s*/?>", mark.findtext("k:description", "", NS)):
            key, separator, value = unescape(re.sub("<[^>]+>", "", item)).partition(":")
            if separator:
                fields[key.strip()] = value.strip()
        longitude, latitude, *_ = map(float, coordinates.split(","))
        if not (-24.1 < latitude < -23.3 and -46.95 < longitude < -46.2) or not fields.get("N-ecpt"):
            raise ValueError(f"Invalid municipal facility: {name}")
        materials = ["Papel", "Papelão", "Plástico", "Vidro", "Metal", "Entulho"]
        if "gesso" in name.lower() or "gesso" in fields.get("Recebimento diferenciado", "").lower():
            materials.append("Gesso")
        points.append(dict(
            id="sp-ecoponto-" + fields["N-ecpt"], name="Ecoponto " + name,
            category="recycling", address=fields.get("Endereço", "") + " - " + fields.get("Distrito", "") + ", São Paulo - SP",
            latitude=latitude, longitude=longitude, materials=materials, public=True,
            hours="Segunda a sábado: 06h–22h; domingos e feriados: 06h–18h",
            source=SOURCE, source_label="Prefeitura de São Paulo / mapa oficial no Google Maps",
            coordinate_source=MAP, verified_on=verified_on,
            notes="Pequenos volumes de entulho: até 1 m³ por entrega. Não recebe lixo orgânico domiciliar, medicamentos, pilhas, baterias ou eletrônicos. Confirme os limites de recebimento.",
        ))
    points.extend(json.loads((ROOT / "docs/data/disposal_points_extras.json").read_text(encoding="utf-8")))
    if len({point["id"] for point in points}) != len(points):
        raise ValueError("Duplicate facility identifiers")
    points.sort(key=lambda p: (0 if any(n in p["name"].lower() for n in ["astarte", "badra", "tatuap", "condessa", "anália"]) else 1, p["name"]))
    return dict(version=1, verified_on=verified_on, city="São Paulo - SP", points=points)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kml", type=Path)
    parser.add_argument("--verified-on", default="2026-10-31")
    args = parser.parse_args()
    result = catalogue(args.kml, args.verified_on)
    output = ROOT / "app/src/main/assets/disposal_points_sao_paulo.json"
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(result['points'])} facilities written to {output}")
