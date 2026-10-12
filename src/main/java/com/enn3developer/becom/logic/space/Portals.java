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

    /** Whether a block {@code dx}, {@code dy}, {@code dz} from a relay is within {@code reach} blocks of it. */
    public static boolean inReach(int dx, int dy, int dz, double reach) {
        return (double) dx * dx + (double) dy * dy + (double) dz * dz <= reach * reach;
    }
}
