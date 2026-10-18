package com.enn3developer.becom.gui.paint;

import java.util.ArrayList;
import java.util.List;

import com.enn3developer.becom.logic.SignalLevel;

/** The design boards' look, as calls on a {@link Canvas}: their colours, bevels, buttons, pills, bars and icons. */
public final class Paint {

    private Paint() {}

    // The boards' palette.
    public static final int SCREEN = 0x0d1014;
    public static final int STATUS = 0x06080a;
    public static final int BODY = 0x1a1c1f;
    public static final int BLACK = 0x000000;
    public static final int LINE = 0x1c2229;
    public static final int WHITE = 0xffffff;
    public static final int TEXT = 0xe0e0e0;
    public static final int LIGHT = 0xc0c0c0;
    public static final int DIM = 0xa0a0a0;
    public static final int GREY = 0x8a8a8a;
    public static final int DARK = 0x6a6a6a;
    public static final int SECTION = 0x6a8aaa;
    public static final int UNLIT = 0x3a3a3a;
    public static final int RED = 0xff5555;
    public static final int ORANGE = 0xffaa00;
    public static final int YELLOW = 0xffff55;
    public static final int GREEN = 0x55ff55;
    public static final int CYAN = 0x55ffff;
    public static final int PILL_ON = 0x3d8a3d;
    public static final int PILL_OFF = 0x3a3d42;

    /** The colour a signal level is drawn in: Low orange, Medium yellow, High green, Max cyan, none red. */
    public static int level(SignalLevel level) {
        return switch (level) {
            case LOW -> ORANGE;
            case MEDIUM -> YELLOW;
            case HIGH -> GREEN;
            case MAX -> CYAN;
            default -> RED;
        };
    }

    /** A button's look: its face, and the light and dark edges of its 2-pixel bevel. */
    public enum Style {

        PLAIN(0x1c2229, 0x3a434e, 0x0a0c0f),
        GO(0x3d8a3d, 0x6fcf6f, 0x1f4f1f),
        DANGER(0xa8342a, 0xff7a6a, 0x5a1a14),
        STORE(0x9f2f5a, 0xff8ab4, 0x4a1428),
        WARN(0xb8761c, 0xffc070, 0x5a3a0a),
        ON(0xe0e0e0, 0xffffff, 0x8a8a8a);

        public final int face;
        public final int light;
        public final int dark;

        Style(int face, int light, int dark) {
            this.face = face;
            this.light = light;
            this.dark = dark;
        }
    }

    /** A box with an edge {@code edge} pixels wide: light on top and left, dark on the bottom and right. */
    public static void bevel(Canvas c, float x, float y, float w, float h, float edge, int light, int dark) {
        c.rect(x, y, w, edge, light);
        c.rect(x, y, edge, h, light);
        c.rect(x, y + h - edge, w, edge, dark);
        c.rect(x + w - edge, y + edge, edge, h - edge, dark);
    }

    /** A plain border {@code edge} pixels wide. */
    public static void border(Canvas c, float x, float y, float w, float h, float edge, int color) {
        bevel(c, x, y, w, h, edge, color, color);
    }

    /** A filled box with a bevelled edge. */
    public static void box(Canvas c, float x, float y, float w, float h, float edge, int face, int light, int dark) {
        c.rect(x, y, w, h, face);
        bevel(c, x, y, w, h, edge, light, dark);
    }

    /** A button of the boards: a bevelled face and a centred white label with a black shadow. */
    public static void button(Canvas c, float x, float y, float w, float h, Style style, String label, float size,
        boolean hovered) {
        box(c, x, y, w, h, 2, hovered ? brighter(style.face) : style.face, style.light, style.dark);
        int color = style == Style.ON ? SCREEN : WHITE;
        float ty = y + (h - c.lineHeight(size)) / 2;
        if (style != Style.ON) centered(c, label, x + w / 2 + 1, ty + 1, size, BLACK);
        centered(c, label, x + w / 2, ty, size, color);
    }

    /** A button as above, with a label of its own colour and no shadow: the dialer's red DEL. */
    public static void key(Canvas c, float x, float y, float w, float h, String label, float size, int color,
        boolean hovered) {
        box(c, x, y, w, h, 2, hovered ? brighter(Style.PLAIN.face) : Style.PLAIN.face, 0x3a434e, 0x0a0c0f);
        centered(c, label, x + w / 2, y + (h - c.lineHeight(size)) / 2, size, color);
    }

    /** The boards' on/off pill, 36 by 18: green with its knob right when on. */
    public static void pill(Canvas c, float x, float y, boolean on) {
        c.rect(x, y, 36, 18, 0x06080a);
        c.rect(x + 1, y + 1, 34, 16, on ? PILL_ON : PILL_OFF);
        c.rect(on ? x + 21 : x + 3, y + 3, 12, 12, TEXT);
    }

    /** Four signal bars, 3 wide and 4, 6, 9 and 12 tall, bottom at {@code y + 12}: as many lit as the level. */
    public static void bars(Canvas c, float x, float y, SignalLevel level) {
        int lit = level.ordinal();
        int color = level(level);
        int[] heights = { 4, 6, 9, 12 };
        for (int i = 0; i < heights.length; i++) {
            c.rect(x + i * 5, y + 12 - heights[i], 3, heights[i], i < lit ? color : UNLIT);
        }
    }

    /** The status bar's battery, 21 wide: an outline 18 by 9, its charge inside, and a 2 by 4 tip. */
    public static void battery(Canvas c, float x, float y, int percent, int color) {
        border(c, x, y, 18, 9, 1, color);
        float inner = 14 * Math.max(0, Math.min(100, percent)) / 100f;
        c.rect(x + 2, y + 2, inner, 5, color);
        c.rect(x + 19, y + 2.5f, 2, 4, color);
    }

    /**
     * An icon of the boards ({@link Glyphs}), {@code cell} pixels a cell, in {@code color}; its holes in {@code hole},
     * the colour behind it.
     */
    public static void glyph(Canvas c, float x, float y, float cell, int[][] rects, int color, int hole) {
        for (int[] r : rects) {
            c.rect(
                x + r[0] * cell,
                y + r[1] * cell,
                r[2] * cell,
                r[3] * cell,
                r.length > 4 && r[4] == 1 ? hole : color);
        }
    }

    // Minecraft's sixteen text colours, by code: a Lua app's icon is drawn in them.
    private static final int[] TEXT_COLOURS = { 0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00,
        0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF };

    /**
     * A Lua app's 8 by 8 icon, 64 hex digits a row at a time from the top, each a text colour code, 0 for none;
     * false, drawing nothing, for an icon that isn't 64 digits.
     */
    public static boolean appIcon(Canvas c, float x, float y, float cell, String icon) {
        if (icon == null || icon.length() != 64) return false;
        for (int i = 0; i < 64; i++) {
            int colour = Character.digit(icon.charAt(i), 16);
            if (colour <= 0) continue;
            c.rect(x + (i % 8) * cell, y + (i / 8) * cell, cell, cell, TEXT_COLOURS[colour]);
        }
        return true;
    }

    /** A key cap of the HUD and the incoming screen: a small bevelled box around a key's name. */
    public static float keyCap(Canvas c, float x, float y, String key, float size) {
        float w = Math.max(16, c.width(key, size) + 6);
        box(c, x, y, w, 16, 1, 0x2a2d31, DARK, 0x0a0b0c);
        centered(c, key, x + w / 2, y + (16 - c.lineHeight(size)) / 2, size, WHITE);
        return w;
    }

    public static void centered(Canvas c, String text, float cx, float y, float size, int color) {
        c.text(text, cx - c.width(text, size) / 2, y, size, color);
    }

    public static void right(Canvas c, String text, float rightX, float y, float size, int color) {
        c.text(text, rightX - c.width(text, size), y, size, color);
    }

    /** Text with a 1-pixel shadow down and right, as the boards' app bars have. */
    public static void shadowed(Canvas c, String text, float x, float y, float size, int color, int shadow) {
        c.text(text, x + 1, y + 1, size, shadow);
        c.text(text, x, y, size, color);
    }

    /** {@code text} cut to {@code max} pixels, ending in an ellipsis when it had to be cut. */
    public static String fit(Canvas c, String text, float size, float max) {
        if (c.width(text, size) <= max) return text;
        String dots = "...";
        int end = text.length();
        while (end > 0 && c.width(text.substring(0, end) + dots, size) > max) end--;
        return text.substring(0, end) + dots;
    }

    /** {@code text} broken into lines no wider than {@code max}, at spaces where it can be. */
    public static List<String> wrap(Canvas c, String text, float size, float max) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String next = line.length() == 0 ? word : line + " " + word;
                if (c.width(next, size) <= max || line.length() == 0) {
                    line.setLength(0);
                    line.append(next);
                } else {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** A colour a step lighter, for a hovered button. */
    public static int brighter(int rgb) {
        int r = Math.min(255, ((rgb >> 16) & 0xff) + 16);
        int g = Math.min(255, ((rgb >> 8) & 0xff) + 16);
        int b = Math.min(255, (rgb & 0xff) + 16);
        return (r << 16) | (g << 8) | b;
    }

    /** A colour half way to black, for a border of the same hue. */
    public static int darker(int rgb, float by) {
        int r = (int) (((rgb >> 16) & 0xff) * (1 - by));
        int g = (int) (((rgb >> 8) & 0xff) * (1 - by));
        int b = (int) ((rgb & 0xff) * (1 - by));
        return (r << 16) | (g << 8) | b;
    }
}
