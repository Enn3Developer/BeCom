package com.enn3developer.becom.logic.radio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The capture effect (D24): on a radio channel, each listener hears only the strongest of several transmitters.
 * Whoever a listener hears keeps the channel until {@code hold} after their last frame; another transmitter takes over
 * sooner only if its link to that listener is at least {@code margin} dB stronger.
 * <p>
 * Run from {@code route()} on GTNH Voice's network thread; the server thread only ever removes entries, so a
 * concurrent map is enough.
 */
public final class Capture<L> {

    private static final long SWEEP_FLOOR_NANOS = 10_000_000_000L;
    private final long holdNanos;
    private final double margin;
    private final Map<Key<L>, State> holders = new ConcurrentHashMap<>();

    public Capture(long holdNanos, double margin) {
        this.holdNanos = holdNanos;
        this.margin = margin;
    }

    private record Key<L> (L listener, int channel) {}

    // Written by route() on the network thread and read by the server thread (holder()), so volatile: the server must
    // see a whole state, not a field at a time.
    private static final class State {

        volatile Object speaker;
        volatile double snr;
        volatile long lastNanos;
    }

    /**
     * Whether {@code listener} hears this frame from {@code speaker} on {@code channel}, whose link to it is
     * {@code snr} dB, at {@code nowNanos}. Admitting a frame makes the speaker the one this listener holds.
     */
    public boolean admit(L listener, int channel, Object speaker, double snr, long nowNanos) {
        State state = holders.computeIfAbsent(new Key<>(listener, channel), k -> new State());
        if (speaker.equals(state.speaker) && state.lastNanos == nowNanos) {
            // The same frame again, along another path: the listener's link is the strongest of them.
            state.snr = Math.max(state.snr, snr);
            return true;
        }
        boolean free = state.speaker == null || nowNanos - state.lastNanos > holdNanos;
        if (!free && !state.speaker.equals(speaker) && snr < state.snr + margin) return false;
        state.speaker = speaker;
        state.snr = snr;
        state.lastNanos = nowNanos;
        return true;
    }

    /**
     * Who {@code listener} is holding on {@code channel}, or null. Safe from any thread; the answer may be a frame old.
     */
    public Object holder(L listener, int channel) {
        State state = holders.get(new Key<>(listener, channel));
        return state == null ? null : state.speaker;
    }

    /** Server thread: forgets a listener, on every channel. */
    public void forgetListener(L listener) {
        holders.keySet()
            .removeIf(k -> k.listener.equals(listener));
    }

    /** Server thread: forgets holds whose last frame is long gone, so the map doesn't only grow. */
    public void sweep(long nowNanos) {
        // Never sooner than ten seconds: the server reads holds after a speaker lets go, for the squelch tail, and a
        // hold set near zero in the config mustn't sweep them before then.
        long keep = Math.max(holdNanos * 100, SWEEP_FLOOR_NANOS);
        holders.values()
            .removeIf(s -> nowNanos - s.lastNanos > keep);
    }

    public int size() {
        return holders.size();
    }

    public void clear() {
        holders.clear();
    }
}
