package com.enn3developer.becom.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DropTimerTest {

    @Test
    void dropsOnlyAfterMoreThanTheGraceBelowThreshold() {
        DropTimer timer = new DropTimer(6, 3000);
        assertFalse(timer.update(5, 1000));
        assertTrue(timer.isBelow());
        assertFalse(timer.update(5, 4000));
        assertTrue(timer.update(5, 4001));
    }

    @Test
    void climbingBackResetsTheClock() {
        DropTimer timer = new DropTimer(6, 3000);
        assertFalse(timer.update(5, 0));
        assertFalse(timer.update(6, 2500));
        assertFalse(timer.isBelow());
        assertFalse(timer.update(5, 3000));
        assertFalse(timer.update(5, 6000));
        assertTrue(timer.update(5, 6001));
    }

    @Test
    void aVanishedPathCountsAsBelow() {
        DropTimer timer = new DropTimer(6, 3000);
        assertFalse(timer.update(Double.NEGATIVE_INFINITY, 0));
        assertTrue(timer.update(Double.NEGATIVE_INFINITY, 3001));
    }

    @Test
    void worksOnAClockThatReadsNegative() {
        // System.nanoTime may be negative by its contract.
        DropTimer timer = new DropTimer(6, 3000);
        assertFalse(timer.update(0, -10_000));
        assertFalse(timer.update(0, -7_000));
        assertTrue(timer.update(0, -6_999));
    }
}
