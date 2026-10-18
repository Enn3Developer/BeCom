---
navigation:
  title: Phones
  parent: index.md
  position: 50
  icon: becom:mobile_phone
item_ids:
  - becom:mobile_phone:*
  - becom:me_phone:*
  - becom:machine:25008
  - becom:machine:25009
quest_ids:
  - "Cs4eUVpnUBeevZzOvcInrw"
  - "1e_b8ZF0VRq1STROZstNIg"
  - "tmuzvF6QVE-ZigvcfAhM5Q"
  - "QTf-qLjuX5uncRhFsloOcg"
  - "SSBYl-CuVHeaS00Zgy5NWA"
  - "6JNs0PJ2W_2dWPcb8B5q0A"
  - "1FYiW9xFV5eNdWDcFL6cbw"
---

# Phones

Any phone can call any other phone the network has a path to. A call's level is its weakest hop, capped by both
phones. Calls take up to 8 people: anyone in one can add a phone from the keypad or contacts.

## Mobile Phone

<ItemLink id="becom:mobile_phone" showIcon="left">Mobile Phone</ItemLink> gets its service from antennas on the phone
band, about 64 blocks from a T1 on open ground, and from satellites under open sky ([Space and other
dimensions](space.md)). Its calls go up to High. It takes its number, and its owner, as soon as someone carries it, and
works from the inventory or a bauble slot. Its battery holds 1,200,000 EU and drains 4 EU/t on standby, 16 in a call:
charge it in a GT charger or battery buffer of HV or better. A flat phone switches off, and stays off once charged until
you switch it back on.

- **Phone key,** <KeyBind id="key.becom.phone">N</KeyBind>: answers a ringing phone, hangs up during a call, and opens
  the phone screen otherwise. Sneak and press it to turn a call down. It also answers a ringing Telephone within its
  cord, or a Videophone you're standing at.
- **Ringing:** a carried phone rings from its holder, and players nearby hear it. A notice in the top right corner, or
  by the video window, names the caller, with the keys to press: N answers, Shift+N declines. In silent mode only you
  feel it, as a buzz.
- **Bars** right of the hotbar show your service while you hold a phone, or the call's level during one, with the
  level's name above them in its colour: "No service" and an x when there's none.
- **Settings:** power, silent mode, do-not-disturb, hiding from contacts and the block list.
- **Signal:** what a [Signal Analyzer](analyzer.md) reads for phones where you stand: what serves you and how far
  away, what the link starts with, what each loss takes off it, and how far you are from the next level.

## Videophone

<ItemLink id="becom:machine:25009" showIcon="left">Videophone</ItemLink> is a speakerphone with a screen for
wired networks, at MV and 32 EU/t. Give it Phone Cable to a Router, like a Telephone. Everyone within 4 blocks is
in its call and on its screen, and its loudspeaker carries the call 16 blocks. It hangs up by itself 30 seconds after
the last person walks away.

Right-click it to use it: a Telephone's display and keys ([The wired network](wired.md)) under a screen for the video,
with three speed-dial keys, M1 to M3. As it's a speakerphone, its wide key also answers and hangs up, and its
**Camera** key switches its camera on or off for video calls.

## Video calls

Mobile Phones, ME Phones and Videophones show video. The front camera shows your character, moving as you move and
nodding as you talk. The rear camera shows what you see, from a Mobile or ME Phone, in calls of two people at High or
better, and starts switched off. A Videophone has no rear camera, but shows the other side's.

| Level | Front camera | Rear camera |
| --- | --- | --- |
| Low | Frozen, "Weak signal"; the voice goes on | Off |
| Medium | 48 pixels tall, 5 pictures a second, noisy | Off |
| High | 96 pixels tall, 12 a second, a little noise | 2 pictures a second |
| Max | 192 pixels tall, every frame, clean | 5 pictures a second |

Only a call between two ME Phones reaches Max: every other phone stops at High.

During a call, a small window in a corner shows the other side. The phone screen's Video tab shows it full size,
switches each camera, and hides or shows the window.

## ME Phone

<ItemLink id="becom:me_phone" showIcon="left">ME Phone</ItemLink> is a Mobile Phone with an ME network's link. Link it
at an ME Security Terminal, like a Wireless Terminal: it has service at Max wherever that network's wireless access
points reach. Out of that range it is a Mobile Phone: antennas and satellites serve it, at a Mobile Phone's signal, up
to High. ME Phones on one ME network call each other with no antenna. It charges in an ME charger.

To call any other phone, the ME network needs an
<ItemLink id="becom:machine:25008" showIcon="left">ME Bridge</ItemLink>: put it on ME cable, where it takes
one channel, and cable it, on any face, to a Router or an Exchange: the cable's tier sets its rate. It uses
512 EU/t, joins the two networks at Max, and carries 8 calls at once. An ME network carries calls only within each dimension.
