package com.enn3developer.becom.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.enn3developer.gtnhvoice.api.server.ActivationIds;
import com.enn3developer.gtnhvoice.api.server.SourceState;

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
    private final RadioRouting shared = new RadioRouting(16, 6, 16, 100 * MS, 3, () -> roll, () -> now);
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
                .packet()
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
            around.packet()
                .getSourceState());
        assertEquals(
            100,
            around.packet()
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
            s.packet()
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
                .packet()
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
                .packet()
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
                .packet()
                .getSourceId());
        // Once Alice has been quiet for over 100 ms, Cat gets through.
        now += 101 * MS;
        fixture.route(one, cat, RADIO);
        assertEquals(
            cat,
            fixture.sent.get(1)
                .packet()
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
        Set<RadioRouting.Contact> contacts = new HashSet<>();
        for (RadioRouting.Contact c; (c = shared.pollContact()) != null;) contacts.add(c);
        assertEquals(Set.of(new RadioRouting.Contact(alice, bob), new RadioRouting.Contact(alice, close)), contacts);
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
}
