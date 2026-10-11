# Better Communications: design

Draft 3, 2026-10-11. This rewrites the original Google Doc draft, adds the decisions made since, and fits everything to
the GTNH Voice 0.10.1 API. From now on this file is the source of truth.

Every choice is in the [decision log](#2-decision-log): **decided** means settled with the owner, **proposed** means it
still needs a yes or a change. Every number lives in [Tunables](#13-tunables) and is a starting point for playtesting,
not a promise.

## 1. What BeCom is

A GT New Horizons addon that adds a communications tech tree on top of GTNH Voice: wired telephones on pylon lines at
LV, radio from MV, mobile phones and satellites at HV, ME-backed phones at EV. Every call is real voice carried by GTNH
Voice. BeCom decides who hears whom, and how good it sounds.

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
without anyone reserving anything for it ([12.1](#121-machine-ids)).

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
| D17 | When a listener is within 16 blocks of the speaker, BeCom doesn't route the call or radio audio; they hear the speaker directly. | decided  |
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

## 3. Progression

| Stage    | Content                                                                    |
|----------|----------------------------------------------------------------------------|
| Early LV | Pylon, Line Terminal, Repeater, Router, Phone Cable, Telephone             |
| Late LV  | Antenna T1                                                                 |
| Early MV | Transceiver, Signal Analyzer                                               |
| Mid MV   | Walkie-Talkie, Antenna T2, Portal Relay                                    |
| Early HV | Mobile Phone                                                               |
| Mid HV   | Antenna T3, Rocket Radio Receiver                                          |
| Late HV  | Satellite Rocket, Satellite Launch Pad, Satellite Dish                     |
| Late EV  | ME Phone                                                                   |

Recipes are GT machine recipes at the listed tier. The actual recipes come later.

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

The listener's client filters audio that arrived through a BeCom group ([11.5](#115-client-audio)). Starting recipes,
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
  Otherwise, link by hand: GT wrench on head A, then on head B. Wrench on a linked head to unlink.
- **Clearance (D20):** the straight line between two heads must be free of solid blocks when they're linked. Every 60
  seconds, each link whose whole length is in loaded chunks is checked again. A blocked link goes down, and its wire
  renders broken, until the blocks are cleared. Links running through unloaded chunks keep their last state.
- **Wire:** rendered between linked heads as a sagging line. Its cost is part of the head recipe.
- **Passive:** no power needed. A line works through unloaded chunks as long as its powered nodes are loaded (D11).

### 5.2 Line Terminal

Ground block where a line starts or ends (the draft's "inizio e fine linea"). ULV, 4 EU/t. Links to one Pylon Head
(wrench) and connects to exactly one Telephone through Phone Cable (D19). A line needs a terminal at each end.

### 5.3 Repeater

A Pylon Head variant that takes power from a cable on any side (ULV, 4 EU/t). It regenerates the signal, so line loss
starts over after it ([5.7](#57-line-loss)).

### 5.4 Router

A Pylon Head variant with 15 link slots (LV, 16 EU/t). It also regenerates the signal. Routers turn lines into a
network: without one, a line connects exactly two telephones; with routers, any telephone can dial any other telephone
on the connected network.

### 5.5 Phone Cable

Cable block made from red alloy wire (D8). Connects a Telephone to a Line Terminal, up to 32 blocks of cable. Lossless.

### 5.6 Telephone

- **Block.** Right-click picks up the handset. You can move 1 block away (the cord). Further than that hangs up.
- **On a 1 ↔ 1 line:** picking up rings the other end.
- **On a routed network:** picking up opens the dialer ([8.2](#82-calls)).
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

- `B`: the hop's link budget in dB ([13](#13-tunables)). +6 dB doubles the range on open ground.
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

- For 64 bearings, step outward in 4-block steps up to the antenna's maximum range, and record the running horizon: the
  obstacle that rises highest above the line of sight so far, and its distance.
- During the same survey, compute the links to other antennas in range.

A player's link to an antenna is then cheap: find the bearing and distance, read the horizon obstacle in front of the
player, compute `ν` from the player's real eye height, and add cover loss at the player's position.

### 6.4 Direct links

Radio to radio with no antenna in between (D9): walkie to walkie, or walkie to Transceiver. At least one end moves,
so there's nothing to precompute. While someone is transmitting, each listener's link is evaluated once per second by
sampling the height cache every 4 blocks along the path and taking the worst obstacle.

### 6.5 Budget

- Player access links: once per second for each player holding a working device, spread evenly over the 20 ticks.
- Surveys: spread over ticks, at most 2 ms per tick in total.
- Direct links: only for transmitters that are currently keyed.

A T3 survey is 64 bearings × 64 steps, about 4,000 height-cache reads. A player's access evaluation is one horizon
lookup per antenna in range.

### 6.6 Making it learnable

- **Signal Analyzer** (early MV, handheld): shows SNR, level, the serving antenna, and the biggest loss (distance,
  terrain, cover, weather or noise).
- **Antenna GUI:** a coverage map drawn from the survey, showing expected SNR at standing height around the antenna.
- **`/becom signal`:** the same breakdown in chat, for testing and debugging.

## 7. Antennas

- **Structure:** a base, a mast of frame boxes with variable height, and an antenna head as the controller. The survey
  uses the head's height, so a taller mast on a hill covers more.
- **Tiers:** T1 (late LV), T2 (mid MV), T3 (mid HV). Link budgets of 42, 48 and 54 dB, which give roughly 64, 128 and
  256 blocks to the edge of Low on open ground: the draft's ranges.
- **Power:** 32, 128 and 512 EU/t. Offline when unpowered or unloaded (D11).
- **What an antenna does:**
  - Access point for wireless devices in range, on both bands.
  - Repeater for all 16 radio channels.
  - Bridge for wired lines: an antenna can be wrench-linked to a Pylon Head or Line Terminal, which carries the wired
    signal over radio, as in the draft.
  - Links to other antennas wherever the survey finds at least Low, separately for each band: radio channels travel
    between antennas on VHF, calls on UHF.

## 8. Wireless devices

| Device        | Stage    | Band | Cap    | Power             | Talking                          |
|---------------|----------|------|--------|-------------------|----------------------------------|
| Transceiver   | Early MV | VHF  | Medium | MV, 32 EU/t       | push-to-talk within 2 blocks     |
| Walkie-Talkie | Mid MV   | VHF  | Medium | battery           | push-to-talk key                 |
| Mobile Phone  | Early HV | UHF  | High   | battery           | your usual voice settings, in calls |
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

## 9. Space and other dimensions

### 9.1 Satellites

- **Launch (D6, D16):** the Satellite Launch Pad (late HV multiblock) launches a Satellite Rocket, using rocket fuel,
  into orbit over the pad's dimension. Every dimension with a sky keeps its own constellation.
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
- Wrench-linked to an antenna, Pylon Head or Line Terminal, which joins it to that network.
- While its dimension has at least 5 satellites, it links its network to every other dish in the same dimension. In the
  Overworld it also links to every planet's uplink ([9.3](#93-rocket-radio-receiver)). These are sky hops, so rain at
  the dish weakens them.
- This is the backbone half of D5: two networks far apart, each with a dish, become one.

### 9.3 Rocket Radio Receiver

- An upgrade for Galacticraft rockets (D6), carried in the rocket's cargo.
- While the rocket is landed in a dimension other than the Overworld and its chunk is loaded, it acts as a T1 antenna
  there, and as that dimension's uplink to the Overworld's satellites. It needs no power.

### 9.4 Portal Relay

- Mid MV machine, 32 EU/t (D22). Works when it's within 4 blocks of a Nether portal, an End portal or the End's exit
  portal.
- Two relays on opposite sides of a portal pair the same way pylons link: wrench on one, carry the wrench through the
  portal, wrench on the other.
- A linked pair is a fixed High hop between the two dimensions' networks.

### 9.5 Calls between dimensions

- **Overworld and a planet:** needs at least 5 satellites over the Overworld, and a landed rocket with a Rocket Radio
  Receiver on the planet. On the Overworld side, the call reaches devices under open sky directly, and whole networks
  through a Satellite Dish. The gateway hop's level is the lower of the Overworld end's sky hop (the device's or the
  dish's) and the receiver's uplink (High).
- **Planet and planet:** through the Overworld's satellites, so both planets need a receiver.
- **Overworld and the Nether or the End:** through a linked pair of Portal Relays.
- Longer chains combine these, for example Nether to Overworld to planet.

## 10. Calls and channels

- **Call states:** idle, dialing, ringing, connected, ended. Dial tone, ringback, busy, unreachable and hang-up are
  ordinary Minecraft sounds.
- **One call at a time** per player in v1.
- **Unreachable** means: no voice session, no device with service, or a link at None.
- **Levels** are per direction: what A hears from B uses the link from B to A. Calls through antennas come out the same
  both ways; direct radio links can differ, since a Transceiver transmits further than a walkie. All re-evaluated once
  per second.
- **Logout** ends the player's calls.
- **A voice session that times out** ends the player's calls after a 10-second grace period, unless the voice comes back.

## 11. GTNH Voice integration (0.10.1)

### 11.1 Groups

Registered in `FMLServerStartingEvent` on every server start through `GtnhVoiceApi.groupManager().registerGroup(...)`.
GTNH Voice has no unregistration, so the set is fixed.

| Group                                | Display name           | Priority | Carries                              |
|--------------------------------------|------------------------|----------|--------------------------------------|
| `becom:call`                         | Call                   | 200      | every call: telephone, mobile, ME    |
| `becom:radio.1` … `becom:radio.16`   | Radio 1 … Radio 16     | 100      | radio channels                       |

Calls beat radio, radio beats proximity. GTNH Voice's global group stays above everything.

### 11.2 Membership and sessions

- A speaker's frames reach a group's `route()` only if the speaker is a member, so:
  - joining `becom:call` when a call connects, and leaving when it ends;
  - joining `becom:radio.N` while carrying a working walkie tuned to N (inventory or Baubles), or standing at a
    Transceiver tuned to N.
- Who receives the audio is decided inside `route()`, from the routing snapshot ([11.3](#113-routing)).
- GTNH Voice wipes memberships on logout and on a timed-out session. BeCom listens through
  `GtnhVoiceApi.addSessionListener(...)`:
  - `sessionStarted`: re-join the player's groups.
  - `sessionEnded` with `LOGOUT` or `TIMED_OUT`: apply the call rules in [10](#10-calls-and-channels).
  - `REPLACED` keeps memberships, so nothing to do.
- `GtnhVoiceApi.session(uuid)` answers "does this player have voice?" for the unreachable check.

### 11.3 Routing

- **Snapshot:** the server thread builds an immutable routing snapshot whenever something changes: calls, channel
  members, per-direction levels and dropout settings, walkie holders and their channels, Transceiver positions. It's
  published through a volatile field.
- **`route()`** runs on GTNH Voice's network thread and reads only the snapshot and the `RoutingContext`, never the world.
- **`becom:call`:**
  - For each other participant at Low or better, unless they're within 16 blocks of the speaker (D17), apply the dropout
    model.
  - If the frame survives, `sendTo(listener, SourceState.FLAT, ...)`.
- **`becom:radio.N`:**
  - Only frames whose `getAudio().getActivationId()` equals `ActivationIds.of("BeCom", "radio")`, meaning the speaker
    is holding push-to-talk.
  - Listeners need a link at Low or better, pass the capture rule (D24), get the dropout model, and are skipped within
    16 blocks of the speaker (D17).
  - First, FLAT to walkie holders tuned to N.
  - Then POSITIONAL from each walkie holder who got the frame to players within 6 blocks of them (D21).
  - Then POSITIONAL from each Transceiver on N that got the frame to players within its 16-block loudspeaker range.
  - GTNH Voice keeps only the first `sendTo` per player per frame, so this order decides which path a player hears,
    and nobody hears a channel twice.
- **Per-frame state:** the dropout model (one Gilbert-Elliott state per speaker-listener pair) and the capture state
  (who each listener is hearing on each channel, and when their last frame arrived) live in BeCom's own concurrent maps.
  Non-blocking, as `route()` requires.

### 11.4 Push-to-talk

- The client registers the addon with `GtnhVoiceClient.addon("BeCom").register()`, then takes its
  `activation("radio")`.
- Every client tick, the push-to-talk handler calls `setKeyed(keyHeld && hasUsableRadio)`.
- `isTransmitting()` drives the walkie's TX light.
- GTNH Voice clears keyed activations when a voice session ends, so a stuck key can't open the mic on the next connection.

### 11.5 Client audio

- **One `audio().playbackChain(...)`** with a `when()` branch per row of the table in [4.3](#43-how-it-sounds).
- **Each branch** matches frames whose `groupName()` starts with `becom:` and whose level, looked up by `(sourceId(),
  groupName())` in BeCom's client-side table, is that row's level.
- **The table** is filled by a BeCom packet each time the level a player hears a source at changes. A player hearing a
  source through someone else's walkie or a Transceiver gets that device's level.
- **Branch state:** `when()` rebuilds a branch's filter state each time it's entered again. The server's hysteresis and
  once-per-second evaluation keep that rare.

### 11.6 Other sounds

Ringing, tones and the radio's squelch tail are Minecraft sounds, not voice.

## 12. Server architecture

- **Network data:** one `WorldSavedData` per dimension holds every node (pylons, terminals, repeaters, routers, antennas,
  dishes, relays, Transceivers), with its position and links.
- **Global data:** the Overworld's data holds the phone number registry, and each satellite's dimension and launch time.
- **Online status:** powered nodes report from their tile entity tick. A node not heard from for 2 seconds counts as
  offline. A landed rocket with a receiver reports the same way from its entity tick.
- **Path finding:** a call's best path is a widest-path search (maximize the weakest hop's SNR). It's re-run only when a
  node, a link or an access level changes.
- **Threading:** surveys, access evaluation, clearance checks, path finding and snapshot building all run on the server
  thread, within the budgets in [6.5](#65-budget). Only the snapshot crosses to the network thread.

### 12.1 Machine IDs

- BeCom's GT MetaTileEntities use IDs **25000–25199** (D18).
- How the range was chosen: GT5-Unofficial 5.09.54.205, with all its merged addons, was booted and every registered ID
  listed. GT's static ID table was also checked, because it includes machines GT registers only when certain mods are
  present. The largest free block is 20001–29989, where the static table only uses 28001–28055. 25000–25199 sits far
  from both edges.
- Nobody reserves this range for BeCom. Re-check it whenever BeCom moves to a new GT5-Unofficial version.
- At startup, BeCom checks every ID it is about to use. If another mod already took one, BeCom stops the game with an
  error naming that machine, instead of letting two machines share an ID and corrupt worlds.

## 13. Tunables

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
| Direct-hearing range (D17)              | 16 blocks                     |
| Phone number length                     | 6 digits                      |
| Group call size                         | 8                             |
| Timed-out session grace                 | 10 s                          |
| Satellites for Low, for Max             | 5, 100                        |
| Satellite lifetime                      | 100 Minecraft days, ± 20%     |
| Satellite Dish power                    | 512 EU/t                      |
| Rocket Radio Receiver uplink level      | High                          |
| Portal Relay power, portal distance     | 32 EU/t, 4 blocks             |
| Portal Relay hop level                  | High                          |
| Machine ID range                        | 25000–25199                   |

## 14. Open questions

1. **Recipes:** tiers are set above; the actual GT recipes are still to do.
2. **Rockets:** which rocket entities count for the Rocket Radio Receiver? Galacticraft's tiered rockets for sure;
   GTNH also has rockets from other mods (GalaxySpace), to be checked when the integration is built.
