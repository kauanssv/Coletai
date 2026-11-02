"""Build the presentation map/road graph from OSM. Runtime needs no internet.

Data © OpenStreetMap contributors, ODbL 1.0; see docs/BLUETOOTH_OFFLINE.md.
"""
import json
import sys
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BBOX = (-23.575, -46.61, -23.515, -46.515)
target = ROOT / 'app/src/main/assets/offline_streets_sao_paulo.geojson'
KEYS = {'name', 'ref', 'highway', 'oneway', 'oneway:bicycle', 'junction', 'access', 'vehicle', 'motor_vehicle', 'foot', 'bicycle', 'nodes', 'osm_id'}
def bounds(features):
    coordinates = [point for feature in features for point in feature['geometry']['coordinates']]
    return [min(p[0] for p in coordinates), min(p[1] for p in coordinates),
            max(p[0] for p in coordinates), max(p[1] for p in coordinates)]

if '--trim-existing' in sys.argv:
    output = json.loads(target.read_text(encoding='utf-8'))
    features = []
    for feature in output['features']:
        coordinates = feature['geometry']['coordinates']
        if not any(BBOX[0] <= lat <= BBOX[2] and BBOX[1] <= lng <= BBOX[3] for lng, lat in coordinates):
            continue
        feature['properties'] = {key: value for key, value in feature['properties'].items() if key in KEYS}
        feature['geometry']['coordinates'] = [[round(lng, 6), round(lat, 6)] for lng, lat in coordinates]
        features.append(feature)
    output['features'], output['bbox'], output['query_bbox'] = features, bounds(features), list(BBOX)
    target.write_text(json.dumps(output, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
    print(f'{len(features)} ways; {target.stat().st_size} bytes')
    sys.exit(0)
query = '[out:json][timeout:120];way[highway][highway!~"^(construction|proposed)$"](%s);out body geom;' % ','.join(map(str, BBOX))
request = urllib.request.Request('https://overpass-api.de/api/interpreter',
    data=urllib.parse.urlencode({'data': query}).encode(),
    headers={'User-Agent': 'Coletai-Offline-Presentation/1.0'})
with urllib.request.urlopen(request, timeout=150) as response:
    source = json.load(response)
if source.get('remark'):
    raise RuntimeError(source['remark'])
features = []
for way in source['elements']:
    if way['type'] != 'way' or len(way.get('geometry', [])) < 2:
        continue
    geometry = way['geometry']
    if any('lat' not in point or 'lon' not in point for point in geometry):
        continue
    properties = {key: value for key, value in way.get('tags', {}).items() if key in KEYS}
    properties.update(osm_id=way['id'], nodes=way['nodes'])
    features.append(dict(type='Feature', properties=properties,
        geometry=dict(type='LineString', coordinates=[[round(p['lon'], 6), round(p['lat'], 6)] for p in geometry])))
if len(features) < 100:
    raise RuntimeError('Road extract unexpectedly empty; existing asset was preserved')
output = dict(type='FeatureCollection', bbox=bounds(features), query_bbox=list(BBOX),
    attribution='© OpenStreetMap contributors', license='ODbL-1.0',
    source='https://overpass-api.de/api/interpreter',
    retrieved_at=datetime.now(timezone.utc).isoformat(), features=features)
target.write_text(json.dumps(output, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
print(f'{len(features)} ways; {target.stat().st_size} bytes')
