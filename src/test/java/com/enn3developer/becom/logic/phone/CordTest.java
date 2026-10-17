package com.enn3developer.becom.logic.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The cord reaches 1.2 blocks from the phone's block to the player's body. */
class CordTest {

    private static final double REACH = 1.2;

    @Test
    void phoneOnADeskReachesAStepBack() {
        // Phone on a desk at y 65; the player stands on the floor at y 64, a block back from the desk's side.
        assertTrue(Cord.within(0, 65, 0, 2.3, 64, 0.5, REACH));
        assertEquals(1.0, Cord.gap(0, 65, 0, 2.3, 64, 0.5), 1e-9);
    }

    @Test
    void phoneAtHeadHeightIsReachable() {
        assertTrue(Cord.within(0, 66, 0, 1.4, 64, 0.5, REACH));
    }

    @Test
    void walkingAwayPullsTheCord() {
        assertFalse(Cord.within(0, 64, 0, 3.2, 64, 0.5, REACH));
    }

    @Test
    void aPhoneFarAboveIsOutOfReach() {
        // Two blocks above the player's head.
        assertFalse(Cord.within(0, 68, 0, 0.5, 64, 0.5, REACH));
    }

    @Test
    void standingOnThePhoneIsNoGap() {
        assertEquals(0, Cord.gap(0, 63, 0, 0.5, 64, 0.5), 1e-9);
    }
}
