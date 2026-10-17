package com.enn3developer.becom.logic.radio;

/**
 * Terrain heights for the radio model: the highest point of the height-cache cell holding a column,
 * or sea level where nothing was ever seen.
 */
@FunctionalInterface
public interface Terrain {

    /** Blocks per cell side: samples that fall in an end's own cell are skipped, since that cell holds the end. */
    int CELL = HeightRegion.CELL_BLOCKS;

    double heightAt(int x, int z);

    static int cellOf(double coordinate) {
        return Math.floorDiv((int) Math.floor(coordinate), CELL);
    }
}
