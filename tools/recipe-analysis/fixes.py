import re
# What the model knows beyond the dataset: facts about the world, and what it leaves out on purpose. Gaps in the
# export itself belong in the exporter (gtnh-calc-oracle), not here.

# Left out: magic production the tiers don't follow. IC2 crops come without the blocks they need under
# them (an iron block for Ferru), so they'd be free metal; CropsNH crops carry theirs and count.
EXCLUDED_CATEGORIES = {'thaumcraft', 'ic2-crop'}

# The natural world is free: anything from these mods that no recipe makes (logs, vanilla drops, IC2 rubber trees,
# Twilight Forest loot, GT's programmed circuit). Anything else nothing makes (Galacticraft's workbench rockets, old machine ids) is
# unobtainable; counting it free would let the Arc Furnace recycle it into titanium.
NATURAL = ('minecraft:', 'ic2:blockrubwood', 'ic2:blockrubsapling', 'ic2:blockrubleaves', 'ic2:itemharz',
           'gregtech:gt.integrated_circuit', 'etfuturum:', 'twilightforest:')
# World drops and blocks some recipe also makes (a pearl from its dust): still free, pick them up. Water and lava are
# pumped from the world.
WORLD = {'minecraft:' + n for n in (
    'ender_pearl blaze_rod string bone slime_ball gunpowder leather feather spider_eye ghast_tear glowstone_dust '
    'redstone coal diamond emerald quartz log log2 sand gravel cobblestone stone dirt clay_ball clay snowball obsidian '
    'netherrack soul_sand end_stone reeds sapling wheat wheat_seeds egg wool rotten_flesh flint netherbrick nether_wart '
    'cactus pumpkin melon apple carrot potato dye vine waterlily red_flower yellow_flower brown_mushroom red_mushroom '
    'iron_ore gold_ore coal_ore diamond_ore redstone_ore lapis_ore emerald_ore quartz_ore ice packed_ice snow').split()}
WORLD |= {'water', 'minecraft:water', 'lava', 'minecraft:lava'}
# AE2's inscriber presses are in every meteorite's chest; the EV Laser Engraver only copies them
WORLD |= {f'appliedenergistics2:item.itemmultimaterial@{m}' for m in (13, 14, 15, 19)}

# Recycling a made thing back into its metal is not a way to make the metal: in these maps a recipe counts only when
# what it consumes is fluid, natural, or a material form (dust, ingot, plate, ore...).
RECYCLING_MAPS = {'Arc Furnace', 'Plasma Arc Furnace', 'Macerator', 'Pulverization', 'Fluid Extractor', 'Extractor'}
MATERIAL_FORM = re.compile(r'(Dust|Ingot|Nugget|Plate|Sheet|Rod|Bolt|Screw|Ring|Gear|Foil|Wire|Round|Spring|Rotor|Gem|Ore|'
                           r'Block|Crystal|Cell|Bar|Pulp|Shard|Chunk|Gravel|Sand|Log|Wood|Seeds?|Leaves|Sapling|Bone|Resin)$')

# Small ores are the overworld's only Topaz (its vein is on Callisto), and Topaz lenses engrave Simple SoCs, so NAND
# chips. They supply trace materials, never a tier's bulk metal (see tier.py's bulk state).
SMALL_ORES_ARE_SOURCES = True

# Mob drops (the dump has them only as EV Extreme Entity Crusher recipes, with the drops that need a weapon or
# enchantment named in its notes). By hand, a mob gives its common drops; rare and conditional ones are the chain's one
# chance step. A farm (spikes, plus the mob's world spawner or a Powered Spawner holding it in a Soul Vial) with
# looting makes every drop a steady supply. Summoned and magic mobs stay out, as Thaumcraft does; space mobs need
# their planet's rocket. GT++'s mobs (the Staballoy Construct, whose every kill drops a Staballoy Ingot) live in the
# Toxic Everglades; Witchery's biome brew could bring them to the overworld, but Witchery isn't modelled.
MOB_MAGIC = {'awwayoftime', 'thaumcraft', 'thaumichorizons', 'warptheory', 'automagy', 'thaumicexploration', 'witchery',
             'emt', 'botania'}
MOB_DIMS = {'galacticraftcore': 1, 'galacticraftmars': 2, 'miscutils': 'dimensionDarkWorld',
            # GalacticraftAmunRa's planets are tier 9
            'alienbug': 9, 'alienvillagerar': 9, 'porcodon': 9, 'robotvillager': 9, 'sentryrobot': 9}
DUNGEON_MOBS = {'zombie', 'skeleton', 'spider', 'cavespider', 'blaze', 'silverfish'}  # their spawners are in the world
POWERED_SPAWNER, SOUL_VIAL = 'enderio:blockpoweredspawner', 'enderio:itemsoulvessel'
SPIKES = ['extrautilities:spike_base', 'extrautilities:spike_base_gold', 'extrautilities:spike_base_diamond']
# Dimensions with no rocket tier, and what opens them. One not listed here is unreachable.
PORTALS = {'Overworld': [], 'Nether': [], 'The End': [], 'EndAsteroid': [], 'Twilight': [],
           'dimensionDarkWorld': ['toxiceverglades:blockdarkworldportalframe', 'toxiceverglades:everglades.trigger']}
# bosses the crusher can't hold: (inputs, drops)
SUMMONS = {'Wither': ([('minecraft:skull@1', 3), ('minecraft:soul_sand', 4)], ['minecraft:nether_star'])}

# Bees: the species at the roots of the breeding tree live in wild hives, which also drop a few combs. Breeding and steady produce need
# a housing. A mutation's foundation block, dimension or running GT machine gates it; climate, biome and time don't.
HOUSINGS = ['forestry:apiculture@2', 'forestry:apiculture', 'forestry:alveary', 'gregtech:gt.blockmachines@9399']
# the breeding conditions' names for dimensions the ore veins name otherwise
DIM_ALIASES = {'End': 'The End', 'IO': 'Io', 'Asteriods': 'Asteroids', 'Kuiper Belt': 'Kuiperbelt', 'tCeti E': 'TcetiE',
               'aCentauri': 'CentauriA', 'Barnard E': 'BarnardE', 'Barnard F': 'BarnardF', 'Barnarda C': 'BarnardC',
               'Barnarda E': 'BarnardE', 'Barnarda F': 'BarnardF', 'Deimos': 'Phobos', 'T Ceti E': 'TcetiE',
               'α Centauri Bb': 'CentauriA', 'Vega B': 'VegaB', 'Deep Dark': 'De'}

# Galacticraft's rockets come from the NASA Workbench, which the export doesn't cover. Rocket tier t reaches planets of
# tier t and needs the tier below; past T3 each tier just needs the last.
ROCKETS = {1: ['Tier 1 Rocket Engine', 'Nose Cone', 'Rocket Fins', 'Heavy Duty Plate'],
           2: ['Heavy Rocket Engine', 'Heavy Nose Cone', 'Heavy Rocket Fins'],
           3: ['Heavy Duty Rocket Engine Tier 3', 'Heavy Duty Plate Tier 5']}
MAX_ROCKET = 9

# EBF coils: heat, name. Heat grows 100 K per tier of the energy hatches' summed voltage above MV.
COILS = [(1801, 'Cupronickel Coil Block'), (2701, 'Kanthal Coil Block'), (3601, 'Nichrome Coil Block'),
         (4501, 'TPV-Alloy Coil Block'), (5401, 'HSS-G Coil Block'), (6301, 'HSS-S Coil Block'),
         (7201, 'Naquadah Coil Block'), (8101, 'Naquadah Alloy Coil Block'), (9001, 'Trinium Coil Block'),
         (9901, 'Electrum Flux Coil Block'), (10801, 'Awakened Draconium Coil Block'), (11701, 'Infinity Coil Block'),
         (12601, 'Hypogen Coil Block'), (13501, 'Eternal Coil Block')]
CLEANROOM = 'gregtech:gt.blockmachines@1172'

# The tier a circuit belongs to, by its name's tier: where players make and use it. LV to HV circuits are their own
# tier; HV's circuit assembler builds EV and IV ones, EV's LuV ones, and so on a tier below.
CIRCUIT_CLASS = {'ULV': 'ULV', 'LV': 'LV', 'MV': 'MV', 'HV': 'HV', 'EV': 'HV', 'IV': 'HV', 'LuV': 'EV', 'ZPM': 'IV',
                 'UV': 'LuV', 'UHV': 'ZPM', 'UEV': 'UV'}
