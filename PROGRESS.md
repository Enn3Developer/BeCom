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
  smaller range, players between it and 16 blocks hear neither.
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
| Radio model: link budget, knife-edge terrain, cover, noise (6.1)     | doing  |
| Surveys (6.3) within the survey budget (6.5)                         | doing  |
| Direct links between radios (6.4)                                    | doing  |
| Antennas T1, T2, T3: structure, power, recipes (7, 3.1, 3.2)         | doing  |
| Antenna links on VHF and UHF, and bridges onto wired lines (7)       | doing  |
| Antenna screen: links and team lock (11.4, 12.2)                     | doing  |
| Transceiver: machine, screen, loudspeaker, recipe (8, 11.3, 3.1)     | todo   |
| Walkie-Talkie: item, screen, sneak + scroll, battery, recipe       | todo   |
| Push-to-talk key and the `radio` activation (11.1, 13.4)             | todo   |
| Channels `becom:radio.1` to `16` (8.1, 13.1, 13.2)                   | doing  |
| Capture effect (D24)                                                 | doing  |
| Walkie speaker (D21)                                                 | doing  |
| Radio audio filters per level (4.3, 13.5)                            | todo   |
| Signal Analyzer: item, overlay, recipe (6.6, 11.4, 3.1)              | todo   |
| `/becom signal` (6.6)                                                | todo   |
| The 0.2 tunables in the config (17)                                  | done   |
| Self-test rows for 0.2 (16.3)                                        | todo   |

### Untested assumptions (DESIGN.md 16.2)

| Assumption                                                         | Status |
|--------------------------------------------------------------------|--------|
| Caps Lock works as a held push-to-talk key on every OS             | todo   |

### Quests (DESIGN.md 15.5)

| Item                                                               | Status |
|--------------------------------------------------------------------|--------|
| The radio quests, with the Radio contact task                      | todo   |

### Assets (DESIGN.md 15)

| Item                                                               | Status |
|--------------------------------------------------------------------|--------|
| Antenna T1, T2, T3 head overlays and glows                         | todo   |
| Transceiver overlay and glow                                       | todo   |
| Walkie-Talkie icon, with its TX light                              | todo   |
| Signal Analyzer icon                                               | todo   |
| Modernity style of all of the above                                | todo   |
| Sound: walkie squelch tail                                         | todo   |
| English language entries                                           | todo   |
