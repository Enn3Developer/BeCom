---
navigation:
  title: Antennas
  parent: index.md
  position: 75
  icon: gregtech:gt.blockmachines:25004
item_ids:
  - gregtech:gt.blockmachines:25004
  - gregtech:gt.blockmachines:25005
  - gregtech:gt.blockmachines:25006
quest_ids:
  - "UYdKN4lyUKWOiqsyX6luCQ"
  - "BRsUHvwqVhSei0mx4y1eWg"
  - "fia8emGdXIqzunSxVhLBng"
---

# Antennas

An antenna serves every radio and phone in its reach, ME Phones aside, repeats all 16 radio channels, links to other
antennas, and carries the lines bridged onto it.

## Building one

An antenna head sits on a mast of 4 to 32 frame boxes, over a 3 by 3 base of machine casings with an energy hatch in
it. The higher the head, the further it sees: a tall mast on a hill covers far more than the same antenna in a valley.

| Antenna | Head | Base | Mast | Power | Reach on open ground |
| --- | --- | --- | --- | --- | --- |
| T1 (late LV) | <ItemImage id="gregtech:gt.blockmachines:25004">Antenna T1 Head</ItemImage> | LV Machine Casings | Steel Frame Boxes | 32 EU/t | about 64 blocks |
| T2 (mid MV) | <ItemImage id="gregtech:gt.blockmachines:25005">Antenna T2 Head</ItemImage> | MV Machine Casings | Aluminium Frame Boxes | 128 EU/t | about 128 blocks |
| T3 (mid HV) | <ItemImage id="gregtech:gt.blockmachines:25006">Antenna T3 Head</ItemImage> | HV Machine Casings | Stainless Steel Frame Boxes | 512 EU/t | about 256 blocks |

An antenna works only while it's loaded and powered.

## What it does

- **Serves devices** in its reach on both bands: walkie-talkies on the radio band, phones on the phone band.
- **Links to other antennas** wherever their signal reaches at least Low, separately for each band: radio channels
  travel between antennas on the radio band, calls on the phone band.
- **Bridges lines:** wrench it, then a Pylon Head or Line Terminal within 16 blocks, and that line's calls go over
  the air, to every phone the antennas reach. An antenna has 15 link slots, shared by its bridges and any Satellite
  Dish, Portal Relay or ME Bridge linked onto it.

## Its screen

Right-click the head for its screen: the antennas it links to and the lines it bridges, and a coverage map of the
signal a player standing on the ground would get around it, for radios or for calls. The owner, their team or a
server operator can lock it to the owner's team: it then serves only that team's devices, but still carries calls
along its links. Teams are ServerUtilities' teams, made with its My Team button beside the inventory, and a team's
allies aren't part of it.

An antenna maps the land around it when it forms, and again every 5 minutes. A new building in the way shows up at
the next look.
