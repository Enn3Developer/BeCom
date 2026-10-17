package com.enn3developer.becom.logic.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.LevelScale;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;

class SkyStrengthTest {

    private final Tunables t = Tunables.defaults();
    private final SkyStrength sky = SkyStrength.of(t);
    private final LevelScale levels = t.levelScale();

    private SignalLevel level(int satellites) {
        return levels.levelOf(sky.snr(satellites));
    }

    @Test
    void designsCountsReachEachLevelFirst() {
        // 5, 14, 37 and 100 satellites give Low, Medium, High and Max.
        assertEquals(SignalLevel.LOW, level(5));
        assertEquals(SignalLevel.MEDIUM, level(14));
        assertEquals(SignalLevel.HIGH, level(37));
        assertEquals(SignalLevel.MAX, level(100));
        // ...and one fewer doesn't yet.
        assertEquals(SignalLevel.LOW, level(13));
        assertEquals(SignalLevel.MEDIUM, level(36));
        assertEquals(SignalLevel.HIGH, level(99));
    }

    @Test
    void theFormulaIsTheDesigns() {
        // 6 + 18 * log10(n / 5) / log10(20), up to Max's 100.
        for (int n = 5; n <= 100; n += 5) {
            assertEquals(6 + 18 * Math.log10(n / 5.0) / Math.log10(20), sky.snr(n), 1e-9, n + " satellites");
        }
    }

    @Test
    void moreThanAHundredAddNothing() {
        // Max at 100 is the most; a thousand satellites don't reach deeper underground.
        assertEquals(t.levelMax(), sky.snr(1000), 1e-9);
        assertEquals(t.levelMax(), sky.snr(Integer.MAX_VALUE), 1e-9);
        assertTrue(sky.hop(4000, 200, t.uhf(), 0) < t.levelLow());
        assertTrue(sky.hop(1000, 12, t.uhf(), 0) < t.levelLow());
    }

    @Test
    void fewerThanFiveGiveNoSkyHop() {
        assertFalse(sky.enough(4));
        assertTrue(sky.enough(5));
        assertEquals(Double.NEGATIVE_INFINITY, sky.snr(4));
        assertEquals(Double.NEGATIVE_INFINITY, sky.snr(0));
    }

    @Test
    void aRoofWeakensItAndUndergroundItsGone() {
        double open = sky.hop(100, -10, t.uhf(), 0);
        assertEquals(sky.snr(100), open, 1e-9);
        // Two blocks of roof on UHF: 2.5 dB each.
        assertEquals(sky.snr(100) - 5, sky.hop(100, 2, t.uhf(), 0), 1e-9);
        assertEquals(sky.snr(100) - 3, sky.hop(100, 2, t.vhf(), 0), 1e-9);
        // Deep down, the cover's 40 dB cap leaves nothing at Low.
        assertTrue(sky.hop(100, 30, t.uhf(), 0) < t.levelLow());
    }

    @Test
    void theAnalyzersTermsAddUpToTheHop() {
        // The satellites' strength is the budget, less the cover and the weather.
        var losses = sky.losses(37, 3, t.uhf(), t.weatherRainLoss());
        assertEquals(sky.hop(37, 3, t.uhf(), t.weatherRainLoss()), losses.snr(), 1e-9);
        assertEquals(sky.snr(37), losses.budget(), 1e-9);
        assertEquals(7.5, losses.coverA(), 1e-9);
        assertEquals(null, sky.losses(4, 0, t.uhf(), 0));
    }

    @Test
    void weatherCostsASkyHop() {
        assertEquals(sky.snr(37) - t.weatherStormLoss(), sky.hop(37, 0, t.vhf(), t.weatherStormLoss()), 1e-9);
    }
}
