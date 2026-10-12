package com.enn3developer.becom.logic.radio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** D24: each listener hears the strongest transmitter, held for 100 ms, taken over at 3 dB stronger. */
class CaptureTest {

    private static final long MS = 1_000_000L;
    private final Capture<String> capture = new Capture<>(100 * MS, 3);

    @Test
    void firstSpeakerIsHeard() {
        assertTrue(capture.admit("ann", 1, "bob", 10, 0));
    }

    @Test
    void holderKeepsTheChannelAgainstASimilarSignal() {
        assertTrue(capture.admit("ann", 1, "bob", 10, 0));
        assertFalse(capture.admit("ann", 1, "cat", 12.9, 20 * MS));
        assertTrue(capture.admit("ann", 1, "bob", 10, 40 * MS));
    }

    @Test
    void threeDecibelsStrongerTakesOver() {
        assertTrue(capture.admit("ann", 1, "bob", 10, 0));
        assertTrue(capture.admit("ann", 1, "cat", 13, 20 * MS));
        assertFalse(capture.admit("ann", 1, "bob", 10, 40 * MS));
    }

    @Test
    void channelFreesUpAfterTheHold() {
        assertTrue(capture.admit("ann", 1, "bob", 20, 0));
        assertFalse(capture.admit("ann", 1, "cat", 8, 100 * MS));
        assertTrue(capture.admit("ann", 1, "cat", 8, 101 * MS));
    }

    @Test
    void eachListenerAndChannelIsSeparate() {
        assertTrue(capture.admit("ann", 1, "bob", 20, 0));
        assertTrue(capture.admit("dan", 1, "cat", 8, 0));
        assertTrue(capture.admit("ann", 2, "cat", 8, 0));
    }

    @Test
    void forgettingAListenerClearsEveryChannel() {
        capture.admit("ann", 1, "bob", 20, 0);
        capture.admit("ann", 2, "bob", 20, 0);
        capture.admit("dan", 1, "bob", 20, 0);
        capture.forgetListener("ann");
        assertEquals(1, capture.size());
        assertTrue(capture.admit("ann", 1, "cat", 1, MS));
    }

    @Test
    void sweepDropsLongGoneHolds() {
        capture.admit("ann", 1, "bob", 20, 0);
        capture.admit("dan", 1, "bob", 20, 9_000 * MS);
        capture.sweep(10_001 * MS);
        assertEquals(1, capture.size());
    }

    @Test
    void oneFrameAlongSeveralPathsCountsItsStrongest() {
        Capture<String> capture = new Capture<>(100, 3);
        assertTrue(capture.admit("bob", 1, "alice", 20, 0));
        assertTrue(capture.admit("bob", 1, "alice", 7, 0));
        // Held at 20, not 7: Cat at 21 doesn't take over.
        assertFalse(capture.admit("bob", 1, "cat", 21, 10));
    }

    @Test
    void aHoldSetNearZeroIsntSweptBeforeTheServerReadsIt() {
        Capture<String> capture = new Capture<>(0, 3);
        capture.admit("bob", 1, "alice", 10, 0);
        capture.sweep(1_000_000_000L);
        assertEquals("alice", capture.holder("bob", 1));
    }
}
