package com.enn3developer.becom.client;

import net.minecraft.client.gui.Gui;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.enn3developer.becom.logic.SignalLevel;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Signal bars next to the hotbar while the player is in a call or holds a phone: one bar per level,
 * Low to Max, lit up to the level they hear the call at, or else to the service of the phone they carry, none lit when
 * it has none.
 */
public final class SignalBars {

    private static final int BARS = 4;
    private static final int BAR_WIDTH = 2;
    private static final int GAP = 1;
    // Just right of the hotbar, which is 182 pixels wide and centred.
    private static final int HOTBAR_HALF_WIDTH = 91;
    private static final int MARGIN = 6;
    private static final int LIT = 0xFFE0E0E0;
    private static final int UNLIT = 0x80404040;

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;
        SignalLevel level = ClientPhone.callLevel();
        if (level == null) level = ClientPhone.heldService();
        if (level == null) return;
        bars(
            event.resolution.getScaledWidth() / 2 + HOTBAR_HALF_WIDTH + MARGIN,
            event.resolution.getScaledHeight() - 4,
            level);
    }

    /** Draws the bars from {@code (left, bottom)}; nothing at None, so a screen shows them only during a call. */
    public static void draw(int left, int bottom, SignalLevel level) {
        if (level != SignalLevel.NONE) bars(left, bottom, level);
    }

    private static void bars(int left, int bottom, SignalLevel level) {
        for (int i = 0; i < BARS; i++) {
            int height = 3 + 2 * i;
            int x = left + i * (BAR_WIDTH + GAP);
            // LOW is ordinal 1, so it lights the first bar.
            Gui.drawRect(x, bottom - height, x + BAR_WIDTH, bottom, i < level.ordinal() ? LIT : UNLIT);
        }
    }
}
