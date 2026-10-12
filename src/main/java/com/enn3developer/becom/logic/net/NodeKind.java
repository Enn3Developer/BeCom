package com.enn3developer.becom.logic.net;

/**
 * Every kind of node a BeCom network holds (DESIGN.md 14). Saved by name, so constants can be added but never
 * renamed.
 */
public enum NodeKind {

    /** A plain Pylon Head: passive, carries a line through (DESIGN.md 5.1). */
    PYLON(2, false, true),
    /** A Pylon Head that takes power from a cable and regenerates the signal (DESIGN.md 5.3). */
    REPEATER(2, true, true),
    /** A powered Pylon Head with 15 link slots that joins lines into a network (DESIGN.md 5.4). */
    ROUTER(15, true, true),
    /** A ground block where a line starts or ends, serving one phone (DESIGN.md 5.2). */
    TERMINAL(1, true, false);

    private final int linkSlots;
    private final boolean powered;
    private final boolean pylonHead;

    NodeKind(int linkSlots, boolean powered, boolean pylonHead) {
        this.linkSlots = linkSlots;
        this.powered = powered;
        this.pylonHead = pylonHead;
    }

    public int linkSlots() {
        return linkSlots;
    }

    /** Powered nodes work only while loaded and powered (D11), and regenerate the line signal (DESIGN.md 5.7). */
    public boolean powered() {
        return powered;
    }

    /** Sits on a frame column with a span limit and a clearance check (DESIGN.md 5.1). */
    public boolean pylonHead() {
        return pylonHead;
    }
}
