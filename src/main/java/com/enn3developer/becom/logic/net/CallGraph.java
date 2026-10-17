package com.enn3developer.becom.logic.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import com.enn3developer.becom.logic.WidestPath;

/**
 * The graph calls are routed over: working powered nodes of every dimension, the phones
 * hanging off them, and the hops between, each weighted by its SNR. Each hop also has a data rate, shared by every call
 * crossing its link, and some vertices a number of calls they carry at once. Built when the network
 * changes, then queried; each source's best paths, capacity aside, are solved once and kept until the next rebuild,
 * and {@link #route} shares the capacity out among calls.
 * <p>
 * A phone is where a path starts or ends, never a way through: inside, each phone is two vertices, one that only has
 * hops out and one that only has hops in. A phone touching two lines can call either, but doesn't join them.
 */
public final class CallGraph {

    /** A powered node of some dimension. */
    public record NodeVertex(int dimension, long pos) {}

    /** A phone, by number. */
    public record PhoneVertex(int number) {}

    /**
     * An ME network in one dimension, by the id the server gives its grid while it lives. An ME
     * network spanning dimensions is a vertex in each, so calls cross dimensions only through satellites, dishes,
     * receivers and relay pairs.
     */
    public record MeVertex(int grid, int dimension) {}

    /**
     * The satellites over a dimension: devices under its open sky and its Satellite Dishes join them,
     * and in the Overworld every planet's Rocket Radio Receiver too (9.2, 9.3).
     */
    public record SkyVertex(int dimension) {}

    /**
     * Where a phone that isn't a vertex of its own joins the graph, and how good that hop is: a carried phone at each
     * antenna it reaches. A phone on a line joins at its own vertex, through a lossless hop.
     */
    public record Entry(Object vertex, double snr) {

        public static List<Entry> of(int phone) {
            return List.of(new Entry(new PhoneVertex(phone), Double.POSITIVE_INFINITY));
        }
    }

    // The two halves of a phone: where its calls leave, and where calls to it arrive.
    private record PhoneOut(int number) {}

    private record PhoneIn(int number) {}

    /**
     * One hop as routing sees it: its kind, the link whose rate it shares with the hop back the other way and any
     * other hop of the same link, and that rate in KB/s.
     */
    public record Hop(HopKind kind, Object link, int rate) {}

    /** No limit on a hop's rate, or on a vertex's calls. */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    /** What a hop is, for anything that cares which kinds of hop a path crossed. */
    public enum HopKind {
        LINE,
        CABLE,
        /** A UHF link between two antennas. */
        RADIO,
        /** An ME Bridge joining its ME network. */
        ME_BRIDGE,
        /** A Satellite Dish's hop up to the satellites over its dimension. */
        SKY,
        /** A Rocket Radio Receiver's uplink to the Overworld's satellites. */
        UPLINK,
        /** A linked pair of Portal Relays. */
        RELAY
    }

    private final Map<Object, Integer> index;
    private final List<Object> vertices;
    private final WidestPath paths;
    private final int[] slots;
    private final Map<Integer, WidestPath.Result> solved = new HashMap<>();

    private CallGraph(Map<Object, Integer> index, List<Object> vertices, WidestPath paths, int[] slots) {
        this.index = index;
        this.vertices = vertices;
        this.paths = paths;
        this.slots = slots;
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
        for (WidestPath.Edge edge : result(from).edges(to)) hops.add(((Hop) edge.tag()).kind());
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

    // region Capacity

    /**
     * One stream a call needs: between two of its phones, joining the graph through their entries, at {@code rate}
     * KB/s. Streams of one {@code call} share each vertex's call slot.
     */
    public record Demand(Object call, Object key, List<Entry> from, List<Entry> to, int rate) {}

    /** Where a stream went: its weakest hop's SNR, negative infinity when nothing had room, and its hops. */
    public record Route(double width, List<HopKind> hops) {

        public static final Route NONE = new Route(Double.NEGATIVE_INFINITY, List.of());

        public boolean routed() {
            return width != Double.NEGATIVE_INFINITY;
        }
    }

    /**
     * Routes {@code demands} in order, each over the best path that still has room for it after the ones before:
     * {@code rate} on every hop's link and a call slot at every vertex with a limit. The first calls keep their
     * route; a call made when the network was full doesn't push one out.
     */
    public Routing route(List<Demand> demands) {
        Routing routing = new Routing();
        for (Demand demand : demands) routing.add(demand);
        return routing;
    }

    /**
     * Where data between two phones goes: its weakest hop's SNR, the KB/s free on its narrowest limited link
     * ({@link #UNLIMITED} when it crosses none), those links, and the vertices on the way.
     */
    public record DataRoute(double width, int free, List<Object> links, List<Object> vertices) {

        public static final DataRoute NONE = new DataRoute(Double.NEGATIVE_INFINITY, 0, List.of(), List.of());

        public boolean routed() {
            return width != Double.NEGATIVE_INFINITY;
        }
    }

    private record Found(double width, List<WidestPath.Edge> edges, List<Integer> path) {

        List<HopKind> kinds() {
            List<HopKind> kinds = new ArrayList<>();
            for (WidestPath.Edge edge : edges) kinds.add(((Hop) edge.tag()).kind());
            return kinds;
        }
    }

    /** What calls took of the network's capacity, and where each stream went. */
    public final class Routing {

        private final Map<Object, Integer> used = new HashMap<>();
        private final int[] callsAt = new int[vertices.size()];
        private final Map<Object, Set<Integer>> verticesOf = new HashMap<>();
        private final Map<Object, Route> routes = new LinkedHashMap<>();

        private Routing() {}

        /** Where the stream {@code key} went; {@link Route#NONE} for one never routed. */
        public Route route(Object key) {
            return routes.getOrDefault(key, Route.NONE);
        }

        /** The KB/s calls take of a link. */
        public int used(Object link) {
            return used.getOrDefault(link, 0);
        }

        /** The calls through a vertex; 0 for one the graph doesn't have. */
        public int calls(Object vertex) {
            Integer i = index.get(vertex);
            return i == null ? 0 : callsAt[i];
        }

        /** The best route a stream would get now, without taking it: whether a new call would find room. */
        public Route probe(Demand demand) {
            return best(demand, null);
        }

        /** Routes one more stream over what's left, and takes its share. */
        public Route add(Demand demand) {
            Route route = best(demand, demand);
            routes.put(demand.key(), route);
            return route;
        }

        private Route best(Demand demand, Demand taking) {
            Set<Integer> own = verticesOf.getOrDefault(demand.call(), Set.of());
            Found found = search(
                demand.from(),
                demand.to(),
                hop -> fits(hop, demand.rate()),
                vertex -> hasRoom(vertex, own));
            if (found == null) return Route.NONE;
            if (taking != null) {
                for (WidestPath.Edge edge : found.edges()) {
                    Hop hop = (Hop) edge.tag();
                    if (hop.rate() != UNLIMITED) used.merge(hop.link(), taking.rate(), Integer::sum);
                }
                Set<Integer> mine = verticesOf.computeIfAbsent(taking.call(), k -> new HashSet<>());
                for (int vertex : found.path()) if (mine.add(vertex)) callsAt[vertex]++;
            }
            return new Route(found.width(), found.kinds());
        }

        /**
         * The best route for data between two phones over what calls leave, less what other data already takes
         * ({@code dataOn}, KB/s by link). Data takes no call slot, and a call never makes way for it.
         */
        public DataRoute data(List<Entry> from, List<Entry> to, ToIntFunction<Object> dataOn) {
            Found found = search(
                from,
                to,
                hop -> hop.rate() == UNLIMITED || used(hop.link()) + dataOn.applyAsInt(hop.link()) < hop.rate(),
                vertex -> true);
            if (found == null) return DataRoute.NONE;
            int free = UNLIMITED;
            List<Object> links = new ArrayList<>();
            for (WidestPath.Edge edge : found.edges()) {
                Hop hop = (Hop) edge.tag();
                if (hop.rate() == UNLIMITED) continue;
                links.add(hop.link());
                free = Math.min(free, hop.rate() - used(hop.link()) - dataOn.applyAsInt(hop.link()));
            }
            List<Object> on = new ArrayList<>();
            for (int vertex : found.path()) on.add(vertices.get(vertex));
            return new DataRoute(found.width(), free, links, on);
        }

        private Found search(List<Entry> from, List<Entry> to, Predicate<Hop> hopOk, IntPredicate vertexOk) {
            Found best = null;
            for (Entry a : from) {
                Integer tail = index.get(tail(a.vertex()));
                if (tail == null || !vertexOk.test(tail)) continue;
                WidestPath.Result result = paths.solve(tail, edge -> hopOk.test((Hop) edge.tag()), vertexOk);
                for (Entry b : to) {
                    Integer head = index.get(head(b.vertex()));
                    if (head == null) continue;
                    double width;
                    if (head.equals(tail)) {
                        // Two phones joining at one vertex, as at one antenna, lose nothing between.
                        if (a.vertex() instanceof PhoneVertex) continue;
                        width = Math.min(a.snr(), b.snr());
                    } else {
                        if (!result.reachable(head)) continue;
                        width = Math.min(Math.min(a.snr(), b.snr()), result.width(head));
                    }
                    if (best == null || width > best.width()) {
                        best = head.equals(tail) ? new Found(width, List.of(), List.of(tail))
                            : new Found(width, result.edges(head), result.path(head));
                    }
                }
            }
            return best;
        }

        private boolean fits(Hop hop, int rate) {
            return hop.rate() == UNLIMITED || used(hop.link()) + rate <= hop.rate();
        }

        private boolean hasRoom(int vertex, Set<Integer> own) {
            return slots[vertex] == UNLIMITED || own.contains(vertex) || callsAt[vertex] < slots[vertex];
        }
    }

    /** How many calls a vertex carries at once; {@link #UNLIMITED} for most. */
    public int slots(Object vertex) {
        Integer i = index.get(vertex);
        return i == null ? UNLIMITED : slots[i];
    }

    // endregion

    public static final class Builder {

        private final Map<Object, Integer> index = new HashMap<>();
        private final List<Object> vertices = new ArrayList<>();
        private final List<Object[]> edges = new ArrayList<>();
        private final Map<Object, Integer> slots = new HashMap<>();

        private int vertex(Object key) {
            return index.computeIfAbsent(key, k -> {
                vertices.add(k);
                return vertices.size() - 1;
            });
        }

        /** A one-way hop with no limit on what it carries. */
        public Builder hop(Object from, Object to, double snr, HopKind kind) {
            return hop(from, to, snr, kind, null, UNLIMITED);
        }

        /**
         * A one-way hop carrying {@code rate} KB/s, shared with every other hop of the same {@code link}: runs of wire
         * are found from each end, so each end adds its own, of one link.
         */
        public Builder hop(Object from, Object to, double snr, HopKind kind, Object link, int rate) {
            Object tail = from instanceof PhoneVertex phone ? new PhoneOut(phone.number()) : from;
            Object head = to instanceof PhoneVertex phone ? new PhoneIn(phone.number()) : to;
            edges.add(new Object[] { vertex(tail), vertex(head), snr, new Hop(kind, link, rate) });
            return this;
        }

        public Builder twoWay(Object a, Object b, double snr, HopKind kind) {
            hop(a, b, snr, kind);
            return hop(b, a, snr, kind);
        }

        /** A hop each way sharing one link of {@code rate} KB/s. */
        public Builder twoWay(Object a, Object b, double snr, HopKind kind, Object link, int rate) {
            hop(a, b, snr, kind, link, rate);
            return hop(b, a, snr, kind, link, rate);
        }

        /** {@code vertex} carries at most {@code calls} calls at once: an Exchange's racks, an antenna's radio. */
        public Builder slots(Object vertex, int calls) {
            vertex(vertex);
            slots.merge(vertex, calls, Math::min);
            return this;
        }

        public CallGraph build() {
            WidestPath paths = new WidestPath(vertices.size());
            for (Object[] edge : edges) paths.addEdge((int) edge[0], (int) edge[1], (double) edge[2], edge[3]);
            int[] limits = new int[vertices.size()];
            java.util.Arrays.fill(limits, UNLIMITED);
            for (Map.Entry<Object, Integer> entry : slots.entrySet())
                limits[index.get(entry.getKey())] = entry.getValue();
            return new CallGraph(index, vertices, paths, limits);
        }
    }
}
