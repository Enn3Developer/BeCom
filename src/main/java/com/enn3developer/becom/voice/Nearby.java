package com.enn3developer.becom.voice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.enn3developer.gtnhvoice.api.server.PlayerSnapshot;

/**
 * One frame's players by dimension and 16-block cell, so a device finds the players around it without looking at
 * everyone: with every listener's walkie playing around it (D21), that was one look per listener per player, for every
 * frame (DESIGN.md 6.5). Built from the frame's own position snapshot, on the thread that routes it, and on the server
 * thread from the online players when a radio publish works out who hears whom.
 */
public final class Nearby {

    private static final int CELL = 16;

    private record Cell(int dimension, int x, int z) {}

    private final Map<Cell, List<PlayerSnapshot>> cells = new HashMap<>();

    public Nearby(Collection<PlayerSnapshot> players) {
        for (PlayerSnapshot p : players) {
            cells
                .computeIfAbsent(
                    new Cell(p.getDimensionId(), cellOf(p.getX()), cellOf(p.getZ())),
                    c -> new ArrayList<>())
                .add(p);
        }
    }

    private static int cellOf(double coordinate) {
        return Math.floorDiv((int) Math.floor(coordinate), CELL);
    }

    /**
     * The players of {@code dimension} in the cells within {@code range} of {@code (x, z)}: everyone that close, and
     * some further, so the caller still checks the distance.
     */
    public List<PlayerSnapshot> around(int dimension, double x, double z, double range) {
        List<PlayerSnapshot> found = new ArrayList<>();
        int minX = cellOf(x - range), maxX = cellOf(x + range);
        int minZ = cellOf(z - range), maxZ = cellOf(z + range);
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                List<PlayerSnapshot> in = cells.get(new Cell(dimension, cx, cz));
                if (in != null) found.addAll(in);
            }
        }
        return found;
    }
}
