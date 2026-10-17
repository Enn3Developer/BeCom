package com.enn3developer.becom.logic.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** How calls share the network's capacity (DESIGN.md 5.4, 5.5, 7): link rates and vertex call slots. */
class CallRoutingTest {

    private static final int VOICE = 2;

    private final CallGraph.NodeVertex ta = new CallGraph.NodeVertex(0, 1);
    private final CallGraph.NodeVertex tb = new CallGraph.NodeVertex(0, 2);
    private final CallGraph.NodeVertex exchange = new CallGraph.NodeVertex(0, 3);

    /** Phones 100.. on terminal A and 200.. on terminal B, joined by one run of {@code rate} KB/s. */
    private CallGraph.Builder twoTerminals(int phones, int rate) {
        CallGraph.Builder builder = CallGraph.builder();
        for (int i = 0; i < phones; i++) {
            builder.twoWay(
                new CallGraph.PhoneVertex(100 + i),
                ta,
                Double.POSITIVE_INFINITY,
                CallGraph.HopKind.CABLE,
                "a" + i,
                16);
            builder.twoWay(
                new CallGraph.PhoneVertex(200 + i),
                tb,
                Double.POSITIVE_INFINITY,
                CallGraph.HopKind.CABLE,
                "b" + i,
                16);
        }
        return builder.twoWay(ta, tb, Double.POSITIVE_INFINITY, CallGraph.HopKind.LINE, "run", rate);
    }

    private static CallGraph.Demand call(int id, int from, int to) {
        return new CallGraph.Demand(id, "stream" + id, CallGraph.Entry.of(from), CallGraph.Entry.of(to), VOICE);
    }

    @Test
    void aRunCarriesAsManyCallsAsItsRateHasRoomFor() {
        CallGraph graph = twoTerminals(9, 16).build();
        List<CallGraph.Demand> demands = new ArrayList<>();
        for (int i = 0; i < 9; i++) demands.add(call(i, 100 + i, 200 + i));
        CallGraph.Routing routing = graph.route(demands);
        // 16 KB/s of Phone Cable is room for 8 voice calls at 2 KB/s; the ninth finds none.
        for (int i = 0; i < 8; i++) assertTrue(
            routing.route("stream" + i)
                .routed(),
            "call " + i);
        assertFalse(
            routing.route("stream8")
                .routed());
        assertEquals(16, routing.used("run"));
    }

    @Test
    void theOldestCallsKeepTheirRoute() {
        CallGraph graph = twoTerminals(2, 2).build();
        CallGraph.Routing routing = graph.route(List.of(call(1, 100, 200), call(2, 101, 201)));
        assertTrue(
            routing.route("stream1")
                .routed());
        assertFalse(
            routing.route("stream2")
                .routed());
        assertFalse(
            routing.probe(call(3, 101, 201))
                .routed());
    }

    @Test
    void aFullLinkSendsACallTheOtherWay() {
        // A direct run with room for one call, and a longer way round through the Exchange.
        CallGraph graph = twoTerminals(2, 2)
            .twoWay(ta, exchange, Double.POSITIVE_INFINITY, CallGraph.HopKind.LINE, "ax", 64)
            .twoWay(exchange, tb, Double.POSITIVE_INFINITY, CallGraph.HopKind.LINE, "xb", 64)
            .slots(exchange, 8)
            .build();
        CallGraph.Routing routing = graph.route(List.of(call(1, 100, 200), call(2, 101, 201)));
        assertTrue(
            routing.route("stream1")
                .routed());
        assertTrue(
            routing.route("stream2")
                .routed());
        assertEquals(1, routing.calls(exchange));
    }

    @Test
    void anExchangeSwitchesOnlyAsManyCallsAsItHasSlots() {
        CallGraph.Builder builder = CallGraph.builder()
            .twoWay(ta, exchange, Double.POSITIVE_INFINITY, CallGraph.HopKind.LINE, "ax", 256)
            .twoWay(exchange, tb, Double.POSITIVE_INFINITY, CallGraph.HopKind.LINE, "xb", 256)
            .slots(exchange, 2);
        for (int i = 0; i < 3; i++) {
            builder.twoWay(new CallGraph.PhoneVertex(100 + i), ta, Double.POSITIVE_INFINITY, CallGraph.HopKind.CABLE);
            builder.twoWay(new CallGraph.PhoneVertex(200 + i), tb, Double.POSITIVE_INFINITY, CallGraph.HopKind.CABLE);
        }
        CallGraph graph = builder.build();
        CallGraph.Routing routing = graph.route(List.of(call(1, 100, 200), call(2, 101, 201), call(3, 102, 202)));
        assertTrue(
            routing.route("stream2")
                .routed());
        assertFalse(
            routing.route("stream3")
                .routed());
        assertEquals(2, routing.calls(exchange));
        // Capacity aside, the third pair still reaches: it's busy, not unreachable.
        assertEquals(Double.POSITIVE_INFINITY, graph.width(102, 202));
    }

    @Test
    void streamsOfOneCallShareAVertexSlot() {
        // A group call of three through one Exchange with a single slot: one call, so one slot.
        CallGraph.Builder builder = CallGraph.builder()
            .twoWay(ta, exchange, Double.POSITIVE_INFINITY, CallGraph.HopKind.LINE, "ax", 256)
            .twoWay(exchange, tb, Double.POSITIVE_INFINITY, CallGraph.HopKind.LINE, "xb", 256)
            .slots(exchange, 1)
            .twoWay(new CallGraph.PhoneVertex(100), ta, Double.POSITIVE_INFINITY, CallGraph.HopKind.CABLE)
            .twoWay(new CallGraph.PhoneVertex(200), tb, Double.POSITIVE_INFINITY, CallGraph.HopKind.CABLE)
            .twoWay(new CallGraph.PhoneVertex(201), tb, Double.POSITIVE_INFINITY, CallGraph.HopKind.CABLE);
        CallGraph graph = builder.build();
        CallGraph.Routing routing = graph.route(
            List.of(
                new CallGraph.Demand(1, "ab", CallGraph.Entry.of(100), CallGraph.Entry.of(200), VOICE),
                new CallGraph.Demand(1, "ac", CallGraph.Entry.of(100), CallGraph.Entry.of(201), VOICE)));
        assertTrue(
            routing.route("ab")
                .routed());
        assertTrue(
            routing.route("ac")
                .routed());
        assertEquals(1, routing.calls(exchange));
    }

    @Test
    void radioWidthStillCountsOnARoutedCall() {
        CallGraph graph = CallGraph.builder()
            .twoWay(new CallGraph.PhoneVertex(100), ta, Double.POSITIVE_INFINITY, CallGraph.HopKind.CABLE)
            .twoWay(ta, tb, 14, CallGraph.HopKind.RADIO, "radio", 16)
            .twoWay(new CallGraph.PhoneVertex(200), tb, Double.POSITIVE_INFINITY, CallGraph.HopKind.CABLE)
            .build();
        CallGraph.Route route = graph.route(List.of(call(1, 100, 200)))
            .route("stream1");
        assertEquals(14, route.width(), 1e-12);
        assertEquals(List.of(CallGraph.HopKind.CABLE, CallGraph.HopKind.RADIO, CallGraph.HopKind.CABLE), route.hops());
    }
}
