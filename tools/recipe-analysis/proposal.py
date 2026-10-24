# Proposed BeCom recipe tree. 'becom:X' are BeCom items (new components included). Order matters: parts first.
# Every input is what the thing is made of or the tier gate; where GT has the part (a monitor, a long rod, a circuit)
# the recipe takes GT's, no BeCom wrapper. AE2 opens in early EV, so every ME item is EV-class and runs at EV.
L, M, H, E = 'ore:circuitBasic', 'ore:circuitGood', 'ore:circuitAdvanced', 'ore:circuitData'
RUBBER, PLASTIC = 'Molten Rubber', 'Molten Polyethylene (PE)'
SOLDER = ('Molten Soldering Alloy', 72)
# GTNH's own substitutes: a recipe taking the first also comes in each of the rest at that fraction of the amount
# (cable insulation; GT's hatches for plastic; SubstituteFluidStack.soldering for solder)
SWAPS = {
    RUBBER: [('Molten Styrene-Butadiene Rubber (SBR)', 0.75), ('Molten Silicone Rubber', 0.5)],
    PLASTIC: [('Molten Polytetrafluoroethylene (PTFE)', 0.5), ('Molten Polybenzimidazole (PBI)', 0.0625)],
    'Molten Soldering Alloy': [('Molten Tin', 2), ('Molten Lead', 4)],
}
CA, AS, CR = 'Circuit Assembler', 'Assembler', 'Crafting'
RECIPES = {
 # --- cables: laid by the block over buses of 128 to 4096, so ×4 a craft and a quarter of a GT cable's insulation
 'Phone Cable':        (AS, 'LV', 30, 5, 4, [('Fine Copper Wire', 8), (RUBBER, 144)]),
 'Coaxial Cable':      (AS, 'MV', 120, 5, 4, [('Fine Annealed Copper Wire', 4), ('Aluminium Foil', 1), (PLASTIC, 144)]),
 'Fibre Cable':        (AS, 'HV', 480, 5, 4, [('Glass Fiber', 4), (PLASTIC, 144)]),
 # --- audio: a magnet, a coil and a diaphragm
 'Loudspeaker':        (AS, 'LV', 30, 10, 1, [('Magnetic Steel Rod', 1), ('Fine Copper Wire', 4), ('Steel Foil', 2)]),
 'Microphone':         (AS, 'LV', 30, 10, 1, [('Magnetic Steel Rod', 1), ('Fine Copper Wire', 2), ('Tin Foil', 1)]),
 'Handset':            (AS, 'LV', 30, 10, 1, [('becom:Loudspeaker', 1), ('becom:Microphone', 1), ('becom:Phone Cable', 1), (PLASTIC, 144)]),
 # --- RF modules: a tier's emitter and sensor on its board, the core of everything that transmits
 'RF Module LV':       (AS, 'LV', 30, 10, 1, [('Circuit Board', 1), ('Emitter (LV)', 1), ('Sensor (LV)', 1), SOLDER]),
 'RF Module MV':       (CA, 'LV', 30, 10, 1, [('Plastic Circuit Board', 1), ('Emitter (MV)', 1), ('Sensor (MV)', 1), SOLDER]),
 'RF Module HV':       (CA, 'MV', 120, 10, 1, [('Advanced Circuit Board', 1), ('Emitter (HV)', 1), ('Sensor (HV)', 1), SOLDER]),
 'RF Module EV':       (CA, 'HV', 480, 10, 1, [('More Advanced Circuit Board', 1), ('Emitter (EV)', 1), ('Sensor (EV)', 1), SOLDER]),
 # --- switching: relays, then logic, then packets
 'Crossbar Switch':    (AS, 'LV', 30, 10, 1, [('Circuit Board', 1), ('Small Steel Spring', 4), ('1x Red Alloy Wire', 4), (L, 1)]),
 'Switching Board':    (CA, 'LV', 30, 10, 1, [('Plastic Circuit Board', 1), ('id:gregtech:gt.metaitem.03@32037', 2), (M, 1), SOLDER]),
 'Packet Switch':      (CA, 'MV', 120, 10, 1, [('Advanced Circuit Board', 1), ('Simple SoC', 1), ('Random Access Memory Chip', 2), (H, 1), SOLDER]),
 # --- end products
 'Telephone':          (CR, 'NONE', 0, 0, 1, [('becom:Handset', 1), ('Note Block', 1), (L, 1), ('Steel Plate', 2), ('becom:Phone Cable', 1)]),
 'Router LV':          (CR, 'NONE', 0, 0, 1, [('becom:Crossbar Switch', 1), ('becom:Phone Cable', 2), (L, 2), ('LV Machine Hull', 1), ('1x Tin Cable', 2)]),
 'Router MV':          (CR, 'NONE', 0, 0, 1, [('becom:Switching Board', 1), ('becom:Coaxial Cable', 2), (M, 2), ('MV Machine Hull', 1), ('1x Copper Cable', 2)]),
 'Router HV':          (CR, 'NONE', 0, 0, 1, [('becom:Packet Switch', 1), ('becom:Fibre Cable', 2), (H, 2), ('HV Machine Hull', 1), ('1x Gold Cable', 2)]),
 # hatches like GT's: hull, the part, plastic growing with the tier
 'Line Hatch LV':      (AS, 'LV', 30, 10, 1, [('LV Machine Hull', 1), ('becom:Phone Cable', 2), (L, 1), (PLASTIC, 144)]),
 'Line Hatch MV':      (AS, 'MV', 120, 10, 1, [('MV Machine Hull', 1), ('becom:Coaxial Cable', 2), (M, 1), (PLASTIC, 288)]),
 'Line Hatch HV':      (AS, 'HV', 480, 10, 1, [('HV Machine Hull', 1), ('becom:Fibre Cable', 2), (H, 1), (PLASTIC, 432)]),
 'Switching Rack LV':  (AS, 'LV', 30, 20, 1, [('LV Machine Hull', 1), ('becom:Crossbar Switch', 2), ('Steel Plate', 4), ('becom:Phone Cable', 4)]),
 'Switching Rack MV':  (AS, 'MV', 120, 20, 1, [('MV Machine Hull', 1), ('becom:Switching Board', 2), ('Aluminium Plate', 4), ('becom:Coaxial Cable', 4)]),
 'Switching Rack HV':  (AS, 'HV', 480, 20, 1, [('HV Machine Hull', 1), ('becom:Packet Switch', 2), ('Stainless Steel Plate', 4), ('becom:Fibre Cable', 4)]),
 'Exchange':           (CR, 'NONE', 0, 0, 1, [(L, 4), ('becom:Crossbar Switch', 2), ('becom:Phone Cable', 2), ('LV Machine Hull', 1)]),
 'Antenna T1':         (CR, 'NONE', 0, 0, 1, [('Long Steel Rod', 2), ('becom:RF Module LV', 1), (L, 2), ('LV Machine Hull', 1), ('1x Tin Cable', 2), ('Steel Frame Box', 1)]),
 'Antenna T2':         (CR, 'NONE', 0, 0, 1, [('Long Aluminium Rod', 2), ('becom:RF Module MV', 1), (M, 2), ('MV Machine Hull', 1), ('1x Copper Cable', 2), ('Aluminium Frame Box', 1)]),
 'Antenna T3':         (CR, 'NONE', 0, 0, 1, [('Long Stainless Steel Rod', 2), ('becom:RF Module HV', 1), (H, 2), ('HV Machine Hull', 1), ('1x Gold Cable', 2), ('Stainless Steel Frame Box', 1)]),
 'Transceiver':        (CR, 'NONE', 0, 0, 1, [('becom:Loudspeaker', 1), ('becom:RF Module MV', 1), ('becom:Microphone', 1), (M, 2), ('MV Machine Hull', 1), ('1x Copper Cable', 2)]),
 'Videophone':         (CR, 'NONE', 0, 0, 1, [('Computer Monitor Cover', 1), ('becom:Handset', 1), ('Sensor (MV)', 1), (M, 2), ('MV Machine Hull', 1), ('becom:Phone Cable', 2)]),
 'App Store Mirror':   (CR, 'NONE', 0, 0, 1, [('Hard Disk Drive (Tier 1) (1MB)', 2), ('becom:Packet Switch', 1), ('Memory (Tier 1)', 2), ('HV Machine Hull', 1), (H, 1), ('becom:Fibre Cable', 2)]),
 'Signal Analyzer':    (AS, 'MV', 120, 20, 1, [('Sensor (MV)', 1), ('Long Aluminium Rod', 1), ('Computer Monitor Cover', 1), (M, 1), ('Aluminium Casing', 2)]),
 'Walkie-Talkie':      (AS, 'MV', 120, 20, 1, [('becom:RF Module MV', 1), ('becom:Loudspeaker', 1), ('becom:Microphone', 1), ('Medium Lithium Battery', 1), ('Aluminium Rod', 1), (PLASTIC, 144)]),
 'Mobile Phone':       (AS, 'HV', 480, 30, 1, [('becom:RF Module HV', 1), ('Computer Monitor Cover', 1), ('becom:Loudspeaker', 1), ('becom:Microphone', 1), (H, 1), ('Large Lithium Battery', 1), (PLASTIC, 144)]),
 # ME Terminal + Wireless Receiver, not AE2's Wireless Terminal: the phone has a battery, the terminal's Dense Energy
 # Cell would cost 15 HV circuits
 'ME Phone':           (AS, 'EV', 1920, 30, 1, [('becom:Mobile Phone', 1), ('ME Terminal', 1), ('id:appliedenergistics2:item.itemmultimaterial@41', 1), ('becom:RF Module EV', 1), (E, 1)]),
 'Satellite':          (AS, 'HV', 480, 40, 1, [('becom:RF Module HV', 1), (H, 2), ('id:gregtech:gt.metaitem.01@32750', 2), ('Long Stainless Steel Rod', 2), ('Stainless Steel Frame Box', 1)]),
 'Satellite Rocket':   (AS, 'HV', 480, 60, 1, [('becom:Satellite', 1), ('Nose Cone', 1), ('Tier 1 Rocket Engine', 1), ('Rocket Fins', 4), ('id:galacticraftcore:item.heavyplating', 4)]),
 'Rocket Radio Receiver': (AS, 'HV', 480, 30, 1, [('becom:RF Module HV', 1), ('Long Stainless Steel Rod', 1), (H, 1), ('Stainless Steel Plate', 4)]),
 'Launch Pad':         (CR, 'NONE', 0, 0, 1, [('Electric Pump (HV)', 2), ('Robot Arm (HV)', 1), (H, 2), ('HV Machine Hull', 1), ('becom:RF Module HV', 1), ('1x Gold Cable', 2)]),
 'Portal Relay MV':    (CR, 'NONE', 0, 0, 1, [('Ender Pearl', 2), ('becom:RF Module MV', 1), ('becom:Switching Board', 1), (M, 2), ('MV Machine Hull', 1), ('1x Copper Cable', 2)]),
 'Portal Relay HV':    (CR, 'NONE', 0, 0, 1, [('Eye of Ender', 2), ('becom:RF Module HV', 1), ('becom:Packet Switch', 1), (H, 2), ('HV Machine Hull', 1), ('1x Gold Cable', 2)]),
 'Portal Relay EV':    (CR, 'NONE', 0, 0, 1, [('Quantum Eye', 2), ('becom:RF Module EV', 1), ('becom:Packet Switch', 1), (E, 2), ('EV Machine Hull', 1), ('1x Aluminium Cable', 2)]),
 'Satellite Dish':     (CR, 'NONE', 0, 0, 1, [('Stainless Steel Plate', 3), ('becom:RF Module HV', 1), (H, 2), ('HV Machine Hull', 1), ('1x Gold Cable', 2)]),
 'ME Bridge':          (AS, 'EV', 1920, 30, 1, [('EV Machine Hull', 1), ('ME Wireless Access Point', 1), ('becom:RF Module EV', 1), (E, 2), ('ME Glass Cable - Fluix', 2)]),
 # cards like OC's own: Card Base, a microchip, the part, solder (its Network Card adds 8 Electrum Bolt)
 'BeCom Card Radio':   (CA, 'MV', 120, 15, 1, [('Card Base', 1), ('becom:RF Module MV', 1), ('Microchip (Tier 1)', 1), SOLDER]),
 'BeCom Card Wired':   (CA, 'MV', 120, 15, 1, [('Card Base', 1), ('becom:Coaxial Cable', 2), ('Microchip (Tier 1)', 1), ('Electrum Bolt', 8), SOLDER]),
 'BeCom Card ME':      (CA, 'EV', 1920, 15, 1, [('becom:BeCom Card Radio', 1), ('id:appliedenergistics2:item.itemmultimaterial@41', 1), ('Engineering Processor', 1), ('Titanium Foil', 4), SOLDER]),
 'ME Mirror':          (AS, 'EV', 1920, 30, 1, [('becom:App Store Mirror', 1), ('ME Drive', 1), (E, 2), ('ME Glass Cable - Fluix', 2)]),
}
# Every grid craft also has an Assembler recipe at its class voltage, a little cheaper: half the circuits, GT cables,
# plates and BeCom cables (at least 1), the parts that do the work unchanged
VOLTS = {'LV': 30, 'MV': 120, 'HV': 480, 'EV': 1920}
CLASS = {'Telephone': 'LV', 'Router LV': 'LV', 'Router MV': 'MV', 'Router HV': 'HV', 'Exchange': 'LV',
         'Antenna T1': 'LV', 'Antenna T2': 'MV', 'Antenna T3': 'HV', 'Transceiver': 'MV', 'Videophone': 'MV',
         'App Store Mirror': 'HV', 'Launch Pad': 'HV', 'Portal Relay MV': 'MV', 'Portal Relay HV': 'HV',
         'Portal Relay EV': 'EV', 'Satellite Dish': 'HV'}


def cheaper(ins):
    bulk = lambda r: r.startswith('ore:circuit') or r.endswith((' Cable', ' Plate'))
    return [(r, max(1, a // 2) if bulk(r) else a) for r, a in ins]


assert all(sum(a for _, a in r[5]) <= 9 for r in RECIPES.values() if r[0] == CR), 'a grid craft takes at most 9'
assert set(CLASS) == {n for n, r in RECIPES.items() if r[0] == CR}, 'every grid craft needs its Assembler twin'
ASSEMBLER = {n: (AS, t, VOLTS[t], 10, 1, cheaper(RECIPES[n][5])) for n, t in CLASS.items()}
# GT's way out of a collision: a Programmed Circuit (not used up). Fibre: else it steals GT's Optical Fiber Cable;
# RF Module HV: else it steals OC's Wireless Network Card (Tier 2)
EXTRA = {'Fibre Cable': [('id:gregtech:gt.integrated_circuit@2', 1)], 'RF Module HV': [('id:gregtech:gt.integrated_circuit@2', 1)]}
CLEANROOM = set()
