# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/2.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Milestone 0.1, wired calls: the Pylon Head with its Repeater and Router variants, the Line Terminal, Phone Cable and
  the Telephone, with GregTech recipes.
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
- `/becom calls` and `/becom hangup` for operators, and `/becom selftest` in the development environment.
- Generated textures in GT's style and a Modernity add-on pack, and synthesized call tones, ringtone and hang-up click,
  the handset's in stereo so they sit at the ear.
- Milestone 0.2, radio: Antennas T1 to T3 (multiblocks on a frame-box mast over a casing base), the Transceiver, the
  Walkie-Talkie and the Signal Analyzer, with GregTech recipes.
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
- Line re-checks now share the survey budget per tick with antenna surveys.
- The config refuses NaN and Infinity in its decimal values, naming the key.
- Milestone 0.3, phones: the Mobile Phone, an HV handheld with service through antennas on UHF and a cap of High. It
  rings from its player with a notice in the corner, buzzes only for its holder in silent mode, drains its battery on
  standby and more in a call, and works from the inventory or a bauble slot, as walkies now do too.
- The Phone key (N): answers a ringing phone, hangs up during a call, opens the phone screen otherwise; sneak + Phone
  rejects a call.
- Group calls of up to 8 people: anyone in a call adds a phone from the phone screen, which now lists everyone in the
  call with their signal bars, and has power and silent mode for carried phones.
- Rain and thunderstorms weaken calls between antennas and phones' service where they fall: 3 dB in rain, 6 in a storm.
- The antenna screen draws a coverage map: the level a player standing on the ground would get around the antenna,
  for radio channels or for calls.
- The ME Phone and the ME Bridge: an ME Phone linked to an ME network at a Security Terminal has service at Max inside
  that network's wireless range, calls other ME Phones on it directly, and any other phone through an ME Bridge,
  which joins the ME network on ME cable and is wrench-linked to a BeCom node.
- The Videophone, an MV speakerphone for wired networks on a Line Terminal's Phone Cable: everyone within 4 blocks is
  in its call, which plays from its loudspeaker to everyone within 16 blocks, and it hangs up by itself 30 s after
  the last person walks off.
- Video calls between Mobile Phones, ME Phones and Videophones: the front camera shows each person's character as the
  server knows it, moving at the level's frame rate, noisy at Medium, frozen at Low; the rear camera streams what one
  person sees to the other in a call of two at High or better. Video shows in a corner window, full size on the
  phone screen's new Video tab with each camera's switch, and on a Videophone's screen for its room.
- Seven quests for phones, video and ME, with three new task types: Mobile call, Video call and ME Bridge call.
