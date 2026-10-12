package com.enn3developer.becom.logic.radio;

/**
 * An antenna's survey (DESIGN.md 6.3): for each bearing, the terrain height every few blocks out to the antenna's
 * range. A device's link to the antenna then scans one bearing's profile, up to the device, for the obstacle with the
 * largest diffraction parameter toward the device's real eye height. The survey keeps whole profiles because which
 * obstacle is worst depends on where the far end is.
 * <p>
 * Steps that land in the antenna's own height-cache cell are stored as NaN and never count: that cell's height is the
 * antenna itself. Steps in the device's own cell are skipped too, for the same reason; what stands over the device is
 * its cover.
 */
public final class Survey {

    private final double originX;
    private final double originZ;
    private final int bearings;
    private final int step;
    private final double[][] heights;

    /**
     * @param originX the antenna's x, at the centre of its block
     * @param originZ the antenna's z, at the centre of its block
     * @param heights {@code heights[bearing][k]}: terrain height at {@code (k + 1) * step} blocks out, NaN where unused
     */
    public Survey(double originX, double originZ, int bearings, int step, double[][] heights) {
        this.originX = originX;
        this.originZ = originZ;
        this.bearings = bearings;
        this.step = step;
        this.heights = heights;
    }

    /** Surveys {@code steps} steps on every bearing at once; see {@link Builder} for a survey spread over ticks. */
    public static Survey of(Terrain terrain, double originX, double originZ, int bearings, int step, int steps) {
        Builder builder = new Builder(originX, originZ, bearings, step, steps);
        while (!builder.done()) builder.next(terrain);
        return builder.build();
    }

    public int bearings() {
        return bearings;
    }

    public int step() {
        return step;
    }

    public int steps() {
        return heights.length == 0 ? 0 : heights[0].length;
    }

    public double height(int bearing, int k) {
        return heights[bearing][k];
    }

    /** The bearing index for a horizontal offset from the antenna: 0 is east, counting toward south. */
    public static int bearingOf(double dx, double dz, int bearings) {
        double angle = Math.atan2(dz, dx);
        if (angle < 0) angle += 2 * Math.PI;
        return (int) Math.round(angle / (2 * Math.PI / bearings)) % bearings;
    }

    /** The unit direction of bearing {@code b}: {x, z}. */
    public static double[] direction(int b, int bearings) {
        double angle = b * 2 * Math.PI / bearings;
        return new double[] { Math.cos(angle), Math.sin(angle) };
    }

    /**
     * The largest diffraction parameter between the antenna, at height {@code antennaY}, and a device at
     * {@code (x, y, z)}, scanning the profile of the bearing it lies on. Negative infinity when nothing lies between.
     */
    public double worstNu(double antennaY, double x, double y, double z, double lambda) {
        double dx = x - originX;
        double dz = z - originZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        int bearing = bearingOf(dx, dz, bearings);
        double[] direction = direction(bearing, bearings);
        int deviceCellX = Terrain.cellOf(x);
        int deviceCellZ = Terrain.cellOf(z);
        double[] profile = heights[bearing];
        int n = Math.min(profile.length, (int) Math.floor(horizontal / step));
        double worst = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < n; k++) {
            double h = profile[k];
            if (Double.isNaN(h)) continue;
            double d1 = (k + 1) * step;
            double d2 = horizontal - d1;
            if (d2 <= 0) break;
            if (Terrain.cellOf(originX + direction[0] * d1) == deviceCellX
                && Terrain.cellOf(originZ + direction[1] * d1) == deviceCellZ) continue;
            double lineY = antennaY + (y - antennaY) * (d1 / horizontal);
            worst = Math.max(worst, RadioModel.nu(h - lineY, d1, d2, lambda));
        }
        return worst;
    }

    /** A survey taken a bearing at a time, so it can be spread over ticks within the survey budget (DESIGN.md 6.5). */
    public static final class Builder {

        private final double originX;
        private final double originZ;
        private final int bearings;
        private final int step;
        private final double[][] heights;
        private int next;

        public Builder(double originX, double originZ, int bearings, int step, int steps) {
            this.originX = originX;
            this.originZ = originZ;
            this.bearings = bearings;
            this.step = step;
            this.heights = new double[bearings][steps];
        }

        public boolean done() {
            return next >= bearings;
        }

        /** Surveys the next bearing. */
        public void next(Terrain terrain) {
            int b = next++;
            double[] direction = direction(b, bearings);
            int ownCellX = Terrain.cellOf(originX);
            int ownCellZ = Terrain.cellOf(originZ);
            for (int k = 0; k < heights[b].length; k++) {
                double d = (k + 1) * step;
                double x = originX + direction[0] * d;
                double z = originZ + direction[1] * d;
                if (Terrain.cellOf(x) == ownCellX && Terrain.cellOf(z) == ownCellZ) {
                    heights[b][k] = Double.NaN;
                } else {
                    heights[b][k] = terrain.heightAt((int) Math.floor(x), (int) Math.floor(z));
                }
            }
        }

        public Survey build() {
            return new Survey(originX, originZ, bearings, step, heights);
        }
    }
}
