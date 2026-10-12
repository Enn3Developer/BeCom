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

    /** An ME network, by the id the server gives its grid while it lives (DESIGN.md 8.3). */
    public record MeVertex(int grid) {}

    /**
     * Where a phone that isn't a vertex of its own joins the graph, and how good that hop is: a carried phone at each
     * antenna it reaches (DESIGN.md 8.2). A phone on a line joins at its own vertex, through a lossless hop.
     */
    public record Entry(Object vertex, double snr) {

        public static List<Entry> of(int phone) {
            return List.of(new Entry(new PhoneVertex(phone), Double.POSITIVE_INFINITY));
        }
    }

    // The two halves of a phone: where its calls leave, and where calls to it arrive.
    private record PhoneOut(int number) {}

    private record PhoneIn(int number) {}

    /** What a hop is, for anything that cares which kinds of hop a path crossed. */
    public enum HopKind {
        LINE,
        CABLE,
        /** A UHF link between two antennas (DESIGN.md 7). */
        RADIO,
        /** An ME Bridge joining its ME network (DESIGN.md 8.3). */
        ME_BRIDGE
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
        return width(new PhoneVertex(fromPhone), new PhoneVertex(toPhone));
    }

    /**
     * The weakest hop on the best path between two vertices: phones as where calls leave and arrive, nodes as they
     * are. Positive infinity from a node to itself, so two phones at the same antenna lose nothing between.
     */
    public double width(Object from, Object to) {
        if (from instanceof PhoneVertex a && to instanceof PhoneVertex b && a.number() == b.number()) {
            return Double.NEGATIVE_INFINITY;
        }
        // Entries come only from working nodes, and a node with no links at all still serves its phones.
        if (!(from instanceof PhoneVertex) && from.equals(to)) return Double.POSITIVE_INFINITY;
        Integer tail = index.get(tail(from));
        Integer head = index.get(head(to));
        if (tail == null || head == null) return Double.NEGATIVE_INFINITY;
        return result(tail).width(head);
    }

    /** The best path between two phones joining the graph through {@code from} and {@code to}: any pair of them. */
    public double width(List<Entry> from, List<Entry> to) {
        Entry[] best = bestPair(from, to);
        return best == null ? Double.NEGATIVE_INFINITY : widthOf(best[0], best[1]);
    }

    private double widthOf(Entry a, Entry b) {
        return Math.min(Math.min(a.snr(), b.snr()), width(a.vertex(), b.vertex()));
    }

    private Entry[] bestPair(List<Entry> from, List<Entry> to) {
        Entry[] best = null;
        double width = Double.NEGATIVE_INFINITY;
        for (Entry a : from) {
            for (Entry b : to) {
                double w = widthOf(a, b);
                if (w > width) {
                    width = w;
                    best = new Entry[] { a, b };
                }
            }
        }
        return best;
    }

    /** The kinds of hop on the best path between two phones joining through entries, the entry hops left out. */
    public List<HopKind> hops(List<Entry> from, List<Entry> to) {
        Entry[] best = bestPair(from, to);
        return best == null ? new ArrayList<>() : hops(best[0].vertex(), best[1].vertex());
    }

    private static Object tail(Object vertex) {
        return vertex instanceof PhoneVertex phone ? new PhoneOut(phone.number()) : vertex;
    }

    private static Object head(Object vertex) {
        return vertex instanceof PhoneVertex phone ? new PhoneIn(phone.number()) : vertex;
    }

    /** The hops on the best path between two phones, in order; empty when there's none. */
    public List<HopKind> hops(int fromPhone, int toPhone) {
        if (fromPhone == toPhone) return new ArrayList<>();
        return hops(new PhoneVertex(fromPhone), new PhoneVertex(toPhone));
    }

    private List<HopKind> hops(Object fromVertex, Object toVertex) {
        Integer from = index.get(tail(fromVertex));
        Integer to = index.get(head(toVertex));
        List<HopKind> hops = new ArrayList<>();
        if (from == null || to == null || from.equals(to)) return hops;
        for (WidestPath.Edge edge : result(from).edges(to)) hops.add((HopKind) edge.tag());
        return hops;
    }

    /** Every other phone with any path from {@code fromPhone}, with the path's weakest SNR. */
    public Map<Integer, Double> phonesFrom(int fromPhone) {
        return phonesFrom(Entry.of(fromPhone));
    }

    /**
     * Every phone on a line with any path from a phone joining the graph through {@code from}, with the best path's
     * weakest SNR, entry hops included. Never the phone itself.
     */
    public Map<Integer, Double> phonesFrom(List<Entry> from) {
        Map<Integer, Double> phones = new LinkedHashMap<>();
        for (Entry entry : from) {
            Integer tail = index.get(tail(entry.vertex()));
            if (tail == null) continue;
            int own = entry.vertex() instanceof PhoneVertex phone ? phone.number() : -1;
            WidestPath.Result result = result(tail);
            for (int i = 0; i < vertices.size(); i++) {
                if (!(vertices.get(i) instanceof PhoneIn phone) || phone.number() == own) continue;
                if (!result.reachable(i)) continue;
                phones.merge(phone.number(), Math.min(entry.snr(), result.width(i)), Math::max);
            }
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
