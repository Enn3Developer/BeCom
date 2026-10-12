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

    private final long holdNanos;
    private final double margin;
    private final Map<Key<L>, State> holders = new ConcurrentHashMap<>();

    public Capture(long holdNanos, double margin) {
        this.holdNanos = holdNanos;
        this.margin = margin;
    }

    private record Key<L> (L listener, int channel) {}

    private static final class State {

        Object speaker;
        double snr;
        long lastNanos;
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

    /** Server thread: forgets a listener, on every channel. */
    public void forgetListener(L listener) {
        holders.keySet()
            .removeIf(k -> k.listener.equals(listener));
    }

    /** Server thread: forgets holds whose last frame is long gone, so the map doesn't only grow. */
    public void sweep(long nowNanos) {
        holders.values()
            .removeIf(s -> nowNanos - s.lastNanos > holdNanos * 100);
    }

    public int size() {
        return holders.size();
    }

    public void clear() {
        holders.clear();
    }
}
