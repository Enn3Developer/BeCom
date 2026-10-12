package com.enn3developer.becom.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.enn3developer.gtnhvoice.api.server.ActivationIds;
import com.enn3developer.gtnhvoice.api.server.SourceState;

/**
 * DESIGN.md 13.3: who gets each call frame, flat or from a Videophone's loudspeaker, and who's skipped: direct hearing
 * (D17), dropouts.
 */
class CallGroupTest {

    private static final int VIDEOPHONE = 123_456;

    private final RoutingFixture fixture = new RoutingFixture();
    private double roll = 0.99;
    private final CallGroup group = new CallGroup("Call", 16, () -> roll);

    @Test
    void sendsFlatToTheOtherParticipant() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 500, 64, 0, 0);
        group.publish(
            CallSnapshot.builder()
                .route(alice, new CallSnapshot.Route(bob, 0, 1.0 / 3))
                .route(bob, new CallSnapshot.Route(alice, 0, 1.0 / 3))
                .build());
        fixture.route(group, alice, ActivationIds.DEFAULT);
        assertEquals(List.of(bob), fixture.recipients());
        assertEquals(
            SourceState.FLAT,
            fixture.sent.get(0)
                .packet()
                .getSourceState());
        assertEquals(
            alice,
            fixture.sent.get(0)
                .packet()
                .getSourceId());
    }

    @Test
    void routesNoMatterWhichActivationTheFrameCarries() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 500, 64, 0, 0);
        group.publish(
            CallSnapshot.builder()
                .route(alice, new CallSnapshot.Route(bob, 0, 1.0 / 3))
                .build());
        fixture.route(group, alice, ActivationIds.of("BeCom", "radio"));
        assertEquals(List.of(bob), fixture.recipients());
    }

    @Test
    void skipsListenersWhoHearTheSpeakerDirectly() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID near = fixture.player("near", 10, 64, 12, 0);
        UUID edge = fixture.player("edge", 16, 64, 0, 0);
        UUID far = fixture.player("far", 16.01, 64, 0, 0);
        UUID otherWorld = fixture.player("nether", 1, 64, 0, -1);
        CallSnapshot.Builder snapshot = CallSnapshot.builder();
        for (UUID listener : List.of(near, edge, far, otherWorld)) {
            snapshot.route(alice, new CallSnapshot.Route(listener, 0, 1.0 / 3));
        }
        group.publish(snapshot.build());
        fixture.route(group, alice, ActivationIds.DEFAULT);
        // Exactly 16 blocks still hears directly, as GTNH Voice keeps exactly-at-cutoff.
        assertEquals(List.of(far, otherWorld), fixture.recipients());
    }

    @Test
    void speakerWithNoRoutesSendsNothing() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        fixture.player("bob", 100, 64, 0, 0);
        fixture.route(group, alice, ActivationIds.DEFAULT);
        assertTrue(fixture.sent.isEmpty());
    }

    @Test
    void dropoutsFollowTheBurstChain() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 500, 64, 0, 0);
        group.publish(
            CallSnapshot.builder()
                .route(alice, new CallSnapshot.Route(bob, 0.2, 0.5))
                .build());
        // Rolls under the enter chance start a burst; rolls over the leave chance keep it going.
        roll = 0.1;
        fixture.route(group, alice, ActivationIds.DEFAULT);
        roll = 0.9;
        fixture.route(group, alice, ActivationIds.DEFAULT);
        assertTrue(fixture.sent.isEmpty());
        roll = 0.4;
        fixture.route(group, alice, ActivationIds.DEFAULT);
        assertEquals(1, fixture.sent.size());
    }

    @Test
    void missingSessionsAreSkippedAndChainsAreForgotten() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 500, 64, 0, 0);
        UUID gone = UUID.randomUUID();
        group.publish(
            CallSnapshot.builder()
                .route(alice, new CallSnapshot.Route(gone, 0, 1.0 / 3))
                .route(alice, new CallSnapshot.Route(bob, 0, 1.0 / 3))
                .build());
        fixture.route(group, alice, ActivationIds.DEFAULT);
        assertEquals(List.of(bob), fixture.recipients());
        assertEquals(1, group.chainCount());
        group.onPlayerRemoved(bob);
        assertEquals(0, group.chainCount());
        fixture.route(group, alice, ActivationIds.DEFAULT);
        assertEquals(1, group.chainCount());
        group.publish(CallSnapshot.EMPTY);
        assertEquals(0, group.chainCount(), "pairs gone from the snapshot lose their chains");
    }

    @Test
    void identityAndPriority() {
        assertEquals("becom:call", group.getName());
        assertEquals("Call", group.getDisplayName());
        assertEquals(200, group.priority());
    }

    private static CallSnapshot.Loudspeaker loudspeaker(double x, double enterBad, double leaveBad) {
        return new CallSnapshot.Loudspeaker(VIDEOPHONE, 0, x, 64.5, 0.5, 16, enterBad, leaveBad);
    }

    @Test
    void aVideophonePlaysTheCallFromItsBlockToEveryoneAround() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID room = fixture.player("room", 502, 64, 0, 0);
        UUID passer = fixture.player("passer", 510, 64, 5, 0);
        UUID far = fixture.player("far", 520, 64, 0, 0);
        UUID nether = fixture.player("nether", 500, 64, 0, -1);
        group.publish(
            CallSnapshot.builder()
                .loudspeaker(alice, loudspeaker(500.5, 0, 1.0 / 3))
                .build());
        fixture.route(group, alice, ActivationIds.DEFAULT);
        assertEquals(Set.of(room, passer), new HashSet<>(fixture.recipients()));
        for (RoutingFixture.Sent sent : fixture.sent) {
            assertEquals(
                SourceState.POSITIONAL,
                sent.packet()
                    .getSourceState());
            assertEquals(
                500.5,
                sent.packet()
                    .getX(),
                1e-9);
        }
        assertTrue(
            !fixture.recipients()
                .contains(far)
                && !fixture.recipients()
                    .contains(nether));
    }

    @Test
    void someoneOnTheirOwnPhoneHearsItFlatAndNotFromALoudspeakerToo() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        UUID bob = fixture.player("bob", 505, 64, 0, 0);
        group.publish(
            CallSnapshot.builder()
                .route(alice, new CallSnapshot.Route(bob, 0, 1.0 / 3))
                .loudspeaker(alice, loudspeaker(500.5, 0, 1.0 / 3))
                .build());
        fixture.route(group, alice, ActivationIds.DEFAULT);
        assertEquals(List.of(bob), fixture.recipients());
        assertEquals(
            SourceState.FLAT,
            fixture.sent.get(0)
                .packet()
                .getSourceState());
    }

    @Test
    void aLoudspeakerSkipsTheSpeakerAndWhoeverHearsThemDirectly() {
        // Alice stands 10 blocks from a Videophone in her own call: she and those near her hear each other directly.
        UUID alice = fixture.player("alice", 510, 64, 0, 0);
        UUID near = fixture.player("near", 505, 64, 0, 0);
        UUID beyond = fixture.player("beyond", 488, 64, 0, 0);
        group.publish(
            CallSnapshot.builder()
                .loudspeaker(alice, loudspeaker(500.5, 0, 1.0 / 3))
                .build());
        fixture.route(group, alice, ActivationIds.DEFAULT);
        assertEquals(List.of(beyond), fixture.recipients());
        assertTrue(
            !fixture.recipients()
                .contains(near));
    }

    @Test
    void everyoneAroundALoudspeakerHearsTheSameDropouts() {
        UUID alice = fixture.player("alice", 0, 64, 0, 0);
        fixture.player("room", 502, 64, 0, 0);
        fixture.player("passer", 510, 64, 0, 0);
        group.publish(
            CallSnapshot.builder()
                .loudspeaker(alice, loudspeaker(500.5, 0.2, 0.5))
                .build());
        roll = 0.1;
        fixture.route(group, alice, ActivationIds.DEFAULT);
        roll = 0.9;
        fixture.route(group, alice, ActivationIds.DEFAULT);
        assertTrue(fixture.sent.isEmpty());
        assertEquals(1, group.chainCount(), "one chain for the phone, not one per player around it");
        roll = 0.4;
        fixture.route(group, alice, ActivationIds.DEFAULT);
        assertEquals(2, fixture.sent.size());
        group.publish(CallSnapshot.EMPTY);
        assertEquals(0, group.chainCount(), "a Videophone gone from the snapshot loses its chain");
    }
}
