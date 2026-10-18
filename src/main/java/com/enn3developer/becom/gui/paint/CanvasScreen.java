package com.enn3developer.becom.gui.paint;

import java.util.ArrayList;
import java.util.List;

/**
 * A screen painted from its design board: each frame it paints itself, and the buttons it paints are where clicks go.
 * Coordinates are board pixels. Client only in practice: the server builds the panel but never paints or clicks.
 */
public abstract class CanvasScreen {

    /** A clickable box and what a left and a right click do; either may be null. */
    private record Hit(float x, float y, float w, float h, Runnable left, Runnable right) {

        boolean contains(float px, float py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private float mouseX = -1;
    private float mouseY = -1;

    /** The board's size, in its pixels. */
    public abstract float width();

    public abstract float height();

    /** Paints one frame; the mouse is where it is, in board pixels. */
    protected abstract void paint(Canvas canvas);

    /** A key typed while the screen has focus; true when it used it. */
    public boolean key(char typed, int code) {
        return false;
    }

    /** The mouse wheel over the screen: positive up. */
    public void scroll(int amount) {}

    public final void paint(Canvas canvas, float mouseX, float mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        hits.clear();
        paint(canvas);
    }

    /** A box that does {@code left} on a left click and {@code right} on a right click. */
    public final void hit(float x, float y, float w, float h, Runnable left, Runnable right) {
        hits.add(new Hit(x, y, w, h, left, right));
    }

    public final void hit(float x, float y, float w, float h, Runnable left) {
        hit(x, y, w, h, left, null);
    }

    /** Whether the mouse is over a box, to light a button. */
    public final boolean hover(float x, float y, float w, float h) {
        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }

    /** Where the mouse was this frame, in board pixels: where a menu a right click opens goes. */
    public final float mouseX() {
        return mouseX;
    }

    public final float mouseY() {
        return mouseY;
    }

    /** A click at board pixels; the box painted last on top wins. True when something took it. */
    public final boolean click(float x, float y, int button) {
        mouseX = x;
        mouseY = y;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(x, y)) continue;
            Runnable action = button == 1 ? hit.right() : hit.left();
            if (action == null) return true;
            action.run();
            return true;
        }
        return clickedNothing(button);
    }

    /** A click on no button, such as one that closes a menu. */
    protected boolean clickedNothing(int button) {
        return false;
    }
}
