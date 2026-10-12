package com.enn3developer.becom.logic.radio;

/**
 * The radio simulation's link budget (DESIGN.md 6.1):
 * {@code SNR = B - L_dist - L_terrain - L_cover(a) - L_cover(b) - L_weather - N}.
 * <p>
 * Pure arithmetic over numbers the caller gathers: terrain comes as heights sampled along the path (from the height
 * cache, DESIGN.md 6.2), cover as each end's depth below the surface. Nothing here touches a world.
 */
public final class RadioModel {

    // ITU-R P.526's single knife-edge: no loss once the line clears the edge by this much.
    private static final double KNIFE_EDGE_CLEAR = -0.78;

    private final double terrainCap;
    private final double coverCap;
    private final double noSkyNoise;

    /**
     * @param terrainCap the most terrain can cost, in dB (DESIGN.md 17)
     * @param coverCap   the most cover can cost at one end, in dB
     * @param noSkyNoise the noise floor in a dimension without a sky (the Nether), in dB
     */
    public RadioModel(double terrainCap, double coverCap, double noSkyNoise) {
        this.terrainCap = terrainCap;
        this.coverCap = coverCap;
        this.noSkyNoise = noSkyNoise;
    }

    /** {@code 20 log10(d)}, with d at least one block. */
    public static double distanceLoss(double distance) {
        return 20 * Math.log10(Math.max(1, distance));
    }

    /** The distance at which {@code budget} falls to {@code snr} on open ground. */
    public static double rangeFor(double budget, double snr) {
        return Math.pow(10, (budget - snr) / 20);
    }

    /**
     * The diffraction parameter for an obstacle rising {@code h} blocks above the line between the ends (negative
     * when the line clears it), {@code d1} and {@code d2} blocks from each end, at wavelength {@code lambda}.
     */
    public static double nu(double h, double d1, double d2, double lambda) {
        if (d1 <= 0 || d2 <= 0) return Double.NEGATIVE_INFINITY;
        return h * Math.sqrt(2 * (d1 + d2) / (lambda * d1 * d2));
    }

    /** Single knife-edge loss for {@code nu}, in dB, capped. */
    public double knifeEdge(double nu) {
        if (nu <= KNIFE_EDGE_CLEAR) return 0;
        double v = nu - 0.1;
        return Math.min(terrainCap, 6.9 + 20 * Math.log10(Math.sqrt(v * v + 1) + v));
    }

    /** An end {@code depth} blocks below the surface loses {@code perBlock} dB a block, capped. */
    public double coverLoss(double depth, double perBlock) {
        if (depth <= 0) return 0;
        return Math.min(coverCap, depth * perBlock);
    }

    public double noSkyNoise() {
        return noSkyNoise;
    }

    /**
     * The worst obstacle on the straight path from {@code a} to {@code b}, as the largest diffraction parameter over
     * height-cache samples every {@code step} blocks (DESIGN.md 6.4). Samples in either end's own cell are skipped:
     * that cell holds the end itself, and what stands over an end is its cover. So are samples no higher than
     * {@code floor}, the ground the lower end stands on: open ground isn't an obstacle (X20). Negative infinity when
     * nothing lies between.
     */
    public static double worstNuAlong(Terrain terrain, double ax, double ay, double az, double bx, double by, double bz,
        int step, double lambda, double floor) {
        double dx = bx - ax;
        double dz = bz - az;
        double length = Math.sqrt(dx * dx + dz * dz);
        double worst = Double.NEGATIVE_INFINITY;
        if (length <= step) return worst;
        int aCellX = Terrain.cellOf(ax);
        int aCellZ = Terrain.cellOf(az);
        int bCellX = Terrain.cellOf(bx);
        int bCellZ = Terrain.cellOf(bz);
        for (double d1 = step; d1 < length; d1 += step) {
            double x = ax + dx * d1 / length;
            double z = az + dz * d1 / length;
            int cellX = Terrain.cellOf(x);
            int cellZ = Terrain.cellOf(z);
            if ((cellX == aCellX && cellZ == aCellZ) || (cellX == bCellX && cellZ == bCellZ)) continue;
            double h = terrain.heightAt((int) Math.floor(x), (int) Math.floor(z));
            if (h <= floor) continue;
            double lineY = ay + (by - ay) * (d1 / length);
            worst = Math.max(worst, nu(h - lineY, d1, length - d1, lambda));
        }
        return worst;
    }

    /**
     * One hop's losses (DESIGN.md 6.1). In a dimension without a sky, terrain and cover are left out and the noise
     * floor rises instead.
     *
     * @param distance the 3D distance between the ends
     * @param nu       the worst obstacle's diffraction parameter
     * @param depthA   how far the first end is below the surface (negative above it)
     * @param depthB   the same for the second end
     * @param weather  the weather loss, already worked out for the band
     */
    public Losses losses(double budget, double distance, double nu, double depthA, double depthB, Band band,
        double weather, boolean noSky) {
        if (noSky) return new Losses(budget, distanceLoss(distance), 0, 0, 0, weather, noSkyNoise);
        return new Losses(
            budget,
            distanceLoss(distance),
            knifeEdge(nu),
            coverLoss(depthA, band.coverPerBlock()),
            coverLoss(depthB, band.coverPerBlock()),
            weather,
            0);
    }

    /** Every term of one hop's budget, so the Signal Analyzer can say what costs the most (DESIGN.md 6.6). */
    public record Losses(double budget, double distance, double terrain, double coverA, double coverB, double weather,
        double noise) {

        public double snr() {
            return budget - distance - terrain - coverA - coverB - weather - noise;
        }

        /** Which loss is biggest. Cover counts both ends together. */
        public Loss biggest() {
            Loss loss = Loss.DISTANCE;
            double most = distance;
            if (terrain > most) {
                loss = Loss.TERRAIN;
                most = terrain;
            }
            if (coverA + coverB > most) {
                loss = Loss.COVER;
                most = coverA + coverB;
            }
            if (weather > most) {
                loss = Loss.WEATHER;
                most = weather;
            }
            if (noise > most) loss = Loss.NOISE;
            return loss;
        }
    }

    /** The losses the analyzer names (DESIGN.md 6.6). Ordinals go on the wire, so constants are only appended. */
    public enum Loss {

        DISTANCE,
        TERRAIN,
        COVER,
        WEATHER,
        NOISE;

        private static final Loss[] VALUES = values();

        public static Loss byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : DISTANCE;
        }
    }
}
