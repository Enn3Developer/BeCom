package com.enn3developer.becom.logic;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Work done a slice per tick within a time budget (DESIGN.md 6.5, 14: threading), so a large network's periodic
 * checks never land in a single tick. Each tick takes at least one item, so a sweep always finishes.
 */
public final class Sweep<T> {

    private final Deque<T> queue = new ArrayDeque<>();

    /** Queues a new round of items behind whatever the last round left. */
    public void add(Collection<? extends T> items) {
        queue.addAll(items);
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int remaining() {
        return queue.size();
    }

    /**
     * Handles queued items until the queue is empty or {@code budgetNanos} has passed on {@code clock}.
     *
     * @return how many items were handled
     */
    public int run(Consumer<? super T> work, LongSupplier clock, long budgetNanos) {
        long start = clock.getAsLong();
        int done = 0;
        while (!queue.isEmpty()) {
            work.accept(queue.poll());
            done++;
            if (clock.getAsLong() - start >= budgetNanos) break;
        }
        return done;
    }
}
