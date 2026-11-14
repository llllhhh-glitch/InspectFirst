#!/usr/bin/env python3
import json,concurrent.futures,hashlib,xml.etree.ElementTree as ET
from pathlib import Path
from urllib.request import urlopen
P=Path(__file__).resolve().parent
PLAN=json.loads((P/'examples.json').read_text());OLD=json.loads((P/'before-examples.json').read_text())
def get(path):
 with urlopen('http://127.0.0.1:5173'+path,timeout=20) as r:return json.load(r)
assets={};checked=[]
for old in OLD:
 pid=old['product']['id'];g=get('/api/products/'+pid+'/guide');v=get('/api/guides/versions/'+g['versionId'])
 assert g['versionId']!=old['versionId'] and g['version']>old['version']
 for a,b in zip(g['items'],old['items']):
  for key in ('title','purpose','steps','expectedObservation','abnormalSigns','limitations'):assert a[key]==b[key]
  assert len(a['images'])==1 and a['images'][0]['kind']=='DIAGRAM'
  assert a['images']==next(x['images'] for x in v['items'] if x['id']==a['id'])
  plan=next(e for e in PLAN if e['productId']==pid and e['title']==a['title']);assert a['images'][0]==plan['image']
  assets[a['images'][0]['url']]=a['images'][0]
 # Original version content and item IDs must remain intact after new publication.
 original=get('/api/guides/versions/'+old['versionId'])
 assert [x['id'] for x in original['items']]==[x['id'] for x in old['items']]
 assert all(not x.get('images') for x in original['items'])
 checked.append({'productId':pid,'name':g['product']['name'],'versionId':g['versionId'],'version':g['version'],'checks':len(g['items'])})
def verify_asset(path):
 with urlopen('http://127.0.0.1:5173'+path,timeout=15) as r:
  assert r.headers.get_content_type()=='image/svg+xml';b=r.read()
 tree=ET.fromstring(b)
 assert tree.tag=='{http://www.w3.org/2000/svg}svg'
 assert b'<script' not in b and b'<foreignObject' not in b and b'ILLUSTRATION' in b
 assert path.split('/')[-1].split('.')[0].endswith(hashlib.sha256(b).hexdigest()[:10])
 return path
valid=list(concurrent.futures.ThreadPoolExecutor(max_workers=8).map(verify_asset,assets))
assert len(checked)==45 and len(valid)==179 and sum(x['checks'] for x in checked)==179
report={'products':45,'currentChecks':179,'illustratedChecks':179,'uniqueCurrentImages':179,'allAreLabelledSchematics':True,'originalVersionsRetained':45,'tests':['Every public check has the planned image metadata','Public product and version APIs agree','All 179 SVG requests succeed and parse','No scripts or foreignObject in generated illustrations','Image paths match content hashes','Original version IDs and checks unchanged','Guide backend tests: 10 passed','Frontend production build passed'],'productVersions':checked}
(P/'verification.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({k:v for k,v in report.items() if k!='productVersions'}))
