package com.enn3developer.becom.gui.paint;

/**
 * The design boards' icons, cell by cell: each rectangle is {x, y, w, h}, and a fifth value of 1 marks a hole, painted
 * in the background's colour. Most are 8 cells square; the size says when one isn't.
 */
public final class Glyphs {

    private Glyphs() {}

    public static final int[][] PHONE = { { 1, 1, 2, 2 }, { 1, 3, 1, 2 }, { 2, 5, 1, 1 }, { 3, 6, 2, 1 },
        { 5, 5, 2, 2 } };
    public static final int[][] CONTACTS = { { 3, 1, 2, 2 }, { 2, 4, 4, 1 }, { 1, 5, 6, 2 } };
    public static final int[][] RECENT = { { 2, 1, 4, 1 }, { 1, 2, 1, 4 }, { 6, 2, 1, 4 }, { 2, 6, 4, 1 },
        { 4, 2, 1, 2 }, { 4, 4, 2, 1 } };
    public static final int[][] SIGNAL = { { 1, 5, 1, 2 }, { 3, 3, 1, 4 }, { 5, 1, 1, 6 } };
    public static final int[][] SETTINGS = { { 3, 0, 2, 8 }, { 0, 3, 8, 2 }, { 1, 1, 6, 6 }, { 3, 3, 2, 2, 1 } };
    public static final int[][] STORE = { { 1, 2, 6, 5 }, { 2, 1, 1, 1 }, { 5, 1, 1, 1 }, { 3, 0, 2, 1 },
        { 3, 4, 2, 1, 1 } };
    public static final int[][] CAMERA = { { 0, 2, 5, 4 }, { 5, 3, 3, 2 } };
    public static final int[][] REAR = { { 1, 2, 6, 5 }, { 3, 1, 2, 1 }, { 3, 3, 2, 2, 1 } };
    public static final int[][] ADD = { { 3, 0, 2, 8 }, { 0, 3, 8, 2 } };
    /** 7 cells square. */
    public static final int[][] CROSS = { { 0, 0, 2, 2 }, { 2, 2, 3, 3 }, { 5, 0, 2, 2 }, { 0, 5, 2, 2 },
        { 5, 5, 2, 2 } };
    /** 6 cells square: the hardware Back button. */
    public static final int[][] BACK = { { 1, 2, 1, 2 }, { 2, 1, 1, 4 }, { 3, 0, 1, 6 } };
    /** 6 cells square: the Settings page's padlock. */
    public static final int[][] LOCK = { { 1, 0, 4, 1 }, { 1, 0, 1, 3 }, { 4, 0, 1, 3 }, { 0, 3, 6, 3 } };
    /** 5 cells square: an app's "network" use. */
    public static final int[][] NETWORK = { { 0, 2, 5, 1 }, { 3, 1, 1, 3 } };
    /** 5 cells square: an app's "notifications" use. */
    public static final int[][] NOTIFY = { { 0, 0, 5, 3 }, { 2, 3, 1, 2 } };
    /** A store app with no icon of its own: a tank, from the store board. */
    public static final int[][] APP = { { 1, 4, 6, 3 }, { 2, 2, 4, 2 }, { 3, 1, 2, 1 } };
}
