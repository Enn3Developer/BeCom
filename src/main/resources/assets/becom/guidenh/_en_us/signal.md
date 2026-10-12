---
navigation:
  title: How signal works
  parent: index.md
  position: 90
  icon: becom:signal_analyzer
---

# How signal works

A call or a radio channel travels hop by hop: Telephone to Line Terminal to pylons, a walkie to an antenna to the next
antenna, a phone up to the satellites. Each hop has a signal-to-noise ratio, its SNR, in decibels (dB). The link is as
good as its weakest hop, and no better than the devices at either end allow.

## Levels

| Level | SNR | What you hear |
| --- | --- | --- |
| None | below 6 dB | Nothing: a new link can't start |
| Low | 6 dB | Narrow, gritty and noisy, but you can make it out |
| Medium | 12 dB | Band-limited, with a little noise |
| High | 18 dB | Wide, with faint noise |
| Max | 24 dB | Clean |

A link reaches a level at its threshold and leaves it only 2 dB below, so it doesn't flicker between two: a link at
Low stays audible down to 4 dB. Links are looked at again once a second.

Each device caps what it hears and sends: a Telephone at Low, radios at Medium, a Mobile Phone and a Videophone at
High, an ME Phone at Max. A call between two phones takes the lower cap of the two.

## Breaking up and dropping

Between 6 and 12 dB the signal breaks up: short bursts of voice go missing, 30% of it at 6 dB, less as the signal
rises, and none from 12. Below 4 dB you hear nothing.

When a link falls below 6 dB, a 3-second count starts. Links are looked at once a second, so it runs out after 3 or 4
seconds. Then:

- a call between two phones ends; in a group call, a phone cut off from everyone else leaves, and the others carry on;
- a phone still ringing stops;
- on a radio channel nothing ends: that link stays silent until it's back at 6 dB.

## Wires

Each powered node drives a line with 30 dB, and the wire loses 0.08 dB a block. A line breaks up past 225 blocks from
its last powered node and drops at 300. A Repeater or a Router starts the count again.

## Radio

A radio hop starts from its link budget and loses:

- **Distance:** 20 · log10 of the distance in blocks. Each doubling of the distance costs 6 dB, so 6 dB more budget
  doubles the range on open ground.
- **Terrain:** the worst hill or building between the two ends, by how far it rises above the straight line between
  them. Up to 40 dB.
- **Cover:** an end with something over it loses 1.5 dB a block on the radio band and 2.5 on the phone band, up to 40
  dB. The blocks counted run from the end's eyes, or the top of an antenna's head, up to the top of the highest
  light-blocking block above it: roofs, leaves and water count, glass doesn't. A roof costs a few dB; deep under rock,
  the link is gone.
- **Weather:** 3 dB in rain and 6 in a thunderstorm, where rain or snow falls on either end. Deserts stay clear. Only
  the phone band and satellite hops feel it.
- **Noise:** 10 dB in a dimension without a sky, like the Nether, where terrain and cover aren't counted.

The radio band (VHF), which radios use, bends around hills better; the phone band (UHF), which calls use, needs a
clearer line of sight.

Antennas map the land around them when they form, and every 5 minutes after that, which is why a new hill can take a
while to show up in a link.
