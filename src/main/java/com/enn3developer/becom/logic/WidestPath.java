package com.enn3developer.becom.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * A call's best path (DESIGN.md 14): the one whose weakest hop is strongest. A Dijkstra variant over directed edges
 * weighted by hop SNR, where a path's value is its smallest edge instead of its sum.
 * <p>
 * Vertices are dense ints; callers keep their own index. Not thread-safe; build, solve and throw away.
 */
public final class WidestPath {

    private final int vertexCount;
    private final List<List<Edge>> out;

    public WidestPath(int vertexCount) {
        this.vertexCount = vertexCount;
        this.out = new ArrayList<>(vertexCount);
        for (int i = 0; i < vertexCount; i++) out.add(new ArrayList<>());
    }

    public int vertexCount() {
        return vertexCount;
    }

    /** Adds a hop from {@code from} to {@code to} at {@code snr} dB. {@code tag} is the caller's, carried through. */
    public void addEdge(int from, int to, double snr, Object tag) {
        out.get(from)
            .add(new Edge(to, snr, tag));
    }

    public void addUndirected(int a, int b, double snr, Object tag) {
        addEdge(a, b, snr, tag);
        addEdge(b, a, snr, tag);
    }

    /** Best paths from {@code source} to every vertex. */
    public Result solve(int source) {
        return solve(source, edge -> true, vertex -> true);
    }

    /**
     * Best paths from {@code source} to every vertex over only the edges {@code edgeOk} allows, entering only the
     * vertices {@code vertexOk} allows: what's left of the network once other calls took their share.
     */
    public Result solve(int source, Predicate<Edge> edgeOk, IntPredicate vertexOk) {
        double[] best = new double[vertexCount];
        int[] prev = new int[vertexCount];
        Edge[] via = new Edge[vertexCount];
        Arrays.fill(best, Double.NEGATIVE_INFINITY);
        Arrays.fill(prev, -1);
        best[source] = Double.POSITIVE_INFINITY;
        boolean[] done = new boolean[vertexCount];
        PriorityQueue<double[]> queue = new PriorityQueue<>((x, y) -> Double.compare(y[0], x[0]));
        queue.add(new double[] { best[source], source });
        while (!queue.isEmpty()) {
            double[] head = queue.poll();
            int u = (int) head[1];
            if (done[u]) continue;
            done[u] = true;
            for (Edge e : out.get(u)) {
                if (!edgeOk.test(e) || !vertexOk.test(e.to)) continue;
                double width = Math.min(best[u], e.snr);
                if (width > best[e.to]) {
                    best[e.to] = width;
                    prev[e.to] = u;
                    via[e.to] = e;
                    queue.add(new double[] { width, e.to });
                }
            }
        }
        return new Result(source, best, prev, via);
    }

    public record Edge(int to, double snr, Object tag) {}

    /** Best paths from one source. */
    public static final class Result {

        private final int source;
        private final double[] best;
        private final int[] prev;
        private final Edge[] via;

        private Result(int source, double[] best, int[] prev, Edge[] via) {
            this.source = source;
            this.best = best;
            this.prev = prev;
            this.via = via;
        }

        /** The weakest hop's SNR on the best path to {@code target}, or negative infinity when unreachable. */
        public double width(int target) {
            return best[target];
        }

        public boolean reachable(int target) {
            return best[target] != Double.NEGATIVE_INFINITY;
        }

        /** The vertices from the source to {@code target}, both included; empty when unreachable. */
        public List<Integer> path(int target) {
            if (!reachable(target)) return Collections.emptyList();
            List<Integer> path = new ArrayList<>();
            for (int v = target; v != -1; v = prev[v]) path.add(v);
            Collections.reverse(path);
            return path;
        }

        /** The edges from the source to {@code target}, in order; empty when unreachable or the source itself. */
        public List<Edge> edges(int target) {
            if (!reachable(target)) return Collections.emptyList();
            List<Edge> edges = new ArrayList<>();
            for (int v = target; v != source && v != -1; v = prev[v]) edges.add(via[v]);
            Collections.reverse(edges);
            return edges;
        }
    }
}
