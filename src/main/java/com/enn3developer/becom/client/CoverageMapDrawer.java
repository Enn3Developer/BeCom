package com.enn3developer.becom.client;

import net.minecraft.client.gui.Gui;

import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.logic.LevelScale;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.radio.Coverage;

/**
 * Draws an antenna's coverage map (DESIGN.md 6.6, 11.4): one square per cell, north up, coloured by the level a
 * player standing there would get, the antenna marked in the middle.
 */
public final class CoverageMapDrawer {

    private static final int BACKGROUND = 0xFF101010;
    private static final int OUT_OF_REACH = 0xFF202020;
    private static final int ANTENNA = 0xFFFFFFFF;
    // None, Low, Medium, High, Max: from red to cyan, like the analyzer's bars warming up.
    private static final int[] LEVEL_COLOURS = { 0xFF303030, 0xFFB03020, 0xFFD0A020, 0xFF40B040, 0xFF30C0D0 };

    private CoverageMapDrawer() {}

    /** The map in {@code (x, y, width, height)}; levels stop at {@code cap}. */
    public static void draw(int x, int y, int width, int height, byte[] map, SignalLevel cap) {
        Gui.drawRect(x, y, x + width, y + height, BACKGROUND);
        if (map == null || map.length != Coverage.CELLS * Coverage.CELLS) return;
        LevelScale scale = BeComConfig.get()
            .levelScale();
        for (int row = 0; row < Coverage.CELLS; row++) {
            for (int col = 0; col < Coverage.CELLS; col++) {
                byte snr = map[row * Coverage.CELLS + col];
                int colour = snr == Coverage.NONE ? OUT_OF_REACH
                    : LEVEL_COLOURS[SignalLevel.min(scale.levelOf(snr), cap)
                        .ordinal()];
                int left = x + col * width / Coverage.CELLS;
                int right = x + (col + 1) * width / Coverage.CELLS;
                int top = y + row * height / Coverage.CELLS;
                int bottom = y + (row + 1) * height / Coverage.CELLS;
                Gui.drawRect(left, top, right, bottom, colour);
            }
        }
        int cx = x + width / 2;
        int cy = y + height / 2;
        Gui.drawRect(cx - 1, cy - 1, cx + 1, cy + 1, ANTENNA);
    }

    /** The colour key under the map: Low to Max, as many as {@code cap} allows. */
    public static void legend(int x, int y, int width, int height, SignalLevel cap) {
        int levels = cap.ordinal();
        if (levels <= 0) return;
        int each = width / levels;
        for (int i = 1; i <= levels; i++) {
            int left = x + (i - 1) * each;
            Gui.drawRect(left + 1, y, left + each - 1, y + height, LEVEL_COLOURS[i]);
        }
    }
}
