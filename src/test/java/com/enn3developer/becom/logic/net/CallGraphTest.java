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
        // Two separate lines, A on one and C on the other. B touches the router of each: it may call either side,
        // but it doesn't join the lines.
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

    @Test
    void aCarriedPhoneJoinsAtTheAntennasItReaches() {
        // A Telephone on a router an antenna bridges, and two carried phones near antennas linked over UHF.
        CallGraph.NodeVertex router = new CallGraph.NodeVertex(0, 1);
        CallGraph.NodeVertex near = new CallGraph.NodeVertex(0, 2);
        CallGraph.NodeVertex far = new CallGraph.NodeVertex(0, 3);
        CallGraph graph = CallGraph.builder()
            .twoWay(new CallGraph.PhoneVertex(111111), router, Double.POSITIVE_INFINITY, CallGraph.HopKind.CABLE)
            .twoWay(near, router, 28, CallGraph.HopKind.LINE)
            .twoWay(near, far, 14, CallGraph.HopKind.RADIO)
            .build();
        java.util.List<CallGraph.Entry> mobile = java.util.List.of(new CallGraph.Entry(near, 10));
        java.util.List<CallGraph.Entry> other = java.util.List
            .of(new CallGraph.Entry(near, 6), new CallGraph.Entry(far, 20));
        // Its access hop is the weakest.
        assertEquals(10, graph.width(mobile, CallGraph.Entry.of(111111)), 1e-12);
        assertEquals(10, graph.width(CallGraph.Entry.of(111111), mobile), 1e-12);
        // The other phone reaches both antennas: through the far one is better (min 14) than the near one (6).
        assertEquals(10, graph.width(mobile, other), 1e-12);
        assertEquals(
            12,
            graph.width(
                java.util.List.of(new CallGraph.Entry(near, 12)),
                java.util.List.of(new CallGraph.Entry(far, 20))),
            1e-12);
        assertEquals(
            java.util.List.of(CallGraph.HopKind.RADIO),
            graph.hops(
                java.util.List.of(new CallGraph.Entry(near, 30)),
                java.util.List.of(new CallGraph.Entry(far, 30))));
        // Two phones at one antenna lose nothing between them.
        assertEquals(6, graph.width(java.util.List.of(new CallGraph.Entry(near, 6)), mobile), 1e-12);
        assertEquals(Map.of(111111, 10.0), graph.phonesFrom(mobile));
    }

    @Test
    void anAntennaWithNoLinksStillServesTwoPhones() {
        CallGraph graph = CallGraph.builder()
            .build();
        CallGraph.NodeVertex lone = new CallGraph.NodeVertex(0, 9);
        CallGraph.NodeVertex other = new CallGraph.NodeVertex(0, 10);
        assertEquals(
            15,
            graph.width(
                java.util.List.of(new CallGraph.Entry(lone, 20)),
                java.util.List.of(new CallGraph.Entry(lone, 15))),
            1e-12);
        assertEquals(
            Double.NEGATIVE_INFINITY,
            graph.width(
                java.util.List.of(new CallGraph.Entry(lone, 20)),
                java.util.List.of(new CallGraph.Entry(other, 20))));
    }
}
