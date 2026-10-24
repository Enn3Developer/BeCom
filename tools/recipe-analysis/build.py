import sys, json, gzip, re, pickle, collections
# build.py recipes.json.gz MetaTileEntityIDs.java graph.pkl : one pass over a GTNH dataset into a compact recipe graph.
#   names     id -> display name;  ore  'oredict:x' -> member ids
#   recipes   (rid, map, category, kind, tier, eut, special, inputs, outputs, dims)
#             inputs  ((slot id, *alternatives), amount, consumed);  outputs (id, amount, chance)
#             dims    the dimension nodes an ore vein is in ('dim:2' by rocket tier, 'dim:Nether' for a tier-0 one), else None
#   handlers  map -> [(label, kind, tiers)]: who runs it. A map with one machine family has none by design; its icon
#   icons     map -> icon item id                                    machine is that family.
#   crops     CropsNH recipe id -> (requirements text, only in the Industrial Farm, minimum seed bed tier)
#   sporadic  ore vein recipe id -> its sporadic ore ids (a few blocks scattered through the vein)
#   cond      Extreme Entity Crusher recipe id -> names of the mob's drops that need a weapon, enchantment or the like
#   breed     bee mutation recipe id -> its special conditions as the analyzer shows them
#   dimtier   dimension name (as the ore veins list it) -> rocket tier
#   singles   single-block label -> {tier: GT machine item}, from GT's MetaTileEntityIDs
dump, mte, out = sys.argv[1:4]
TIERS = ['NONE', 'ULV', 'LV', 'MV', 'HV', 'EV', 'IV', 'LuV', 'ZPM', 'UV', 'UHV', 'UEV', 'UIV', 'UMV', 'UXV', 'OpV', 'MAX']
TI = {t: i for i, t in enumerate(TIERS)}

names, ore, recipes, handlers, icons, crops, sporadic, cond, breed, dimtier = {}, {}, [], collections.defaultdict(dict), {}, {}, {}, {}, {}, {}
sec = None
with gzip.open(dump, 'rt') as f:
    for line in f:
        if line.startswith('  "'):
            sec = line[3:line.index('"', 3)]
            continue
        s = line.strip().rstrip(',')
        if sec not in ('resources', 'resourceIndex', 'recipeMapIcons', 'recipes') or not s.startswith('{'):
            continue
        r = json.loads(s)
        if sec in ('resources', 'resourceIndex'):
            names.setdefault(r['id'], r.get('displayName'))
            if r['id'].startswith('oredict:'):
                ore[r['id']] = [a['id'] for a in r.get('alternatives', [])]
        elif sec == 'recipeMapIcons':
            icons[r['recipeMap']] = r['resource']['id']
            names.setdefault(r['resource']['id'], r['resource'].get('displayName'))
        elif sec == 'recipes':
            if 'planner convenience' in ' '.join((r.get('nei') or {}).get('additionalInfo', [])):
                continue  # the planner's own shortcuts, not game recipes
            for h in r.get('machineHandlers', []):
                handlers[r['machineType']][h['id']] = (h['label'], h['kind'], tuple(h.get('availableTiers') or ()))
            for x in r['inputs'] + r['outputs']:
                names.setdefault(x['id'], x.get('displayName'))
                for a in x.get('alternatives', []):
                    names.setdefault(a['id'], a.get('displayName'))
            dims = None
            if r['kind'] in ('ore_vein', 'small_ore', 'underground_fluid'):
                ds = (r.get('metadata') or {}).get('dimensions', [])
                dimtier.update({d['name']: d.get('tier') or 0 for d in ds})
                dims = tuple(sorted({f"dim:{d.get('tier') or d['name']}" for d in ds})) or None
            layers = (r.get('metadata') or {}).get('oreLayers') or []
            if layers:
                sporadic[r['id']] = {l['resourceId'] for l in layers if l.get('role') == 'sporadic'}
            if r['category'] == 'kubatech-eec' and '):' in (r.get('notes') or ''):
                cond[r['id']] = tuple(n.strip() for n in r['notes'].split('):', 1)[1].rstrip('.').split(','))
            if r['kind'] == 'bee_mutation':
                breed[r['id']] = tuple((r.get('metadata') or {}).get('conditions') or ())
            c = (r.get('metadata') or {}).get('cropsNh')
            if c:
                crops[r['id']] = (tuple(c.get('requirements') or ()), bool(c.get('machineOnly')), c.get('minSeedBedTier') or 0)
            ins =tuple((tuple([x['id']] + [a['id'] for a in x.get('alternatives', [])]), x.get('amount') or 0,
                         x.get('consumed') is not False) for x in r['inputs'] if x['kind'] in ('item', 'fluid'))
            outs = tuple((x['id'], x.get('amount') or 0, x.get('chance') or 1) for x in r['outputs'] if x['kind'] in ('item', 'fluid'))

            odd = any(x['kind'] not in ('item', 'fluid') for x in r['inputs'])  # aspects, essentia: not modelled
            recipes.append((r['id'], r['machineType'], r['category'], 'odd' if odd else r['kind'], TI[r['minimumTier']],
                            r.get('eut') or 0, r.get('specialValue') or 0, ins, outs, dims))

# GT's single-block machines: the label NEI shows -> the machine item at each tier
by_name = {}
for i, n in names.items():
    if i.startswith('gregtech:gt.blockmachines@'):
        by_name[int(i.split('@')[1])] = n
ids = {m.group(1): int(m.group(2)) for m in re.finditer(r'^\s+([A-Z0-9_]+)\((\d+)\)', open(mte).read(), re.M)}
singles = {}
fam = collections.defaultdict(dict)
for k, v in ids.items():
    m = re.match(r'(.+)_(LV|MV|HV|EV|IV|LuV|LUV|ZPM|UV|UHV|UEV|UIV|UMV|UXV)$', k)
    if m:
        fam[m.group(1)][m.group(2).replace('LUV', 'LuV')] = v
for tiers in fam.values():
    first = tiers.get('LV') or tiers.get('MV') or tiers.get('HV')
    n = by_name.get(first)
    if n:  # NEI names the family after its lowest tier, minus the tier adjective
        label = re.sub(r'^(Basic|Advanced|Elite|Epic|Ultimate|Good|Super)\s+', '', n)
        singles.setdefault(label, {}).update({TI[t]: f'gregtech:gt.blockmachines@{i}' for t, i in tiers.items()})

pickle.dump({'tiers': TIERS, 'names': names, 'ore': ore, 'recipes': recipes, 'handlers': {k: list(v.values()) for k, v in handlers.items()},
             'icons': icons, 'singles': singles, 'crops': crops, 'sporadic': sporadic, 'cond': cond, 'breed': breed, 'dimtier': dimtier}, open(out, 'wb'), protocol=pickle.HIGHEST_PROTOCOL)
print(f'{len(recipes)} recipes, {len(names)} items, {len(handlers)} maps with handlers, {len(icons)} icons, {len(singles)} machine families')
