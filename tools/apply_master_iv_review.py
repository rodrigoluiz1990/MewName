"""Apply explicit approvals from the user's workbook; never rewrite that workbook.
Usage: python tools/apply_master_iv_review.py [--apply]
"""
import argparse, hashlib, json, re, unicodedata
from pathlib import Path
from openpyxl import load_workbook
from openpyxl.utils.datetime import from_excel
ROOT = Path(__file__).resolve().parents[1]
PERCENTS = {'98':44, '96':43, '93':42, '91':41, '67':30}
def norm(value):
    key = re.sub('[^A-Z0-9]', '', unicodedata.normalize('NFD', value).encode('ascii','ignore').decode().upper())
    for prefix, suffix in [('ALOLAN','ALOLA'),('GALARIAN','GALAR'),('HISUIAN','HISUI'),('PALDEAN','PALDEA')]:
        if key.startswith(prefix): return key[len(prefix):]+suffix
    return key

def parse_iv(value, percent):
    if not isinstance(value, str) or not re.fullmatch(r'\d{1,2}/\d{1,2}/\d{1,2}', value.strip()):
        raise ValueError(f'IV ambiguo/invalido: {value!r}')
    iv = list(map(int, value.split('/')))
    if any(x < 0 or x > 15 for x in iv) or sum(iv) != PERCENTS[percent]:
        raise ValueError(f'IV {value} nao corresponde a {percent}%')
    return iv

def approved_iv(value, proposal, percent):
    note = ''
    if isinstance(value, (int, float)):
        date = from_excel(value)
        candidate = f'{date.day}/{date.month}/{date.year % 100}'
        # Recover only a serial date that exactly matches the explicitly approved proposal.
        if candidate not in str(proposal).split(' ou '): raise ValueError(f'Data Excel ambigua: {value}')
        value = candidate
        note = f'Excel armazenou {candidate} como data serial {date.strftime("%d/%m/%Y")}; coincide com a proposta aprovada.'
    return parse_iv(value or proposal, percent), note

def plan():
    workbook = ROOT/'docs/revisao-iv-master-nivel-40.xlsx'
    path = ROOT/'app/src/main/assets/pvp/master_iv_table.json'
    before = path.read_bytes(); table = json.loads(before)
    index = {r['key']:r for r in table}; assert len(index)==len(table)
    wb = load_workbook(workbook, data_only=False)
    changes=[]; decisions=[]
    for r in list(wb['Divergências'].values)[1:]:
        if r[8] not in ('Aprovar proposta','Definir outro IV'): continue
        percent=str(r[2]); iv,note=approved_iv(r[9],r[4] if r[8]=='Aprovar proposta' else '',percent)
        old=index[r[1]][percent]
        assert old in (parse_iv(r[3],percent),iv), f'Valor alterado fora da planilha: {r[0]}'
        decisions.append(dict(id=r[0],key=r[1],percent=percent,before=old,after=iv,note=note))
        if old!=iv: changes.append(decisions[-1]);index[r[1]][percent]=iv
    for r in list(wb['Famílias sem regra'].values)[1:]:
        if r[11] not in ('Aprovar proposta','Definir outro IV'):continue
        key=norm(r[3]); entry=index.get(key)
        if entry is None:
            entry={'key':key};table.append(entry);index[key]=entry
        for i,percent in enumerate(('98','96','93','91')):
            iv,note=approved_iv(r[12+i],r[5+i] if r[11]=='Aprovar proposta' else '',percent)
            old=entry.get(percent)
            if old is not None and old!=iv:raise ValueError(f'Regra existente conflitante: {key}')
            decision=dict(id=r[0],key=key,percent=percent,before=old,after=iv,note=note)
            decisions.append(decision)
            if old!=iv:changes.append(decision);entry[percent]=iv
        if '67' not in entry:
            entry['67']=[10,10,10]
            changes.append(dict(id=r[0],key=key,percent='67',before=None,after=[10,10,10],note='Convenção 67% confirmada pelo usuário.'))
    for row in table:
        for percent,iv in row.items():
            if percent in PERCENTS:parse_iv('/'.join(map(str,iv)),percent)
    new='[\n'+',\n'.join('  '+json.dumps(row,ensure_ascii=False,separators=(',',':')) for row in sorted(table,key=lambda r:r['key']))+'\n]\n'
    return path,new,dict(workbook_sha256=hashlib.sha256(workbook.read_bytes()).hexdigest(),before_sha256=hashlib.sha256(before).hexdigest(),decisions=decisions,changes=changes)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    path,new,report=plan()
    print(json.dumps(dict(decisions=len(report['decisions']),changes=len(report['changes']),keys=sorted(set(r['key'] for r in report['changes']))),ensure_ascii=True))
    if args.apply and report['changes']:
        (ROOT/'docs/iv-master-aprovacoes-aplicadas-2026-10-04.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
        path.write_text(new,encoding='utf8',newline='\n')
