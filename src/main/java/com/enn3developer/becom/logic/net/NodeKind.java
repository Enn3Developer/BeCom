package com.enn3developer.becom.logic.net;

/**
 * Every kind of node a BeCom network holds. Saved by name, so constants can be added but never
 * renamed.
 */
public enum NodeKind {

    /** A ground machine that routes the calls of every bus of cable joining it. */
    ROUTER(0),
    /** The Exchange: a multiblock whose Switching Racks switch calls between every bus its Line Hatches join. */
    EXCHANGE(0),
    /**
     * Antennas. Links between antennas come from their surveys and aren't saved. Through its Line Hatches, an antenna
     * is a router to the buses they join.
     */
    ANTENNA_T1(1),
    ANTENNA_T2(2),
    ANTENNA_T3(3),
    /** A radio on a block: kept here so the network knows where every Transceiver is. */
    TRANSCEIVER(0),
    /** The ME Bridge: joins an ME network to a BeCom network, by its cable to a router. */
    ME_BRIDGE(0),
    /** The Satellite Dish: joins the router its Line Hatch's bus reaches to the satellites over its dimension. */
    SATELLITE_DISH(0),
    /**
     * The Portal Relay: joins the router its cable reaches to its partner's, the relay by the far side of a portal,
     * a link of its own between two dimensions.
     */
    PORTAL_RELAY(0);

    private final int antennaTier;

    NodeKind(int antennaTier) {
        this.antennaTier = antennaTier;
    }

    /** 1 to 3 for an antenna, 0 for anything else. */
    public int antennaTier() {
        return antennaTier;
    }

    public boolean antenna() {
        return antennaTier > 0;
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
