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
