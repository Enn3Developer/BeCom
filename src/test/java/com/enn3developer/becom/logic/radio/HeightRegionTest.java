package com.enn3developer.becom.logic.radio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** DESIGN.md 6.2: 4 x 4 cells per chunk, the highest point in each, 16 KB per 32 x 32-chunk region. */
class HeightRegionTest {

    @Test
    void regionIsSixteenKilobytesOfCells() {
        assertEquals(16 * 1024, HeightRegion.BYTES);
    }

    @Test
    void cellsKeepTheHighestPointOfEachFourByFour() {
        int[] heightMap = new int[256];
        for (int i = 0; i < 256; i++) heightMap[i] = 64;
        heightMap[0] = 70; // x 0, z 0: cell 0
        heightMap[5 * 16 + 15] = 90; // x 15, z 5: cell (3, 1)
        int[] cells = HeightRegion.cellsOf(heightMap);
        assertEquals(16, cells.length);
        assertEquals(70, cells[0]);
        assertEquals(90, cells[1 * 4 + 3]);
        assertEquals(64, cells[2 * 4 + 2]);
    }

    @Test
    void unseenCellsAreZeroAndHeightsAreClamped() {
        HeightRegion region = new HeightRegion();
        assertEquals(0, region.get(5, 5));
        region.set(5, 5, 300);
        assertEquals(255, region.get(5, 5));
        region.set(5, 5, -3);
        assertEquals(0, region.get(5, 5));
    }

    @Test
    void settingAChunkWritesItsSixteenCells() {
        HeightRegion region = new HeightRegion();
        int[] cells = new int[16];
        for (int i = 0; i < 16; i++) cells[i] = 60 + i;
        region.setChunk(31, 2, cells);
        assertEquals(60, region.get(124, 8));
        assertEquals(75, region.get(127, 11));
        assertTrue(region.dirty());
    }

    @Test
    void onlyAChangeMarksItDirty() {
        HeightRegion region = new HeightRegion();
        region.set(1, 1, 0);
        assertFalse(region.dirty());
        region.set(1, 1, 64);
        region.clean();
        region.set(1, 1, 64);
        assertFalse(region.dirty());
    }

    @Test
    void roundTripsThroughBytes() {
        HeightRegion region = new HeightRegion();
        region.set(0, 0, 12);
        region.set(127, 127, 200);
        HeightRegion back = HeightRegion.fromBytes(region.toBytes());
        assertNotNull(back);
        assertEquals(region, back);
        assertEquals(200, back.get(127, 127));
    }

    @Test
    void anyOtherFileIsThrownAway() {
        byte[] bytes = new HeightRegion().toBytes();
        assertNull(HeightRegion.fromBytes(null));
        assertNull(HeightRegion.fromBytes(new byte[10]));
        byte[] newer = bytes.clone();
        newer[7] = 2; // format 2
        assertNull(HeightRegion.fromBytes(newer));
        byte[] foreign = bytes.clone();
        foreign[0] = 0;
        assertNull(HeightRegion.fromBytes(foreign));
        assertArrayEquals(
            bytes,
            HeightRegion.fromBytes(bytes)
                .toBytes());
    }
}
