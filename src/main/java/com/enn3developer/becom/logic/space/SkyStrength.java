package com.enn3developer.becom.logic.space;

import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.radio.Band;
import com.enn3developer.becom.logic.radio.RadioModel;

/**
 * How strong the satellites over a dimension make its sky hops (DESIGN.md 9.1). With {@code n} satellites in orbit, at
 * least as many as Low needs, the SNR is {@code Low + (Max - Low) * log10(n / nLow) / log10(nMax / nLow)}. With
 * DESIGN.md's numbers that's {@code 6 + 18 * log10(n / 5) / log10(20)}: Low at 5, Medium at 14, High at 37 and Max at
 * 100. More than Max's count add nothing (X38). A sky hop then loses its ground end's cover and the weather (DESIGN.md
 * 6.1).
 */
public final class SkyStrength {

    private final int satellitesLow;
    private final int satellitesMax;
    private final double levelLow;
    private final double levelMax;
    private final RadioModel model;

    public SkyStrength(int satellitesLow, int satellitesMax, double levelLow, double levelMax, RadioModel model) {
        if (satellitesLow < 1 || satellitesMax <= satellitesLow) {
            throw new IllegalArgumentException(
                "satellites for Low and Max must rise: " + satellitesLow + ", " + satellitesMax);
        }
        this.satellitesLow = satellitesLow;
        this.satellitesMax = satellitesMax;
        this.levelLow = levelLow;
        this.levelMax = levelMax;
        this.model = model;
    }

    public static SkyStrength of(Tunables t) {
        return new SkyStrength(t.satellitesLow(), t.satellitesMax(), t.levelLow(), t.levelMax(), t.radioModel());
    }

    /** Whether {@code satellites} in orbit give a dimension sky hops at all. */
    public boolean enough(int satellites) {
        return satellites >= satellitesLow;
    }

    /**
     * The SNR of a sky hop with nothing in its way; negative infinity with too few satellites. It stops at Max's count
     * (X38): "100 for maximum", and underground the hop stays gone however many go up (DESIGN.md 9.1).
     */
    public double snr(int satellites) {
        if (!enough(satellites)) return Double.NEGATIVE_INFINITY;
        int counted = Math.min(satellites, satellitesMax);
        return levelLow + (levelMax - levelLow) * Math.log10((double) counted / satellitesLow)
            / Math.log10((double) satellitesMax / satellitesLow);
    }

    /**
     * A sky hop from a ground end {@code depth} blocks below the surface (negative above it): the satellites' SNR less
     * its cover on {@code band}, so a thin roof weakens it and underground it's gone, and less {@code weather} dB.
     */
    public double hop(int satellites, double depth, Band band, double weather) {
        return snr(satellites) - model.coverLoss(depth, band.coverPerBlock()) - weather;
    }

    /**
     * The same hop term by term, for the Signal Analyzer (DESIGN.md 6.6): the satellites' SNR as its budget, its
     * cover and the weather; null with too few satellites.
     */
    public RadioModel.Losses losses(int satellites, double depth, Band band, double weather) {
        if (!enough(satellites)) return null;
        return new RadioModel.Losses(
            snr(satellites),
            0,
            0,
            model.coverLoss(depth, band.coverPerBlock()),
            0,
            weather,
            0);
    }
}
