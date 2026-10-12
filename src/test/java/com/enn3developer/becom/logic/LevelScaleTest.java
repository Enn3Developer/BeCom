package com.enn3developer.becom.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LevelScaleTest {

    private final LevelScale scale = Tunables.defaults()
        .levelScale();

    @Test
    void thresholdsAreDesignsSixTwelveEighteenTwentyFour() {
        assertEquals(SignalLevel.NONE, scale.levelOf(5.99));
        assertEquals(SignalLevel.LOW, scale.levelOf(6));
        assertEquals(SignalLevel.LOW, scale.levelOf(11.99));
        assertEquals(SignalLevel.MEDIUM, scale.levelOf(12));
        assertEquals(SignalLevel.HIGH, scale.levelOf(18));
        assertEquals(SignalLevel.MAX, scale.levelOf(24));
        assertEquals(SignalLevel.MAX, scale.levelOf(Double.POSITIVE_INFINITY));
        assertEquals(SignalLevel.NONE, scale.levelOf(Double.NEGATIVE_INFINITY));
    }

    @Test
    void entersAtThresholdLeavesTwoDecibelsBelow() {
        SignalLevel level = scale.next(SignalLevel.NONE, 12);
        assertEquals(SignalLevel.MEDIUM, level);
        // Within 2 dB below the Medium threshold it holds.
        assertEquals(SignalLevel.MEDIUM, scale.next(level, 11));
        assertEquals(SignalLevel.MEDIUM, scale.next(level, 10));
        // Past 2 dB below, it falls to whatever the SNR reaches.
        assertEquals(SignalLevel.LOW, scale.next(level, 9.99));
        // Coming back up, it needs the full threshold again.
        assertEquals(SignalLevel.LOW, scale.next(SignalLevel.LOW, 11.5));
        assertEquals(SignalLevel.MEDIUM, scale.next(SignalLevel.LOW, 12));
    }

    @Test
    void fallsSeveralLevelsAtOnceWhenTheSignalCollapses() {
        assertEquals(SignalLevel.NONE, scale.next(SignalLevel.MAX, 0));
        assertEquals(SignalLevel.LOW, scale.next(SignalLevel.MAX, 7));
        // Holding near the Max threshold keeps Max even though the raw level is High.
        assertEquals(SignalLevel.MAX, scale.next(SignalLevel.MAX, 22.5));
    }

    @Test
    void lowHoldsDownToFourDecibels() {
        assertEquals(SignalLevel.LOW, scale.next(SignalLevel.LOW, 4));
        assertEquals(SignalLevel.NONE, scale.next(SignalLevel.LOW, 3.99));
    }

    @Test
    void rejectsThresholdsThatDontRise() {
        assertThrows(IllegalArgumentException.class, () -> new LevelScale(6, 6, 18, 24, 2));
        assertThrows(IllegalArgumentException.class, () -> new LevelScale(6, 12, 18, 24, -1));
    }
}
