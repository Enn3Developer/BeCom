package com.enn3developer.becom.logic.phone;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BatteryUseTest {

    @Test
    void sharesAddUpByUse() {
        BatteryUse use = BatteryUse.empty(3);
        use.add(3, BatteryUse.Use.STANDBY, 300);
        use.add(3, BatteryUse.Use.CALLS, 100);
        int[] shares = use.shares();
        assertEquals(75, shares[BatteryUse.Use.STANDBY.ordinal()]);
        assertEquals(25, shares[BatteryUse.Use.CALLS.ordinal()]);
        assertEquals(0, shares[BatteryUse.Use.VIDEO.ordinal()]);
    }

    @Test
    void aNewDayStartsOver() {
        BatteryUse use = BatteryUse.empty(3);
        use.add(3, BatteryUse.Use.CALLS, 100);
        use.add(4, BatteryUse.Use.STANDBY, 10);
        assertEquals(4, use.day());
        assertEquals(0, use.eu()[BatteryUse.Use.CALLS.ordinal()]);
        assertEquals(100, use.shares()[BatteryUse.Use.STANDBY.ordinal()]);
    }

    @Test
    void nothingDrawnHasNoShares() {
        assertArrayEquals(
            new int[BatteryUse.Use.values().length],
            BatteryUse.empty(0)
                .shares());
    }

    @Test
    void savedUsesFromANewerVersionAreCutToTheKnownOnes() {
        BatteryUse use = new BatteryUse(1, new long[] { 1, 2, 3, 4, 5, 6, 7 });
        assertEquals(BatteryUse.Use.values().length, use.eu().length);
    }

    @Test
    void minutesLeftAtADrain() {
        // 1.2M EU at 8 EU/t: 150,000 ticks, 125 minutes.
        assertEquals(125, BatteryUse.minutesLeft(1_200_000, 8));
        assertEquals(-1, BatteryUse.minutesLeft(1_200_000, 0));
    }

    @Test
    void theSaverComesOnByItselfAtItsLevel() {
        assertTrue(BatteryUse.saving(false, 15, 100, 15));
        assertFalse(BatteryUse.saving(false, 16, 100, 15));
        assertTrue(BatteryUse.saving(true, 90, 100, 15));
        assertFalse(BatteryUse.saving(false, 0, 0, 15));
    }

    @Test
    void aDropWarnsAtTwentyAndFivePercent() {
        assertEquals(20, BatteryUse.crossed(21, 20, 100));
        assertEquals(5, BatteryUse.crossed(6, 4, 100));
        assertEquals(-1, BatteryUse.crossed(19, 18, 100));
        // Both at once warns the lower.
        assertEquals(5, BatteryUse.crossed(30, 1, 100));
        // Charging warns nothing.
        assertEquals(-1, BatteryUse.crossed(4, 30, 100));
    }
}
