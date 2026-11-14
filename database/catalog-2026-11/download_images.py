#!/usr/bin/env python3
"""Download the explicitly selected public reference photographs. No stock-photo substitution.
Preserves originals, records origin and hashes; does not claim rights ownership or authenticate goods.
"""
from pathlib import Path
from urllib.request import Request,urlopen
from urllib.parse import urlsplit
import json,re,hashlib,concurrent.futures,time
P=Path(__file__).resolve().parent
ROOT=P.parent.parent
DEST=ROOT/'frontend/public/images/products'
DATA=ROOT/'frontend/src/data'
DEST.mkdir(parents=True,exist_ok=True);DATA.mkdir(parents=True,exist_ok=True)
selected=json.loads((P/'image-selections.json').read_text())
previous=json.loads((P/'image-download-results.json').read_text()).get('images',{}) if (P/'image-download-results.json').exists() else {}
try:
 from PIL import Image
 import io
except ImportError: Image=None

def download(pair):
 code,s=pair
 name=re.sub('[^a-z0-9-]+','-',code.lower()).strip('-')+'-'+hashlib.sha256(s['url'].encode()).hexdigest()[:10]
 cache=list(DEST.glob(name+'.*'))
 if not cache and previous.get(code,{}).get('url')==s['url']:
  old=ROOT/'frontend/public'/previous[code]['src'].lstrip('/')
  if old.exists():
   new=DEST/(name+old.suffix);new.write_bytes(old.read_bytes());cache=[new]
 try:
  if cache:
   path=cache[0];body=path.read_bytes()
  else:
   req=Request(s['url'],headers={'User-Agent':'Mozilla/5.0 (InspectFirst local course project)','Accept':'image/avif,image/webp,image/png,image/jpeg,*/*;q=0.5'})
   for attempt in range(2):
    try:
     with urlopen(req,timeout=22) as r:
      body=r.read(15_000_001);ct=r.headers.get_content_type()
     break
    except Exception:
     if attempt:raise
     time.sleep(.5)
   if len(body)>15_000_000 or len(body)<1000:raise ValueError('Invalid size')
   if body.startswith(b'\xff\xd8\xff'):ext='jpg'
   elif body.startswith(b'\x89PNG\r\n'):ext='png'
   elif body.startswith(b'RIFF') and body[8:12]==b'WEBP':ext='webp'
   elif body.startswith((b'GIF87a',b'GIF89a')):ext='gif'
   elif b'ftypavif' in body[:32]:ext='avif'
   else:raise ValueError('Not a supported raster image: '+ct)
   path=DEST/(name+'.'+ext);path.write_bytes(body)
  entry={**s,'modelCode':code,'src':'/images/products/'+path.name,'bytes':len(body),'sha256':hashlib.sha256(body).hexdigest(),'retrievedOn':'2026-11-06','usage':'Reference photograph for local student prototype; rights remain with the respective owner. Not a photo of the seller\'s actual item.'}
  if Image:
   with Image.open(io.BytesIO(body)) as img:
    img.verify()
   with Image.open(path) as img:entry.update(width=img.width,height=img.height)
  print('OK',code,len(body),flush=True);return code,entry,None
 except Exception as e:
  print('FAILED',code,type(e).__name__,flush=True);return code,None,type(e).__name__

results=list(concurrent.futures.ThreadPoolExecutor(max_workers=6).map(download,selected.items()))
images={k:v for k,v,e in results if not e};failures={k:e for k,v,e in results if e}
(P/'image-download-results.json').write_text(json.dumps({'images':images,'failures':failures},ensure_ascii=False,indent=2)+'\n')
if failures:raise SystemExit('Fix failed selections before publishing image manifest: '+', '.join(failures))
assert len(images)==45 and len({v['sha256'] for v in images.values()})==45
(DATA/'product-images.json').write_text(json.dumps(images,ensure_ascii=False,indent=2)+'\n')
credits=['# Product reference photographs','', 'Local student prototype. Photos identify product families/variants, not actual second-hand sale items. Colours and included accessories shown in a reference photo are not a promise about a listing. Rights remain with the brand, photographer or retailer. Recheck permissions before a public/commercial release. Original files are retained without generated replacement photos.','']
for code,v in images.items():
 credits+=['## '+v['name']+' — '+code,'','- Page: '+v['page'],'- Original image: '+v['url'],'- Local file: products/'+Path(v['src']).name,'- SHA-256: '+v['sha256'],'- Retrieved: '+v['retrievedOn'],'']
(ROOT/'frontend/public/images/SOURCES.md').write_text('\n'.join(credits))
print('Published 45 local image mappings; no duplicates.')
