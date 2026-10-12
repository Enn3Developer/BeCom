package com.enn3developer.becom.logic.space;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The satellites in orbit (DESIGN.md 9.1, D23): each over the dimension it was launched from, until it comes down.
 * Times are the Overworld's world time in ticks, which only runs while the server does, so decay only advances then.
 * Server thread only; saved by the server.
 */
public final class Satellites {

    /** A Minecraft day, in ticks. */
    public static final long DAY_TICKS = 24_000;

    /** One satellite: the dimension it orbits, when it went up, and when it comes down. */
    public record Satellite(int dimension, long launched, long comesDown) {}

    private final List<Satellite> satellites = new ArrayList<>();
    // How many orbit over each dimension, kept as they go up and come down: radios ask for every listener.
    private final Map<Integer, Integer> counts = new HashMap<>();
    private long version;

    /**
     * How long a satellite stays up (D23): {@code days}, give or take {@code spread} of it, so they don't all come down
     * together. {@code random} is a draw from [0, 1).
     */
    public static long lifetime(double days, double spread, double random) {
        return Math.max(1, Math.round(days * DAY_TICKS * (1 + spread * (2 * random - 1))));
    }

    /** A satellite goes up over {@code dimension} at {@code now}, to stay {@code lifetime} ticks. */
    public void launch(int dimension, long now, long lifetime) {
        add(new Satellite(dimension, now, now + lifetime));
    }

    /** Adds a satellite as it was saved. */
    public void add(Satellite satellite) {
        satellites.add(satellite);
        counts.merge(satellite.dimension(), 1, Integer::sum);
        version++;
    }

    /** Takes down every satellite whose time has come by {@code now}; returns the dimensions that lost one. */
    public List<Integer> decay(long now) {
        List<Integer> lost = new ArrayList<>();
        Iterator<Satellite> it = satellites.iterator();
        while (it.hasNext()) {
            Satellite satellite = it.next();
            if (satellite.comesDown() > now) continue;
            it.remove();
            counts.computeIfPresent(satellite.dimension(), (d, n) -> n > 1 ? n - 1 : null);
            if (!lost.contains(satellite.dimension())) lost.add(satellite.dimension());
        }
        if (!lost.isEmpty()) version++;
        return lost;
    }

    /** How many satellites orbit over {@code dimension}. */
    public int count(int dimension) {
        return counts.getOrDefault(dimension, 0);
    }

    /** When the next satellite over {@code dimension} comes down; {@link Long#MAX_VALUE} with none up. */
    public long nextDown(int dimension) {
        long next = Long.MAX_VALUE;
        for (Satellite satellite : satellites) {
            if (satellite.dimension() == dimension) next = Math.min(next, satellite.comesDown());
        }
        return next;
    }

    /** Every dimension with satellites over it, and how many, by dimension. */
    public Map<Integer, Integer> counts() {
        return new TreeMap<>(counts);
    }

    public List<Satellite> all() {
        return Collections.unmodifiableList(satellites);
    }

    /** Goes up on every launch and every satellite that comes down. */
    public long version() {
        return version;
    }
}
