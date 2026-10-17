package com.enn3developer.becom.logic.phone;

import java.util.Arrays;

/**
 * Where a carried phone's charge went today, by what used it (the Battery settings page): kept per Minecraft day and
 * started over when the day changes. Also the rules the page shows: how long the charge lasts, when the battery saver
 * is on, and which warning a drop crosses. Pure, so it's tested without a world.
 */
public final class BatteryUse {

    /** What drew the charge. Ordinals are saved, so constants are only ever appended. */
    public enum Use {

        STANDBY,
        CALLS,
        VIDEO,
        SCREEN,
        APPS;

        private static final Use[] VALUES = values();

        public static Use byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
        }
    }

    /** The charge left, as percentages, at which a phone warns its holder (on screen and with a beep). */
    public static final int[] WARNINGS = { 20, 5 };

    private long day;
    private final long[] eu;

    public BatteryUse(long day, long[] eu) {
        this.day = day;
        this.eu = Arrays.copyOf(eu, Use.VALUES.length);
    }

    public static BatteryUse empty(long day) {
        return new BatteryUse(day, new long[0]);
    }

    public long day() {
        return day;
    }

    /** EU drawn today by each use, by ordinal. */
    public long[] eu() {
        return eu.clone();
    }

    /** Adds {@code amount} EU of {@code use} on {@code today}, starting over first when the day changed. */
    public void add(long today, Use use, long amount) {
        if (today != day) {
            day = today;
            Arrays.fill(eu, 0);
        }
        if (amount > 0) eu[use.ordinal()] += amount;
    }

    /** Each use's share of today's drain, in whole percent, by ordinal; all 0 before anything drew. */
    public int[] shares() {
        long total = 0;
        for (long e : eu) total += e;
        int[] shares = new int[eu.length];
        if (total <= 0) return shares;
        for (int i = 0; i < eu.length; i++) shares[i] = (int) Math.round(100.0 * eu[i] / total);
        return shares;
    }

    /** Minutes {@code charge} EU lasts at {@code euPerTick}; -1 when nothing drains it. */
    public static long minutesLeft(double charge, double euPerTick) {
        if (euPerTick <= 0) return -1;
        return (long) Math.floor(Math.max(0, charge) / euPerTick / 20 / 60);
    }

    /** Whether the saver is on: switched on, or by itself at {@code autoPercent} or less. */
    public static boolean saving(boolean switchedOn, double charge, double max, int autoPercent) {
        return switchedOn || max > 0 && charge * 100 <= max * autoPercent;
    }

    /**
     * The warning a drop from {@code before} to {@code after} EU crosses, the lowest if it crosses more than one, or -1
     * for none. A charge going up crosses nothing.
     */
    public static int crossed(double before, double after, double max) {
        if (max <= 0 || after >= before) return -1;
        int found = -1;
        for (int mark : WARNINGS) {
            double at = max * mark / 100;
            if (before > at && after <= at) found = mark;
        }
        return found;
    }
}
