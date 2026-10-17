package com.enn3developer.becom.client;

import net.minecraft.client.gui.Gui;

/**
 * Draws a Lua app's 8 by 8 icon: 64 hex digits, a row at a time from the top, each one of Minecraft's sixteen text
 * colours by its code, 0 for none.
 */
public final class AppIcon {

    private static final int[] COLOURS = { 0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00,
        0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF };

    public static final int CELLS = 8;

    private AppIcon() {}

    /** Draws {@code icon} {@code cell} pixels a cell from {@code (x, y)}; nothing for an icon that isn't 64 digits. */
    public static void draw(int x, int y, int cell, String icon) {
        if (icon == null || icon.length() != CELLS * CELLS) return;
        for (int i = 0; i < icon.length(); i++) {
            int colour = Character.digit(icon.charAt(i), 16);
            if (colour <= 0) continue;
            int left = x + (i % CELLS) * cell;
            int top = y + (i / CELLS) * cell;
            Gui.drawRect(left, top, left + cell, top + cell, 0xFF000000 | COLOURS[colour]);
        }
    }
}
