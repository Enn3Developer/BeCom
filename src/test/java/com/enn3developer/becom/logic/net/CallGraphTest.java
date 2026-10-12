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
    void aPhoneIsNeverAWayThrough() {
        // Two separate lines, A on one and C on the other. B touches the terminal of each: it may call either side,
        // but it doesn't join the lines (D19, DESIGN.md 5.4).
        CallGraph.NodeVertex t1 = new CallGraph.NodeVertex(0, 1);
        CallGraph.NodeVertex t1b = new CallGraph.NodeVertex(0, 2);
        CallGraph.NodeVertex t2 = new CallGraph.NodeVertex(0, 3);
        CallGraph.NodeVertex t2b = new CallGraph.NodeVertex(0, 4);
        CallGraph graph = CallGraph.builder()
            .twoWay(new CallGraph.PhoneVertex(111111), t1, Double.POSITIVE_INFINITY, CallGraph.HopKind.CABLE)
            .twoWay(t1, t1b, 20, CallGraph.HopKind.LINE)
            .twoWay(new CallGraph.PhoneVertex(222222), t1b, Double.POSITIVE_INFINITY, CallGraph.HopKind.CABLE)
            .twoWay(new CallGraph.PhoneVertex(222222), t2b, Double.POSITIVE_INFINITY, CallGraph.HopKind.CABLE)
            .twoWay(t2b, t2, 20, CallGraph.HopKind.LINE)
            .twoWay(new CallGraph.PhoneVertex(333333), t2, Double.POSITIVE_INFINITY, CallGraph.HopKind.CABLE)
            .build();
        assertEquals(Double.NEGATIVE_INFINITY, graph.width(111111, 333333));
        assertEquals(Double.NEGATIVE_INFINITY, graph.width(333333, 111111));
        assertEquals(Map.of(222222, 20.0), graph.phonesFrom(111111));
        assertEquals(20, graph.width(222222, 333333), 1e-12);
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
