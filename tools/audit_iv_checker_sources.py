import collections,csv,hashlib,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];P=ROOT/'reference-assets/iv-checkers-2026-09-30'
load=lambda n:json.loads((P/n).read_text(encoding='utf8'))
F=('attack','defense','stamina')
def norm(s):
 s=s.upper().replace('ALOLAN','ALOLA').replace('_NORMAL_','_')
 if s.endswith('_NORMAL'):s=s[:-7]
 s=re.sub('[^A-Z0-9]','',s)
 for a,b in [('GALARIAN','GALAR'),('HISUIAN','HISUI'),('PALDEAN','PALDEA')]:s=s.replace(a,b)
 return s
phone=json.loads((ROOT/'reference-assets/game-master-phone-2026-09-27/stats.json').read_text())['stats'];gm={}
for k,v in sorted(phone.items(),key=lambda x:x[0].endswith('_NORMAL')):gm[norm(k.split('_POKEMON_')[1])]=(tuple(v[f] for f in F),k,'celular 2026-09-27')
public={}
for r in load('game-master-public.json'):
 ps=r.get('data',{}).get('pokemonSettings',{});st=ps.get('stats',{})
 if all('base'+f.title() in st for f in F):public[r['templateId']]=tuple(st['base'+f.title()] for f in F)
 for mega in ps.get('tempEvoOverrides',[]):
  st=mega.get('stats',{})
  if not all('base'+f.title() in st for f in F):continue
  name=ps.get('form',ps['pokemonId'])+'_'+mega['tempEvoId'].replace('TEMP_EVOLUTION_','')
  gm[norm(name)]=(tuple(st['base'+f.title()] for f in F),r['templateId']+'/'+mega['tempEvoId'],'GM publico 2026-08-29: tempEvoOverrides')
alias={'GIRATINA':'GIRATINAALTERED','SHAYMIN':'SHAYMINLAND','TORNADUS':'TORNADUSINCARNATE','THUNDURUS':'THUNDURUSINCARNATE','LANDORUS':'LANDORUSINCARNATE','KELDEO':'KELDEOORDINARY','MELOETTA':'MELOETTAARIA','DARMANITAN':'DARMANITANSTANDARD','DARMANITANGALAR':'DARMANITANGALARSTANDARD','LYCANROC':'LYCANROCMIDDAY','GOURGEIST':'GOURGEISTAVERAGE','PUMPKABOO':'PUMPKABOOAVERAGE'}
rows=[]
for site,mons in [('Dittobase',load('ditto-pokemon.json')),('Stadium',load('stadium-pokemon.json'))]:
 for m in mons:
  name=m['slug'] if site=='Dittobase' else m['id'];st=tuple(m.get('base'+f.title()) for f in F) if site=='Dittobase' else tuple(m.get('stats',{}).get(f) for f in F)
  k=norm(name)
  if name.upper().endswith(('-SHADOW','_SHADOW')):k=k[:-6]
  if k=='GENESECTNORMAL':k='GENESECT'
  k=k.replace('PLUMAGE','').replace('DUDUNSPARCETHREESEGMENT','DUDUNSPARCETHREE')
  if name in ('pumpkaboo','gourgeist','PUMPKABOO','GOURGEIST'):k += 'SMALL'
  k=alias.get(k,k);ref=gm.get(k)
  if k in ('NIDORANF','NIDORANM','NIDORAN'):
   dex = '0029' if k=='NIDORANF' or (k=='NIDORAN' and '|29|' in m.get('names','')) else '0032' if k=='NIDORANM' or (k=='NIDORAN' and '|32|' in m.get('names','')) else None
   if dex:
    tk='V'+dex+'_POKEMON_NIDORAN_NORMAL';ref=(tuple(phone[tk][f] for f in F),tk,'celular 2026-09-27');k='NIDORAN'+dex
   else:ref=None
  status='sem_atributos' if not all(isinstance(x,(int,float)) and x>0 for x in st) else 'sem_correspondencia_segura' if ref is None else 'igual' if st==ref[0] else 'divergente'
  rows.append(dict(site=site,registro=name,chave=k,atributos_site='/'.join(map(str,st)),atributos_gm='/'.join(map(str,ref[0])) if ref else '',situacao=status,template=ref[1] if ref else '',referencia=ref[2] if ref else ''))
with (ROOT/'docs/auditoria-dittobase-stadium.csv').open('w',encoding='utf-8-sig',newline='') as f:
 w=csv.DictWriter(f,fieldnames=rows[0],delimiter=';');w.writeheader();w.writerows(rows)
summary={s:dict(collections.Counter(r['situacao'] for r in rows if r['site']==s)) for s in ('Dittobase','Stadium')}
summary['public_phone_shared']=len(set(phone)&set(public));summary['public_phone_differences']=[k for k in set(phone)&set(public) if tuple(phone[k][f] for f in F)!=public[k]]
summary['hashes']={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in P.glob('*.json') if f.name!='audit-summary.json'}
(P/'audit-summary.json').write_text(json.dumps(summary,indent=2));print(json.dumps(summary,indent=2))
for site in ('Dittobase','Stadium'):
 print(site,'DIFFERENCES')
 for r in rows:
  if r['site']==site and r['situacao']=='divergente':print(r['registro'],r['atributos_site'],r['atributos_gm'])
 print('UNMAPPED',[r['registro'] for r in rows if r['site']==site and r['situacao']=='sem_correspondencia_segura'])
