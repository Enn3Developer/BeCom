package com.enn3developer.becom.logic.space;

import java.util.Collection;
import java.util.function.IntUnaryOperator;

import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.CallGraph;

/**
 * The hops that join networks far apart and in other dimensions (DESIGN.md 9.2 to 9.5): each working Satellite Dish up
 * to the satellites over its dimension, each planet's Rocket Radio Receiver up to the Overworld's, and each linked
 * pair of Portal Relays through its portal. Devices under open sky join the satellites on their own, as they reach
 * them. Pure, so the rules are tested without a world; the server says what is working.
 */
public final class Gateways {

    /** The Overworld, whose satellites every planet's receiver uplinks to (DESIGN.md 9.3). */
    public static final int OVERWORLD = 0;

    /** A working Satellite Dish, and what its hop up loses: rain at the dish and its maintenance problems (9.2). */
    public record Dish(int dimension, long pos, double loss) {}

    /** A working Rocket Radio Receiver, by the dimension and spot its rocket stands at (DESIGN.md 9.3). */
    public record Receiver(int dimension, long pos) {}

    /** A linked pair of working Portal Relays (DESIGN.md 9.4). */
    public record RelayPair(int dimensionA, long posA, int dimensionB, long posB) {}

    private final SkyStrength sky;
    private final double uplink;
    private final double relay;

    /**
     * @param uplink a receiver's uplink SNR, a fixed hop (DESIGN.md 9.5)
     * @param relay  a relay pair's SNR, a fixed hop (DESIGN.md 9.4)
     */
    public Gateways(SkyStrength sky, double uplink, double relay) {
        this.sky = sky;
        this.uplink = uplink;
        this.relay = relay;
    }

    /** The receiver's uplink and the relay pair's hop sit at the thresholds of their configured levels. */
    public static Gateways of(Tunables t) {
        return new Gateways(
            SkyStrength.of(t),
            t.levelScale()
                .threshold(t.receiverUplink()),
            t.levelScale()
                .threshold(t.relayHop()));
    }

    /**
     * Adds every gateway's hops to {@code builder}. {@code satellites} gives how many satellites orbit over a
     * dimension: 0 for one without a sky, which satellites don't reach (DESIGN.md 6.1).
     */
    public void addTo(CallGraph.Builder builder, IntUnaryOperator satellites, Collection<Dish> dishes,
        Collection<Receiver> receivers, Collection<RelayPair> relays) {
        for (Dish dish : dishes) {
            // Below Low's count there are no sky hops at all (DESIGN.md 9.2: at least 5 satellites).
            double snr = sky.snr(satellites.applyAsInt(dish.dimension()));
            if (snr == Double.NEGATIVE_INFINITY) continue;
            builder.twoWay(
                new CallGraph.NodeVertex(dish.dimension(), dish.pos()),
                new CallGraph.SkyVertex(dish.dimension()),
                snr - dish.loss(),
                CallGraph.HopKind.SKY);
        }
        // Planets reach the Overworld's satellites only while it has enough of them (DESIGN.md 9.5).
        if (sky.enough(satellites.applyAsInt(OVERWORLD))) {
            for (Receiver receiver : receivers) {
                if (receiver.dimension() == OVERWORLD) continue;
                builder.twoWay(
                    new CallGraph.NodeVertex(receiver.dimension(), receiver.pos()),
                    new CallGraph.SkyVertex(OVERWORLD),
                    uplink,
                    CallGraph.HopKind.UPLINK);
            }
        }
        for (RelayPair pair : relays) {
            builder.twoWay(
                new CallGraph.NodeVertex(pair.dimensionA(), pair.posA()),
                new CallGraph.NodeVertex(pair.dimensionB(), pair.posB()),
                relay,
                CallGraph.HopKind.RELAY);
        }
    }
}
