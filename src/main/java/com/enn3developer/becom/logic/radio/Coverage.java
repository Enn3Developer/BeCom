package com.enn3developer.becom.logic.radio;

/**
 * An antenna's coverage map (DESIGN.md 6.6, 11.4): the SNR a player standing on the ground would get from it, on a
 * square grid around the antenna as far as a link at Low still holds (X22). Worked out from the antenna's survey, as a
 * device's access link is (DESIGN.md 6.3), so it costs one profile scan per cell.
 */
public final class Coverage {

    /** Cells on each side of the map. */
    public static final int CELLS = 32;
    /** A cell out of reach, or with nothing to say, in the whole-dB values the map is sent as. */
    public static final byte NONE = Byte.MIN_VALUE;
    // A player's eyes above the ground they stand on.
    private static final double EYE_HEIGHT = 1.62;

    private Coverage() {}

    /**
     * The map, row by row from the north-west: each cell's SNR in whole dB, clamped to a byte, or {@link #NONE} out of
     * reach.
     *
     * @param range how far from the antenna the map reaches, in blocks: half its side
     */
    public static byte[] of(RadioHops hops, Survey survey, RadioHops.End antenna, double budget, Terrain terrain,
        Band band, boolean noSky, double range) {
        byte[] map = new byte[CELLS * CELLS];
        double cell = 2 * range / CELLS;
        for (int row = 0; row < CELLS; row++) {
            for (int col = 0; col < CELLS; col++) {
                double x = antenna.x() - range + (col + 0.5) * cell;
                double z = antenna.z() - range + (row + 0.5) * cell;
                double ground = terrain.heightAt((int) Math.floor(x), (int) Math.floor(z));
                RadioHops.End standing = new RadioHops.End(x, ground + EYE_HEIGHT, z, -EYE_HEIGHT, ground);
                if (antenna.distanceTo(standing) > range) {
                    map[row * CELLS + col] = NONE;
                    continue;
                }
                double snr = hops.access(survey, antenna, budget, standing, band, 0, noSky)
                    .snr();
                map[row * CELLS + col] = clamp(snr);
            }
        }
        return map;
    }

    private static byte clamp(double snr) {
        if (Double.isNaN(snr) || snr <= NONE) return NONE;
        return (byte) Math.min(Byte.MAX_VALUE, Math.round(snr));
    }
}
