package com.enn3developer.becom.logic.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CallGraphTest {

    @Test
    void phonesReachEachOtherOverCableAndLine() {
        CallGraph.NodeVertex ta = new CallGraph.NodeVertex(0, 1);
        CallGraph.NodeVertex r = new CallGraph.NodeVertex(0, 2);
        CallGraph.NodeVertex tb = new CallGraph.NodeVertex(0, 3);
        CallGraph graph = CallGraph.builder()
            .twoWay(new CallGraph.PhoneVertex(111111), ta, Double.POSITIVE_INFINITY, CallGraph.HopKind.CABLE)
            .twoWay(ta, r, 18, CallGraph.HopKind.LINE)
            .twoWay(r, tb, 17, CallGraph.HopKind.LINE)
            .twoWay(new CallGraph.PhoneVertex(222222), tb, Double.POSITIVE_INFINITY, CallGraph.HopKind.CABLE)
            .build();
        assertEquals(17, graph.width(111111, 222222), 1e-12);
        assertEquals(
            Arrays.asList(
                CallGraph.HopKind.CABLE,
                CallGraph.HopKind.LINE,
                CallGraph.HopKind.LINE,
                CallGraph.HopKind.CABLE),
            graph.hops(111111, 222222));
        Map<Integer, Double> reachable = graph.phonesFrom(111111);
        assertEquals(1, reachable.size());
        assertEquals(17, reachable.get(222222), 1e-12);
    }

    @Test
    void unknownPhonesAreUnreachable() {
        CallGraph graph = CallGraph.builder()
            .build();
        assertEquals(Double.NEGATIVE_INFINITY, graph.width(1, 2));
        assertTrue(
            graph.phonesFrom(1)
                .isEmpty());
        assertTrue(
            graph.hops(1, 2)
                .isEmpty());
    }
}
