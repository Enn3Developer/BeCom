# Better Communications

Better Communications (BeCom) adds telephones, radios, mobile phones and satellites to GT New Horizons. Every call and
every radio channel is real voice, carried by [GTNH Voice](https://github.com/Enn3Developer/gtnh-voice), and how well
you hear someone depends on the path between you: how far it runs, the hills in the way, how deep underground each end
stands, and the weather.

## What it adds

- **LV:** Telephones on cable, with Routers to join buses of it into a network and the Exchange, a hall of switching
  racks, for a whole town's calls. Then the first antenna, which carries a network's calls over the air.
- **MV:** walkie-talkies and the Transceiver, on 16 open radio channels, with taller antennas that repeat them. The
  Signal Analyzer shows your signal and what costs it most. The Videophone brings video calls to wired networks, and
  the Portal Relay joins networks through Nether, End, Twilight Forest and Personal Space portals.
- **HV:** the Mobile Phone, served by antennas and later by satellites, which a Satellite Launch Pad puts into orbit.
  The Satellite Dish joins networks through them, and a Rocket Radio Receiver in a landed rocket links a planet back to
  the Overworld.
- **EV:** the ME Phone, with service wherever its ME network's wireless access points reach, and the ME Bridge, which
  joins an ME network to the rest.

Machines are GregTech machines, built and powered like any other, and their recipes follow GregTech's. A quest book
tab, an in-game guide and WAILA info come with them, all in English and Italian.

## Requirements

On both the server and every client, for Minecraft 1.7.10 with Forge 10.13.4.1614:

- GTNH Voice 0.11.0 or newer
- GregTech 5 Unofficial, Applied Energistics 2 Unofficial, Baubles Expanded and Galacticraft, the GTNH versions

Used when they're installed:

- **BetterQuesting:** BeCom adds its own tab to the quest book.
- **GuideNH:** the in-game guide, in English and Italian.
- **WAILA:** what each BeCom machine and Telephone is doing.
- **ServerUtilities:** its teams are BeCom's teams. A team's owner, moderators and members can lock each other's
  antennas and change each other's phone settings, and an antenna locked to the team serves
  all of them; its allies can't. Without ServerUtilities, only a machine's owner and server operators manage it, and
  a locked antenna serves its owner alone. On a dedicated server, the "singleplayer" team a world first played alone
  keeps isn't one for BeCom, since anyone there may join it.
- **Twilight Forest, Personal Space:** Portal Relays work at their portals too.

## Installing

Put the BeCom jar in the `mods` folder of the server and of every client. Players using
[Modernity](https://github.com/ModernityGTNH/Modernity-GTNH) can put `Modernity-GTNHAddon-BeCom.zip`, attached to each
release, in their resource packs above Modernity.

BeCom's GregTech machines use IDs 25000 to 25199. If another mod already took one of them, BeCom stops the game at
startup and names the machine, rather than letting two machines share an ID.

## Playing

Start with the **Better Communications** tab of the quest book: it goes through the machines in order. For the
details, open the guide, or point at a BeCom item or quest and hold GuideNH's guide key. The guide's pages can also be
read [here](src/main/resources/assets/becom/guidenh/_en_us/index.md).

| Key | Default | Does |
| --- | --- | --- |
| Radio push-to-talk | Caps Lock | Talk on your radio's channel while held |
| Phone | N | Answer, hang up, or open your phone's screen; with sneak, turn a call down |

Both can be changed in Minecraft's controls screen.

## For server operators

The server's settings are in `config/becom.cfg`: every range, budget, power draw and timing, read at startup. Clients
learn what they need from the server. At each start, BeCom brings its own quests up to date
with the ones it ships, keeping players' progress, so hand edits to them are lost. Setting `questlineEnabled`, under
`quests`, to false keeps BeCom out of the quest book, for servers with a curated one.

Commands, for operators:

- `/becom calls`: the calls going on.
- `/becom hangup <id>`: ends a call.
- `/becom signal`: your signal where you stand, with every loss.
- `/becom satellites [<dimension> <count>]`: each dimension's satellites, or sets how many orbit one, for testing.

Pack makers can merge BeCom's quests into their own book: the files are in the jar, under
`assets/becom/questline/DefaultQuests`, in GTNH's layout and with fixed IDs. A pack that changes them sets
`questlineEnabled` to false, or BeCom puts its own back at the next start.

## Building

```bash
./gradlew build
```

The tests run with the build. `./gradlew runServer25` starts a development server on Java 25, where `becom selftest`
builds what it checks far from spawn, prints one line per check and stops the server; CI runs it on every push.
`./gradlew loadTest` times BeCom's per-tick work against its budgets at a busy server's scale.

The textures and sounds are generated by the scripts in `tools/assets`, and the quests by `tools/quests`.

## License

[LGPL-3.0](LICENSE).
