package com.enn3developer.becom.logic.net;

/**
 * Every kind of node a BeCom network holds (DESIGN.md 14). Saved by name, so constants can be added but never
 * renamed.
 */
public enum NodeKind {

    /** A plain Pylon Head: passive, carries a line through (DESIGN.md 5.1). */
    PYLON(2, false, true, 0),
    /** A Pylon Head that takes power from a cable and regenerates the signal (DESIGN.md 5.3). */
    REPEATER(2, true, true, 0),
    /** A powered Pylon Head with 15 link slots that joins lines into a network (DESIGN.md 5.4). */
    ROUTER(15, true, true, 0),
    /** A ground block where a line starts or ends, serving one phone (DESIGN.md 5.2). */
    TERMINAL(1, true, false, 0),
    /**
     * Antennas (DESIGN.md 7). Their wrench links are bridges onto wired lines; links between antennas come from their
     * surveys and aren't saved. Like a Router, an antenna joins whatever it bridges into one network (X19).
     */
    ANTENNA_T1(15, true, false, 1),
    ANTENNA_T2(15, true, false, 2),
    ANTENNA_T3(15, true, false, 3),
    /** A radio on a block (DESIGN.md 8): kept here so the network knows where every Transceiver is (DESIGN.md 14). */
    TRANSCEIVER(0, true, false, 0),
    /**
     * The ME Bridge (DESIGN.md 8.3, D45): joins an ME network to a BeCom network. Its one wrench link is a bridge onto
     * any other node within the bridge distance, a short stretch of line it drives, like an antenna's.
     */
    ME_BRIDGE(1, true, false, 0),
    /**
     * The Satellite Dish (DESIGN.md 9.2): its one wrench link is a bridge onto an antenna, a Pylon Head or a Line
     * Terminal within the bridge distance, and it joins that network to the satellites over its dimension.
     */
    SATELLITE_DISH(1, true, false, 0),
    /**
     * The Portal Relay (DESIGN.md 9.4): its one wrench link is a bridge onto its own network, as a dish's is (X36), and
     * its partner, the relay on the far side of a portal, is a link of its own between two dimensions.
     */
    PORTAL_RELAY(1, true, false, 0);

    private final int linkSlots;
    private final boolean powered;
    private final boolean pylonHead;
    private final int antennaTier;

    NodeKind(int linkSlots, boolean powered, boolean pylonHead, int antennaTier) {
        this.linkSlots = linkSlots;
        this.powered = powered;
        this.pylonHead = pylonHead;
        this.antennaTier = antennaTier;
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

    /** 1 to 3 for an antenna, 0 for anything else. */
    public int antennaTier() {
        return antennaTier;
    }

    public boolean antenna() {
        return antennaTier > 0;
    }

    /** Reaches the bridge distance to link, instead of a pylon's span: an antenna, an ME Bridge, a dish or a relay. */
    public boolean bridge() {
        return antenna() || this == ME_BRIDGE || gateway();
    }

    /** Joins its network to others far away, and links only onto an antenna, a Pylon Head or a Line Terminal. */
    public boolean gateway() {
        return this == SATELLITE_DISH || this == PORTAL_RELAY;
    }

    /** Needs its multiblock formed before it works or links. */
    public boolean structure() {
        return pylonHead || antenna() || this == SATELLITE_DISH;
    }

    /** The antenna kind of a tier, 1 to 3. */
    public static NodeKind antenna(int tier) {
        return switch (tier) {
            case 1 -> ANTENNA_T1;
            case 2 -> ANTENNA_T2;
            case 3 -> ANTENNA_T3;
            default -> throw new IllegalArgumentException("No antenna tier " + tier);
        };
    }
}
