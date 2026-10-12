package com.enn3developer.becom.logic;

/**
 * Says when a weak link drops (DESIGN.md 4.2): once its SNR has stayed below the Low threshold for longer than the
 * grace period. Climbing back above the threshold resets the clock.
 */
public final class DropTimer {

    private final double thresholdSnr;
    private final long graceMillis;
    private long belowSince = -1;

    public DropTimer(double thresholdSnr, long graceMillis) {
        this.thresholdSnr = thresholdSnr;
        this.graceMillis = graceMillis;
    }

    /** Feeds one evaluation at {@code nowMillis}; true once the link should drop. */
    public boolean update(double snr, long nowMillis) {
        if (snr >= thresholdSnr) {
            belowSince = -1;
            return false;
        }
        if (belowSince < 0) belowSince = nowMillis;
        return nowMillis - belowSince > graceMillis;
    }

    public boolean isBelow() {
        return belowSince >= 0;
    }
}
