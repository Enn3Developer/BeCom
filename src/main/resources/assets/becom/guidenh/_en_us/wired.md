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
  - becom:machine:25000
  - becom:machine:25001
  - becom:machine:25002
  - becom:machine:25003
  - becom:machine:25013
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

Its screen has a display, a keypad and four speed-dial keys, M1 to M4. Click a speed-dial key to dial the number stored
on it; right-click it to store the number typed on the keypad, or to empty it when nothing is typed. Only the owner,
their team or a server operator can store one. Under them, **Redial** calls the last number you called, **Calls** steps
through the recent calls, with an orange light for missed ones, and **Menu** steps through **Do not disturb**,
**Unlisted** and the blocked list. On a recent call, **Dial** calls it back and **C** twice blocks whoever was on the
other end; in the menu, **Dial** switches the setting shown and **C** unblocks the player shown.

There are three cables, and each carries its rate over a run up to its reach, and nothing past it:

| Cable | Tier | Rate | Longest run |
| --- | --- | --- | --- |
| <ItemLink id="becom:phone_cable:0" showIcon="left">Phone Cable</ItemLink> | LV | 16 KB/s | 128 blocks |
| <ItemLink id="becom:phone_cable:1" showIcon="left">Coaxial Cable</ItemLink> | MV | 64 KB/s | 512 blocks |
| <ItemLink id="becom:phone_cable:2" showIcon="left">Fibre Cable</ItemLink> | HV | 256 KB/s | 4096 blocks |

A voice call takes 2 KB/s, a video call 8 KB/s. Cables of any tier join each other, and a device's run is as good as
the poorest cable on its way.

## Router

A <ItemLink id="becom:machine:25003" showIcon="left">Router</ItemLink> is where a building's line
starts. It runs on 4 EU/t and serves every device on its cable, the nearest first, as many as it has ports: 4 for the
LV one, 8 for the <ItemLink id="becom:machine:25002" showIcon="left">Router (MV)</ItemLink>, 16 for
the <ItemLink id="becom:machine:25013" showIcon="left">Router (HV)</ItemLink>. Farther devices get
no line, and a device on two routers' cable takes the nearer one. When more devices want a line than it has ports,
WAILA says so in red, "9 devices on 8 ports", with "The farthest one gets no line." All its devices share its one link,
its uplink: a span to a pylon, or an antenna wired to it.

Right-click it for its screen: its uplink and how much of it calls take, and a row for each port, with the device on it,
its cable run and what it's doing. A device it can't serve says why: no free port, a cable run longer than its cable
reaches, or a nearer router. The Satellite Dish, the Satellite Launch Pad, the ME Bridge and the Portal Relay show a
status line on top, green when the machine works and red with the reason when it doesn't, then labelled rows, such as
their calls and backhaul. Anything wrong is listed under the rows, below a rule: in red, or in orange for a warning.

## Pylons and spans

A <ItemLink id="becom:machine:25000" showIcon="left">Pylon Head</ItemLink> tops a telephone pole: 6 to 16
Wood Frame Boxes, with a crossarm of 4 more right under the head. It takes 2 links. Two pylons link up to 4 times the
shorter one's height apart, 24 to 64 blocks, as long as nothing solid is in the way. Every minute each link is looked at
again, and one that something blocks goes down until it's clear. Pylons need no power and carry the line through
unloaded chunks.

To string a span, put cable in your hotbar, then wrench one node and the other: the first cable in your hotbar goes up,
one for every 4 blocks of span. Wrench the pair again with cable to add another: a span holds up to 4, and their rates
add up. Once it's full, a better cable swaps out the poorest one, which comes back to you. Wrench it with no cable in
your hotbar to unlink it, and every cable on it comes back. Breaking a pylon drops the cables of its spans.

A run is the wire between two powered nodes: routers, Exchanges, antennas and gateways. It carries what its thinnest
span does, counting only the cables whose reach covers the whole run. WAILA on a pylon shows its pole's height, each
span by its compass direction with its cables and length, and its run's length against the reach of the best cable
strung on every span: "Run 600 of 4096". Anyone
can link into any network; only an owner of either end, their team or a server operator can unlink. Teams are
ServerUtilities' teams, made with its My Team button beside the inventory: a team's owner, moderators and members
count, its allies don't. On a dedicated server, the "singleplayer" team a world first played alone keeps doesn't count,
since anyone there may join it. Without ServerUtilities, only the owner and operators can.

## Exchange

Pylon lines join two routers. To join more, wire them to an
<ItemLink id="becom:machine:25001" showIcon="left">Exchange</ItemLink>: a building of LV Machine Casings,
three blocks each way, with the controller in the middle of its front, an Energy Hatch and an Input Bus. It draws
64 EU/t and switches calls between up to 15 lines.

With no <ItemLink id="becom:switching_rack:0" showIcon="left">Switching Rack (LV)</ItemLink> it switches nothing. Put up
to 8 racks in its Input Bus: each LV rack switches 8 calls at once, MV 32, HV 128. When every way between two phones is
full, the next caller hears busy. Calls already going keep their way.

Right-click its controller for its own screen, in place of GT's terminal: whether it works, then its calls, "9 of 72",
with a bar, its data in KB/s over how many routes, and how many numbers it reaches through how many exchanges. A row of
8 slots shows its racks: a filled one with the rack's voltage and calls, a free one as a dashed "+". **Joined to** lists
every run of wire over pylons and everything on its own cable: antennas as "Antenna T3, backhaul", other Exchanges by
their coordinates, Routers by their compass direction, gateways by name, and numbered devices by kind and number.
Each row has the bundle's cables and a bar of what they carry, or in red why the run carries nothing:
"310 blocks: a Phone Cable run is 128 at most".

Unlike a pylon, an Exchange works only while it's loaded and powered.

## Numbers and contacts

Every phone has a six-digit number: a Telephone or a Videophone from when it's placed, a carried phone from when someone
first carries it. It belongs to whoever placed it, or first carried it. A Telephone's screen has a keypad, speed dial
and its recent and missed calls; a carried phone's also has contacts, listed by their owners' names. Their settings hold
do-not-disturb, hiding from contacts and a block list, which only the owner, their team or a server operator can change.
A caller gets at most 5 attempts a minute.
