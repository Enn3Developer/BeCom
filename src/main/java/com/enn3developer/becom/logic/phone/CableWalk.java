package com.enn3developer.becom.logic.phone;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.enn3developer.becom.logic.net.BlockPos;

/**
 * Follows Phone Cable out from a Line Terminal (DESIGN.md 5.2, 5.5): a breadth-first walk over the cable touching it,
 * collecting the phones on it. A terminal takes up to {@code maxCables} blocks of cable in all; a bigger run serves
 * nothing, so a solid blob of cable can't make every scan read thousands of blocks, and which phone a terminal finds
 * never depends on walk order.
 */
public final class CableWalk {

    private CableWalk() {}

    public enum Block {
        CABLE,
        PHONE,
        OTHER
    }

    /** What stands at a position; must answer OTHER for anything it can't see, like an unloaded chunk. */
    @FunctionalInterface
    public interface Probe {

        Block at(int x, int y, int z);
    }

    /** The phones found, by packed position, or {@code overflow} when the cable ran past the limit. */
    public record Result(List<Long> phones, int cables, boolean overflow) {}

    public static Result walk(int x, int y, int z, int maxCables, Probe probe) {
        List<Long> phones = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        long start = BlockPos.pack(x, y, z);
        seen.add(start);
        queue.add(start);
        int cables = 0;
        while (!queue.isEmpty()) {
            long pos = queue.poll();
            for (int side = 0; side < 6; side++) {
                int nx = BlockPos.x(pos) + DX[side];
                int ny = BlockPos.y(pos) + DY[side];
                int nz = BlockPos.z(pos) + DZ[side];
                long next = BlockPos.pack(nx, ny, nz);
                if (!seen.add(next)) continue;
                switch (probe.at(nx, ny, nz)) {
                    case PHONE -> phones.add(next);
                    case CABLE -> {
                        if (++cables > maxCables) return new Result(List.of(), cables, true);
                        queue.add(next);
                    }
                    case OTHER -> {}
                }
            }
        }
        return new Result(phones, cables, false);
    }

    private static final int[] DX = { 0, 0, 0, 0, -1, 1 };
    private static final int[] DY = { -1, 1, 0, 0, 0, 0 };
    private static final int[] DZ = { 0, 0, -1, 1, 0, 0 };
}
