package com.enn3developer.becom.logic;

/**
 * Positions that aren't numbers. A player's position that isn't finite counts as no position, and a range check keeps
 * only what's provably within range, so NaN and infinity are out of every range, as in GTNH Voice's own
 * {@code withinDistanceOf}. A check that skips when the squared distance is greater than the range's lets NaN through.
 */
public final class Positions {

    private Positions() {}

    public static boolean finite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    /** Whether a point {@code (dx, dy, dz)} away is within {@code range}, the range itself included. */
    public static boolean within(double dx, double dy, double dz, double range) {
        return withinSq(dx * dx + dy * dy + dz * dz, range);
    }

    /** Whether a squared distance is within {@code range}, the range itself included. */
    public static boolean withinSq(double distanceSq, double range) {
        return distanceSq <= range * range;
    }
}
