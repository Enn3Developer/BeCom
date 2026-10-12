# What BeCom needs from GTNH Voice

BeCom talks to GTNH Voice only through its public API, the `com.enn3developer.gtnhvoice.api` packages. When BeCom needs
something that API can't do, the need is listed here, and the fix goes into GTNH Voice itself (D57 in
[DESIGN.md](DESIGN.md#2-decision-log)). Until it's there, BeCom uses the entry's stopgap, kept in one place in its code
and marked `// VOICE.md V<n>`.

The owner reviews this list and decides what goes into GTNH Voice. Claude Code sessions working on BeCom add entries
when they find a need, and never change GTNH Voice themselves.

## Entry format

    ### V<n>: <what's needed>
    - **Status:** open, in GTNH Voice <version>, or dropped
    - **Needed by:** the BeCom milestone and DESIGN.md section
    - **Missing:** what BeCom can't do, and why it matters
    - **Proposed API:** a sketch, for the owner to change freely
    - **Stopgap:** what BeCom does until then, and what's wrong with it
    - **Found:** when, and by whom

Once an entry's status names the GTNH Voice version BeCom builds against, BeCom switches to the real API and drops the
stopgap.

## Entries

### V1: Read the proximity range

- **Status:** open
- **Needed by:** 0.1, DESIGN.md D17 and [13.3](DESIGN.md#133-routing)
- **Missing:** BeCom doesn't route call or radio audio to a listener who hears the speaker directly: one within
  `min(16, r)` blocks, where `r` is GTNH Voice's effective proximity range. GTNH Voice routes proximity audio up to
  `min(distance, maxDistance)` from its server config, but that config is internal, and both values can be set as low
  as 1.
- **Proposed API:** `int GtnhVoiceApi.proximityRange()`: the effective server-side range in blocks, the same value the
  proximity group cuts off at. Safe to call from `IGroup.route()` on the network thread.
- **Stopgap:** BeCom uses 16, as if `r` were at least 16. GTNH Voice's default is 48, so this is only wrong on servers
  that set `distance` or `maxDistance` below 16. There, players between `r` and 16 blocks from a speaker hear them
  neither directly nor through BeCom.
- **Found:** design review, 2026-10-12.

### V2: Video frames over the voice transport

- **Status:** open, only needed if BeCom's own channel lags
- **Needed by:** 0.3, DESIGN.md [14](DESIGN.md#14-server-architecture) (video)
- **Missing:** BeCom sends front-camera poses (about 10 a second) and rear-camera frames (up to 8 KB, up to 5 a
  second) on its own Forge network channel, which shares the game's TCP connection. If that lags the game, video
  should move to GTNH Voice's UDP transport, which carries no addon data.
- **Proposed API:** an addon data channel on the voice transport: small byte payloads to one session, unreliable,
  unordered and size-capped, with a receive callback on the client.
- **Stopgap:** BeCom's own channel, with the rates and size caps in DESIGN.md [17](DESIGN.md#17-tunables).
- **Found:** design review, 2026-10-12.

### V3: Quieter positional audio with its own range

- **Status:** open, polish
- **Needed by:** 0.2, DESIGN.md D21 and [8.1](DESIGN.md#81-radio-channels)
- **Missing:** BeCom plays radio from a walkie-talkie's holder to players within 6 blocks (D21), and from a Transceiver
  or Videophone loudspeaker to players within 16. A POSITIONAL send always plays with voice's own falloff (inverse
  distance from 1 block), so all BeCom can do is stop sending past its range. A walkie at 6 blocks still plays at
  about −16 dB, so it cuts off audibly instead of fading out.
- **Proposed API:** either the position a POSITIONAL frame plays from, on `IPlaybackFrame`, so a playback stage can
  fade it by distance, or an optional maximum distance on `RoutingContext.sendTo`.
- **Stopgap:** voice's falloff, cut off at BeCom's range.
- **Found:** design review, 2026-10-12.

### V4: Routing tests through API types

- **Status:** open, tests only
- **Needed by:** 0.1, DESIGN.md [16.3](DESIGN.md#163-verification) (routing tests)
- **Missing:** `RoutingContext.builder()` takes GTNH Voice's internal `VoiceServerSession`, and a capturing
  `PacketSender` receives internal packet classes. So BeCom's routing tests, which CLAUDE.md asks to go through that
  builder, have to build `VoiceServerSession`, `AesEncryption`, `PlayerAudioPacket` and read `SourceAudioPacket`, all
  outside the `api` packages. A GTNH Voice update can break the tests without breaking BeCom.
- **Proposed API:** a test session factory in the `api` packages (or builder overloads taking `IVoiceSession` and an
  `IAudioFrame`), and a sender callback that gets the recipient session, source state, position and source id as
  plain values.
- **Stopgap:** BeCom's test fixture builds the internal types, in one place (`RoutingFixture`). Nothing outside the
  tests touches them.
- **Found:** 0.1 design review, 2026-10-12.

### V5: Whether a send went through

- **Status:** open, polish
- **Needed by:** 0.2, DESIGN.md 15.5 (the Radio contact task)
- **Missing:** `RoutingContext.sendTo` returns nothing, and drops a send silently when a higher-priority group already
  served that listener this frame. BeCom counts a radio contact for the quests when it sends a radio frame, so it
  also counts one that GTNH Voice dropped because the two players were, say, in a call with each other.
- **Proposed API:** `sendTo` returns whether it sent, or `RoutingContext` gets `isServed(UUID)`.
- **Stopgap:** count the contact on every send; the case it miscounts is two players already talking another way.
- **Found:** code review, 2026-10-12.
