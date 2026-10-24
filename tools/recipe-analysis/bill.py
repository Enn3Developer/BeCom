import sys, collections
from common import Model
# bill.py model.pkl name... : the tier-named parts (circuits, GT components, hulls) an item's cheapest tree consumes,
# per tier. It stands in for cost. Recipe-set items are 'prefix:Name' (new:Mobile Phone).
M = Model(sys.argv[1])


def bill(i, n, acc, stack):
    if i in M.nom and stack:
        acc[M.nom[i]] += n; return
    r = M.recipe(i)
    if r is None or i in stack or len(stack) > 40 or r[0].startswith('run:'): return
    label, mp, v, ins, out = r
    for j, a in ins[:M.materials(i)]:
        if a: bill(j, n * a / (out or 1), acc, stack + [i])


for q in sys.argv[2:]:
    i = M.find(q)
    acc = collections.Counter()
    bill(i, 1, acc, [])
    print(f"{q:32} made {M.t(i):5} " + '  '.join(f"{M.TN[t]}:{v:g}" for t, v in sorted(acc.items())))
