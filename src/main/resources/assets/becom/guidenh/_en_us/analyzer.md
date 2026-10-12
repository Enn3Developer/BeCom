---
navigation:
  title: The Signal Analyzer
  parent: index.md
  position: 65
  icon: becom:signal_analyzer
item_ids:
  - becom:signal_analyzer:*
quest_ids:
  - "8sQY72VCXU2ZURqIKp1zVw"
---

# The Signal Analyzer

Hold a <ItemLink id="becom:signal_analyzer" showIcon="left">Signal Analyzer</ItemLink> and an overlay shows your signal,
read again every second. It needs no power. Server operators can print the same reading in chat with `/becom signal`.

It reads two bands:

- **Radios (VHF):** what a walkie-talkie would get from antennas and satellites where you stand.
- **Phones (UHF):** what a Mobile Phone would get.

## Reading a band

Each band shows its SNR and level, then what serves you best: an antenna, with its tier and position, or the satellites
over your dimension. "No antenna or satellites in reach", after a band's name, means a phone here has no service. A
walkie still reaches other radios straight, about 80 blocks, which the analyzer doesn't show.

The next line names the **biggest loss**, the one to fix first, and the last line lists every term of the link
([How signal works](signal.md)):

- **Budget:** what the antenna starts with: 42 dB for a T1, 48 for a T2, 54 for a T3.
- **Distance:** grows by 6 dB each time the distance doubles. Move closer, or use a stronger antenna.
- **Terrain:** a hill or building between you and the antenna. Raise the antenna on a taller mast, or put it on the
  hill.
- **Cover:** what's over your head. Step outside, or bring an antenna down to you.
- **Weather:** rain or a storm where you or the antenna stand, on the phone band, and on either band from the
  satellites. It passes.
- **Noise:** 10 dB in a dimension without a sky.

From the satellites, the line shows what they give, from the number in orbit, and what cover and the weather take
off it.

## Reading it in practice

- **Low and breaking up:** look at the biggest loss. Terrain means the antenna needs height; distance means it needs
  a higher tier or a second antenna halfway.
- **None underground:** cover is past what any antenna can make up. Run a line down, with an antenna bridging it at
  the surface.
- **Fine outside, gone inside:** that's cover. It counts every block from your eyes up to the top of the roof: under a
  one-block roof over a room two blocks high, about 3.5 dB on the phone band, and more with a higher ceiling.
