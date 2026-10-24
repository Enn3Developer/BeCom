import sys, collections
from common import Model
# never.py model.pkl name... : why an item can't be made. Follows the first input no recipe can supply down to what
# blocks it: something nothing makes, or something only excluded kinds make (Thaumcraft, bees, crops).
M = Model(sys.argv[1])
makers = collections.defaultdict(list)
for k, e in enumerate(M.edges):
    for o in e[4]: makers[o].append(k)


def blocker(i, seen):
    if i in M.tier or i in seen: return None
    seen.add(i)
    if not makers.get(i): return i
    for k in makers[i]:
        for alts in M.edges[k][3]:
            if not any(a in M.tier for a in alts):
                b = blocker(alts[0], seen)
                if b: return b
    return None


for q in sys.argv[2:]:
    i = M.find(q)
    if i in M.tier:
        print(f'{q}: made at {M.t(i)}'); continue
    b = blocker(i, set())
    print(f"{q}: blocked by {M.names.get(b) or b} ({'nothing in the dataset makes it' if b and not makers.get(b) else '?'})")
