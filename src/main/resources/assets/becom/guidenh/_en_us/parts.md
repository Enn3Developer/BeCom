---
navigation:
  title: Parts and recipes
  parent: index.md
  position: 85
  icon: becom:part:3
item_ids:
  - becom:part:*
---

# Parts and recipes

BeCom crafts the way GregTech does:

- **Parts** come from the Assembler, or from the Circuit Assembler a voltage tier below the part's own.
- **What you place** is crafted in the grid around a machine hull of its tier. The Assembler makes it too, at its
  tier's voltage, and a little cheaper: half the circuits, GT cables, plates and BeCom cables.
- **Handhelds, Line Hatches, Switching Racks and everything ME** come from the Assembler only.

Fluids take GTNH's own substitutes, and NEI shows each alternative:

| Fluid | Any one of |
| --- | --- |
| Plastic | Polyethylene; Polytetrafluoroethylene at half; Polybenzimidazole at a sixteenth |
| Rubber | Rubber; Styrene-Butadiene Rubber at three quarters; Silicone Rubber at half |
| Solder | Soldering Alloy; Tin at twice; Lead at four times |

## Cable

Cable is laid a block at a time, over buses of up to 128, 512 and 4096 blocks. So each craft gives four, with a quarter
of the insulation a GT cable takes.

| Cable | Assembler | Four take |
| --- | --- | --- |
| <ItemLink id="becom:phone_cable:0" showIcon="left">Phone Cable</ItemLink> | LV | 8 Fine Copper Wire, 144 L of rubber |
| <ItemLink id="becom:phone_cable:1" showIcon="left">Coaxial Cable</ItemLink> | MV | 4 Fine Annealed Copper Wire, an Aluminium Foil, 144 L of plastic |
| <ItemLink id="becom:phone_cable:2" showIcon="left">Fibre Cable</ItemLink> | HV | 4 Glass Fiber, 144 L of plastic |

## Audio

A <ItemLink id="becom:part:0" showIcon="left">Loudspeaker</ItemLink> and a
<ItemLink id="becom:part:1" showIcon="left">Microphone</ItemLink> are each a magnet, a coil and a diaphragm. Together on
a cord, in a plastic shell, they make a <ItemLink id="becom:part:2" showIcon="left">Handset</ItemLink>. The Handset goes
into the Telephone and the Videophone. The Loudspeaker and the Microphone also go into the Walkie-Talkie, the
Transceiver and the Mobile Phone.

<RecipeFor id="becom:part:0" />
<RecipeFor id="becom:part:1" />
<RecipeFor id="becom:part:2" />

## RF Modules

An RF Module is a tier's emitter and sensor on its circuit board: the core of everything that transmits.

| Module | Goes into |
| --- | --- |
| <ItemLink id="becom:part:3" showIcon="left">RF Module (LV)</ItemLink> | Antenna T1 |
| <ItemLink id="becom:part:4" showIcon="left">RF Module (MV)</ItemLink> | Antenna T2, Transceiver, Walkie-Talkie, Portal Relay, BeCom Card (Radio) |
| <ItemLink id="becom:part:5" showIcon="left">RF Module (HV)</ItemLink> | Antenna T3, Mobile Phone, Portal Relay (HV), Satellite, Rocket Radio Receiver, Satellite Launch Pad, Satellite Dish |
| <ItemLink id="becom:part:6" showIcon="left">RF Module (EV)</ItemLink> | ME Phone, ME Bridge, Portal Relay (EV) |

<RecipeFor id="becom:part:3" />
<RecipeFor id="becom:part:4" />
<RecipeFor id="becom:part:5" />
<RecipeFor id="becom:part:6" />

## Switching

Each tier switches its own way.

| Part | Tier | Goes into |
| --- | --- | --- |
| <ItemLink id="becom:part:7" showIcon="left">Crossbar Switch</ItemLink>: relays, bars across bars | LV | Router, Switching Rack (LV), Exchange |
| <ItemLink id="becom:part:8" showIcon="left">Switching Board</ItemLink>: logic chips | MV | Router (MV), Switching Rack (MV), Portal Relay |
| <ItemLink id="becom:part:9" showIcon="left">Packet Switch</ItemLink>: a system on a chip and its memory | HV | Router (HV), Switching Rack (HV), Portal Relay (HV and EV), App Store Mirror |

<RecipeFor id="becom:part:7" />
<RecipeFor id="becom:part:8" />
<RecipeFor id="becom:part:9" />
