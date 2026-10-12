# Deviations

Every place the code differs from [DESIGN.md](DESIGN.md), and every choice where DESIGN.md is silent that changes
gameplay, balance, saved data, config, commands or names. The owner decides on each one.

### X1: Sneak + right-click opens a Telephone's phone screen

- **Where:** 0.1, DESIGN.md 5.6, 11.2, D34
- **Design says:** right-click picks up the handset, and opens the phone screen only when there is more than one
  phone to dial. The Phone key opens the phone screen otherwise, but the Phone key comes with 0.3.
- **Built:** sneak + right-click on a Telephone opens its phone screen without touching the handset, so its owner can
  reach the settings (do-not-disturb, hide, block list) and recent calls in 0.1.
- **Why:** without it, a Telephone's settings can only be reached while its handset is up and more than one phone is
  reachable.
- **To match the design:** drop the sneak branch in `BlockTelephone.onBlockActivated` once the Phone key opens the
  screen.

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

### X5: Operators may always unlink

- **Where:** 0.1, DESIGN.md 12.2, D37
- **Design says:** only the owner of either end, or their team, can unlink.
- **Built:** also server operators (permission level 2), so a griefed or orphaned network can be repaired.
- **Why:** admins have no other way to undo a link in 0.1.
- **To match the design:** remove the operator check in `Ownership.mayManage`.

### X6: A Telephone's owner is whoever placed it

- **Where:** 0.1, DESIGN.md 12.1
- **Design says:** a phone's owner is its first user.
- **Built:** for the Telephone block, placing it counts as first use: the placer owns it and names it in contacts. A
  Telephone whose number was lost (copied by a world editor, say) gets a fresh number with no owner.
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
  block-by-name field. The server only accepts a contact that row's player can see, and a recent row by position and
  number. A phone blocks at most 128 players.
- **Why:** blocking by typed name would need a name lookup that can block the server thread on Mojang's API. Blocking
  any typed number would let a modified client learn who owns a hidden number, and the 0.1 code review found that
  blocking a recent row by the phone's owner missed someone who called from another player's phone. The cap keeps the
  screen's data inside one packet.
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
  own the phone or are on its owner's team (or an op).
- **Why:** a desk phone stands in the open. Showing its history to whoever walks up would tell anyone who called the
  owner and when (found in the 0.1 design review). DESIGN.md doesn't say who sees a shared phone's history.
- **To match the design:** drop the `manage` check around the history in `PhoneScreenActions.view`.
