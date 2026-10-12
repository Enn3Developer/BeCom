package com.enn3developer.becom.logic.radio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NodeRef;

/** D26, DESIGN.md 6.1: rain weakens UHF links where either end stands in rain, and leaves VHF alone. */
class AntennaWeatherTest {

    private static final int DIM = 0;
    private static final double GROUND = 64;
    private final Tunables t = Tunables.defaults();
    private final UUID user = UUID.randomUUID();
    private double weather;
    // West of x 50 it rains; east of it is desert.
    private final AntennaNetwork network = new AntennaNetwork(t, new AntennaNetwork.Dimensions() {

        @Override
        public Terrain terrain(int dimension) {
            return (x, z) -> GROUND;
        }

        @Override
        public boolean noSky(int dimension) {
            return false;
        }

        @Override
        public double weather(int dimension) {
            return weather;
        }

        @Override
        public boolean rains(int dimension, int x, int z) {
            return x < 50;
        }
    }, (a, b) -> true);

    private final NodeRef wet = at(0);
    private final NodeRef dry = at(60);
    private final NodeRef drier = at(120);

    private static NodeRef at(int x) {
        return new NodeRef(DIM, BlockPos.pack(x, 84, 0));
    }

    private void run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            for (NodeRef ref : List.of(wet, dry, drier)) {
                network.seen(ref, 2, new RadioHops.End(BlockPos.x(ref.pos()) + 0.5, 85, 0.5, 0, GROUND), false, user);
            }
            network.tick(Long.MAX_VALUE, () -> 0);
        }
    }

    private Map<Long, Double> uhf() {
        Map<Long, Double> snr = new HashMap<>();
        network.forEachUhfLink(DIM, (a, b, value) -> snr.put(a ^ b, value));
        return snr;
    }

    private static RadioHops.End player(double x) {
        return new RadioHops.End(x, GROUND + 1.62, 0.5, -1.62, GROUND);
    }

    @Test
    void rainWeakensOnlyUhfLinksWithAnEndInIt() {
        run(20);
        Map<Long, Double> clear = uhf();
        long version = network.version();
        weather = t.weatherLoss(1, 1);
        run(20);
        assertTrue(network.version() > version, "a change in the weather moves calls' paths");
        Map<Long, Double> storm = uhf();
        assertEquals(clear.get(wet.pos() ^ dry.pos()) - 6, storm.get(wet.pos() ^ dry.pos()), 1e-9);
        assertEquals(clear.get(dry.pos() ^ drier.pos()), storm.get(dry.pos() ^ drier.pos()), 1e-9);
    }

    @Test
    void rainWeakensAPhonesServiceButNotARadios() {
        run(20);
        double phone = bestSnr(network.uhf(), true);
        double radio = bestSnr(network.vhf(), true);
        double desert = bestFrom(90, network.uhf(), false, dry, drier);
        weather = t.weatherLoss(1, 0);
        run(20);
        assertEquals(phone - 3, bestSnr(network.uhf(), true), 1e-9);
        assertEquals(radio, bestSnr(network.vhf(), true), 1e-9);
        // A phone in the desert, served by desert antennas, keeps its signal.
        assertEquals(desert, bestFrom(90, network.uhf(), false, dry, drier), 1e-9);
        // The same phone through the antenna standing in rain loses it.
        assertTrue(bestFrom(90, network.uhf(), false, wet) < desert);
    }

    private double bestSnr(Band band, boolean rains) {
        return network.best(DIM, player(20), user, band, rains)
            .losses()
            .snr();
    }

    private double bestFrom(double x, Band band, boolean rains, NodeRef... only) {
        double best = Double.NEGATIVE_INFINITY;
        for (AntennaNetwork.Best reached : network.reach(DIM, player(x), user, band, rains)) {
            for (NodeRef ref : only) if (reached.antenna()
                .ref()
                .equals(ref))
                best = Math.max(
                    best,
                    reached.losses()
                        .snr());
        }
        return best;
    }

    @Test
    void theLossIsTheRainsInRainAndTheStormsInAStorm() {
        assertEquals(3, t.weatherLoss(1, 0), 1e-9);
        assertEquals(6, t.weatherLoss(1, 1), 1e-9);
        assertEquals(1.5, t.weatherLoss(0.5, 0), 1e-9);
        assertEquals(0, t.weatherLoss(0, 0), 1e-9);
    }
}
