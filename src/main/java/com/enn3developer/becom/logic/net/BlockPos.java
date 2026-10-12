package com.enn3developer.becom.logic.net;

/**
 * Packs block coordinates into one long, the way later Minecraft versions do: 26 bits of x, 12 of y, 26 of z. It's
 * the key nodes are saved and looked up by, so the layout never changes.
 */
public final class BlockPos {

    private static final int XZ_BITS = 26;
    private static final int Y_BITS = 12;
    private static final long XZ_MASK = (1L << XZ_BITS) - 1;
    private static final long Y_MASK = (1L << Y_BITS) - 1;
    private static final int Z_SHIFT = 0;
    private static final int Y_SHIFT = XZ_BITS;
    private static final int X_SHIFT = XZ_BITS + Y_BITS;

    private BlockPos() {}

    public static long pack(int x, int y, int z) {
        return ((x & XZ_MASK) << X_SHIFT) | ((y & Y_MASK) << Y_SHIFT) | ((z & XZ_MASK) << Z_SHIFT);
    }

    public static int x(long pos) {
        return (int) (pos << (64 - X_SHIFT - XZ_BITS) >> (64 - XZ_BITS));
    }

    public static int y(long pos) {
        return (int) (pos << (64 - Y_SHIFT - Y_BITS) >> (64 - Y_BITS));
    }

    public static int z(long pos) {
        return (int) (pos << (64 - Z_SHIFT - XZ_BITS) >> (64 - XZ_BITS));
    }

    /** Distance between two block centres. */
    public static double distance(long a, long b) {
        double dx = x(a) - x(b);
        double dy = y(a) - y(b);
        double dz = z(a) - z(b);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static String toString(long pos) {
        return x(pos) + ", " + y(pos) + ", " + z(pos);
    }
}
