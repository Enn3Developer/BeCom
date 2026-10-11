# Working on BeCom

Better Communications (BeCom) is a GT New Horizons 1.7.10 addon built on GTNH Voice. [DESIGN.md](DESIGN.md) says what
to build, in what order, and how to check it. This file says how to work.

The rules under [The run](#the-run) are for building the milestones unattended. In any other session, the owner's
instructions come first; the rest of this file always applies.

## The run

One Claude Code session in the cloud builds every milestone in [DESIGN.md 16](DESIGN.md#16-roadmap), in order: 0.1,
0.2, 0.3, 0.4, 1.0 (D54). Nobody is watching. The owner reviews everything afterwards.

- You're on a cloud VM with a fresh clone. Nothing from the owner's machine is here, and a VM left idle can be
  reclaimed with everything that isn't pushed.
- Read DESIGN.md in full before starting, and the sections a milestone touches again before starting it.
- Don't stop to ask. Where DESIGN.md is silent, make the plainest choice that fits it, and log it if it matters
  ([DEVIATIONS.md](#deviationsmd)).
- After a context reset, or on a fresh VM: fetch, check out the latest milestone branch, and read `PROGRESS.md`,
  `DEVIATIONS.md` and `git log` before anything else. Then carry on where they say.
- Never edit DESIGN.md. Where the code differs from it, that goes in DEVIATIONS.md for the owner to decide.
- Work in the clone itself: no worktrees, for you or for the reviewers.

### Branches

- One branch per milestone (D55): `milestone/0.1-wired`, `milestone/0.2-radio`, `milestone/0.3-phones`,
  `milestone/0.4-space`, `milestone/1.0-polish`. The first starts from `master`; each later one starts from the last
  commit of the one before.
- Push the branch after every commit.
- Never push to `master`, delete a branch, push a tag or open a pull request. The owner merges after review.

### Finishing a milestone

1. Every item in its row of DESIGN.md 16, its quests and its assets is `done` in PROGRESS.md, or replaced by a logged
   fallback.
2. `./gradlew build` passes, and so does the self-test in the server boot ([Checking your work](#checking-your-work)).
3. Adversarial review (D58). Spawn the `design-adversary` and `code-adversary` agents from `.claude/agents/` together,
   in one message and in the foreground, so the session stays busy while they work. Give each the milestone, its
   branch and its commit range: `master..HEAD` for 0.1, the previous milestone's branch `..HEAD` after that. Then take
   every finding in turn:
   - confirm it with a failing test, a self-test check or the code itself, then fix it and keep the test;
   - or reject it, with a concrete reason;
   - or, when the fix doesn't belong in this milestone, record it as a known issue.

   A big fix, or one that touches threads or saved data, gets a fresh reviewer of the same kind before you move on.
4. Steps 1 and 2 still hold after the fixes.
5. Its changes are in `CHANGELOG.md` under Unreleased, and its summary is in PROGRESS.md.

Then start the next milestone's branch from this one.

### When something fails

- An assumption from DESIGN.md [16.2](DESIGN.md#162-untested-assumptions) fails: build its fallback, log it in
  DEVIATIONS.md, carry on (D56).
- Anything else fails, with no agreed fallback: make the smallest change that keeps the milestone's "Done when" true,
  log it, carry on.
- Never move on from a failing build.

## Commits

- Small commits, one change each: subject `[AI] <area>: <what changed>`, a body that says why, and the Co-Authored-By
  trailer. Wrap at 120 columns.
- Before every commit, run `./gradlew spotlessApply test`. Never commit code that doesn't compile or fails a test.
- Never tag. Tags publish releases (`.github/workflows/release-tags.yml`), and BeCom stays private until 1.0 (D33).

## Checking your work

DESIGN.md [16.3](DESIGN.md#163-verification) has the five layers. In practice:

- Logic goes in classes with no Minecraft imports, with unit tests. Routing tests use GTNH Voice's
  `RoutingContext.builder()` with a capturing `PacketSender`.
- Boot the server the way CI's run-server job does (`.github/workflows/build-and-test.yml`), but feed it
  `becom selftest` on stdin instead of `stop`: the self-test stops the server when it's done. It passes when the log
  shows `BeCom self-test: N passed, 0 failed`, with no crash report and none of the failure patterns the workflow greps
  for.
- Keep the workflow's run-server job doing exactly that, and have the workflow run on pushes to `milestone/**` as well
  as `master`, with a newer push to a branch cancelling that branch's older run.
- There's no game client in the cloud. Whatever needs one goes on the milestone's owner checklist.

## PROGRESS.md

The ledger. Create it on the first milestone's branch, and update it in the same commit as the work it tracks.

- When a milestone starts, add its section: every item from its row in DESIGN.md 16, its quests and its assets, each
  with a status: `todo`, `doing`, `done`, `fallback X<n>` or `blocked`.
- When it ends, add its summary:
  - **Built:** what works now.
  - **Tests:** what the unit, routing and self-tests cover, with counts.
  - **Review:** what each reviewer found, and for each finding whether it was fixed (with its test), rejected (with
    the reason) or left as a known issue.
  - **Deviations** and **GTNH Voice:** this milestone's entries in DEVIATIONS.md and VOICE.md.
  - **Known issues:** what's broken, missing or slow.
  - **Owner checks:** steps for everything that needs a game client or two players, each with what should happen.

## DEVIATIONS.md

Every place the code differs from DESIGN.md, and every choice where DESIGN.md is silent that changes gameplay, balance,
saved data, config, commands or names. Small code-level choices don't belong here. One entry each:

    ### X<n>: <title>
    - **Where:** milestone, DESIGN.md section, decision if any
    - **Design says:** ...
    - **Built:** ...
    - **Why:** ...
    - **To match the design:** what it would take

## VOICE.md

What BeCom needs from GTNH Voice (D57). Use only GTNH Voice's public API, the `com.enn3developer.gtnhvoice.api`
packages, and never change the gtnh-voice repo. When BeCom needs something the API can't do, add an entry to
[VOICE.md](VOICE.md) in its format, build the entry's stopgap in one place marked `// VOICE.md V<n>`, and carry on.

## Code

- Match the surrounding code and GTNH's conventions. Comments say why, not what.
- Every value from DESIGN.md 17 is read from the config, except the three that
  [14.2](DESIGN.md#142-config-and-saved-data) keeps fixed. No magic numbers.
- Every player-facing string goes through the language files ([15.3](DESIGN.md#153-languages)).
- Machine IDs only from 25000–25199, behind the startup check ([14.1](DESIGN.md#141-machine-ids)).
- `route()` runs on GTNH Voice's network thread: it reads only the routing snapshot and its `RoutingContext`
  ([13.3](DESIGN.md#133-routing)).
- No mixins (`usesMixins = false`). Needing one is a deviation.
- Only the dependency versions in DESIGN.md [16.4](DESIGN.md#164-versions).

## Assets

Generated, never drawn or recorded by hand (D53). The scripts live in `tools/assets/` (Python 3 with numpy and Pillow,
ffmpeg for OGG Vorbis). Commit the scripts and what they make; the build never runs them. Look at every texture you
make next to the GT textures it sits on before committing it. The Modernity style follows Modernity's own pack
([References](#references)): use it for style and palette only, and never copy from it.

## References

Other repositories aren't attached to the session. Read them with a shallow clone, or file by file through
`raw.githubusercontent.com`.

- GTNH's quest book, to balance quest rewards (DESIGN.md [15.5](DESIGN.md#155-questline)): the
  `config/betterquesting/DefaultQuests` folder of
  [GT-New-Horizons-Modpack](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack).
- Modernity, for the add-on pack's style: [Modernity-GTNH](https://github.com/ModernityGTNH/Modernity-GTNH).
- GTNH Voice's source, to read only: [gtnh-voice](https://github.com/Enn3Developer/gtnh-voice).
