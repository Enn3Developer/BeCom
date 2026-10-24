import sys, runpy
from common import Model
# report.py model.pkl recipes.py:prefix... : each recipe-set item's class, the tier it's made at, and its depth.
# class   the highest tier named by what goes in and by the recipe's voltage: what tier the item belongs to
# made    the lowest voltage tier that can make it; usually class or one below (HV machines are built on MV power)
M = Model(sys.argv[1])
sets = [x.split(':') for x in sys.argv[2:]]
R = {pre: runpy.run_path(path)['RECIPES'] for path, pre in sets}
names = list(dict.fromkeys(n for pre in R for n in R[pre]))
def cell(pre, n):
    node = pre + ':' + n
    if n not in R[pre]: return '-'
    return f"{M.TN[M.klass.get(node, 0)]} / {M.t(node)} / {M.depth.get(node, '-')}"
print(f"{'item':24}" + ''.join(f"{pre + ' (class/made/depth)':>30}" for _, pre in sets))
for n in names:
    print(f"{n:24}" + ''.join(f"{cell(pre, n):>30}" for _, pre in sets))
