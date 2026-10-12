# Deviations

Every place the code differs from [DESIGN.md](DESIGN.md), and every choice where DESIGN.md is silent that changes
gameplay, balance, saved data, config, commands or names. The owner decides on each one.

### X1: Sneak + right-click opens a Telephone's phone screen

- **Where:** 0.1, DESIGN.md 5.6, 11.1, 11.2, D34
- **Design says:** right-click picks up the handset, and opens the phone screen only when there is more than one
  phone to dial. The Phone key opens the phone screen otherwise.
- **Built:** sneak + right-click on a Telephone opens its phone screen without touching the handset, so its owner can
  reach the settings (do-not-disturb, hide, block list) and recent calls. From 0.3 the Phone key answers a ringing
  Telephone within its cord, but the screen it opens is that of the first phone the player carries (X26), never a
  Telephone's.
- **Why:** without it, a Telephone's settings can only be reached while its handset is up and more than one phone is
  reachable.
- **To match the design:** have the Phone key open the screen of a Telephone within reach when the player carries no
  phone, then drop the sneak branch in `BlockTelephone.onBlockActivated`.

### X2: A terminal with more than one phone on its cable serves none, and so does a phone on two terminals

- **Where:** 0.1, DESIGN.md 5.2, D19
- **Design says:** a Line Terminal connects to exactly one Telephone or Videophone through Phone Cable.
- **Built:** the terminal scans its cable once a second; with exactly one phone it serves it, with none or several it
  serves nothing, and its scanner info says how many it found. The other way round, a phone found on the cables of
  two terminals is served by neither, and each terminal's scanner info says so. Calls never pass through a phone
  either way: the call graph treats each phone as a start or an end only.
- **Why:** DESIGN.md doesn't say what happens when a player wires two phones to one terminal, or one phone to two
  terminals. Picking one would make D19 depend on scan order, and a phone shared by two lines used to join them into
  one network with no Router (found in the 0.1 design review).
- **To match the design:** nothing to change unless the owner prefers, say, the nearest phone.

### X3: A terminal's link to its pylon spans up to 4 times that pylon's height

- **Where:** 0.1, DESIGN.md 5.1, 5.2
- **Design says:** two pylons link within 4 times the shorter one's height. It gives no limit for a terminal's link.
- **Built:** a terminal on the ground uses its pylon's height in the same rule, so it can be up to 24 to 64 blocks from
  its pylon. Only head-to-head links get the clearance check, as DESIGN.md 5.1 says.
- **Why:** the plainest rule that fits; a terminal has no height of its own.
- **To match the design:** `LinkRules.maxSpan`, if the owner wants a different limit.

### X4: The second wrench click links, or unlinks a pair that is already linked

- **Where:** 0.1, DESIGN.md 5.1, 12.2
- **Design says:** GT wrench on head A, then on head B, to link; "wrench on a linked head to unlink".
- **Built:** the first wrench click selects a node (kept for 5 minutes); a second click on another node links the two,
  or unlinks them if they're already linked; clicking the same node again cancels. Sneak + wrench keeps GT's own
  wrench action (turning the block).
- **Why:** a head can have up to 15 links, so "wrench on a linked head" alone can't say which link to cut.
- **To match the design:** nothing, unless the owner wants a different gesture.

### X5: Operators may always unlink, and alone manage what has no owner

- **Where:** 0.1, DESIGN.md 12.2, D37
- **Design says:** only the owner of either end, or their team, can unlink.
- **Built:** also server operators (permission level 2), so a griefed or orphaned network can be repaired. GT records
  whoever places a machine as its owner, fake players included, so a machine a robot placed belongs to that robot's
  fake player, and anything acting as that same fake player can manage it. A machine with no owner recorded at all
  (from a world editor, or a mod that sets none) can be unlinked only by an operator, or through the owner of the
  other end.
- **Why:** admins have no other way to undo a link in 0.1. The first version let anyone manage what had no owner,
  which the 0.1 design review pointed out opened those links, and ownerless phones' settings, to everyone.
- **To match the design:** remove the operator check in `Ownership.mayManage`.

### X6: A Telephone's owner is whoever placed it

- **Where:** 0.1, DESIGN.md 12.1
- **Design says:** a phone's owner is its first user.
- **Built:** for the Telephone block, placing it counts as first use: the placer owns it and names it in contacts. A
  Telephone placed by a machine has no owner until the first player uses it, who then owns it. So does one whose
  number isn't in the registry at all (pasted from another world, a copy of a phone since broken, or every phone after
  the registry file is deleted): it gets a fresh number with blank settings on first use, owned by whoever used it.
  A copy of a Telephone whose original still has its record belongs to the original's owner (X17). Machines' fake
  players never own one, so the self-test can't check a claim.
- **Why:** a block has a placer; waiting for the first pickup would leave a fresh phone ownerless and nameless.
- **To match the design:** register the owner on the first pickup in `CallService.takeHandset` instead.

### X7: "No answer" is a chat message and the busy tone

- **Where:** 0.1, DESIGN.md 11.2, 15.2
- **Design says:** after 30 s without an answer the caller hears "no answer". DESIGN.md 15.2 has no sound for it.
- **Built:** the caller gets a "No answer" message and, still holding the handset, the busy tone, which is also what a
  handset plays when the other side hangs up.
- **Why:** there's no listed sound to play; the busy tone is the conventional dead-line tone.
- **To match the design:** add a "no answer" sound to 15.2 and play it from `CallService.ended`.

### X8: Answering a call needs a voice session

- **Where:** 0.1, DESIGN.md 10
- **Design says:** "unreachable" includes having no voice session, but a block phone has no known callee until someone
  picks it up.
- **Built:** a player without a GTNH Voice session can't answer: the phone keeps ringing and they're told why. Callers
  without voice can't dial out either.
- **Why:** answering without voice connects a call in which nobody can be heard.
- **To match the design:** nothing to change; noting it because it's a rule DESIGN.md doesn't state.

### X9: Blocking is done from the contacts and recent calls lists

- **Where:** 0.1, DESIGN.md 11.2, 12.3
- **Design says:** the phone screen's settings have a block list.
- **Built:** the settings page lists blocked players, a page at a time, with an Unblock button. A Block button on each
  contacts row blocks that phone's owner; on each recent calls row it blocks the player on the other end of that call
  (whoever called, from whichever phone), or the phone's owner when nobody answered an outgoing call. There's no
  block-by-name field. The server only accepts a contact that row's player can see, and a recent row by its time and
  number; a recent row of a call nobody answered blocks the other phone's owner at the time. A phone blocks at most
  128 players, and the screen lists at most 512 contacts (the first 512 by name).
- **Why:** blocking by typed name would need a name lookup that can block the server thread on Mojang's API. Blocking
  any typed number would let a modified client learn who owns a hidden number, and the 0.1 code review found that
  blocking a recent row by the phone's owner missed someone who called from another player's phone. The cap keeps the
  screen's data inside one packet; a network with more than 512 reachable phones needs the number typed in.
- **To match the design:** nothing, unless the owner wants a name field limited to online players.

### X10: The handset cord reaches 1.2 blocks from the phone to the player's body

- **Where:** 0.1, DESIGN.md 5.6
- **Design says:** you can move 1 block away; further than that hangs up.
- **Built:** the call hangs up once the gap between the phone's block and the player's body (their 0.6 × 1.8 hitbox)
  is more than 1.2 blocks, checked 4 times a second. The handset can only be taken from within that reach, so picking
  up from across the room doesn't hang up again a moment later.
- **Why:** "1 block away" from a player standing next to the phone, with a little slack so standing still at the edge
  doesn't hang up. Measuring to the body rather than the feet keeps a phone on a desk or at head height usable; the
  first version measured to the feet, and the 0.1 code review found a desk phone hung up after 0.8 blocks.
- **To match the design:** `TileTelephone.CORD_REACH`.

### X11: A Telephone's recent calls show only to its owner and their team

- **Where:** 0.1, DESIGN.md 11.2, 12.2
- **Design says:** the phone screen has recent and missed calls; calls are private to the people in them.
- **Built:** anyone can open a Telephone's screen to dial, but its recent and missed calls list is empty unless they
  own the phone or are on its owner's team (or an op). They see every call made from or to that phone, including
  calls other people made on it, each with the name of the player on the other end.
- **Why:** a desk phone stands in the open. Showing its history to whoever walks up would tell anyone who called the
  owner and when (found in the 0.1 design review). Its owner seeing everything made on their own phone, like a
  phone's log, is the plainest reading; DESIGN.md doesn't say who sees a shared phone's history.
- **To match the design:** drop the `manage` check around the history in `PhoneScreenActions.view`; or, to keep calls
  the viewer wasn't in private, show only the phone's number and direction for those.

### X12: Phone Cable's assembler recipe takes programmed circuit 2

- **Where:** 0.1, DESIGN.md 3.1, D31
- **Design says:** Phone Cable ×2 from Red Alloy Wire and 72 L Rubber in the assembler, 8 EU/t, 5 s.
- **Built:** the same, plus programmed circuit 2, which isn't used up. Circuits 1, 4 and 5 already make other things
  from that wire in GT's assembler; 2 is free, and easy to remember for a recipe that makes two.
- **Why:** GT makes Red Alloy Cable in the assembler from the same wire, circuit 24 and 144 L of rubber. GT checks a
  machine's last recipe first, so without a circuit of its own a machine that last made Phone Cable kept making it
  from GT's inputs (found in the 0.1 design review, confirmed by the self-test). GT keeps overlapping assembler
  recipes apart the same way, which is what D31 asks for.
- **To match the design:** drop `.circuit(PHONE_CABLE_CIRCUIT)` in `BeComRecipes`, and accept the clash.

### X13: An offline Repeater or Router cuts its line

- **Where:** 0.1, DESIGN.md 5.3, 5.4, D11
- **Design says:** powered nodes work only while loaded and powered (D11). 5.3 doesn't say what happens to a line
  whose Repeater is down.
- **Built:** a Repeater or Router that is unpowered, switched off or in an unloaded chunk passes nothing: the line
  through it is dead until it's back. Its links stay.
- **Why:** D11 has powered nodes work only while loaded and powered; passing the signal through unregenerated would
  make a Repeater's power optional. It does mean a Repeater far from every player needs its chunk loaded: in the
  "Done when" test the Repeater sits 150 blocks from each player, inside the loaded area at view distance 10 but not
  at 8 (found in the 0.1 design review).
- **To match the design:** if the owner prefers pass-through, `WiredTopology` can treat an offline Repeater as a
  passive pylon.

### X14: Quest text names DESIGN.md 17's default values

- **Where:** 0.1, DESIGN.md 15.3, 15.5, 17
- **Design says:** every tunable is a server config option; quest text lives in BeCom's language files.
- **Built:** quest descriptions give the default numbers (32 blocks of cable, 4 EU/t, pylons 6 to 16 frames, a
  repeater every 225 blocks). Machine tooltips show the server's own values.
- **Why:** BetterQuesting shows a quest's language string as it is, with nothing to fill in a server's values, so a
  server that changes those config options shows stale numbers in the quest book (found in the 0.1 design review).
- **To match the design:** a server that changes them can override the strings in a resource pack; BeCom could also
  word the quests without numbers.

### X15: A Line Terminal walks at most 128 blocks of Phone Cable in all

- **Where:** 0.1, DESIGN.md 5.5, 17
- **Design says:** a Telephone connects to its terminal through up to 32 blocks of Phone Cable.
- **Built:** the phone must be within 32 blocks of cable from the terminal, as designed, and a terminal whose cable
  run holds more than 128 blocks in all (4 for every block of length) serves no phone; its scanner info says why.
- **Why:** the terminal scans its cable every second, and with only the length limit a solid blob of cable reached
  about 44,000 blocks within 32 steps (found in the 0.1 code review). Branches and spare runs fit easily in 128.
- **To match the design:** raise `PhoneCables.CABLES_PER_LENGTH`, at the cost of slower scans.

### X16: Saved data that can't be read stops the server; a newer Telephone is left alone

- **Where:** 0.1, 0.2, 0.3, DESIGN.md 14.2
- **Design says:** data newer than BeCom understands stops the server instead of being overwritten.
- **Built:** the same for data BeCom can't read at all: a network or phone registry file that exists but fails to
  load (cut short by a crash mid-save, say) stops the server with the file's path. So does data in a known format
  holding something this BeCom doesn't know: a node, phone or history kind, more links than a kind has slots, or an
  invalid or repeated phone number (reported as a damaged file, since no BeCom writes those). The one exception is a
  Telephone block saved by a newer BeCom: its tile entity is kept as it was (only its position is written fresh),
  never given a number, and tells whoever uses it to update BeCom. From 0.2, the same holds for an antenna, a
  Transceiver or a walkie-talkie whose BeCom keys a newer BeCom wrote: the machine stays offline and the walkie
  dead, their keys are written back as they were, and a scanner or tooltip says to update BeCom. Their keys, which
  share GT's machine NBT or IC2's charge tag, carry `becomFormat` (the 0.2 design review found them unversioned).
  From 0.3, the same for a Mobile Phone, an ME Phone, a Videophone and an ME Bridge. Such a bridge also stays out of
  ME, and its AE2 node's data, which holds its AE2 owner and its network, is written back with its keys.
- **Why:** vanilla swallows the read error and hands back empty data, which BeCom would then save over the file,
  losing every link or number (found in the 0.1 code review). Stopping leaves the file for the admin, who can restore
  a backup, or delete the file to start with none (for the phone registry, every Telephone then goes to the first
  player who uses it, X6). A Telephone's data comes with its chunk, while the server is
  already running, where stopping would only crash it. Leaving it alone protects it unless a player breaks it: then
  the newer data is lost with the block, and its number stays reserved, since this BeCom can't see which it was.
- **To match the design:** start empty and log instead of stopping, accepting the overwrite; for the Telephone,
  crash the server on such a chunk.

### X17: Numbers are held for good; a moved Telephone keeps its own, and a copy gets a new one

- **Where:** 0.1, DESIGN.md 8.2, 12.1, D12
- **Design says:** every phone gets a number unique in the world, assigned when first placed; a phone's owner is its
  first user.
- **Built:** a number is freed only when its Telephone is broken. A Telephone moved by a frame or a dolly takes its
  record along on its first tick at the new spot. A copy of one that still stands (a world editor's, say), or one
  whose old spot isn't loaded, takes a fresh number at once, owned by the original's owner and with its
  do-not-disturb, hiding and block list. The history stays with the old number.
- **Why:** DESIGN.md doesn't say what happens to a number whose phone disappears without being broken, or to copies.
  A version that freed the numbers of vanished Telephones once a minute also freed the numbers of phones in transit
  and of phones saved by a newer BeCom (found in the third round of the 0.1 reviews), and a vanished phone's number
  costs nothing: nobody can reach it. A copy holding the original's number would put one number on two cables. A
  phone carried away from a spot that then unloads is taken for a copy, which costs it its number and history but
  not its owner or settings.
- **To match the design:** nothing to change unless the owner wants vanished numbers freed, which needs a check that
  can tell a vanished phone from one being carried.

### X18: Pylon height and Phone Cable length have ceilings

- **Where:** 0.1, DESIGN.md 14.2, 17
- **Design says:** pylons are 6 to 16 frames and Phone Cable reaches 32 blocks by default; DESIGN.md 17 gives no
  limits on the config values.
- **Built:** `pylonMaxHeight` goes up to 255 and `phoneCableLength` from 1 to 256. From 0.3: `groupCallSize` from 2 to
  16, the video heights up to 512, rear pictures up to 640x360 and 32 KB (`rearMaxBytes`), `videoPoseRate` up to 20
  a second (one a server tick), and `meBridgeChannels` 0 or 1. Every whole-number option shows its
  range in the config file (those, and at least 1 where 0 would mean every tick or nothing at all), and a value outside
  it is used at the nearest end with a warning naming what was written. The checks no single range can hold (ordered
  level thresholds, the tallest pylon above the shortest, a burst of at least one voice frame, a format this BeCom
  understands and so on) run when a server is about to start with the file. A bad file refuses the world through FML's
  own startup notice, naming the key: on the console for a dedicated server, and on screen in single-player, where the
  client's file is the server's, leading back to the menu. A client joining a server never acts on its own file, so a
  bad one doesn't stop it.
- **Why:** a server sends its values to clients for tooltips and holograms, and without ceilings a hostile one could
  make a client's hologram loop two billion times, or overflow the cable cap (found in the third round of the 0.1
  code review). No frame column is taller than the world, and 256 blocks of cable is far past any use.
- **To match the design:** raise `Tunables.MAX_COLUMN`, `MAX_CABLE` and 0.3's `MAX_` ceilings beside them.

### X19: Antennas take up to 15 bridges and route like a Router

- **Where:** 0.2, DESIGN.md 7, 5.4
- **Design says:** an antenna can be wrench-linked to a Pylon Head or Line Terminal within 16 blocks, carrying the
  wired signal over radio. It doesn't say how many bridges one antenna takes, or whether lines bridged onto antennas
  join into one network.
- **Built:** an antenna has 15 bridge slots, a Router's count. Each bridge is a short stretch of line driven afresh by
  the antenna, which regenerates like a Repeater (DESIGN.md 5.7), and whatever an antenna bridges, together with
  whatever its UHF links reach, is one network: a phone on it can dial any other, as through a Router. Bridges hang a
  wire like any link but skip the clearance check, which DESIGN.md 5.1 gives to links between Pylon Heads.
- **Why:** DESIGN.md 5.6 already treats an antenna bridge like a router ("a line that reaches more phones, through
  routers or an antenna bridge, dials"), and an antenna is a powered node, so it drives the line like the others.
- **To match the design:** change `NodeKind.ANTENNA_T*`'s slot count if the owner wants fewer bridges.

### X20: Open ground isn't an obstacle

- **Where:** 0.2, DESIGN.md 6.1, 6.4, 8.1, 16.3
- **Design says:** `L_terrain` is single knife-edge diffraction over the worst obstacle on the path, with `h` how far
  the obstacle rises above the straight line between the ends, and a loss whenever `ν > −0.78`. The same section and
  8.1 give the worked ranges on open ground: a walkie about 80 blocks, a Transceiver about 125.
- **Built:** a terrain sample counts as an obstacle only when it stands higher than the ground under the lower end (a
  player's feet, a Transceiver's block, an antenna's casings). Anything higher is scored exactly as the design says.
- **Why:** taken literally, flat ground under two players is an obstacle 1.62 blocks under their line of sight, which
  at VHF costs about 3.5 dB halfway along an 80-block path: a walkie would reach about 66 blocks and a Transceiver
  about 75, and the Signal Analyzer would blame "terrain" in an empty field. The unit test that pins the design's
  ranges caught it. With the ground left out, open ground gives the worked ranges, and a hill or a house between still
  costs what the formula says.
- **To match the design:** drop the `floor` argument from `RadioModel.worstNuAlong` and `Survey.worstNu`, and lower the
  worked ranges.

### X21: One walkie per player, open Transceivers, and walkie drain in 0.2

- **Where:** 0.2, DESIGN.md 8.1, 11.3, 13.2, 16 (0.3's "battery drain"), D36
- **Design says:** players join `becom:radio.N` while carrying a working walkie tuned to N; the Transceiver's screen
  sets its channel and loudspeaker; walkies and phones drain their battery (D36), which DESIGN.md 16 lists under 0.3.
- **Built:**
  - A player talks and listens on one walkie: the first working one, the one in hand first, then the hotbar, then the
    rest of the inventory, then the bauble slots (D14). Other walkies switched on in the same inventory only drain
    their standby charge.
  - Anyone can tune a Transceiver and switch its loudspeaker, from within 8 blocks of it.
  - The walkie drains its battery from 0.2 on (1, 2 and 4 EU/t on standby, receiving and transmitting), and a flat one
    switches off. Phones get theirs with the Mobile Phone in 0.3.
  - The walkie's recipe takes GT's MV lithium battery, as GT's own MV tools do.
- **Why:** a player carrying walkies on two channels would otherwise transmit on both at once, and the routing
  snapshot holds one channel per speaker. Radio channels are public (DESIGN.md 12.2, D47), so a radio anyone can tune
  matches them. The walkie's battery is part of it from the start (DESIGN.md 8: "MV battery"), so the walkie can't
  ship in 0.2 without it.
- **To match the design:** let `RadioService` hold several channels per speaker, and check ownership in
  `TransceiverScreen`.

### X22: Surveys and direct links reach where a link at Low still holds

- **Where:** 0.2, DESIGN.md 4.1, 4.2, 6.3, 6.5
- **Design says:** a level is left only 2 dB below its threshold (4.1), and a link below Low for more than 3 s drops
  (4.2); a survey steps out "up to the antenna's maximum range", "at most 64 heights per bearing, about 4 KB per
  antenna" (6.3); 6.5 counts a T3 survey at about 4,000 height-cache reads.
- **Built:** surveys, device-to-antenna links and direct links all reach as far as a link already at Low holds on
  open ground, 4 dB instead of 6. A link between 4 and 6 dB holds at Low for the 3 s grace of 4.2 and then drops, and
  comes back only at 6 dB. What it costs against 6.3 and 6.5: a T3's survey is 80 steps a bearing, about 5 KB and
  5,120 height-cache reads; a device's access scan reads up to 80 heights per antenna instead of 64; each antenna is
  in range of devices over about 1.6 times the area; and a walkie's direct link samples out to 100 blocks instead of
  80. The ceiling on radio ranges in the config counts the hysteresis too.
- **Why:** stopping at the 6 dB range cut every link to None the moment it crossed it, so links at the edge of range
  flickered once a second, which 4.1's hysteresis exists to prevent (found by both 0.2 reviews; the self-test checks
  a walkie link holding at 81 blocks, then dropping after 3 s there).
- **To match the design:** keep 64 steps and give up hysteresis at the edge of range. Lengthening the survey step to
  5 blocks would keep 64 steps reaching as far, but trades this departure for another: DESIGN.md 17 sets the step at
  4 blocks.

### X23: `/becom signal` is for operators, and an end's own cell isn't terrain

- **Where:** 0.2, DESIGN.md 6.1, 6.4, 6.6, 12.3
- **Design says:** `/becom signal` gives the Signal Analyzer's breakdown in chat "for testing and debugging" (6.6);
  12.3's list of operator-only commands doesn't name it. The terrain term scores the worst obstacle on the path.
- **Built:**
  - `/becom signal` needs operator level 2, like the rest of `/becom`. Players get the same breakdown from the Signal
    Analyzer.
  - Terrain samples in either end's own 4×4 height-cache cell are skipped, on direct links, antenna links and surveys.
    A wall right beside a player, in their cell, counts as neither terrain nor cover; a roof over them counts as cover.
- **Why:** the command is a debugging tool, and BeCom's other commands are an operator's. The height cache keeps the
  highest point of each cell, so an end's own cell holds the end itself (an antenna's mast, the block a player
  stands next to) and would read as an obstacle on every path.
- **To match the design:** lower the command's permission for `signal` alone; for the cells, sample the end's column
  from the world, which is loaded, instead of skipping the cell.

### X24: A carried phone's number, owner and copies

- **Where:** 0.3, DESIGN.md 8.2, 12.1, D12, D25
- **Design says:** every phone gets a 6-digit number, unique in the world, assigned when it's first placed or used;
  a phone's owner is its first user.
- **Built:** a Mobile Phone gets its number the first time the server sees it carried, in the inventory or a bauble
  slot, or when its screen first opens; whoever carries it then owns it. A number found in two places at once (a
  copy made in creative or with a world editor) is the original's where it was seen first: the other stack gets a
  fresh number with the original's owner and settings, as a copied Telephone does (X17). A phone whose number this
  world never gave out (one brought from another world) gets a fresh number for its current carrier. A phone that
  nobody online carries, in a chest or dropped, has no service and can't be called.
- **Why:** carrying a phone is the only way to use one, so it's the first use the server can see. Two stacks with one
  number would ring together and split one call between two players.
- **To match the design:** nothing to change for the number; the copy rule is as X17's.

### X25: Group calls: who counts, adding, and who drops

- **Where:** 0.3, DESIGN.md 8.2, 10, 11.2, 10.2
- **Design says:** group calls take up to 8 people; the phone screen has an Add button during a call; a link below Low
  for more than 3 s drops.
- **Built:**
  - The size counts people in the call, everyone in a Videophone's room included, plus phones still ringing to join.
  - Anyone in a connected call can add a phone: the keypad's Call button and the contacts become Add during a call.
    An added phone that isn't answered within the ringing time, is rejected or loses its path leaves while the call
    goes on.
  - A phone drops out when every other phone in the call is cut off from it (more than 3 s below Low either way). The
    call ends once fewer than two phones are left. Two phones cut off from each other but not from a third stay, and
    don't hear each other until their path comes back.
  - A phone's recent calls record the phones it rang or was rung by, and in a group the others it was in the call
    with at the same time, as incoming.
- **Why:** DESIGN.md 4.2's drop rule is written for two phones; in a group, dropping the whole call over one weak
  member would cut everyone off. Counting the room keeps "up to 8 people" true with a Videophone.
- **To match the design:** count phones instead of people in `CallBook.add` and `setRoom`, if the owner meant phones.

### X26: Carried phones: power, silent mode, drain and the Phone key

- **Where:** 0.3, DESIGN.md 11.1, 11.2, 11.3, D34, D36
- **Design says:** settings are power, silent mode, do-not-disturb, hide from contacts and the block list; phones
  drain on standby and more in a call; the Phone key answers, hangs up, or opens the phone screen.
- **Built:**
  - Whoever carries a phone may switch it on or off, as with a walkie; silent mode, like the other settings, is for
    its owner and their team. Silent mode is a carried phone's only: a Telephone rings out loud as before.
  - A phone dialling out or ringing counts as in a call for the drain.
  - The Phone key, in this order:
    - with sneak, turns down a ringing phone the player carries;
    - hangs up a call they're in, or puts down a Telephone's handset they hold with no call;
    - answers a ringing phone they carry;
    - answers, or with sneak turns down, a ringing Telephone whose cord reaches them or a ringing Videophone whose room
      they stand in, the first in the call book's order. So anyone that close can turn its call down, as anyone can
      answer it;
    - otherwise opens the screen of the first phone they carry: held, then inventory, then bauble slots. A phone being
      moved with the mouse still counts as carried.
  - A ringing phone's notice shows while its player is free to answer it, for the latest of their phones to ring,
    which is the one the key answers. While they're in a call or hold a handset, where the key hangs up instead, the
    notice goes and the phone rings on out loud; the notice comes back when they're free. A ringing phone that changes
    hands takes its notice to whoever has it.
  - Someone holding a Telephone's handset can't dial or answer on a phone they carry, and is told to put the handset
    down first.
- **Why:** power is the device's own switch, while silent mode is the owner's choice like the rest of the settings.
  A ringing or dialling phone is busy with the call already. Hanging up comes before answering because a call has to
  end before another can be answered, there being no call waiting in v1, and a notice that said the key answers would
  be wrong while it hangs up. A carried phone's call made while holding a handset would end when the cord pulls the
  handset back.
- **To match the design:** D34 has incoming calls show a notice; here it waits while its player is in another call or
  holds a handset. Show it then too, with key hints saying the key hangs up. The rest fills in what the design leaves
  open.

### X27: The ME Phone's power, linking and range check

- **Where:** 0.3, DESIGN.md 8, 8.3, 17, D13, D45
- **Design says:** the ME Phone is linked to an ME network the same way as a wireless terminal, uses AE2's own
  wireless range check, and runs on AE power like a wireless terminal; DESIGN.md 17's phone battery and drain rows
  name no kind of phone. The ME Bridge uses one ME channel.
- **Built:**
  - Its battery and drain are the Mobile Phone's, 1,200,000 EU and 4 or 16 EU/t, in AE at AE2's own rate (2 AE to
    the EU): 2,400,000 AE, charged in an ME charger.
  - It is linked at a Security Terminal as an encodable item, not as a wireless terminal, so it doesn't also open
    the ME terminal.
  - Its range check is AE2's rule, active access points of its network and their ranges with boosters, done by
    BeCom: AE2 only runs it for items that are wireless terminals.
  - The ME Bridge's channel can be switched off in the config (`meBridgeChannels`, 0 or 1).
  - What this costs: GTNH's AE2 skips the distance test for a wireless terminal with an Infinity Booster Card in it.
    An ME Phone has no card slot, so it never gets that, though D13 has GTNH's booster cards apply unchanged; the
    access points' own boosters do. Nor does it check its user's AE2 security permissions, as a wireless terminal
    does: whoever carries a linked ME Phone calls through its network.
- **Why:** making it a wireless terminal would turn it into one, a feature DESIGN.md doesn't give it. The same
  battery as the phone it's made from keeps the two in step.
- **To match the design:** register the ME Phone as a wireless terminal handler and call AE2's `checkRange`, which
  also gives it the ME terminal; or give it the wireless terminal's own battery.

### X28: Who is in a Videophone's room, who works it, and its number

- **Where:** 0.3, DESIGN.md 10.2, 12.1, D11, D19, D42
- **Design says:** everyone within 4 blocks is in the Videophone's call, unless they're already in another one; it
  hangs up by itself once nobody has been within 4 blocks for 30 s; right-click opens its phone screen to dial,
  answer and hang up. A phone's owner is its first user.
- **Built:**
  - Its room is the people in the call through it: within 4 blocks of the block, nearest first, until the call
    reaches the group call size (8, everyone in it counted). Someone in another call, or holding a Telephone's
    handset, isn't in it, and nobody joins without a voice session or while their own phone rings; someone already in
    it keeps their voice's 10 s grace. The 30 s clock runs while the room is empty, so someone standing near in
    another call doesn't keep it on the line.
  - Its screen stays open from 8 blocks, as any block's does, but only someone in its room can dial, answer or hang
    up there, since its calls are the room's; anyone further off is told to step closer. Hanging up ends the call for
    the whole room, as the Phone key does from anyone in it. Answering needs a voice session, as at a Telephone (X8).
  - While it rings, its screen shows who calls, as a carried phone's notice does, and nobody else in the call. In a
    call, it shows the call to the people in it, wherever they stand, and to anyone else only that it's in use
    (DESIGN.md 12.2). A Telephone's screen does the same.
  - It works while loaded and powered (D11): not heard from for 2 s, it leaves its call, and it can't be called or
    show in contacts.
  - Its number works like a Telephone's (X17): one moved keeps its number, a copy gets its own. GT records whoever
    placed it, a machine's fake player too, so it is owned by its placer only if the server's username cache knows
    them as a player; otherwise its first user owns it (X6).
  - Players around its loudspeaker who aren't in its call hear each speaker at the level the Videophone hears them
    at, as around a Transceiver (DESIGN.md 13.5).
  - Dialling from it, the ringback plays for whoever dialled, and stops if they walk out of the room.
  - The Call tab of the other phones lists each person in its room by name.
- **Why:** DESIGN.md doesn't say how a speakerphone's room works past who is in it, and a timer kept running by
  someone who isn't in the call would leave the far side talking to nobody.
- **To match the design:** count everyone within 4 blocks for the 30 s timer, whatever call they are in.

### X29: Video calls: one to one, whose cameras, and what the server checks

- **Where:** 0.3, DESIGN.md 10.1, 10.2, 14, 17, D41, D42
- **Design says:** the rear camera streams in one-to-one calls only; each camera can be switched on or off during a
  call, the front starting on and the rear off; the server caps each client's rear-camera frames by size and rate.
- **Built:**
  - One to one means two people on two phones. A Videophone with two people in its room makes a group call, and the
    rear camera stops.
  - The Videophone has no rear camera: its screen shows its room as front-camera tiles, and nobody's first-person
    view.
  - Everyone switches their own cameras, from the screen of the phone they're in the call on, a Videophone's
    included. The switches go back to front on, rear off when they leave the call.
  - "Size" is the picture's bytes: the server refuses pictures over `rearMaxBytes` and more than `rearMaxFps` a
    second from one client, and never reads a picture, so not its width and height either. The receiving client
    reads a picture's width and height before decoding it, and drops one larger than the config's ceiling.
  - A viewer's rear pictures are held to their level's rate by an allowance that fills at that rate and holds two
    pictures, not a fixed gap after the last one: pictures land on server ticks, so one a tick early after one a tick
    late still goes on, and the average stays at the level's rate.
  - The corner window is at most a quarter of the screen wide. While a rear picture comes in, it shows that picture
    with its sender in an inset; the phone screen's Video tab shows the picture above the people. A screen open over
    the game hides the window, chat aside.
- **Why:** DESIGN.md doesn't say who counts in "one to one", whose camera a shared Videophone's switches turn, or
  which size the server checks; reading a JPEG's dimensions would mean parsing what the server otherwise only
  relays.
- **To match the design:** count phones instead of people for one to one; give the Videophone's screen switches for
  the whole room.

### X30: Call tones on a phone with no handset

- **Where:** 0.3, DESIGN.md 10, 15.2
- **Design says:** dial tone, ringback, busy, unreachable and hang-up are ordinary Minecraft sounds (DESIGN.md 10), with
  15.2's cadences. It doesn't say when each stops; on a Telephone, 0.1 plays the busy tone on a dead line (X7), and
  the client loops it until the next tone, the click of the handset going down.
- **Built:** a carried phone and a Videophone have no handset to put down. Dialling from one, the ringback plays until
  the call connects or ends, and a call ending, or the phone leaving it, plays the hang-up click, which stops it. A
  busy tone plays for 3 s and stops by itself. Adding a phone to a call makes no ringback over the call; an added
  phone that doesn't answer, turns the call down or is lost is told to whoever added it, in chat.
- **Why:** DESIGN.md's tones are a Telephone's, where the handset ends them; without one, a looping tone never stopped.
- **To match the design:** nothing to undo; give the phones' tones their own sounds if the owner wants them to differ.

### X31: The Signal Analyzer reads both bands

- **Where:** 0.3, DESIGN.md 6.6, 11.4, D15, D26, D35
- **Design says:** the analyzer shows SNR, level, the serving antenna and the biggest loss, weather among them; so does
  `/becom signal`.
- **Built:** both show two readings: what a radio gets where the player stands, on VHF and stopping at Medium, and what
  a Mobile Phone gets, on UHF with rain there, stopping at High. Weather only ever shows on the phones' band.
- **Why:** weather costs UHF alone (D26), so a VHF-only reading, as in 0.2, could never show it, and phone users would
  read the radio band's numbers.
- **To match the design:** nothing to undo; show one band, chosen by what the player carries, if the owner prefers.

### X32: A phone's contacts are kept a second

- **Where:** 0.3, DESIGN.md 14 (path finding), 5.6, 11.2
- **Design says:** a call's best path is found again only when a node, a link or an access level changes.
- **Built:** the call graph solves each starting point's best paths once and keeps them until the network changes, as
  the design says. On top of that, the list of phones a phone can reach, which its screen's contacts and a
  Telephone's pick-up read, is kept a second per phone. That list can be up to a second old: a phone that just came into reach shows a second late, and
  one that just went out of reach still shows, though dialling it checks the path again and is refused.
- **Why:** an open phone screen asks for its contacts every tick, and the list is a lookup for every phone in reach
  and every carried phone switched on. Kept a second, a screen costs one list a second. Carried phones' service
  changes about once a second anyway, as they move.
- **To match the design:** keep the list until the network or a carried phone's service changes, instead of a second.
