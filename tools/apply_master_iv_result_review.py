"""Apply explicit approvals from the result workbook, preserving the workbook.
Dry run by default; --apply updates only the Master IV table. Pending rows stay pending.
"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
import argparse
import json
import math
from openpyxl import load_workbook
from apply_master_iv_review import ROOT, norm, approved_iv
from sort_pokemon_json import dex

def plan():
    workbook = ROOT / 'docs/revisao-iv-master-resultado-2026-10-04.xlsx'
    wb = load_workbook(workbook, data_only=False)
    path = ROOT / 'app/src/main/assets/pvp/master_iv_table.json'
    table = json.loads(path.read_text(encoding='utf-8'))
    index = {r['key']: r for r in table}
    assert len(index) == len(table)
    changes = []
    for sheet in wb:
        rows = list(sheet.values)
        if not rows or len(rows[0]) != 21: continue
        for row in rows[1:]:
            if row[16] not in ('Aprovar proposta', 'Definir outro IV'): continue
            key = norm(row[2])
            entry = {'key': key}
            for i, percent in enumerate(('98', '96', '93', '91')):
                proposal = row[11+i] if row[16] == 'Aprovar proposta' else ''
                entry[percent], _ = approved_iv(row[17+i], proposal, percent)
            entry['67'] = [10, 10, 10]
            old = index.get(key)
            if old is not None and old != entry:
                raise ValueError(f'Conflicting existing rule: {key}; review before overwriting')
            if old is None:
                index[key] = entry
                table.append(entry)
                changes.append((row[0], key))
    # F-023 explicitly requests both standard and Zen Galar forms in the family.
    if 'DARMANITANGALAR' in index:
        zen = dict(index['DARMANITANGALAR'], key='DARMANITANGALARZEN')
        stats = json.loads((ROOT / 'app/src/main/assets/pokemon/stats.json').read_text(encoding='utf-8'))['DARMANITAN (GALAR ZEN)']
        def score(iv):
            a, d, h = iv
            return (stats['attack']+a)*(stats['defense']+d)*math.floor((stats['stamina']+h)*0.7903)
        for percent in ('98', '96', '93', '91'):
            total = sum(zen[percent])
            candidates = [(a,d,total-a-d) for a in range(16) for d in range(16) if 0 <= total-a-d <= 15]
            if score(zen[percent]) != max(map(score, candidates)):
                raise ValueError('Galar Zen no longer matches the approved level-40 combinations')
        if 'DARMANITANGALARZEN' in index and index['DARMANITANGALARZEN'] != zen:
            raise ValueError('Conflicting existing Galar Zen rule')
        if 'DARMANITANGALARZEN' not in index:
            table.append(zen)
            changes.append(('F-023 Zen', zen['key']))
    table.sort(key=lambda row: dex(row['key']))
    return path, table, changes

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    path, table, changes = plan()
    print(json.dumps(changes))
    print(f'{len(changes)} new rules; {len(table)} total.')
    if args.apply and changes:
        path.write_text('[\n' + ',\n'.join('  '+json.dumps(r,separators=(',',':')) for r in table) + '\n]\n', encoding='utf-8')
