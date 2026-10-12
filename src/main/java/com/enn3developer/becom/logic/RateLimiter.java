package com.enn3developer.becom.logic;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * At most {@code limit} attempts per key in any sliding window of {@code windowMillis} (DESIGN.md 12.3: call
 * attempts per caller). Server thread only.
 */
public final class RateLimiter<K> {

    private final int limit;
    private final long windowMillis;
    private final Map<K, ArrayDeque<Long>> attempts = new HashMap<>();

    public RateLimiter(int limit, long windowMillis) {
        if (limit < 1) throw new IllegalArgumentException("limit must be at least 1: " + limit);
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    /** Records an attempt by {@code key} at {@code nowMillis} if it's allowed; false when over the limit. */
    public boolean tryAcquire(K key, long nowMillis) {
        ArrayDeque<Long> times = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        prune(times, nowMillis);
        if (times.size() >= limit) return false;
        times.addLast(nowMillis);
        return true;
    }

    /** Forgets keys with no attempts left in the window, so the map doesn't only grow. */
    public void sweep(long nowMillis) {
        Iterator<ArrayDeque<Long>> it = attempts.values()
            .iterator();
        while (it.hasNext()) {
            ArrayDeque<Long> times = it.next();
            prune(times, nowMillis);
            if (times.isEmpty()) it.remove();
        }
    }

    public int trackedKeys() {
        return attempts.size();
    }

    private void prune(ArrayDeque<Long> times, long nowMillis) {
        while (!times.isEmpty() && nowMillis - times.peekFirst() >= windowMillis) times.removeFirst();
    }
}
