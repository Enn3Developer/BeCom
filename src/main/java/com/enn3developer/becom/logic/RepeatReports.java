package com.enn3developer.becom.logic;

import java.util.HashMap;
import java.util.Map;

/**
 * Lets each key through at most once per window (DESIGN.md 15.5): a quest task done while its quest is still locked
 * is reported again once the window has passed, so it isn't used up for good, and one done every tick doesn't walk the
 * quest book every tick. Server thread only.
 */
public final class RepeatReports<K> {

    private final long windowMillis;
    private final Map<K, Long> last = new HashMap<>();

    public RepeatReports(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    /** Whether {@code key} is reported at {@code nowMillis}: never before, or not within the window. */
    public boolean due(K key, long nowMillis) {
        Long before = last.get(key);
        if (before != null && nowMillis - before < windowMillis) return false;
        last.put(key, nowMillis);
        return true;
    }

    /** Forgets keys whose window has passed, so the map doesn't only grow. */
    public void sweep(long nowMillis) {
        last.values()
            .removeIf(before -> nowMillis - before >= windowMillis);
    }

    public int tracked() {
        return last.size();
    }

    public void clear() {
        last.clear();
    }
}
