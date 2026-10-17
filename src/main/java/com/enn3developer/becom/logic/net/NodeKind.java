package com.enn3developer.becom.logic.net;

/**
 * Every kind of node a BeCom network holds. Saved by name, so constants can be added but never
 * renamed.
 */
public enum NodeKind {

    /** A Pylon Head: passive, holds the cables of a line's spans. */
    PYLON(2, false, true, 0),
    /** A ground machine where a building's line starts: it serves every device on its cable. */
    TERMINAL(1, true, false, 0),
    /**
     * The Exchange: a ground multiblock whose Switching Racks switch calls between every line wired
     * to it.
     */
    EXCHANGE(15, true, false, 0),
    /**
     * Antennas. Their wrench links are wired backhaul; links between antennas come from their surveys
     * and aren't saved. Like an Exchange, an antenna joins whatever it's wired to into one network.
     */
    ANTENNA_T1(15, true, false, 1),
    ANTENNA_T2(15, true, false, 2),
    ANTENNA_T3(15, true, false, 3),
    /** A radio on a block: kept here so the network knows where every Transceiver is. */
    TRANSCEIVER(0, true, false, 0),
    /**
     * The ME Bridge: joins an ME network to a BeCom network. Its one wrench link is a bridge onto
     * a Pylon Head, a Line Terminal or an antenna within the bridge distance, a short stretch of line it drives,
     * like an antenna's.
     */
    ME_BRIDGE(1, true, false, 0),
    /**
     * The Satellite Dish: its one wrench link is a bridge onto an antenna, a Pylon Head or a Line
     * Terminal within the bridge distance, and it joins that network to the satellites over its dimension.
     */
    SATELLITE_DISH(1, true, false, 0),
    /**
     * The Portal Relay: its one wrench link is a bridge onto its own network, as a dish's is, and
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

    /** Powered nodes work only while loaded and powered, and end the runs of wire between them (5.7). */
    public boolean powered() {
        return powered;
    }

    /** Sits on a frame column with a span limit and a clearance check. */
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

    /** Joins its network to others far away, and is wired only to an antenna, a pylon, a terminal or an Exchange. */
    public boolean gateway() {
        return this == SATELLITE_DISH || this == PORTAL_RELAY;
    }

    /** Needs its multiblock formed before it works or links. */
    public boolean structure() {
        return pylonHead || antenna() || this == SATELLITE_DISH || this == EXCHANGE;
    }

    /** Stands on the ground and ends lines: a Line Terminal or an Exchange, which pylons lead to. */
    public boolean ground() {
        return this == TERMINAL || this == EXCHANGE;
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
