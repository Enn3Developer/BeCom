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
  (Caps Lock), direct links between radios, paths through antennas, the capture effect, players around a receiving
  walkie hearing it, Transceiver loudspeakers, radio audio filtered by level, and a squelch tail.
- Antennas bridge wired lines within 16 blocks and carry calls between them over UHF, so a Telephone can call across
  linked antennas.
- Antenna screen with its links, bridges and a team lock; Transceiver and walkie screens; sneak + scroll to change a
  walkie's channel; walkie battery drain, and a flat walkie switching off.
- The Signal Analyzer's overlay and `/becom signal`: SNR, level, the serving antenna and every loss.
- Seven radio quests, completed by BeCom's new Radio contact task among others.
- Line re-checks now share the survey budget per tick with antenna surveys.
