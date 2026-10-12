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
| Machine ID check (14.1)                                | todo   |
| Config and saved-data versions (14.2)                  | todo   |
| The self-test (16.3)                                   | todo   |
| Pylon (5.1)                                            | todo   |
| Line Terminal (5.2)                                    | todo   |
| Repeater (5.3)                                         | todo   |
| Router (5.4)                                           | todo   |
| Phone Cable (5.5)                                      | todo   |
| Telephone (5.6)                                        | todo   |
| Their recipes (3.1)                                    | todo   |
| Numbers (8.2, D25)                                     | todo   |
| Contacts (8.2, D25)                                    | todo   |
| Phone screen (11.2)                                    | todo   |
| `becom:call` routing (13.3)                            | todo   |
| Line loss (5.7)                                        | todo   |
| Levels (4.1)                                           | todo   |
| Dropouts (4.2)                                         | todo   |
| Call audio (4.3, 13.5)                                 | todo   |
| Ownership (12.1)                                       | todo   |
| Linking rules (12.2)                                   | todo   |
| Block list (12.3)                                      | todo   |
| Do-not-disturb (12.3)                                  | todo   |
| Rate limit (12.3)                                      | todo   |
| Quest support: BeCom's task type (D50)                 | todo   |
| Quest support: the off switch (15.5)                   | todo   |
| CI runs on milestone branches, newer pushes cancel     | done   |
| CI's run-server job runs the self-test                 | todo   |

### Untested assumptions (DESIGN.md 16.2)

| Assumption                                                         | Status |
|--------------------------------------------------------------------|--------|
| Repeater and Router heads take power straight from a cable         | todo   |
| BeCom can add its questline through BQ's API, also after a reload  | todo   |

### Quests (DESIGN.md 15.5)

| Item                    | Status |
|-------------------------|--------|
| The LV quests           | todo   |

### Assets (DESIGN.md 15)

| Item                                                              | Status |
|-------------------------------------------------------------------|--------|
| Front overlays and glows: Pylon, Repeater, Router heads, Terminal | todo   |
| Telephone model and textures                                      | todo   |
| Phone Cable texture                                               | todo   |
| Modernity style of all of the above                               | todo   |
| Sounds: dial tone, ringback, busy, unreachable, ringtone, hang-up | todo   |
| English language file                                             | todo   |
