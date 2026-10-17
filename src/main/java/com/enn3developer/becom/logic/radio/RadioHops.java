package com.enn3developer.becom.logic.radio;

/**
 * The three kinds of radio hop, each worked out from what the caller gathered: a device's
 * link to an antenna scans the antenna's survey, a direct link and a link between antennas sample the height cache.
 * Pure arithmetic over {@link RadioModel}; nothing here touches a world.
 */
public final class RadioHops {

    private final RadioModel model;
    private final int step;

    /** @param step blocks between height-cache samples on a direct link */
    public RadioHops(RadioModel model, int step) {
        this.model = model;
        this.step = step;
    }

    /**
     * One end of a hop: where its antenna or the listener's ears are, how far below the surface that is,
     * and the height of the ground it stands on: a player's feet, a block's base, an
     * antenna's casings.
     */
    public record End(double x, double y, double z, double depth, double ground) {

        public double distanceTo(End other) {
            double dx = other.x - x;
            double dy = other.y - y;
            double dz = other.z - z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /**
     * A device's link to an antenna, in both directions. The worst obstacle comes
     * from the bearing of the antenna's survey the device lies on.
     */
    public RadioModel.Losses access(Survey survey, End antenna, double budget, End device, Band band, double weather,
        boolean noSky) {
        double nu = noSky ? Double.NEGATIVE_INFINITY
            : survey.worstNu(
                antenna.y(),
                device.x(),
                device.y(),
                device.z(),
                band.lambda(),
                Math.min(antenna.ground(), device.ground()));
        return model
            .losses(budget, antenna.distanceTo(device), nu, antenna.depth(), device.depth(), band, weather, noSky);
    }

    /**
     * A direct link between two radios, or a link between two antennas: the worst obstacle on the straight path,
     * sampled from the height cache.
     */
    public RadioModel.Losses direct(Terrain terrain, End a, End b, double budget, Band band, double weather,
        boolean noSky) {
        double nu = noSky ? Double.NEGATIVE_INFINITY
            : RadioModel.worstNuAlong(
                terrain,
                a.x(),
                a.y(),
                a.z(),
                b.x(),
                b.y(),
                b.z(),
                step,
                band.lambda(),
                Math.min(a.ground(), b.ground()));
        return model.losses(budget, a.distanceTo(b), nu, a.depth(), b.depth(), band, weather, noSky);
    }
}
