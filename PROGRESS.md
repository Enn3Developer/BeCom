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
