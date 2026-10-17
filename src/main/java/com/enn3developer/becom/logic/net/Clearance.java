package com.enn3developer.becom.logic.net;

/**
 * Whether the straight line between two pylon heads is free of solid blocks. Walks every block the line between
 * the two block centres passes through (Amanatides and Woo's voxel traversal), leaving out the two heads themselves.
 */
public final class Clearance {

    private Clearance() {}

    public enum Result {
        CLEAR,
        BLOCKED,
        /** Part of the line runs through unloaded chunks, so the link keeps its last state. */
        UNKNOWN
    }

    public enum Probe {
        AIR,
        SOLID,
        UNLOADED
    }

    @FunctionalInterface
    public interface World {

        Probe probe(int x, int y, int z);
    }

    public static Result check(long a, long b, World world) {
        int x = BlockPos.x(a), y = BlockPos.y(a), z = BlockPos.z(a);
        int ex = BlockPos.x(b), ey = BlockPos.y(b), ez = BlockPos.z(b);
        double dx = ex - x, dy = ey - y, dz = ez - z;
        int stepX = Integer.signum((int) dx), stepY = Integer.signum((int) dy), stepZ = Integer.signum((int) dz);
        // Starting from a block centre, the first boundary on each axis is half a block away.
        double tDeltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
        double tDeltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
        double tDeltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz);
        double tMaxX = tDeltaX / 2, tMaxY = tDeltaY / 2, tMaxZ = tDeltaZ / 2;
        boolean unknown = false;
        int guard = Math.abs(ex - x) + Math.abs(ey - y) + Math.abs(ez - z) + 1;
        for (int i = 0; i < guard; i++) {
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            if (x == ex && y == ey && z == ez) break;
            Probe probe = world.probe(x, y, z);
            if (probe == Probe.SOLID) return Result.BLOCKED;
            if (probe == Probe.UNLOADED) unknown = true;
        }
        return unknown ? Result.UNKNOWN : Result.CLEAR;
    }
}
