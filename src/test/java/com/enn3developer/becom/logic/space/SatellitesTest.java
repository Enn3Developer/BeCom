package com.enn3developer.becom.logic.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SatellitesTest {

    private static final int OVERWORLD = 0;
    private static final int MOON = -28;

    @Test
    void eachDimensionKeepsItsOwnConstellation() {
        Satellites satellites = new Satellites();
        satellites.launch(OVERWORLD, 0, 100);
        satellites.launch(OVERWORLD, 10, 100);
        satellites.launch(MOON, 20, 100);
        assertEquals(2, satellites.count(OVERWORLD));
        assertEquals(1, satellites.count(MOON));
        assertEquals(0, satellites.count(-1));
        assertEquals(Map.of(MOON, 1, OVERWORLD, 2), satellites.counts());
    }

    @Test
    void satellitesComeDownWhenTheirTimeIsUp() {
        Satellites satellites = new Satellites();
        satellites.launch(OVERWORLD, 0, 100);
        satellites.launch(OVERWORLD, 50, 100);
        assertEquals(100, satellites.nextDown(OVERWORLD));
        assertEquals(List.of(), satellites.decay(99));
        assertEquals(List.of(OVERWORLD), satellites.decay(100));
        assertEquals(1, satellites.count(OVERWORLD));
        assertEquals(150, satellites.nextDown(OVERWORLD));
        satellites.decay(150);
        assertEquals(Long.MAX_VALUE, satellites.nextDown(OVERWORLD));
        assertEquals(0, satellites.count(OVERWORLD));
        assertEquals(Map.of(), satellites.counts());
    }

    @Test
    void clearingADimensionLeavesTheOthers() {
        Satellites satellites = new Satellites();
        satellites.launch(OVERWORLD, 0, 100);
        satellites.launch(MOON, 0, 100);
        long before = satellites.version();
        satellites.clear(OVERWORLD);
        assertEquals(0, satellites.count(OVERWORLD));
        assertEquals(1, satellites.count(MOON));
        assertEquals(Map.of(MOON, 1), satellites.counts());
        assertTrue(satellites.version() > before);
    }

    @Test
    void decayOnlyCountsAsAChangeWhenOneComesDown() {
        Satellites satellites = new Satellites();
        satellites.launch(OVERWORLD, 0, 100);
        long launched = satellites.version();
        satellites.decay(50);
        assertEquals(launched, satellites.version());
        satellites.decay(100);
        assertTrue(satellites.version() > launched);
    }

    @Test
    void lifetimesSpreadAroundTheirDays() {
        // DESIGN.md 9.1: 100 days, give or take 20%.
        assertEquals(80 * Satellites.DAY_TICKS, Satellites.lifetime(100, 0.2, 0));
        assertEquals(100 * Satellites.DAY_TICKS, Satellites.lifetime(100, 0.2, 0.5));
        assertTrue(Satellites.lifetime(100, 0.2, 0.999999) < 120 * Satellites.DAY_TICKS);
        assertTrue(Satellites.lifetime(100, 0.2, 0.999999) > 119 * Satellites.DAY_TICKS);
        assertEquals(100 * Satellites.DAY_TICKS, Satellites.lifetime(100, 0, 0.9));
    }
}
