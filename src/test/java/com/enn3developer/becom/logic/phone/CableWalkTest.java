package com.enn3developer.becom.logic.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.net.BlockPos;

/** DESIGN.md 5.5: up to 32 blocks of Phone Cable between a terminal and its phone, and no more work than that. */
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
}
