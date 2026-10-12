package com.enn3developer.becom.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.enn3developer.gtnhvoice.api.server.ActivationIds;
import com.enn3developer.gtnhvoice.api.server.SourceState;

/** DESIGN.md 13.3: who gets each call frame, flat, and who's skipped: direct hearing (D17), dropouts. */
class CallGroupTest {

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
}
