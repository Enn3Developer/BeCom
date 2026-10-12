package com.enn3developer.becom.logic.phone;

/**
 * The Telephone's handset cord (DESIGN.md 5.6, X10): how far a player is from the phone, as the gap between the phone's
 * block and the player's body. Measuring to the body rather than the feet keeps a phone on a desk, or on a shelf at
 * head height, as easy to hold as one on the floor.
 */
public final class Cord {

    // A player's hitbox: 0.6 wide, 1.8 tall, standing on its feet position.
    private static final double HALF_WIDTH = 0.3;
    private static final double HEIGHT = 1.8;

    private Cord() {}

    /** The gap, in blocks, between the block at {@code (bx, by, bz)} and a player standing at {@code (x, feetY, z)}. */
    public static double gap(int bx, int by, int bz, double x, double feetY, double z) {
        double dx = Math.max(0, Math.abs(x - (bx + 0.5)) - 0.5 - HALF_WIDTH);
        double dz = Math.max(0, Math.abs(z - (bz + 0.5)) - 0.5 - HALF_WIDTH);
        double dy = Math.max(0, Math.max(by - (feetY + HEIGHT), feetY - (by + 1)));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static boolean within(int bx, int by, int bz, double x, double feetY, double z, double reach) {
        return gap(bx, by, bz, x, feetY, z) <= reach;
    }
}
