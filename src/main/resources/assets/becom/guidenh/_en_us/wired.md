---
navigation:
  title: The wired network
  parent: index.md
  position: 80
  icon: becom:telephone
item_ids:
  - becom:telephone:*
  - becom:phone_cable:*
  - becom:switching_rack:*
  - gregtech:gt.blockmachines:25000
  - gregtech:gt.blockmachines:25001
  - gregtech:gt.blockmachines:25002
  - gregtech:gt.blockmachines:25003
  - gregtech:gt.blockmachines:25013
quest_ids:
  - "IHTr1EMRVx6vQQ7722eirw"
  - "b14IecXxXxuWU8oeGOPcNw"
  - "mXRjCMiFWsyGTG0Id2bbig"
  - "_OpArmItVHW8jB5_z18yig"
  - "_xekW5q3V6-D2yESxW4Bqw"
  - "EXDKXjgxXWGVm2xGizbPmw"
  - "Dd10sdc7U-eNKPKXD7E0Ig"
---

# The wired network

The first tier, from LV: Telephones on a building's cable, and lines on pylons between buildings. Wire is digital: it
loses nothing, rain or shine, and what it gives you is room for calls.

## Telephone and cable

<ItemLink id="becom:telephone" showIcon="left">Telephone</ItemLink> is a block. It gets its six-digit number when you
place it, and it's yours. Right-click it to pick up the handset: if exactly one other phone is in reach it rings that
one straight away; otherwise the phone screen opens to dial. Walk more than about a block away, or right-click again,
and you hang up. Picking up a ringing Telephone answers it. Sneak and right-click to open its screen without lifting the
handset, for its settings and recent calls. A Telephone sounds narrow and gritty: its calls never go above Low.

There are three cables, and each carries its rate over a run up to its reach, and nothing past it:

| Cable | Tier | Rate | Longest run |
| --- | --- | --- | --- |
| <ItemLink id="becom:phone_cable:0" showIcon="left">Phone Cable</ItemLink> | LV | 16 KB/s | 128 blocks |
| <ItemLink id="becom:phone_cable:1" showIcon="left">Coaxial Cable</ItemLink> | MV | 64 KB/s | 512 blocks |
| <ItemLink id="becom:phone_cable:2" showIcon="left">Fibre Cable</ItemLink> | HV | 256 KB/s | 4096 blocks |

A voice call takes 2 KB/s, a video call 8 KB/s. Cables of any tier join each other, and a device's run is as good as
the poorest cable on its way.

## Line Terminal

A <ItemLink id="gregtech:gt.blockmachines:25003" showIcon="left">Line Terminal</ItemLink> is where a building's line
starts. It runs on 4 EU/t and serves every device on its cable, the nearest first, as many as it has ports: 4 for the
LV one, 8 for the <ItemLink id="gregtech:gt.blockmachines:25002" showIcon="left">Line Terminal (MV)</ItemLink>, 16 for
the <ItemLink id="gregtech:gt.blockmachines:25013" showIcon="left">Line Terminal (HV)</ItemLink>. Farther devices get
no line, and a device on two terminals' cable takes the nearer one. All its devices share its one link, its uplink: a
pylon, or an antenna, a dish, a relay or an ME Bridge wired to it.

Right-click it for its screen: its uplink and how much of it calls take, and a row for each port, with the device on it,
its cable run and what it's doing. A device it can't serve says why: no free port, a cable run longer than its cable
reaches, or a nearer terminal. The Exchange, the Satellite Dish, the ME Bridge and the Portal Relay show their calls and
backhaul the same way, with anything wrong in red on top.

## Pylons and spans

A <ItemLink id="gregtech:gt.blockmachines:25000" showIcon="left">Pylon Head</ItemLink> tops a telephone pole: 6 to 16
Wood Frame Boxes, with a crossarm of 4 more right under the head. It takes 2 links. Two pylons link up to 4 times the
shorter one's height apart, 24 to 64 blocks, as long as nothing solid is in the way. Every minute each link is looked at
again, and one that something blocks goes down until it's clear. Pylons need no power and carry the line through
unloaded chunks.

To string a span, put cable in your hotbar, then wrench one node and the other: the first cable in your hotbar goes up,
one for every 4 blocks of span. Wrench the pair again with cable to add another: a span holds up to 4, and their rates
add up. Once it's full, a better cable swaps out the poorest one, which comes back to you. Wrench it with no cable in
your hotbar to unlink it, and every cable on it comes back. Breaking a pylon drops the cables of its spans.

A run is the wire between two powered nodes: terminals, Exchanges, antennas and gateways. It carries what its thinnest
span does, counting only the cables whose reach covers the whole run. Anyone
can link into any network; only an owner of either end, their team or a server operator can unlink. Teams are
ServerUtilities' teams, made with its My Team button beside the inventory: a team's owner, moderators and members
count, its allies don't. On a dedicated server, the "singleplayer" team a world first played alone keeps doesn't count,
since anyone there may join it. Without ServerUtilities, only the owner and operators can.

## Exchange

Pylon lines join two terminals. To join more, wire them to an
<ItemLink id="gregtech:gt.blockmachines:25001" showIcon="left">Exchange</ItemLink>: a building of LV Machine Casings,
three blocks each way, with the controller in the middle of its front, an Energy Hatch and an Input Bus. It draws
64 EU/t and switches calls between up to 15 lines.

With no <ItemLink id="becom:switching_rack:0" showIcon="left">Switching Rack (LV)</ItemLink> it switches nothing. Put up
to 8 racks in its Input Bus: each LV rack switches 8 calls at once, MV 32, HV 128. When every way between two phones is
full, the next caller hears busy. Calls already going keep their way.

Unlike a pylon, an Exchange works only while it's loaded and powered.

## Numbers and contacts

Every phone has a six-digit number: a Telephone or a Videophone from when it's placed, a carried phone from when
someone first carries it. It belongs to whoever placed it, or first carried it. The phone screen has a keypad, contacts
listed by their owners' names, and recent and missed calls. Its settings hold do-not-disturb, hiding from contacts and
a block list, which only the owner, their team or a server operator can change. A caller gets at most 5 attempts a
minute.
