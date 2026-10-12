---
navigation:
  title: The wired network
  parent: index.md
  position: 80
  icon: becom:telephone
item_ids:
  - becom:telephone:*
  - becom:phone_cable:*
  - gregtech:gt.blockmachines:25000
  - gregtech:gt.blockmachines:25001
  - gregtech:gt.blockmachines:25002
  - gregtech:gt.blockmachines:25003
quest_ids:
  - "IHTr1EMRVx6vQQ7722eirw"
  - "b14IecXxXxuWU8oeGOPcNw"
  - "mXRjCMiFWsyGTG0Id2bbig"
  - "_OpArmItVHW8jB5_z18yig"
  - "_xekW5q3V6-D2yESxW4Bqw"
  - "EwUn7B5YXiqaZtu5vTGc2Q"
  - "ARn2PctkVOe0cqI7997r1Q"
---

# The wired network

The first tier, at LV: Telephones joined by lines on pylons, with no radio involved.

## Telephone and Phone Cable

<ItemLink id="becom:telephone" showIcon="left" /> is a block. It gets its six-digit number when you place it, and it's
yours. Right-click it to pick up the handset: if exactly one other phone is in reach it rings that one straight away;
otherwise the phone screen opens to dial. Walk more than about a block away, or right-click again, and you hang up.
Picking up a ringing Telephone answers it. Sneak and right-click to open its screen without lifting the handset, for
its settings and recent calls. A Telephone sounds narrow and gritty: its calls never go above Low.

<ItemLink id="becom:phone_cable" showIcon="left" /> joins it to a Line Terminal and loses nothing on the way. The phone
can sit up to 32 blocks of cable from the terminal, and a terminal takes at most 128 blocks of cable in all, branches
included.

## Line Terminal

<ItemLink id="gregtech:gt.blockmachines:25003" showIcon="left" /> is where a line starts and ends. It runs on 4 EU/t
of LV power and serves the one phone on its cable: with two phones on its cable it serves neither, and a phone on two
terminals' cables is served by neither. It has a single link: a Pylon Head, or an antenna, a dish, a relay or an ME
Bridge bridged onto it. A line needs a terminal at each end.

## Pylons

A <ItemLink id="gregtech:gt.blockmachines:25000" showIcon="left" /> sits on a column of 6 to 16 Steel Frame Boxes and
takes 2 links. Two pylons link up to 4 times the shorter one's height apart, 24 to 64 blocks, as long as nothing solid
is in the way. Every minute each link is looked at again, and one that something blocks goes down until it's clear.
Plain pylons need no power and carry the line through unloaded chunks.

When a Pylon Head forms, it links by itself if exactly one other formed Pylon Head in range has a free slot and a clear
line to it. Otherwise, link by hand: wrench one head, then the other. Wrench a linked pair again to unlink it. Anyone
can link into any network; only an owner of either end, their GT team or a server operator can unlink.

## Repeaters and Routers

A line loses 0.08 dB a block: it breaks up past 225 blocks from its last powered node and drops at 300.

- A <ItemLink id="gregtech:gt.blockmachines:25001" showIcon="left" /> is a Pylon Head that starts the count again. It
  takes 4 EU/t of LV power from a cable on any side.
- A <ItemLink id="gregtech:gt.blockmachines:25002" showIcon="left" /> also has 15 link slots and turns lines into a
  network, where any phone can dial any other. It takes 16 EU/t.

Unlike a plain pylon, a Repeater or a Router works only while it's loaded and powered, and one that isn't cuts the line
there.

## Numbers and contacts

Every phone has a six-digit number: a Telephone or a Videophone from when it's placed, a carried phone from when
someone first carries it. It belongs to whoever placed it, or first carried it. The phone screen has a keypad, contacts
listed by their owners' names, and recent and missed calls. Its settings hold do-not-disturb, hiding from contacts and
a block list, which only the owner or their GT team can change. A caller gets at most 5 attempts a minute.
