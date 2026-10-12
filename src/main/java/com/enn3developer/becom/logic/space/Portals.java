package com.enn3developer.becom.logic.space;

/**
 * What the Portal Relay knows about portals (DESIGN.md 9.4, 9.5, D22, D46): the kinds it works at, how it tells the
 * Twilight Forest's and Personal Space's portals and worlds by class name, so neither mod is a dependency, and which
 * two dimensions a pair may join. Pure, so it's tested without those mods.
 */
public final class Portals {

    /** The portals a relay works at; vanilla's two it knows by their own blocks. */
    public enum Kind {
        /** A Nether portal, on either side. */
        NETHER,
        /** An End portal, or the End's exit portal: the same block. */
        END,
        TWILIGHT_FOREST,
        PERSONAL_SPACE
    }

    public static final String TWILIGHT_PORTAL = "twilightforest.block.BlockTFPortal";
    public static final String PERSONAL_PORTAL = "me.eigenraven.personalspace.block.PortalBlock";
    public static final String TWILIGHT_WORLD = "twilightforest.world.WorldProviderTwilightForest";
    public static final String PERSONAL_WORLD = "me.eigenraven.personalspace.world.PersonalWorldProvider";

    public static final int OVERWORLD = 0;
    public static final int NETHER = -1;
    public static final int END = 1;
    /**
     * How far Minecraft looks for the far side's Nether portal, in that world's blocks, around where a portal leads.
     */
    public static final int NETHER_SEARCH = 128;
    /**
     * How far from where a portal leads Twilight Forest puts the far one: it looks 200 blocks around for one, and when
     * it has to make one in a biome the player can't enter yet, it moves it up to 600 blocks and builds it within 16 of
     * that spot (Twilight Forest 2.7.44's TFTeleporter).
     */
    public static final int TWILIGHT_SEARCH = 616;
    /** The widest portals, in blocks across: a Nether portal's frame holds 21, a Twilight Forest pool 15 by default. */
    public static final int NETHER_PORTAL_SIZE = 21;
    public static final int TWILIGHT_PORTAL_SIZE = 15;

    private Portals() {}

    /** The kind of portal a block of class {@code name} is, for the mods BeCom doesn't depend on; else null. */
    public static Kind ofBlockClass(String name) {
        if (TWILIGHT_PORTAL.equals(name)) return Kind.TWILIGHT_FOREST;
        if (PERSONAL_PORTAL.equals(name)) return Kind.PERSONAL_SPACE;
        return null;
    }

    /**
     * The portal that leads from the Overworld to {@code dimension}, whose world provider is of class
     * {@code provider}; null for a dimension no relay reaches in v1 (DESIGN.md 9.5).
     */
    public static Kind leadingTo(int dimension, String provider) {
        if (dimension == NETHER) return Kind.NETHER;
        if (dimension == END) return Kind.END;
        if (TWILIGHT_WORLD.equals(provider)) return Kind.TWILIGHT_FOREST;
        if (PERSONAL_WORLD.equals(provider)) return Kind.PERSONAL_SPACE;
        return null;
    }

    /**
     * The portal a pair of relays in these two dimensions goes through: one end in the Overworld, the other in a
     * dimension a portal leads to from there (DESIGN.md 9.5). Null for any other pair.
     */
    public static Kind pairKind(int dimensionA, String providerA, int dimensionB, String providerB) {
        if (dimensionA == OVERWORLD && dimensionB != OVERWORLD) return leadingTo(dimensionB, providerB);
        if (dimensionB == OVERWORLD && dimensionA != OVERWORLD) return leadingTo(dimensionA, providerA);
        return null;
    }

    /**
     * Whether relays at these two spots stand on the two sides of one portal (DESIGN.md 9.4). Through a Nether or a
     * Twilight Forest portal, one relay's spot, scaled by the two worlds' movement factors the way Minecraft scales a
     * portal trip, lands within that portal's search of the other's, one way or the other. Each relay stands within
     * {@code reach} of some block of its portal, which may be the far end of a wide one. The End, and each personal
     * dimension, is reached at one place, so any of their portals will do.
     *
     * @param factorA the movement factor of relay A's world: 8 for the Nether, 1 for most
     */
    public static boolean sameLink(Kind kind, double factorA, int xA, int zA, double factorB, int xB, int zB,
        double reach) {
        if (kind == Kind.END || kind == Kind.PERSONAL_SPACE) return true;
        int search = kind == Kind.NETHER ? NETHER_SEARCH : TWILIGHT_SEARCH;
        double slack = reach + (kind == Kind.NETHER ? NETHER_PORTAL_SIZE : TWILIGHT_PORTAL_SIZE) - 1;
        double ratio = factorA / factorB;
        boolean toB = near(xA * ratio, zA * ratio, xB, zB, search + slack * (1 + ratio));
        boolean toA = near(xB / ratio, zB / ratio, xA, zA, search + slack * (1 + 1 / ratio));
        return toB || toA;
    }

    private static boolean near(double x, double z, int toX, int toZ, double within) {
        return Math.abs(toX - x) <= within && Math.abs(toZ - z) <= within;
    }

    /** Whether a block {@code dx}, {@code dy}, {@code dz} from a relay is within {@code reach} blocks of it. */
    public static boolean inReach(int dx, int dy, int dz, double reach) {
        return (double) dx * dx + (double) dy * dy + (double) dz * dz <= reach * reach;
    }
}
