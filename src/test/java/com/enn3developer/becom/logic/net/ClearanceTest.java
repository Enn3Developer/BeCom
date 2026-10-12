package com.enn3developer.becom.logic.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** D20: the straight line between two heads must be free of solid blocks. */
class ClearanceTest {

    private final Set<Long> solid = new HashSet<>();
    private final Set<Long> visited = new HashSet<>();
    private int unloadedBelowX = Integer.MIN_VALUE;

    private Clearance.Probe probe(int x, int y, int z) {
        visited.add(BlockPos.pack(x, y, z));
        if (x < unloadedBelowX) return Clearance.Probe.UNLOADED;
        return solid.contains(BlockPos.pack(x, y, z)) ? Clearance.Probe.SOLID : Clearance.Probe.AIR;
    }

    @Test
    void openAirIsClearAndTheHeadsThemselvesDontCount() {
        long a = BlockPos.pack(0, 100, 0);
        long b = BlockPos.pack(40, 110, 30);
        solid.add(a);
        solid.add(b);
        assertEquals(Clearance.Result.CLEAR, Clearance.check(a, b, this::probe));
        assertFalse(visited.contains(a));
        assertFalse(visited.contains(b));
    }

    @Test
    void aBlockOnTheLineBlocksIt() {
        long a = BlockPos.pack(0, 100, 0);
        long b = BlockPos.pack(20, 100, 0);
        solid.add(BlockPos.pack(10, 100, 0));
        assertEquals(Clearance.Result.BLOCKED, Clearance.check(a, b, this::probe));
        assertEquals(Clearance.Result.BLOCKED, Clearance.check(b, a, this::probe));
    }

    @Test
    void aBlockBesideTheLineDoesNot() {
        long a = BlockPos.pack(0, 100, 0);
        long b = BlockPos.pack(20, 100, 0);
        solid.add(BlockPos.pack(10, 101, 0));
        solid.add(BlockPos.pack(10, 100, 1));
        assertEquals(Clearance.Result.CLEAR, Clearance.check(a, b, this::probe));
    }

    @Test
    void diagonalLineVisitsAConnectedRunOfBlocks() {
        long a = BlockPos.pack(0, 64, 0);
        long b = BlockPos.pack(30, 80, -25);
        Clearance.check(a, b, this::probe);
        // Every step moves one axis by one, so the run is at least the Manhattan distance minus the two ends.
        assertTrue(visited.size() >= 30 + 16 + 25 - 1);
    }

    @Test
    void unloadedPartsLeaveTheResultUnknownUnlessSomethingLoadedBlocks() {
        long a = BlockPos.pack(0, 100, 0);
        long b = BlockPos.pack(40, 100, 0);
        unloadedBelowX = 20;
        assertEquals(Clearance.Result.UNKNOWN, Clearance.check(a, b, this::probe));
        solid.add(BlockPos.pack(30, 100, 0));
        assertEquals(Clearance.Result.BLOCKED, Clearance.check(a, b, this::probe));
    }
}
