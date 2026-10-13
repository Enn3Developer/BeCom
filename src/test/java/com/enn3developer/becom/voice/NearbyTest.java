package com.enn3developer.becom.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.enn3developer.gtnhvoice.api.server.PlayerSnapshot;

/** The per-frame index may return players too far away, but never leaves out one in range. */
class NearbyTest {

    private static PlayerSnapshot at(double x, double y, double z, int dimension) {
        return new PlayerSnapshot(UUID.randomUUID(), "p", x, y, z, dimension);
    }

    @Test
    void everyoneInRangeIsFoundAcrossCellsAndBelowZero() {
        Random random = new Random(21);
        List<PlayerSnapshot> players = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            players.add(at(random.nextDouble() * 160 - 80, 64, random.nextDouble() * 160 - 80, random.nextInt(2)));
        }
        Nearby nearby = new Nearby(players);
        for (int q = 0; q < 200; q++) {
            double x = random.nextDouble() * 160 - 80, z = random.nextDouble() * 160 - 80;
            double range = q % 2 == 0 ? 6 : 16;
            int dimension = random.nextInt(2);
            List<PlayerSnapshot> found = nearby.around(dimension, x, z, range);
            for (PlayerSnapshot p : players) {
                double dx = p.getX() - x, dz = p.getZ() - z;
                boolean inRange = p.getDimensionId() == dimension && dx * dx + dz * dz <= range * range;
                if (inRange) assertTrue(found.contains(p), "a player " + Math.sqrt(dx * dx + dz * dz) + " away");
                if (p.getDimensionId() != dimension) assertFalse(found.contains(p), "a player in another dimension");
            }
        }
    }

    @Test
    void aPlayerOnACellsEdgeIsFoundFromTheNextCell() {
        PlayerSnapshot edge = at(16.0, 64, -16.0, 0);
        PlayerSnapshot inside = at(15.99, 64, -0.01, 0);
        Nearby nearby = new Nearby(List.of(edge, inside));
        assertTrue(
            nearby.around(0, 15.5, -15.5, 1)
                .contains(edge));
        assertTrue(
            nearby.around(0, 16.5, -0.5, 1)
                .contains(inside));
    }

    @Test
    void aPlayerWhosePositionIsntANumberIsInNoCell() {
        // NaN used to land in the cell at the world's origin, where spawn often is.
        PlayerSnapshot spawn = at(1, 64, 1, 0);
        Nearby nearby = new Nearby(
            List.of(
                spawn,
                at(Double.NaN, 64, Double.NaN, 0),
                at(Double.NaN, Double.NaN, Double.NaN, 0),
                at(Double.POSITIVE_INFINITY, 64, 0, 0),
                at(0, Double.NEGATIVE_INFINITY, 0, 0)));
        assertEquals(List.of(spawn), nearby.around(0, 0, 0, 16));
        assertEquals(List.of(), nearby.around(0, Double.NaN, 0, 16), "and a device at NaN finds nobody");
        assertEquals(List.of(), nearby.around(0, 0, Double.POSITIVE_INFINITY, 16));
    }
}
