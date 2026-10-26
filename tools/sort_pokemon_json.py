"""Stable National Dex ordering for Pokemon catalogs; use --write to apply.
Preserves record formatting, evolution paths, move lists and IV rankings.
"""
import argparse
import json
import re
import unicodedata
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/assets'
DECODER = json.JSONDecoder()
def read(path):
    return (ROOT / path).read_text(encoding='utf-8-sig')
def normalize(name):
    # Some imported catalogs contain mojibake gender symbols; match only, never rewrite.
    name = name.replace('\u00e2\u2122\u20ac', ' female ').replace('\u00e2\u2122\u201a', ' male ')
    name = name.replace('\u00c2\u2122\u20ac', ' female ').replace('\u00c2\u2122\u201a', ' male ')
    name = name.replace(chr(0x2640), ' female ').replace(chr(0x2642), ' male ')
    text = unicodedata.normalize('NFKD', name.lower())
    return re.sub(r'[^a-z0-9]+', ' ', ''.join(c for c in text if not unicodedata.combining(c))).strip()
INDEX = {}
for entry in json.loads(read('pokemon/names.json')):
    for name in [entry['name'], *entry.get('aliases', [])]:
        INDEX.setdefault(normalize(name), set()).add(entry['dex'])
for entries in [json.loads(read('pokemon/collection_game_forms.json')), json.loads(read('raids/metadata.json'))['pokemon']]:
    for entry in entries:
        if entry.get('dex'):
            INDEX.setdefault(normalize(entry['id']), set()).add(entry['dex'])
def dex(name):
    normalized = normalize(name)
    # The generic GM NIDORAN_NORMAL entry has the male height/weight.
    matches = {32} if normalized == 'nidoran normal' else INDEX.get(normalized, set())
    if not matches:
        matches = set().union(*(v for k, v in INDEX.items() if k.replace(' ', '') == normalized.replace(' ', '')))
    if not matches:
        candidates = [(len(key), values) for key, values in INDEX.items() if f' {key} ' in f' {normalized} ']
        if candidates:
            longest = max(length for length, _ in candidates)
            matches = set().union(*(values for length, values in candidates if length == longest))
    if len(matches) != 1:
        raise ValueError(f'Unresolved National Dex: {name!r}: {matches}')
    return next(iter(matches))
def image_dex(name):
    match = re.search(r'(?:pm|pokemon_icon_)(\d+)', name, re.I)
    if not match:
        if name in {'pokemon_home_logo.png', 'pokemon_home_transporter.png', 'pokemon_home_transporter_pokeball.png', 'transporter_line_fx.png'}: return 100000
        raise ValueError(f'Unknown sprite: {name}')
    return int(match[1])
def children(text, start=0):
    while text[start].isspace(): start += 1
    is_object = text[start] == '{'
    pos = start + 1
    result = []
    while True:
        while text[pos].isspace(): pos += 1
        if text[pos] in ']}': return result
        begin = pos
        key = None
        if is_object:
            key, pos = DECODER.raw_decode(text, pos)
            while text[pos].isspace() or text[pos] == ':': pos += 1
        value, end = DECODER.raw_decode(text, pos)
        result.append((key, value, begin, end, pos))
        pos = end
        while text[pos].isspace(): pos += 1
        if text[pos] == ',': pos += 1
def reorder(text, path, key):
    start = 0
    for part in path:
        start = next(row[4] for row in children(text, start) if row[0] == part)
    rows = children(text, start)
    ordered = sorted(rows, key=lambda row: key(row[0], row[1]))
    if rows == ordered or len(rows) < 2: return text, False
    separator = text[rows[0][3]:rows[1][2]]
    replacement = separator.join(text[row[2]:row[3]] for row in ordered)
    return text[:rows[0][2]] + replacement + text[rows[-1][3]:], True
def targets():
    for name in ['stats', 'families', 'pokedex_sprites']:
        yield f'pokemon/{name}.json', (), lambda k, v: dex(k)
    for name in ['names', 'collection_game_forms', 'go_costumes']:
        yield f'pokemon/{name}.json', (), lambda k, v: v['dex']
    for name in ['current_moves', 'evolution_stages']:
        yield f'pokemon/{name}.json', (), lambda k, v: dex(v['name'])
    for name in ['pokedex_info', 'release_status']:
        yield f'pokemon/{name}.json', ('entries',), lambda k, v: dex(k)
    yield 'pokemon/evolutions.json', ('edges',), lambda k, v: dex(v['from'])
    yield 'pokemon/go_sprite_index.json', ('files',), lambda k, v: image_dex(k)
    yield 'pokemon/costume_names.json', ('labels',), lambda k, v: image_dex(k)
    yield 'catalogs/pokemon_types.json', (), lambda k, v: v['pokemon_id']
    yield 'catalogs/game_catalogs.json', ('legacyMoves',), lambda k, v: dex(k)
    yield 'catalogs/game_catalogs.json', ('adventureEffects',), lambda k, v: dex(v['pokemon'])
    yield 'pvp/master_iv_table.json', (), lambda k, v: dex(v['key'])
    yield 'raids/metadata.json', ('pokemon',), lambda k, v: v.get('dex') or dex(v['id'])
    yield 'raids/images.json', (), lambda k, v: dex(k)
    yield 'pokedex_icons/candy_colors.json', ('CandyColors',), lambda k, v: v['FamilyId']
    yield 'pokedex_icons/mega_colors.json', (), lambda k, v: int(k.split('_')[0])
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true')
    args = parser.parse_args()
    pending, errors = {}, []
    for filename, path, key in targets():
        text = pending.get(filename, read(filename))
        try:
            updated, changed = reorder(text, path, key)
            if changed:
                pending[filename] = updated
                print(f'Unsorted: {filename} {"/".join(path)}')
        except ValueError as error: errors.append(f'{filename}: {error}')
    if errors: raise SystemExit('\n'.join(errors))
    if args.write:
        for filename, text in pending.items():
            original = (ROOT / filename).read_bytes()
            if b'\r\n' in original: text = text.replace('\n', '\r\n')
            (ROOT / filename).write_bytes((b'\xef\xbb\xbf' if original.startswith(b'\xef\xbb\xbf') else b'') + text.encode('utf-8'))
    print(f'{len(pending)} files {"ordered" if args.write else "need ordering"}.')
    return 0 if args.write or not pending else 1
if __name__ == '__main__': raise SystemExit(main())
