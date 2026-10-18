package com.enn3developer.becom.gui.paint;

import java.util.function.Consumer;

/**
 * The machine boards' look: Minecraft's grey GUI panel with dark inset displays, raised grey buttons, green status
 * chips, sunken switches, and signal bars and levels in colours that read on grey.
 */
public final class Grey {

    private Grey() {}

    public static final int PANEL = 0xc6c6c6;
    public static final int TITLE = 0x404040;
    public static final int TEXT = 0x303030;
    public static final int NOTE = 0x5a5a5a;
    public static final int FAINT = 0x6a6a6a;
    public static final int DISPLAY = 0x101418;
    public static final int BUTTON = 0x8b8b8b;
    public static final int GOOD = 0x3d8a3d;
    public static final int BAD = 0xa8342a;
    public static final int OFF = 0x6a6a6a;

    /** The panel, {@code w} by {@code h} with its black outline: a 2-pixel bevel, white on top, dark below. */
    public static void panel(Canvas c, float w, float h) {
        c.rect(0, 0, w, h, Paint.BLACK);
        Paint.box(c, 2, 2, w - 4, h - 4, 2, PANEL, 0xffffff, 0x555555);
    }

    /** A dark display set into the panel. */
    public static void display(Canvas c, float x, float y, float w, float h, int color) {
        Paint.box(c, x, y, w, h, 2, color, 0x373737, 0xffffff);
    }

    public static void display(Canvas c, float x, float y, float w, float h) {
        display(c, x, y, w, h, DISPLAY);
    }

    /** A display cell: a small grey label over a value, as the boards' stat boxes. */
    public static void cell(Canvas c, float x, float y, float w, float h, String label, float labelSize, String value,
        float valueSize, int valueColor) {
        display(c, x, y, w, h);
        float top = y + (h - c.lineHeight(labelSize) - 3 - c.lineHeight(valueSize)) / 2;
        c.text(Paint.fit(c, label, labelSize, w - 16), x + 8, top, labelSize, Paint.GREY);
        c.text(Paint.fit(c, value, valueSize, w - 16), x + 8, top + c.lineHeight(labelSize) + 3, valueSize, valueColor);
    }

    /** A status chip: white text with a dark shadow on green, red or grey. Returns its width. */
    public static float chip(Canvas c, float x, float y, String text, int color) {
        float w = c.width(text, 13) + 12;
        c.rect(x, y, w, 17, color);
        Paint.shadowed(c, text, x + 6, y + (17 - c.lineHeight(13)) / 2, 13, Paint.WHITE, 0x2a2a2a);
        return w;
    }

    /** A raised grey button with a white label and a dark shadow two pixels down. */
    public static void button(CanvasScreen s, Canvas c, float x, float y, float w, float h, String label, float size,
        Runnable press) {
        boolean hovered = press != null && s.hover(x, y, w, h);
        Paint.box(c, x, y, w, h, 2, hovered ? 0x9b9b9b : BUTTON, 0xdbdbdb, 0x3a3a3a);
        float ty = y + (h - c.lineHeight(size)) / 2;
        Paint.centered(c, label, x + w / 2 + 2, ty + 2, size, 0x3f3f3f);
        Paint.centered(c, label, x + w / 2, ty, size, Paint.WHITE);
        if (press != null) s.hit(x, y, w, h, press);
    }

    /** A pressed-in grey button: a tab that's chosen. */
    public static void pressed(Canvas c, float x, float y, float w, float h, String label, float size) {
        Paint.box(c, x, y, w, h, 2, 0x5e5e5e, 0x3a3a3a, 0x9a9a9a);
        Paint.centered(c, label, x + w / 2, y + (h - c.lineHeight(size)) / 2, size, Paint.WHITE);
    }

    /** The boards' sunken switch, 44 by 20: green with its knob right when on; still when {@code change} is null. */
    public static void toggle(CanvasScreen s, Canvas c, float x, float y, boolean on, Consumer<Boolean> change) {
        Paint.box(c, x, y, 44, 20, 2, on ? GOOD : 0x5e5e5e, 0x3a3a3a, 0xdbdbdb);
        float kx = on ? x + 44 - 4 - 14 : x + 4;
        c.rect(kx, y + 4, 14, 12, 0x3a3a3a);
        c.rect(kx + 1, y + 5, 12, 10, 0xdbdbdb);
        if (change == null) c.rect(x, y, 44, 20, 0x60c6c6c6);
        else s.hit(x, y, 44, 20, () -> change.accept(!on));
    }

    /** A level's colour on grey: bars' and text's. */
    public static int bar(com.enn3developer.becom.logic.SignalLevel level) {
        return switch (level) {
            case LOW -> 0xb03020;
            case MEDIUM -> 0xd0a020;
            case HIGH -> 0x40b040;
            case MAX -> 0x30a0c0;
            default -> 0x7a7a7a;
        };
    }

    public static int text(com.enn3developer.becom.logic.SignalLevel level) {
        return switch (level) {
            case LOW -> 0xa02818;
            case MEDIUM -> 0x8a6a10;
            case HIGH -> 0x2f7f2f;
            case MAX -> 0x1f6f8f;
            default -> 0x8a8a8a;
        };
    }

    /** Four bars on grey, 3 wide with gaps of 2, 4 to 12 tall, bottom at {@code y + 12}. */
    public static void bars(Canvas c, float x, float y, com.enn3developer.becom.logic.SignalLevel level) {
        int[] heights = { 4, 6, 9, 12 };
        for (int i = 0; i < 4; i++) {
            c.rect(x + i * 5, y + 12 - heights[i], 3, heights[i], i < level.ordinal() ? bar(level) : 0x7a7a7a);
        }
    }

    /**
     * A small arrow button pair around a page count, "< 1/3 >"; returns its width. Draws right-aligned at
     * {@code right}.
     */
    public static void pager(CanvasScreen s, Canvas c, float right, float y, int page, int pages, Runnable previous,
        Runnable next) {
        String label = (page + 1) + "/" + Math.max(1, pages);
        float lw = c.width(label, 14);
        float x = right - 24 - 6 - lw - 6 - 24;
        button(s, c, x, y, 24, 24, "<", 14, page > 0 ? previous : null);
        c.text(label, x + 24 + 6, y + (24 - c.lineHeight(14)) / 2, 14, TITLE);
        button(s, c, right - 24, y, 24, 24, ">", 14, page + 1 < pages ? next : null);
    }
}
