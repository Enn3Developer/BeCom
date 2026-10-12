package com.enn3developer.becom.logic.radio;

/**
 * The antennas of one dimension on one band, and the links between them (DESIGN.md 7): a radio signal can go from a
 * device up to any antenna it reaches, along the antenna links, and down to another device. The path's SNR is its
 * weakest hop (DESIGN.md 4.1), so the best path is a widest path.
 * <p>
 * Antennas are dense ints the caller numbers. Not thread-safe; built on the server thread from the latest surveys.
 */
public final class AntennaMesh {

    private final int antennas;
    private final double[][] links;

    public AntennaMesh(int antennas) {
        this.antennas = antennas;
        this.links = new double[antennas][antennas];
        for (double[] row : links) java.util.Arrays.fill(row, Double.NEGATIVE_INFINITY);
    }

    public int antennas() {
        return antennas;
    }

    /** Sets the link between two antennas, both ways. Links below Low should be left out by the caller. */
    public void link(int a, int b, double snr) {
        links[a][b] = snr;
        links[b][a] = snr;
    }

    public double linkSnr(int a, int b) {
        return links[a][b];
    }

    /**
     * For each antenna, the best SNR a signal arrives there with, starting from a device whose access links to the
     * antennas are {@code access} (negative infinity where it reaches none). A widest path over the link matrix as it
     * stands, with nothing built per call: a radio publish asks this for every keyed radio.
     */
    public double[] reach(double[] access) {
        // Each antenna starts at its own access link; the closest unsettled antenna then passes its width on. A NaN,
        // from
        // a device whose position isn't a number, counts as no link, and only a wider path replaces a width, so none
        // spreads: the graph this replaced left NaN edges out the same way.
        double[] best = new double[antennas];
        for (int a = 0; a < antennas; a++) best[a] = Double.isNaN(access[a]) ? Double.NEGATIVE_INFINITY : access[a];
        boolean[] settled = new boolean[antennas];
        for (int round = 0; round < antennas; round++) {
            int next = -1;
            for (int a = 0; a < antennas; a++) {
                if (!settled[a] && (next < 0 || best[a] > best[next])) next = a;
            }
            if (best[next] == Double.NEGATIVE_INFINITY) break;
            settled[next] = true;
            double[] row = links[next];
            for (int b = 0; b < antennas; b++) {
                double via = Math.min(best[next], row[b]);
                if (!settled[b] && via > best[b]) best[b] = via;
            }
        }
        return best;
    }

    /**
     * The best SNR from a device to another, through the antennas: the strongest antenna to come down from, given
     * what {@link #reach} found and the receiving device's own {@code access} links.
     */
    public static double through(double[] reach, double[] access) {
        double best = Double.NEGATIVE_INFINITY;
        for (int a = 0; a < reach.length; a++) best = Math.max(best, Math.min(reach[a], access[a]));
        return best;
    }
}
