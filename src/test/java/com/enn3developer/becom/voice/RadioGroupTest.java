package com.enn3developer.becom.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.gtnhvoice.api.server.ActivationIds;
import com.enn3developer.gtnhvoice.api.server.SourceState;
import com.enn3developer.gtnhvoice.api.server.group.IGroup;
import com.enn3developer.gtnhvoice.api.server.group.RoutingContext;

/**
 * DESIGN.md 13.3: who gets each radio frame and how. Flat to walkie holders, positional around receiving walkies
 * (D21) and Transceivers, nothing for direct hearers (D17), the capture rule (D24) and dropouts.
 */
class RadioGroupTest {

    private static final long MS = 1_000_000L;
    private static final UUID RADIO = RadioGroup.ACTIVATION;

    private final RoutingFixture fixture = new RoutingFixture();
    private double roll = 0.99;
    private long now = 1_000 * MS;
    private int proximity = 48;
    private final RadioRouting shared = new RadioRouting(
        16,
        () -> proximity,
        6,
        16,
        100 * MS,
        3,
        () -> roll,
        () -> now);
    private final RadioGroup one = new RadioGroup(1, "Radio 1", shared);

    private static RadioSnapshot.Link walkie(UUID holder, double snr) {
        return new RadioSnapshot.Link(holder, null, snr, 0, 1);
    }

    private static RadioSnapshot.Link transceiver(RadioSnapshot.Transceiver t, double snr) {
        return new RadioSnapshot.Link(null, t, snr, 0, 1);
    }

    @Test
    void namesSixteenChannelsBelowCalls() {
        assertEquals("becom:radio.1", one.getName());
        assertEquals("becom:radio.16", RadioGroup.nameOf(16));
        assertEquals(100, one.priority());
        assertTrue(one.priority() < CallGroup.PRIORITY);
        assertEquals(ActivationIds.of("BeCom", "radio"), RADIO);
    }

    @Test
    void sendsFlatToAWalkieHolderInReach() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 60, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 10))
                .build());
        fixture.route(one, alice, RADIO);
        assertEquals(List.of(bob), fixture.recipients());
        assertEquals(
            SourceState.FLAT,
            fixture.sent.get(0)
                .delivery()
                .getSourceState());
    }

    @Test
    void ignoresFramesSentWithoutPushToTalk() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 60, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 10))
                .build());
        fixture.route(one, alice, ActivationIds.DEFAULT);
        assertTrue(fixture.sent.isEmpty());
        assertTrue(
            shared.keyedSpeakers()
                .isEmpty());
    }

    @Test
    void ignoresASpeakerOnAnotherChannel() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 60, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 2)
                .link(alice, walkie(bob, 10))
                .build());
        fixture.route(one, alice, RADIO);
        assertTrue(fixture.sent.isEmpty());
    }

    @Test
    void recordsWhoIsKeyedEvenWithNobodyInReach() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        fixture.route(one, alice, RADIO);
        RadioRouting.Keyed keyed = shared.keyedSpeakers()
            .get(alice);
        assertNotNull(keyed);
        assertEquals(1, keyed.channel());
        assertEquals(now, keyed.nanos());
    }

    @Test
    void skipsAHolderWhoHearsTheSpeakerDirectly() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID near = fixture.player("near", 12, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(near, 30))
                .build());
        fixture.route(one, alice, RADIO);
        assertTrue(fixture.sent.isEmpty());
    }

    @Test
    void aShortProximityRangeLeavesTheRadioToCarryThoseBeyondIt() {
        // GTNH Voice's proximity reaches 10 blocks on this server. Bob, 12 blocks from Alice, doesn't hear her
        // directly,
        // so his walkie plays her, and so does it for Dan beside him. Eve, 9 blocks from Alice, hears her in person.
        proximity = 10;
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 12, 64, 0, 0);
        UUID dan = fixture.player("dan", 15, 64, 0, 0);
        UUID eve = fixture.player("eve", 9, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 30))
                .build());
        fixture.route(one, alice, RADIO);
        assertEquals(List.of(bob, dan), fixture.recipients());
        assertTrue(
            !fixture.recipients()
                .contains(eve));
    }

    @Test
    void playsAroundAReceivingWalkieWithinSixBlocks() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        UUID close = fixture.player("close", 105, 64, 0, 0);
        UUID far = fixture.player("far", 107, 64, 0, 0);
        UUID elsewhere = fixture.player("elsewhere", 101, 64, 0, -1);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 10))
                .build());
        fixture.route(one, alice, RADIO);
        assertEquals(List.of(bob, close), fixture.recipients());
        RoutingFixture.Sent around = fixture.sent.get(1);
        assertEquals(
            SourceState.POSITIONAL,
            around.delivery()
                .getSourceState());
        assertEquals(
            100,
            around.delivery()
                .getX(),
            1e-9);
        assertTrue(
            !fixture.recipients()
                .contains(far)
                && !fixture.recipients()
                    .contains(elsewhere));
    }

    @Test
    void aHolderHearingDirectlyStillPlaysToThoseAround() {
        // Bob is close enough to hear Alice himself, but his walkie still received her, and Carol hears it.
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 14, 64, 0, 0);
        UUID carol = fixture.player("carol", 19, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 30))
                .build());
        fixture.route(one, alice, RADIO);
        assertEquals(List.of(carol), fixture.recipients());
    }

    @Test
    void aHolderGetsTheirOwnWalkieFlatBeforeAnyoneElses() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        UUID carol = fixture.player("carol", 103, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 10))
                .link(alice, walkie(carol, 10))
                .build());
        fixture.route(one, alice, RADIO);
        assertEquals(2, fixture.sent.size());
        for (RoutingFixture.Sent s : fixture.sent) assertEquals(
            SourceState.FLAT,
            s.delivery()
                .getSourceState());
    }

    @Test
    void transceiverPlaysFromItsBlockWhenItsLoudspeakerIsOn() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID dan = fixture.player("dan", 210, 64, 0, 0);
        UUID eve = fixture.player("eve", 217, 64, 0, 0);
        RadioSnapshot.Transceiver on = new RadioSnapshot.Transceiver(0, 1L, 200.5, 64.5, 0.5, true);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, transceiver(on, 10))
                .build());
        fixture.route(one, alice, RADIO);
        assertEquals(List.of(dan), fixture.recipients());
        assertEquals(
            200.5,
            fixture.sent.get(0)
                .delivery()
                .getX(),
            1e-9);
        assertTrue(
            !fixture.recipients()
                .contains(eve));
    }

    @Test
    void transceiverWithItsLoudspeakerOffPlaysNothing() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        fixture.player("dan", 210, 64, 0, 0);
        RadioSnapshot.Transceiver off = new RadioSnapshot.Transceiver(0, 1L, 200.5, 64.5, 0.5, false);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, transceiver(off, 10))
                .build());
        fixture.route(one, alice, RADIO);
        assertTrue(fixture.sent.isEmpty());
    }

    @Test
    void walkieHoldersComeBeforeTransceivers() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 200, 64, 0, 0);
        RadioSnapshot.Transceiver t = new RadioSnapshot.Transceiver(0, 1L, 202.5, 64.5, 0.5, true);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, transceiver(t, 10))
                .link(alice, walkie(bob, 10))
                .build());
        fixture.route(one, alice, RADIO);
        assertEquals(List.of(bob), fixture.recipients());
        assertEquals(
            SourceState.FLAT,
            fixture.sent.get(0)
                .delivery()
                .getSourceState());
    }

    @Test
    void strongestSpeakerCapturesTheListener() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID cat = fixture.player("cat", 0, 64, 200, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .transmitter(cat, 1)
                .link(alice, walkie(bob, 10))
                .link(cat, walkie(bob, 12))
                .build());
        fixture.route(one, alice, RADIO);
        now += 20 * MS;
        fixture.route(one, cat, RADIO);
        // Cat is only 2 dB stronger, so Alice keeps Bob.
        assertEquals(1, fixture.sent.size());
        assertEquals(
            alice,
            fixture.sent.get(0)
                .delivery()
                .getSourceId());
        // Once Alice has been quiet for over 100 ms, Cat gets through.
        now += 101 * MS;
        fixture.route(one, cat, RADIO);
        assertEquals(
            cat,
            fixture.sent.get(1)
                .delivery()
                .getSourceId());
    }

    @Test
    void threeDecibelsStrongerTakesOverAtOnce() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID cat = fixture.player("cat", 0, 64, 200, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .transmitter(cat, 1)
                .link(alice, walkie(bob, 10))
                .link(cat, walkie(bob, 13))
                .build());
        fixture.route(one, alice, RADIO);
        now += 20 * MS;
        fixture.route(one, cat, RADIO);
        assertEquals(2, fixture.sent.size());
    }

    @Test
    void dropoutsEatFrames() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        UUID close = fixture.player("close", 103, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, new RadioSnapshot.Link(bob, null, 7, 1, 0))
                .build());
        roll = 0.5;
        fixture.route(one, alice, RADIO);
        // A dropped frame never reached Bob's walkie, so nobody around him hears it either.
        assertTrue(fixture.sent.isEmpty());
        assertTrue(
            !fixture.recipients()
                .contains(close));
    }

    @Test
    void reportsEachRadioContactOnce() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        UUID close = fixture.player("close", 103, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 10))
                .build());
        fixture.route(one, alice, RADIO);
        fixture.route(one, alice, RADIO);
        assertEquals(Set.of(new RadioRouting.Contact(alice, bob), new RadioRouting.Contact(alice, close)), contacts());
    }

    @Test
    void aListenerWhoseVoiceIsntConnectedYetIsNoContact() {
        // GTNH Voice drops a send to a session with no UDP address yet, so Bob and Dave hear nothing: flat on Bob's
        // own walkie, positional for Dave by Carol's. Close and Carol do hear Alice.
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.playerWithoutUdpAddress("bob", 100, 64, 0, 0);
        UUID close = fixture.player("close", 103, 64, 0, 0);
        UUID carol = fixture.player("carol", 200, 64, 0, 0);
        fixture.playerWithoutUdpAddress("dave", 203, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 10))
                .link(alice, walkie(carol, 10))
                .build());
        fixture.route(one, alice, RADIO);
        assertEquals(Set.of(close, carol), new HashSet<>(fixture.recipients()));
        assertEquals(
            Set.of(new RadioRouting.Contact(alice, close), new RadioRouting.Contact(alice, carol)),
            contacts());
    }

    @Test
    void aListenerTheCallAlreadyServedIsNoRadioContact() {
        // Alice and Bob are in a call and share channel 1. GTNH Voice sends each frame to a player once, from the
        // highest group: Bob hears Alice through the call, so the radio's send to him is dropped and isn't a contact.
        // Close, by Bob's walkie and not in the call, hears her on the radio.
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        UUID close = fixture.player("close", 103, 64, 0, 0);
        CallGroup call = new CallGroup("Call", 16, () -> proximity, () -> roll);
        call.publish(
            CallSnapshot.builder()
                .route(alice, new CallSnapshot.Route(bob, 0, 1.0 / 3))
                .build());
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 10))
                .build());
        fixture.route(alice, RADIO, one, call);
        assertEquals(List.of(bob, close), fixture.recipients());
        assertSame(
            call,
            fixture.sent.get(0)
                .delivery()
                .getGroup());
        assertEquals(Set.of(new RadioRouting.Contact(alice, close)), contacts());
    }

    @Test
    void aListenerAnyHigherGroupAlreadyServedIsNoRadioContact() {
        // Another addon's group above radio sends Bob this frame first. Carol, whom it leaves alone, is a contact.
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        UUID carol = fixture.player("carol", 200, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 10))
                .link(alice, walkie(carol, 10))
                .build());
        fixture.route(alice, RADIO, one, above(bob));
        assertEquals(List.of(bob, carol), fixture.recipients());
        assertEquals(Set.of(new RadioRouting.Contact(alice, carol)), contacts());
    }

    /** A group just above radio that sends every frame flat to {@code listener}, as another addon's might. */
    private static IGroup above(UUID listener) {
        return new IGroup() {

            @Override
            public String getName() {
                return "test:above";
            }

            @Override
            public String getDisplayName() {
                return "Above";
            }

            @Override
            public int priority() {
                return RadioGroup.PRIORITY + 1;
            }

            @Override
            public void route(RoutingContext context) {
                context.sendTo(
                    context.getSessionsByPlayerUuid()
                        .get(listener),
                    SourceState.FLAT,
                    0,
                    0,
                    0);
            }
        };
    }

    /** Every radio contact noticed so far, drained. */
    private Set<RadioRouting.Contact> contacts() {
        Set<RadioRouting.Contact> contacts = new HashSet<>();
        for (RadioRouting.Contact c; (c = shared.pollContact()) != null;) contacts.add(c);
        return contacts;
    }

    @Test
    void directHearingIsNoContact() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID near = fixture.player("near", 5, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(near, 30))
                .build());
        fixture.route(one, alice, RADIO);
        assertNull(shared.pollContact());
    }

    @Test
    void forgetsAPlayerWhoseSessionEnds() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 10))
                .build());
        fixture.route(one, alice, RADIO);
        assertEquals(1, shared.chainCount());
        one.onPlayerRemoved(bob);
        assertEquals(0, shared.chainCount());
        one.onPlayerRemoved(alice);
        assertTrue(
            shared.keyedSpeakers()
                .isEmpty());
    }

    @Test
    void publishingDropsChainsOfPairsThatAreGone() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 10))
                .build());
        fixture.route(one, alice, RADIO);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .build());
        assertEquals(0, shared.chainCount());
    }

    @Test
    void aListenerHearsOneSpeakerWhicheverDeviceTheirAudioComesThrough() {
        // D24 is per listener: Bob's own walkie holds Alice, Dan's walkie 4 blocks away is taken by Cat. Bob must not
        // hear Cat through Dan's walkie while Alice has him.
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID cat = fixture.player("cat", 300, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        UUID dan = fixture.player("dan", 104, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 20))
                .link(alice, walkie(dan, 7))
                .transmitter(cat, 1)
                .link(cat, walkie(dan, 20))
                .link(cat, walkie(bob, 7))
                .build());
        fixture.route(one, alice, RADIO);
        assertTrue(
            fixture.recipients()
                .contains(bob));
        now += 10 * MS;
        fixture.sent.clear();
        fixture.route(one, cat, RADIO);
        assertTrue(
            !fixture.recipients()
                .contains(bob),
            "Bob hears Alice, at 20 dB on his own walkie; Cat isn't 3 dB stronger anywhere he is");
        // Dan heard Alice through Bob's walkie at 20 dB too, so Cat's 20 on his own walkie doesn't take him either.
        assertTrue(
            !fixture.recipients()
                .contains(dan));
    }

    @Test
    void aStrongerSpeakerTakesAListenerThroughANeighboursWalkie() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID cat = fixture.player("cat", 300, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        UUID dan = fixture.player("dan", 104, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 7))
                .transmitter(cat, 1)
                .link(cat, walkie(dan, 20))
                .build());
        fixture.route(one, alice, RADIO);
        now += 10 * MS;
        fixture.sent.clear();
        fixture.route(one, cat, RADIO);
        // Cat reaches Bob only through Dan's walkie, 13 dB stronger than Alice on his own: he switches to Cat.
        assertEquals(List.of(dan, bob), fixture.recipients());
        now += 10 * MS;
        fixture.sent.clear();
        fixture.route(one, alice, RADIO);
        assertTrue(fixture.sent.isEmpty(), "Bob's own walkie still has Alice, but Bob is Cat's now");
    }

    @Test
    void tellsWhichSpeakerEachDeviceHolds() {
        // The server plays the squelch tail only where a device was holding the speaker who let go.
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 60, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 10))
                .build());
        assertTrue(!shared.deviceHeldBy(bob, 1, alice));
        fixture.route(one, alice, RADIO);
        assertTrue(shared.deviceHeldBy(bob, 1, alice));
        assertTrue(!shared.deviceHeldBy(bob, 2, alice));
    }

    @Test
    void reportsAContactAgainAfterAMinute() {
        // A contact made while its quest was still locked mustn't be used up for good.
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 60, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 10))
                .build());
        fixture.route(one, alice, RADIO);
        assertNotNull(shared.pollContact());
        now += 1_000 * MS;
        fixture.route(one, alice, RADIO);
        assertNull(shared.pollContact());
        now += 61_000 * MS;
        fixture.route(one, alice, RADIO);
        assertNotNull(shared.pollContact());
    }

    @Test
    void tellsWhichSpeakerEachListenerHears() {
        // Dan's walkie holds Cat, but Dan and Bob hear Alice: Cat's squelch tail is for neither of them.
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID cat = fixture.player("cat", 300, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        UUID dan = fixture.player("dan", 104, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 20))
                .link(alice, walkie(dan, 7))
                .transmitter(cat, 1)
                .link(cat, walkie(dan, 20))
                .build());
        fixture.route(one, alice, RADIO);
        now += 10 * MS;
        fixture.route(one, cat, RADIO);
        assertTrue(shared.deviceHeldBy(dan, 1, cat));
        assertTrue(!shared.listenerHeldBy(dan, 1, cat) && !shared.listenerHeldBy(bob, 1, cat));
        assertTrue(shared.listenerHeldBy(dan, 1, alice) && shared.listenerHeldBy(bob, 1, alice));
    }

    @Test
    void aBurstOfDroppedFramesDoesntHandAListenerToAnotherSpeaker() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID cat = fixture.player("cat", 300, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        UUID dan = fixture.player("dan", 104, 64, 0, 0);
        RadioSnapshot.Link catToDan = walkie(dan, 7);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, new RadioSnapshot.Link(bob, null, 7, 0, 0))
                .transmitter(cat, 1)
                .link(cat, catToDan)
                .build());
        fixture.route(one, alice, RADIO);
        fixture.route(one, cat, RADIO);
        // A burst on Alice's link to Bob: his walkie still holds her, but her frames don't get through.
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, new RadioSnapshot.Link(bob, null, 7, 1, 0))
                .transmitter(cat, 1)
                .link(cat, catToDan)
                .build());
        for (int i = 0; i < 6; i++) {
            now += 20 * MS;
            fixture.sent.clear();
            fixture.route(one, alice, RADIO);
            fixture.sent.clear();
            fixture.route(one, cat, RADIO);
            assertTrue(
                !fixture.recipients()
                    .contains(bob),
                "frame " + i + ": Bob is still Alice's, mid-burst");
        }
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, new RadioSnapshot.Link(bob, null, 7, 0, 1))
                .transmitter(cat, 1)
                .link(cat, catToDan)
                .build());
        now += 20 * MS;
        fixture.sent.clear();
        fixture.route(one, alice, RADIO);
        assertTrue(
            fixture.recipients()
                .contains(bob),
            "the burst ends and Bob hears Alice again");
    }

    @Test
    void someoneHearingTheSpeakerInPersonDoesntHearASecondOneOnTheRadio() {
        // Bob stands 12 blocks from Alice and hears her directly (D17); Cat reaches Dan's walkie, 5 blocks from Bob.
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID cat = fixture.player("cat", 300, 64, 0, 0);
        UUID bob = fixture.player("bob", 12, 64, 0, 0);
        UUID dan = fixture.player("dan", 17, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 20))
                .link(alice, walkie(dan, 7))
                .transmitter(cat, 1)
                .link(cat, walkie(dan, 20))
                .build());
        fixture.route(one, alice, RADIO);
        assertTrue(
            !fixture.recipients()
                .contains(bob),
            "Bob hears Alice in person, not on the radio");
        now += 10 * MS;
        fixture.sent.clear();
        fixture.route(one, cat, RADIO);
        assertTrue(
            !fixture.recipients()
                .contains(bob),
            "and Cat, no stronger than Alice on Bob's walkie, doesn't take him");
    }

    @Test
    void aListenerGetsTheLevelOfTheDeviceTheyActuallyHear() {
        // Bob's own walkie holds Alice, so Cat reaches him only through Dan's walkie: Cat's level for Bob is that
        // walkie's (DESIGN.md 13.5).
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID cat = fixture.player("cat", 300, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        UUID dan = fixture.player("dan", 104, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 10))
                .transmitter(cat, 1)
                .link(cat, walkie(bob, 7))
                .link(cat, walkie(dan, 20))
                .build());
        fixture.route(one, alice, RADIO);
        now += 10 * MS;
        fixture.route(one, cat, RADIO);
        assertEquals(
            SignalLevel.MEDIUM,
            shared
                .heardThrough(cat, 1, List.of(carrier(bob, 7, SignalLevel.LOW), carrier(dan, 20, SignalLevel.MEDIUM))));
        assertEquals(
            SignalLevel.LOW,
            shared.heardThrough(
                alice,
                1,
                List.of(carrier(bob, 10, SignalLevel.LOW), carrier(dan, 0, SignalLevel.MEDIUM))));
    }

    @Test
    void anExpiredHoldDoesntCountWhenChoosingTheLevelForTheNextSpeaker() {
        // Cat spoke to Bob's walkie half a second ago; Alice now starts an over. Her first publish comes before her
        // first frame reaches route(), so Bob's walkie still records Cat, but that hold ended 400 ms ago.
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID cat = fixture.player("cat", 300, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(cat, 1)
                .link(cat, walkie(bob, 20))
                .build());
        fixture.route(one, cat, RADIO);
        now += 500 * MS;
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 7))
                .build());
        assertEquals(SignalLevel.LOW, shared.heardThrough(alice, 1, List.of(carrier(bob, 7, SignalLevel.LOW))));
        fixture.sent.clear();
        fixture.route(one, alice, RADIO);
        assertTrue(
            fixture.recipients()
                .contains(bob));
    }

    @Test
    void aStrongerSpeakerTakingADeviceGetsItsLevel() {
        // Cat holds Bob's walkie at 7 dB when Alice keys up at 20: her first frame takes the walkie (D24's margin), so
        // her level for Bob is the walkie's, though its hold on Cat hasn't run out.
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID cat = fixture.player("cat", 300, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(cat, 1)
                .link(cat, walkie(bob, 7))
                .build());
        fixture.route(one, cat, RADIO);
        now += 10 * MS;
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(cat, 1)
                .link(cat, walkie(bob, 7))
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 20))
                .build());
        assertEquals(SignalLevel.MEDIUM, shared.heardThrough(alice, 1, List.of(carrier(bob, 20, SignalLevel.MEDIUM))));
        fixture.sent.clear();
        fixture.route(one, alice, RADIO);
        assertTrue(
            fixture.recipients()
                .contains(bob));
    }

    @Test
    void aSpeakerWaitingOutAHoldGetsTheLevelTheyWillBeHeardAt() {
        // Alice keys up 20 ms after Cat's last frame, no stronger: Bob's walkie takes her once Cat's 100 ms hold runs
        // out, before the next publish, so her level for Bob is already the walkie's, not the client's fallback.
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID cat = fixture.player("cat", 300, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(cat, 1)
                .link(cat, walkie(bob, 10))
                .build());
        fixture.route(one, cat, RADIO);
        now += 20 * MS;
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 8))
                .build());
        assertEquals(SignalLevel.LOW, shared.heardThrough(alice, 1, List.of(carrier(bob, 8, SignalLevel.LOW))));
        now += 100 * MS;
        fixture.sent.clear();
        fixture.route(one, alice, RADIO);
        assertTrue(
            fixture.recipients()
                .contains(bob));
        // With no device at all, there's no level.
        assertEquals(SignalLevel.NONE, shared.heardThrough(alice, 1, List.of()));
    }

    @Test
    void theLevelComesFromTheFirstDeviceThatWouldTakeTheSpeaker() {
        // Cat holds both walkies. Alice is too weak to take Bob's (8 against 20) but takes Dan's by the margin (20
        // against 7), so she's heard through Dan's: its level, not the first device's.
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID cat = fixture.player("cat", 300, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        UUID dan = fixture.player("dan", 104, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(cat, 1)
                .link(cat, walkie(bob, 20))
                .link(cat, walkie(dan, 7))
                .build());
        fixture.route(one, cat, RADIO);
        now += 10 * MS;
        assertEquals(
            SignalLevel.MEDIUM,
            shared.heardThrough(
                alice,
                1,
                List.of(carrier(bob, 8, SignalLevel.LOW), carrier(dan, 20, SignalLevel.MEDIUM))));
    }

    private static RadioRouting.Carrier carrier(UUID device, double snr, SignalLevel level) {
        return new RadioRouting.Carrier(device, snr, level);
    }

    @Test
    void aPlayerWithoutVoiceIsntHeldByAnyone() {
        // Pat stands by Bob's walkie but has no voice session: they hear nothing, so nothing holds them.
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 100, 64, 0, 0);
        UUID pat = fixture.playerWithoutVoice("pat", 104, 64, 0, 0);
        shared.publish(
            RadioSnapshot.builder()
                .transmitter(alice, 1)
                .link(alice, walkie(bob, 10))
                .link(alice, walkie(pat, 10))
                .build());
        fixture.route(one, alice, RADIO);
        assertTrue(!shared.listenerHeldBy(pat, 1, alice));
        assertTrue(shared.listenerHeldBy(bob, 1, alice));
    }
}
