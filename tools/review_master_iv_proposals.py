"""Read-only follow-up for IV Master review. Uses captured sources; does not change assets or the user's workbook."""
import collections, csv, hashlib, json, math, re, unicodedata
from pathlib import Path
from openpyxl import load_workbook, Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.worksheet.table import Table, TableStyleInfo
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'reference-assets/iv-master-review-2026-10-04'
load=lambda p:json.loads(p.read_text(encoding='utf8'))
def raw(s):return re.sub('[^A-Z0-9]','',unicodedata.normalize('NFD',s).encode('ascii','ignore').decode().upper())
def canonical(s):
 s=raw(s)
 for a,b in [('ALOLAN','ALOLA'),('GALARIAN','GALAR'),('HISUIAN','HISUI'),('PALDEAN','PALDEA')]:s=s.replace(a,b)
 return s
stats=load(ROOT/'app/src/main/assets/pokemon/stats.json')
local={raw(k):(k,tuple(v[f] for f in ('attack','defense','stamina'))) for k,v in stats.items()}
phone=load(SRC/'phone-stats.json')['stats']
gm={canonical(k.split('_POKEMON_')[1]):(k,tuple(v[f] for f in ('attack','defense','stamina'))) for k,v in phone.items()}
stadium={canonical(m['id']):m for m in load(SRC/'stadium-pokemon.json')}
ditto={canonical(m['slug']):m for m in load(SRC/'ditto-pokemon.json') if not m.get('isShadow') and not m.get('isMega')}
# Explicit form identity, independent of matching stats. Never identify a form by stats alone.
keys={
 'ARCANINEHISUI':'ARCANINEHISUI','DUGTRIO':'DUGTRIONORMAL','GOURGEIST':'GOURGEISTAVERAGE',
 'ZYGARDE':'ZYGARDEFIFTYPERCENT','ZYGARDE10':'ZYGARDETENPERCENT','ZYGARDECOMPLETO':'ZYGARDECOMPLETE',
 'MINIORCORE':'MINIORRED','AEGISLASH':'AEGISLASHSHIELD','AEGISLASHBLADE':'AEGISLASHBLADE',
 'DARMANITAN':'DARMANITANSTANDARD','DARMANITANGALAR':'DARMANITANGALARSTANDARD','DARMANITANGALARZEN':'DARMANITANGALARZEN',
 'SINISTCHACOMUM':'SINISTCHAUNREMARKABLE','SINISTCHAOBRAPRIMA':'SINISTCHAMASTERPIECE',
 'DUDUNSPARCE2SEGMENTOS':'DUDUNSPARCETWO','DUDUNSPARCE3SEGMENTOS':'DUDUNSPARCETHREE',
 'MEWTWOARMADURA':'MEWTWOA','DEOXYSATAQUE':'DEOXYSATTACK','DEOXYSDEFESA':'DEOXYSDEFENSE','DEOXYSVELOCIDADE':'DEOXYSSPEED',
 'DIALGAORIGEM':'DIALGAORIGIN','PALKIAORIGEM':'PALKIAORIGIN','GIRATINAORIGEM':'GIRATINAORIGIN',
 'BASCULEGIONFEMEA':'BASCULEGIONFEMALE','KYUREMPRETO':'KYUREMBLACK','KYUREMBRANCO':'KYUREMWHITE',
 'NECROZMAJUBADOCREPUSCULO':'NECROZMADUSKMANE','NECROZMAASASDAALVORADA':'NECROZMADAWNWINGS',
 'INDEEDEEFEMEA':'INDEEDEEFEMALE','MAUSHOLDFAMILIADE4':'MAUSHOLDFAMILYOFFOUR','MAUSHOLDFAMILIADE3':'MAUSHOLDFAMILYOFTHREE',
 'GIMMIGHOULBAU':'GIMMIGHOULCHEST','GIMMIGHOULANDARILHO':'GIMMIGHOULROAMING','TAUROSPALDEA':'TAUROSPALDEACOMBAT',
 'SHAYMIN':'SHAYMINLAND','SHAYMINCEU':'SHAYMINSKY','TORNADUS':'TORNADUSINCARNATE','THUNDURUS':'THUNDURUSINCARNATE','LANDORUS':'LANDORUSINCARNATE',
 'KELDEO':'KELDEOORDINARY','MELOETTA':'MELOETTAARIA','GIRATINA':'GIRATINAALTERED','LYCANROC':'LYCANROCMIDDAY','LYCANROCNOTURNO':'LYCANROCMIDNIGHT',
}
localkeys={'SINISTCHACOMUM':'SINISTCHAUNREMARKABLEFORM','SINISTCHAOBRAPRIMA':'SINISTCHAMASTERPIECEFORM',
 'DUDUNSPARCE2SEGMENTOS':'DUDUNSPARCETWOSEGMENTFORM','DUDUNSPARCE3SEGMENTOS':'DUDUNSPARCETHREESEGMENTFORM',
 'KYUREMPRETO':'KYUREMBLACK','KYUREMBRANCO':'KYUREMWHITE','KELDEORESOLUTE':'KELDEO','MELOETTAARIA':'MELOETTA','MAUSHOLDFAMILIADE4':'MAUSHOLDFAMILYOFFOUR','MAUSHOLDFAMILIADE3':'MAUSHOLDFAMILYOFTHREE'}
sitekeys={'DUGTRIONORMAL':'DUGTRIO','AEGISLASHSHIELD':'AEGISLASH', 'SINISTCHAUNREMARKABLE':'SINISTCHA',
 'DUDUNSPARCETWO':'DUDUNSPARCE','DUDUNSPARCETHREE':'DUDUNSPARCETHREESEGMENT','MEWTWOA':'MEWTWOARMORED'}
gmkeys={'TAUROSPALDEACOMBAT':'TAUROSPALDEACOMBAT','MAUSHOLDFAMILYOFFOUR':'MAUSHOLDFAMILYOFFOUR','MAUSHOLDFAMILYOFTHREE':'MAUSHOLDFAMILYOFTHREE'}
s=(SRC/'stadium-app.js').read_text(encoding='utf8');cpm=[float(v) for v in re.search(r'cpm:\[([^\]]+)\]',s[s.index('7846:'):])[1].split(',')]
CPM=cpm[78]
def best(st, rounded=False):
 if not st or not all(isinstance(x,(int,float)) and x>0 for x in st):return []
 a,d,h=st;result=[]
 for total in (44,43,42,41):
  candidates=[(i,j,total-i-j) for i in range(16) for j in range(16) if 0<=total-i-j<=15]
  def score(iv):
   c=next(v for v in reversed(cpm[:79]) if math.floor((a+iv[0])*math.sqrt((d+iv[1])*(h+iv[2]))*v*v/10)<=(10000 if rounded else 9999))
   sp=(a+iv[0])*(d+iv[1])*c*c*max(10,math.floor((h+iv[2])*c))
   return math.floor(sp+0.5) if rounded else sp
  peak=max(map(score,candidates));result.append([iv for iv in candidates if abs(score(iv)-peak)<1e-7])
 return result
fmt=lambda iv:'/'.join(map(str,iv))
combos=lambda result:[' ou '.join(map(fmt,x)) for x in result] if result else ['']*4

def lookup(name):
 k=canonical(name);k=keys.get(k,k)
 g=gm.get(gmkeys.get(k,k)) or gm.get(k+'NORMAL')
 sm=stadium.get(k) or stadium.get(sitekeys.get(k,''))
 dm=ditto.get(k) or ditto.get(sitekeys.get(k,''))
 l=local.get(localkeys.get(raw(name),raw(name)))
 ss=tuple(sm.get('stats',{}).get(f) for f in ('attack','defense','stamina')) if sm else None
 ds=tuple(dm.get('base'+f) for f in ('Attack','Defense','Stamina')) if dm else None
 return k,g,sm,dm,l,ss,ds

# Pre-evolutions are evaluated using their actual final evolution, not their own base stats.
targets={'Meowth (Galar)':'Perrserker','Mr. Mime (Galar)':'Mr. Rime','Wooper (Paldea)':'Clodsire','Sneasel (Hisui)':'Sneasler','Corsola (Galar)':'Cursola','Zigzagoon (Galar)':'Obstagoon','Linoone (Galar)':'Obstagoon','Yamask (Galar)':'Runerigus','Gimmighoul (Bau)':'Gholdengo','Gimmighoul (Andarilho)':'Gholdengo',
 'Pumpkaboo (Small)':'Gourgeist (Small)','Pumpkaboo (Large)':'Gourgeist (Large)','Pumpkaboo (Super)':'Gourgeist (Super)'}
wb=load_workbook(ROOT/'docs/revisao-iv-master-nivel-40.xlsx')
table=load(ROOT/'app/src/main/assets/pvp/master_iv_table.json');cat={canonical(x['key']):x for x in table}
requests=[]
for r in list(wb['Famílias sem regra'].values)[1:]:requests.append((r[0],r[3],r[3],r[11],''))
for r in list(wb['Formas herdadas'].values)[1:]:requests.append((r[0],r[1],targets.get(r[1],r[1]),r[5],r[3]))
for r in list(wb['Fora das famílias'].values)[1:]:requests.append((r[0],r[1],r[1],r[4],''))
for name in ('Zygarde','Zygarde (10%)','Zygarde (Completo)','Aegislash','Aegislash (Blade)','Dugtrio','Gourgeist','Sirfetch\'d','Glimmora','Slowbro (Galar)','Basculegion'):
 requests.append(('ANÁLISE',name,name,'Revisar forma/família', {'Zygarde (Completo)':'ZYGARDE',"Sirfetch'd":'GALARIANFARFETCHD','Glimmora':'GLIMMET','Slowbro (Galar)':'SLOWKINGGALAR','Basculegion':'BASCULIN','Zygarde (10%)':'ZYGARDE','Aegislash (Blade)':'AEGISLASH'}.get(name,canonical(name))))
rows=[]
for rid,name,target,decision,inherited in requests:
 k,g,sm,dm,l,ss,ds=lookup(target)
 source='Game Master do celular 04/10/2026' if g else 'Stats locais; não confirmados no cache atual'
 st=g[1] if g else l[1] if l else None
 if not st and ds:st=ds;source='Dittobase; não confirmado no cache atual'
 sb=best(ss);db=best(ds,True);own=best(st)
 rule=cat.get(canonical(target))
 if target=='Deoxys (Defesa)':rule=cat.get('DEOXYSDEFENSE')
 if not rule and inherited:
  rule=next((cat.get(canonical(x.strip())) for x in inherited.split(',') if cat.get(canonical(x.strip()))),None)
 current=[fmt(rule[p]) if rule and p in rule else '' for p in ('98','96','93','91')]
 differences=[p for p,cur,opts in zip(('98','96','93','91'),current,combos(own)) if cur and cur not in opts.split(' ou ')]
 status='Sem regra direta' if not any(current) else 'Rever '+', '.join(differences) if differences else 'Combinações compatíveis'
 if decision=='Aprovar proposta':status='Aplicado conforme aprovação'+('; cálculo atual difere: '+','.join(differences) if differences else '')
 notes=[]
 if not g:notes.append('Não afirmar validação pelo jogo: template não localizado neste cache.')
 if l and g and l[1]!=g[1]:notes.append('Atributos locais divergem do cache: '+fmt(l[1]))
 if ds and g and ds!=g[1]:notes.append('Dittobase usa atributos diferentes: '+fmt(ds))
 if target=='Deoxys (Defesa)':notes.append('DEOXYSDEFENSE já contém a combinação; o resolvedor ainda aceita múltiplas formas da família e deve ser revisado separadamente.')
 if ss and g and all(ss) and ss!=g[1]:notes.append('Stadium usa atributos diferentes: '+fmt(ss))
 if target!=name:notes.append('Regra da evolução '+target)
 if target=='Slowbro (Galar)':notes.append('A aprovação F-010 usa Slowking como alvo; Slowbro é evolução alternativa; a comparação separada confirmou as mesmas combinações neste nível.')
 if target=='Minior (Core)':notes.append('MINIOR genérico é Meteor; Core comparado ao template RED (mesmos atributos nas sete cores).')
 if 'Zygarde' in target:notes.append('Complete=100%; Complete Fifty Percent e Complete Ten Percent NÃO são a forma 100%.')
 if name=='Slither Wing':notes.append('É espécie básica; a pendência era associação ao catálogo, não necessidade de evoluir.')
 if name.startswith('Kyurem'):notes.append('Conferir aliases Black/Preto e White/Branco no resolvedor.')
 row={'id':rid,'espécie/forma':name,'alvo':target,'decisão original':decision,'situação':status,'atributos usados':fmt(st) if st else '', 'fonte atributos':source,'template jogo':g[0] if g else '', 'chave local':l[0] if l else '', 'regra comparada':rule['key'] if rule else '', 'observação':'; '.join(notes)}
 for p,cur,a,b,c in zip(('98','96','93','91'),current,combos(own),combos(sb),combos(db)):
  row.update({f'atual {p}':cur,f'proposta {p}':a,f'Stadium {p}':b,f'Dittobase {p}':c})
 row['Stadium atributos']=fmt(ss) if ss and all(ss) else 'Sem atributos/registro'
 row['Dittobase atributos']=fmt(ds) if ds else 'Sem registro'
 row['Stadium link']='https://www.stadiumgaming.gg/rank-checker?pokemon='+sm['id'] if sm else ''
 row['Dittobase link']='https://www.dittobase.com/pokemon-go/pvp-ivs?pokemon='+dm['slug']+'&league=master' if dm else ''
 row['Poke Genie']='Não verificado numericamente; sem tabela/API pública encontrada.'
 row['nova decisão']='Pendente' if decision!='Aprovar proposta' else 'Aplicado'
 rows.append(row)

if __name__=='__main__':
 with (ROOT/'docs/iv-master-propostas-2026-10-04.csv').open('w',encoding='utf-8-sig',newline='') as f:
  w=csv.DictWriter(f,fieldnames=rows[0],delimiter=';');w.writeheader();w.writerows(rows)
 (SRC/'analysis.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf8')
 print('Rows',len(rows),'sem GM',[(r['id'],r['alvo']) for r in rows if not r['template jogo']])
 print('sem local',[(r['id'],r['alvo']) for r in rows if not r['chave local']])
 print('divergencias atributos',[(r['alvo'],r['observação']) for r in rows if 'diverge' in r['observação'] or 'diferentes' in r['observação']])
 print('Aprovadas',[(r['alvo'],r['situação']) for r in rows if r['decisão original']=='Aprovar proposta'])
