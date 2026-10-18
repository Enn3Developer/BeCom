package com.enn3developer.becom.gui.paint;

import com.cleanroommc.modularui.api.drawable.IDrawable;

/**
 * Where a screen paints, in its design board's pixels: the client draws it with GL, scaled to the screen. Text sizes
 * are the board's font sizes; the client picks the nearest crisp size of Minecraft's font.
 */
public interface Canvas {

    /** A filled rectangle; {@code argb} with alpha 0 means opaque, as most colours here are written as RGB. */
    void rect(float x, float y, float w, float h, int argb);

    /** Text with the top of its line at {@code y}. */
    void text(String text, float x, float y, float size, int rgb);

    /** How wide {@code text} is at {@code size}, in board pixels. */
    float width(String text, float size);

    /** How tall a line of text at {@code size} is, in board pixels. */
    float lineHeight(float size);

    /** A player's face from their skin, {@code size} square. */
    void face(String player, float x, float y, float size);

    /**
     * An item by registry name, {@code "modid:name"} or {@code "modid:name:meta"}, as in a slot, {@code size} square.
     */
    void item(String item, float x, float y, float size);

    /** A drawable of ModularUI's, such as the video tiles, in a box. */
    void drawable(IDrawable drawable, float x, float y, float w, float h);

    /** Only what falls inside the box is drawn, until {@link #unclip()}. */
    void clip(float x, float y, float w, float h);

    void unclip();
}
