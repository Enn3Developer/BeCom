# Better Communications: design

Draft 9, 2026-10-12. This rewrites the original Google Doc draft, adds the decisions made since, and fits everything to
the GTNH Voice 0.10.1 API. From now on this file is the source of truth.

Every choice is in the [decision log](#2-decision-log): **decided** means settled with the owner, **proposed** means it
still needs a yes or a change. Every number lives in [Tunables](#17-tunables) and is a starting point for playtesting,
not a promise.

## 1. What BeCom is

A GT New Horizons addon that adds a communications tech tree on top of GTNH Voice: wired telephones on pylon lines at
LV, radio from MV, mobile phones and satellites at HV, ME-backed phones at EV. Every call is real voice carried by GTNH
Voice, with in-game video on phones that have a screen. BeCom decides who hears and sees whom, and how good it is.

Goals:

- Each tier changes how you reach people far away, and builds on the tier before it.
- Call quality comes from a radio simulation players can learn: distance, terrain, antenna height and cover all matter,
  and in-game tools show why a link is good or bad.
- Server cost stays bounded no matter how big a network grows.

Not in v1:

- Text messages, data or redstone signals over the network.
- Call waiting, or being in more than one call at once.
- Real-world RF accuracy. The model is physically flavoured and tuned for gameplay.

BeCom is not part of the official GTNH pack, and won't be in the foreseeable future. It has to fit into a GTNH install
without anyone reserving anything for it ([14.1](#141-machine-ids)).

## 2. Decision log

| #   | Decision                                                                                                                           | Status   |
|-----|------------------------------------------------------------------------------------------------------------------------------------|----------|
| D1  | Call quality comes from a simulated radio model: distance, terrain, antenna height, cover.                                        | decided  |
| D2  | Pylons are multiblocks, spaced much further apart than the draft's 8 blocks.                                                      | decided  |
| D3  | The design lives in this file, in English.                                                                                         | decided  |
| D4  | Where the draft's tier summary and its progression list disagree, the list wins: radio at MV, mobile phone at HV, antenna T1 at late LV. | decided  |
| D5  | Satellite dishes do both jobs from the draft: satellites give devices a base signal under open sky, and Satellite Dishes link their networks through the satellites. | decided  |
| D6  | The draft's "craftable rocket with antenna" is the Satellite Rocket the Launch Pad launches. The "rocket radio receiver" is an upgrade fitted to a Galacticraft rocket. | decided  |
| D7  | The stationary radio is called **Transceiver**, so it doesn't clash with GTNH Voice Radio's "Radio" block.                        | decided  |
| D8  | Phone Cable is made from red alloy wire.                                                                                           | decided  |
| D9  | Walkie-talkies reach each other directly over a short range; antennas repeat radio channels for longer range.                     | decided  |
| D10 | Device quality caps follow the draft's adjectives: Telephone Low, radios Medium, Mobile Phone High, ME Phone Max.                 | decided  |
| D11 | Powered nodes (terminals, repeaters, routers, antennas, dishes, relays) work only while loaded and powered, like any GT machine. Pylons are passive and work unloaded. | decided  |
| D12 | Phone numbers are unique per world, not per network.                                                                               | decided  |
| D13 | The ME Phone uses AE2's own wireless range check, so GTNH's range and booster cards apply unchanged.                             | decided  |
| D14 | Mobile phones and walkie-talkies work from a Baubles slot (the draft's proposal).                                                  | decided  |
| D15 | A handheld Signal Analyzer and a coverage map in the antenna GUI explain the simulation to players.                               | decided  |
| D16 | Satellites are launched from BeCom's own Satellite Launch Pad, not from Galacticraft rockets.                                     | decided  |
| D17 | When a listener is within 16 blocks of the speaker (or GTNH Voice's proximity range, if smaller), BeCom doesn't route the call or radio audio; they hear the speaker directly. | decided  |
| D18 | BeCom's machines are GT MetaTileEntities, in their own ID range (25000–25199) since BeCom isn't in the official pack.            | decided  |
| D19 | One Telephone per Line Terminal: strictly the draft's "1 ↔ 1".                                                                     | decided  |
| D20 | Pylon lines are re-checked from time to time, and a blocked line goes down until it's cleared.                                    | decided  |
| D21 | Players within 6 blocks of someone whose walkie-talkie is receiving hear it too.                                                  | decided  |
| D22 | Portal Relays link networks through Nether and End portals.                                                                         | decided  |
| D23 | Satellites decay: each one falls out of orbit after a while and has to be replaced.                                               | decided  |
| D24 | On a radio channel, each listener hears only the strongest of several simultaneous transmitters (capture effect).                 | decided  |
| D25 | Every phone has a 6-digit number, and the dialer lists reachable phones by owner name.                                            | decided  |
| D26 | Rain and thunder weaken UHF links (calls) and satellite hops: 3 dB in rain, 6 dB in a thunderstorm. VHF links on the ground don't care. | decided  |
| D27 | GTNH Voice, GregTech, AE2, Baubles and Galacticraft are all hard dependencies: BeCom doesn't load without any of them.          | decided  |
| D28 | The Rocket Radio Receiver works in any rocket built on Galacticraft's `EntityAutoRocket`: Galacticraft T1–T3 and its cargo rocket, GalaxySpace T4–T8, AmunRa's shuttle. | decided  |
| D29 | Line Terminals and Repeaters are LV machines drawing 4 EU/t, not real ULV machines that LV power would blow up.                  | decided  |
| D30 | A Satellite Rocket is a BeCom Satellite on Galacticraft Tier 1 rocket parts.                                                        | decided  |
| D31 | Recipes follow GT's conventions: controllers are shaped crafting around a machine hull, devices come from the assembler, emitters transmit and sensors receive at the item's tier, handhelds included. Structures use only blocks GT already has. | decided  |
| D32 | Default textures ship in the jar. A separate add-on resource pack restyles BeCom for Modernity. BeCom reuses GT's textures wherever it can and never detects resource packs in code. | decided  |
| D33 | BeCom is built in five milestones (0.1 wired, 0.2 radio, 0.3 phones, 0.4 space, 1.0 polish) and stays private until 1.0. | decided  |
| D34 | Two keys: radio push-to-talk, and Phone, which answers, hangs up or opens the dialer. Sneak + Phone rejects a call. Incoming calls show an on-screen notice. | decided  |
| D35 | The Signal Analyzer shows a live on-screen overlay while held.                                                                     | decided  |
| D36 | Walkie-talkies and phones drain their battery on standby too, more while in use, and can be switched off.                          | decided  |
| D37 | Anyone can wrench-link a node into any network. Only a node's owner or their GT team can unlink it.                               | decided  |
| D38 | Antennas serve everyone. The owner can lock one to their GT team.                                                                  | decided  |
| D39 | Phones get do-not-disturb, a block list and a hide-from-contacts option. The server rate-limits call attempts.                    | decided  |
| D40 | Only the Satellite Launch Pad and the Satellite Dish need a maintenance hatch.                                                     | decided  |
| D41 | Video calls stay in-game, with no real webcam. The front camera shows the caller's character; the rear camera streams their first-person view, in one-to-one calls only, off by default. | decided  |
| D42 | Video works on the Mobile Phone (up to High), the ME Phone (Max) and a new MV Videophone block for wired networks. Telephones stay voice-only. | decided  |
| D43 | Video needs Medium or better. At Low the picture freezes and the voice carries on.                                                 | decided  |
| D44 | During a call, video shows in a small corner window that can be hidden, and full size in the phone screen.                         | decided  |
| D45 | An ME Bridge joins an ME network to a BeCom network, so ME phones can call any phone. ME phones on the same ME network need no bridge. | decided  |
| D46 | Portal Relays also work at Twilight Forest and Personal Space portals. Other dimensions stay unreachable in v1.                   | decided  |
| D47 | Radio channels stay open: blocking and do-not-disturb apply to calls only.                                                         | decided  |
| D48 | An in-game GuideNH guide explains the mod, written for 1.0.                                                                        | decided  |
| D49 | BeCom carries its own questline and adds it to the BetterQuesting quest book itself, with a config switch to turn that off. The same quest files are published for pack makers. | decided  |
| D50 | BeCom adds its own task type to BetterQuesting, which BeCom completes when something happens: a first call, a first radio contact, satellites in orbit. | decided  |
| D51 | Quests give GTNH-style rewards, balanced per tier against GTNH's own quests.                                                       | decided  |
| D52 | Quests are written with each milestone, for the content that milestone adds.                                                       | decided  |
| D53 | Claude Code generates every asset: textures in both styles, models and synthesized sounds.                                        | decided  |

## 3. Progression

| Stage    | Content                                                                    |
|----------|----------------------------------------------------------------------------|
| Early LV | Pylon, Line Terminal, Repeater, Router, Phone Cable, Telephone             |
| Late LV  | Antenna T1                                                                 |
| Early MV | Transceiver, Signal Analyzer                                               |
| Mid MV   | Walkie-Talkie, Antenna T2, Portal Relay                                    |
| Late MV  | Videophone                                                                 |
| Early HV | Mobile Phone                                                               |
| Mid HV   | Antenna T3, Rocket Radio Receiver                                          |
| Late HV  | Satellite Rocket, Satellite Launch Pad, Satellite Dish                     |
| Late EV  | ME Phone, ME Bridge                                                        |

### 3.1 Recipes

Following GT's conventions (D31): machines and multiblock controllers are shaped crafting recipes built around a machine
hull, like GT's own controllers. Devices and parts come from the assembler. Whatever transmits takes an emitter, whatever
receives takes a sensor, at the item's tier. The exact crafting grids are left to the code.

| Item                  | Made in                     | Ingredients                                                                                  |
|-----------------------|-----------------------------|----------------------------------------------------------------------------------------------|
| Phone Cable ×2        | Assembler, 8 EU/t, 5 s      | Red Alloy Wire, 72 L Rubber                                                                  |
| Telephone             | Assembler, 30 EU/t, 10 s    | LV circuit, Note Block (the bell), 2 Steel Plates, Phone Cable (the cord)                    |
| Pylon Head            | Crafting                    | Steel Frame Box, 2 Steel Plates, 4 Steel Wires (the line), 2 Glass Plates (insulators)       |
| Line Terminal         | Crafting                    | LV Machine Hull, LV circuit, 2 Phone Cables, 2 Steel Wires                                   |
| Repeater Head         | Crafting                    | Pylon Head, LV Machine Hull, 2 LV circuits, 2 Tin Cables                                     |
| Router Head           | Crafting                    | Pylon Head, LV Machine Hull, 4 LV circuits, 2 Tin Cables                                     |
| Antenna T1 Head       | Crafting                    | LV Machine Hull, LV Emitter, LV Sensor, 2 LV circuits, 2 Tin Cables, 2 Long Steel Rods       |
| Transceiver           | Crafting                    | MV Machine Hull, MV Emitter, MV Sensor, 2 MV circuits, 2 Copper Cables, Note Block (the speaker) |
| Signal Analyzer       | Assembler, 120 EU/t, 20 s   | MV Sensor, Screen Cover, MV circuit, 4 Aluminium Plates                                      |
| Walkie-Talkie         | Assembler, 120 EU/t, 20 s   | MV Emitter, MV Sensor, MV circuit, MV battery, 2 Polyethylene Plates                         |
| Antenna T2 Head       | Crafting                    | MV Machine Hull, MV Emitter, MV Sensor, 2 MV circuits, 2 Copper Cables, 2 Long Aluminium Rods |
| Portal Relay          | Crafting                    | MV Machine Hull, MV Emitter, MV Sensor, 2 MV circuits, 2 Copper Cables, 2 Eyes of Ender      |
| Videophone            | Crafting                    | MV Machine Hull, Screen Cover, MV Sensor (the camera), 2 MV circuits, Note Block (the speaker), 2 Phone Cables |
| Mobile Phone          | Assembler, 480 EU/t, 30 s   | MV Emitter, MV Sensor, Screen Cover, HV circuit, HV battery, 2 Polyethylene Plates           |
| Antenna T3 Head       | Crafting                    | HV Machine Hull, HV Emitter, HV Sensor, 2 HV circuits, 2 Gold Cables, 2 Long Stainless Steel Rods |
| Rocket Radio Receiver | Assembler, 480 EU/t, 30 s   | HV Emitter, HV Sensor, 2 HV circuits, 4 Stainless Steel Plates                               |
| Satellite             | Assembler, 480 EU/t, 40 s   | HV Emitter, HV Sensor, 2 HV circuits, 2 Solar Panel covers, 4 Aluminium Plates               |
| Satellite Rocket      | Assembler, 480 EU/t, 60 s   | Satellite, and Galacticraft's Nose Cone, Tier 1 Rocket Engine, 4 Rocket Fins, 4 Heavy Duty Plates (D30) |
| Satellite Launch Pad  | Crafting                    | HV Machine Hull, 2 HV Electric Pumps, HV Robot Arm, 3 HV circuits, 2 Gold Cables             |
| Satellite Dish        | Crafting                    | HV Machine Hull, 2 HV Emitters, 2 HV Sensors, 2 HV circuits, 2 Gold Cables                   |
| ME Phone              | Assembler, 1,920 EU/t, 30 s | Mobile Phone, AE2 Wireless Terminal, EV Emitter, 2 EV circuits                               |
| ME Bridge             | Crafting                    | EV Machine Hull, AE2 Wireless Access Point, EV Emitter, EV Sensor, 2 EV circuits, 2 Aluminium Cables, ME Glass Cable |

### 3.2 Structures

Multiblocks use only blocks GT already has (D31). Power comes in through GT energy hatches, except on Repeater and Router
heads, which take a cable directly.

| Multiblock           | Blocks                                                                                                     |
|----------------------|------------------------------------------------------------------------------------------------------------|
| Pylon                | Steel Frame Boxes, 6 to 16 tall, with the head on top                                                      |
| Antenna T1, T2, T3   | A 3×3 base of LV, MV or HV Machine Casings, a mast of Steel, Aluminium or Stainless Steel Frame Boxes, the head on top, and an Energy Hatch in the base |
| Satellite Launch Pad | A 5×5 floor of Clean Stainless Steel Machine Casings with the controller on one edge. An Input Bus for Satellite Rockets, an Input Hatch for fuel, an Energy Hatch and a Maintenance Hatch (D40) take the place of casings. |
| Satellite Dish       | A 5×5 dish of Clean Stainless Steel Machine Casings on a stand of Stainless Steel Frame Boxes, with an Energy Hatch and a Maintenance Hatch (D40) |

## 4. Signal quality

Every link in BeCom ends up at one of five levels:

| Level  | What the listener hears                          |
|--------|--------------------------------------------------|
| None   | Nothing. A call can't connect, or it drops.      |
| Low    | Narrow, gritty, noisy, but intelligible.         |
| Medium | Band-limited with light noise.                   |
| High   | Wide band with faint noise.                      |
| Max    | Clean.                                           |

### 4.1 From SNR to level

A link is a chain of hops (Telephone to Terminal to Repeater, or Walkie to Antenna to Antenna to Walkie, and so on).
Each hop gets a signal-to-noise ratio (SNR) in dB, from the line model ([5.7](#57-line-loss)) or the radio model
([6](#6-radio-simulation)). Then:

- **level** = the level of the weakest hop, capped by the caps of both end devices (D10).
- Thresholds: Low at 6 dB, Medium at 12, High at 18, Max at 24.
- Hysteresis: a link enters a level at its threshold and leaves it only 2 dB below, so levels don't flicker.
- Links are re-evaluated once per second on the server. Clients are told only when a level changes.

### 4.2 Dropouts

While the weakest hop sits between 6 and 12 dB, the server drops voice frames: 30% at 6 dB, falling to 0% at 12 dB.
Losses come in bursts (a two-state Gilbert-Elliott model, mean burst about 60 ms), which sounds like a real breaking
signal rather than random crackle. Below 6 dB for more than 3 seconds, the link drops. This is the draft's "the signal
degrades until it drops", and it works the same for wires and radio.

### 4.3 How it sounds

The listener's client filters audio that arrived through a BeCom group ([13.5](#135-client-audio)). Starting recipes,
to be tuned by ear:

| Level  | Calls                                                    | Radio channels                                     |
|--------|----------------------------------------------------------|----------------------------------------------------|
| Low    | `bandPass(300, 3000)`, `mulaw()`, `downsampleHold(4)`, `noise(0.03)` | `bandPass(400, 2800)`, `softClip()`, `mulaw()`, `noise(0.05)` |
| Medium | `bandPass(250, 4000)`, `mulaw()`, `noise(0.012)`         | `bandPass(300, 3400)`, `softClip()`, `noise(0.02)` |
| High   | `bandPass(150, 7000)`, `noise(0.004)`                    | (radios cap at Medium)                             |
| Max    | no processing                                            | (radios cap at Medium)                             |

## 5. Wired network (LV)

The first tier: telephones connected by lines on pylons. No radio involved.

### 5.1 Pylon

- **Structure:** a vertical column of steel frame boxes, 6 to 16 blocks tall, with a **Pylon Head** on top as the
  controller. Built like any GT multiblock, with StructureLib hologram support.
- **Span:** two pylons can be linked if they are at most 4 × the shorter pylon's height apart, so 24 to 64 blocks.
  Taller pylons mean fewer pylons per line.
- **Links:** a plain head has 2 link slots (a line passes through).
- **Linking:** when a pylon forms, it links on its own only if exactly one formed pylon in range has a free slot.
  Otherwise, link by hand: GT wrench on head A, then on head B. Wrench on a linked head to unlink. Who may do which:
  [12.2](#122-networks).
- **Clearance (D20):** the straight line between two heads must be free of solid blocks when they're linked. Every 60
  seconds, each link whose whole length is in loaded chunks is checked again. A blocked link goes down, and its wire
  renders broken, until the blocks are cleared. Links running through unloaded chunks keep their last state.
- **Wire:** rendered between linked heads as a sagging line. Its cost is part of the head recipe.
- **Passive:** no power needed. A line works through unloaded chunks as long as its powered nodes are loaded (D11).

### 5.2 Line Terminal

Ground block where a line starts or ends (the draft's "inizio e fine linea"). LV, 4 EU/t (D29). Links to one Pylon Head
(wrench) and connects to exactly one Telephone or Videophone through Phone Cable (D19). A line needs a terminal at each
end.

### 5.3 Repeater

A Pylon Head variant that takes power from a cable on any side (LV, 4 EU/t). It regenerates the signal, so line loss
starts over after it ([5.7](#57-line-loss)).

### 5.4 Router

A Pylon Head variant with 15 link slots (LV, 16 EU/t). It also regenerates the signal. Routers turn lines into a
network: without one, a line connects exactly two telephones; with routers, any telephone can dial any other telephone
on the connected network.

### 5.5 Phone Cable

Cable block made from red alloy wire (D8). Connects a Telephone to a Line Terminal, up to 32 blocks of cable. Lossless.

### 5.6 Telephone

- **Block.** Right-click picks up the handset. You can move 1 block away (the cord). Further than that hangs up.
- **Picking up:** if exactly one other phone is reachable, it rings that phone. Otherwise it opens the dialer
  ([8.2](#82-calls)). A plain 1 ↔ 1 line rings the other end; a line that reaches more phones, through routers or an
  antenna bridge, dials.
- **Incoming call:** the phone rings (a sound at the block). Picking up answers.
- **Quality cap:** Low (D10).

### 5.7 Line loss

Each powered node (terminal, repeater, router) drives the line at a 30 dB budget, and the wire loses 0.08 dB per block
of span. A stretch of line has `SNR = 30 − 0.08 × length since the last powered node`. That gives no dropouts up to
225 blocks, dropouts after that, and a dropped line at 300 blocks. So: a repeater at least every 225 blocks.

## 6. Radio simulation

The draft's MV idea, made concrete (D1): signal parameters are worked out when an antenna is built and refreshed every
few minutes, and that's what keeps the per-player cost small.

### 6.1 Link budget

For a radio hop between two ends `a` and `b`:

```
SNR = B − L_dist − L_terrain − L_cover(a) − L_cover(b) − L_weather − N
```

- `B`: the hop's link budget in dB ([17](#17-tunables)). +6 dB doubles the range on open ground.
  - If either end is an antenna, `B` is that antenna's budget, in both directions: an antenna's gain helps it receive
    as much as transmit, so a phone's small transmitter doesn't limit its range. Between two antennas, the lower
    budget.
  - On a direct link between two devices ([6.4](#64-direct-links)), `B` is the transmitting device's budget.
- `L_dist = 20 · log10(d)`, with `d` the 3D distance in blocks (at least 1).
- `L_terrain`: single knife-edge diffraction over the worst obstacle on the path (ITU-R P.526):
  `ν = h · sqrt(2 · (d1 + d2) / (λ · d1 · d2))`, where `h` is how far the obstacle rises above the straight line
  between the ends (negative if the line clears it) and `d1`, `d2` are its distances to each end.
  `L_terrain = 6.9 + 20 · log10(sqrt((ν − 0.1)² + 1) + ν − 0.1)` when `ν > −0.78`, otherwise 0. Capped at 40 dB.
- `λ` is the band's wavelength in blocks: **VHF** (radios) 3, **UHF** (phones) 1. VHF bends around hills better; UHF
  needs a clearer line of sight.
- `L_cover`: an end below the terrain surface loses `k_cover × depth`, capped at 40 dB. `depth` is the surface height at
  that column minus the end's eye height. VHF loses 1.5 dB per block, UHF 2.5. A roof costs a few dB; a mine kills the
  signal.
- `L_weather` (D26): `3 · rain + 3 · thunder` dB, where `rain` and `thunder` are Minecraft's 0–1 rain and thunder
  strengths in that dimension: 3 dB in rain, 6 dB in a thunderstorm, fading in and out with the weather. It applies to
  UHF links and satellite hops only, and only when at least one end is in a biome where it rains or snows, so deserts
  stay clear. VHF links on the ground ignore the weather.
- `N`: the noise floor. 0 in dimensions with a sky.
- **Dimensions without a sky** (the Nether): the heightmap there is the bedrock ceiling, and a 2.5D height model can't
  describe caves anyway. So `L_terrain` and `L_cover` are skipped, and `N` is 10 dB instead. Satellites don't reach them.

### 6.2 Height cache

The simulation never loads chunks. Terrain comes from a per-dimension height cache:

- 4 × 4 cells per chunk, each holding the highest point of the chunk's heightmap in that cell. Trees and buildings count;
  glass doesn't, because the heightmap ignores transparent blocks.
- Updated when a chunk loads and when it saves. Stored per dimension in region files of 32 × 32 chunks, 1 byte per cell
  (16 KB per region).
- Terrain that was never loaded is assumed to be at sea level. The next survey corrects it once someone explores there.

### 6.3 Antenna survey

When an antenna forms, and every 5 minutes after that, it surveys its surroundings:

- For 64 bearings, step outward in 4-block steps up to the antenna's maximum range, and store the terrain height at
  every step: a profile of at most 64 heights per bearing, about 4 KB per antenna.
- During the same survey, compute the links to other antennas in range.

A player's link to an antenna is then cheap: find the bearing and distance, scan that bearing's profile up to the player
for the obstacle with the largest `ν` toward the player's real eye height, and add cover loss at the player's position.
Which obstacle is worst depends on where the far end is, so the survey keeps the whole profile, not just a horizon.

### 6.4 Direct links

Radio to radio with no antenna in between (D9): walkie to walkie, or walkie to Transceiver. At least one end moves,
so there's nothing to precompute. While someone is transmitting, each listener's link is evaluated once per second by
sampling the height cache every 4 blocks along the path and taking the worst obstacle.

### 6.5 Budget

- Player access links: once per second for each player holding a working device, spread evenly over the 20 ticks.
- Surveys: spread over ticks, at most 2 ms per tick in total.
- Direct links: only for transmitters that are currently keyed.

A T3 survey is 64 bearings × 64 steps, about 4,000 height-cache reads. A player's access evaluation scans one stored
profile, at most 64 heights, per antenna in range.

### 6.6 Making it learnable

- **Signal Analyzer** (early MV, handheld, needs no power): shows SNR, level, the serving antenna, and the biggest loss
  (distance, terrain, cover, weather or noise).
- **Antenna GUI:** a coverage map drawn from the survey, showing expected SNR at standing height around the antenna.
- **`/becom signal`:** the same breakdown in chat, for testing and debugging.

## 7. Antennas

- **Structure:** a base of machine casings, a mast of 4 to 32 frame boxes, and an antenna head on top as the controller
  ([3.2](#32-structures)). The survey uses the head's height, so a taller mast on a hill covers more.
- **Tiers:** T1 (late LV), T2 (mid MV), T3 (mid HV). Link budgets of 42, 48 and 54 dB, which give roughly 64, 128 and
  256 blocks to the edge of Low on open ground: the draft's ranges.
- **Power:** 32, 128 and 512 EU/t. Offline when unpowered or unloaded (D11).
- **What an antenna does:**
  - Access point for wireless devices in range, on both bands.
  - Repeater for all 16 radio channels.
  - Bridge for wired lines: an antenna can be wrench-linked to a Pylon Head or Line Terminal within 16 blocks, which
    carries the wired signal over radio, as in the draft.
  - Links to other antennas wherever the survey finds at least Low, separately for each band: radio channels travel
    between antennas on VHF, calls on UHF.

## 8. Wireless devices

| Device        | Stage    | Band | Cap    | Power             | Talking                          |
|---------------|----------|------|--------|-------------------|----------------------------------|
| Transceiver   | Early MV | VHF  | Medium | MV, 32 EU/t       | push-to-talk within 2 blocks     |
| Walkie-Talkie | Mid MV   | VHF  | Medium | MV battery        | push-to-talk key                 |
| Mobile Phone  | Early HV | UHF  | High   | HV battery        | your usual voice settings, in calls |
| ME Phone      | Late EV  | none | Max    | AE, like a wireless terminal | your usual voice settings, in calls |

### 8.1 Radio channels

- 16 channels. Tune with the GUI, or by scrolling while sneaking.
- **Transmit:** hold the BeCom push-to-talk key with a working walkie-talkie in your inventory or Baubles slot, or
  within 2 blocks of a Transceiver. Standing next to a Transceiver, it transmits instead of your walkie.
- **Receive:**
  - Walkie-talkie holders tuned to the channel hear it directly.
  - Players within 6 blocks of a receiving walkie-talkie hear it too, from its holder's position (D21).
  - Transceivers play it from the block as a loudspeaker, for anyone within 16 blocks.
- **Range:** each listener gets the better of the direct link and the path through antennas. Direct, a walkie-talkie
  (44 dB budget) reaches about 80 blocks on open ground, and a Transceiver (48 dB, the same as a T2 antenna) about 125.
- **Several people talking at once (D24):** each listener hears only the strongest signal. Whoever a listener is
  hearing keeps the channel until 100 ms after their last frame. Another transmitter takes over only if its link to
  that listener is at least 3 dB stronger.

### 8.2 Calls

- **Numbers (D25):** every phone (Telephone, Mobile Phone, ME Phone) gets a 6-digit number, unique in the world,
  assigned when it's first placed or used.
- **Dialer:** type a number, or pick from the contacts list: every reachable phone, listed by its owner's name.
- **Mobile phones** ring, get answered or rejected, and hang up. Group calls take up to 8 people.
- **Any phone can call any other phone** if the network has a path between them. The call's level is the weakest hop on
  that path, capped by both phones.
- **Mobile phone service** needs an antenna or satellite link of at least Low.

### 8.3 ME Phone

- Linked to an ME network the same way as a wireless terminal.
- Works only inside that network's wireless range, using AE2's own range check (D13). Max quality there, no service
  anywhere else.
- ME phones on the same ME network call each other at Max, with no BeCom network involved.
- **ME Bridge (D45):** to reach any other phone, the ME network needs an ME Bridge. It's a late-EV machine that connects
  to ME cable the way GT's ME hatches do, uses one ME channel, and draws 512 EU/t. Wrench-link it to a BeCom node
  within 16 blocks, and it joins the two networks as a Max hop.

## 9. Space and other dimensions

### 9.1 Satellites

- **Launch (D6, D16):** the Satellite Launch Pad (late HV multiblock, [3.2](#32-structures)) needs open sky above it. It
  takes Satellite Rockets from its input bus, and fuel from its input hatch: any fluid Galacticraft accepts as rocket
  fuel. Each launch takes 30 s at 480 EU/t and 1,000 L of fuel, and puts one satellite into orbit over the pad's
  dimension. Every dimension with a sky keeps its own constellation. Maintenance problems slow launches down, as on any
  GT machine (D40).
- **Decay (D23):** each satellite stays up for 100 Minecraft days of world time, ± 20% at random so they don't all come
  down together. Decay only advances while the server runs. The Launch Pad GUI shows each dimension's satellite count
  and when the next one comes down.
- **Base signal (D5):** any device under open sky gets a sky hop to the satellites. A thin roof weakens it through cover
  loss; underground it's gone.
- **Strength:** `SNR_sky = 6 + 18 · log10(n / 5) / log10(20)` for `n ≥ 5` satellites in orbit. That's Low at 5,
  Medium at 14, High at 37 and Max at 100: the draft's "at least 5 to start, 100 for maximum".
- **A sky hop's SNR** is `SNR_sky` minus the ground end's cover loss and the weather loss ([6.1](#61-link-budget)).

### 9.2 Satellite Dish

- Late HV multiblock. Needs open sky above the dish and 512 EU/t.
- Wrench-linked to an antenna, Pylon Head or Line Terminal within 16 blocks, which joins it to that network.
- It has no process to slow down, so each maintenance problem costs its sky hops 2 dB instead (D40).
- While its dimension has at least 5 satellites, it links its network to every other dish in the same dimension. In the
  Overworld it also links to every planet's uplink ([9.3](#93-rocket-radio-receiver)). These are sky hops, so rain at
  the dish weakens them.
- This is the backbone half of D5: two networks far apart, each with a dish, become one.

### 9.3 Rocket Radio Receiver

- An upgrade for rockets (D6), carried in the rocket's cargo. A rocket built without chests has no cargo slots, so it
  needs at least one.
- Works in any rocket built on Galacticraft's `EntityAutoRocket` (D28): Galacticraft's Tier 1–3 rockets and its cargo
  rocket, GalaxySpace's Tier 4–8 rockets, and AmunRa's shuttle.
- Counts while the rocket stands unignited on a launch pad in a dimension other than the Overworld, with its chunk
  loaded. There it acts as a T1 antenna, and as that dimension's uplink to the Overworld's satellites. It needs no
  power.
- A rocket you fly yourself turns into an item in a parachest when you arrive: place it on a launch pad there and put
  the receiver in its cargo. An unmanned rocket sent by a Launch Controller counts as soon as it lands on its target pad.

### 9.4 Portal Relay

- Mid MV machine, 32 EU/t (D22). Works when it's within 4 blocks of a Nether portal, an End portal, the End's exit
  portal, a Twilight Forest portal or a Personal Space portal (D46). It recognizes them by block, so neither Twilight
  Forest nor Personal Space is a dependency.
- Two relays on opposite sides of a portal pair the same way pylons link: wrench on one, go through the portal, and
  wrench the other within 5 minutes ([12.2](#122-networks)).
- A linked pair is a fixed High hop between the two dimensions' networks.

### 9.5 Calls between dimensions

- **Overworld and a planet:** needs at least 5 satellites over the Overworld, and a landed rocket with a Rocket Radio
  Receiver on the planet. On the Overworld side, the call reaches devices under open sky directly, and whole networks
  through a Satellite Dish. The gateway hop's level is the lower of the Overworld end's sky hop (the device's or the
  dish's) and the receiver's uplink (High).
- **Planet and planet:** through the Overworld's satellites, so both planets need a receiver.
- **Overworld and the Nether, the End, the Twilight Forest or a personal dimension:** through a linked pair of Portal
  Relays.
- Longer chains combine these, for example Nether to Overworld to planet.
- **Every other dimension** (the Deep Dark, the Spirit World and so on) has no link in v1. Players there can't be
  reached.

## 10. Calls and channels

- **Call states:** idle, dialing, ringing, connected, ended. Dial tone, ringback, busy, unreachable and hang-up are
  ordinary Minecraft sounds.
- **One call at a time** per player in v1.
- **Unreachable** means: no voice session, no device with service, a link at None, or a phone that is switched off, on
  do-not-disturb, or blocking the caller ([12.3](#123-unwanted-calls)).
- **Levels** are per direction: what A hears from B uses the link from B to A. Calls through antennas come out the same
  both ways; direct radio links can differ, since a Transceiver transmits further than a walkie. All re-evaluated once
  per second.
- **Logout** ends the player's calls.
- **A voice session that times out** ends the player's calls after a 10-second grace period, unless the voice comes back.

### 10.1 Video

Video calls stay in-game (D41): there's no webcam. Each side shows the other what its character is and what it sees.

- **Front camera:** shows your character: skin, armor, held item, head turning where you look, arm swings, and a talking
  animation.
  - The server already knows all of that, so it sends it to the others in the call about 10 times a second (about 1–2
    kbps). Your client uploads nothing.
  - Your profile goes along once when the call connects, so their clients can load your skin.
  - Their client draws you the way the inventory screen draws the player, into a small image at the size your level
    allows.
  - The talking animation comes from your voice, which is arriving anyway ([13.5](#135-client-audio)).
- **Rear camera:** shows what you see.
  - Your client copies its finished world image just before the HUD is drawn, so no HUD and no coordinates.
  - It shrinks the copy on the GPU, compresses it to JPEG off the main thread, and sends it on BeCom's network channel.
  - The server only drops frames a viewer's level doesn't allow; it never decodes them.
  - One-to-one calls only, so the bandwidth stays bounded.
- **Devices (D42):** Mobile Phone up to High, ME Phone up to Max, Videophone up to High ([10.2](#102-videophone)).
  Telephones are voice-only.
- **Group calls:** a grid of up to 8 front-camera tiles.
- **Your choice:** each camera can be switched on or off during a call. The front camera starts on, the rear camera
  off.

Video uses the same per-direction level as voice, and needs Medium or better (D43):

| Level  | Front camera                                          | Rear camera     |
|--------|-------------------------------------------------------|-----------------|
| Low    | Frozen, with "weak signal". The voice carries on.    | Off             |
| Medium | 48 px tall, 5 fps, noisy                              | Off             |
| High   | 96 px tall, 12 fps, light noise                       | 128×72, 2 fps   |
| Max    | 192 px tall, every frame, clean                       | 192×108, 5 fps  |

Dropouts use the same burst model as voice ([4.2](#42-dropouts)): the server drops video frames, so the picture
freezes, then shows a few frames of blocky glitches.

### 10.2 Videophone

- An MV block for wired networks (D42), 32 EU/t, with a cap of High. It connects to a Line Terminal like a Telephone, and
  counts as that terminal's one phone (D19).
- It's a speakerphone: everyone within 4 blocks is in the call, unless they're already in another one. The call plays
  from its loudspeaker to everyone within 16 blocks.
- Its screen shows the call to the whole room. The other side sees everyone within 4 blocks as front-camera tiles.
- Right-click opens its phone screen: dial, answer, hang up. It hangs up by itself once nobody has been within 4 blocks
  for 30 s.

## 11. Controls and screens

### 11.1 Keys

| Key                | Default   | What it does                                                                                        |
|--------------------|-----------|-----------------------------------------------------------------------------------------------------|
| Radio push-to-talk | Caps Lock | Hold to transmit on your walkie-talkie, or on a Transceiver within 2 blocks ([8.1](#81-radio-channels)). |
| Phone              | N         | Answers a ringing phone, hangs up during a call, opens the phone screen otherwise. Sneak + Phone rejects a call (D34). |

Both defaults are unbound in a full GTNH install, and both can be rebound. GTNH Voice itself uses `,` and `M`, and leaves
its own push-to-talk unbound.

### 11.2 Phones

- **Ringing:** an incoming call rings at the phone, so a carried phone rings from its player and nearby players hear it.
  A notice in the corner of the screen shows the caller's name and number, and the keys. After 30 s without an answer
  the caller hears "no answer", and the call shows up as missed.
- **Silent mode:** only the phone's owner hears it, as a short buzz.
- **Phone screen** (Phone key while idle, or right-click with the phone in hand):
  - keypad, contacts (D25), recent and missed calls;
  - during a call: everyone in it with their signal bars, Add (group calls up to 8), Hang up;
  - settings: power, silent mode, do-not-disturb, hide from contacts, block list ([12.3](#123-unwanted-calls)).
- **Signal bars** sit next to the hotbar while you hold a phone or are in a call.
- **Video window (D44):** during a video call, a small window in a corner of the screen shows the other side. The phone
  screen shows it full size, switches each camera on or off, and hides or shows the window.
- **Telephone (block):** right-click picks up the handset. If exactly one other phone is reachable that rings it;
  otherwise it opens the phone screen ([5.6](#56-telephone)). Right-click again, or walk more than 1 block away, to hang
  up. A ringing Telephone is answered the same way.

### 11.3 Radios

- **Walkie-talkie:** right-click it in hand to open its screen: channel (1–16) and power. Sneak + scroll in hand changes
  the channel. Its TX light shows while you transmit.
- **Transceiver:** its screen sets the channel and turns the loudspeaker on or off.
- **Power (D36):** a walkie or phone that is switched off uses no charge, receives nothing and can't be called. A flat
  battery switches it off.

### 11.4 Machines and tools

- **Signal Analyzer (D35):** while held, an overlay shows SNR, level, what's serving you (an antenna or the satellites)
  and the biggest loss, refreshed every second.
- **Antenna screen:** the coverage map ([6.6](#66-making-it-learnable)), the antenna's links, and the team lock
  ([12.2](#122-networks)).
- **Satellite Launch Pad screen:** each dimension's constellation, when the next satellite comes down, and the queue of
  rockets waiting to launch.
- **All screens use ModularUI2 with GT's theme**, like GT's newer machines, so GTNH's UI resource packs restyle them too.

## 12. Ownership and abuse

### 12.1 Owners and teams

- A machine's owner is the player who placed it; GT already records this for every machine. A phone's owner is its
  first user.
- Teams are GT's own, the ones it uses for wireless EU and space projects (`/sp invite`, `/sp accept`, `/sp leave`).
  BeCom adds no team system of its own.

### 12.2 Networks

- **Linking is open (D37):** anyone can wrench-link a node into any network.
- **Unlinking isn't:** only the owner of either end, or their team, can unlink. Otherwise anyone with a wrench could cut
  your lines, and land claims don't cover that.
- **Half-made links:** after the wrench touches the first node, the server remembers it for that player for 5 minutes.
  That is how a Portal Relay pair gets linked through a portal.
- **Antennas serve everyone (D38).** The owner can lock one to their team in its screen. A locked antenna refuses other
  players' devices, but still relays calls along its links.
- **Calls are private** to the people in them. Radio channels are public: anyone tuned in hears them.
- **Radio stays open (D47):** blocking and do-not-disturb apply to calls only. If someone jams a channel, change
  channel.

### 12.3 Unwanted calls

From D39:

- **Do-not-disturb:** rejects every incoming call; callers hear the unreachable tone.
- **Block list:** blocked players can't call you, and you don't appear in their contacts.
- **Hide from contacts:** you aren't listed, so people need your number.
- **Rate limit:** at most 5 call attempts per minute per caller, enforced by the server.
- **Admin commands** (ops only): list active calls, hang up any call, set a dimension's satellite count for testing.

## 13. GTNH Voice integration (0.10.1)

### 13.1 Groups

Registered in `FMLServerStartingEvent` on every server start through `GtnhVoiceApi.groupManager().registerGroup(...)`.
GTNH Voice has no unregistration, so the set is fixed.

| Group                                | Display name           | Priority | Carries                              |
|--------------------------------------|------------------------|----------|--------------------------------------|
| `becom:call`                         | Call                   | 200      | every call: telephone, mobile, ME    |
| `becom:radio.1` … `becom:radio.16`   | Radio 1 … Radio 16     | 100      | radio channels                       |

Calls beat radio, radio beats proximity. GTNH Voice's global group stays above everything.

### 13.2 Membership and sessions

- A speaker's frames reach a group's `route()` only if the speaker is a member, so:
  - joining `becom:call` when a call connects, and leaving when it ends;
  - for a Videophone call, joining `becom:call` while within 4 blocks of the Videophone;
  - joining `becom:radio.N` while carrying a working walkie tuned to N (inventory or Baubles), or standing at a
    Transceiver tuned to N.
- Who receives the audio is decided inside `route()`, from the routing snapshot ([13.3](#133-routing)).
- GTNH Voice wipes memberships on logout and on a timed-out session. BeCom listens through
  `GtnhVoiceApi.addSessionListener(...)`:
  - `sessionStarted`: re-join the player's groups.
  - `sessionEnded` with `LOGOUT` or `TIMED_OUT`: apply the call rules in [10](#10-calls-and-channels).
  - `REPLACED` keeps memberships, so nothing to do.
- `GtnhVoiceApi.session(uuid)` answers "does this player have voice?" for the unreachable check.

### 13.3 Routing

- **Snapshot:** the server thread builds an immutable routing snapshot whenever something changes: calls, channel
  members, per-direction levels and dropout settings, walkie holders and their channels, Transceiver positions. It's
  published through a volatile field.
- **`route()`** runs on GTNH Voice's network thread and reads only the snapshot and the `RoutingContext`, never the world.
- **`becom:call`:**
  - For each other participant at Low or better, unless they hear the speaker directly (D17), apply the dropout model.
  - If the frame survives, `sendTo(listener, SourceState.FLAT, ...)`.
  - A Videophone at the other end gets the frame POSITIONAL from its block, sent to every player within 16 blocks of it
    ([10.2](#102-videophone)).
- **`becom:radio.N`:**
  - Only frames whose `getAudio().getActivationId()` equals `ActivationIds.of("BeCom", "radio")`, meaning the speaker
    is holding push-to-talk.
  - Listeners need a link at Low or better, pass the capture rule (D24), get the dropout model, and are skipped when
    they hear the speaker directly (D17).
  - First, FLAT to walkie holders tuned to N.
  - Then POSITIONAL from each walkie holder who got the frame to players within 6 blocks of them (D21).
  - Then POSITIONAL from each Transceiver on N that got the frame to players within its 16-block loudspeaker range.
  - GTNH Voice keeps only the first `sendTo` per player per frame, so this order decides which path a player hears,
    and nobody hears a channel twice.
- **Per-frame state:** the dropout model (one Gilbert-Elliott state per speaker-listener pair) and the capture state
  (who each listener is hearing on each channel, and when their last frame arrived) live in BeCom's own concurrent maps.
  Non-blocking, as `route()` requires.

### 13.4 Push-to-talk

- The client registers the addon with `GtnhVoiceClient.addon("BeCom").register()`, then takes its
  `activation("radio")`.
- Every client tick, the push-to-talk handler calls `setKeyed(keyHeld && hasUsableRadio)`, with the key from
  [11.1](#111-keys).
- `isTransmitting()` drives the walkie's TX light.
- GTNH Voice clears keyed activations when a voice session ends, so a stuck key can't open the mic on the next connection.

### 13.5 Client audio

- **One `audio().playbackChain(...)`** with a `when()` branch per row of the table in [4.3](#43-how-it-sounds).
- **Each branch** matches frames whose `groupName()` starts with `becom:` and whose level, looked up by `(sourceId(),
  groupName())` in BeCom's client-side table, is that row's level.
- **The table** is filled by a BeCom packet each time the level a player hears a source at changes. A player hearing a
  source through someone else's walkie or a Transceiver gets that device's level.
- **Branch state:** `when()` rebuilds a branch's filter state each time it's entered again. The server's hysteresis and
  once-per-second evaluation keep that rare.
- **Talking animation:** a `frame()` stage on the call branches measures each source's loudness for the front camera
  ([10.1](#101-video)).

### 13.6 Other sounds

Ringing, tones and the radio's squelch tail are Minecraft sounds, not voice.

## 14. Server architecture

- **Network data:** one `WorldSavedData` per dimension holds every node (pylons, terminals, repeaters, routers, antennas,
  dishes, relays, Transceivers), with its position and links.
- **Global data:** the Overworld's data holds the phone number registry, and each satellite's dimension and launch time.
- **Online status:** powered nodes report from their tile entity tick. A node not heard from for 2 seconds counts as
  offline.
- **Rockets:** rockets are Galacticraft's entities, so BeCom can't tick them. It keeps track of the `EntityAutoRocket`
  entities in loaded chunks as they join their world, and checks each one's pad, launch phase and cargo once per second
  ([9.3](#93-rocket-radio-receiver)).
- **Video ([10.1](#101-video)):**
  - Front-camera poses go out about 10 times a second to the others in each video call.
  - Rear-camera frames are relayed as they are, dropped per viewer by level and dropouts, and never decoded.
  - Both travel on BeCom's own network channel. If that turns out to lag the game connection, they move to GTNH Voice's
    UDP transport, which would need a new API there.
- **Path finding:** a call's best path is a widest-path search (maximize the weakest hop's SNR). It's re-run only when a
  node, a link or an access level changes.
- **Client input:** the server caps each client's rear-camera frames by size and rate, and checks every phone-screen
  action against what that player may do, so a modified client can't flood the server or act for someone else.
- **Threading:** surveys, access evaluation, clearance checks, path finding and snapshot building all run on the server
  thread, within the budgets in [6.5](#65-budget). Only the snapshot crosses to the network thread.

### 14.1 Machine IDs

- BeCom's GT MetaTileEntities use IDs **25000–25199** (D18).
- How the range was chosen: GT5-Unofficial 5.09.54.205, with all its merged addons, was booted and every registered ID
  listed. GT's static ID table was also checked, because it includes machines GT registers only when certain mods are
  present. The largest free block is 20001–29989, where the static table only uses 28001–28055. 25000–25199 sits far
  from both edges.
- Nobody reserves this range for BeCom. Re-check it whenever BeCom moves to a new GT5-Unofficial version.
- At startup, BeCom checks every ID it is about to use. If another mod already took one, BeCom stops the game with an
  error naming that machine, instead of letting two machines share an ID and corrupt worlds.

## 15. Assets

### 15.1 Textures

From D32:

- **GT's textures wherever possible:** machine bodies use GT's hull textures, structures are GT blocks
  ([3.2](#32-structures)), and screens use GT's theme. A resource pack that restyles GT restyles those parts of BeCom
  for free. That includes Modernity, with its connected textures and its UI packs.
- **BeCom's own art** is small:
  - front overlays for its 13 machines, drawn as transparent layers over GT's hull like GT's own overlays, with `_GLOW`
    variants for lights;
  - about 8 item icons;
  - the Telephone's model and the Phone Cable.

  All of it lives under `assets/becom/textures/`, at 16×16 like GT.
- **Default style:** ships in the jar.
- **Modernity style:** lives in the repo under `resourcepacks/modernity/`. CI zips it as `Modernity-GTNHAddon-BeCom.zip`
  and attaches it to each release. Players stack it above Modernity, like Modernity's other add-ons. It can be offered
  to ModernityGTNH upstream later.
- **No pack detection in code:** pack file names change, and detection would mean shipping both styles in the jar.
- **Who makes it (D53):** Claude Code generates all of it, in both styles, alongside each milestone's content, and the
  owner reviews it in-game. The Modernity style uses Modernity's textures only as a reference for style and palette;
  none of them are copied.

### 15.2 Sounds

Claude Code synthesizes every sound (D53), so none of them needs a license. The call tones follow North America's
precise tone plan, which most players know from films and games:

| Sound                | How it's made                                                    |
|----------------------|------------------------------------------------------------------|
| Dial tone            | 350 + 440 Hz, continuous                                         |
| Ringback             | 440 + 480 Hz, 2 s on, 4 s off                                    |
| Busy                 | 480 + 620 Hz, 0.5 s on, 0.5 s off                                |
| Unreachable          | The three rising special information tones (950, 1400, 1800 Hz) |
| Ringtone             | A synthesized two-tone ring                                      |
| Silent-mode buzz     | A short low buzz                                                 |
| Hang-up click        | A short click                                                    |
| Walkie squelch tail  | A short burst of filtered noise at the end of each transmission  |
| Rocket launch        | A rising rumble of filtered noise                                |

### 15.3 Languages

Every player-facing string goes through the language files from the first milestone. English (`en_US`) always, Italian
(`it_IT`) by 1.0.

### 15.4 Guide

An in-game guide through GuideNH, which GTNH already ships (D48). It covers the tiers, how signal works and how to read
the Signal Analyzer. The questline ([15.5](#155-questline)) is where players start; the guide is for those who want the
details. Written for 1.0.

### 15.5 Questline

BeCom isn't in GTNH's quest book, so it brings its own tab (D49).

- **How it gets there:**
  - BetterQuesting (BQ) stores quests and questlines by UUID. BeCom carries its questline in GTNH's quest file format,
    with fixed UUIDs, so it can never collide with GTNH's quests.
  - BeCom adds the questline once server start has finished: after BQ has loaded the world's quests, and after the
    automatic reload BQ does whenever the pack version goes up. That reload wipes anything not in `DefaultQuests`.
  - BQ fires no event after `/bq_admin default load`, so BeCom checks every minute and adds the tab back if it's gone.
  - BeCom only ever touches its own questline and quests. The UUIDs never change, so players keep their progress.
  - If anything goes wrong, BeCom logs it and carries on, without the quests.
- **Off switch:** a server config option stops BeCom from touching the quest book, for servers with a curated book.
- **For pack makers:** the same quest files are published in GTNH's `DefaultQuests` layout, so a pack can merge them
  into its own book.
- **One caveat:** an admin who exports their quest book with `/bq_admin default save` while BeCom is installed bakes
  BeCom's quests into their own files.
- **BQ is optional:** without it, nothing happens.
- **Text:** BQ looks up `betterquesting.quest.<id>.name` and `.desc` in the language files first, so quest text lives in
  BeCom's own language files ([15.3](#153-languages)): English, and Italian by 1.0.
- **Shape:** one "Better Communications" tab, ordered by tier: LV wired, MV radio and Videophone, HV phones and space,
  EV ME. About 25 quests, short and in GTNH's style, each pointing to its guide page for depth. No prerequisites on
  GTNH's own quests, since their IDs can change between pack versions.
- **Tasks:** item quests use BQ's own item tasks. The quests that teach a mechanic use BeCom's own task type (D50), which
  BeCom completes when it happens:
  - a first call, a first call from a mobile phone, a first video call, a first ME Phone call through an ME Bridge;
  - a first radio contact;
  - a Portal Relay pair linked;
  - a first satellite launched, and 5 satellites over one dimension;
  - a first call between dimensions.
- **Rewards (D51):** like GTNH's own quests. Most give a lootbag of the quest's tier (`enhancedlootbags`); the big steps
  add Technician coins (`dreamcraft`), sometimes as a choice. They're balanced against GTNH's quests of the same tier.
  A reward whose item isn't installed is left out.
- **Timing (D52):** quest support and the LV quests come in 0.1. Each milestone then adds the quests for its own
  content, so no quest ever describes a feature that doesn't exist yet.

## 16. Roadmap

BeCom stays private until 1.0 (D33). Milestones are tracked here, with no public releases and no version tags before
1.0, since the release workflow publishes on tags.

Each milestone ends with:

- unit tests for its math;
- the CI server boot check;
- a two-player test on a dev server.

Each hard dependency (D27) joins the build with the milestone that first uses it: AE2 and Baubles in 0.3, Galacticraft
in 0.4. Each milestone also brings the textures and sounds for its own content ([15](#15-assets)).

Each milestone starts by trying out what it relies on that nobody has tested yet:

- **0.1:**
  - Repeater and Router heads taking power straight from a cable. GT controllers normally only take power through
    energy hatches, so this needs a custom controller.
  - Adding the questline through BQ's API on a GTNH server, including right after a pack-version reload.
- **0.3:**
  - the rear camera's frame copy with Angelica and shader packs;
  - drawing a far-away player's skin from their profile;
  - the ME Bridge connecting to ME cable.
- **0.4:** rocket detection on a real Galacticraft install, so far only read from its code.

| Milestone       | Contents                                                                                                   | Done when |
|-----------------|------------------------------------------------------------------------------------------------------------|-----------|
| 0.1 Wired calls | Machine ID check. Pylon, Line Terminal, Repeater, Router, Phone Cable, Telephone and their recipes. Numbers, contacts and the phone screen. `becom:call` routing, line loss, levels, dropouts and call audio. Ownership, linking rules, block list, do-not-disturb, rate limit. Quest support (BeCom's task type, the off switch) and the LV quests. | Two players talk over a 300-block line with one repeater, and the audio degrades and drops past the limits in [5.7](#57-line-loss). |
| 0.2 Radio       | Height cache, radio model, surveys. Antennas T1–T3. Transceiver, Walkie-Talkie, push-to-talk, channels, capture effect, walkie speaker. Signal Analyzer overlay, `/becom signal`. The radio quests. | Two walkies talk directly and through an antenna, and the analyzer explains every loss. |
| 0.3 Phones      | Mobile Phone, Baubles slots, ringing and the Phone key, group calls, weather, coverage map, battery drain. ME Phone and ME Bridge. Video calls: front and rear camera, the corner window, the Videophone. The quests for phones, ME and video. | A mobile phone calls a Telephone through an antenna bridged onto a pylon line, and a video call between a mobile phone and a Videophone shows both sides. |
| 0.4 Space       | Launch Pad, Satellite, Satellite Rocket, decay, sky hops. Satellite Dish, Rocket Radio Receiver, Portal Relay. Calls between dimensions. The space quests. | A call connects from the Overworld to the Moon, and to the Nether. |
| 1.0 Polish      | A final pass over art and sounds, the Modernity add-on pack, Italian, the GuideNH guide, WAILA info, a balancing pass, profiling on a big server, README. | First public release. |

## 17. Tunables

| What                                    | Value                         |
|-----------------------------------------|-------------------------------|
| Level thresholds (Low, Medium, High, Max) | 6, 12, 18, 24 dB            |
| Level hysteresis                        | 2 dB                          |
| Dropouts                                | 30% at 6 dB, down to 0% at 12 dB; mean burst 60 ms |
| Drop after                              | 3 s below 6 dB                |
| Pylon height                            | 6 to 16 blocks                |
| Pylon span                              | 4 × the shorter pylon's height |
| Pylon clearance re-check                | every 60 s                    |
| Line budget, line loss                  | 30 dB, 0.08 dB per block      |
| Phone Cable length                      | 32 blocks                     |
| Terminal, Repeater, Router power        | 4, 4, 16 EU/t                 |
| Antenna link budget (T1, T2, T3)        | 42, 48, 54 dB                 |
| Antenna power (T1, T2, T3)              | 32, 128, 512 EU/t             |
| Direct-link budget (Transceiver, Walkie) | 48, 44 dB                    |
| Wavelength (VHF, UHF)                   | 3, 1 blocks                   |
| Cover loss (VHF, UHF)                   | 1.5, 2.5 dB per block, cap 40 dB |
| Terrain loss cap                        | 40 dB                         |
| Weather loss (rain, thunderstorm)       | 3, 6 dB                       |
| Noise floor without a sky (Nether)      | 10 dB                         |
| Survey: bearings, step, refresh         | 64, 4 blocks, 5 min           |
| Survey budget                           | 2 ms per tick                 |
| Transceiver loudspeaker range           | 16 blocks                     |
| Transceiver talk range                  | 2 blocks                      |
| Walkie-talkie speaker range (D21)       | 6 blocks                      |
| Capture: hold, takeover margin (D24)    | 100 ms, 3 dB                  |
| Direct-hearing range (D17)              | 16 blocks, or GTNH Voice's proximity range if smaller |
| Phone number length                     | 6 digits                      |
| Group call size                         | 8                             |
| Timed-out session grace                 | 10 s                          |
| Satellites for Low, for Max             | 5, 100                        |
| Satellite lifetime                      | 100 Minecraft days, ± 20%     |
| Satellite Dish power                    | 512 EU/t                      |
| Rocket Radio Receiver uplink level      | High                          |
| Portal Relay power, portal distance     | 32 EU/t, 4 blocks             |
| Portal Relay hop level                  | High                          |
| Satellite launch                        | 30 s at 480 EU/t, 1,000 L fuel |
| Dish maintenance penalty                | 2 dB per problem              |
| Antenna mast height                     | 4 to 32 frame boxes           |
| Wrench-link distance (dish or antenna to a node) | 16 blocks            |
| Half-made link kept for                 | 5 min                         |
| Default keys (radio, phone)             | Caps Lock, N                  |
| Ringing before "no answer"              | 30 s                          |
| Call attempts per caller                | 5 per minute                  |
| Walkie battery; drain standby, receiving, transmitting | 300,000 EU; 1, 2, 4 EU/t |
| Phone battery; drain standby, in a call | 1,200,000 EU; 4, 16 EU/t      |
| Analyzer overlay refresh                | 1 s                           |
| Video: front-camera pose updates        | 10 per second                 |
| Video: front-camera height (Medium, High, Max) | 48, 96, 192 px         |
| Video: front-camera frame rate (Medium, High, Max) | 5, 12 fps, every frame |
| Video: rear camera (High, Max)          | 128×72 at 2 fps, 192×108 at 5 fps |
| Videophone power, room, loudspeaker     | 32 EU/t, 4 blocks, 16 blocks  |
| Videophone hangs up after an empty room | 30 s                          |
| Rear-camera cap per client              | 8 KB per frame, 6 frames per second |
| ME Bridge power, ME channels            | 512 EU/t, 1                   |
| Questline re-check                      | every 60 s                    |
| Machine ID range                        | 25000–25199                   |

## 18. Open questions

None right now.
