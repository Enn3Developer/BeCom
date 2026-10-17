package com.enn3developer.becom.logic.radio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A survey: 64 bearings, 4-block steps, a whole profile per bearing. */
class SurveyTest {

    @Test
    void bearingsStartEastAndTurnTowardSouth() {
        assertEquals(0, Survey.bearingOf(10, 0, 64));
        assertEquals(16, Survey.bearingOf(0, 10, 64));
        assertEquals(32, Survey.bearingOf(-10, 0, 64));
        assertEquals(48, Survey.bearingOf(0, -10, 64));
        // Just short of a full turn rounds back to 0, not 64.
        assertEquals(0, Survey.bearingOf(10, -0.01, 64));
    }

    @Test
    void directionMatchesBearing() {
        double[] south = Survey.direction(16, 64);
        assertEquals(0, south[0], 1e-9);
        assertEquals(1, south[1], 1e-9);
    }

    @Test
    void storesAProfilePerBearingAndSkipsItsOwnCell() {
        Survey survey = Survey.of((x, z) -> 64, 0.5, 0.5, 64, 4, 16);
        assertEquals(64, survey.bearings());
        assertEquals(16, survey.steps());
        // Due east, 4 blocks out is x 4.5: the next cell over.
        assertEquals(64, survey.height(0, 0), 1e-9);
        // Diagonally, 4 blocks out is (3.3, 3.3): still the antenna's own cell.
        assertTrue(Double.isNaN(survey.height(8, 0)));
    }

    @Test
    void worstObstacleDependsOnWhereTheDeviceIs() {
        // Flat at 64, with a 70-block ridge across x 40..43, east of the antenna.
        Terrain terrain = (x, z) -> x >= 40 && x < 44 ? 70 : 64;
        Survey survey = Survey.of(terrain, 0.5, 0.5, 64, 4, 16);
        // A device 36 blocks out is short of the ridge; one 60 blocks out is behind it.
        double near = survey.worstNu(80, 36.5, 65.6, 0.5, 3, 0);
        double far = survey.worstNu(80, 60.5, 65.6, 0.5, 3, 0);
        double lineAt40 = 80 + (65.6 - 80) * (40.0 / 60);
        assertEquals(RadioModel.nu(70 - lineAt40, 40, 20, 3), far, 1e-9);
        assertTrue(far > near);
    }

    @Test
    void stepsInTheDevicesCellAreSkipped() {
        // A tree in the device's own cell, 2 blocks short of it, isn't terrain between: it's next to the device.
        Terrain terrain = (x, z) -> x >= 56 && x < 60 ? 90 : 64;
        Survey survey = Survey.of(terrain, 0.5, 0.5, 64, 4, 16);
        double worst = survey.worstNu(80, 58.5, 65.6, 0.5, 3, 0);
        assertTrue(worst < -0.78);
    }

    @Test
    void builderSpreadsTheSurveyOverCalls() {
        Survey.Builder builder = new Survey.Builder(0.5, 0.5, 4, 4, 8);
        int calls = 0;
        while (!builder.done()) {
            builder.next((x, z) -> 70);
            calls++;
        }
        assertEquals(4, calls);
        assertEquals(
            70,
            builder.build()
                .height(2, 3),
            1e-9);
    }

    @Test
    void openGroundIsNoObstacle() {
        // Flat at 64 with both ends standing on it: the ground is no higher than either end's, so nothing counts.
        Survey survey = Survey.of((x, z) -> 64, 0.5, 0.5, 64, 4, 32);
        assertEquals(Double.NEGATIVE_INFINITY, survey.worstNu(85, 100.5, 65.62, 0.5, 3, 64));
        // A 1-block rise across the path does.
        Survey ridge = Survey.of((x, z) -> x >= 48 && x < 52 ? 65 : 64, 0.5, 0.5, 64, 4, 32);
        assertTrue(ridge.worstNu(85, 100.5, 65.62, 0.5, 3, 64) > Double.NEGATIVE_INFINITY);
    }
}
