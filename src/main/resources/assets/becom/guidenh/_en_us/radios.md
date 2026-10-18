---
navigation:
  title: Radios
  parent: index.md
  position: 70
  icon: becom:walkie_talkie
item_ids:
  - becom:walkie_talkie:*
  - becom:machine:25007
quest_ids:
  - "VdecT0iBUsCxBEbIo6xNeA"
  - "82uwmy_wU3eMPoGqyurVlw"
  - "gNOxxRElWUebInHOkYOeQw"
---

# Radios

From MV, radios share 16 open channels: anyone tuned in hears what's said, and nobody can be blocked. Hold the radio
push-to-talk key, <KeyBind id="key.becom.radio">Caps Lock</KeyBind>, to talk.

## Walkie-Talkie

<ItemLink id="becom:walkie_talkie" showIcon="left">Walkie-Talkie</ItemLink> runs on its own MV battery: charge it in a
GT charger or battery buffer of MV or better. It works from your inventory or a bauble slot. Right-click it to pick a
channel or switch it off, or sneak and scroll with it in hand to change channel: for a moment, a strip above the hotbar
shows the channels either side, "3 ◂ 4 ▸ 5". While you talk on it, a red TX lamp and its channel show above the hotbar.

On its own it reaches about 80 blocks on open ground, and much further through antennas. Anyone within 6 blocks of a
walkie that's receiving hears it too. It drains a little on standby and more while receiving or talking. A flat one
switches off, and stays off once charged until you switch it back on.

## Transceiver

<ItemLink id="becom:machine:25007" showIcon="left">Transceiver</ItemLink> is a radio on a block, at MV and
32 EU/t. Stand within 2 blocks of it and hold push-to-talk to talk through it instead of your walkie. Its loudspeaker
plays its channel to everyone within 16 blocks. On its own it reaches about 125 blocks on open ground.

## Who you hear

You hear each transmitter by the better of two paths: straight from radio to radio, or through the antennas between
you, which repeat every channel ([Antennas](antennas.md)). Radios are capped at Medium.

When two people talk at once, you hear only the stronger. Whoever you're hearing keeps the channel until a moment after
they stop; someone else takes over only if their signal to you is at least 3 dB stronger.

Under open sky, once 5 satellites orbit your dimension, radios also reach each other through the satellites, however
far apart ([Space and other dimensions](space.md)). With only 5, that link sits right at Low's edge, and rain or a roof
over either radio cuts it; 14 satellites keep it at Medium. Radio never leaves its dimension.

When you're within 16 blocks of someone, you hear their voice directly, as GTNH Voice plays it, and BeCom stays out
of the way.
