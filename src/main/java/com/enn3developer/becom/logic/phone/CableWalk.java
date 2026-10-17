package com.enn3developer.becom.logic.phone;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.CableTier;

/**
 * Follows cable out from a Line Terminal (DESIGN.md 5.2, 5.5): a breadth-first walk over the cable touching it,
 * collecting the devices within {@code maxLength} blocks of cable, each with the length of its shortest run and the
 * poorest cable on it. A run of more than {@code maxCables} blocks in all serves nothing, so a solid blob of cable
 * can't make every scan read thousands of blocks, and what a terminal finds never depends on walk order.
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

        /** The tier of the cable at a position {@link #at} called cable. */
        default CableTier tier(int x, int y, int z) {
            return CableTier.PHONE;
        }
    }

    /** A device the cable reaches: where, how many blocks of cable lead to it, and the poorest of them. */
    public record Found(long pos, int length, CableTier cable) {}

    /**
     * The devices found, nearest first, or {@code overflow} when the cable ran past the limit. A device touching the
     * terminal itself has length 0 and the best cable.
     */
    public record Result(List<Found> devices, int cables, boolean overflow) {

        /** The devices' packed positions, nearest first. */
        public List<Long> phones() {
            List<Long> positions = new ArrayList<>();
            for (Found found : devices) positions.add(found.pos());
            return positions;
        }
    }

    public static Result walk(int x, int y, int z, int maxLength, int maxCables, Probe probe) {
        List<Found> phones = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        // Each entry: a packed position, how many cables lead to it, and the poorest of them by ordinal.
        ArrayDeque<long[]> queue = new ArrayDeque<>();
        long start = BlockPos.pack(x, y, z);
        seen.add(start);
        CableTier[] tiers = CableTier.values();
        queue.add(new long[] { start, 0, tiers.length - 1 });
        int cables = 0;
        while (!queue.isEmpty()) {
            long[] entry = queue.poll();
            long pos = entry[0];
            long length = entry[1];
            int poorest = (int) entry[2];
            for (int side = 0; side < 6; side++) {
                int nx = BlockPos.x(pos) + DX[side];
                int ny = BlockPos.y(pos) + DY[side];
                int nz = BlockPos.z(pos) + DZ[side];
                long next = BlockPos.pack(nx, ny, nz);
                if (!seen.add(next)) continue;
                switch (probe.at(nx, ny, nz)) {
                    case PHONE -> phones.add(new Found(next, (int) length, tiers[poorest]));
                    case CABLE -> {
                        if (length >= maxLength) continue;
                        if (++cables > maxCables) return new Result(List.of(), cables, true);
                        int tier = Math.min(
                            poorest,
                            probe.tier(nx, ny, nz)
                                .ordinal());
                        queue.add(new long[] { next, length + 1, tier });
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
