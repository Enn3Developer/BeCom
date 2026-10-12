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

### X2: A terminal that finds more than one phone on its cable serves none

- **Where:** 0.1, DESIGN.md 5.2, D19
- **Design says:** a Line Terminal connects to exactly one Telephone or Videophone through Phone Cable.
- **Built:** the terminal scans its cable once a second; with exactly one phone it serves it, with none or several it
  serves nothing, and its scanner info says how many it found.
- **Why:** DESIGN.md doesn't say what happens when a player wires two phones to one terminal, and picking one of them
  would make D19 depend on scan order.
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
- **Built:** the settings page lists blocked players with an Unblock button; a Block button on each contacts and recent
  calls row blocks the owner of that number. There's no block-by-name field.
- **Why:** blocking by typed name would need a name lookup that can block the server thread on Mojang's API.
- **To match the design:** nothing, unless the owner wants a name field limited to online players.

### X10: The handset cord reaches 1.5 blocks from the phone

- **Where:** 0.1, DESIGN.md 5.6
- **Design says:** you can move 1 block away; further than that hangs up.
- **Built:** the call hangs up once the player's feet are more than 1.5 blocks from the phone's block, measured from
  its sides, checked 4 times a second.
- **Why:** "1 block away" from a player standing next to the phone, allowing for the player's own half-block width.
- **To match the design:** `TileTelephone.CORD_REACH`.
