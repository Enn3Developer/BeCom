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
  - becom:machine:25010
  - becom:machine:25011
  - becom:machine:25012
  - becom:machine:25014
  - becom:machine:25015
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
<ItemLink id="becom:machine:25010" showIcon="left">Satellite Launch Pad</ItemLink>: a launch complex of Clean
Stainless Steel Machine Casings and Stainless Steel Frame Boxes. A 7 by 7 pad round a flame pit, the controller on its
front edge with the energy hatch and maintenance hatch beside it; a service tower behind the pad with an input bus for
rockets in its base and two swing arms reaching over the pad; a propellant tank beside it with an input hatch for
rocket fuel; and lightning masts on the pad's front corners. A launch takes 30 s at 480 EU/t and 1,000 L of fuel, and
puts one satellite over the pad's dimension. The rocket stands in the pit while the launch counts down, and lifts off
when it ends. Maintenance problems make it take longer, and any light-blocking block over the pit holds the launch until
it's clear; glass doesn't. Each tier its lowest energy hatch is above HV is an overclock: half the time for four times
the power. One below HV keeps it from forming.

Satellites come down after about 100 Minecraft days, give or take a fifth: about 33 hours of the server running.
Sleeping through the night doesn't bring them down sooner. Keep launching.

The pad's screen says **Launching**, with the seconds and a progress bar, **On hold** or **Ready**, then the rockets
waiting, the fuel in litres and how many launches that is, and each dimension's satellites, with their calls and when
the next comes down. Its **Orbit** tab, beside **Launches**, has a table of the skies: **Over**, **Up**, **Level**,
**Calls** and **Who's using it**: dishes, uplinks from receivers on other planets, and phones under open sky. Under it
is a forecast of the pad's own sky over the next 60 days, if nothing more goes up, with the count below which its level
drops, and a warning in red when it will: "Drops to Low in 29 days, when 3 come down together: the sky then carries 26
calls, not 36."

## Sky hops

With 5 satellites over a dimension, every Mobile Phone and radio under its open sky reaches them, at Low. ME Phones
use them only outside their ME network's range. More satellites give a stronger signal:

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

Each satellite carries 2 calls, shared by everything under that sky: phones, dishes and receivers alike. 18 satellites
carry 36 calls at once, and the next call is busy until one ends.

## Satellite Dish

A <ItemLink id="becom:machine:25011" showIcon="left">Satellite Dish</ItemLink> joins its network to every
other dish in its dimension, through the satellites, and in the Overworld to every planet with a Rocket Radio Receiver.
It's a ground station on 384 EU/t: a bowl of Clean Stainless Steel Machine Casings facing straight up, 7 across at the
rim, with a feed horn held over it by four struts of Stainless Steel Frame Boxes, on a pedestal. The controller stands
in a plinth at its foot, with the energy hatch, the maintenance hatch and one Line Hatch beside it. It needs open sky over the whole dish. Cable its Line
Hatch to a Router or an Exchange: that bus is its backhaul, and its cable's tier sets its rate. Rain at the dish costs its hop 3 dB, a storm 6, and each maintenance problem 2 dB.

A dish carries 32 calls at most, twice as many for each tier its lowest energy hatch is above HV at four times the
power, and never more than its hop's level has room for: a hop at Low carries 4 KB/s, room
for 2 calls, Medium 16 KB/s, High 64 and Max 256, at 2 KB/s a call.

Its screen shows its hop in dB, with signal bars and its level, and how many of its calls are in use. Then come the
satellites overhead and how many of the sky's calls are in use, **Sky** clear in green or covered in red,
**Maintenance** problems and the dB they cost, and its **Backhaul**.

## Rocket Radio Receiver

Put a <ItemLink id="becom:rocket_radio_receiver" showIcon="left">Rocket Radio Receiver</ItemLink> in the cargo of a
Galacticraft rocket standing on a Galacticraft launch pad, on a moon, a planet or a space station. The rocket becomes a
T1 antenna there, reaching about 64 blocks, and that world's link to the Overworld's satellites, at High. It carries
4 calls at once. The rocket
needs chests to have cargo, and counts only while its chunk is loaded. A rocket you fly yourself arrives as an item in
its Parachest: place it on a pad and put the receiver in.

The link needs at least 5 satellites over the Overworld, and a call through it is no better than the Overworld end's
own hop: phones under the Overworld's open sky, and networks with a dish, reach the planet. Planets reach each other
the same way, through the Overworld, with a receiver on each. The rocket takes no cable: to bring a planet's
wired network in, cable it to an antenna's Line Hatch within reach of the rocket.

## Portal Relay

A <ItemLink id="becom:machine:25012" showIcon="left">Portal Relay</ItemLink> works within 4 blocks of a
Nether portal, an End portal, a Twilight Forest portal or a Personal Space portal. Cable it, on any face, to a Router or an
Exchange of your network. Then go through the portal and place a relay by the other side, cabled to one there: the two pair by
themselves, and the pair joins the two networks at High. Its screen says **Unpaired** until they do.

It comes in three tiers, and a pair works at its lower relay's:

| Relay | Power | Calls | Rate |
| --- | --- | --- | --- |
| <ItemLink id="becom:machine:25012" showIcon="left">Portal Relay</ItemLink> | 32 EU/t, MV | 16 | 64 KB/s |
| <ItemLink id="becom:machine:25014" showIcon="left">Portal Relay (HV)</ItemLink> | 128 EU/t, HV | 32 | 128 KB/s |
| <ItemLink id="becom:machine:25015" showIcon="left">Portal Relay (EV)</ItemLink> | 512 EU/t, EV | 64 | 256 KB/s |

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
