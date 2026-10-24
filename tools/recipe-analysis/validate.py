import sys, pickle, collections
from common import Model
# validate.py model.pkl graph.pkl : checks the model against what's known about GTNH.
#  1. Facts players know (ANCHORS): the tier each item is made at, and its class.
#  2. Every part, hull and GT machine named for a tier (ULV-IV): one made above its own tier must be GTNH's design
#     (a next-tier part, a recipe voltage, a coil, the cleanroom). SUSPECT means the chain ends in none of those:
#     a model or data bug.
M = Model(sys.argv[1])
G = pickle.load(open(sys.argv[2], 'rb'))
TI = {t: i for i, t in enumerate(M.TN)}

ANCHORS = {  # item: (made at, class); None = not claimed
    # a coil is made late in the tier it unlocks, and is that tier's; the EBF is LV's, on Cupronickel
    'Electric Blast Furnace': (None, 'LV'), 'Cupronickel Coil Block': ('LV', 'LV'),
    'Kanthal Coil Block': ('MV', 'MV'), 'Nichrome Coil Block': ('HV', 'HV'), 'TPV-Alloy Coil Block': ('EV', 'EV'),
    'Eye of Ender': ('HV', 'HV'),              # an HV Chemical Reactor recipe, and the Large one is HV's too
    # a tier's material is made in the tier before and used through its own
    'Steel Ingot': ('ULV', 'LV'), 'Aluminium Ingot': ('LV', 'MV'), 'Stainless Steel Ingot': ('MV', 'HV'),
    'Titanium Ingot': ('HV', 'EV'),            # Moon ores (late HV), or the Staballoy line, which is a tier skip
    'Tungstensteel Ingot': ('EV', 'IV'),
    'Mainframe': ('HV', 'HV'),                 # HV crafts up to IV circuits, and they're HV's
    'Basic Circuit Assembler': (None, 'MV'),   # takes MV circuits
    'Cleanroom Controller': (None, 'HV'),
    'ME Controller': ('HV', 'EV'),             # AE2 opens in early EV: Titanium plates in an HV assembler
    'Vacuum Tube': ('LV', None),
}
fails = 0
for name, (made, cls) in ANCHORS.items():
    i = M.find(name)
    got_made, got_cls = M.t(i), M.TN[M.klass.get(i, 0)]
    bad = (made and got_made != made) or (cls and got_cls != cls)
    fails += bool(bad)
    print(f"{'FAIL' if bad else 'ok  '} {name:28} made {got_made:5} (want {made or '-':3})  class {got_cls:4} (want {cls or '-'})")

def reason(i, seen):
    """what puts i at its tier: a part named for that tier, a recipe or machine voltage, or the cleanroom"""
    if i in seen: return None
    seen.add(i)
    r = M.recipe(i)
    if r is None: return None
    label, mp, v, ins, _ = r
    t = M.tier[i]
    if label.startswith('run:'):
        return f'{label[4:]} needs a tier-{M.TN[t]} machine' if v == t else reason(ins[0][0], seen) if ins else None
    for j, _ in ins:
        if M.tier.get(j) == t and M.nom.get(j) == t and j != i: return 'needs ' + M.names.get(j, j)
    if j == 'gregtech:gt.blockmachines@1172' or any(j == 'gregtech:gt.blockmachines@1172' and M.tier.get(j) == t for j, _ in ins):
        return 'cleanroom for ' + M.names.get(i, i)
    if v == t and mp: return f'{mp} recipe voltage for {M.names.get(i, i)}'
    for j, _ in ins:
        if M.tier.get(j) == t:
            r2 = reason(j, seen)
            if r2: return r2
    return None

want = {i: t for i, t in M.nom.items() if t <= TI['IV']}
for fam in G['singles'].values():
    for t, i in fam.items():
        if t <= TI['IV']: want[i] = t
ok, off = collections.Counter(), collections.defaultdict(list)
for i, t in want.items():
    if i not in M.names: continue
    if M.tier.get(i, 99) > t: off[M.TN[t]].append(i)
    else: ok[M.TN[t]] += 1
print(f"\nTier-named parts: {sum(ok.values())} made at or below their tier, {sum(map(len, off.values()))} above")
for t in M.TN:
    for i in sorted(off.get(t, []), key=lambda i: M.names[i]):
        why = reason(i, set()) if i in M.tier else None
        print(f"  {t:>3} -> {M.t(i):5} {M.names[i]:42} {why if why is not None else 'SUSPECT'}")
print(f"\n{fails} anchor(s) failed")
