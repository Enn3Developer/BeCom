package com.enn3developer.becom.logic.app;

/**
 * How apps' data moves (joining a computer to BeCom): a message takes as long as its size at the route's rate, and a
 * route is as fast as its slowest end. No signal, no data.
 */
public final class DataLink {

    private static final int TICKS_PER_SECOND = 20;

    private DataLink() {}

    /** Ticks a message of {@code bytes} takes at {@code rateKBps}; at least one, and -1 when nothing carries it. */
    public static int ticks(int bytes, int rateKBps) {
        if (rateKBps <= 0) return -1;
        double seconds = (double) Math.max(0, bytes) / (rateKBps * 1024.0);
        return Math.max(1, (int) Math.ceil(seconds * TICKS_PER_SECOND));
    }

    /** The rate of a route between two ends: the slower one's; 0 when either has none. */
    public static int rate(int a, int b) {
        return Math.max(0, Math.min(a, b));
    }
}
