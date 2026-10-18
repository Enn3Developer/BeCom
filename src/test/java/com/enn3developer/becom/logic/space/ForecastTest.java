package com.enn3developer.becom.logic.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;

class ForecastTest {

    private final Tunables t = Tunables.defaults();
    private final SkyStrength sky = SkyStrength.of(t);

    private static long day(int day) {
        return day * Satellites.DAY_TICKS + 100;
    }

    private SignalLevel level(int satellites) {
        return sky.enough(satellites) ? t.levelScale()
            .levelOf(sky.snr(satellites)) : SignalLevel.NONE;
    }

    @Test
    void theThresholdIsTheFewestThatKeepTheLevel() {
        int count = 30;
        Forecast forecast = Forecast.of(t, count, List.of(), Forecast.DAYS);
        assertEquals(level(count), level(forecast.threshold()));
        assertTrue(level(forecast.threshold() - 1).ordinal() < level(count).ordinal());
        assertEquals(level(forecast.threshold() - 1), forecast.below());
        assertEquals(-1, forecast.dropDay());
    }

    @Test
    void itDropsTheDayEnoughComeDownTogether() {
        int count = 30;
        int threshold = Forecast.of(t, count, List.of(), Forecast.DAYS)
            .threshold();
        int spare = count - threshold;
        List<Long> falls = new ArrayList<>();
        // One a day keeps it up until the spare ones are gone, then three on day 29 drop it.
        for (int i = 0; i < spare; i++) falls.add(day(i + 1));
        falls.add(day(29));
        falls.add(day(29));
        falls.add(day(29));
        falls.add(day(70));
        Forecast forecast = Forecast.of(t, count, falls, Forecast.DAYS);
        assertEquals(29, forecast.dropDay());
        assertEquals(3, forecast.together());
        assertEquals(count * t.satelliteCalls(), forecast.callsNow());
        assertEquals((threshold - 3) * t.satelliteCalls(), forecast.callsThen());
        // The one after the forecast's days isn't in it.
        assertEquals(
            spare + 3,
            forecast.fallDays()
                .size());
    }

    @Test
    void aSkyWithTooFewHasNoThreshold() {
        Forecast forecast = Forecast.of(t, t.satellitesLow() - 1, List.of(day(3)), Forecast.DAYS);
        assertEquals(0, forecast.threshold());
        assertEquals(-1, forecast.dropDay());
        assertEquals(0, forecast.callsNow());
    }
}
