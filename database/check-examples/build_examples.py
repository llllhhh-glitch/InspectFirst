#!/usr/bin/env python3
"""Create original code-native SVG teaching diagrams, one per immutable published check.
These are expressly labelled schematics, not photographs, official screenshots or authentication evidence.
"""
import json,hashlib,re,textwrap
from pathlib import Path
from urllib.request import urlopen
from html import escape as e
P=Path(__file__).resolve().parent;ROOT=P.parent.parent
OUT=ROOT/'frontend/public/images/checks';OUT.mkdir(parents=True,exist_ok=True)
def get(path):
 with urlopen('http://127.0.0.1:5173'+path,timeout=30) as r:return json.load(r)
products=[];page=0
while True:
 data=get('/api/products?page='+str(page)+'&size=48');products+=data['items']
 if not data['hasNext']:break
 page+=1
# Snapshot the original immutable text for review, restart and import reconciliation.
snapshot=P/'before-examples.json'
if snapshot.exists():guides=json.loads(snapshot.read_text())
else:
 guides=[get('/api/products/'+p['id']+'/guide') for p in products]
 snapshot.write_text(json.dumps(guides,ensure_ascii=False,indent=2)+'\n')

def rect(x,y,w,h,fill='#fff',stroke='#718078',rx=12):return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="3"/>'
def line(x,y,a,b,color='#718078',sw=4):return f'<path d="M{x} {y}L{a} {b}" fill="none" stroke="{color}" stroke-width="{sw}" stroke-linecap="round"/>'
def circle(x,y,r,fill='#fff',stroke='#718078'):return f'<circle cx="{x}" cy="{y}" r="{r}" fill="{fill}" stroke="{stroke}" stroke-width="3"/>'
def text(x,y,s,size=20,color='#25382e',weight='400'):
 return f'<text x="{x}" y="{y}" font-size="{size}" font-weight="{weight}" fill="{color}">{e(s)}</text>'
def label(x,y,s,width=33,size=18):
 return ''.join(text(x,y+i*24,t,size) for i,t in enumerate(textwrap.wrap(s,width=width)[:3]))
def tick(x,y,bad=False):
 color='#c14c3e' if bad else '#258060'
 return circle(x,y,16,'#fff',color)+(line(x-6,y-6,x+6,y+6,color,3)+line(x+6,y-6,x-6,y+6,color,3) if bad else f'<path d="M{x-7} {y}l5 5 10-11" fill="none" stroke="{color}" stroke-width="3"/>')
def ring(x,y):return circle(x,y,26,'none','#c14c3e')+line(x+20,y-20,x+48,y-42,'#c14c3e',3)
def phone(x,y,tablet=False):
 w=130 if tablet else 92;h=166
 return rect(x,y,w,h,'#f1f5f2','#43594d',14)+rect(x+8,y+15,w-16,h-29,'#fff','#c2cbc5',4)+line(x+w/2-12,y+8,x+w/2+12,y+8,'#43594d',3)
def screen(x,y):return rect(x,y,196,120,'#eff4f0','#43594d',5)+line(x+98,y+120,x+98,y+144)+line(x+63,y+146,x+133,y+146)
def laptop(x,y):return rect(x,y,184,107,'#f4f7f4','#43594d',5)+f'<path d="M{x} {y+108}l-17 20h218l-17-20Z" fill="#e6ece8" stroke="#43594d" stroke-width="3"/>'
def camera(x,y):return rect(x,y,180,106,'#eff4f0','#43594d',10)+rect(x+30,y-18,58,23,'#eff4f0','#43594d',4)+circle(x+102,y+57,37,'#fff','#43594d')+circle(x+102,y+57,24,'#e7eee9','#43594d')+rect(x+12,y+16,22,12,'#a9bbb0','#43594d',2)
def bag(x,y,code=''):
 if 'M41178' in code:return f'<path d="M{x} {y+40}h190l-18 121H{x+18}Z M{x+42} {y+40}v-15a40 40 0 0 1 80 0v15" fill="#f4ece1" stroke="#806c53" stroke-width="4"/>'
 return rect(x,y+40,184,117,'#f4ece1','#806c53',15)+f'<path d="M{x+25} {y+38}V{y+5}q65-70 130 0v33" fill="none" stroke="#806c53" stroke-width="4"/>'+line(x+12,y+68,x+172,y+68,'#806c53',3)
def furniture(x,y,code):
 if code in ('00522047','70351886'):
  h=175 if code=='00522047' else 130;w=115 if code=='00522047' else 150
  s=rect(x,y,w,h,'#f8f5ef','#806c53',1)
  for z in range(1,5 if h==175 else 2):s+=line(x,y+z*h/(5 if h==175 else 2),x+w,y+z*h/(5 if h==175 else 2),'#806c53',3)
  if w==150:s+=line(x+75,y,x+75,y+h,'#806c53',3)
  return s
 if code=='POANG-BIRCH-FRAME':return f'<path d="M{x+20} {y}l15 95 110 5 20 55H{x+5}l10-80 M{x+15} {y+75}h150 M{x+153} {y+75}l-14-65" fill="none" stroke="#806c53" stroke-width="8"/>'
 s=rect(x,y+35,183,18,'#f8f5ef','#806c53',1)+line(x+12,y+53,x+12,y+157,'#806c53',7)+line(x+170,y+53,x+170,y+157,'#806c53',7)
 if code=='80354276':s+=rect(x+10,y+57,65,92,'#f8f5ef','#806c53',1)+line(x+10,y+83,x+75,y+83,'#806c53',3)
 return s
def headphones(x,y,buds=False):
 if buds:return rect(x+20,y+80,143,75,'#f1f5f2','#43594d',22)+f'<path d="M{x+55} {y+19}q-22-25-29 0q-2 20 21 15v43 M{x+127} {y+19}q22-25 29 0q2 20-21 15v43" fill="none" stroke="#43594d" stroke-width="9"/>'
 return f'<path d="M{x+10} {y+101}V{y+70}a77 77 0 0 1 154 0v31" fill="none" stroke="#43594d" stroke-width="9"/>'+rect(x,y+70,35,78,'#e7eee9','#43594d',12)+rect(x+140,y+70,35,78,'#e7eee9','#43594d',12)
def device(x,y,cat,code):
 if cat in ('phones','tablets'):return phone(x+42,y,cat=='tablets')
 if cat=='computers':return laptop(x,y+20)
 if cat=='monitors':return screen(x,y+10)
 if cat=='cameras':return camera(x,y+28)
 if cat=='audio':return headphones(x,y+12,code.startswith('AIRPODS'))
 if cat=='furniture':return furniture(x+15,y,code)
 if cat=='luxury':return bag(x,y,code)
 return rect(x+48,y,93,157,'#f1f5f2','#43594d',9)+circle(x+94,y+19,4,'#43594d')+rect(x,y+119,155,48,'#eff4f0','#43594d',18)+circle(x+30,y+143,10)+circle(x+125,y+143,10)

# Each concept has a specific visual grammar and a concrete observation; no true/fake imagery.
def concept(title,cat):
 t=title.lower()
 if cat=='luxury' and ('evidence' in t or 'provenance' in t):return 'provenance'
 if 'memory-card' in t:return 'storage'
 if 'review a photo' in t:return 'playback'
 if 'promised accessories' in t:return 'identity'
 if any(v in t for v in ('account','ownership','handover','evidence','provenance','clean handover')):return 'access'
 if any(v in t for v in ('diagnostic','f12')):return 'diagnostics'
 if any(v in t for v in ('exact','model','generation','configuration','version','bundle','edition','body and included')):return 'identity'
 if any(v in t for v in ('noise control',)):return 'noise'
 if any(v in t for v in ('s pen','screen and touch','screen and face','screen and top','screen and home')):return 'touch'
 if any(v in t for v in ('focus','saved photographs')):return 'focus'
 if any(v in t for v in ('camera,','cameras,','each ear','stereo audio','connections and conferencing','display, input','practical workstation','display, controls','keyboard','gameplay','joy-con')):return 'function'
 if any(v in t for v in ('charging','ports,','power','usb-c','inputs and controls','connections')):return 'ports'
 if any(v in t for v in ('completeness',)):return 'parts'
 if any(v in t for v in ('safe collection','installation')):return 'assembly'
 if any(v in t for v in ('closure','included pieces','disc or digital')):return 'mechanism'
 if any(v in t for v in ('display','screen')):return 'display'
 return 'condition'
DESCRIPTIONS={
'provenance':('Compare purchase / repair records','Investigate unresolved history','Compare the seller history with available evidence. Receipts, packaging and codes can be copied; none proves authenticity.'),
'storage':('Try a supported memory card','Record any read/write error','Match the card type to the camera manual and review saved files. Do not format seller data.'),
'playback':('Review the saved image','Retest unreadable or damaged files','Check the saved file, not just the live preview. Use a known-good card and lens.'),
'access':('Seller releases access','Previous-owner prompt','Stop at an unresolved account or organization prompt; ask the owner to resolve it.'),
'diagnostics':('Run available tests','Save any error code','Record the actual result. A diagnostic pass is not a lifetime guarantee.'),
'identity':('Compare the actual label','Investigate a mismatch','Match the exact model, variant and agreed accessories with the listing.'),
'noise':('Compare two listening modes','Listen for a difference','Use steady background sound at safe volume. ANC never means complete silence.'),
'ports':('Use compatible equipment','Flag an unstable connection','Retest with a known-good cable or accessory. Do not force, scrape or probe a port.'),
'parts':('Match the correct manual','Identify missing parts','Missing structural fixings must be resolved before use; do not improvise replacements.'),
'assembly':('Plan stable installation','Stop if incomplete or loose','Use the matching assembly manual. Never climb, tip-test or stress-load furniture.'),
'condition':('Inspect the marked areas','Record visible damage','Compare wear with your agreement. A visual inspection cannot prove hidden condition.'),
'mechanism':('Operate gently','Flag jams or loose fittings','Use normal motion only; never force a closure, card slot or disc drive.'),
'display':('Look at plain images','Mark persistent defects','Compare normal viewing distance and lighting; a diagram is not a pixel warranty.'),
'focus':('Focus near, then far','Retest repeated blur','Use a known-good compatible lens/card and review saved photographs.'),
'touch':('Trace across the surface','Retest a repeatable gap','Use compatible input hardware and test edge areas. Settings and accessories can interfere.'),
'function':('Try the named controls','Record skipped or failed tests','Use the checklist steps for this exact feature. A brief trial does not test every mode.')}

def scene(kind,x,bad,cat,code):
 y=176;color='#c14c3e' if bad else '#258060'
 if kind=='identity':
  s=device(x+54,y-5,cat,code)
  s+=rect(x+20,y+112,267,56,'#fff','#aebeb4',6)
  s+=text(x+34,y+146,'MODEL: '+('OTHER VERSION' if bad else code[:23]),17,color,'600')
  return s+(ring(x+195,y+139) if bad else tick(x+305,y+139))
 if kind=='provenance':
  s=rect(x+70,y,190,164,'#fff','#806c53',5)
  s+=text(x+88,y+30,'PURCHASE / REPAIR',14,'#806c53','600')
  for j in range(4):s+=line(x+89,y+53+j*23,x+239,y+53+j*23,'#cfbc9f',3)
  s+=text(x+245,y+80,'?' if bad else '↔',44,color,'600')
  return s+(ring(x+165,y+95) if bad else '')
 if kind=='access':
  s=rect(x+56,y,218,155,'#fff','#43594d',12)
  s+=rect(x+130,y+33,70,55,'#f7f1e6',color,8)
  s+=f'<path d="M{x+142} {y+33}v-15a23 23 0 0 1 46 0v15" fill="none" stroke="{color}" stroke-width="5"/>' if bad else f'<path d="M{x+142} {y+33}v-15a23 23 0 0 1 46 0" fill="none" stroke="{color}" stroke-width="5"/>'
  return s+label(x+77,y+120,'Previous owner required' if bad else 'Ready for agreed setup',25,17)+(tick(x+288,y+35,bad))
 if kind=='diagnostics':
  s=rect(x+45,y,245,165,'#fff','#43594d',8)
  for j,k in enumerate(('Display / input','Sound / sensors','Other available tests')):
   s+=text(x+62,y+37+j*41,k,16)+tick(x+264,y+30+j*41,bad and j==1)
  return s
 if kind in ('display','touch') and cat in ('phones','tablets'):
  wide=cat=='tablets';ox=x+122;oy=y+12;w=106 if wide else 68
  s=phone(x+110,y-8,wide)
  if kind=='touch':
   for j in range(4):s+=line(ox+j*w/3,oy,ox+j*w/3,oy+110,'#cfdbd3',1)
   for j in range(4):s+=line(ox,oy+j*110/3,ox+w,oy+j*110/3,'#cfdbd3',1)
   s+=f'<path d="M{ox+4} {oy+85}l{w*.25} -50 {w*.25} 46'+(f'm{w*.18} -20 {w*.23} -30' if bad else f' {w*.18} -20 {w*.23} -30')+'" fill="none" stroke="'+color+'" stroke-width="5"/>'
   if bad:s+=ring(ox+w*.65,oy+63)
  else:
   for j,c in enumerate(('#fff','#101b16','#d26760','#56a274','#5d8cba')):s+=rect(ox+j*w/5,oy,w/5,110,c,'none',0)
   if bad:s+=line(ox+w*.4,oy,ox+w*.4,oy+110,'#ffbe32',4)+ring(ox+w*.4,oy+53)
  return s
 if kind in ('display','touch'):
  s=screen(x+70,y+7)
  if kind=='touch':
   for j in range(4):s+=line(x+83+j*52,y+20,x+83+j*52,y+114,'#cfdbd3',1)
   for j in range(3):s+=line(x+82,y+27+j*35,x+254,y+27+j*35,'#cfdbd3',1)
   s+=f'<path d="M{x+90} {y+98}l35-43 38 42'+('m24-26 24-24' if bad else ' 26-27 24-24')+'" fill="none" stroke="'+color+'" stroke-width="6"/>'
   if bad:s+=ring(x+195,y+77)
  else:
   for j,c in enumerate(('#fff','#101b16','#d26760','#56a274','#5d8cba')):s+=rect(x+78+j*36,y+15,36,104,c,'none',0)
   if bad:s+=line(x+145,y+17,x+145,y+118,'#ffbe32',4)+ring(x+145,y+60)
  return s
 if kind=='ports':
  s=rect(x+45,y+12,250,100,'#f1f5f2','#43594d',12)+rect(x+139,y+94,64,17,'#fff',color,5)
  s+=rect(x+141+(14 if bad else 0),y+118,60,25,'#eff4f0',color,4)+line(x+171+(14 if bad else 0),y+144,x+171,y+175,color,6)
  return s+(ring(x+183,y+113) if bad else tick(x+271,y+137))
 if kind=='noise':
  s=headphones(x+88,y+5)
  for j in range(6):s+=line(x+26+j*17,y+48-(j%3)*9,x+26+j*17,y+90+(j%3)*9,'#b4c5bb',3)
  for j in range(4 if bad else 2):s+=line(x+275+j*12,y+56-(j%2)*8,x+275+j*12,y+81+(j%2)*8,color,3)
  return s+text(x+89,y+184,'Fit + settings matter',17)
 if kind=='storage':
  s=f'<path d="M{x+95} {y}h110l27 28v145H{x+95}Z" fill="#eff4f0" stroke="#43594d" stroke-width="4"/>'
  for j in range(5):s+=rect(x+108+j*21,y+8,12,26,'#c6a663','none',1)
  return s+text(x+109,y+80,'MEMORY',19)+text(x+113,y+110,'CARD',22)+tick(x+268,y+130,bad)
 if kind=='playback':
  s=rect(x+60,y+10,220,143,'#eff4f0','#43594d',12)+rect(x+80,y+29,150,101,'#fff',color,3)
  s+=f'<path d="M{x+89} {y+114}l35-53 30 32 27-18 38 39Z" fill="#dbe8df" stroke="{color}" stroke-width="3"/>'
  return s+tick(x+263,y+109,bad)
 if kind=='function' and cat=='audio':
  s=headphones(x+84,y+4,code.startswith('AIRPODS'))
  for j in range(8):
   amplitude=(4 if j%2 else 28) if bad else (12+(j%3)*10)
   s+=line(x+88+j*21,y+80-amplitude,x+88+j*21,y+80+amplitude,color,3)
  return s+tick(x+300,y+148,bad)
 if kind=='function' and cat=='computers':
  s=laptop(x+71,y+10)
  for j in range(5):s+=rect(x+91+j*27,y+125,18,12,'#fff',color if bad and j==2 else '#718078',2)
  return s+(ring(x+151,y+131) if bad else tick(x+290,y+126))
 if kind=='focus':
  s=camera(x+30,y+43)+line(x+210,y+95,x+267,y+95,color,3)+rect(x+260,y+55,60,80,'#fff',color,2)
  for delta in ((-4,4) if bad else (0,)):
   s+=line(x+271+delta,y+65,x+308+delta,y+122,color,2)+line(x+308+delta,y+65,x+271+delta,y+122,color,2)
  return s
 if kind in ('parts','assembly'):
  s=furniture(x+18,y-10,code)
  for j in range(3):
   s+=rect(x+245,y+12+j*45,32,11,'#e3e9e5',color,2)+line(x+277,y+17+j*45,x+296,y+17+j*45,color,3)
  if bad:s+=ring(x+268,y+62)+text(x+237,y+156,'Missing / loose',15,color)
  else:s+=tick(x+270,y+157)
  return s
 if kind=='mechanism':
  if cat=='luxury':s=bag(x+76,y-5,code)
  else:s=device(x+61,y-3,cat,code)
  s+=line(x+111,y+70,x+229,y+70,color,4)
  if bad:s+=f'<path d="M{x+154} {y+62}l10 18 10-18 10 18" fill="none" stroke="{color}" stroke-width="4"/>'+ring(x+178,y+73)
  else:s+=tick(x+282,y+68)
  return s
 s=device(x+68,y-7,cat,code)
 if kind=='condition':
  if bad:s+=f'<path d="M{x+127} {y+29}l13 23-17 17 21 21-12 22" fill="none" stroke="{color}" stroke-width="4"/>'+ring(x+137,y+65)
  else:s+=tick(x+289,y+75)
 else:
  s+=rect(x+25,y+128,78,37,'#fff',color,8)+circle(x+45,y+146,5,'#fff',color)+line(x+60,y+146,x+88,y+146,color,3)
  if bad:s+=tick(x+282,y+145,True)
  else:s+=tick(x+282,y+145)
 return s

entries=[];seen=set()
for guide in guides:
 product=guide['product'];cat=product['categorySlug'];code=product['modelCode']
 for check in guide['items']:
  kind=concept(check['title'],cat);good,bad,caption=DESCRIPTIONS[kind]
  if cat=='luxury':
   if kind=='identity':good,bad='Compare the claimed version','Ask about inconsistencies'
   caption+=' This does not establish authenticity.'
  if kind=='function':
   # Short feature labels connect the illustration to this check, rather than a repeated generic photo.
   good='Try: '+check['title'][:40];bad='Flag: failures or skipped tests'
  digest=hashlib.sha256((code+'|'+check['title']+'|'+json.dumps(check['steps'])).encode()).hexdigest()[:12]
  slug=re.sub('[^a-z0-9]+','-',code.lower()).strip('-')+'-'+kind+'-'+digest
  src='/images/checks/'+slug+'.svg'
  assert src not in seen;seen.add(src)
  title=product['name']+' — '+check['title']
  svg='<svg xmlns="http://www.w3.org/2000/svg" width="900" height="536" viewBox="0 0 900 536" role="img" aria-labelledby="title desc"><title id="title">'+e(title)+'</title><desc id="desc">'+e(caption)+' Simplified editorial illustration, not a photograph or device screenshot.</desc><rect width="900" height="536" fill="#fff"/><g font-family="Arial,Helvetica,sans-serif">'
  svg+=text(28,36,'INSPECTFIRST  /  ILLUSTRATED EXAMPLE',16,'#67786e','600')+text(28,70,check['title'][:65],27,'#183c2a','600')+text(28,98,product['name'][:85],18,'#67786e')
  svg+=rect(24,119,418,325,'#f3f8f4','#d9e7dd',16)+rect(458,119,418,325,'#fff7f3','#f1ddd4',16)
  svg+=text(44,153,'COMPARE',15,'#258060','700')+text(478,153,'CHECK FURTHER',15,'#a34838','700')
  svg+=scene(kind,60,False,cat,code)+scene(kind,494,True,cat,code)
  svg+=label(44,398,good,36,18)+label(478,398,bad,36,18)
  svg+=text(28,478,'ILLUSTRATION · simplified positions, colours and interfaces',17,'#67786e')+text(28,507,'Use the written steps. These drawings are not real faults or proof of authenticity.',16,'#67786e')+'</g></svg>'
  slug+='-'+hashlib.sha256(svg.encode()).hexdigest()[:10]
  src='/images/checks/'+slug+'.svg'
  (OUT/(slug+'.svg')).write_text(svg)
  image=dict(url=src,alt=title+'. '+caption,caption=caption,kind='DIAGRAM',sourceUrl=check['sources'][0]['url'],sourceTitle='Reference: '+check['sources'][0]['title'])
  entries.append(dict(productId=product['id'],modelCode=code,guideId=guide['id'],baseVersionId=guide['versionId'],baseVersion=guide['version'],checkId=check['id'],title=check['title'],order=check['order'],concept=kind,image=image))
assert len(guides)==45 and len(entries)==179
(P/'examples.json').write_text(json.dumps(entries,ensure_ascii=False,indent=2)+'\n')
print('Created',len(entries),'unique check-specific SVG illustrations across',len(guides),'products.')
