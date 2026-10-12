package com.enn3developer.becom.logic.radio;

import com.enn3developer.becom.logic.WidestPath;

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
     * antennas are {@code access} (negative infinity where it reaches none).
     */
    public double[] reach(double[] access) {
        int source = antennas;
        WidestPath paths = new WidestPath(antennas + 1);
        for (int a = 0; a < antennas; a++) {
            if (access[a] > Double.NEGATIVE_INFINITY) paths.addEdge(source, a, access[a], null);
            for (int b = 0; b < antennas; b++) {
                if (a != b && links[a][b] > Double.NEGATIVE_INFINITY) paths.addEdge(a, b, links[a][b], null);
            }
        }
        WidestPath.Result result = paths.solve(source);
        double[] reach = new double[antennas];
        for (int a = 0; a < antennas; a++) reach[a] = result.width(a);
        return reach;
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
