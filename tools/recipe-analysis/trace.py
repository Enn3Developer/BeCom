import sys
from common import Model
# trace.py model.pkl [--all | --class] [--depth N] name... : how an item gets its tier. By default it follows only what
# sets the made-at tier (the inputs, machine or cleanroom at the item's own tier); --class follows what sets its class
# (materials only); --all shows the whole cheapest tree.
args = sys.argv[2:]
show_all = '--all' in args
by_class = '--class' in args
limit = int(args[args.index('--depth') + 1]) if '--depth' in args else 12
names = [a for k, a in enumerate(args) if a not in ('--all', '--class', '--depth') and (k == 0 or args[k - 1] != '--depth')]
M = Model(sys.argv[1])


def show(i, amount, d, seen):
    r = M.recipe(i)
    head = (f"{'  ' * d}{amount + 'x ' if amount else ''}{M.names.get(i) or i}  "
            f"[made {M.t(i)}, class {M.TN[M.klass.get(i, 0)]}, depth {M.depth.get(i, '-')}]")
    if r is None or i in seen or d >= limit:
        print(head); return
    label, mp, v, ins, _ = r
    if label.startswith('run:'):  # a machine node: name the machine it settled on, whatever its own tier
        print(head + ''.join(f'  <- {M.names.get(j) or j} [{M.t(j)}]' for j, _ in ins) if ins else head + '  <- no machine data')
        return
    print(head + (f'  <- {mp} {M.TN[v]}' if mp else f'  <- {label}'))
    seen = seen | {i}
    for n, (j, a) in enumerate(ins):
        if by_class:
            go = n < M.materials(i) and M.klass.get(j, 0) >= M.klass.get(i, 0) > M.nom.get(i, 0)
        else:
            go = show_all or M.tier.get(j, 0) >= M.tier[i] > 0
        if go: show(j, f'{a:g}' if a else '', d + 1, seen)


for q in names:
    show(M.find(q), '', 0, frozenset())
