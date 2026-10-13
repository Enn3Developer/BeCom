package com.enn3developer.becom.voice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A transmission worked out once is published as it is until its radio is worked out again. */
class RadioSnapshotTest {

    private static final UUID ALICE = new UUID(1, 1), BOB = new UUID(2, 2), CAROL = new UUID(3, 3);
    private static final RadioSnapshot.Transceiver TRANSCEIVER = new RadioSnapshot.Transceiver(
        0,
        1L,
        0.5,
        64.5,
        0.5,
        true);

    @Test
    void aTransmissionHoldsWalkiesThenTransceiversEachInTheirOrder() {
        RadioSnapshot.Link bob = new RadioSnapshot.Link(BOB, null, 10, 0, 1);
        RadioSnapshot.Link at = new RadioSnapshot.Link(null, TRANSCEIVER, 12, 0, 1);
        RadioSnapshot.Link carol = new RadioSnapshot.Link(CAROL, null, 8, 0, 1);
        RadioSnapshot.Transmission tx = RadioSnapshot.transmission(3, List.of(bob, at, carol));
        assertEquals(3, tx.channel());
        assertArrayEquals(new RadioSnapshot.Link[] { bob, carol }, tx.walkies());
        assertArrayEquals(new RadioSnapshot.Link[] { at }, tx.transceivers());
    }

    @Test
    void aTransmissionGivenWholeIsPublishedAsItIs() {
        RadioSnapshot.Transmission tx = RadioSnapshot
            .transmission(1, List.of(new RadioSnapshot.Link(BOB, null, 10, 0, 1)));
        RadioSnapshot snapshot = RadioSnapshot.builder()
            .transmission(ALICE, tx)
            .transmitter(CAROL, 2)
            .build();
        assertSame(tx, snapshot.transmission(ALICE));
        assertEquals(2, snapshot.speakers());
        assertEquals(
            2,
            snapshot.transmission(CAROL)
                .channel());
    }
}
