---
navigation:
  title: Antennas
  parent: index.md
  position: 75
  icon: becom:machine:25004
item_ids:
  - becom:machine:25004
  - becom:machine:25005
  - becom:machine:25006
quest_ids:
  - "UYdKN4lyUKWOiqsyX6luCQ"
  - "BRsUHvwqVhSei0mx4y1eWg"
  - "fia8emGdXIqzunSxVhLBng"
---

# Antennas

An antenna serves every radio and phone in its reach, ME Phones in their ME network's range aside, repeats all 16 radio channels, links to other
antennas, and routes the buses cabled to its Line Hatches.

## Building one

An antenna is built like a real radio mast. The head tops a mast of frame boxes standing on a pad of machine casings,
with antennas hanging under the head and an equipment shelter in front of the pad, where the energy hatch goes. T1 and
T2 are guyed masts, held up by three guys of frame boxes running down to casing anchors at the back and sides; T3 is a
self-supporting lattice tower. The higher the head, the further it sees: a tall mast on a hill covers far more than the
same antenna in a valley. NEI's preview shows each one block by block.

| Antenna | Head | Build | Casings | Mast | Power | Calls | Reach on open ground |
| --- | --- | --- | --- | --- | --- | --- | --- |
| T1 (late LV) | <ItemImage id="becom:machine:25004">Antenna T1 Head</ItemImage> | Guyed mast, one dipole | LV Machine Casings | 6 to 32 Steel Frame Boxes | 24 EU/t | 4 | about 64 blocks |
| T2 (mid MV) | <ItemImage id="becom:machine:25005">Antenna T2 Head</ItemImage> | Taller guyed mast, two crossed dipoles | MV Machine Casings | 8 to 32 Aluminium Frame Boxes | 96 EU/t | 8 | about 128 blocks |
| T3 (mid HV) | <ItemImage id="becom:machine:25006">Antenna T3 Head</ItemImage> | Lattice tower, panel antennas | HV Machine Casings | 10 to 32 Stainless Steel Frame Boxes | 384 EU/t | 16 | about 256 blocks |

An antenna works only while it's loaded and powered. An Energy Hatch below its casings' tier keeps it from forming.
Each tier its lowest Energy Hatch is above them is an overclock: twice the calls for four times the power, with the same
reach.

## What it does

- **Serves devices** in its reach on both bands: walkie-talkies on the radio band, phones on the phone band.
- **Links to other antennas** wherever their signal reaches at least Low, separately for each band: radio channels
  travel between antennas on the radio band, calls on the phone band.
- **Routes buses:** through the Line Hatches in its shelter, it's the router of every bus they join. A Telephone
  cabled to one gets its calls through the antenna, and a Router cabled to one joins its buses to the antenna: their
  calls go over the air, to every phone the antennas reach.

## Its screen

Right-click the head for its screen: the antennas it links to and the buses its Line Hatches join, and a coverage map of
the signal a player standing on the ground would get around it, for radios or for calls. The owner, their team or a
server operator can lock it to the owner's team: it then serves only that team's devices, but still carries calls
along its links. Teams are ServerUtilities' teams, made with its My Team button beside the inventory. A team's allies
aren't part of it, and on a dedicated server the "singleplayer" team doesn't count.

An antenna maps the land around it when it forms, and again every 5 minutes. A new building in the way shows up at
the next look.
