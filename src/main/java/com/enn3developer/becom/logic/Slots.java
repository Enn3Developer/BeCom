package com.enn3developer.becom.logic;

/**
 * Work spread over a second's 20 ticks (DESIGN.md 6.5): each thing has its own tick of the second, the same every
 * second, so once-a-second work for many things never lands in one tick.
 */
public final class Slots {

    public static final int TICKS_PER_SECOND = 20;

    private Slots() {}

    /** Whether {@code tick} is {@code key}'s tick of the second. */
    public static boolean due(Object key, long tick) {
        return Math.floorMod(key.hashCode(), TICKS_PER_SECOND) == Math.floorMod(tick, TICKS_PER_SECOND);
    }
}
