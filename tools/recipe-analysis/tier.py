import sys, re, heapq, pickle, runpy, collections
import fixes
# tier.py graph.pkl model.pkl [recipes.py:prefix ...] : the tier every item is made at, and the class of BeCom's.
#   made at  the lowest voltage tier that can make it: the max of its recipe's voltage, the machine that runs it,
#            the cleanroom, the rocket for its ore or low gravity, and every input's own tier. A multiblock runs a
#            recipe one tier above its energy hatches (GT ProcessingLogic maxTierSkips = 1), so Kanthal is MV.
#   depth    processing steps on the longest branch of that cheapest tree.
#   class    for recipe sets only: the highest tier named by what goes in (an MV circuit, a Titanium plate, an HV hull)
#            and by the recipe's voltage. A recipe made at MV can still be HV-class: that's normal GT progression.
# Every GT rule is an edge in one hypergraph: "run map M at voltage V" is a node of its own, reached through a
# single-block machine of tier >= V, or a controller plus hatches one tier lower (plus a coil, for EBF heat).
# Knuth's algorithm (Dijkstra for recipes with many inputs) settles each node once.
graph, out = sys.argv[1:3]
SETS = [x.split(':') for x in sys.argv[3:]]
G = pickle.load(open(graph, 'rb'))
TN, NAMES, ORE = G['tiers'], G['names'], G['ore']
TI = {t: i for i, t in enumerate(TN)}
BY_NAME = collections.defaultdict(list)
for i, n in NAMES.items():
    if n and not i.startswith('oredict:'):
        BY_NAME[n].append(i)
def ids_of(name):
    return BY_NAME.get(name, [])

# --- edges: (label, const tier, step, slots, outputs, slot amounts, output amounts, materials); a slot is alternative
# nodes. The first `materials` slots are what gets processed; the rest (machine, cleanroom, low gravity) gate the
# tier but add no processing steps to depth.
E = []
def edge(label, const, slots, outs, step=1, amts=None, outamts=None, materials=None):
    E.append((label, const, step, [tuple(s) for s in slots], list(outs), amts or [0] * len(slots), outamts or [1] * len(outs),
              len(slots) if materials is None else materials))

MAXEUT = collections.defaultdict(int)
for r in G['recipes']:
    MAXEUT[r[1]] = max(MAXEUT[r[1]], r[5])
COILS = [(h, ids_of(n)) for h, n in fixes.COILS]
HATCH = {TI[n.split()[0]]: ids[0] for n, ids in BY_NAME.items() if re.match(r'^(ULV|LV|MV|HV|EV|IV|LuV|ZPM|UV|UHV|UEV|UIV|UMV|UXV) Energy Hatch$', n)}

def runners(m):
    """who runs map m: ('single', {tier: item}) | ('multi', controller) | ('unknown', None)"""
    rs = []
    for label, kind, _ in G['handlers'].get(m, []):
        if kind == 'single':
            rs.append(('single', G['singles'][label]) if label in G['singles'] else ('unknown', None))
        else:
            c = [i for i in ids_of(label) if 'blockmachines' in i] or ids_of(label)
            if c: rs.append(('multi', c[0]))
    if not rs and G['icons'].get(m, '').startswith('gregtech:gt.blockmachines'):
        # one machine family runs this map, so the dataset gives no handlers: the icon is that machine
        icon = G['icons'][m]
        fam = next((f for f in G['singles'].values() if icon in f.values()), None)
        rs.append(('single', fam) if fam else ('multi', icon))
    return rs

RUN = {}
def run_node(m, v, heat):
    """the node 'map m can run a recipe of voltage v (and EBF heat)'"""
    key = (m, v, heat)
    if key in RUN: return RUN[key]
    node = f'run:{m}@{TN[v]}' + (f'/{heat}K' if heat else '')
    RUN[key] = node
    rs = runners(m)
    if not rs: edge(node, v, [], [node], 0)  # no machine data at all: assume one of the recipe's tier exists
    for kind, x in rs:
        if kind == 'unknown':
            edge(node, v, [], [node], 0)
        elif kind == 'single':
            for t, item in x.items():
                if t >= max(v, TI['LV']): edge(node, t, [[item]], [node], 0, materials=0)
        elif v == 0 or not MAXEUT[m]:
            edge(node, 0, [[x]], [node], 0, materials=0)  # unpowered (coke oven, bricked blast furnace): no hatch
        else:
            for h in range(max(v - 1, 1), len(TN) - 1):
                if h not in HATCH: continue
                # EBF heat: coil + 100 K per tier of the hatches' summed voltage above MV (GT's getMaxInputVoltage adds
                # them up), so two HV hatches count as EV
                coils = [i for c, ids in COILS if c + 100 * max(0, h + 1 - TI['MV']) >= heat for i in ids] if heat else None
                if coils == []: continue
                edge(node, h, [[x], [HATCH[h]]] + ([coils] if coils else []), [node], 0, materials=0)
    return node

# meta 32767 is Forge's wildcard: any damage of that item (a charged Lapotron Crystal is @26)
METAS = collections.defaultdict(list)
for i in NAMES:
    if '@' in i and not i.endswith('@32767'): METAS[i.rsplit('@', 1)[0]].append(i)
def slot(alts):
    out = [a for x in alts for a in [x] + ORE.get(x, [])]
    return out + [m for a in out if a.endswith('@32767') for m in [a[:-6]] + METAS[a[:-6]]]

TRE = '(ULV|LV|MV|HV|EV|IV|LuV|ZPM|UV|UHV)'
# a tier's hull material is that tier's: the plates its machine casing is made of (Aluminium MV, Titanium EV)
MAT = {}
NAME_ID = {i: n for n, ids in BY_NAME.items() for i in ids}
for rid, m, cat, kind, t, eut, sv, ins, outs, dims in G['recipes']:
    if cat != 'crafting' or len(outs) != 1: continue
    mc = re.match(TRE + r' Machine Casing$', NAMES.get(outs[0][0]) or '')
    if not mc: continue
    for a, _, _ in ins:
        for i in slot(a):
            pm = re.match(r'^(.+) Plate$', NAME_ID.get(i, ''))
            if pm:
                mat = re.escape(pm.group(1))
                form = re.compile(r'^(?:(?:Long|Small|Tiny|Fine|Double|Triple|Quadruple|Quintuple|Dense|Huge|Large) )?' + mat +
                                  r' (?:Plate|Rod|Ingot|Bolt|Screw|Ring|Gear|Foil|Frame Box|Round|Spring|Rotor|Fluid Pipe|Item Pipe)$')
                for n, ids in BY_NAME.items():
                    if form.match(n) or n == 'Molten ' + pm.group(1):
                        for i2 in ids: MAT[i2] = max(MAT.get(i2, 0), TI[mc.group(1)])

# A byproduct's byproduct is not a supply: an item's chain may hold at most one output that only comes by chance
# (gallium from Sphalerite, 9%, counts; rutile from Andradite from Calcite, 10% then 6%, doesn't). And a tier's
# material (MAT: Steel, Aluminium, Titanium...) is used by the stack, so it needs a bulk supply: no chance outputs, no
# sporadic or small ores. Titanium from the Dolomite vein's sporadic Andradite at 6% is a trickle, not a tier. So every
# item has three nodes: its own (at most one chance step), a clean one, '=' + id (none), and a bulk one, '#' + id (none,
# and only bulk ores). A recipe on bulk inputs gives bulk sure outputs; on clean inputs, clean sure outputs and its one
# chance step on the rest; on any inputs, sure outputs only. Tier materials come only from bulk recipes. Machines and
# the cleanroom are built once, so they take any.
CLEAN, BULK = '=', '#'
def recipe_edges(label, mats, extra, outs, amts, step=1):
    """mats: material slots; extra: machine/cleanroom slots; outs: [(id, amount, chance)]"""
    # one item in several chanced slots is still the product when it comes out more often than not (the Sifter's nine
    # 10% Rhodium Filter Cake slots)
    odds, amount = collections.defaultdict(float), collections.defaultdict(float)
    for o, n, c in outs:
        odds[o] += c; amount[o] += n * min(c, 1)
    sure = [(o, amount[o]) for o in odds if odds[o] >= 0.5]
    chanced = [(o, amount[o]) for o in odds if odds[o] < 0.5 and o not in MAT]
    amts = list(amts) + [0] * len(extra)
    if sure: edge(label, 0, [[BULK + a for a in s] for s in mats] + extra, [BULK + o for o, _ in sure], step, amts,
                  [n for _, n in sure], len(mats))
    sure = [(o, n) for o, n in sure if o not in MAT]
    if sure or chanced:
        edge(label, 0, [[CLEAN + a for a in s] for s in mats] + extra, [CLEAN + o for o, _ in sure] + [o for o, _ in chanced],
             step, amts, [n for _, n in sure + chanced], len(mats))
    if sure: edge(label, 0, mats + extra, [o for o, _ in sure], step, amts, [n for _, n in sure], len(mats))

HARVEST = {o for r in G['recipes'] if r[0] in G['crops'] for o, *_ in r[8]}
def raw_input(i):
    """what a recycling map may consume and still count: a fluid (no ':' in its id), a natural thing, a crop's
    harvest, a material form"""
    return ':' not in i or i.startswith(fixes.NATURAL) or i in HARVEST or bool(fixes.MATERIAL_FORM.search(NAMES.get(i) or ''))

# A CropsNH crop is a supply once planted. Breeding isn't modelled, so the seed is free; what gates it is the block it
# needs under it (placed, not used up; "titanium, ilmenite, or rutile" are alternatives), space ground (the Moon), the
# Industrial Farm, and a seed bed of the crop's tier. Light levels are free.
FARM = ids_of('Industrial Farm')
def crop_slots(rid):
    reqs, farm, bed = G['crops'][rid]
    slots = [FARM] if farm else []
    if bed > 0: slots.append([f'cropsnh:cropsnh.seedbed@{bed}'])
    for q in reqs:
        if 'from space' in q:
            slots.append(['dim:1'])
        elif q.startswith('Needs a block of'):
            words = re.split(r',\s*(?:or\s+)?|\s+or\s+', q[17:].removesuffix(' under it to grow'))
            slots.append([i for w in words for n in (f'Block of {w.title()}', f'{w.title()} Ore', f'{w.title()} Block', w.title())
                          for i in ids_of(n)])
        elif q.startswith('Needs a skull'):
            slots.append(['minecraft:skull'])
    return slots

# Bees (see fixes.HOUSINGS)
# A wild hive's species roots the breeding tree: no mutation makes it, some use it. A species neither made nor used
# (Magic Bees' compat species GTNH turned off, like TE Platinum) is creative-only.
BREEDS = {o for r in G['recipes'] if r[3] == 'bee_mutation' for o, *_ in r[8]}
HIVES = {a[0][0] for r in G['recipes'] if r[3] == 'bee_mutation' for a in r[7]} - BREEDS
LV_MACHINES = [f[TI['LV']] for f in G['singles'].values() if TI['LV'] in f]
def dim_node(name):
    name = fixes.DIM_ALIASES.get(name, name)
    t = G['dimtier'].get(name)
    return None if t is None else f'dim:{t or name}'
def breed_slots(rid):
    slots = []
    for q in G['breed'][rid]:
        if q.startswith('Required Dimension '):
            slots.append([dim_node(q[19:].strip()) or '?' + q])
        elif q.startswith('Requires ') and q.rstrip('.').endswith(' as a foundation'):
            block = q[9:].rstrip('.')[:-16]
            # a planet's own block (Callisto Surface Block, Enceladus Snow) means going there
            planet = next((dim_node(w) for w in sorted({*G['dimtier'], *fixes.DIM_ALIASES}, key=len, reverse=True)
                           if block.startswith(w + ' ') and dim_node(w)), None)
            slots.append(ids_of(block) + ([planet] if planet else []) or ['?' + block])
        elif q.startswith('Needs a running GT Machine'):
            slots.append(LV_MACHINES)
    return slots

skipped = collections.Counter()
for rid, m, cat, kind, tier, eut, sv, ins, outs, dims in G['recipes']:
    if cat in fixes.EXCLUDED_CATEGORIES or kind == 'odd':
        skipped['excluded'] += 1; continue
    if cat == 'enderio-machines':  # runs on the machine block; a Soul Binder's mob soul is a gate, not an ingredient
        souls = [list(a) for a, _, _ in ins if a[0].startswith('factoryflow:eec_mob:')]
        real = [(a, n) for a, n, _ in ins if not a[0].startswith('factoryflow:eec_mob:')]
        recipe_edges(rid, [slot(a) for a, _ in real], [[G['icons'][m]]] + souls, outs, [n for _, n in real])
        continue
    if cat == 'enderio-beheading':  # the mob is killed by hand with the sword: its skull is a chance drop
        recipe_edges(rid, [], [list(ins[1][0])], outs, [])
        continue
    if kind == 'bee_mutation':
        edge(rid, 0, [list(a) for a, _, _ in ins] + [fixes.HOUSINGS] + breed_slots(rid), [o for o, *_ in outs], 0, materials=0)
        continue
    if kind == 'bee_produce':
        bee = ins[0][0][0]
        edge(rid + ':apiary', 0, [[bee], fixes.HOUSINGS], [BULK + o for o, *_ in outs], 0, materials=0)
        if bee in HIVES:  # a wild hive's few combs
            edge(rid + ':hive', 0, [[bee]], [o for o, *_ in outs if o not in MAT], 0, materials=0)
        continue
    if rid in G['crops']:
        edge(rid, 0, crop_slots(rid), [BULK + o for o, *_ in outs], 0)
        continue
    if cat == 'kubatech-eec':
        mod = ins[0][0][0].split(':')[-1].split('-')[0]
        if mod not in fixes.MOB_MAGIC:  # the crusher recipe below stays as the EV way
            dims = [[f'dim:{fixes.MOB_DIMS[mod]}']] if mod in fixes.MOB_DIMS else []
            common = [o for o, n, c in outs if c >= 0.5]
            rare = [o for o, n, c in outs if 0 < c < 0.5] + [i for n in G['cond'].get(rid, ()) if len(ids_of(n)) <= 3
                                                             for i in ids_of(n)]
            drops = list(dict.fromkeys(common + rare))
            edge(rid + ':mob', 0, dims, [ins[0][0][0]], 0, materials=0)  # the mob itself, for a Soul Vial
            edge(rid + ':kill', 0, dims, [CLEAN + o for o in common] + [o for o in drops if o not in common and o not in MAT], 0)
            edge(rid + ':farm', 0, dims + [[fixes.POWERED_SPAWNER], [fixes.SOUL_VIAL], fixes.SPIKES], [BULK + o for o in drops],
                 0, materials=0)
            if mod in fixes.DUNGEON_MOBS:
                edge(rid + ':dungeon farm', 0, [fixes.SPIKES], [BULK + o for o in drops], 0, materials=0)
    if kind == 'small_ore' and not fixes.SMALL_ORES_ARE_SOURCES:
        skipped['small ore'] += 1; continue
    if kind in ('ore_vein', 'small_ore', 'underground_fluid'):
        # a vein's ores are a supply, its sporadic one too (Sphalerite is sporadic everywhere on the overworld, and
        # gallium, so every MV circuit, comes from it), but only its layers are bulk
        few = {o for o, *_ in outs} if kind == 'small_ore' else G['sporadic'].get(rid, set())
        if dims is not None: edge(rid, 0, [list(dims)], [(CLEAN if o in few else BULK) + o for o, *_ in outs], 0)
        continue
    if m in fixes.RECYCLING_MAPS and not all(raw_input(a[0]) for a, _, used in ins if used):
        skipped['recycling'] += 1; continue
    real = [(a, n) for a, n, _ in ins if not a[0].startswith('oredict:craftingTool')]
    # the voltage lives on the machine node (a multi runs it a tier lower), so the recipe itself adds no tier
    extra = [[run_node(m, tier, sv if m == 'Blast Furnace' and sv > 0 else 0)]]
    if sv in (-200, -300): extra.append([fixes.CLEANROOM])
    if sv in (-100, -300): extra.append(['dim:1'])
    recipe_edges(rid, [slot(a) for a, _ in real], extra, outs, [n for _, n in real])
for name, parts in fixes.PORTALS.items():
    edge(f'portal {name}', 0, [[p] for p in parts], [f'dim:{name}'], 0, materials=0)
for label, (ins, outs) in fixes.SUMMONS.items():
    recipe_edges(label, [[i] for i, _ in ins], [], [(o, 1, 1) for o in outs], [n for _, n in ins])
for t, parts in fixes.ROCKETS.items():
    edge(f'rocket T{t}', 0, [ids_of(p) or ['?' + p] for p in parts] + ([[f'dim:{t - 1}']] if t > 1 else []), [f'dim:{t}'])
for t in range(max(fixes.ROCKETS) + 1, fixes.MAX_ROCKET + 1):
    edge(f'rocket T{t}', 0, [[f'dim:{t - 1}']], [f'dim:{t}'])

# --- BeCom's recipe sets, judged by the same rules
REFS, SETREC = {}, {}
def resolve(pre, ref):
    if ref.startswith('becom:'): return [pre + ':' + ref[6:]]
    if ref.startswith('ore:'): return ORE['oredict:' + ref[4:]]
    if ref.startswith('id:'): return [ref[3:]]
    if not ids_of(ref): raise SystemExit('unknown ingredient ' + ref)
    return ids_of(ref)
for path, pre in SETS:
    F = runpy.run_path(path)
    # ASSEMBLER: a second recipe for a RECIPES item (a grid craft's Assembler alternative), its own edge
    sets = [(name, r, '') for name, r in F['RECIPES'].items()] + [(name, r, ' (Assembler)') for name, r in F.get('ASSEMBLER', {}).items()]
    for name, (mach, tier, eut, secs, n, ins), tag in sets:
        refs = list(ins) + F.get('EXTRA', {}).get(name + tag, [])
        m = 'Shaped Crafting' if mach == 'Crafting' else mach
        extra = [[run_node(m, TI[tier], 0)]] + ([[fixes.CLEANROOM]] if name in F.get('CLEANROOM', ()) else [])
        node = pre + ':' + name
        NAMES[node] = name
        SETREC[node + tag] = (m, TI[tier], refs, pre)
        recipe_edges(node + tag, [resolve(pre, r) for r, _ in refs], extra, [(node, n, 1)], [a for _, a in refs])

# --- class: the highest tier named by what a recipe-set recipe consumes, and by its voltage
CIRC = {'circuitPrimitive': 'ULV', 'circuitBasic': 'LV', 'circuitGood': 'MV', 'circuitAdvanced': 'HV', 'circuitData': 'EV',
        'circuitElite': 'IV', 'circuitMaster': 'LuV', 'circuitUltimate': 'ZPM', 'circuitSuperconductor': 'UV',
        'circuitInfinite': 'UHV', 'circuitBio': 'UEV'}
NOM = {}
for k, t in CIRC.items():
    for i in ORE.get('oredict:' + k, []): NOM[i] = TI[fixes.CIRCUIT_CLASS[t]]
for n, ids in BY_NAME.items():
    c = re.match(r'Any ' + TRE + r' Circuit$', n)
    m = re.search(r'\(' + TRE + r'\)$', n) or re.match(TRE + r' Machine (Hull|Casing)$', n)
    t = TI[fixes.CIRCUIT_CLASS[c.group(1)]] if c else TI[m.group(1)] if m else None
    if t is not None:
        for i in ids: NOM[i] = max(NOM.get(i, 0), t)
# --- Knuth: settle nodes in (tier, depth) order; an edge fires once every slot has a settled node
for i in {o[1:] for e in E for o in e[4] if o.startswith(BULK)}:
    edge('bulk', 0, [[BULK + i]], [CLEAN + i], 0, [1], [1], 1)  # a bulk supply is also a clean one
for i in {o[1:] for e in E for o in e[4] if o.startswith(CLEAN)}:
    edge('clean', 0, [[CLEAN + i]], [i], 0, [1], [1], 1)  # a clean supply is also a supply
produced = {o.lstrip(CLEAN + BULK) for e in E for o in e[4]}
free = {'dim:0'} | fixes.WORLD | {i for i in NAMES if i not in produced and i.startswith(fixes.NATURAL)}
free |= HIVES
free |= {p + i for i in free for p in (CLEAN, BULK)}
for i in list(NAMES): NAMES[CLEAN + i] = NAMES[BULK + i] = NAMES[i]
users = collections.defaultdict(list)
for k, (_, _, _, slots, *_) in enumerate(E):
    for s, alts in enumerate(slots):
        for a in alts: users[a].append((k, s))
left = [len(e[3]) for e in E]
done = [None] * len(E)
etier = [e[1] for e in E]
edepth = [0] * len(E)
heap = [(0, 0, i, -1) for i in free]
heap += [(e[1], e[2], o, k) for k, e in enumerate(E) if not e[3] for o in e[4]]
heapq.heapify(heap)
# Class follows the materials down the cheapest tree (not machines or the cleanroom): AE2's Wireless Terminal is LuV
# because its Dense Energy Cell takes LuV circuits. It's never below the tier the item is made at, and a recipe-set
# recipe's voltage counts too: it's part of the design. A machine, a hatch or the cleanroom works only from its class
# tier: the Large Chemical Reactor is built from HV parts at late MV, but it's HV's, so the Eye of Ender (its 480 EU/t
# recipe) is HV, not late MV on MV hatches. The EBF is LV's (Cupronickel coils), so MV hatches on it make Kanthal
# (an HV recipe) at late MV.
tier, depth, best, order, klass = {}, {}, {}, [], {}
while heap:
    t, d, node, k = heapq.heappop(heap)
    if node in tier: continue
    tier[node], depth[node], best[node] = t, d, k
    order.append(node)
    c = max(t, NOM.get(node.lstrip(CLEAN + BULK), 0), MAT.get(node.lstrip(CLEAN + BULK), 0))
    if k >= 0:
        for s_ in range(E[k][7]):
            c = max(c, klass.get(done[k][s_], 0))
        if E[k][0] in SETREC and SETREC[E[k][0]][0] != 'Shaped Crafting': c = max(c, SETREC[E[k][0]][1])
    klass[node] = c
    for k2, s in users.get(node, ()):
        if done[k2] is None: done[k2] = {}
        if s in done[k2]: continue
        done[k2][s] = node  # the first of a slot's alternatives to settle is the one it uses
        left[k2] -= 1
        etier[k2] = max(etier[k2], t if s < E[k2][7] else c)
        if s < E[k2][7]: edepth[k2] = max(edepth[k2], d)
        if left[k2] == 0:
            for o in E[k2][4]:
                if o not in tier: heapq.heappush(heap, (etier[k2], edepth[k2] + E[k2][2], o, k2))

MAPS = {r[0]: (r[1], r[4]) for r in G['recipes']}  # recipe id -> (map, voltage), for traces
CHOSEN = {k: [done[k][s] for s in range(len(E[k][3]))] for k in set(best.values()) if k >= 0}
pickle.dump({'TN': TN, 'names': NAMES, 'maps': MAPS, 'chosen': CHOSEN, 'setrec': {k: (v[0], v[1]) for k, v in SETREC.items()}, 'tier': tier, 'depth': depth, 'best': best, 'edges': E, 'nom': NOM, 'material': MAT, 'class': klass,
             'produced': produced}, open(out, 'wb'), protocol=pickle.HIGHEST_PROTOCOL)
print(f'{len(E)} edges, {len(tier)} settled, {len(RUN)} machine nodes, skipped {dict(skipped)}')
