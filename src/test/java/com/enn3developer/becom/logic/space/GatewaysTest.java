package com.enn3developer.becom.logic.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.CallGraph;

class GatewaysTest {

    private static final int OVERWORLD = 0;
    private static final int NETHER = -1;
    private static final int MOON = -28;
    private static final int MARS = -29;

    private final Tunables t = Tunables.defaults();
    private final Gateways gateways = Gateways.of(t);
    private final double high = t.levelScale()
        .threshold(SignalLevel.HIGH);

    private static CallGraph.NodeVertex node(int dimension, long pos) {
        return new CallGraph.NodeVertex(dimension, pos);
    }

    private CallGraph graph(Map<Integer, Integer> satellites, List<Gateways.Dish> dishes,
        List<Gateways.Receiver> receivers, List<Gateways.RelayPair> relays) {
        CallGraph.Builder builder = CallGraph.builder();
        gateways.addTo(builder, d -> satellites.getOrDefault(d, 0), dishes, receivers, relays);
        return builder.build();
    }

    @Test
    void twoDishesOfADimensionJoinThroughItsSatellites() {
        List<Gateways.Dish> dishes = List.of(new Gateways.Dish(OVERWORLD, 1, 0), new Gateways.Dish(OVERWORLD, 2, 0));
        // DESIGN.md 9.2: at least 5 satellites, and 5 give Low.
        CallGraph five = graph(Map.of(OVERWORLD, 5), dishes, List.of(), List.of());
        assertEquals(t.levelLow(), five.width(node(OVERWORLD, 1), node(OVERWORLD, 2)), 1e-9);
        CallGraph four = graph(Map.of(OVERWORLD, 4), dishes, List.of(), List.of());
        assertEquals(Double.NEGATIVE_INFINITY, four.width(node(OVERWORLD, 1), node(OVERWORLD, 2)));
    }

    @Test
    void rainAndMaintenanceCostADishsHop() {
        // A dish in the rain with two maintenance problems loses 3 + 2 * 2 dB on its hop; the other end's dish is
        // dry. 100 satellites give Max.
        double loss = t.weatherRainLoss() + 2 * t.dishMaintenanceLoss();
        CallGraph graph = graph(
            Map.of(OVERWORLD, 100),
            List.of(new Gateways.Dish(OVERWORLD, 1, loss), new Gateways.Dish(OVERWORLD, 2, 0)),
            List.of(),
            List.of());
        assertEquals(t.levelMax() - loss, graph.width(node(OVERWORLD, 1), node(OVERWORLD, 2)), 1e-9);
    }

    @Test
    void dishesOfDifferentDimensionsStayApart() {
        CallGraph graph = graph(
            Map.of(OVERWORLD, 100, MOON, 100),
            List.of(new Gateways.Dish(OVERWORLD, 1, 0), new Gateways.Dish(MOON, 2, 0)),
            List.of(),
            List.of());
        assertEquals(Double.NEGATIVE_INFINITY, graph.width(node(OVERWORLD, 1), node(MOON, 2)));
    }

    @Test
    void aDimensionWithoutASkyHasNoSkyHops() {
        // The server counts no satellites over a dimension without a sky (DESIGN.md 6.1).
        CallGraph graph = graph(
            Map.of(),
            List.of(new Gateways.Dish(NETHER, 1, 0), new Gateways.Dish(NETHER, 2, 0)),
            List.of(),
            List.of());
        assertEquals(Double.NEGATIVE_INFINITY, graph.width(node(NETHER, 1), node(NETHER, 2)));
    }

    @Test
    void aPhoneUnderTheOverworldsSkyReachesAMoonPhoneThroughTheReceiverAtHigh() {
        // DESIGN.md 9.5: the gateway hop is the lower of the Overworld end's sky hop and the receiver's uplink.
        // The Mobile Phone joins the sky at Max; the Moon phone reaches the receiver, a T1 antenna, at 30 dB.
        CallGraph graph = graph(Map.of(OVERWORLD, 100), List.of(), List.of(new Gateways.Receiver(MOON, 7)), List.of());
        List<CallGraph.Entry> overworld = List
            .of(new CallGraph.Entry(new CallGraph.SkyVertex(OVERWORLD), t.levelMax()));
        List<CallGraph.Entry> moon = List.of(new CallGraph.Entry(node(MOON, 7), 30));
        assertEquals(high, graph.width(overworld, moon), 1e-9);
        assertEquals(List.of(CallGraph.HopKind.UPLINK), graph.hops(overworld, moon));
        // A weak sky hop at the Overworld end is the weakest hop instead.
        List<CallGraph.Entry> roofed = List.of(new CallGraph.Entry(new CallGraph.SkyVertex(OVERWORLD), 8));
        assertEquals(8, graph.width(roofed, moon), 1e-9);
    }

    @Test
    void anOverworldDishTakesItsWholeNetworkToThePlanet() {
        CallGraph.NodeVertex terminal = node(OVERWORLD, 1);
        CallGraph.NodeVertex dish = node(OVERWORLD, 2);
        CallGraph.Builder builder = CallGraph.builder()
            .twoWay(new CallGraph.PhoneVertex(111111), terminal, Double.POSITIVE_INFINITY, CallGraph.HopKind.CABLE)
            .twoWay(terminal, dish, 28, CallGraph.HopKind.LINE);
        gateways.addTo(
            builder,
            d -> d == OVERWORLD ? 37 : 0,
            List.of(new Gateways.Dish(OVERWORLD, 2, 0)),
            List.of(new Gateways.Receiver(MOON, 7)),
            List.of());
        CallGraph graph = builder.build();
        List<CallGraph.Entry> moon = List.of(new CallGraph.Entry(node(MOON, 7), 30));
        // 37 satellites give High, the same as the uplink.
        assertEquals(high, graph.width(CallGraph.Entry.of(111111), moon), 1e-6);
        assertEquals(
            List.of(CallGraph.HopKind.CABLE, CallGraph.HopKind.LINE, CallGraph.HopKind.SKY, CallGraph.HopKind.UPLINK),
            graph.hops(CallGraph.Entry.of(111111), moon));
    }

    @Test
    void planetsReachEachOtherThroughTheOverworldsSatellites() {
        CallGraph graph = graph(
            Map.of(OVERWORLD, 5),
            List.of(),
            List.of(new Gateways.Receiver(MOON, 7), new Gateways.Receiver(MARS, 9)),
            List.of());
        List<CallGraph.Entry> moon = List.of(new CallGraph.Entry(node(MOON, 7), 30));
        List<CallGraph.Entry> mars = List.of(new CallGraph.Entry(node(MARS, 9), 25));
        assertEquals(high, graph.width(moon, mars), 1e-9);
    }

    @Test
    void uplinksNeedEnoughSatellitesOverTheOverworldNotOverThePlanet() {
        List<Gateways.Receiver> receivers = List.of(new Gateways.Receiver(MOON, 7));
        List<CallGraph.Entry> overworld = List.of(new CallGraph.Entry(new CallGraph.SkyVertex(OVERWORLD), 20));
        List<CallGraph.Entry> moon = List.of(new CallGraph.Entry(node(MOON, 7), 30));
        CallGraph few = graph(Map.of(OVERWORLD, 4, MOON, 100), List.of(), receivers, List.of());
        assertEquals(Double.NEGATIVE_INFINITY, few.width(overworld, moon));
        CallGraph enough = graph(Map.of(OVERWORLD, 5), List.of(), receivers, List.of());
        assertTrue(enough.width(overworld, moon) >= t.levelLow());
    }

    @Test
    void aReceiverInTheOverworldIsNoUplink() {
        CallGraph graph = graph(
            Map.of(OVERWORLD, 100),
            List.of(),
            List.of(new Gateways.Receiver(OVERWORLD, 7)),
            List.of());
        List<CallGraph.Entry> sky = List.of(new CallGraph.Entry(new CallGraph.SkyVertex(OVERWORLD), 24));
        List<CallGraph.Entry> receiver = List.of(new CallGraph.Entry(node(OVERWORLD, 7), 30));
        assertEquals(Double.NEGATIVE_INFINITY, graph.width(sky, receiver));
    }

    @Test
    void aRelayPairIsAHighHopBetweenTheOverworldAndTheNether() {
        // DESIGN.md 9.4 and 0.4's "Done when": a Telephone in the Overworld calls one in the Nether through a pair.
        CallGraph.NodeVertex terminalA = node(OVERWORLD, 1);
        CallGraph.NodeVertex relayA = node(OVERWORLD, 2);
        CallGraph.NodeVertex relayB = node(NETHER, 3);
        CallGraph.NodeVertex terminalB = node(NETHER, 4);
        CallGraph.Builder builder = CallGraph.builder()
            .twoWay(new CallGraph.PhoneVertex(111111), terminalA, Double.POSITIVE_INFINITY, CallGraph.HopKind.CABLE)
            .twoWay(terminalA, relayA, 28, CallGraph.HopKind.LINE)
            .twoWay(relayB, terminalB, 28, CallGraph.HopKind.LINE)
            .twoWay(new CallGraph.PhoneVertex(222222), terminalB, Double.POSITIVE_INFINITY, CallGraph.HopKind.CABLE);
        gateways.addTo(builder, d -> 0, List.of(), List.of(), List.of(new Gateways.RelayPair(OVERWORLD, 2, NETHER, 3)));
        CallGraph graph = builder.build();
        assertEquals(high, graph.width(111111, 222222), 1e-9);
        assertTrue(
            graph.hops(111111, 222222)
                .contains(CallGraph.HopKind.RELAY));
    }

    @Test
    void longerChainsCombineRelaysAndSatellites() {
        // DESIGN.md 9.5: Nether to Overworld to planet. A Nether phone on a relay pair's far side, the Overworld's
        // relay on a line to a dish, and a receiver on the Moon.
        CallGraph.NodeVertex netherTerminal = node(NETHER, 1);
        CallGraph.NodeVertex netherRelay = node(NETHER, 2);
        CallGraph.NodeVertex relay = node(OVERWORLD, 3);
        CallGraph.NodeVertex dish = node(OVERWORLD, 4);
        CallGraph.Builder builder = CallGraph.builder()
            .twoWay(
                new CallGraph.PhoneVertex(111111),
                netherTerminal,
                Double.POSITIVE_INFINITY,
                CallGraph.HopKind.CABLE)
            .twoWay(netherTerminal, netherRelay, 28, CallGraph.HopKind.LINE)
            .twoWay(relay, dish, 28, CallGraph.HopKind.LINE);
        gateways.addTo(
            builder,
            d -> d == OVERWORLD ? 100 : 0,
            List.of(new Gateways.Dish(OVERWORLD, 4, 0)),
            List.of(new Gateways.Receiver(MOON, 7)),
            List.of(new Gateways.RelayPair(NETHER, 2, OVERWORLD, 3)));
        CallGraph graph = builder.build();
        List<CallGraph.Entry> moon = List.of(new CallGraph.Entry(node(MOON, 7), 30));
        assertEquals(high, graph.width(CallGraph.Entry.of(111111), moon), 1e-9);
    }
}
