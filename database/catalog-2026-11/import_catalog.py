#!/usr/bin/env python3
"""Validate or import through the existing ADMIN/CSRF content API, never direct SQL.
Usage: python3 import_catalog.py [--apply | --verify] [--base-url http://127.0.0.1:5173]
Private credentials are read locally, never accepted on the command line or printed.
The private manifest makes interrupted imports resumable. Existing publications are never edited.
"""
import argparse, collections, hashlib, http.cookiejar, json, os, re, sys
from pathlib import Path
from urllib.request import Request, build_opener, HTTPCookieProcessor
from urllib.parse import urlencode, urlsplit
from urllib.error import HTTPError
P=Path(__file__).resolve().parent
ROOT=P.parent.parent
LOCAL=ROOT/'.local'
DATA=json.loads((P/'catalog.json').read_text())
SOURCES=json.loads((P/'sources.json').read_text())
MANIFEST=LOCAL/'catalog-2026-11-manifest.json'
def digest(value): return hashlib.sha256(json.dumps(value,sort_keys=True,ensure_ascii=False).encode()).hexdigest()
def private_json(path,value):
    temp=path.with_suffix('.tmp')
    fd=os.open(temp,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    with os.fdopen(fd,'w') as f: json.dump(value,f,indent=2); f.write('\n')
    os.replace(temp,path)
def check_text(value,limit):
    assert isinstance(value,str) and value.strip() and len(value)<=limit

def validate():
    count=collections.Counter(); keys=set()
    known={v['url'] for v in SOURCES.values()}
    for c in DATA['categories']:
        assert re.fullmatch('[a-z-]{1,60}',c['slug']);check_text(c['name'],80)
    slugs={c['slug'] for c in DATA['categories']}
    for row in DATA['products']:
        assert row['categorySlug'] in slugs;count[row['categorySlug']]+=1
        p=row['product']; g=row['guide']; key=(p['brand'],p['modelCode'])
        assert key not in keys;keys.add(key)
        for k,n in [('brand',80),('modelCode',80),('name',160)]:check_text(p[k],n)
        assert p['status']=='ACTIVE' and g['expectedVersion']==0
        assert g['reviewedOn']==DATA['reviewedOn']
        for k,n in [('title',200),('tools',600),('contentNote',1200)]:check_text(g[k],n)
        assert 1<=len(g['items'])<=40
        for i in g['items']:
            assert set(i)=={'title','purpose','steps','expectedObservation','abnormalSigns','limitations','sources'}
            for k,n in [('title',200),('purpose',600),('expectedObservation',1200),('abnormalSigns',1200),('limitations',1200)]:check_text(i[k],n)
            assert 1<=len(i['steps'])<=20 and len('\n'.join(i['steps']))<=2000
            for step in i['steps']:check_text(step,500)
            assert 1<=len(i['sources'])<=10
            for s in i['sources']:
                for k,n in [('title',200),('url',1000),('publisher',80)]:check_text(s[k],n)
                u=urlsplit(s['url']);assert u.scheme in ('https','http') and u.hostname and not u.username
                assert s['url'] in known
    assert len(count)==9 and all(v>= (4 if k=='cameras' else 5) for k,v in count.items())
    return dict(count)

class Client:
    def __init__(self,base): self.base=base;self.http=build_opener(HTTPCookieProcessor(http.cookiejar.CookieJar()))
    def call(self,path,method='GET',data=None,form=False):
        headers={}
        if method!='GET':
            csrf=self.call('/api/auth/csrf');headers[csrf['headerName']]=csrf['token']
            headers['Content-Type']='application/x-www-form-urlencoded' if form else 'application/json'
        body=None if data is None else (urlencode(data).encode() if form else json.dumps(data).encode())
        try:
            with self.http.open(Request(self.base+path,data=body,headers=headers,method=method),timeout=40) as r:
                b=r.read();return json.loads(b) if b else None
        except HTTPError as e: raise RuntimeError('HTTP %s at %s (no credentials logged)'%(e.code,path)) from None
    def login(self):
        props={}
        for line in (LOCAL/'identity-db.properties').read_text().splitlines():
            if '=' in line and not line.lstrip().startswith('#'):
                k,v=line.split('=',1);props[k.strip()]=v.strip()
        self.call('/api/auth/login','POST',{'email':props['inspectfirst.admin.email'],'password':props['inspectfirst.admin.password']},True)
    def pages(self,path):
        out=[];page=0
        while True:
            data=self.call(path+'?'+urlencode({'page':page,'size':48}));out.extend(data['items'])
            if not data['hasNext']:return out
            page+=1

def equivalent(actual,expected):
    for k in ('title','tools','reviewedOn','contentNote'):
        assert actual[k]==expected[k], 'Published field mismatch: '+k
    assert len(actual['items'])==len(expected['items'])
    for a,b in zip(actual['items'],expected['items']):
        for k in ('title','purpose','steps','expectedObservation','abnormalSigns','limitations'):
            assert a[k]==b[k], 'Published check mismatch: '+k
        assert sorted((s['title'],s['url'],s['publisher']) for s in a['sources'])==sorted((s['title'],s['url'],s['publisher']) for s in b['sources'])

def verify(c):
    results={};allrows=[]
    for cat in DATA['categories']:
        page=0;items=[]
        while True:
            d=c.call('/api/products?'+urlencode({'category':cat['slug'],'page':page,'size':48}));items+=d['items']
            if not d['hasNext']:break
            page+=1
        expected=[r for r in DATA['products'] if r['categorySlug']==cat['slug']]
        for row in expected:
            found=[p for p in items if p['brand']==row['product']['brand'] and p['modelCode']==row['product']['modelCode']]
            assert len(found)==1,'Missing/duplicate product '+row['product']['name']
            g=c.call('/api/products/'+found[0]['id']+'/guide');equivalent(g,row['guide'])
            allrows.append({'name':found[0]['name'],'productId':found[0]['id'],'versionId':g['versionId'],'checks':len(g['items'])})
        assert len(items)>=5, 'Category remains incomplete: '+cat['slug']
        results[cat['name']]=len(items)
    # Exercise real pagination and model-name search, not just admin listings.
    public=c.pages('/api/products');assert len({p['id'] for p in public})==len(public)
    for row in DATA['products']:
        q=c.call('/api/products?'+urlencode({'q':row['product']['modelCode'],'size':48}))
        assert any(p['modelCode']==row['product']['modelCode'] for p in q['items'])
    report={'verifiedOn':DATA['reviewedOn'],'categoryCounts':results,'importedProducts':len(allrows),'importedChecks':sum(r['checks'] for r in allrows),'totalPublicProducts':len(public),'products':allrows,'checks':['Draft schema and length validation','Every imported public guide matches all content fields and sources','All nine category filters','All catalogue model-code searches','Public pagination and unique product IDs'],'limits':'Editorial buyer checklists; no expert authenticity certification, laboratory measurements or real-device testing were performed.'}
    (P/'verification.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({k:report[k] for k in ('categoryCounts','importedProducts','importedChecks','totalPublicProducts')},ensure_ascii=False))

def apply(c):
    c.login()
    try:
        m=json.loads(MANIFEST.read_text()) if MANIFEST.exists() else {'products':{}}
        cats={x['slug']:x for x in c.call('/api/ops/content/categories')}
        existing=c.pages('/api/ops/content/products')
        backup=LOCAL/'catalog-before-2026-11.json'
        if not backup.exists():
            gs=c.pages('/api/ops/content/guides');versions=[]
            for g in gs:
                for v in c.call('/api/ops/content/guides/'+g['id']+'/versions'):
                    versions.append(c.call('/api/ops/content/versions/'+v['id']))
            private_json(backup,{'categories':list(cats.values()),'products':existing,'guides':gs,'versions':versions})
        for cat in DATA['categories']:
            current=cats.get(cat['slug'])
            if current is None:current=c.call('/api/ops/content/categories','POST',cat)
            elif current['name']!=cat['name']:current=c.call('/api/ops/content/categories/'+current['id'],'PUT',cat)
            cats[cat['slug']]=current
        for row in DATA['products']:
            p=row['product'];key=p['brand']+'::'+p['modelCode'];record=m['products'].get(key,{})
            if record.get('published'):
                assert record['hash']==digest(row),'Dataset changed after publication; create a reviewed new version explicitly.'
                equivalent(c.call('/api/products/'+record['productId']+'/guide'),row['guide']);continue
            if not record.get('productId'):
                matches=[x for x in existing if x['brand']==p['brand'] and x['modelCode']==p['modelCode']]
                product=matches[0] if matches else c.call('/api/ops/content/products','POST',{**p,'categoryId':cats[row['categorySlug']]['id']})
                record={'productId':product['id'],'hash':digest(row)};m['products'][key]=record;private_json(MANIFEST,m)
            if not record.get('versionId'):
                gs=[g for g in c.pages('/api/ops/content/guides') if g.get('productId')==record['productId']]
                versions=c.call('/api/ops/content/guides/'+gs[0]['id']+'/versions') if gs else []
                assert not any(v['status']=='PUBLISHED' for v in versions),'Existing publication outside this import: '+p['name']
                drafts=[v for v in versions if v['status']=='DRAFT']
                v=c.call('/api/ops/content/versions/'+drafts[0]['id']) if drafts else c.call('/api/ops/content/guides','POST',{'productId':record['productId']})
                record['versionId']=v['versionId'];private_json(MANIFEST,m)
            v=c.call('/api/ops/content/versions/'+record['versionId'])
            if v['status']=='DRAFT':
                saved=c.call('/api/ops/content/versions/'+record['versionId'],'PUT',{**row['guide'],'expectedVersion':v['editVersion']})
                c.call('/api/ops/content/versions/'+record['versionId']+'/publish','POST',{'expectedVersion':saved['editVersion']})
            equivalent(c.call('/api/products/'+record['productId']+'/guide'),row['guide'])
            record['published']=True;private_json(MANIFEST,m)
            print('Published:',p['name'],flush=True)
    finally:c.call('/api/auth/logout','POST',{})
    verify(c)

def main():
    a=argparse.ArgumentParser();g=a.add_mutually_exclusive_group();g.add_argument('--apply',action='store_true');g.add_argument('--verify',action='store_true');a.add_argument('--base-url',default='http://127.0.0.1:5173');args=a.parse_args()
    counts=validate();print('Validated:',json.dumps(counts),flush=True)
    base=urlsplit(args.base_url)
    assert base.scheme=='http' and base.hostname in ('127.0.0.1','localhost'),'Use the local development API only.'
    c=Client(args.base_url.rstrip('/'))
    if args.apply:apply(c)
    elif args.verify:verify(c)
if __name__=='__main__':
    try:main()
    except (Exception,KeyboardInterrupt) as e:
        print(type(e).__name__+': '+str(e));sys.exit(1)
