import sys, pickle, runpy, collections
# collide2.py graph.pkl recipes.py : proposed machine recipes vs the dataset's recipes in the same map, both ways: does
# one recipe's input set also satisfy the other's? Extra non-consumed inputs (lens) come from EXTRA in the recipes file,
# and every substitute fluid in SWAPS is checked as its own recipe.
G = pickle.load(open(sys.argv[1], 'rb'))
F = runpy.run_path(sys.argv[2])
R, X = F['RECIPES'], F.get('EXTRA', {})
ORE = G['ore']
BY_NAME = collections.defaultdict(set)
for i, n in G['names'].items():
    if n: BY_NAME[n].add(i)
BY_MAP = collections.defaultdict(list)
for rid, m, cat, kind, t, eut, sv, ins, outs, dims in G['recipes']:
    made = ', '.join(G['names'].get(o) or o for o, *_ in outs)
    BY_MAP[m].append((made, [(set(a) | {x for i in a for x in ORE.get(i, [])}, n) for a, n, _ in ins]))
# GT's machine recipes take circuits as an 'Any LV Circuit' slot
CIRC = {'ore:circuitBasic': 'dreamcraft:circuitlv', 'ore:circuitGood': 'dreamcraft:circuitmv',
        'ore:circuitAdvanced': 'dreamcraft:circuithv', 'ore:circuitData': 'dreamcraft:circuitev'}


def ids(ref):
    if ref.startswith(('becom:', 'id:')): return {ref.replace('id:', '')}
    if ref.startswith('ore:'): return {'oredict:' + ref[4:], CIRC.get(ref, '')} | set(ORE.get('oredict:' + ref[4:], []))
    if ref not in BY_NAME: raise SystemExit('no id for ' + ref)
    return BY_NAME[ref]


def covers(have, need):
    return all(any((h[0] & n[0]) and h[1] >= n[1] for h in have) for n in need)


SWAPS = F.get('SWAPS', {})


def variants(ins):
    """the recipe as written, and once more for each substitute of a fluid it takes (SWAPS)"""
    yield list(ins)
    for k, (r, a) in enumerate(ins):
        for sub, f in SWAPS.get(r, []):
            yield list(ins[:k]) + [(sub, max(1, int(a * f)))] + list(ins[k + 1:])


ALL = [(name, r) for name, r in R.items()] + [(name + ' (Assembler)', r) for name, r in F.get('ASSEMBLER', {}).items()]
for name, (mach, tier, eut, secs, n, ins) in ALL:
    if mach == 'Crafting': continue
    hits = []
    for v in variants(list(ins)):
        mine = [(ids(r), a) for r, a in v + X.get(name, [])]
        for made, theirs in BY_MAP[mach]:
            if covers(mine, theirs): hits.append('THEIRS fires on ours: ' + made)
            elif covers(theirs, mine): hits.append('OURS fires on theirs: ' + made)
    print(f"{'!!' if hits else 'ok'} {name} ({mach}, extra={X.get(name, [])})")
    for h in hits[:6]: print('     ' + h)
