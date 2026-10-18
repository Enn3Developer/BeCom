package com.enn3developer.becom.logic.space;

import java.util.Collection;
import java.util.function.IntUnaryOperator;

import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.CallGraph;

/**
 * The hops that join networks far apart and in other dimensions: each working Satellite Dish up
 * to the satellites over its dimension, each planet's Rocket Radio Receiver up to the Overworld's, and each linked
 * pair of Portal Relays through its portal. Devices under open sky join the satellites on their own, as they reach
 * them. Each carries a fixed number of calls: a dish its own, the satellites over a dimension a few each, shared by
 * everything under that sky, a receiver its own, and a relay pair its lower relay's tier's. Pure, so the rules are
 * tested without a world; the server says what is working.
 */
public final class Gateways {

    /** The Overworld, whose satellites every planet's receiver uplinks to. */
    public static final int OVERWORLD = 0;

    /** A working Satellite Dish, and what its hop up loses: rain at the dish and its maintenance problems (9.2). */
    public record Dish(int dimension, long pos, double loss, int calls) {}

    /** A working Rocket Radio Receiver, by the dimension and spot its rocket stands at. */
    public record Receiver(int dimension, long pos) {}

    /** A linked pair of working Portal Relays, and the GT voltage tier the pair works at: its lower relay's. */
    public record RelayPair(int dimensionA, long posA, int dimensionB, long posB, int tier) {}

    /** The link a dish's hop up shares its rate on. */
    private record DishLink(int dimension, long pos) {}

    /** The link a receiver's uplink shares its rate on. */
    private record UplinkLink(int dimension, long pos) {}

    /** The link a relay pair's hop shares its rate on. */
    private record RelayLink(int dimensionA, long posA, int dimensionB, long posB) {}

    private final SkyStrength sky;
    private final Tunables t;
    private final double uplink;
    private final double relay;

    /** The receiver's uplink and the relay pair's hop sit at the thresholds of their configured levels. */
    public Gateways(Tunables t) {
        this.sky = SkyStrength.of(t);
        this.t = t;
        this.uplink = t.levelScale()
            .threshold(t.receiverUplink());
        this.relay = t.levelScale()
            .threshold(t.relayHop());
    }

    public static Gateways of(Tunables t) {
        return new Gateways(t);
    }

    /** How many calls the satellites over a dimension carry at once, shared by everything under its sky. */
    public int skyCalls(int satellites) {
        return sky.enough(satellites) ? satellites * t.satelliteCalls() : 0;
    }

    /**
     * Adds every gateway's hops to {@code builder}. {@code satellites} gives how many satellites orbit over a
     * dimension: 0 for one without a sky, which satellites don't reach.
     */
    public void addTo(CallGraph.Builder builder, IntUnaryOperator satellites, Collection<Dish> dishes,
        Collection<Receiver> receivers, Collection<RelayPair> relays) {
        for (Dish dish : dishes) {
            // Below Low's count there are no sky hops at all.
            int count = satellites.applyAsInt(dish.dimension());
            double snr = sky.snr(count) - dish.loss();
            if (snr == Double.NEGATIVE_INFINITY) continue;
            CallGraph.NodeVertex vertex = new CallGraph.NodeVertex(dish.dimension(), dish.pos());
            builder.twoWay(
                vertex,
                new CallGraph.SkyVertex(dish.dimension()),
                snr,
                CallGraph.HopKind.SKY,
                new DishLink(dish.dimension(), dish.pos()),
                t.radioRate(
                    t.levelScale()
                        .levelOf(snr)));
            builder.slots(vertex, dish.calls());
            builder.slots(new CallGraph.SkyVertex(dish.dimension()), skyCalls(count));
        }
        // Planets reach the Overworld's satellites only while it has enough of them.
        int overworld = satellites.applyAsInt(OVERWORLD);
        if (sky.enough(overworld)) {
            for (Receiver receiver : receivers) {
                if (receiver.dimension() == OVERWORLD) continue;
                CallGraph.NodeVertex vertex = new CallGraph.NodeVertex(receiver.dimension(), receiver.pos());
                builder.twoWay(
                    vertex,
                    new CallGraph.SkyVertex(OVERWORLD),
                    uplink,
                    CallGraph.HopKind.UPLINK,
                    new UplinkLink(receiver.dimension(), receiver.pos()),
                    t.radioRate(t.receiverUplink()));
                builder.slots(vertex, t.receiverCalls());
                builder.slots(new CallGraph.SkyVertex(OVERWORLD), skyCalls(overworld));
            }
        }
        for (RelayPair pair : relays) {
            CallGraph.NodeVertex a = new CallGraph.NodeVertex(pair.dimensionA(), pair.posA());
            CallGraph.NodeVertex b = new CallGraph.NodeVertex(pair.dimensionB(), pair.posB());
            builder.twoWay(
                a,
                b,
                relay,
                CallGraph.HopKind.RELAY,
                new RelayLink(pair.dimensionA(), pair.posA(), pair.dimensionB(), pair.posB()),
                t.relayRate(pair.tier()));
            builder.slots(a, t.relayCalls(pair.tier()));
            builder.slots(b, t.relayCalls(pair.tier()));
        }
    }
}
