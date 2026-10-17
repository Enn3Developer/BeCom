package com.enn3developer.becom.logic.radio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The radio model, and the ranges it gives antennas, phones and walkies. */
class RadioModelTest {

    private static final double LOW = 6;
    private final RadioModel model = new RadioModel(40, 40, 10);

    private static void assertAbout(double expected, double actual) {
        // The design's ranges are rounded: "about 64, 128 and 256 blocks".
        assertEquals(expected, actual, expected * 0.03);
    }

    @Test
    void antennasReachAbout64And128And256Blocks() {
        assertAbout(64, RadioModel.rangeFor(42, LOW));
        assertAbout(128, RadioModel.rangeFor(48, LOW));
        assertAbout(256, RadioModel.rangeFor(54, LOW));
    }

    @Test
    void walkieReachesAbout80AndTransceiverAbout125() {
        assertAbout(80, RadioModel.rangeFor(44, LOW));
        assertAbout(125, RadioModel.rangeFor(48, LOW));
    }

    @Test
    void sixDecibelsDoublesTheRange() {
        assertEquals(2, RadioModel.rangeFor(48, LOW) / RadioModel.rangeFor(42, LOW), 0.01);
    }

    @Test
    void distanceLossIsAtLeastOneBlock() {
        assertEquals(0, RadioModel.distanceLoss(0.2), 1e-9);
        assertEquals(40, RadioModel.distanceLoss(100), 1e-9);
    }

    @Test
    void knifeEdgeIsSixDecibelsAtGrazing() {
        assertEquals(6.0, model.knifeEdge(0), 0.1);
    }

    @Test
    void knifeEdgeIsFreeWhenTheLineClearsWell() {
        assertEquals(0, model.knifeEdge(-0.78), 1e-9);
        assertEquals(0, model.knifeEdge(-5), 1e-9);
        assertTrue(model.knifeEdge(-0.7) > 0);
    }

    @Test
    void knifeEdgeIsCappedAtForty() {
        assertEquals(40, model.knifeEdge(1000), 1e-9);
    }

    @Test
    void knifeEdgeMatchesTheFormula() {
        // L = 6.9 + 20 log10(sqrt((v - 0.1)^2 + 1) + v - 0.1) at v = 2.
        double expected = 6.9 + 20 * Math.log10(Math.sqrt(1.9 * 1.9 + 1) + 1.9);
        assertEquals(expected, model.knifeEdge(2), 1e-9);
    }

    @Test
    void vhfBendsAroundHillsBetterThanUhf() {
        // A 4-block hill halfway along a 100-block path.
        double vhf = RadioModel.nu(4, 50, 50, 3);
        double uhf = RadioModel.nu(4, 50, 50, 1);
        assertTrue(model.knifeEdge(vhf) < model.knifeEdge(uhf));
    }

    @Test
    void coverLossIsPerBlockAndCapped() {
        assertEquals(0, model.coverLoss(-3, 1.5), 1e-9);
        assertEquals(4.5, model.coverLoss(3, 1.5), 1e-9);
        assertEquals(40, model.coverLoss(100, 2.5), 1e-9);
    }

    @Test
    void worstNuAlongFindsTheHighestObstacleAboveTheLine() {
        // Flat at 60, with a 75-block wall across x 48..51 and a 72-block one across x 20..23.
        Terrain terrain = (x, z) -> x >= 48 && x < 52 ? 75 : x >= 20 && x < 24 ? 72 : 60;
        double worst = RadioModel.worstNuAlong(terrain, 0.5, 70, 0.5, 100.5, 70, 0.5, 4, 3, 0);
        // Samples fall every 4 blocks from x 0.5: the wall is sampled at 48.5, 48 blocks out.
        assertEquals(RadioModel.nu(5, 48, 52, 3), worst, 1e-9);
    }

    @Test
    void worstNuAlongSkipsTheEndsOwnCells() {
        // Towers right next to both ends, in their own cells, don't count; open ground between.
        Terrain terrain = (x, z) -> x < 4 || x >= 100 ? 200 : 60;
        double worst = RadioModel.worstNuAlong(terrain, 1.5, 70, 1.5, 101.5, 70, 1.5, 4, 3, 0);
        assertEquals(0, model.knifeEdge(worst), 1e-9);
    }

    @Test
    void worstNuAlongIsNothingOverAShortPath() {
        assertEquals(Double.NEGATIVE_INFINITY, RadioModel.worstNuAlong((x, z) -> 255, 0, 70, 0, 3, 70, 0, 4, 3, 0));
    }

    @Test
    void noSkyDropsTerrainAndCoverAndRaisesTheNoise() {
        Band vhf = new Band("vhf", 3, 1.5);
        RadioModel.Losses losses = model.losses(48, 100, 5, 10, 10, vhf, 0, true);
        assertEquals(0, losses.terrain(), 1e-9);
        assertEquals(0, losses.coverA() + losses.coverB(), 1e-9);
        assertEquals(10, losses.noise(), 1e-9);
        assertEquals(48 - 40 - 10, losses.snr(), 1e-9);
    }

    @Test
    void lossesUseTheBandsCover() {
        Band uhf = new Band("uhf", 1, 2.5);
        RadioModel.Losses losses = model.losses(48, 10, Double.NEGATIVE_INFINITY, 2, -1, uhf, 3, false);
        assertEquals(5, losses.coverA(), 1e-9);
        assertEquals(0, losses.coverB(), 1e-9);
        assertEquals(48 - 20 - 5 - 3, losses.snr(), 1e-9);
    }

    @Test
    void lossesAddUpAndNameTheBiggest() {
        RadioModel.Losses losses = new RadioModel.Losses(48, 30, 4, 3, 3, 0, 0);
        assertEquals(8, losses.snr(), 1e-9);
        assertEquals(RadioModel.Loss.DISTANCE, losses.biggest());
        assertEquals(RadioModel.Loss.COVER, new RadioModel.Losses(48, 10, 4, 6, 6, 0, 0).biggest());
        assertEquals(RadioModel.Loss.TERRAIN, new RadioModel.Losses(48, 10, 20, 6, 6, 0, 0).biggest());
        assertEquals(RadioModel.Loss.NOISE, new RadioModel.Losses(48, 5, 0, 0, 0, 3, 10).biggest());
    }
}
