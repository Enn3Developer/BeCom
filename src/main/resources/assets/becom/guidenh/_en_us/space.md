---
navigation:
  title: Space and other dimensions
  parent: index.md
  position: 40
  icon: becom:satellite
item_ids:
  - becom:satellite:*
  - becom:satellite_rocket:*
  - becom:rocket_radio_receiver:*
  - gregtech:gt.blockmachines:25010
  - gregtech:gt.blockmachines:25011
  - gregtech:gt.blockmachines:25012
quest_ids:
  - "oNm-cIc3Xoy6hejGu6UM7Q"
  - "78j3bHKHWF6_v27ljQ8cYg"
  - "CW6Vyi4yXYClMf9my2HQKQ"
  - "J3UFmY7aUkyD5jAJI7lDBw"
  - "aEl8W97yVGeFRBy0p4O4ZA"
  - "iphLiVwwUKakj8QR4PH2-w"
  - "Ww6rBsobWMmbyc-M1rYILg"
---

# Space and other dimensions

## Satellites

A <ItemLink id="becom:satellite" showIcon="left">Satellite</ItemLink> goes up on a
<ItemLink id="becom:satellite_rocket" showIcon="left">Satellite Rocket</ItemLink>, from a
<ItemLink id="gregtech:gt.blockmachines:25010" showIcon="left">Satellite Launch Pad</ItemLink>: a 5 by 5 floor of Clean
Stainless Steel Machine Casings under open sky, with an input bus for rockets, an input hatch for rocket fuel, an energy
hatch and a maintenance hatch. A launch takes 30 s at 480 EU/t and 1,000 L of fuel, and puts one satellite over the
pad's dimension. Maintenance problems make it take longer, and any light-blocking block over the pad holds the launch
until it's clear; glass doesn't.

Satellites come down after about 100 Minecraft days, give or take a fifth: about 33 hours of the server running.
Sleeping through the night doesn't bring them down sooner. Keep launching. The pad's screen shows each dimension's
satellites and when the next comes down.

## Sky hops

With 5 satellites over a dimension, every Mobile Phone and radio under its open sky reaches them, at Low. ME Phones
don't use them. More satellites give a stronger signal:

| Satellites | Level |
| --- | --- |
| 5 | Low |
| 14 | Medium |
| 37 | High |
| 100 | Max, and no more past it |

A roof weakens a sky hop by its cover, rain by 3 dB and a thunderstorm by 6 where rain or snow falls, and underground
it's gone. With only 5 satellites the hop sits right at Low's edge, so any of these cuts it; 14 keep it at Medium.
Radios under open sky hear each other through the satellites, however far apart. Dimensions without a sky, like the
Nether, have none.

## Satellite Dish

A <ItemLink id="gregtech:gt.blockmachines:25011" showIcon="left">Satellite Dish</ItemLink> joins its network to every
other dish in its dimension, through the satellites, and in the Overworld to every planet with a Rocket Radio Receiver.
It's a 5 by 5 dish of Clean Stainless Steel Machine Casings on a stand of three Stainless Steel Frame Boxes, with an
energy hatch and a maintenance hatch, under open sky, on 512 EU/t. Wrench-link it to an antenna, a Pylon Head or a Line
Terminal. Rain at the dish costs its hop 3 dB, a storm 6, and each maintenance problem 2 dB.

## Rocket Radio Receiver

Put a <ItemLink id="becom:rocket_radio_receiver" showIcon="left">Rocket Radio Receiver</ItemLink> in the cargo of a
Galacticraft rocket standing on a Galacticraft launch pad, on a moon, a planet or a space station. The rocket becomes a
T1 antenna there, reaching about 64 blocks, and that world's link to the Overworld's satellites, at High. The rocket
needs chests to have cargo, and counts only while its chunk is loaded. A rocket you fly yourself arrives as an item in
its Parachest: place it on a pad and put the receiver in.

The link needs at least 5 satellites over the Overworld, and a call through it is no better than the Overworld end's
own hop: phones under the Overworld's open sky, and networks with a dish, reach the planet. Planets reach each other
the same way, through the Overworld, with a receiver on each. The receiver can't be wrench-linked: to bring a planet's
wired lines in, bridge them onto an antenna within reach of the rocket.

## Portal Relay

A <ItemLink id="gregtech:gt.blockmachines:25012" showIcon="left">Portal Relay</ItemLink> works within 4 blocks of a
Nether portal, an End portal, a Twilight Forest portal or a Personal Space portal, on 32 EU/t of MV power. Wrench-link
it to an antenna, a Pylon Head or a Line Terminal of your network. Then wrench it, go through the portal, and wrench a
relay on the other side within 5 minutes: the pair joins the two networks at High.

The two relays must stand on the two sides of one portal. Through the Nether, the far relay stands where the portal
leads, an eighth of the distance from the world's center; through the Twilight Forest, near the same spot. A Personal
Space relay in the Overworld must stand by a portal into the far relay's own dimension.

## Calls between dimensions

- The Overworld and a planet: through the satellites, to a Rocket Radio Receiver there.
- Two planets: through the Overworld's satellites, with a receiver on each.
- The Overworld and the Nether, the End, the Twilight Forest or a personal dimension: through a pair of Portal
  Relays.
- Longer chains combine these, from the Nether through the Overworld to a planet.

Every other dimension can't be reached yet. Radio channels stay in their own dimension.
