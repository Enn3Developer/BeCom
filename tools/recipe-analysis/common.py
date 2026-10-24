import pickle, collections
# Loads model.pkl (from tier.py) for the report and trace scripts.


class Model:
    def __init__(self, path):
        P = pickle.load(open(path, 'rb'))
        self.TN, self.names, self.tier, self.depth, self.best = P['TN'], P['names'], P['tier'], P['depth'], P['best']
        self.edges, self.nom, self.klass, self.produced = P['edges'], P['nom'], P['class'], P['produced']
        self.maps, self.setrec, self.chosen = P['maps'], P['setrec'], P['chosen']
        self.by_name = collections.defaultdict(list)
        for i, n in self.names.items():
            if n: self.by_name[n].append(i)

    def find(self, name):
        """an item id from a display name ('prefix:Name' for recipe-set items), the made one when names collide"""
        if name in self.names and name not in self.by_name: return name
        ids = [i for i in self.by_name.get(name, []) if not i.startswith(('oredict:', '=', '#'))]
        if not ids: raise SystemExit('no item named ' + name)
        return min(ids, key=lambda i: (i not in self.tier, self.tier.get(i, 99)))

    def t(self, i):
        """an item's made-at tier name: NEVER when nothing makes it, raw when nothing needs to"""
        if i in self.tier: return self.TN[self.tier[i]]
        return 'NEVER' if i in self.produced else 'NEVER (raw, not free)'

    def materials(self, i):
        """how many of the slots of the edge that made i are materials (the rest: machine, cleanroom, low gravity)"""
        k = self.best.get(i, -1)
        return self.edges[k][7] if k >= 0 else 0

    def recipe(self, i):
        """(label, map, voltage, [(slot item, amount)], output amount) of the edge that made i, or None"""
        k = self.best.get(i, -1)
        if k < 0: return None
        label, const, step, slots, outs, amts, outamts, _ = self.edges[k]
        mp, v = self.maps.get(label) or self.setrec.get(label) or (None, const)
        return label, mp, v, list(zip(self.chosen[k], amts)), outamts[outs.index(i)] if i in outs else 1
