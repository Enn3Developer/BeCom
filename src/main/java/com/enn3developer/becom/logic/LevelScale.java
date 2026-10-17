package com.enn3developer.becom.logic;

/**
 * Turns a hop's SNR into a {@link SignalLevel}. A link enters a level at its threshold and leaves it
 * only {@code hysteresis} dB below, so a link sitting on a threshold doesn't flicker.
 */
public final class LevelScale {

    private final double low;
    private final double medium;
    private final double high;
    private final double max;
    private final double hysteresis;

    public LevelScale(double low, double medium, double high, double max, double hysteresis) {
        if (!(low < medium && medium < high && high < max)) {
            throw new IllegalArgumentException(
                "level thresholds must rise: " + low + ", " + medium + ", " + high + ", " + max);
        }
        if (hysteresis < 0) throw new IllegalArgumentException("hysteresis must not be negative: " + hysteresis);
        this.low = low;
        this.medium = medium;
        this.high = high;
        this.max = max;
        this.hysteresis = hysteresis;
    }

    /** The dB a link needs to enter {@code level}; {@link SignalLevel#NONE} needs nothing. */
    public double threshold(SignalLevel level) {
        return switch (level) {
            case NONE -> Double.NEGATIVE_INFINITY;
            case LOW -> low;
            case MEDIUM -> medium;
            case HIGH -> high;
            case MAX -> max;
        };
    }

    public double hysteresis() {
        return hysteresis;
    }

    /** The level for {@code snr} with no history: the highest level whose threshold it reaches. */
    public SignalLevel levelOf(double snr) {
        if (snr >= max) return SignalLevel.MAX;
        if (snr >= high) return SignalLevel.HIGH;
        if (snr >= medium) return SignalLevel.MEDIUM;
        if (snr >= low) return SignalLevel.LOW;
        return SignalLevel.NONE;
    }

    /**
     * The level after {@code current} sees {@code snr}: up to whatever {@code snr} reaches, but down only to the
     * highest level {@code snr} hasn't fallen {@code hysteresis} dB below.
     */
    public SignalLevel next(SignalLevel current, double snr) {
        SignalLevel fresh = levelOf(snr);
        if (fresh.atLeast(current)) return fresh;
        // Falling: stay at the highest level at or below current that snr is still within hysteresis of.
        SignalLevel level = current;
        while (level != SignalLevel.NONE && snr < threshold(level) - hysteresis) {
            level = SignalLevel.byOrdinal(level.ordinal() - 1);
        }
        return SignalLevel.max(level, fresh);
    }
}
