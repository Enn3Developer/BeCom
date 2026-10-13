package com.enn3developer.becom.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A position that isn't a number is no position, and out of every range. */
class PositionsTest {

    private static final double NAN = Double.NaN, INF = Double.POSITIVE_INFINITY;

    @Test
    void onlyFiniteCoordinatesAreAPosition() {
        assertTrue(Positions.finite(0, 64, -30_000_000));
        assertFalse(Positions.finite(NAN, 64, 0));
        assertFalse(Positions.finite(0, NAN, 0));
        assertFalse(Positions.finite(0, 64, -INF));
    }

    @Test
    void notANumberAndInfinityAreOutOfRange() {
        assertTrue(Positions.within(3, 4, 0, 5), "the range itself is in");
        assertFalse(Positions.within(3, 4, 0.01, 5));
        assertFalse(Positions.within(NAN, 0, 0, 5));
        assertFalse(Positions.within(0, INF, 0, 5));
        assertFalse(Positions.within(INF - INF, 0, 0, INF), "even against an infinite range");
        assertFalse(Positions.withinSq(NAN, 16));
        assertTrue(Positions.withinSq(256, 16));
    }
}
