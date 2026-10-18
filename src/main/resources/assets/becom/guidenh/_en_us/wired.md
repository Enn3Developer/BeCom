---
navigation:
  title: The wired network
  parent: index.md
  position: 80
  icon: becom:telephone
item_ids:
  - becom:telephone:*
  - becom:phone_cable:*
  - becom:machine:25001
  - becom:machine:25002
  - becom:machine:25003
  - becom:machine:25013
  - becom:machine:25016
  - becom:machine:25017
  - becom:machine:25018
  - becom:machine:25019
  - becom:machine:25020
  - becom:machine:25021
quest_ids:
  - "IHTr1EMRVx6vQQ7722eirw"
  - "b14IecXxXxuWU8oeGOPcNw"
  - "ARn2PctkVOe0cqI7997r1Q"
  - "we4VyYt3UnGXhG2OVdCzHQ"
  - "_xekW5q3V6-D2yESxW4Bqw"
  - "EXDKXjgxXWGVm2xGizbPmw"
  - "Dd10sdc7U-eNKPKXD7E0Ig"
---

# The wired network

The first tier, from LV: Telephones on a building's cable, and cable between buildings. Wire is digital: it loses
nothing, rain or shine, and what it gives you is room for calls.

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

There are three cables. Each joins only cable of its own tier, and a bus of it carries its rate up to its reach, and
nothing past it:

| Cable | Tier | Rate | Longest bus |
| --- | --- | --- | --- |
| <ItemLink id="becom:phone_cable:0" showIcon="left">Phone Cable</ItemLink> | LV | 16 KB/s | 128 blocks |
| <ItemLink id="becom:phone_cable:1" showIcon="left">Coaxial Cable</ItemLink> | MV | 64 KB/s | 512 blocks |
| <ItemLink id="becom:phone_cable:2" showIcon="left">Fibre Cable</ItemLink> | HV | 256 KB/s | 4096 blocks |

A voice call takes 2 KB/s, a video call 8 KB/s. Cable joins like GT's: placed against a block, it joins that block, and
GT wire cutters open and close its sides. It opens a side only toward something that takes it, and water doesn't wash
it away.

## Buses

A bus is a run of cable of one tier, joined side to side, and everything it reaches: phones, routers and gateways. It
works only with a working router on it, and only up to its cable's longest bus; past that it carries nothing. Everything
on a bus shares its rate. A call between two phones on one bus crosses it twice, in and out of the router.

A router ends a bus: cable on another of its faces is another bus, counted from zero again. A bus between two routers
joins them, so calls cross from one to the other; that's how a network reaches the next building. Past what a bus of
Fibre reaches, calls go by radio, through antennas, or through an ME network.

## Router

A <ItemLink id="becom:machine:25003" showIcon="left">Router</ItemLink> routes the calls of every bus joining it. It
takes cable up to its tier, on any face: the LV one Phone Cable, 8 calls at once on 8 EU/t, the
<ItemLink id="becom:machine:25002" showIcon="left">Router (MV)</ItemLink> up to Coaxial, 16 calls on 32 EU/t, the
<ItemLink id="becom:machine:25013" showIcon="left">Router (HV)</ItemLink> up to Fibre, 32 calls on 128 EU/t.

Right-click it for its screen: the calls it carries, a row for each bus joining it, with its cable, its length against
the longest bus and what it carries, then what's on those buses: each phone by number with what it's doing, then the
routers and gateways. WAILA shows the same rows. The Satellite Dish, the Satellite Launch Pad, the ME Bridge and the
Portal Relay show a status line on top, green when the machine works and red with the reason when it doesn't, then
labelled rows, such as their calls and backhaul. Anything wrong is listed under the rows, below a rule: in red, or in
orange for a warning.

## Line Hatch

A multiblock joins buses through its Line Hatches:
<ItemLink id="becom:machine:25016" showIcon="left">Line Hatch (LV)</ItemLink>,
<ItemLink id="becom:machine:25017" showIcon="left">Line Hatch (MV)</ItemLink> and
<ItemLink id="becom:machine:25018" showIcon="left">Line Hatch (HV)</ItemLink>. Each takes one cable, on its front face,
up to its tier's, and joins that bus to the multiblock it's part of: an Exchange or an antenna as its router, a
Satellite Dish as its backhaul. Its lamp is lit while that multiblock works. Out of a formed one, it joins nothing.

## Exchange

An <ItemLink id="becom:machine:25001" showIcon="left">Exchange</ItemLink> is a big router: a hall of switching racks. It
switches calls between every bus its Line Hatches join.

The hall is 3 blocks wide, 5 tall and 5 to 19 deep:

- **A front cap** with the controller in the middle of its second row, and **a back cap**, each a wall of casings.
- **1 to 8 rack slices**, with a slice of frame posts before and after each. A rack slice's side walls take 4 racks,
  two on each side.
- **A cable ladder** of frame boxes along the ridge, over every slice.

Its tier is its casings': all LV, MV or HV Machine Casings, with Steel, Aluminium or Stainless Steel Frame Boxes to
match. A mixed hall doesn't form. Its racks and Line Hatches go up to that tier, so an MV hall takes LV and MV racks,
and cable up to Coaxial. Upgrading it means recasing it.

Energy Hatches and Line Hatches go on the caps, the roof and the floor; it needs at least one of each. The Structure
Projector builds it: its stack size, or the length channel, sets the slices, and the machine casing channel the tier.

**Switching Racks** are hatches on its side walls:
<ItemLink id="becom:machine:25019" showIcon="left">Switching Rack (LV)</ItemLink> switches 8 calls at once,
<ItemLink id="becom:machine:25020" showIcon="left">Switching Rack (MV)</ItemLink> 16 and
<ItemLink id="becom:machine:25021" showIcon="left">Switching Rack (HV)</ItemLink> 32. The hall switches as many calls as
its racks add up to; with none it switches nothing. Racks fill from the front, and a rack's lamps are lit while calls use
it, so you can see from outside how busy it is. When every way between two phones is full, the next caller hears busy.
Calls already going keep their way.

It draws for its length and its racks: 8 EU/t a slice in an LV hall, 32 in MV and 128 in HV, and 4, 16 or 64 EU/t for
each LV, MV or HV rack. An Energy Hatch below the hall's tier keeps it from forming. Each tier its lowest Energy Hatch
is above the hall's is a perfect overclock: four times the calls for four times the power.

Right-click its controller for its own screen, in place of GT's terminal: its tier and whether it works, then its calls,
"9 of 72", with a bar, its data in KB/s over how many routes, and how many numbers it reaches through how many
exchanges. Its racks show as the hall holds them, a column a slice, the left wall over the right: a free slot is a dashed
"+", and a rack's lamp is lit while it's in use. Then a row for each Line Hatch: what's on its bus, its length, its cable
and what it carries, or in red why it carries nothing. While it isn't formed, the screen says why.

## Numbers and contacts

Every phone has a six-digit number: a Telephone or a Videophone from when it's placed, a carried phone from when someone
first carries it. It belongs to whoever placed it, or first carried it. A Telephone's screen has a keypad, speed dial
and its recent and missed calls; a carried phone's also has contacts, listed by their owners' names. Their settings hold
do-not-disturb, hiding from contacts and a block list, which only the owner, their team or a server operator can change.
A caller gets at most 5 attempts a minute.

Teams are ServerUtilities' teams, made with its My Team button beside the inventory: a team's owner, moderators and
members count, its allies don't. On a dedicated server, the "singleplayer" team a world first played alone keeps doesn't
count, since anyone there may join it. Without ServerUtilities, only the owner and operators can change them.
