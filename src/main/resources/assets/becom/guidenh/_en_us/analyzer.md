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

At a glance it's a line a band: four signal bars and the SNR, coloured by level, then the **biggest loss**, the one to
fix first, with its icon. With nothing in the way it names what serves you instead: a T1, T2 or T3 antenna, or the
satellites. "No service" means a phone here has none. A walkie still reaches other radios straight, about 80 blocks,
which the analyzer doesn't show.

**Sneak** to see every term. Each band names what serves it (an antenna, with how far it is and an arrow pointing to it,
or the satellites overhead), then draws a bar for what the link starts with, a bar for each loss that takes something
off it, biggest first, and what's left at the level it makes. The last line says how many dB short of the next level
you are ([How signal works](signal.md)):

- **Budget:** what the antenna starts with: 42 dB for a T1, 48 for a T2, 54 for a T3.
- **Distance:** grows by 6 dB each time the distance doubles. Move closer, or use a stronger antenna.
- **Terrain:** a hill or building between you and the antenna. Raise the antenna on a taller mast, or put it on the
  hill.
- **Cover:** what's over your head. Step outside, or bring an antenna down to you.
- **Weather:** rain or a storm where you or the antenna stand, on the phone band, and on either band from the
  satellites. It passes.
- **Noise:** 10 dB in a dimension without a sky.

From the satellites, the first bar is what they give, from the number in orbit, and what cover and the weather take
off it.

## Reading it in practice

- **Low and breaking up:** look at the biggest loss. Terrain means the antenna needs height; distance means it needs
  a higher tier or a second antenna halfway.
- **None underground:** cover is past what any antenna can make up. Run cable down from an antenna's Line Hatch at the
  surface, and call from a Telephone on it.
- **Fine outside, gone inside:** that's cover. It counts every block from your eyes up to the top of the roof: under a
  one-block roof over a room two blocks high, about 3.5 dB on the phone band, and more with a higher ceiling.
