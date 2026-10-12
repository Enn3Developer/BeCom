package com.enn3developer.becom.logic;

/**
 * Says when a weak link drops (DESIGN.md 4.2): once its SNR has stayed below the Low threshold for longer than the
 * grace period. Climbing back above the threshold resets the clock.
 */
public final class DropTimer {

    private final double thresholdSnr;
    private final long graceMillis;
    // A flag, not a sentinel time: the monotonic clock behind it may read negative.
    private boolean below;
    private long belowSince;

    public DropTimer(double thresholdSnr, long graceMillis) {
        this.thresholdSnr = thresholdSnr;
        this.graceMillis = graceMillis;
    }

    /** Feeds one evaluation at {@code nowMillis}; true once the link should drop. */
    public boolean update(double snr, long nowMillis) {
        if (snr >= thresholdSnr) {
            below = false;
            return false;
        }
        if (!below) {
            below = true;
            belowSince = nowMillis;
        }
        return nowMillis - belowSince > graceMillis;
    }

    /**
     * Carries on from {@code other}: below since when it was, so a link that takes over a weak one gets no fresh grace.
     */
    public void continueFrom(DropTimer other) {
        below = other.below;
        belowSince = other.belowSince;
    }

    public boolean isBelow() {
        return below;
    }
}
