package com.enn3developer.becom.logic.radio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.Tunables;

/** DESIGN.md 6.1 to 6.4, and the worked ranges of 7 and 8.1, through the hops the server works out. */
class RadioHopsTest {

    private final Tunables t = Tunables.defaults();
    private final RadioHops hops = new RadioHops(t.radioModel(), t.surveyStep());
    private final Band vhf = t.vhf();
    private final Band uhf = t.uhf();
    private static final Terrain FLAT = (x, z) -> 64;

    // An antenna head 20 blocks up a mast on flat ground at 64, and players standing on that ground.
    private static final double ANTENNA_Y = 85;
    private static final double EYE_Y = 65.62;

    private Survey surveyOf(Terrain terrain, double budget) {
        return Survey.of(
            terrain,
            0.5,
            0.5,
            t.surveyBearings(),
            t.surveyStep(),
            Survey.stepsFor(t.rangeToLow(budget), t.surveyStep()));
    }

    private static RadioHops.End antenna() {
        // Its casings stand on the ground.
        return new RadioHops.End(0.5, ANTENNA_Y, 0.5, 0, 64);
    }

    private static RadioHops.End player(double x, double z) {
        return new RadioHops.End(x, EYE_Y, z, 64 - EYE_Y, 64);
    }

    @Test
    void anAntennaT2ReachesLowAboutAHundredAndTwentyEightBlocksOutOnOpenGround() {
        double budget = t.antennaBudget(2);
        Survey survey = surveyOf(FLAT, budget);
        // The 3D distance counts, so the edge sits a little inside 128 blocks for an antenna 20 blocks up.
        double inside = hops.access(survey, antenna(), budget, player(120.5, 0.5), vhf, 0, false)
            .snr();
        double outside = hops.access(survey, antenna(), budget, player(132.5, 0.5), vhf, 0, false)
            .snr();
        assertTrue(inside >= t.levelLow(), "120 blocks: " + inside);
        assertTrue(outside < t.levelLow(), "132 blocks: " + outside);
    }

    @Test
    void aWalkieReachesAboutEightyBlocksAndATransceiverAboutOneTwentyFive() {
        RadioHops.End a = player(0.5, 0.5);
        double walkie = hops.direct(FLAT, a, player(78.5, 0.5), t.walkieBudget(), vhf, 0, false)
            .snr();
        double walkieFar = hops.direct(FLAT, a, player(82.5, 0.5), t.walkieBudget(), vhf, 0, false)
            .snr();
        assertTrue(walkie >= t.levelLow() && walkieFar < t.levelLow(), walkie + " / " + walkieFar);
        double transceiver = hops.direct(FLAT, a, player(123.5, 0.5), t.transceiverBudget(), vhf, 0, false)
            .snr();
        double transceiverFar = hops.direct(FLAT, a, player(128.5, 0.5), t.transceiverBudget(), vhf, 0, false)
            .snr();
        assertTrue(transceiver >= t.levelLow() && transceiverFar < t.levelLow(), transceiver + " / " + transceiverFar);
    }

    @Test
    void aHillBetweenCostsTerrainLossAndUhfMoreThanVhf() {
        // A ridge 20 blocks above the ground halfway to a player 60 blocks east.
        Terrain hill = (x, z) -> x >= 28 && x < 32 ? 84 : 64;
        double budget = t.antennaBudget(2);
        Survey survey = surveyOf(hill, budget);
        RadioModel.Losses open = hops
            .access(surveyOf(FLAT, budget), antenna(), budget, player(60.5, 0.5), vhf, 0, false);
        RadioModel.Losses behindVhf = hops.access(survey, antenna(), budget, player(60.5, 0.5), vhf, 0, false);
        RadioModel.Losses behindUhf = hops.access(survey, antenna(), budget, player(60.5, 0.5), uhf, 0, false);
        assertEquals(0, open.terrain(), 1e-9);
        assertTrue(behindVhf.terrain() > 0);
        assertTrue(behindUhf.terrain() > behindVhf.terrain(), "UHF needs a clearer line of sight");
        assertEquals(
            RadioModel.Loss.TERRAIN,
            behindUhf.biggest() == RadioModel.Loss.DISTANCE ? RadioModel.Loss.TERRAIN : behindUhf.biggest());
    }

    @Test
    void coverCostsPerBlockBelowTheSurfaceUpToTheCap() {
        double budget = t.antennaBudget(1);
        Survey survey = surveyOf(FLAT, budget);
        // Ten blocks down a mine: eyes 10 blocks under the surface at 64.
        RadioHops.End miner = new RadioHops.End(30.5, 54, 0.5, 10, 52.38);
        RadioModel.Losses losses = hops.access(survey, antenna(), budget, miner, vhf, 0, false);
        assertEquals(10 * t.vhfCoverPerBlock(), losses.coverB(), 1e-9);
        RadioHops.End deep = new RadioHops.End(30.5, 10, 0.5, 54, 8.38);
        assertEquals(
            t.coverLossCap(),
            hops.access(survey, antenna(), budget, deep, vhf, 0, false)
                .coverB(),
            1e-9);
    }

    @Test
    void withoutASkyTerrainAndCoverAreSkippedAndTheNoiseFloorRises() {
        Terrain ceiling = (x, z) -> 127;
        RadioHops.End a = new RadioHops.End(0.5, 40, 0.5, 87, 38.38);
        RadioHops.End b = new RadioHops.End(40.5, 40, 0.5, 87, 38.38);
        RadioModel.Losses losses = hops.direct(ceiling, a, b, t.walkieBudget(), vhf, 0, true);
        assertEquals(0, losses.terrain(), 1e-9);
        assertEquals(0, losses.coverA() + losses.coverB(), 1e-9);
        assertEquals(t.noSkyNoise(), losses.noise(), 1e-9);
        assertEquals(t.walkieBudget() - RadioModel.distanceLoss(40) - t.noSkyNoise(), losses.snr(), 1e-9);
    }

    @Test
    void aSurveyOfAnAntennaT3IsAboutFourKilobytes() {
        Survey survey = surveyOf(FLAT, t.antennaBudget(3));
        // DESIGN.md 6.3: at most 64 heights per bearing, about 4 KB per antenna.
        assertEquals(64, survey.bearings());
        assertTrue(survey.steps() <= 64, "steps " + survey.steps());
        assertTrue(survey.bearings() * survey.steps() <= 4096);
    }
}
