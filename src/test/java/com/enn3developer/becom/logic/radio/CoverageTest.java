package com.enn3developer.becom.logic.radio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.Tunables;

/** The coverage map, the SNR a player standing on the ground would get around an antenna. */
class CoverageTest {

    private static final double GROUND = 64;
    private final Tunables t = Tunables.defaults();
    private final RadioHops hops = new RadioHops(t.radioModel(), t.surveyStep());
    private final double budget = t.antennaBudget(1);
    private final double range = t.rangeToHold(budget);
    private final RadioHops.End antenna = new RadioHops.End(0.5, GROUND + 6, 0.5, 0, GROUND);

    private byte[] map(Terrain terrain) {
        Survey survey = Survey.of(
            terrain,
            antenna.x(),
            antenna.z(),
            t.surveyBearings(),
            t.surveyStep(),
            Survey.stepsFor(range, t.surveyStep()));
        return Coverage.of(hops, survey, antenna, budget, terrain, t.uhf(), false, range);
    }

    private static int cell(int row, int col) {
        return row * Coverage.CELLS + col;
    }

    @Test
    void openGroundFollowsTheLinkBudgetAndCornersAreOutOfReach() {
        byte[] map = map((x, z) -> GROUND);
        assertEquals(Coverage.CELLS * Coverage.CELLS, map.length);
        // The corners lie past the range, the square's diagonal.
        assertEquals(Coverage.NONE, map[cell(0, 0)]);
        // East of the antenna, on its row: budget less distance, rounded to whole dB.
        double cellSize = 2 * range / Coverage.CELLS;
        int col = Coverage.CELLS / 2 + 4;
        int row = Coverage.CELLS / 2;
        double x = antenna.x() - range + (col + 0.5) * cellSize;
        double z = antenna.z() - range + (row + 0.5) * cellSize;
        double distance = new RadioHops.End(x, GROUND + 1.62, z, -1.62, GROUND).distanceTo(antenna);
        assertEquals(Math.round(budget - RadioModel.distanceLoss(distance)), map[cell(row, col)]);
    }

    @Test
    void aRidgeCastsAShadow() {
        // A 30-block ridge 20 blocks east: what lies beyond it does worse than the same distance west.
        byte[] map = map((x, z) -> x >= 20 && x < 24 ? GROUND + 30 : GROUND);
        int row = Coverage.CELLS / 2;
        int east = Coverage.CELLS / 2 + 8;
        int west = Coverage.CELLS / 2 - 9;
        assertTrue(map[cell(row, east)] < map[cell(row, west)], map[cell(row, east)] + " vs " + map[cell(row, west)]);
    }
}
