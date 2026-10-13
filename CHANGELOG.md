# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/2.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Wired calls: the Pylon Head with its Repeater and Router variants, the Line Terminal, Phone Cable and the Telephone,
  with GregTech recipes.
- Calls carried by GTNH Voice through the `becom:call` group: line loss, signal levels with hysteresis, burst dropouts,
  and call audio filtered by level on the listener's client.
- Six-digit phone numbers, contacts by owner name, recent and missed calls, do-not-disturb, hiding from contacts, block
  lists and a call rate limit, on a phone screen built with ModularUI2 in GT's theme.
- Wrench linking with owner-only unlinking, pylons that link on their own when there's no doubt, and clearance checks
  that take a blocked line down.
- BetterQuesting support: BeCom's own task type, its questline added to the book (with a config switch to leave the book
  alone), and seven LV quests.
- A config file and saved data with format versions, and a startup check of BeCom's GregTech machine IDs. Saved data
  that is newer, holds kinds BeCom doesn't know, or can't be read stops the server instead of being saved over.
- Clients show the server's tunables in tooltips and holograms, sent at login.
- `/becom calls` and `/becom hangup` for operators.
- Generated textures in GT's style and a Modernity add-on pack, and synthesized call tones, ringtone and hang-up click,
  the handset's in stereo so they sit at the ear.
- Radio: Antennas T1 to T3 (multiblocks on a frame-box mast over a casing base), the Transceiver, the Walkie-Talkie
  and the Signal Analyzer, with GregTech recipes.
- A radio simulation from DESIGN.md 6: link budgets, knife-edge diffraction over terrain from a height cache filled
  as chunks load and save, cover below the surface, and a higher noise floor without a sky. Antennas survey the land
  around them within a per-tick budget and link to each other on VHF for radio and UHF for calls.
- Sixteen radio channels through GTNH Voice's `becom:radio.1` to `becom:radio.16` groups, with a push-to-talk key
  (Caps Lock), direct links between radios, paths through antennas, the capture effect per listener, players around
  a receiving walkie hearing it, Transceiver loudspeakers, radio audio filtered by level, and a squelch tail. Radio
  links keep their level with the same hysteresis as calls: one that falls below 4 dB cuts out at once, and one
  between 4 and 6 dB holds at Low for 3 s, then drops.
- Antennas bridge wired lines within 16 blocks and carry calls between them over UHF, so a Telephone can call across
  linked antennas.
- Antenna screen with its links, bridges and a team lock; Transceiver and walkie screens; sneak + scroll to change a
  walkie's channel; walkie battery drain, and a flat walkie switching off.
- The Signal Analyzer's overlay and `/becom signal`: SNR, level, the serving antenna and every loss on the link to it.
- Seven radio quests, completed by BeCom's new Radio contact task among others.
- Line re-checks share the survey budget per tick with antenna surveys.
- The config refuses NaN and Infinity in its decimal values, naming the key.
- Phones: the Mobile Phone, an HV handheld with service through antennas on UHF and a cap of High. It rings from its
  player with a notice in the corner, buzzes only for its holder in silent mode, drains its battery on standby and more
  in a call, and works from the inventory or a bauble slot, as walkies do.
- The Phone key (N): answers a ringing phone, the player's own or a Telephone or Videophone close enough to answer,
  hangs up during a call, opens the phone screen otherwise; sneak + Phone rejects a call.
- Group calls of up to 8 people: anyone in a call adds a phone from the phone screen, a Telephone's included, and hears
  when it doesn't answer. The screen lists everyone in the call with their signal bars, everyone in a Videophone's
  room by name, and has power and silent mode for carried phones.
- Signal bars by the hotbar while a phone is held or during a call: the call's weakest level, or the phone's service,
  unlit with none.
- Rain and thunderstorms weaken calls between antennas and phones' service where they fall: 3 dB in rain, 6 in a storm.
- The antenna screen draws a coverage map: the level a player standing on the ground would get around the antenna,
  for radio channels or for calls.
- The ME Phone and the ME Bridge: an ME Phone linked to an ME network at a Security Terminal has service at Max inside
  that network's wireless range, calls other ME Phones on it directly, and any other phone through an ME Bridge,
  which joins the ME network on ME cable and is wrench-linked to a Pylon Head, a Line Terminal or an antenna.
- An ME network carries calls only within each dimension it spans: a Quantum Network Bridge carries none between them.
- The Videophone, an MV speakerphone for wired networks on a Line Terminal's Phone Cable: everyone within 4 blocks is
  in its call, which plays from its loudspeaker to everyone within 16 blocks, and it hangs up by itself 30 s after
  the last person walks off.
- Video calls between Mobile Phones, ME Phones and Videophones: the front camera shows each person's character as the
  server knows it, moving at the level's frame rate, noisy at Medium, frozen at Low; the rear camera streams what one
  person sees to the other in a call of two at High or better. Video shows in a corner window, full size on the
  phone screen's new Video tab with each camera's switch, and on a Videophone's screen for its room.
- Seven quests for phones, video and ME, with three new task types: Mobile call, Video call and ME Bridge call.
- The Signal Analyzer and `/becom signal` read both bands: what a radio gets on VHF and what a Mobile Phone gets on
  UHF, where weather shows.
- Space, with Galacticraft as a dependency: the Satellite, the Satellite Rocket (a Satellite on Galacticraft's Tier 1
  rocket parts) and the Rocket Radio Receiver, from the assembler.
- The Satellite Launch Pad, a 5 by 5 multiblock under open sky: each launch takes a Satellite Rocket and 1,000 L of
  rocket fuel, runs 30 s at 480 EU/t, and puts a satellite into orbit over its dimension. Anything covering the pad
  holds a launch, which goes on once the sky clears. Its screen shows the rockets waiting, a launch on hold, each
  dimension's satellites and when the next comes down. Satellites come down after about 100 days of world time, give
  or take a fifth.
- Sky hops: with at least 5 satellites over a dimension, Mobile Phones, walkies and Transceivers under open sky reach
  them, at Low with 5 and up to Max with 100, less a roof's cover and the rain. Radios under open sky hear each other
  through them. The Signal Analyzer and `/becom signal` show the satellites when they serve better than any antenna.
- The Satellite Dish, a multiblock wrench-linked to an antenna, a Pylon Head or a Line Terminal: it joins its network
  to every other dish in its dimension through the satellites, and in the Overworld to every planet's uplink. Rain at
  the dish and each maintenance problem weaken its hop.
- The Rocket Radio Receiver: in the cargo of a rocket standing on a launch pad on a planet, it makes the rocket a T1
  antenna there and the planet's uplink to the Overworld's satellites, at High.
- The Portal Relay, an MV machine that works by a Nether, End, Twilight Forest or Personal Space portal. Wrench one,
  go through the portal and wrench another: the pair joins the Overworld's network to the one beyond at High.
- Calls between dimensions, through relays, dishes and receivers, longer chains included.
- `/becom satellites` for operators: lists each dimension's satellites, or sets how many orbit one, for testing.
- Seven space quests, with four new task types: Portal Relay pair, First satellite, Five satellites and Call between
  dimensions.
- Textures for the space machines and parts in both styles, and a rocket launch sound.
- Italian: every string and the quest text.
- Each release comes with the Modernity add-on pack, `Modernity-GTNHAddon-BeCom.zip`, to put above Modernity.
- WAILA shows what GT's scanner shows for each BeCom machine, links listed one by one aside, in the looking player's
  language, and a Telephone's number, owner and whether it rings or its handset is lifted. Only a player close by gets
  BeCom's lines, so a modified client can't read a Telephone's number from afar.
- An in-game guide through GuideNH, in English and Italian: the tiers, how signal works, reading the Signal Analyzer,
  and a page each for the wired network, antennas, radios, phones, and space and other dimensions. Each quest ends with
  a link to its page, and holding GuideNH's key over a BeCom item or quest opens it. Without GuideNH, the links show
  their label alone.
- Three big quests offer their coins as a choice: First call against two Pylon Heads, Radio contact against a
  Walkie-Talkie, Five satellites against a Satellite Rocket. A choice keeps only the options whose items are installed.
- BeCom needs GTNH Voice 0.11.0 or newer, on the server and every client. Forge refuses to load it beside an older
  one, and names the version it needs.
- A radio contact counts only when the listener hears the speaker on the radio: not before their voice connection is
  up, and not when they already hear the speaker through a call.
- Walkie speakers and loudspeakers fade to silence at the edge of their range instead of cutting off there.
- The voice chat's speaker list shows BeCom's group names, Call and Radio 1 to 16, in each player's own language.
- On a server whose voice proximity range is under 16 blocks, calls and radio carry a speaker to the players beyond it.
