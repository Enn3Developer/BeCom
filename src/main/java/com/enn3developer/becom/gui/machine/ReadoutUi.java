package com.enn3developer.becom.gui.machine;

import java.util.function.Supplier;

import com.enn3developer.becom.gui.paint.Canvas;
import com.enn3developer.becom.gui.paint.CanvasScreen;
import com.enn3developer.becom.gui.paint.Grey;
import com.enn3developer.becom.machine.InfoLines;

/**
 * A machine's readout as a screen: on its own grey panel with the machine's name over the display (the ME Bridge and
 * the Portal Relay, on the Gateways board), or alone, to fill a Gregtech multiblock's terminal (the Satellite Dish and
 * Launch Pad, on the Machines board).
 */
public final class ReadoutUi extends CanvasScreen {

    // The boards' display is 352 wide; a panel adds its frame.
    private static final float DISPLAY = 352;
    private static final float PANEL_WIDTH = DISPLAY + 2 * 16 + 4 + 8;
    private static final float PANEL_HEIGHT = 340;

    private final Supplier<String> lines;
    private final String title;
    private final String chip;
    private final float height;
    // The Launch Pad's tabs: opens its Orbit page; null for a readout without tabs.
    private final Runnable orbit;

    /** A machine's own panel, {@code chip} its tier's chip or null. */
    public static ReadoutUi panel(Supplier<String> lines, String title, String chip) {
        return new ReadoutUi(lines, title, chip, PANEL_HEIGHT, null);
    }

    /** The display alone, {@code height} board pixels tall, for a multiblock's terminal. */
    public static ReadoutUi terminal(Supplier<String> lines, float height, Runnable orbit) {
        return new ReadoutUi(lines, null, null, height, orbit);
    }

    private ReadoutUi(Supplier<String> lines, String title, String chip, float height, Runnable orbit) {
        this.lines = lines;
        this.title = title;
        this.chip = chip;
        this.height = height;
        this.orbit = orbit;
    }

    /**
     * How much wider a multiblock's terminal gets, in GUI pixels, for the board's display to fit it at the boards'
     * scale: its text then is Minecraft's font at full size.
     */
    public static final int TERMINAL_EXTRA = 84;

    /**
     * Puts the readout on top of a multiblock's terminal {@code list}, {@code guiWidth} by {@code guiHeight} GUI
     * pixels, with a line left under it for GT's own words, such as Running perfectly.
     */
    public static com.cleanroommc.modularui.widgets.ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> intoTerminal(
        com.cleanroommc.modularui.widgets.ListWidget<com.cleanroommc.modularui.api.widget.IWidget, ?> list,
        Supplier<String> lines, int guiWidth, int guiHeight, Runnable orbit) {
        float scale = guiWidth / DISPLAY;
        list.addChild(
            new com.enn3developer.becom.gui.paint.CanvasWidget(
                terminal(lines, (guiHeight - GT_LINE) / scale, orbit),
                scale),
            0);
        return list;
    }

    // The height GT's words under the readout take.
    private static final int GT_LINE = 14;

    private static String t(String key) {
        return net.minecraft.util.StatCollector.translateToLocal(key);
    }

    public static float panelScale(int guiHeight) {
        return guiHeight <= 0 ? 0.75f : Math.min(0.75f, (guiHeight - 8) / PANEL_HEIGHT);
    }

    @Override
    public float width() {
        return title == null ? DISPLAY : PANEL_WIDTH;
    }

    @Override
    public float height() {
        return height;
    }

    @Override
    protected void paint(Canvas c) {
        if (title == null) {
            float y = 4;
            if (orbit != null) {
                // The Launch Pad's tabs, as on the Orbit board, this page's pressed in.
                float tab = 84;
                Grey.pressed(c, DISPLAY - 4 - 2 * tab, y, tab, 22, t("becom.ui.orbit.launches"), 12);
                Grey.button(this, c, DISPLAY - 4 - tab, y, tab, 22, t("becom.ui.orbit.orbit"), 12, orbit);
                y += 22 + 8;
            }
            Readout.paint(c, InfoLines.rows(lines.get()), 4, y, DISPLAY - 8);
            return;
        }
        Grey.panel(c, PANEL_WIDTH, PANEL_HEIGHT);
        float x = 16;
        float y = 16;
        c.text(title, x, y + (20 - c.lineHeight(15)) / 2, 15, Grey.TITLE);
        if (chip != null) Grey.chip(c, x + c.width(title, 15) + 8, y + 2, chip, 0xb0a020);
        y += 20 + 10;
        float w = PANEL_WIDTH - 2 * x;
        Grey.display(c, x, y, w, PANEL_HEIGHT - y - 16, 0x1e2024);
        Readout.paint(c, InfoLines.rows(lines.get()), x + 12, y + 10, w - 24);
    }
}
