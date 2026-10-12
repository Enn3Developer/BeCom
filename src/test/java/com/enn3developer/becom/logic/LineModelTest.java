package com.enn3developer.becom.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** DESIGN.md 5.7 and 16.3: a line starts dropping out past 225 blocks and drops at 300. */
class LineModelTest {

    private final Tunables tunables = Tunables.defaults();
    private final LineModel line = tunables.lineModel();
    private final DropoutModel dropouts = tunables.dropoutModel(20);

    @Test
    void noDropoutsUpTo225Blocks() {
        assertEquals(12.0, line.snr(225), 1e-9);
        assertEquals(0.0, dropouts.lossRate(line.snr(225)), 1e-9);
        assertTrue(dropouts.lossRate(line.snr(226)) > 0);
    }

    @Test
    void droppedLineAt300Blocks() {
        DropTimer timer = new DropTimer(tunables.levelLow(), (long) (tunables.dropAfterSeconds() * 1000));
        // Just short of 300 the link still holds at Low, losing frames at the peak rate.
        assertTrue(line.snr(299) >= tunables.levelLow());
        assertFalse(timer.update(line.snr(299), 0));
        assertFalse(timer.update(line.snr(299), 10_000));
        // Just past 300 it's below Low, and drops once it has stayed there longer than 3 s.
        assertTrue(line.snr(301) < tunables.levelLow());
        assertFalse(timer.update(line.snr(301), 20_000));
        assertFalse(timer.update(line.snr(301), 23_000));
        assertTrue(timer.update(line.snr(301), 23_001));
    }

    @Test
    void aRepeaterAtLeastEvery225Blocks() {
        assertEquals(225, line.lengthFor(tunables.dropoutCleanSnr()), 1e-9);
        assertEquals(300, line.lengthFor(tunables.levelLow()), 1e-9);
    }

    @Test
    void halfOfThe300BlockLineIsEighteenDecibels() {
        // The "Done when" line: 300 blocks with one repeater in the middle, so 150 blocks per stretch.
        assertEquals(18.0, line.snr(150), 1e-9);
    }
}
