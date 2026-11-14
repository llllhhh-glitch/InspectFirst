#!/usr/bin/env python3
"""Publish new guide versions with per-check images. Preserves original published versions."""
import json,sys,hashlib
from pathlib import Path
P=Path(__file__).resolve().parent;ROOT=P.parent.parent
sys.path.insert(0,str(P.parent/'catalog-2026-11'))
from import_catalog import Client,private_json
PLAN=json.loads((P/'examples.json').read_text());ORIGINAL=json.loads((P/'before-examples.json').read_text())
M=ROOT/'.local/check-examples-import.json'
def fingerprint(value):return hashlib.sha256(json.dumps(value,sort_keys=True).encode()).hexdigest()
def check(actual,expected):
 for key in ('title','tools','contentNote','reviewedOn'):assert actual[key]==expected[key]
 assert len(actual['items'])==len(expected['items'])
 for a,b in zip(actual['items'],expected['items']):
  for key in ('title','purpose','steps','expectedObservation','abnormalSigns','limitations'):assert a[key]==b[key],key
  assert a['images']==b['images'],'Example mismatch: '+a['title']
def main():
 c=Client('http://127.0.0.1:5173');c.login()
 state=json.loads(M.read_text()) if M.exists() else {}
 try:
  for g in ORIGINAL:
   pid=g['product']['id'];images=[e for e in PLAN if e['productId']==pid]
   payload={k:g[k] for k in ('title','tools','contentNote','reviewedOn')};payload['items']=[]
   for item in g['items']:
    planned=[e for e in images if e['checkId']==item['id']];assert len(planned)==1
    image=planned[0]['image'];assert len(image['alt'])<=400 and len(image['caption'])<=600 and len(image['sourceTitle'])<=200
    payload['items'].append({**{k:item[k] for k in ('title','purpose','steps','expectedObservation','abnormalSigns','limitations','sources')},'images':[image]})
   f=fingerprint(payload);r=state.get(pid,{})
   if r.get('published'):
    if r['hash']==f:
     check(c.call('/api/products/'+pid+'/guide'),payload);continue
    assert '--revise' in sys.argv,'Revised assets need explicit --revise.'
    current=c.call('/api/products/'+pid+'/guide');assert current['versionId']==r['versionId'],'A newer external version exists.'
    draft=c.call('/api/ops/content/guides','POST',{'productId':pid})
    r={'versionId':draft['versionId'],'baseVersionId':current['versionId'],'hash':f};state[pid]=r;private_json(M,state)
   if not r:
    current=c.call('/api/products/'+pid+'/guide');assert current['versionId']==g['versionId'],'Guide changed since review: '+g['product']['name']
    draft=c.call('/api/ops/content/guides','POST',{'productId':pid})
    r={'versionId':draft['versionId'],'baseVersionId':g['versionId'],'hash':f};state[pid]=r;private_json(M,state)
   assert r['hash']==f
   v=c.call('/api/ops/content/versions/'+r['versionId'])
   if v['status']=='DRAFT':
    saved=c.call('/api/ops/content/versions/'+r['versionId'],'PUT',{**payload,'expectedVersion':v['editVersion']})
    c.call('/api/ops/content/versions/'+r['versionId']+'/publish','POST',{'expectedVersion':saved['editVersion']})
   check(c.call('/api/products/'+pid+'/guide'),payload)
   r['published']=True;private_json(M,state);print('Published illustrated guide:',g['product']['name'],flush=True)
 finally:c.call('/api/auth/logout','POST',{})
 assert len(state)==45 and all(r.get('published') for r in state.values())
 print('All 45 guides / 179 checks have persisted examples; original versions retained.')
if __name__=='__main__':main()
