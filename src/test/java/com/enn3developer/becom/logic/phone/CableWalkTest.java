package com.enn3developer.becom.logic.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.CableTier;

/** The devices within a walk's length of cable from a terminal, and no more work than that. */
class CableWalkTest {

    private final Map<Long, CableWalk.Block> world = new HashMap<>();
    private int probes;

    private CableWalk.Result walk() {
        return CableWalk.walk(0, 64, 0, 32, 128, (x, y, z) -> {
            probes++;
            return world.getOrDefault(BlockPos.pack(x, y, z), CableWalk.Block.OTHER);
        });
    }

    private void cableLine(int length) {
        for (int i = 1; i <= length; i++) world.put(BlockPos.pack(i, 64, 0), CableWalk.Block.CABLE);
        world.put(BlockPos.pack(length + 1, 64, 0), CableWalk.Block.PHONE);
    }

    @Test
    void findsThePhoneAtTheEndOf32Cables() {
        cableLine(32);
        CableWalk.Result result = walk();
        assertFalse(result.overflow());
        assertEquals(List.of(BlockPos.pack(33, 64, 0)), result.phones());
    }

    @Test
    void aPhone33CablesAwayIsOutOfReach() {
        cableLine(33);
        CableWalk.Result result = walk();
        assertFalse(result.overflow());
        assertTrue(
            result.phones()
                .isEmpty());
    }

    @Test
    void aSideBranchDoesntCutThePhoneOff() {
        // 32 cables to the phone, and a stub of 10 more off the side: the phone is still within 32 of cable.
        cableLine(32);
        for (int z = 1; z <= 10; z++) world.put(BlockPos.pack(5, 64, z), CableWalk.Block.CABLE);
        CableWalk.Result result = walk();
        assertFalse(result.overflow());
        assertEquals(
            1,
            result.phones()
                .size());
    }

    @Test
    void moreThan128CablesInAllIsTooMuch() {
        // A comb: a 32-long spine with long teeth, every cable within 32 steps, 129 cables in all.
        int cables = 0;
        for (int x = 1; x <= 16 && cables < 129; x++) {
            world.put(BlockPos.pack(x, 64, 0), CableWalk.Block.CABLE);
            cables++;
            for (int z = 1; z <= 12 && cables < 129; z++) {
                world.put(BlockPos.pack(x, 64, z), CableWalk.Block.CABLE);
                cables++;
            }
        }
        assertTrue(walk().overflow());
    }

    @Test
    void tooMuchCableStillServesTheNearerDevices() {
        // A Telephone 2 cables out, then a blob of cable past it that runs over the limit.
        world.put(BlockPos.pack(1, 64, 0), CableWalk.Block.CABLE);
        world.put(BlockPos.pack(2, 64, 0), CableWalk.Block.CABLE);
        world.put(BlockPos.pack(2, 65, 0), CableWalk.Block.PHONE);
        for (int x = 3; x <= 12; x++) for (int y = 60; y < 70; y++)
            for (int z = -5; z < 5; z++) world.put(BlockPos.pack(x, y, z), CableWalk.Block.CABLE);
        CableWalk.Result result = walk();
        assertTrue(result.overflow());
        assertEquals(List.of(BlockPos.pack(2, 65, 0)), result.phones());
    }

    @Test
    void aPhoneTouchingTheTerminalNeedsNoCable() {
        world.put(BlockPos.pack(0, 64, 1), CableWalk.Block.PHONE);
        assertEquals(
            1,
            walk().phones()
                .size());
    }

    @Test
    void aBlobOfCableStopsEarly() {
        // A 30 x 30 x 30 cube of cable next to the terminal.
        for (int x = 1; x <= 30; x++) for (int y = 50; y < 80; y++)
            for (int z = -15; z < 15; z++) world.put(BlockPos.pack(x, y, z), CableWalk.Block.CABLE);
        CableWalk.Result result = walk();
        assertTrue(result.overflow());
        // At most 129 cables are walked, each probing its 6 sides.
        assertTrue(probes <= 6 * 130, probes + " probes");
    }

    @Test
    void devicesComeNearestFirstWithTheirRunAndPoorestCable() {
        // Fibre out to x = 10, Phone Cable after it; a Telephone above x = 3 and another at the end.
        Map<Long, CableTier> tiers = new HashMap<>();
        for (int x = 1; x <= 20; x++) {
            world.put(BlockPos.pack(x, 64, 0), CableWalk.Block.CABLE);
            tiers.put(BlockPos.pack(x, 64, 0), x <= 10 ? CableTier.FIBRE : CableTier.PHONE);
        }
        world.put(BlockPos.pack(21, 64, 0), CableWalk.Block.PHONE);
        world.put(BlockPos.pack(3, 65, 0), CableWalk.Block.PHONE);
        CableWalk.Result result = CableWalk.walk(0, 64, 0, 32, 128, new CableWalk.Probe() {

            @Override
            public CableWalk.Block at(int x, int y, int z) {
                return world.getOrDefault(BlockPos.pack(x, y, z), CableWalk.Block.OTHER);
            }

            @Override
            public CableTier tier(int x, int y, int z) {
                return tiers.get(BlockPos.pack(x, y, z));
            }
        });
        assertEquals(
            List.of(
                new CableWalk.Found(BlockPos.pack(3, 65, 0), 3, CableTier.FIBRE),
                new CableWalk.Found(BlockPos.pack(21, 64, 0), 20, CableTier.PHONE)),
            result.devices());
    }
}
