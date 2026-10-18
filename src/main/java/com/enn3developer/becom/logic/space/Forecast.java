package com.enn3developer.becom.logic.space;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;

/**
 * How a sky's satellites thin out if nothing more goes up, for the Launch Pad's Orbit page: the day each comes down,
 * the count below which the sky's level drops a step, and when it first does. Pure, so it's tested without a world.
 *
 * @param fallDays  the day from now each satellite comes down within {@code days}, in order
 * @param threshold the fewest satellites that keep the level it has now; 0 when it has none
 * @param below     the level under {@code threshold}
 * @param dropDay   the day the level first drops, or -1 when it holds over {@code days}
 * @param together  how many satellites come down that day
 * @param callsNow  the calls the sky carries now
 * @param callsThen the calls it carries once the level has dropped
 */
public record Forecast(List<Integer> fallDays, int threshold, SignalLevel below, int dropDay, int together,
    int callsNow, int callsThen) {

    /** The days a forecast looks ahead. */
    public static final int DAYS = 60;

    /**
     * The forecast for {@code count} satellites, which come down {@code ticksLeft} ticks from now each, over
     * {@code days} days.
     */
    public static Forecast of(Tunables t, int count, Collection<Long> ticksLeft, int days) {
        SkyStrength sky = SkyStrength.of(t);
        Gateways gateways = Gateways.of(t);
        List<Integer> falls = new ArrayList<>();
        for (long ticks : ticksLeft) {
            int day = (int) (Math.max(0, ticks) / Satellites.DAY_TICKS);
            if (day < days) falls.add(day);
        }
        falls.sort(null);
        SignalLevel now = levelOf(t, sky, count);
        int threshold = 0;
        if (now != SignalLevel.NONE) {
            threshold = count;
            while (threshold > 1 && levelOf(t, sky, threshold - 1) == now) threshold--;
        }
        SignalLevel below = threshold == 0 ? SignalLevel.NONE : levelOf(t, sky, threshold - 1);
        int dropDay = -1;
        int together = 0;
        int left = count;
        for (int i = 0; i < falls.size() && dropDay < 0;) {
            int day = falls.get(i);
            int same = 0;
            while (i < falls.size() && falls.get(i) == day) {
                same++;
                i++;
            }
            left -= same;
            if (threshold > 0 && left < threshold) {
                dropDay = day;
                together = same;
            }
        }
        return new Forecast(
            List.copyOf(falls),
            threshold,
            below,
            dropDay,
            together,
            gateways.skyCalls(count),
            dropDay < 0 ? gateways.skyCalls(count) : gateways.skyCalls(left));
    }

    private static SignalLevel levelOf(Tunables t, SkyStrength sky, int satellites) {
        return sky.enough(satellites) ? t.levelScale()
            .levelOf(sky.snr(satellites)) : SignalLevel.NONE;
    }
}
