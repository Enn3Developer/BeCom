# Progress

The ledger of the cloud run that builds BeCom's milestones (DESIGN.md 16, D54). Every item of every milestone, with its
status: `todo`, `doing`, `done`, `fallback X<n>` (see [DEVIATIONS.md](DEVIATIONS.md)) or `blocked`. Each milestone ends
with its summary.

`done` means built, wired up and checked as far as the cloud can check it: by unit and routing tests, and by the
self-test in a real dev server where the item needs one. Anything that needs a game client or two players is also on
the milestone's owner checklist, and isn't proven until the owner has played it.

## 0.1 Wired calls

Branch `milestone/0.1-wired`, from `master`.

### Contents (DESIGN.md 16)

| Item                                                   | Status |
|--------------------------------------------------------|--------|
| Machine ID check (14.1)                                | done   |
| Config and saved-data versions (14.2)                  | done   |
| The self-test (16.3)                                   | done   |
| Pylon (5.1)                                            | done   |
| Line Terminal (5.2)                                    | done   |
| Repeater (5.3)                                         | done   |
| Router (5.4)                                           | done   |
| Phone Cable (5.5)                                      | done   |
| Telephone (5.6)                                        | done   |
| Their recipes (3.1)                                    | done   |
| Numbers (8.2, D25)                                     | done   |
| Contacts (8.2, D25)                                    | done   |
| Phone screen (11.2)                                    | done   |
| `becom:call` routing (13.3)                            | done   |
| Line loss (5.7)                                        | done   |
| Levels (4.1)                                           | done   |
| Dropouts (4.2)                                         | done   |
| Call audio (4.3, 13.5)                                 | done   |
| Ownership (12.1)                                       | done   |
| Linking rules (12.2)                                   | done   |
| Block list (12.3)                                      | done   |
| Do-not-disturb (12.3)                                  | done   |
| Rate limit (12.3)                                      | done   |
| Quest support: BeCom's task type (D50)                 | done   |
| Quest support: the off switch (15.5)                   | done   |
| CI runs on milestone branches, newer pushes cancel     | done   |
| CI's run-server job runs the self-test                 | done   |

### Untested assumptions (DESIGN.md 16.2)

| Assumption                                                         | Status |
|--------------------------------------------------------------------|--------|
| Repeater and Router heads take power straight from a cable         | done   |
| BeCom can add its questline through BQ's API, also after a reload  | done   |

### Quests (DESIGN.md 15.5)

| Item                    | Status |
|-------------------------|--------|
| The LV quests           | done   |

### Assets (DESIGN.md 15)

| Item                                                              | Status |
|-------------------------------------------------------------------|--------|
| Front overlays and glows: Pylon, Repeater, Router heads, Terminal | done   |
| Telephone model and textures                                      | done   |
| Phone Cable texture                                               | done   |
| Modernity style of all of the above                               | done   |
| Sounds: dial tone, ringback, busy, unreachable, ringtone, hang-up | done   |
| English language file                                             | done   |

### Summary

**Built.** Wired telephone calls on GregTech pylon lines, end to end on the server:

- Pylon, Repeater and Router heads (GT multiblock controllers on 6 to 16 steel frame boxes), the Line Terminal, Phone
  Cable and the Telephone, with their recipes. Repeater and Router heads take LV straight from a cable on any side,
  as 16.2 hoped.
- Linking by wrench, with the span limit checked against the pylons' current heights, the clearance check when
  linking and every 60 s after (spread over ticks), pylons that link on their own when exactly one is in range, and
  owner-only unlinking (operators too, X5).
- Six-digit numbers unique per world (kept by a moved Telephone, a fresh one for a copy), contacts by owner name,
  hiding, do-not-disturb, the block list and the rate limit, all checked by the server on every dial.
- The call graph: widest paths over line stretches whose SNR falls 0.08 dB a block from 30 dB, regenerated at each
  powered node, with phones only ever at a path's ends. Levels with hysteresis, capped at Low by the Telephone; bursty
  dropouts between 6 and 12 dB; a drop after 3 s below Low, for ringing and connected calls alike. Calls ring,
  connect (with routes from the first frame), and end on hang-up, no answer, rejection, a dropped line, logout, a lost
  voice session after 10 s of grace, a broken phone or an admin.
- `becom:call` routing at priority 200 on GTNH Voice's network thread, from an immutable snapshot, skipping direct
  hearers (D17, with V1's 16-block stopgap).
- The phone screen (keypad, contacts, recent calls for the owner and team, settings with a paged block list, signal
  bars), call tones as Minecraft sounds (the handset's in stereo), the client's call filters per level, signal bars by
  the hotbar, the Telephone model, and wires drawn between linked heads, broken ones hanging.
- BeCom's BetterQuesting task type, the questline added at server start and after BQ's own reload with progress
  kept, the off switch, and seven LV quests with rewards matched to GTNH's LV quests.
- Config and saved-data format versions, the Telephone's tile included. Newer data, data holding something this
  BeCom doesn't know, and files that can't be read all stop the server instead of being saved over (X16). Clients
  use the server's tunables, sent at login and checked like a config file's.
- The machine ID check in 25000-25199, and `/becom selftest`, which CI's run-server job runs.

**Tests.**

- 156 unit and routing tests, all passing. Logic: the line model with DESIGN.md's 225 and 300-block numbers (4),
  levels and hysteresis (5), dropouts (4), drop timer (4), widest path (4), call graph (3), wired topology (8), link
  rules (7), clearance (5), network model (4), call book (19), phone registry, blocking and history (13), numbers (5),
  rate limit (4), cord reach (5), cable walk (6), sweeps (3), owner rule (3), tunables and validation (10). Data:
  saved-data round trips, too-new, not-understood and damaged data, unreadable files loaded through vanilla's own
  MapStorage (17), the Telephone tile's NBT (4), the config file (5). Wire: the phone view (4), config sync (3).
  Routing: `becom:call` through GTNH Voice's `RoutingContext` (7).
- The self-test: 73 checks on a real dev server, 73 passing, also in CI. Machine IDs and the clash check, the voice
  group, the config defaults, saved-data guarding, recipes (including that Phone Cable can't take over GT's Red Alloy
  Cable), power from a cable on all six sides, forming at the shortest and tallest heights, auto-linking, wrench
  linking, clearance and its re-check, owner-only unlinking, the 300-block line with a repeater at 18 dB per stretch
  capped at Low, 260 blocks at 9.2 dB losing 14% of frames, 310 blocks out of reach, a whole call through the call
  service with fake players (pick up, dial, answer, routes from the first frame, the screen naming the far player,
  levels, hang up, history, an unanswered call recording the far phone's owner, do-not-disturb, block list, busy),
  restored ringing, moved phones keeping their numbers, copies taking their own with the same owner and settings, a copy
  staying silent and harmless during the original's call, a carried tile settling at its new spot, an original saved
  by a newer BeCom keeping its number,
  handsets put back after a restart, a switched-off repeater killing its line, and the questline surviving BQ's own
  `/bq_admin default load` with progress.

**Review.** Five rounds, each a design reviewer and a code reviewer side by side (D58): the milestone
(`master..dcc99c0`), then each batch of fixes in turn (`dcc99c0..ff05396`, `ff05396..698c1da`, `698c1da..73117b5`,
`73117b5..d771614`), as CLAUDE.md asks after fixes that touch saved data. One finding was critical, in round three, and
only after a downgrade. Each finding below was confirmed with a failing test, a self-test check or the code before it
was fixed, unless it says otherwise.

- First round, design review (2 major, 15 minor):
  - A Telephone on two terminals joined their lines into one network with no Router: fixed, phones are only ever a
    path's ends and a phone on two cables is served by neither (CallGraphTest; X2).
  - The Phone Cable recipe took over GT's Red Alloy Cable in a machine that last made Phone Cable: fixed with
    programmed circuit 2 (self-test, failing first; X12).
  - The first second of each call was silent: fixed, a call is evaluated as it connects (CallBookTest; self-test).
  - Handsets stayed off the hook after a server stop: fixed, released at stop and on load (self-test).
  - Unknown node and phone kinds were dropped and saved over: fixed, they stop the server (SavedDataTest).
  - Clients read their own config for tooltips and holograms: fixed, the server's values are sent at login
    (TunablesTest, ConfigSyncPacketTest).
  - Config values that crashed the server passed the check: fixed (TunablesTest).
  - The self-test overclaimed: it dialled around the call service with a hard-coded cap, and simulated BQ's reload.
    Fixed: it now dials through the call service with fake players and runs BQ's own `/bq_admin default load`.
  - Quest progress could lose up to a minute on a reload: fixed, copied every second (self-test).
  - Anything without an owner could be managed by anyone: fixed (OwnerRuleTest; X5, X6).
  - Anyone could read a Telephone's history: fixed, owner and team only (X11).
  - The block list showed 4 players with no pager, and the call screen showed text instead of bars: fixed.
  - An offline Repeater cuts its line: logged (X13), with the view-distance caveat on the owner checklist.
  - Quest text hard-codes defaults: logged (X14).
  - Reward balance was unchecked: checked against GTNH's DefaultQuests; LV matches, MV to EV amounts set from it.
  - The routing fixture uses GTNH Voice internals: the code change is rejected, because `RoutingContext.builder()`,
    which CLAUDE.md asks routing tests to use, only takes internal types. Logged as VOICE.md V4.
  - 0.2 files sat in the 0.1 working tree: moved to the session branch.
- First round, code review (2 major, 16 minor): the shared-phone router and the blocked-list pager (as above), the
  silent first second, stale handsets, config gaps and client config (as above), and:
  - the cord measured to the feet, so a desk phone hung up after 0.8 blocks: fixed (CordTest; X10);
  - a pylon cut down kept its long spans: fixed (WiredTopologyTest);
  - blocking a recent call blocked the phone's owner, not who called: fixed (PhoneRegistryTest; X9);
  - the block action took any number, leaking hidden owners: fixed (PhoneRegistryTest);
  - contacts were cut at 64: raised to 512 (PhoneViewTest; X9);
  - wires weren't resent after a respawn into another world: fixed (code; owner check);
  - the clearance re-check ran in one tick: fixed, sliced to 1 ms a tick (SweepTest);
  - the cable scan could read tens of thousands of blocks: fixed (CableWalkTest; X15);
  - timers ran on the wall clock: fixed, monotonic (code);
  - handset tones panned toward the world origin: fixed, stereo files (checked in vanilla's SoundManager);
  - a truncated data file came back empty and was saved over: fixed, it stops the server (SavedDataTest; X16);
  - ringing wasn't restored after a reload and rings didn't end with their path: fixed (self-test, CallBookTest);
  - vanished Telephones held their numbers: first fixed with a sweep that freed them, then reverted in round three
    (below), so this stays as it was, logged in X17.
- Second round, design review (1 major, 7 minor): the call screen named the far phone's owner, not the player on it:
  fixed (self-test, failing first). X5's wording was wrong and X9, X11 incomplete: corrected. Choices with no
  deviation entry: the cable count (changed back to a path length with a 128-block cap, X15), stopping on unreadable
  files (X16), the contacts cap (X9), rings dropping without the 3 s grace (fixed, CallBookTest), moved Telephones
  losing their numbers (fixed, self-test; X17). Unknown history directions dropped and the Telephone's NBT without a
  format version: fixed (SavedDataTest, TileTelephoneTest). The unreadable-file stop untested end to end: now tested
  through vanilla's MapStorage. Fixed per-tick budgets: left as a known issue for 0.2.
- Second round, code review (6 minor): a call answered in the hysteresis band was silent then dropped: fixed, answered
  calls start at Low (CallBookTest). DropTimer's -1 sentinel against a clock that may read negative: fixed
  (DropTimerTest). An old recent row could block a number's new holder: fixed, the owner at the time is recorded
  (PhoneRegistryTest). The read flag was set before parsing: fixed. Silent trimming of block lists, unknown history
  directions and over-slot links: fixed (SavedDataTest). Smaller: the phone sweep could pile up (fixed), the client
  didn't check synced values (fixed, ConfigSyncPacketTest), block actions had no rate limit (fixed, 60 a minute), a
  row's index could shift (fixed, rows go by time).
- Third round, design and code reviews (1 critical and 1 major from design, 1 major and 7 minor from code, mostly the
  same few): the stale-number sweep deleted the records of Telephones saved by a newer BeCom, and of phones carried
  for more than a minute. The sweep is removed and numbers are held for good (X17); a self-test check covers a newer
  original against a copy. A copy kept the original's number until used, could clash on cables and ring with it, and
  breaking it ended the original's call: copies now take a fresh number owned by the original's owner at once, and
  only the number's own phone rings or ends calls (self-test). Answering restarted the drop grace: fixed
  (CallBookTest). A newer Telephone wrote back its old coordinates: fixed (TileTelephoneTest), and its handling is in
  X16. History was cut on load, and invalid or repeated numbers were dropped: fixed (SavedDataTest). Synced values had
  no ceilings: pylon height and cable length are capped (TunablesTest, ConfigSyncPacketTest). Two tests that didn't
  prove their claim were fixed, and `phoneGone` keeps names until the call has ended.
- Fourth round, design (7 minor) and code (4 minor), both "ready": a renumbered copy lost its owner's settings, so
  players it blocked could see it (fixed, settings carried; self-test). History loaded long was cut at the next call
  (fixed, PhoneRegistryTest). A bad phone number was reported as a newer BeCom's (fixed, reported as damage;
  SavedDataTest). The ceilings would stop a client starting over its own file (fixed: checked when a server starts,
  clamped by Forge; BeComConfigTest). A mover keeping the tile object skipped a Telephone's settling (fixed). The
  break and ring guards had no test (two self-test checks added). Docs: X6, X16 and X17 corrected, X18 added.
- Fifth round, design (1 major, 3 minor) and code (1 major, 4 minor), the major the same in both: round four's
  deferred config check threw where FML swallows it, so in single-player a bad file left the client on "Loading
  world" or back at the menu with no word, and saved-data refusals did the same. Fixed: one refusal through FML's
  StartupQuery notice, which shows the reason and returns to the menu (owner check, since it needs a client). Also
  fixed: the config's format check deferred too, real ranges in the file with a warning for clamped values
  (BeComConfigTest), settling keyed by dimension, a self-test check for a carried tile, and X6, X16 and X18 corrected.
  These last fixes touch neither saved data nor threads, so no sixth round.

**Deviations and GTNH Voice.** DEVIATIONS.md X1 to X18 are 0.1's: the phone screen on sneak + right-click (X1), one
phone per terminal both ways (X2), terminal spans (X3), wrench clicks (X4), operators and ownerless machines (X5),
Telephone owners (X6), "no answer" (X7), answering needs voice (X8), blocking from rows (X9), the cord (X10), private
history (X11), Phone Cable's circuit (X12), offline repeaters (X13), quest text (X14), the cable cap (X15), stopping on
unreadable data (X16), numbers held for good (X17) and the config ceilings (X18). None is a 16.2 fallback: both 0.1
assumptions held. VOICE.md: V1's stopgap (direct hearing at 16 blocks) is in use; V4 (routing tests through API
types) is new. V2 and V3 belong to later milestones.

**Known issues.**

- Nothing client-side has run: the phone screen, the call filters, the HUD bars, the renderers, the tones and the
  textures compile and were reasoned about, and the textures were looked at next to GT's, but no game client ran in
  the cloud. The owner checks below cover them.
- The 300-block "Done when" line needs its Repeater's chunk loaded (X13): fine at view distance 10, not at 8.
- The clearance re-check's per-tick budget (1 ms) is fixed in code. 0.2 brings the survey budget into the config
  (2 ms) and should share it out.
- Direct hearing uses 16 blocks until GTNH Voice can report its proximity range (VOICE.md V1); on servers with a
  smaller range, players between it and 16 blocks hear neither. Fixed on `voice-api`: GTNH Voice 0.11.0 reports the
  range, and direct hearing stops at the smaller of the two (CallGroupTest, RadioGroupTest).
- Quest text shows DESIGN.md's default numbers whatever the server's config says (X14).
- The boot log has a GT-ERR-01 line about IC2 rubber that isn't BeCom's as far as I can tell; I didn't trace it.
- A single-player world refused over a bad config value or unreadable saved data should show the reason and go back
  to the menu; only a client can show it (owner check 11).
- Local builds here used a GTNH Nexus mirror for Maven Central (which rate-limited the VM), set in ~/.gradle on the VM
  only; the repo and CI use the normal repositories.
- A Telephone that vanishes without being broken (a rolled-back chunk) keeps its number reserved forever (X17).
  Nobody can reach it, so it only costs one number of 900,000.
- Settings changes on the phone screen are limited to 60 a minute per player, a protection rather than a DESIGN.md
  value.
- `phone/buzz.ogg` was generated ahead of 0.3, which uses it.

**Owner checks.** Each needs a game client, and some two players.

1. The "Done when": two players, a 300-block line with a Repeater halfway (Line Terminal, pylons of 16 frames every
   60 blocks or less, a powered Repeater head at 150, a Line Terminal at 300, a Telephone on Phone Cable at each end),
   at view distance 10 or with the Repeater chunk-loaded. Picking up one Telephone rings the other (it's the only one
   reachable); answering connects; both hear each other narrow and gritty (Low), with one signal bar.
2. Degrading and dropping: rebuild a line of 260 blocks with no Repeater; the call works but breaks up in bursts.
   At 310 blocks the far phone isn't reachable. During a call, switch the Repeater off with a soft mallet: after 3 s
   the call drops with a chat message and a busy tone.
3. The phone screen (sneak + right-click a Telephone): keypad dialling, contacts listed by owner name, recent calls
   visible only to the owner and team, blocking from contacts and recent rows, the blocked list paging and unblocking,
   do-not-disturb and hide toggles, signal bars during a call, the other player's name during a call.
4. The cord: holding the handset, step more than about a block from a phone on a desk and the call hangs up; step
   back one block from the desk and it stays.
5. Sounds: dial tone, ringback, busy, the three rising tones of "unreachable", the hang-up click, all centered and
   steady as you turn; the ring comes from the Telephone.
6. Textures, default and with the Modernity add-on pack above Modernity: Pylon, Repeater, Router and Line Terminal
   overlays on GT's hulls, the Telephone model (handset gone while off the hook), Phone Cable, wires sagging between
   heads, broken wires hanging.
7. Tooltips show the server's values: on a dedicated server with `pylonSpanFactor=5`, a client whose own config says
   4 sees 5 in the Pylon Head's tooltip.
8. Wires after respawning in the Overworld from the Nether show the Overworld's lines.
9. The quest book shows BeCom's tab with seven LV quests; a first call completes "First call" for both players.
10. A Telephone placed by a machine (a block placer or robot arm) has no owner until a player uses it, who then owns it.
11. In single-player, put `I:pylonMinHeight=20` and `I:pylonMaxHeight=10` under `wired` in `config/becom.cfg` and open
    a world: a notice names `pylonMaxHeight` and leads back to the menu. Put the values back and the world opens.

## 0.2 Radio

Branch `milestone/0.2-radio`, from the last commit of `milestone/0.1-wired`.

### Contents (DESIGN.md 16)

| Item                                                                 | Status |
|----------------------------------------------------------------------|--------|
| Height cache (6.2)                                                   | done   |
| Radio model: link budget, knife-edge terrain, cover, noise (6.1)     | done   |
| Surveys (6.3) within the survey budget (6.5)                         | done   |
| Direct links between radios (6.4)                                    | done   |
| Antennas T1, T2, T3: structure, power, recipes (7, 3.1, 3.2)         | done   |
| Antenna links on VHF and UHF, and bridges onto wired lines (7)       | done   |
| Antenna screen: links and team lock (11.4, 12.2)                     | done   |
| Transceiver: machine, screen, loudspeaker, recipe (8, 11.3, 3.1)     | done   |
| Walkie-Talkie: item, screen, sneak + scroll, battery, recipe       | done   |
| Push-to-talk key and the `radio` activation (11.1, 13.4)             | done   |
| Channels `becom:radio.1` to `16` (8.1, 13.1, 13.2)                   | done   |
| Capture effect (D24)                                                 | done   |
| Walkie speaker (D21)                                                 | done   |
| Radio audio filters per level (4.3, 13.5)                            | done   |
| Signal Analyzer: item, overlay, recipe (6.6, 11.4, 3.1)              | done   |
| `/becom signal` (6.6)                                                | done   |
| The 0.2 tunables in the config (17)                                  | done   |
| Self-test rows for 0.2 (16.3)                                        | done   |

### Untested assumptions (DESIGN.md 16.2)

| Assumption                                                         | Status  |
|--------------------------------------------------------------------|---------|
| Caps Lock works as a held push-to-talk key on every OS             | blocked |

`blocked`: DESIGN.md 16.2 has the owner try it in a game client. It's on 0.2's owner checklist, and its fallback is
built if it fails there.

### Quests (DESIGN.md 15.5)

| Item                                                               | Status |
|--------------------------------------------------------------------|--------|
| The radio quests, with the Radio contact task                      | done   |

### Assets (DESIGN.md 15)

| Item                                                               | Status |
|--------------------------------------------------------------------|--------|
| Antenna T1, T2, T3 head overlays and glows                         | done   |
| Transceiver overlay and glow                                       | done   |
| Walkie-Talkie icon, with its TX light                              | done   |
| Signal Analyzer icon                                               | done   |
| Modernity style of all of the above                                | done   |
| Sound: walkie squelch tail                                         | done   |
| English language entries                                           | done   |

### Summary

**Built.** Radio on top of 0.1's wired calls, end to end on the server:

- The radio model of DESIGN.md 6.1: link budgets, free-space loss, knife-edge diffraction over the worst terrain
  obstacle, cover below the surface on two bands, and a higher noise floor in worlds without a sky. Open ground under
  both ends isn't an obstacle (X20), and neither is an end's own height-cache cell (X23).
- The height cache: the highest point of each 4×4 column per dimension, 1 byte a cell in 32×32-chunk region files,
  updated as chunks load and save, written with the world and dropped from memory after 10 minutes unused.
- Antennas T1, T2 and T3: GT multiblocks on a frame-box mast over a casing base, powered by an energy hatch. Each
  surveys 64 bearings around it when it forms and every 5 minutes, within the shared survey budget per tick, and
  links to other antennas on VHF for radio and UHF for calls. Antennas bridge Pylon Heads and Line Terminals within
  16 blocks, so calls cross between lines over UHF (X19). The antenna screen lists links and bridges and has the
  owner's team lock.
- Every radio link, between antennas, from a device to an antenna or direct, forms at Low and keeps its level with
  4.1's hysteresis. One that falls below 4 dB cuts out at once; one between 4 and 6 dB holds at Low for 4.2's 3 s,
  then drops, and comes back only at 6 (X22).
- The Transceiver (MV, a channel and a loudspeaker), the Walkie-Talkie (IC2 battery that drains on standby,
  receive and transmit, channel by screen or sneak + scroll, a TX light) and the Signal Analyzer (an overlay with SNR,
  level, serving antenna and every loss on the link to it, once a second), with their recipes (X21).
- Sixteen channels as GTNH Voice groups `becom:radio.1` to `16`, keyed by the `radio` activation on Caps Lock. Each
  frame goes to the receiving devices the speaker reaches, directly or through antennas, at the better of the two.
  Capture (D24) holds a device and each listener to one speaker, the strongest, with the configured margin and hold.
  Frames reach walkie holders flat, players within 6 blocks of a receiving walkie positionally (D21), and players
  around a Transceiver's loudspeaker positionally. Direct hearers (D17) get nothing from BeCom. A listener's audio
  filter follows the level of the device they actually hear, and a squelch tail plays when a speaker lets go.
- `/becom signal` for operators (X23), seven radio quests with BeCom's new Radio contact task, and the 0.2 tunables
  in the config with their checks.
- Line re-checks and antenna surveys share one per-tick budget, 0.1's known issue.

**Tests.**

- 278 unit and routing tests, all passing: 156 from 0.1 and 122 new. Logic: the radio model with DESIGN.md's worked
  ranges (16), surveys (7), height regions (7) and the height cache (3), device-to-antenna and direct hops (6), the
  antenna network with surveys, links, locks and bridges (9), the antenna mesh (5), capture (10), radio link levels
  with hysteresis and the drop timer (4), tunables and their checks (6 more), the config file (1 more), link rules for
  bridges (3 more), the wired topology with antennas (2 more). Data: the `becomFormat` key on machine and item NBT
  (2), the walkie's charge and format (3). Client: levels for unknown radio and call frames (2). Server: the stop and
  start handover between worlds (4). Routing: `becom:radio.N` through GTNH Voice's `RoutingContext` (32), covering
  activation, channels, capture per device and per listener, dropouts, held speakers through dropped frames, direct
  hearers, players without voice, walkie speakers, loudspeakers, contacts and which device sets a listener's level.
- The self-test: 123 checks on a real dev server, 123 passing, 50 of them new. The sixteen radio groups, the six
  recipes (the two assembler recipes' inputs make nothing else), all three antennas forming with a 4-frame mast and
  surveying, one frame short not forming, two T1s linking on VHF and UHF at the model's SNR, the antenna screen's
  links, bridges and owner-only lock, a call between two wired lines bridged onto linked antennas, a walkie heard
  directly at 30 blocks and not on another channel, transmitting through a Transceiver and receiving on its
  loudspeaker, 100 blocks out of direct reach and reached through antennas, the Signal Analyzer's antenna and
  breakdown, a locked antenna refusing a stranger, walkie drain and a flat walkie switching off, a switched-off walkie
  hearing nothing, the edge of range (Low at 78 blocks, held at 81 inside the hysteresis, dropped at 101, kept up
  at 81 through the drop delay and dropped after it, by wall time, back at Low's threshold), an antenna saved by a
  newer BeCom staying out of the network with its keys kept, the four new machine IDs, and the radio-contact quest
  using BeCom's task type. CI runs it on every push to the branch.

**Review.** Five rounds, each a design reviewer and a code reviewer side by side (D58): the milestone
(`milestone/0.1-wired..b6d24f1`), then each batch of fixes in turn (`b6d24f1..14b2aab`, `14b2aab..9143a73`,
`9143a73..2333824`, `2333824..bf08f42`), since most fixes touched GTNH Voice's network thread, the server's
lifecycle or saved data. Each finding below was confirmed with a failing test, a self-test check or the code before it
was fixed, unless it says otherwise.

- First round, design review (2 major, 7 minor):
  - Surveys, access and direct links stopped at the 6 dB range, so a link at the edge of range flickered between Low
    and None: fixed, they reach as far as a link at Low holds (self-test, failing first; X22).
  - Capture was per device, so a player could hear two speakers, one on their own walkie and one on a neighbour's:
    fixed, a listener is held too (RadioGroupTest, failing first).
  - Antenna, Transceiver and walkie NBT had no format version: fixed with `becomFormat` (BeComNbtTest; X16).
  - The squelch tail reached players past 6 blocks and holders captured by another speaker: fixed (RadioGroupTest).
  - The antenna screen capped UHF links at Medium, though calls over them reach Max, and the analyzer showed VHF
    levels above Medium: fixed, VHF stops at Medium in both and UHF shows its own level.
  - `/becom signal` being op-only and the own-cell skip weren't logged: logged (X23). V3's stopgap wasn't marked:
    marked.
  - The analyzer explains only the hop to the serving antenna: rejected, since 6.6 and 11.4 scope it to that antenna.
    For a direct link it says no antenna is in reach, which stays a question for the owner (Known issues).
- First round, code review (1 major, 6 minor): the edge-of-range gap above, also in the config check (TunablesTest,
  failing first), and:
  - a block placed on an antenna's head threw away its survey and links: fixed, only a change of spot, ground or
    tier starts over, and a change of cover queues a fresh survey while the old links hold (AntennaNetworkTest);
  - a radio contact was counted once per pair forever, so it was used up while the quest was still locked: fixed,
    reported again after 60 s (RadioGroupTest); counting sends GTNH Voice dropped is VOICE.md V5;
  - an unknown radio level played clean on the client: fixed, Medium (ClientLevelsTest);
  - level packets looked players up one scan each: fixed with a map;
  - the height cache saved before the world's final save, missing its last chunks: fixed, it saves at server stopped
    (checked on a dev server: region files written after the last "Saving chunks").
- Second round, design review (1 major, 6 minor): radio links between 4 and 6 dB never dropped, against 4.2's 3 s
  below Low: fixed with a drop timer per pair (self-test and RadioLinkLevelTest, failing first). The squelch tail
  followed the device's capture, not the listener's: fixed. A listener's level came from their own walkie even when
  they heard a neighbour's: fixed (RadioGroupTest). An antenna saved by a newer BeCom still joined the network: fixed
  (self-test, failing first). V5's text and X22's cost were incomplete: corrected. The evidence wasn't from a clean
  head: the self-test was rerun on one.
- Second round, code review (10 minor, 4 the same as design's): players hearing through dropped frames, and direct
  hearers, lost their listener hold, so a second speaker could take them mid-sentence: fixed (RadioGroupTest, failing
  first). A late world's stopped event could forget the next world's services in single-player: fixed
  (BeComServerTest). The capture sweep could delete holds shorter than its own period, and capture state wasn't
  volatile: fixed. An unknown call level played clean: fixed, Low (ClientLevelsTest).
- Third round, design review (1 major, 5 minor): the level fix of round two read capture holds that had run out, so a
  listener's level could come from a device they no longer heard: fixed (RadioGroupTest, failing first). The quest
  text promised a grace the code doesn't give, the changelog said "every loss" and left out the drop, and X22's second
  option changed DESIGN.md 17's step: corrected. The server's create and stop could interleave: fixed under one lock.
  The squelch recipients are tested only through their inputs: rejected as a test gap, since fake players can't
  receive packets; the tail is on the owner checklist.
- Third round, code review (1 major, 3 minor): the major was the same as design's. Players without a voice session
  were held by speakers, which steered the squelch tail and blocked a second speaker for them: fixed (RadioGroupTest,
  failing first). A late stopped event reset the quest backend under a newer world: fixed (BeComServerTest). The
  edge-drop self-test counted ticks against a wall-clock timer: fixed, it holds by wall time.
- Fourth round, design review (3 minor) and code review (5 minor, 2 the same as design's), both "ready":
  - The analyzer quest's new text said a link drops "within 3 seconds" below 6 dB; one between 4 and 6 holds for
    the 3 s grace first. Fixed; RadioLinkLevelTest already pinned the code.
  - The level fix still missed two cases: a speaker taking a device by the capture margin, and one waiting out the
    last speaker's 100 ms hold. Either heard the start of an over at the client's fallback level. Fixed (two routing
    tests, failing first, and a Capture test).
  - The edge-drop rows started their clock a tick early and never checked that the link stayed up during the delay,
    so a grace wired in the wrong unit passed. Fixed with a new row, which fails with the grace's "* 1000" removed.
  - A world refused at start gets a stopped event with no stopping event, so stopped() could forget the next world's
    services, and the quest reset ran outside the lock. Fixed: each server's events act only on the services made on
    its own thread, and the reset runs under the lock (BeComServerTest, failing without the thread check; the height
    cache still saved at shutdown, checked on a dev server).
  - Also fixed from the notes: a self-test cut short by its world stopping kept running in the next one, and NaN or
    Infinity in the config passed validation (TunablesTest, failing first).
- Fifth round, design review (2 minor), "ready": the changelog still said radio links "drop after 3 s below Low":
  fixed. And this summary wasn't written yet.
- Fifth round, code review (2 minor), "ready": two test gaps, each a wrong change that every test let through.
  heardThrough's tests had one device each, so its fallback hid a broken margin or SNR; a test with two devices now
  fails if the SNR is ignored. Nothing tested that a world refused after its services were made is forgotten without
  a stopping event; a test now fails if stopped() looks only at the stopping server. No sixth round: these add tests
  and change no code.

**Deviations and GTNH Voice.** DEVIATIONS.md X19 to X23 are 0.2's: antennas take 15 bridges and route like a Router
(X19), open ground isn't an obstacle (X20), one walkie per player with open Transceivers and the walkie's drain in 0.2
(X21), surveys and direct links reaching where a link at Low still holds (X22), and `/becom signal` for operators with
an end's own cell skipped (X23). X16 now covers the machines' and the walkie's NBT. None is a 16.2 fallback: Caps Lock
is the only 0.2 assumption, and it waits on the owner. VOICE.md: V5 (whether a send went through) is new, with its
stopgap of counting a contact on every send. V3's stopgap (BeCom stops sending past a device's range, GTNH Voice
plays it with its own falloff) is in use from 0.2, and so is V1's 16 blocks for direct hearing.

**Known issues.**

- Nothing client-side has run: the push-to-talk key, the radio filters, the squelch tail, the walkie's TX light and
  screen, the Transceiver and antenna screens, the analyzer's overlay and the textures compile and were reasoned
  about, and the textures were looked at next to GT's, but no game client ran in the cloud. The owner checks below
  cover them.
- Caps Lock as a held key (16.2) is `blocked` until the owner tries it.
- The Signal Analyzer explains the link to the serving antenna only. Near another walkie and no antenna, it says no
  antenna is in reach, though the two can talk directly. Whether it should explain direct links is a question for
  the owner.
- The squelch tail's choice of players is tested through its inputs, not by receiving the packet.
- A radio contact counts on every send, including sends GTNH Voice drops (VOICE.md V5). Fixed on `voice-api`: a
  contact counts only for a listener whose voice connection is up and whom no group served that frame first, a call
  included (GTNH Voice 0.11.0's `isServed`; RadioGroupTest).
- Players between GTNH Voice's proximity range and 16 blocks of a speaker hear neither (V1), now on radio as well as
  calls. A loudspeaker or a walkie's speaker plays with GTNH Voice's own falloff and stops at the device's range, so
  it cuts off instead of fading out (V3). On `voice-api`, V1 is fixed as for calls, and the speakers fade to silence
  at their range, which needs a client to hear.
- Left for 0.3, which DESIGN.md 16 gives them to: walkies in Baubles slots (D14; for now they work from the
  inventory), the antenna screen's coverage map, and weather loss (D26). Weather touches only UHF and satellite hops,
  so radio channels don't miss it, but calls over UHF links between antennas ignore the rain until then.
- X22's reach costs more than DESIGN.md 6.3 and 6.5 count: a T3 survey is 80 steps a bearing, about 5 KB and 5,120
  height-cache reads.
- From 0.1, still open: quest text shows default numbers (X14); the IC2 rubber GT-ERR-01 line in the boot log.

**Owner checks.** Two players with voice chat, a GTNH 2.8 install with GTNH Voice 0.10.1 or newer, BeCom from this
branch, and the Modernity add-on pack for the second look at the textures:

1. Both players carry a charged walkie-talkie on channel 1, 50 blocks apart in the open. One holds Caps Lock and
   talks: the other hears them narrow and noisy (the radio filter), and the speaker's walkie shows its red TX light
   while they talk. When they let go, the listener hears a short burst of hiss (the squelch tail).
2. Walk apart: past about 40 blocks the audio starts breaking up in bursts and sounds narrower. Past about 80 it
   stops within 3 seconds, and comes back only a few blocks closer, at about 78.
3. 16.2: Caps Lock works as a held key on each OS you have, macOS especially (it may report only toggles). If it
   doesn't, rebind the key and tell the run: its fallback is another default key.
4. Sneak and scroll with a walkie in hand: the channel changes (its tooltip shows it) and the hotbar doesn't move.
   Right-click opens its screen: channel and power. A walkie on another channel hears nothing.
5. A third player stands within 6 blocks of the listener: they hear the speaker too, from the listener's position.
6. Build two Antenna T1s 60 blocks apart (hologram projector; 4 Steel Frame Boxes, 3x3 LV Machine Casings with an LV
   Energy Hatch, 32 EU/t). Each screen shows "Surveyed" and the other antenna as a link. Players 20 blocks outside
   each, 100 blocks apart, talk through them; without power to one antenna they can't.
7. Hold a Signal Analyzer: the top-left overlay shows the SNR, level, serving antenna and biggest loss, once a second.
   Stand behind a hill: terrain becomes the biggest loss. Go 10 blocks down a mine: cover does. `/becom signal`
   prints the same breakdown and every antenna in reach.
8. Place a powered Transceiver (32 EU/t) on channel 1. Stand within 2 blocks without a walkie and hold Caps Lock: the
   walkie players hear you. Their speech plays from its loudspeaker for anyone within 16 blocks; its screen turns the
   loudspeaker off and changes the channel.
9. Two players talk at once on one channel: a listener hears only the stronger one, and the other takes over once the
   first lets go.
10. The antenna's owner ticks "Serve only my team" on its screen: a player outside the team loses that antenna; the
    owner and their team keep it.
11. A Telephone on a Line Terminal wrench-linked to one antenna (within 16 blocks) calls a Telephone on a terminal
    linked to another antenna, through their UHF link.
12. Leave a walkie switched on: its charge bar goes down; at empty it switches off. A GT battery buffer charges it.
13. Look at the Antenna T1, T2 and T3 heads, the Transceiver, the walkie (TX light off and on) and the Signal
    Analyzer next to GT's machines, in GT's style and with the Modernity add-on pack.
14. The quest book's BeCom tab shows the seven radio quests; talking over the radio to another player completes "Over
    and Out" for both.
15. With a Signal Analyzer, stand 30 blocks from another player's walkie with no antenna built: the overlay says no
    antenna is in reach, though the two walkies talk. Decide whether it should explain the direct link (Known issues).
16. In single-player, open a world with a walkie, leave to the menu and open another world: the radio and the quest
    book's BeCom tab work in the second one.

## 0.3 Phones

Branch `milestone/0.3-phones`, from the last commit of `milestone/0.2-radio`.

### Contents (DESIGN.md 16)

| Item                                                                         | Status |
|------------------------------------------------------------------------------|--------|
| AE2 and Baubles Expanded as hard dependencies (16.4, D27)                    | done   |
| Mobile Phone: item, number, owner, power, recipe (8, 8.2, 3.1)               | done   |
| Mobile service through antennas, on UHF (8.2)                                | done   |
| Baubles slots for phones and walkies (D14, 8)                                | done   |
| Ringing from the player, the incoming-call notice, silent mode (11.2)        | done   |
| The Phone key, and sneak + Phone to reject (11.1, D34)                       | done   |
| Phone screen for carried phones: power, silent mode, everyone in a call (11.2) | done |
| Group calls of up to 8 (8.2, 10.1)                                           | done   |
| Weather loss on UHF links (6.1, D26)                                         | done   |
| Coverage map in the antenna screen (6.6, 11.4)                               | done   |
| Battery drain for phones (D36, 11.3)                                         | done   |
| Signal bars while holding a phone or in a call (11.2)                        | done   |
| ME Phone (8.3, D13)                                                          | done   |
| ME Bridge (8.3, D45)                                                         | done   |
| Video: front camera (10.1, D41, D43)                                         | done   |
| Video: rear camera (10.1)                                                    | done   |
| Video: the corner window and the full-size view, camera switches (11.2, D44) | done   |
| Videophone (10.2, D42)                                                       | done   |
| The 0.3 tunables in the config (17)                                          | done   |
| Self-test rows for 0.3 (16.3)                                                | done   |

### Untested assumptions (DESIGN.md 16.2)

| Assumption                                                                     | Status |
|--------------------------------------------------------------------------------|--------|
| The ME Bridge joins an ME network on ME cable, the way GT's ME hatches do       | done   |
| Video renders under Angelica and shader packs                                  | blocked |
| A far-away player's skin can be drawn from the profile the server sends        | blocked |
| Video on BeCom's own network channel doesn't lag the game connection           | blocked |

### Quests (DESIGN.md 15.5)

| Item                                                                         | Status |
|------------------------------------------------------------------------------|--------|
| The phone quests, with the Mobile call task                                  | done   |
| The video quests, with the Video call task                                   | done   |
| The ME quests, with the ME Bridge call task                                  | done   |

### Assets (DESIGN.md 15)

| Item                                                                         | Status |
|------------------------------------------------------------------------------|--------|
| Mobile Phone and ME Phone icons                                              | done   |
| Videophone overlay and glow                                                  | done   |
| ME Bridge overlay and glow                                                   | done   |
| Modernity style of all of the above                                          | done   |
| Sounds: the ringtone from a carried phone, the silent-mode buzz             | done   |
| English language entries                                                     | done   |

### Summary

**Built.** Phones, ME and video on top of 0.2's radio, end to end on the server, with the client drawing video:

- AE2 and Baubles Expanded are hard dependencies now (D27), at DESIGN.md 16.4's versions.
- The Mobile Phone (HV, assembler): its number and owner on first use (X24), a 1,200,000 EU battery drained at 4 EU/t
  on standby and 16 in a call, a power switch, silent mode, and service through every antenna that reaches its holder
  on UHF, capped at High. It works from the inventory, a bauble slot or the mouse cursor (D14), and walkies work from
  a bauble slot too.
- Ringing from the player: everyone near hears a carried phone ring, or only its holder feels a buzz in silent mode,
  with a notice in the corner naming the caller, their number and the keys, shown while the player is free to answer
  (X26). The Phone key (N) hangs up during a call, answers a ringing phone, the player's own or a Telephone or
  Videophone close enough to answer, and opens the phone screen otherwise; sneak + N turns a call down (D34). Tones on
  phones with no handset stop when the call does (X30). A call shows on a phone's screen only to the people in it.
- Group calls of up to 8 people: anyone in a call adds a phone from the phone screen, a Telephone's handset included,
  and whoever added it is told when it doesn't answer. The phone screen lists everyone in the call with their signal
  bars, everyone in a Videophone's room by name, and has power and silent mode for carried phones (X25, X26). Signal
  bars by the hotbar show the call's weakest level, or the held phone's service, unlit with none.
- Phones join the call graph at the antennas they reach. Rain costs UHF links 3 dB and storms 6, where one end stands
  in the rain (D26). The antenna screen draws a coverage map on each band, and the Signal Analyzer and
  `/becom signal` read both bands (X31).
- The ME Phone (EV): linked at a Security Terminal, it has service at Max inside its ME network's wireless range by
  AE2's own rule, calls other ME Phones on it directly, and any other phone through an ME Bridge (X27). The ME Bridge
  (EV, 512 EU/t, one ME channel) joins an ME network on ME cable like GT's ME hatches and is wrench-linked to a BeCom
  node within 16 blocks, as a Max hop.
- The Videophone (MV, 32 EU/t, cap High): a speakerphone on a Line Terminal's Phone Cable. Everyone within 4 blocks
  with a voice session is in its call and works its screen, the call plays from its loudspeaker out to 16 blocks, it
  hangs up 30 s after its room empties, and it works only while loaded and powered (X28).
- Video calls (D41-D44): the server knows who sees whom at which level, sends front-camera poses about ten times a
  second through each viewer's dropout chain, each person's held item, armor and profile, and relays rear pictures
  within the client caps at each viewer's level (X29). The client draws each person the way the inventory draws the
  player into a picture of the level's height and rate, noisy at Medium, light noise at High, frozen at Low with
  "Weak signal", glitching after lost frames, nodding while they talk; shows the call in a corner window, full size
  on the phone screen's Video tab with each camera's switch, and on the face of the Videophone the player stands at;
  and copies its own view for the rear camera, shrinks it on the GPU and compresses it on a worker.
- Seven quests for phones, video and ME, with three new task types (Mobile call, Video call, ME Bridge call), whose
  triggers are decided in pure classes.
- Textures for the Mobile Phone, ME Phone, Videophone and ME Bridge in GT's style and the Modernity pack, and the
  ringtone and silent-mode buzz.

**Tests.**

- 350 unit and routing tests, all passing: 278 from 0.1 and 0.2, 72 new. Logic: the call book's group calls,
  speakerphone rooms, empty-room hang-up and the world check after the rate limit (15), the video plan (10), the rear
  picture relay (8), the call quest triggers (5), video quality per level (2), repeat reports for quest tasks (2),
  the call graph with carried phones (2), call history in groups (2), weather on antenna links (3), the coverage map
  (2), video frames sampling the dropout chain (1), link rules for the ME Bridge (1), tunables (1). Client: rear
  pictures under the cap, the right way up, and a header claiming a huge picture refused before decoding (4).
  Packets: round trips for 0.3's packets and the antenna view's maps (9). Language: every key built from an enum's
  names (1). Routing: `becom:call` with loudspeakers and rooms through GTNH Voice's `RoutingContext` (4).
- The self-test: 219 checks on a real dev server, all passing, 96 of them new. The Mobile Phone's recipe, number,
  owner and service at the model's SNR; a Mobile Phone calling a Telephone through an antenna bridged onto a pylon
  line, at the path's weakest hop (0.3's first "Done when"); a Telephone's call shown only to the people in it; group
  calls, adding from a carried phone and a Telephone, the Phone key answering, rejecting and hanging up, and a
  Telephone answered with the key; tones stopping; a phone switched off leaving its call; drain; a phone moved
  through the cursor staying in its call; a ringing phone dropped taking its notice with it; two phones ringing for
  one player, the key answering the latest, hanging up before it answers and, with sneak, turning the other down in a
  call; notices hidden while dialling out or holding a handset and shown again after, and following a phone handed
  to someone else, who alone can answer it; a handset holder unable to dial or answer on a carried phone; a copy of a
  phone opening on its own number; a phone screen finding its contacts once over a second of asking every tick, and
  again a second later; a phone in a bauble slot; rain on service and on the analyzer's phone band; the coverage
  map; the ME Bridge joining a secured ME network for its owner and not for a stranger, both placed from items that
  kept some data, each with its AE2 owner checked; an ME Bridge and an ME Phone saved by a newer BeCom left alone;
  ME Phones at Max, through a bridge, and out of range; rear pictures through the relay; a Videophone added to a call
  showing only its caller while it rings, worked only from its room, its call shown to the people in it and to nobody
  else, and hung up for its room by the Phone key; the Videophone's number, ringing, room, loudspeaker, its room's
  signal bars, empty-room hang-up, offline when unpowered; the video call between a Mobile Phone and a Videophone
  (0.3's second "Done when": the server sends each side the other's poses), the Call tab, a voiceless player kept out
  of the room and unable to hang it up, profiles sent again, camera switches; the quest reports. CI runs it on every
  push.
- The client smoke test: started with `-Dbecom.clienttest=<dir>`, the dev client opens a flat world under Xvfb with
  Mesa's software OpenGL, plays a made-up video call at every level through the real drawing code, and checks each
  front picture's height and that it shows a person, that Low and a switched-off camera draw nothing, that the rear
  camera captures a JPEG under the cap, and that no frame raised an OpenGL error: 8 checks, all passing, with
  screenshots of the corner window, the phone screen's Video tab and the Videophone's face. It is run by hand; CI
  doesn't run it.

**Review.** Five rounds, a design reviewer and a code reviewer side by side in each (D58): the milestone
(`milestone/0.2-radio..280ee68`), then each round's fixes in turn (`280ee68..029f3d0`, `029f3d0..542a4d2`,
`542a4d2..4a9a9be`, `4a9a9be..cf47443`). The fifth round's fixes are `cf47443..2840cf4`. Each finding was confirmed
by a failing test, a self-test check or the code before it was fixed, as each item says.

- First round, design review (2 major, 14 minor), "fix first":
  - Ringback and busy tones never stopped on carried phones and Videophones: fixed, a call ending clicks every phone
    with no handset and a busy tone stops after 3 s (four self-test rows; X30). The same as the code review's M2.
  - A Telephone's Add failed with "You can't make that call now": fixed, adding goes the same way from every phone,
    and whoever added a phone that doesn't answer is told (self-test rows). The same as code m1.
  - The three new quest tasks had no text: fixed (LangTest, which checks every key built from an enum, failed on
    exactly those three). The same as code m4.
  - Video call was reported once per player per run, so it was used up while its quest was locked: fixed, reported
    again after a minute, and decided by the video plan (RepeatReportsTest, VideoPlanTest).
  - The Signal Analyzer read VHF only, so it could never show weather: fixed, it reads both bands (self-test row in
    the rain; X31).
  - The rear camera's server side was untested: fixed with code M1 below.
  - Baubles were untested: a self-test row now carries a phone in a bauble slot through service, ringing, the Phone
    key and its power switch. The suggestion to mark the bauble inventory changed was rejected: on this Baubles that
    only marks the main inventory again, and the client reads a bauble phone's state from the server's phone screen,
    or the Baubles screen, which sends its slots when it opens.
  - 0.3's quest triggers weren't in classes without Minecraft: fixed (CallQuestsTest, VideoPlanTest).
  - No round-trip tests for the new packets: added (PacketRoundTripTest); the reviewer had found them symmetric.
  - A person seen again in a group call came back with the default skin: fixed (self-test rows walking someone into
    the Videophone's room, out and back; the server sent their profile twice). The same as code m3.
  - The Videophone's face had no labels: fixed (client smoke test screenshot).
  - Signal bars vanished with no service: fixed, unlit bars. Bars near a Videophone are code m2.
  - The Call tab named one person per Videophone, and an unanswered add told nobody: fixed (self-test rows).
  - The corner window was large and drawn under open screens: smaller, and hidden under them (client smoke test
    screenshots; its size stays the owner's call, X29).
  - DEVIATIONS gaps: X1, X18, X21, X26, X27 and X28 corrected, X30 and X31 added; the Phone key now answers a nearby
    ringing Telephone or Videophone, as DESIGN.md 11.1 says (self-test rows).
  - The video "Done when" row overstated what it proves: reworded, and the rest is owner check 2.
- First round, code review (1 critical, 5 major, 8 minor), "fix first":
  - C1: a rear picture of a few hundred bytes claiming 4000x4000 pixels made a 48 MB picture on the receiving client,
    every frame: fixed, the size is read before decoding and anything over the ceilings dropped (RearJpegTest, which
    decoded the 4000x4000 picture before the fix).
  - M1: the rear camera never delivered a frame, from a Long.MIN_VALUE overflow (confirmed from the code): fixed in a
    new pure relay, which also allows two pictures so tick-rounding doesn't drop on-time ones (RearRelayTest, 8 tests;
    two self-test rows through the real relay).
  - M2: tones, as design 1.
  - M3: rejected. The bridge has no AE owner of its own, but GT's machine item hands the placing player to the
    machine's AE proxy (`ItemMachines.placeBlockAt` calls `getProxy().setOwner`, read in GT 5.09.54.205's
    bytecode), and AE2 gives the node that player as it makes it. The self-test missed security altogether: it now
    secures the ME network and places the owner's bridge, which joins, and a stranger's, which AE2 keeps out. The
    second round found the case this misses (below).
  - M4: a phone on the mouse cursor wasn't carried, so its call ended at the next look (confirmed from the code): fixed,
    the cursor counts (self-test row).
  - M5: a dropped ringing phone left its notice on screen (confirmed from the code): fixed, the call service remembers
    whose screen shows it (self-test row).
  - m1, m3 and m4: as design 2, 10 and 3.
  - m2: anyone near a Videophone in a call got bars, and someone in another call had theirs pulled down: fixed, the
    server tells each player their own call's bars (self-test row).
  - m5: power switching and screen opening ran a full look at the player's phones, unlimited: fixed, rate-limited, and
    opening looks again only when it must (confirmed from the code; the limiter is RateLimiterTest's).
  - m5b: the world check ran before the rate limit: fixed (CallBookTest, which didn't compile against the old API).
  - m6: two ME Bridges on a quantum-linked ME network join BeCom networks in any two dimensions: left as a known
    issue for 0.4, whose DESIGN.md 9.5 rules decide it.
  - m7: a voiceless player, or one whose phone was ringing, joined a Videophone's room: fixed (self-test row for the
    voiceless one; the ringing one confirmed from the code).
- Second round, design review (4 minor), "ready", and code review (4 minor), "ready":
  - Both: an ME Bridge placed from an item that kept some data (paint, a cover, an upgrade) read its missing AE node
    as AE2 player 0's, dropping the placer GT had just given it, where GT's own ME hatches check first: fixed the same
    way. A self-test row places both bridges from such items: without the fix, the stranger's bridge joined the
    secured network as player 0, the owner in that world, and the run read 191 passed, 2 failed.
  - Code m-A: a copy of someone's phone, opened before the server's next look, showed the original's call, a
    regression from m5's fix: fixed, the screen looks again unless it knows the phone in those hands, and shows
    nothing of a phone the player can't use (self-test row).
  - Code m-B: the profile counter for the self-test grew for as long as the server ran: it counts only while the
    self-test asks.
  - Code m-C: two phones ringing for one player shared one notice, and the first to stop took it away: fixed, the
    other's notice shows again, and the Phone key answers the phone the notice names (self-test rows).
  - Design 2: every open phone screen searched paths for its contacts every tick: fixed, a phone's reachable phones
    are kept a second (self-test row counting the searches).
  - Design 3 and the code review's note: X26 and X30 said more than DESIGN.md or the code: corrected.
  - Design 4: a self-test row's name said more than it proved: renamed.
  - The design review's note on m6: the known issue now says how far it reaches.
- Third round, design review (4 minor), "ready", and code review (4 minor in the range, 3 from before it), "ready".
  Every new self-test row below failed on the old code: the run read 192 passed, 11 failed, with 0.3's second-round
  code and the two changes the second round made for two ringing phones undone, and 203 passed, 0 failed with the
  fixes.
  - Design 1, code m2 and m3: in a call while another of the player's carried phones rang, the Phone key tried to
    answer that one, which the call book refuses, so it did nothing; and that phone's notice, hidden during the call,
    never came back. Fixed: the key hangs up before it answers, and a player leaving a call sees the notice again
    (self-test rows).
  - Design 2 and code m1: the row for the key answering the phone the notice names passed without that fix, since the
    phone it answered was also the first carried. Reordered: the latest to ring is now the second phone, and the row
    failed on the old code.
  - Code m4: the contacts row passed with no search at all, and with a list kept forever: it now needs exactly one
    search in a second of views, and exactly one more a second later.
  - Design 3: X26 left out putting a handset down and turning nearby phones down: rewritten with the key's new order.
  - Design 4: keeping a phone's contacts a second wasn't logged against DESIGN.md 14: X32, which also says what the
    call graph keeps by itself.
  - Code O1: an ME Bridge saved by a newer BeCom still joined ME, and saving it lost AE2's node data, its owner and
    its network: fixed, it makes no node and writes that data back with its keys (self-test row, which loads such a
    bridge on the cable next to the owner's).
  - Code O2: a player holding a Telephone's handset could answer or dial on a phone they carry, and the call then hung
    up as they walked off: fixed, carried phones refuse while a handset is held, and the key puts the handset down
    first (self-test rows).
  - Code O3: anyone at a Telephone or Videophone in a call saw who was in it: fixed, anyone else sees it in use
    (self-test rows at a Telephone and in a Videophone's room).
- Fourth round, design review (5 minor), "ready", and code review (3 minor, 2 test gaps), "ready". The new rows that
  check a bug failed on the old code: 206 passed, 8 failed with the third round's code, and 214 passed, 0 failed with
  the fixes. The rows that fill a test gap pass on both.
  - Design m1 and code m1: a ringing phone's notice stayed up while its player was in another call they'd made or
    picked up some other way, saying the Phone key answers, where the key now hangs up that call. Fixed: a notice
    shows only while its player is in no call and holds no handset, and comes back when they're free (self-test rows:
    dialling out, holding a handset; X26 logs that this departs from D34 during another call).
  - Code m2: a ringing phone that changed hands left its notice with the player who had it. Fixed: the notice follows
    the phone (self-test row).
  - Design m2: a ringing Telephone or Videophone added to a group call showed the whole call to anyone at its screen.
    Fixed: a ringing phone's screen shows only who calls, as its notice does (self-test row).
  - Code m3: someone in a call on their own phone saw a Telephone or Videophone of the same call as "in use by
    someone else", and someone who dialled or answered a Videophone from 5 to 8 blocks off was dropped from its room
    at once. Fixed: the call shows to everyone in it, and only someone in a Videophone's room works its screen; anyone
    else is told to step closer (self-test rows; X28).
  - Design m3 and the code review's gaps: the contacts rows asked twenty times within one tick, and no row pressed
    sneak + the Phone key in a call, used the key in a Videophone's room, or looked at a Videophone's screen from its
    room. Added: the screen asks once a tick for twenty ticks, and rows for the other three.
  - Design m4: an ME Phone saved by a newer BeCom swallowed an ME charger's power. Fixed: it hands the power back
    (self-test row).
  - Design m5: X32 didn't say what the kept contacts cost a Telephone's pick-up: it does now.
- Fifth round, design review (1 major, 3 minor), "fix first", and code review (4 minor, 1 trivial), "fix first". The
  five rows that check a bug failed on the old code: 208 passed, 10 failed, the five and five more that follow from
  them in the same steps. With the fixes, 219 passed, 0 failed.
  - Design M1 and code m1: someone within 4 blocks of a Videophone but kept out of its call, in another call, holding
    a handset or without voice, could still hang its call up for everyone, or turn its ring down, from its screen.
    Fixed: hanging up is for the people in its call, and turning a ring down for someone near it in no call and
    holding no handset (self-test rows; X28).
  - Code m2: for up to a second after a ringing phone changed hands, its old holder's Phone key could still answer
    it, from a list the server hadn't looked at again yet. Fixed: only its holder as the server last saw it can
    (self-test row).
  - Design m4 and code m3: Hang up on an offline Videophone told whoever stood next to it to step closer. Fixed
    (self-test row). The code review's note that the message shows the room in whole blocks was rejected: every
    distance BeCom shows is in whole blocks, as DESIGN.md 17's are.
  - Code m4: an ME Phone saved by a newer BeCom gave AE power away without losing any. Nothing takes power out of it
    today, but it now gives none (self-test row).
  - Design m2 and m3: X26 now says that during another call a silent phone's hidden notice leaves only a buzz, and
    that a ringing phone handed over rings on only when the server looks at its new holder first; X32 that a pick-up
    can also open the dialer where it should ring.
  - Code, trivial: when the phone that added a ringing one leaves the call, the ringing phone's screen names someone
    other than its notice does. Rejected: that ring drops once the 3 s drop grace runs out, and a notice can't change
    without being sent again.
  - These fixes got no sixth review: none is big or touches threads or saved data, and each has a self-test row that
    failed on the old code.

**Deviations and GTNH Voice.** DEVIATIONS.md X24 to X32 are 0.3's: carried phones' numbers, owners and copies (X24),
group calls (X25), carried phones' power, silent mode, drain, notices and the Phone key (X26), the ME Phone's battery,
linking and range check and what they cost (X27), the Videophone's room, its screen and its number (X28), video's
choices (X29), tones on phones with no handset (X30), the analyzer's two bands (X31), and a phone's contacts kept a
second (X32). X1, X16, X18 and X21 were brought up to date. None is a 16.2 fallback: the ME Bridge assumption held,
and the other three wait on the owner. VOICE.md: nothing new. Video goes on BeCom's own network channel, V2's
stopgap, and the Videophone's loudspeaker plays with GTNH Voice's falloff and stops at 16 blocks, V3's.

**Known issues.**

- Three of 0.3's 16.2 assumptions wait on the owner and are `blocked` until then: video under Angelica and shader
  packs, a far-away player's skin from the profile the server sends, and video on BeCom's own channel not lagging the
  game connection. Their fallbacks are known and not built.
- No game client has played 0.3 with people. The client smoke test runs the real drawing code on made-up calls with
  software OpenGL; the server's side runs with fake players, who get no packets. 0.3's second "Done when" is proven
  up to the server sending each side the other's poses; that they show is owner check 2.
- The ME Phone can't take an Infinity Booster Card, and doesn't check AE2's security permissions of whoever carries
  it, as a wireless terminal does (X27).
- Calls cross dimensions through ME: an ME network spanning two dimensions through a Quantum Network Bridge, with an
  ME Bridge in each, joins the BeCom networks there at Max, since every bridge of one ME network is one vertex
  whatever its dimension (code review of 0.3, m6). That reaches any two dimensions the ME network spans, the ones
  DESIGN.md 9.5 keeps unreachable in v1 included. 0.4 makes calls between dimensions follow 9.5, and decides this
  with them.
- A phone in a bauble slot: the client's copy of its stack catches up when the Baubles screen opens. Nothing on the
  client reads it otherwise; the phone screen's state comes from the server.
- The Phone key opens a carried phone's screen, never a Telephone's, so a Telephone's settings stay behind sneak +
  right-click (X1).
- The corner window's size and the rear picture's inset are the run's choice (X29); D44 asks for a small window.
- From 0.1 and 0.2, still open: quest text shows default numbers (X14), the IC2 rubber GT-ERR-01 line in the boot
  log, X22's survey cost, and V1, V3 and V5.

**Owner checks.** Two players with voice chat (three for 5 and 9), a GTNH 2.8 install with GTNH Voice 0.10.1 or newer,
BeCom from this branch, and the Modernity add-on pack for the second look at the textures:

1. 0.3's first "Done when": build a pylon line from a Telephone's Line Terminal, an Antenna T1 wrench-linked to one of
   its Pylon Heads, and give the other player a charged Mobile Phone within 60 blocks of the antenna. They dial the
   Telephone's number: it rings; picking up its handset connects the call, and both hear each other. The phone's
   signal bars by the hotbar light up to the call's level.
2. 0.3's second "Done when": a Videophone on its own Line Terminal (Phone Cable, MV power), bridged onto the antenna.
   The Mobile Phone calls it; standing within 4 blocks, press N or answer on its screen. Both sides see the other:
   the phone in the corner window and on the phone screen's Video tab, the Videophone on its face. Each person's
   skin, armor and held item show, the head turns as they look around, an arm swings as they punch, and the head nods
   while they talk. (16.2: the skin is the player's own, not the default one.)
3. Walk the phone away from the antenna: Medium is noisy and choppier (48 px, 5 frames a second), Low freezes the
   picture with "Weak signal" on it while the voice goes on, and a lost frame shows a few frames of blocky glitches.
4. On the Video tab, switch the front camera off: the other side's tile says "Camera off". Hide the window: it goes,
   and comes back on the tab's Window button. Change `videoCorner` in the client config: the window moves. With any
   screen but chat open, the window doesn't show.
5. Rear camera: two Mobile Phones in a call of two, both within 30 blocks of an antenna, at High or better. Switch the
   rear camera on: the other side sees your view, without your HUD, in the corner window with your face in an inset,
   about 2 pictures a second at High. A third person added to the call stops it.
6. 16.2: with Angelica and a shader pack, the corner window, the Video tab and the Videophone's face still show the
   people, and the rear camera still sends a view. Watch the game's ping while the rear camera streams (16.2: video
   doesn't lag the connection).
7. Ringing: call a Mobile Phone in someone's inventory: it rings from them, players near hear it, and the corner notice
   shows the caller's name and number with the keys. N answers; sneak + N turns it down, and the caller hears the
   hang-up click. Silent mode (phone screen, Settings) makes it buzz for its holder only. Call a phone whose holder is
   in a call: a busy tone for 3 s. With it ringing, pick up a Telephone's handset: the notice goes while the phone
   rings on, N puts the handset down and the notice comes back, and N again answers.
8. Group call: in a call, add a third phone from the keypad or contacts, from a Mobile Phone and from a Telephone's
   handset. The Call tab lists everyone with their bars. Add a phone nobody answers: after 30 s whoever added it reads
   that it didn't answer.
9. The Videophone: everyone within 4 blocks is in its call, listed by name on the other side's Call tab; players up
   to 16 blocks away hear the call from it, and a player there who isn't in the call has no signal bars. Its screen
   opens from 8 blocks, but dials, answers and hangs up only from within 4; someone near it in another call sees "In
   use by someone else" and can't hang it up. Everyone walks off: 30 s later it hangs up. Cut its power: it can't be
   called.
10. Put a Mobile Phone in a bauble slot: it rings, N answers it and, with no call, opens its screen; its power switch
    works. Move a phone to another slot with the mouse during a call: the call goes on.
11. Hold a phone where no antenna reaches: four unlit bars. Stand in the rain: the Signal Analyzer's phone band shows a
    weather loss, and the radio band doesn't.
12. The antenna screen's coverage map, on each band, is brightest at the antenna and fades with distance and behind
    hills.
13. ME: link an ME Phone at a Security Terminal of a powered ME network with a Wireless Access Point. Within the access
    point's range it has service at Max; outside, none. Two such phones call each other at Max. Place an ME Bridge
    on the network's cable (EV, 512 EU/t), wrench-link it to an antenna: the ME Phone calls the Telephone. A bridge
    placed by a player the network's security doesn't let build stays offline.
14. Look at the Mobile Phone, ME Phone, Videophone and ME Bridge next to GT's machines, in GT's style and with the
    Modernity add-on pack; and listen to the ringtone from a few blocks away, and the buzz.
15. The quest book's BeCom tab shows the seven new quests with their task texts. A video call completes "Seeing Is
    Believing" for both sides once "Face to Face" is done, even if the first video call came before it.

## 0.4 Space

Branch `milestone/0.4-space`, from the last commit of `milestone/0.3-phones`.

### Contents (DESIGN.md 16)

| Item                                                                                            | Status |
|-------------------------------------------------------------------------------------------------|--------|
| Galacticraft as a hard dependency, Twilight Forest and Personal Space in the dev server (16.4)  | done   |
| Satellite and Satellite Rocket, with their recipes (3.1, D30)                                   | done   |
| Satellite Launch Pad: structure, launches, fuel, open sky, maintenance (9.1, 3.2, D40)          | done   |
| Satellites per dimension and their decay (9.1, D23, 14)                                         | done   |
| The Launch Pad screen: rockets waiting, each dimension's satellites, the next down (9.1, 11.4)   | done   |
| Sky hops for devices under open sky, on calls and radio (9.1, D5, 6.1)                          | done   |
| Satellite Dish: structure, links to other dishes and planets, maintenance (9.2, 3.2, D40)       | done   |
| Rocket Radio Receiver: a T1 antenna and an uplink in a landed rocket (9.3, D28, 14)             | done   |
| Portal Relay: portals, pairing through a portal, a High hop (9.4, D22, D46)                     | done   |
| Calls between dimensions, and ME Bridges across dimensions (9.5, 0.3's known issue)             | done   |
| The 0.4 tunables in the config (17)                                                             | done   |
| `/becom satellites`, the admin command that sets a dimension's satellites (12.3)                | done   |
| Self-test rows for 0.4 (16.3)                                                                   | done   |

### Untested assumptions (DESIGN.md 16.2)

| Assumption                                                                                      | Status |
|-------------------------------------------------------------------------------------------------|--------|
| BeCom finds rockets as they join their world, and counts one unignited on a pad with a receiver | done   |

### Quests (DESIGN.md 15.5)

| Item                                                                                            | Status |
|-------------------------------------------------------------------------------------------------|--------|
| The Portal Relay quest, with the Portal Relay pair task                                         | done   |
| The satellite quests, with the First satellite and Five satellites tasks                        | done   |
| The space call quests, with the Call between dimensions task                                    | done   |

### Assets (DESIGN.md 15)

| Item                                                                                            | Status |
|-------------------------------------------------------------------------------------------------|--------|
| Satellite, Satellite Rocket and Rocket Radio Receiver icons                                     | done   |
| Satellite Launch Pad, Satellite Dish and Portal Relay overlays and glow                         | done   |
| Modernity style of all of the above                                                             | done   |
| Sound: the rocket launch                                                                        | done   |
| English language entries                                                                        | done   |

### Summary

**Built.** Space on top of 0.3, end to end on the server, with Galacticraft as a hard dependency (D27) and Twilight
Forest and Personal Space in the dev server only:

- The Satellite, the Satellite Rocket and the Rocket Radio Receiver, from the assembler (3.1, D30).
- The Satellite Launch Pad (ID 25010): a 5 by 5 floor of Clean Stainless Steel Machine Casings under open sky, with
  an input bus, an input hatch, an energy hatch and a maintenance hatch. A launch takes a Satellite Rocket, renamed
  or not, and 1,000 L of any fluid Galacticraft burns as rocket fuel, runs 30 s at 480 EU/t, and puts one satellite
  over the pad's dimension (9.1). Each maintenance problem makes a launch take longer (D40). While anything covers
  the pad, a launch holds, drawing nothing, and the pad goes dark. Its screen shows the rockets waiting, a launch on
  hold, each dimension's satellites and when the next comes down (11.4).
- Satellites stay up 100 days of the Overworld's world time, give or take a fifth (D23), saved with the Overworld in a
  file of their own (14).
- Sky hops (9.1, D5): with at least 5 satellites over a dimension with a sky, a Mobile Phone, a walkie or a
  Transceiver under open sky reaches them at 6 + 18 log10(n / 5) / log10(20) dB, Max's 24 at 100 and no more past it
  (X38), less a roof's cover and the rain where the device stands. Radios hear each other through them. The Signal
  Analyzer and `/becom signal` show the satellites when they serve best.
- The Satellite Dish (ID 25011): a 5 by 5 dish of the same casings on three Stainless Steel Frame Boxes (X37), 512
  EU/t, wrench-linked to an antenna, a Pylon Head or a Line Terminal. With 5 satellites over its dimension it joins
  its network to every other dish there and, in the Overworld, to every planet's uplink, at its sky hop: rain at the
  dish and 2 dB per maintenance problem come off it (9.2, D40). Without open sky over the whole dish it stops.
- Rocket Radio Receivers (9.3, D28): a rocket standing unignited on a launch pad on one of Galacticraft's worlds, with
  a receiver in its cargo, is a T1 antenna there and the planet's High uplink to the Overworld's satellites (X35,
  X39). BeCom finds rockets as they join their world and looks at them once a second.
- The Portal Relay (ID 25012, MV, 32 EU/t): works within 4 blocks of a Nether, End, Twilight Forest or Personal
  Space portal, telling the last two by their block class. Wrench one, go through, wrench the other within 5 minutes:
  the pair is a High hop between the Overworld's network and the one beyond, as long as the two stand on the two
  sides of one portal: where Minecraft's or Twilight Forest's teleporter puts the far one, or, for Personal Space,
  by a portal into the far relay's own dimension, which the relay reads from the portal and shows on its info screen
  (9.4, D22, D46, X36). A relay joins its own network by a wrench link, like a dish, and an ME Bridge links to
  neither (X40).
- Calls between dimensions follow 9.5: the call graph has a vertex for each dimension's satellites, and the rules for
  dishes, uplinks and relay pairs live in a pure class, Gateways. Planet to planet goes through the Overworld's
  satellites, and longer chains combine. An ME network joins calls only within each dimension (X33), which settles
  0.3's known issue. Radio channels stay in their dimension (X34).
- `/becom satellites` lists each dimension's satellites, or sets a dimension's count for testing (12.3).
- Seven space quests with four new task types: Portal Relay pair, First satellite, Five satellites and Call between
  dimensions (15.5). Textures for the three items and the three machines' faces in GT's style and the Modernity
  pack, and the rocket launch sound.

**Tests.**

- 398 unit and routing tests, all passing: 350 from 0.1 to 0.3, 48 new. Gateways: dishes, uplinks, relay pairs, planet
  to planet and longer chains through the call graph (11). Portals: the block classes, which dimensions a pair joins,
  the two sides of a Nether or Twilight Forest portal, wide portals, Personal Space portals leading into the far relay's
  dimension, and reading Personal Space's own portal tile (11). Satellites: constellations per dimension and their decay
  (5). Sky strength: DESIGN.md 9.1's formula and counts, the cap at 100, cover, weather and the analyzer's terms (7).
  Link rules for dishes, relays and the ME Bridge (4). Call quests between dimensions (3). Saved data: satellites, newer
  satellite files, and a relay's partner (3). Tunables (1). Packets: the pad's and dish's screen views, and a dimension
  with no name (3).
- The self-test: 285 checks on a real dev server, all passing, 66 of them new. GregTech's weather explosions off while
  it runs; the space machines' IDs, recipes and quest task types; Galacticraft's landing pad from nine pads on the Moon;
  the Launch Pad forming, taking rocket fuel, counting the rockets waiting, holding and going dark while covered,
  launching a renamed rocket and putting a satellite up, First satellite and Five satellites, not launching without open
  sky, and launching from an empty hatch when set to need no fuel; a satellite coming down by world time; a Mobile
  Phone's sky hop at Max with 100 satellites, under a roof, and in the rain; walkies 1,000 blocks apart talking through
  the satellites; two dishes forming and joining Telephones 300 blocks apart, a maintenance problem's 2 dB, a covered
  dish stopping, and too few satellites; 16.2's rocket assumption on the Moon (a Tier 1 rocket found as it joins,
  counted on a pad with a receiver, not without one) and a rocket that throws being dropped; a Moon phone's service from
  the rocket; 0.4's first "Done when", a Mobile Phone in the Overworld calling the Moon phone at the uplink's High, and
  a Telephone with a dish doing the same; relays by each kind of portal; two personal dimensions made through Personal
  Space's own portal action, a relay pairing into the one its portal leads to and refused by the other, and the pair
  dropped once the portal is relinked; Twilight Forest relays 300 blocks apart and End relays pairing; 0.4's second
  "Done when", Telephones in the Overworld and the Nether through a relay pair; the pair's halves saved together, and
  neither written while saving is off; one partner each, unpairing, and a relay 1,000 blocks off in the Nether refused;
  `/becom satellites`. CI runs it on every push.

**Review.** Three rounds, a design reviewer and a code reviewer side by side in each (D58): the milestone
(`milestone/0.3-phones..9b9edcf`), then each round's fixes in turn (`9b9edcf..f255fbc`, `f255fbc..369671d`). The third
round's fixes are `369671d..a88352a`. Each finding was confirmed by a failing test, a self-test check or the code
before it was fixed, as each item says.

- First round, design review (1 medium, 9 low, 4 questions), and code review (5 low):
  - Design 1: relays paired whatever portal they stood by, so two Overworld portals far apart could join through the
    Nether. Fixed: they pair only when one stands where the other's portal leads, as Minecraft scales the trip, and a
    relay 1,000 blocks off in the Nether is refused (PortalsTest, which didn't compile before the rule; a self-test
    row; X36).
  - Design 2: an ME Bridge could be wrench-linked onto a dish or a relay, which link only to antennas, Pylon Heads and
    Line Terminals. Fixed (LinkRulesTest, which failed before the fix; logged in the second round as X40).
  - Design 3 and 4: X34 said only dishes carry no radio, and X18 left out 0.4's ceilings. Both now say what the code
    does: radio stays in its dimension, through dishes, relays and uplinks alike.
  - Design 5: a launch set to need no fuel still needed a fuel stack. Fixed; its test came in the second round.
  - Design 6 and code 1: the pad's and dish's screens read server state on the client thread in single player, and
    the dish's was translated on the server. Fixed: both are built on the server and read on the client, as the
    antenna's screen is (round-trip tests for both views).
  - Design 7: the pad checked the sky only when a launch started. Fixed: a launch holds while anything covers the
    pad, drawing no power (a self-test row covers the pad mid-launch).
  - Design 8: the self-test's last log predated the fixes. It runs on every round's head now; run 91 is the last.
  - Design 9: no self-test for Twilight Forest or End pairs, or rain on a sky hop. Added; a Personal Space pair came
    in the third round.
  - Design 10 and code 4: a rocket from another mod that throws when looked at stopped the server's tick. Fixed: it's
    dropped and logged once (a self-test row spawns one).
  - Design question 1 and code 2: more than 100 satellites kept strengthening sky hops, at 1,000 enough for Low 12
    blocks underground. Capped at Max's count (SkyStrengthTest, which failed without the cap; X38).
  - Code 3: a renamed Satellite Rocket stalled the pad, which found it by item and took it by a stack equal to its tag.
    Fixed: it takes the very stack it found (the self-test's launch uses a renamed rocket).
  - Code 5: a pair's two halves were saved at different times, so a crash between could keep one. Fixed: pairing and
    unpairing save both dimensions at once (a self-test row checks both files are clean right after).
  - Design question 2, radio through satellites making a walkie's range moot outdoors, is DESIGN.md 9.1 and D5 as
    written: a known issue for the owner. Question 3, planet to planet: DESIGN.md 9.5 asks for it, and it's built
    (GatewaysTest). Question 4: a receiver bridges no line and can't be locked, now X39.
- Second round, design review (1 medium, 4 low, 2 questions), "fix first", and code review (1 medium, 4 low), "fix
  first":
  - Design 1 and code 1: Twilight Forest pairs were held to vanilla's 128-block Nether search, where Twilight Forest
    2.7.44 looks 200 blocks around and moves a new portal up to 600 blocks out of a biome the player can't enter yet.
    Fixed: a reach per portal kind, 616 for the Twilight Forest, and room for the widest portals (PortalsTest cases
    that failed on the old check; the self-test's Twilight Forest relays now stand 300 blocks apart; X36).
  - Code 2 and design question 1: relays took any Personal Space portal as leading into every personal dimension.
    Fixed: a relay reads where each Personal Space portal leads, and the Overworld relay of a pair must stand by one
    into the far relay's dimension (PortalsTest; a self-test row links a portal's tile and checks the relay reads it).
  - Design 3 and code 5: a held launch kept the pad lit and GT's screen calling it running. Fixed: it goes dark and
    its screen says something covers the pad (self-test row).
  - Code 3: the immediate save of a pair ignored saving switched off, as during a backup copying the world. Fixed:
    neither half is written while either world has saving off (a self-test row that failed on the old code). The
    third round found the window this leaves after a /save-off.
  - Code 4: a dimension with no name made the pad's screen throw every tick. Fixed: it shows the number
    (PacketRoundTripTest, which threw before).
  - Design 4: the no-fuel launch had no test. Added (self-test row).
  - Design 2 and 5: the ME Bridge's narrower link rule wasn't logged and its texts offered any node; X18 didn't say
    where 0.4's ceilings live. X40 added, texts and X18 corrected.
  - Design question 2: two Personal Space portals into one personal dimension, or End portals at two strongholds,
    let relays join two distant Overworld networks at High in mid MV, the job DESIGN.md 9.2 gives the late-HV dish.
    That is how those portals work under D46, so it's the owner's balance call, below.
  - Both reviewers found nothing in the range behind the self-test's one failed run (run 87, 11 of 0.3's Videophone
    rows). GregTech's explosion log had the cause: the self-test's own rain set the phone antenna's energy hatch on
    fire, and it exploded 35 s later. The self-test now switches GregTech's weather explosions off while it runs (its
    first check); what that hazard means for players is a known issue below.
- Third round, design review (6 low, 1 question), "ready", and code review (3 low), "ready":
  - Code 3 and design 3: nothing tested that a Personal Space pair goes into the dimension its portal leads to; the
    self-test only checked that the relay reads the tile. Fixed: the self-test makes two personal dimensions through
    Personal Space's own portal action, pairs into one, is refused by the other, and drops the pair when the portal is
    relinked. Taking out either check fails a row.
  - Code 2: a Personal Space tile of a class BeCom doesn't know counted as unlinked, refusing every pair silently,
    where the fallback was meant to be "leads anywhere". Fixed (PortalsTest, which reads Personal Space's own tile).
  - Design 2: a relay's targets could go stale in the network after its chunk reloaded. Fixed: its first look always
    reports.
  - Design 6: a relay's info screen didn't say where its Personal Space portal leads. It does now.
  - Code 1: a world loaded since a /save-off still saves its half of a new pair alone as it unloads. That can't be
    closed while the other world's saving is off; the javadoc now says so, and it's a known issue below.
  - Design 1, 4 and 5, and a code note: X36, X18, X40, three code comments and the changelog said less than the code
    does. Corrected.
  - Both reviewers asked about GregTech's weather explosions: a known issue below, for the owner.
  - These fixes got no fourth review: none is big or touches threads or saved data, and the self-test rows that check
    them fail with either check taken out.

**Deviations and GTNH Voice.** DEVIATIONS.md X33 to X40 are 0.4's: ME per dimension (X33), dishes, relays and uplinks
carrying calls but not radio (X34), receivers only on Galacticraft's worlds (X35), a relay's own wrench link and the
check that a pair stands on one portal (X36), the dish's stand (X37), the cap at 100 satellites (X38), a receiver that
bridges no line and can't be locked (X39), and an ME Bridge that links to neither a dish nor a relay (X40). X18 gained
0.4's config ceilings. None is a 16.2 fallback: the rocket assumption held. VOICE.md: nothing new.

**Known issues.**

- GregTech's weather hazards hit BeCom's machines like any GT machine's. With GT's `machineRainExplosions` and
  `machineFireExplosions` on, their defaults, a powered machine standing in the rain without a cover on top, or with
  an open side, catches fire at random, and one time in ten explodes on the spot; fire next to it can blow it up
  later. Only machines holding energy explode: Repeater and Router Heads on their pylons, Line Terminals,
  Transceivers, Videophones, ME Bridges, Portal Relays and every hatch, an antenna's energy hatch and the Launch
  Pad's and dish's included, which stand under open sky by design. Covers on the top and open sides protect them, as
  on any GT machine, and don't count against the pad's and dish's open-sky checks. The self-test hit this (run 87).
  DESIGN.md is silent and BeCom keeps GT's rule; to spare BeCom's own machines, they could override
  `willExplodeInRain`, while GT's hatches stay GT's. The owner's call.
- Relays through Personal Space portals or End portals can join two distant Overworld networks at High in mid MV:
  two portals into one personal dimension, or End portals at two strongholds, with relays and a short line beyond.
  That is the job DESIGN.md 9.2 gives the late-HV Satellite Dish. It's how those portals work under D46, so it's the
  owner's balance call.
- A relay pair made while /save-off is on: a world loaded since then still saves its half as it unloads, so a crash
  before the other world's next save keeps one half, and the pair has to be made again.
  BeCom can't write the other half while its saving is off, which is the point of /save-off. ServerUtilities'
  backups switch saving off in worlds loaded during them too, so they don't open this window.
- DESIGN.md 9.1 and D40 say maintenance problems slow a launch "as on any GT machine". GT's processing multiblocks
  instead keep their speed and draw more power with lower efficiency. The pad slows down, as the design asks.
- Radio through satellites makes a walkie's range moot outdoors once a dimension has 5 satellites: any two radios
  under open sky hear each other at the sky's level, however far apart. That is DESIGN.md 9.1 and D5 as written, and
  a balance question for the owner.
- From 0.3: video under Angelica and shader packs, a far player's skin, and video's own channel wait on the owner;
  the ME Phone's Infinity Booster Card and security checks (X27); the Phone key and Telephones (X1); the corner
  window's size (X29). From 0.1 and 0.2: quest text shows default numbers (X14), the IC2 rubber GT-ERR-01 line in the
  boot log, X22's survey cost, and V1, V3 and V5.

**Owner checks.** Two players with voice chat, a GTNH 2.8 install with GTNH Voice 0.10.1 or newer, BeCom from this
branch, operator rights for `/becom satellites`, and the Modernity add-on pack for the second look at the textures:

1. Launch: build a Satellite Launch Pad (the quest's text has the blocks) under open sky, with an HV energy hatch.
   Put a Satellite Rocket in its bus and 1,000 L of rocket fuel in its hatch. The launch sound plays, and 30 s later
   `/becom satellites` lists one satellite over the dimension and First satellite completes. The pad's screen shows
   the rockets waiting, the satellites over each dimension and when the next comes down. Put a block over the pad
   during a launch: the pad goes dark, its screen says something covers it, and the launch goes on once the block
   goes.
2. Sky hops: `/becom satellites 0 5`. A Mobile Phone under open sky far from any antenna shows Low; at 37 satellites
   High, the Mobile Phone's cap. Under a roof the bars drop; in a cave there are none. Two walkies far apart under
   open sky hear each other. In the rain the phone's hop drops 3 dB.
3. The Signal Analyzer, held under open sky away from antennas, names the satellites as what serves it, with the
   roof's and the rain's losses; `/becom signal` says the same.
4. Dishes: two Telephone networks far apart, each with a Satellite Dish wrench-linked to its Line Terminal or an
   antenna. With 5 or more satellites the Telephones call each other. The dish's screen shows its hop; a maintenance
   problem takes 2 dB off it; a block over the dish stops it.
5. 0.4's first "Done when": fly a Tier 1 rocket with a chest to the Moon, place it on a landing pad there and put a
   Rocket Radio Receiver in its cargo; the player on the Moon holds a Mobile Phone near it. With 37 or more satellites
   over the Overworld, the other player, under open sky in the Overworld, calls the Moon phone: it rings, connects at
   High, and both hear each other. A Telephone on a network with a dish reaches it too. (16.2, the real flow: a
   rocket flown by a player comes down as an item in a parachest, and counts once placed on the pad.)
6. 0.4's second "Done when": Portal Relays within 4 blocks of a Nether portal on both sides, each wrench-linked to a
   Telephone network's Line Terminal, powered at MV. Wrench the Overworld one, go through, wrench the Nether one: they
   pair, and the Telephones call each other at High. A relay by a different Nether portal far away refuses to pair.
7. The same through a Twilight Forest portal, an End portal (the stronghold's, and the exit portal in the End), and a
   Personal Space portal: each pair carries a call at High. For Personal Space, the Overworld relay must stand by a
   portal into the far relay's own dimension: one by a portal into another player's dimension is refused. A Twilight
   Forest trip that lands far from where the portal leads, out of a biome you can't enter yet, still pairs.
8. Look at the Satellite, Satellite Rocket and Rocket Radio Receiver, and the Launch Pad's, dish's and relay's faces,
   next to GT's machines, in GT's style and with the Modernity add-on pack; listen to the launch from a few blocks
   away.
9. The quest book's BeCom tab shows the seven space quests with their task texts; pairing relays, launching the first
   satellite, having 5 over the pad's dimension, and a call between dimensions each complete theirs.

## 1.0 Polish

Branch `milestone/1.0-polish`, from the last commit of `milestone/0.4-space`.

### Contents (DESIGN.md 16)

| Item                                                                                            | Status |
|-------------------------------------------------------------------------------------------------|--------|
| A final pass over the art: every texture in both styles, next to GT's (15.1, D32, D53)          | done   |
| A final pass over the sounds (15.2, D53)                                                        | done   |
| The Modernity add-on pack, zipped by CI as `Modernity-GTNHAddon-BeCom.zip` for releases (15.1)  | done   |
| Italian: every string, the quest text included (15.3, 15.5)                                     | done   |
| The GuideNH guide: the tiers, how signal works, reading the Signal Analyzer (15.4, D48)         | done   |
| Quests point to their guide pages (15.5)                                                        | done   |
| WAILA info for BeCom's machines and blocks (16.4: Waila 1.19.34, optional)                      | done   |
| A balancing pass: the tunables, recipes and quest rewards (17, 3.1, 15.5, D51)                  | done   |
| A load test against the budgets in 6.5                                                          | done   |
| README                                                                                          | done   |
| Ready for the first public release, which the owner tags (16, D33)                              | done   |

### Untested assumptions (DESIGN.md 16.2)

| Assumption                                                                                      | Status  |
|-------------------------------------------------------------------------------------------------|---------|
| GuideNH's data-driven loader finds BeCom's pages (tried by the owner)                           | blocked |

`blocked`: DESIGN.md 16.2 has the owner try it in a game client. GuideNH 1.3.41's own code was read instead: it scans
every mod jar's resources for `assets/<modid>/guidenh/_<lang>/`, so BeCom's pages should form the `becom:guidenh`
guide. It's on 1.0's owner checklist, with its fallback built if it fails there.

### Quests (DESIGN.md 15.5)

| Item                                                                                            | Status |
|-------------------------------------------------------------------------------------------------|--------|
| Each quest points to its guide page                                                             | done   |
| The quest text in Italian                                                                       | done   |

### Assets (DESIGN.md 15)

| Item                                                                                            | Status |
|-------------------------------------------------------------------------------------------------|--------|
| Italian language entries                                                                        | done   |
| Guide pages in English and Italian                                                              | done   |
| Textures in both styles, after the final pass                                                   | done   |
| Sounds, after the final pass                                                                    | done   |

### Summary

**Built.** Polish on top of 0.4, and the mod ready for its first public release:

- Italian, every string and all 28 quests' text, kept line for line with English by `tools/quests/make_quests.py` and
  `quests_it.py`. Names from other mods follow those mods' own Italian files (15.3).
- The GuideNH guide in English and Italian, eight pages each: the tiers, how signal works, the Signal Analyzer, the
  wired network, antennas, radios, phones, and space and other dimensions (15.4, D48). Each page lists its items and
  quests, so holding GuideNH's key over one opens it, and each quest's text ends with a link to its page (15.5). With
  BetterQuesting and no GuideNH, the link shows its words alone. Every in-game tag holds its words, so the pages read on
  GitHub too, where the README links them; the start page says the numbers are defaults a server can change (X14).
- WAILA for all nine machines and the Telephone (16.4): what GT's scanner shows, less the line for each link, in the
  looking player's language, and a Telephone's number, owner, and whether it rings or its handset is lifted. WAILA's
  request is handled on the server thread, so the lines are worked out as it asks, and only for a player in the
  machine's dimension whose eyes are within their reach of it, plus 3 blocks.
- The Modernity add-on pack, zipped by CI as `Modernity-GTNHAddon-BeCom.zip` on every build and attached to releases
  (15.1). The final pass over textures and sounds changed nothing.
- The balancing pass (17, 3.1, D51): recipes match 3.1, quest rewards sit at GTNH's commonest for each tier, and the
  tunables stay at DESIGN.md 17's values. Three big quests offer their coins as a choice against a BeCom item (X41), and
  a choice keeps only the options whose items are installed. On the defaults, a walkie's 300,000 EU last 4 h 10 min on
  standby, 2 h 05 min receiving and 62.5 min talking; a Mobile Phone's 1.2M EU, 4 h 10 min on standby and 62.5 min in a
  call. Satellites last about 33 h 20 min of world time, so holding 5 over a dimension takes a launch every 20 Minecraft
  days, 14 every 7, and 100 one a day, at 288,000 EU, 1,000 L of fuel and a rocket each.
- `./gradlew loadTest` times BeCom's per-tick work at a busy server's scale against 6.5's budgets (below).
- Radio publishes cost far less at scale, from the review: an antenna mesh's reach is worked out over its link matrix
  without building a graph, the players who hear each speaker are found by cell, and a press of push-to-talk works out
  only that radio's links, everyone's being worked out once a second (6.4).
- Forge refuses to load BeCom beside a GTNH Voice older than v0.10.1, naming the version it needs. Not so, found on
  `voice-api`: mcmod.info's `useDependencyInformation` made FML take its unversioned lists over @Mod's, so it loaded
  BeCom beside any GTNH Voice. `voice-api` fixes that, and a dev boot with 0.10.1 now stops at the missing-mods error.
- A README for players and server operators, a changelog that reads as release notes, and a release workflow that takes
  its notes from the tag's changelog section, or from Unreleased when there's none.

**Tests.**

- 428 unit and routing tests, all passing: 398 from 0.1 to 0.4, 30 new. Guide pages: titles, links between pages, each
  quest's link and the page listing it back, the same pages and quests in both languages, and every tag holding its
  item's name (7). The GTNH Voice version FML accepts and refuses (6). WAILA: lines written and read back as keys,
  worked out each time WAILA asks, a machine that throws giving none, and the reach from the eyes, two Rings of Far
  Reach included (7). Quest rewards: a choice losing options that aren't installed, and renumbering (2).
  BetterQuesting's guide tags without GuideNH (2). Italian keeping every English key and format (1). The radio cell
  index (2), each listener's devices found by cell matching the old scan (1), and an antenna mesh's reach matching the
  graph it replaced, NaN included (2).
- The self-test: 292 checks on a real dev server, all passing, 7 new: WAILA's first look at a Line Terminal getting its
  lines, the Telephone's number, nothing for a player 14 blocks off or in another dimension, WAILA taking the
  Telephone's provider through IMC, the first-call quest offering its coins as a choice once its definition is reloaded,
  and a press of push-to-talk working out a keyed radio that moved while leaving one that didn't. It also logs BeCom's
  own server tick: over about 3,130 ticks, mean 0.13 ms, p99 1.1 to 1.2 ms, max 10 to 12 ms. In the runs looked at
  closely before the review, every tick over 3 ms came at the first use of a code path.
- The load test, on this 4-core cloud VM with Java 25 (not part of the build; it prints a report):
  - Surveys: 64 T3 antennas 240 blocks apart all surveyed in 14 ticks at 2 ms each, p95 2.02 to 2.03 ms. A few ticks a
    run, none in some runs, go to 2.4 to 6.2 ms when the VM stalls the thread. One T3 survey with its links costs about
    0.45 ms, so the budget sustains about 27,000 T3 antennas surveyed again every 5 minutes.
  - Access for 200 players, each with a walkie and a phone: 0.19 to 0.22 ms a tick. Direct links: 0.05 to 0.07 ms a
    tick.
  - Routing on GTNH Voice's thread: a radio frame to 199 listeners, 100 to 140 µs, of which the bare sends are 15 to 25
    µs; a call frame among 8, 2 to 4 µs.
  - A radio publish with 30 radios keyed on one channel and 200 radios on it, all hearing each other: every radio's
    links, once a second, 5.1 to 5.6 ms in one tick; one press of push-to-talk, 0.5 ms. Measured along the review's
    fixes: finding each listener's devices took 19.3 ms the old way and 3.0 ms by cell; a whole publish took 9.7 ms
    before the reach rewrite; and a press used to cost a whole publish.

**Review.** Three rounds (D58). In the first, a design reviewer and a code reviewer side by side on the milestone
(`milestone/0.4-space..78f359f`; the release-notes fix a794deb landed while the code reviewer worked). Its fixes,
`78f359f..25d8b1b`, touched threads and changed when radio links are worked out, so a fresh code reviewer took them in
the second round, and another took the second round's fixes, `25d8b1b..83c3546`, in the third. The third round's fixes
are `83c3546..623963e`: small, and touching neither threads nor saved data. Each finding was confirmed by a failing
test, a self-test check or the code before it was fixed, as each item says.

- First round, design review (1 critical, 7 minor):
  - Design 1: tagging v1.0.0 couldn't make a release. The changelog action wants a section named for the version, and
    the changelog has only Unreleased, so the job would stop before `gh release create` (read in the action's code).
    Fixed (a794deb): the job takes the tag's section, or Unreleased when there's none; the code reviewer dry-ran it.
  - Design 2: the changelog, which becomes the release notes, carried milestone prefixes and lines only developers care
    about. Fixed (8292f57).
  - Design 3: GitHub drops GuideNH's tags, so the guide pages the README links read with holes. Fixed (a553c58): each
    tag holds its words. GuideTest checks that every tag holds its item's name, and failed before.
  - Design 4: the guide states DESIGN.md 17's defaults, which a server can change. Kept, as X14 does for the quest text:
    the start page says the numbers are defaults, and X14 covers the guide (032d962).
  - Design 5: the Italian page called the Parachest by its English name, where Galacticraft's Italian file has Cassa del
    razzo. Fixed (56f6343).
  - Design 6: GTNH Voice draws a group's name as the server sends it, so an Italian player on a dedicated server sees
    "Call". Confirmed in GTNH Voice 0.10.1's bytecode; logged as VOICE.md V6, with the server-side translation in one
    marked class (a3bdf68).
  - Design 7: the GTNH Voice version the README asks for wasn't enforced. Fixed (7306826): GtnhVoiceVersionTest, which
    failed before because the dependency accepted v0.9.2 and v0.10.0, and a dev boot with v0.10.1. That fix didn't
    work: FML read mcmod.info's dependencies instead, and the dev boot ran the version it allowed anyway. Fixed on
    `voice-api`, with a test that reads mcmod.info as FML does.
  - Design 8: the load test let a survey tick run 1 ms over its 2 ms budget. Tightened to 0.5 ms (5e0b422), on p95
    rather than p99 (e5a7685) once the tighter bound failed one run in four on this VM's stalls.
- First round, code review (4 minor):
  - Code 1: WAILA requests left in the static queue outlived a server stop, and two machines' lines could load an
    unloaded dimension's network. Fixed (bbc8735); the second round showed the queue itself wasn't needed, below.
  - Code 2: the queue's drain had no bound per tick. Fixed (9d320e1): a test ran the same machine 1,000 times in one
    tick before, once after. Moot since the queue's removal.
  - Code 3: the load test didn't time a radio publish as the server runs it, and the publish found each listener's
    devices by looking at every walkie for every listener. Fixed in four commits: the publish timed in the load test
    (25d8b1b); each listener's devices found by cell (a53b89f, CarriersTest against the old scan, 19.3 ms down to 3.0
    ms); an antenna mesh's reach without building a graph (e7a1627, AntennaMeshTest against the graph, about 100 µs down
    to a few); and a press working out only that radio's links (2224ea4, 0.5 ms a press). A whole publish once a second
    still takes 5 to 6 ms at that scale: a known issue.
  - Code 4: WAILA served BeCom's lines for any position a client named, a Telephone's number and owner included. Fixed
    (a353073): only a player close by in the machine's dimension gets them; a unit test and a self-test row.
- Second round, a fresh code reviewer on the first round's fixes (a correction, 3 minor, a test gap):
  - WAILA's request is handled on the server thread, not a network thread: FML's packet keeps the default no priority,
    so Minecraft queues it for `networkTick` (checked in the decompiled sources). The snapshot queue, built on the
    belief it was a network thread, only delayed the first look. Fixed (32ca135): the lines are worked out as WAILA
    asks, and the queue and its lifecycle hooks are gone.
  - Code 1: the reach rewrite let a NaN access link, from a device at a position that isn't a number, spread to every
    antenna, where the graph had left it out. Fixed (4213547), with the reviewer's failing test.
  - Code 2: WAILA's 8 blocks counted from the feet, too tight for Botania's Ring of Far Reach. Fixed (10feffa): 12
    blocks from the eyes, with the reviewer's failing test.
  - Code 3: the version range accepts a GTNH Voice version written without the "v". Rejected: every release tag, v0.1.0
    to v0.10.1, has it, and the version string comes from the tag.
  - Test gap: nothing ran the publish a press makes. Fixed (83c3546): two self-test rows, the first of which failed with
    the moved-radio check taken out (run 103).
- Third round, a fresh code reviewer on the second round's fixes (3 minor, nits):
  - Code 1: Baubles has two ring slots and Botania's Rings of Far Reach stack, so a fixed 12 blocks still cut off a
    player wearing two. Fixed (48e92c4): the reach is the one the server keeps for each player, which the rings raise,
    plus 3 blocks; with the reviewer's failing test.
  - Code 2: GT's Drone Centre camera asks for a machine's WAILA data for a player far away, so a dish or pad with a
    Drone Downlink as its maintenance hatch shows GT's lines there without BeCom's. Left as a known issue: letting the
    camera through would mean trusting the machine and position the client names.
  - Code 3: a radio at a position that isn't a number still turns its through-antenna and sky SNR to NaN outside
    `reach`. From 0.2 and 0.4, and it takes a modified client getting a NaN position past 1.7.10's move checks: a known
    issue.
  - Nits, all taken (2bd2a9f, 623963e): the WAILA rows in one step, the Telephone's row through the provider WAILA took
    by IMC, the exception comment, ClientProxy's comment about the packet thread, and comments spotless had wrapped
    mid-sentence.

**Deviations and GTNH Voice.** DEVIATIONS.md X41 is 1.0's: three big quests offer their coins as a choice against a
BeCom item. X14 now covers the guide as well as the quest text. The 16.2 GuideNH assumption is still untried, so no
fallback was built. VOICE.md V6 is new: GTNH Voice shows a group's name as the server sends it, so an Italian player on
a dedicated server sees "Call".

**Known issues.**

- BeCom adds only the quests a book is missing, so a world that already holds them keeps their old rewards and tasks
  after an update; the text does update, from the language files. The 0.x test worlds keep 0.x's rewards, X41's coin
  choices included, until `/bq_admin default load` or BetterQuesting's pack-version reload adds them again. Fix: refresh
  BeCom's own quests' definitions at server start, keeping progress.
- Group names are in the server's language (VOICE.md V6): "Call" on a dedicated server, whatever the client's language.
  "Radio 3" reads the same in both. On `voice-api`, each client translates them (GTNH Voice 0.11.0; LangTest), which
  needs an Italian client to see.
- A whole radio publish, once a second, still works out every keyed radio's links in one tick: 5 to 6 ms with 30 radios
  keyed on one channel and 200 hearing them all. Spreading it over the second's ticks by radio would flatten that.
- The squelch tail at a release still looks at every online player for each walkie that held the speaker.
- A player watching a Satellite Dish or a Launch Pad through GT's Drone Centre camera, with a Drone Downlink as its
  maintenance hatch, sees GT's WAILA lines without BeCom's.
- A radio at a position that isn't a number gets NaN for its paths through antennas and satellites: it keeps its links
  for the drop grace, then loses them. It takes a modified client getting such a position past 1.7.10's move checks.
- The coin choices' values were never tried in a played game.
- The load figures come from one 4-core cloud VM, which stalls the odd tick.
- From 0.4: GregTech's rain hazards on BeCom's machines, relays through Personal Space and End portals at MV, a relay
  pair made under /save-off, the pad slowing where GT draws more power, and radio through satellites making a walkie's
  range moot outdoors. From 0.3: video under Angelica and shader packs, a far player's skin, video's own channel, the ME
  Phone's Infinity Booster Card and security checks (X27), the Phone key and Telephones (X1), the corner window's size
  (X29). From 0.1 and 0.2: the IC2 rubber GT-ERR-01 line in the boot log, X22's survey cost, and V1, V3 and V5, which
  `voice-api` fixes with GTNH Voice 0.11.0.

**Owner checks.** A GTNH 2.8 client with GuideNH and WAILA, GTNH Voice 0.10.1 or newer, BeCom from this branch, and for
the second part a dedicated server and a second player:

1. GuideNH (16.2's untested assumption): `/guide list` names `becom:guidenh`, and `/guide open becom:guidenh` opens the
   start page, with the eight pages in the navigation and items drawn with their icons. Switch Minecraft to Italian: the
   pages are the Italian ones. If GuideNH doesn't find them, 16.2's fallback is the Markdown in the repo, linked from
   the README, which reads there now.
2. In the quest book's Better Communications tab, the link at the end of each quest opens its page. Holding G over a
   quest, or over a BeCom item's tooltip in an inventory or NEI, opens its page.
3. Look at a Line Terminal with a phone on its cable, a linked Pylon Head, an antenna and a Telephone: WAILA shows their
   state, link counts and the phone's number in English, and in Italian after switching.
4. Put `Modernity-GTNHAddon-BeCom.zip` from a CI run above Modernity: BeCom's textures switch to the Modernity style.
5. On a new world, the First call, Radio contact and Five satellites quests offer their coins or the BeCom item as a
   choice. On a 0.x world, run `/bq_admin default load` first.
6. With GTNH Voice 0.9.2 in the mods folder, the game stops at Forge's missing-mods screen, naming gtnhvoice and the
   version BeCom needs.
7. On a dedicated server, two players on one walkie channel: the speaker list shows "Radio 1"; in a call, "Call", for an
   Italian client too (V6).
8. To release: merge into master; if wanted, rename `## [Unreleased]` to `## [1.0.0] - <date>` with an empty Unreleased
   above it; tag `v1.0.0`. The release gets the jars, the Modernity zip and the changelog's section as its notes.
