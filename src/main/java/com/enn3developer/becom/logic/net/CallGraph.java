package com.enn3developer.becom.logic.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.enn3developer.becom.logic.WidestPath;

/**
 * The graph calls are routed over (DESIGN.md 14: path finding): working powered nodes of every dimension, the phones
 * hanging off them, and the hops between, each weighted by its SNR. Built when the network changes, then queried; each
 * source's best paths are solved once and kept until the next rebuild.
 * <p>
 * A phone is where a path starts or ends, never a way through: inside, each phone is two vertices, one that only has
 * hops out and one that only has hops in. A phone touching two lines can call either, but doesn't join them (D19).
 */
public final class CallGraph {

    /** A powered node of some dimension. */
    public record NodeVertex(int dimension, long pos) {}

    /** A phone, by number. */
    public record PhoneVertex(int number) {}

    // The two halves of a phone: where its calls leave, and where calls to it arrive.
    private record PhoneOut(int number) {}

    private record PhoneIn(int number) {}

    /** What a hop is, for anything that cares which kinds of hop a path crossed. */
    public enum HopKind {
        LINE,
        CABLE,
        /** A UHF link between two antennas (DESIGN.md 7). */
        RADIO
    }

    private final Map<Object, Integer> index;
    private final List<Object> vertices;
    private final WidestPath paths;
    private final Map<Integer, WidestPath.Result> solved = new HashMap<>();

    private CallGraph(Map<Object, Integer> index, List<Object> vertices, WidestPath paths) {
        this.index = index;
        this.vertices = vertices;
        this.paths = paths;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasPhone(int number) {
        return index.containsKey(new PhoneOut(number)) || index.containsKey(new PhoneIn(number));
    }

    /** The weakest hop's SNR on the best path between two phones, or negative infinity when there's none. */
    public double width(int fromPhone, int toPhone) {
        Integer from = index.get(new PhoneOut(fromPhone));
        Integer to = index.get(new PhoneIn(toPhone));
        if (from == null || to == null || fromPhone == toPhone) return Double.NEGATIVE_INFINITY;
        return result(from).width(to);
    }

    /** The hops on the best path between two phones, in order; empty when there's none. */
    public List<HopKind> hops(int fromPhone, int toPhone) {
        Integer from = index.get(new PhoneOut(fromPhone));
        Integer to = index.get(new PhoneIn(toPhone));
        List<HopKind> hops = new ArrayList<>();
        if (from == null || to == null || fromPhone == toPhone) return hops;
        for (WidestPath.Edge edge : result(from).edges(to)) hops.add((HopKind) edge.tag());
        return hops;
    }

    /** Every other phone with any path from {@code fromPhone}, with the path's weakest SNR. */
    public Map<Integer, Double> phonesFrom(int fromPhone) {
        Map<Integer, Double> phones = new LinkedHashMap<>();
        Integer from = index.get(new PhoneOut(fromPhone));
        if (from == null) return phones;
        WidestPath.Result result = result(from);
        for (int i = 0; i < vertices.size(); i++) {
            if (!(vertices.get(i) instanceof PhoneIn phone) || phone.number() == fromPhone) continue;
            if (result.reachable(i)) phones.put(phone.number(), result.width(i));
        }
        return phones;
    }

    private WidestPath.Result result(int from) {
        return solved.computeIfAbsent(from, paths::solve);
    }

    public static final class Builder {

        private final Map<Object, Integer> index = new HashMap<>();
        private final List<Object> vertices = new ArrayList<>();
        private final List<Object[]> edges = new ArrayList<>();

        private int vertex(Object key) {
            return index.computeIfAbsent(key, k -> {
                vertices.add(k);
                return vertices.size() - 1;
            });
        }

        /** A one-way hop: line stretches are found from each end, so each end adds its own. */
        public Builder hop(Object from, Object to, double snr, HopKind kind) {
            Object tail = from instanceof PhoneVertex phone ? new PhoneOut(phone.number()) : from;
            Object head = to instanceof PhoneVertex phone ? new PhoneIn(phone.number()) : to;
            edges.add(new Object[] { vertex(tail), vertex(head), snr, kind });
            return this;
        }

        public Builder twoWay(Object a, Object b, double snr, HopKind kind) {
            hop(a, b, snr, kind);
            return hop(b, a, snr, kind);
        }

        public CallGraph build() {
            WidestPath paths = new WidestPath(vertices.size());
            for (Object[] edge : edges) paths.addEdge((int) edge[0], (int) edge[1], (double) edge[2], edge[3]);
            return new CallGraph(index, vertices, paths);
        }
    }
}
