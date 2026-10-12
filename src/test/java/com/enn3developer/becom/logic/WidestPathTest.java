package com.enn3developer.becom.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class WidestPathTest {

    @Test
    void picksThePathWhoseWeakestHopIsStrongest() {
        // 0 -> 1 -> 3 is short but has a 5 dB hop; 0 -> 2 -> 4 -> 3 is longer but never below 10 dB.
        WidestPath graph = new WidestPath(5);
        graph.addUndirected(0, 1, 30, "a");
        graph.addUndirected(1, 3, 5, "b");
        graph.addUndirected(0, 2, 12, "c");
        graph.addUndirected(2, 4, 10, "d");
        graph.addUndirected(4, 3, 20, "e");
        WidestPath.Result result = graph.solve(0);
        assertEquals(10, result.width(3), 1e-12);
        assertEquals(Arrays.asList(0, 2, 4, 3), result.path(3));
        List<WidestPath.Edge> edges = result.edges(3);
        assertEquals(
            Arrays.asList("c", "d", "e"),
            edges.stream()
                .map(WidestPath.Edge::tag)
                .toList());
    }

    @Test
    void unreachableVertexHasNoPath() {
        WidestPath graph = new WidestPath(3);
        graph.addUndirected(0, 1, 10, null);
        WidestPath.Result result = graph.solve(0);
        assertFalse(result.reachable(2));
        assertEquals(Double.NEGATIVE_INFINITY, result.width(2));
        assertTrue(
            result.path(2)
                .isEmpty());
    }

    @Test
    void directedHopsOnlyGoOneWay() {
        WidestPath graph = new WidestPath(2);
        graph.addEdge(0, 1, 15, null);
        assertTrue(
            graph.solve(0)
                .reachable(1));
        assertFalse(
            graph.solve(1)
                .reachable(0));
    }

    @Test
    void sourceIsInfinitelyWideAndLosslessHopsKeepTheLineSnr() {
        WidestPath graph = new WidestPath(3);
        graph.addUndirected(0, 1, Double.POSITIVE_INFINITY, null);
        graph.addUndirected(1, 2, 18, null);
        WidestPath.Result result = graph.solve(0);
        assertEquals(Double.POSITIVE_INFINITY, result.width(0));
        assertEquals(18, result.width(2), 1e-12);
    }
}
