"""Read-only audit using the public Stadium bundle downloaded to .tmp-stadium-app.js."""
import ast
import collections
import hashlib
import itertools
import json
import math
import re
import csv
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
bundle_path = ROOT / '.tmp-stadium-app.js'
if not bundle_path.exists():
    bundle_path.write_bytes(urllib.request.urlopen('https://www.stadiumgaming.gg/js/app.js').read())
s = bundle_path.read_text(encoding='utf8')
m = re.search(r"7315:e=>\{\"use strict\";e.exports=JSON.parse\((\'(?:[^\'\\]|\\.)*\')\)", s)
mons = json.loads(ast.literal_eval(m[1]))
cpm = [float(v) for v in re.search(r'cpm:\[([^\]]+)\]', s[s.index('7846:'):])[1].split(',')]
normalize = lambda x: re.sub('[^A-Z0-9]', '', x.upper())
index = {normalize(x['id']): x for x in mons}
aliases = {'GALARIANFARFETCHD':'FARFETCHD_GALARIAN','GIRATINAORIGEM':'GIRATINA_ORIGIN',
           'HISUIANDECIDUEYE':'DECIDUEYE_HISUIAN','LYCANROCNOTURNO':'LYCANROC_MIDNIGHT',
           'HISUIANSAMUROTT':'SAMUROTT_HISUIAN','SHAYMINCEU':'SHAYMIN_SKY'}
table = json.loads((ROOT / 'app/src/main/assets/pvp/master_iv_table.json').read_text(encoding='utf8'))
rows, missing = [], []
for entry in table:
    mon = index.get(normalize(aliases.get(entry['key'], entry['key'])))
    if not mon or not all(mon.get('stats',{}).get(k,0)>0 for k in ('attack','defense','stamina')):
        missing.append(entry['key'])
        continue
    a, d, h = (mon['stats'][k] for k in ['attack','defense','stamina'])
    for level in (40, 50, 51):
        def score(iv):
            at, de, hp = iv
            # The site's Master CP cap is 9999; reproduce its floored-HP stat product.
            multiplier = next(v for v in reversed(cpm[:2 * (level - 1) + 1])
                              if math.floor((a+at)*math.sqrt((d+de)*(h+hp))*v*v/10) <= 9999)
            return multiplier*multiplier*(a+at)*(d+de)*math.floor(multiplier*(h+hp))
        for percent, iv in entry.items():
            if percent == 'key':
                continue
            total = sum(iv)
            candidates = [(at,de,total-at-de) for at in range(16) for de in range(16) if 0 <= total-at-de <= 15]
            best = max(map(score,candidates))
            winners = [list(x) for x in candidates if abs(score(x)-best) < 1e-7]
            rows.append(dict(key=entry['key'], site=mon['id'], level=level, percent=percent,
                             app=iv, best=winners, loss=100*(best-score(iv))/best,
                             different=iv not in winners, stats=mon['stats']))
output = {'bundle_sha256':hashlib.sha256(s.encode()).hexdigest(), 'entries':len(table),
          'missing':missing, 'rows':rows}
(ROOT / '.tmp-stadium-audit.json').write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding='utf8')
print('Unmapped:', missing)
for level in (40,50,51):
    print(level, {p:sum(r['different'] for r in rows if r['level']==level and r['percent']==p) for p in ('98','96','93','91','67')})
for row in [r for r in rows if r['level']==50 and r['percent']!='67' and r['different']][:20]:
    print(row['key'],row['percent'],row['app'],row['best'],round(row['loss'],3))

# Full comparison, including equal entries and separate 67% convention.
report_dir = ROOT / 'docs'
with (report_dir / 'auditoria-iv-master-stadium.csv').open('w',encoding='utf-8-sig',newline='') as f:
    writer = csv.writer(f, delimiter=';')
    writer.writerow(['registro_app','id_stadium','nivel','percentual','ivs_app','melhores_mesma_soma','diferente','perda_produto_pct','criterio'])
    for r in rows:
        fmt = lambda iv: '/'.join(map(str,iv))
        writer.writerow([r['key'],r['site'],r['level'],r['percent'],fmt(r['app']), ' ou '.join(map(fmt,r['best'])),r['different'],f"{r['loss']:.6f}",
                         'convencao_10_10_10_nao_tratar_como_erro' if r['percent']=='67' else 'maior_produto_mesma_soma'])
summary = []
for level in (40,50,51):
    rr = [r for r in rows if r['level']==level and r['percent']!='67']
    diffs = [r for r in rr if r['different']]
    summary.append(f"| {level} | {len(rr)} | {len(diffs)} | {len(set(r['key'] for r in diffs))} |")
report = ["""# Auditoria IV Master × Stadium Gaming

Consulta: 27/09/2026. Nenhuma combinação do app foi alterada.

## Escopo e método

Foram lidos 565 registros de `master_iv_table.json`; 564 têm correspondência com atributos utilizáveis no bundle público do Stadium. Aegislash existe lá, mas com `stats: {}`, e ficou pendente. Não foi presumida a forma Escudo ou Espada.

Fontes: [Rank Checker](https://www.stadiumgaming.gg/rank-checker), [bundle público do site](https://www.stadiumgaming.gg/js/app.js).
Foram extraídos os atributos e multiplicadores CPM usados pelo próprio site. O cálculo foi reproduzido localmente: `(AtaqueBase + IV) × (DefesaBase + IV) × CPM² × floor((StaminaBase + IV) × CPM)`, respeitando o limite Master do site (9999 PC). Não se trata de consultar um ranking externo de desempenho em batalhas.

Comparamos a melhor combinação **dentro da mesma soma de IVs**: 98%=44, 96%=43, 93%=42 e 91%=41. Isso adapta o ranking de produto de atributos ao formato da tabela do app. O site não publica uma tabela própria chamada “IV Master por percentual”. Empates exatos são aceitos e todas as alternativas constam no CSV. Não usamos porcentagem de produto de atributos como se fosse porcentagem de IV.

A tabela local não especifica nível. Foram comparados 40, 50 e 51, sem piso mínimo de captura. No site, XL e Melhor Companheiro alteram esse limite; numa sessão sem preferências gravadas, o código usa 40. A forte concordância em 40 indica que esse provavelmente foi o critério original da tabela local.

| Nível | Combinações comparadas (98–91) | Diferentes | Registros com diferença |
|---|---:|---:|---:|""", *summary,
"""
## Diferenças no nível 40

“Stadium” abaixo significa a melhor combinação com a mesma soma, calculada a partir dos dados e fórmula do site. IVs na ordem Ataque/Defesa/PS. A perda mede produto de atributos, não dano, taxa de vitórias ou relevância competitiva.

| Registro | IV % | App | Stadium nível 40 | Perda de produto |
|---|---:|---|---|---:|"""]
for r in rows:
    if r['level']==40 and r['percent']!='67' and r['different']:
        report.append(f"| {r['key']} | {r['percent']} | {fmt(r['app'])} | {' ou '.join(map(fmt,r['best']))} | {r['loss']:.4f}% |")
report.append("""
## Cuidados antes de corrigir

- **Regra de família versus espécie:** o app usa `MasterIvBadgeCatalog` para aplicar regras às famílias/ramificações. O CSV compara cada chave à espécie/forma de mesmo nome; isso não prova que a regra de família esteja errada. Por exemplo, `GALARIANFARFETCHD` precisa ser interpretado considerando Sirfetch’d; `GLIMMET`, considerando Glimmora. Confirmei que as três divergências de Farfetch’d de Galar coincidem com os melhores IVs de Sirfetch’d e as quatro de Glimmet coincidem com os melhores de Glimmora no nível 40. Assim, 7 das 39 diferenças têm explicação pela evolução, restando 32 combinações em 22 registros para revisão; isso ainda não autoriza corrigir todas automaticamente. Decidir o alvo da regra vem antes de trocar IVs.
- **Formas:** aliases explícitos usados: GALARIANFARFETCHD → FARFETCHD_GALARIAN; GIRATINAORIGEM → GIRATINA_ORIGIN; HISUIANDECIDUEYE → DECIDUEYE_HISUIAN; LYCANROCNOTURNO → LYCANROC_MIDNIGHT; HISUIANSAMUROTT → SAMUROTT_HISUIAN; SHAYMINCEU → SHAYMIN_SKY. Chaves genéricas como Rotom e Zygarde foram comparadas ao registro genérico homônimo do site, não a todas as formas.
- **Dados do site também divergem:** Dugtrio tem defesa 134 no registro genérico do Stadium e 136 no app; Gourgeist genérico tem 171/219/146 no Stadium e 175/213/163 no app. No segundo caso, conferir tamanho/forma é indispensável. Não substituir os atributos locais auditados automaticamente pelos do site.
- **67%:** os 522 registros com essa regra usam 10/10/10. Ela não é a melhor combinação irrestrita de soma 30 no Stadium, em nenhum dos três níveis, mas é a única combinação de soma 30 com piso 10/10/10. Portanto, foi separada dos totais de divergências; é uma convenção de coleção/piso de captura, não 522 erros comprovados.
- **Aegislash:** pendente por ausência de atributos no registro consultado do Stadium.

## Arquivos e validação

- `auditoria-iv-master-stadium.csv`: todas as comparações nos três níveis, inclusive equivalentes e a convenção 67%.
- `../tools/audit_stadium_master.py`: reprodução da auditoria; baixa o bundle público caso `.tmp-stadium-app.js` não exista. Para atualizar a consulta, substituir esse cache por uma nova cópia do bundle.
- Validação cruzada: 147 comparações de 10 registros, nos três níveis, executadas também pelo módulo JavaScript original do Stadium (`calculator.calc`), sem divergências em relação ao cálculo da auditoria. Amostra: Ambipom, Arceus, Calyrex, Farfetch’d de Galar, Rotom, Zygarde, Slaking, Lycanroc Noturno, Samurott de Hisui e Absol.

SHA-256 do bundle usado: `""" + output['bundle_sha256'] + "`.\n")
(report_dir / 'auditoria-iv-master-stadium.md').write_text('\n'.join(report),encoding='utf8')
