package com.enn3developer.becom.logic;

import java.util.function.DoubleSupplier;

/**
 * How many frames a weak link loses, and in what bursts (DESIGN.md 4.2). Between the full-loss and the clean SNR the
 * loss rate falls linearly from {@code maxLoss} to zero; the losses come from a two-state Gilbert-Elliott chain whose
 * bad state lasts {@code meanBurstMillis} on average, so a breaking link sounds like one instead of random crackle.
 */
public final class DropoutModel {

    private final double maxLoss;
    private final double fullLossSnr;
    private final double cleanSnr;
    private final double leaveBadChance;

    /**
     * @param maxLoss         loss rate at {@code fullLossSnr}, in [0, 1)
     * @param fullLossSnr     the dB where losses peak (the Low threshold)
     * @param cleanSnr        the dB from which nothing is lost (the Medium threshold)
     * @param meanBurstMillis how long a burst of losses lasts on average
     * @param frameMillis     how long one frame is
     */
    public DropoutModel(double maxLoss, double fullLossSnr, double cleanSnr, double meanBurstMillis,
        double frameMillis) {
        if (maxLoss < 0 || maxLoss >= 1) throw new IllegalArgumentException("maxLoss must be in [0, 1): " + maxLoss);
        if (!(fullLossSnr < cleanSnr)) {
            throw new IllegalArgumentException("fullLossSnr must be below cleanSnr: " + fullLossSnr + ", " + cleanSnr);
        }
        if (meanBurstMillis < frameMillis || frameMillis <= 0) {
            throw new IllegalArgumentException(
                "a burst must last at least one frame: " + meanBurstMillis + " ms, frame " + frameMillis + " ms");
        }
        this.maxLoss = maxLoss;
        this.fullLossSnr = fullLossSnr;
        this.cleanSnr = cleanSnr;
        this.leaveBadChance = frameMillis / meanBurstMillis;
    }

    /**
     * The long-run share of frames lost at {@code snr}. Below the full-loss SNR the link is on its way to dropping
     * ({@link DropTimer}), and it keeps losing at the peak rate until it does.
     */
    public double lossRate(double snr) {
        if (snr >= cleanSnr) return 0;
        if (snr <= fullLossSnr) return maxLoss;
        return maxLoss * (cleanSnr - snr) / (cleanSnr - fullLossSnr);
    }

    /** Chance per frame of leaving a burst: one over the mean burst length in frames. */
    public double leaveBadChance() {
        return leaveBadChance;
    }

    /**
     * Chance per frame of entering a burst for a long-run loss rate of {@code lossRate}: the stationary loss of the
     * chain, {@code enter / (enter + leave)}, solved for {@code enter}.
     */
    public double enterBadChance(double lossRate) {
        if (lossRate <= 0) return 0;
        return Math.min(1, lossRate * leaveBadChance / (1 - lossRate));
    }

    /**
     * One step of the chain for one speaker-listener pair. Thread-confined: the routing thread owns each pair's
     * state, and only one frame of a speaker is routed at a time.
     */
    public static final class Chain {

        private boolean bad;

        /**
         * Advances the chain one frame and says whether to drop it.
         *
         * @param enterBad chance of entering a burst this frame (from {@link #enterBadChance})
         * @param leaveBad chance of leaving a burst this frame (from {@link #leaveBadChance})
         * @param roll     a uniform random number in [0, 1)
         */
        public boolean drop(double enterBad, double leaveBad, double roll) {
            if (bad) {
                if (roll < leaveBad) bad = false;
            } else if (roll < enterBad) {
                bad = true;
            }
            return bad;
        }

        /**
         * Advances the chain {@code frames} frames at once and says whether the last is dropped. Video frames come
         * further apart than voice frames (DESIGN.md 10.1), so each samples the same chain, run on in between.
         */
        public boolean dropAfter(int frames, double enterBad, double leaveBad, DoubleSupplier random) {
            boolean dropped = bad;
            for (int i = 0; i < frames; i++) dropped = drop(enterBad, leaveBad, random.getAsDouble());
            return dropped;
        }

        public boolean inBurst() {
            return bad;
        }
    }
}
