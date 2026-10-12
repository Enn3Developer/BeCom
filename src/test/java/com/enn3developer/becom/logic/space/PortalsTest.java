package com.enn3developer.becom.logic.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** DESIGN.md 9.4 and 9.5: the portals a relay works at, and the dimensions a pair joins. */
class PortalsTest {

    private static final String OVERWORLD = "net.minecraft.world.WorldProviderSurface";
    private static final String MOON = "micdoodle8.mods.galacticraft.core.moon.WorldProviderMoon";
    private static final String DEEP_DARK = "extrautils.worldgen.WorldProviderUnderdark";

    @Test
    void theTwilightForestsAndPersonalSpacesPortalsAreKnownByClass() {
        assertEquals(Portals.Kind.TWILIGHT_FOREST, Portals.ofBlockClass("twilightforest.block.BlockTFPortal"));
        assertEquals(
            Portals.Kind.PERSONAL_SPACE,
            Portals.ofBlockClass("me.eigenraven.personalspace.block.PortalBlock"));
        assertNull(Portals.ofBlockClass("net.minecraft.block.BlockStone"));
    }

    @Test
    void pairsJoinTheOverworldToTheNetherTheEndTheTwilightForestOrAPersonalDimension() {
        assertEquals(Portals.Kind.NETHER, Portals.pairKind(0, OVERWORLD, -1, "net.minecraft.world.WorldProviderHell"));
        assertEquals(Portals.Kind.END, Portals.pairKind(1, "net.minecraft.world.WorldProviderEnd", 0, OVERWORLD));
        assertEquals(Portals.Kind.TWILIGHT_FOREST, Portals.pairKind(0, OVERWORLD, 7, Portals.TWILIGHT_WORLD));
        assertEquals(Portals.Kind.PERSONAL_SPACE, Portals.pairKind(180, Portals.PERSONAL_WORLD, 0, OVERWORLD));
    }

    @Test
    void everyOtherPairIsRefused() {
        // DESIGN.md 9.5: the Deep Dark and the rest have no link in v1, and planets go through satellites.
        assertNull(Portals.pairKind(0, OVERWORLD, -100, DEEP_DARK));
        assertNull(Portals.pairKind(0, OVERWORLD, -28, MOON));
        // Both ends in the Overworld, or neither.
        assertNull(Portals.pairKind(0, OVERWORLD, 0, OVERWORLD));
        assertNull(
            Portals.pairKind(-1, "net.minecraft.world.WorldProviderHell", 1, "net.minecraft.world.WorldProviderEnd"));
    }

    @Test
    void reachIsASphereOfTheDistance() {
        // DESIGN.md 9.4: within 4 blocks.
        assertTrue(Portals.inReach(4, 0, 0, 4));
        assertTrue(Portals.inReach(2, 2, 2, 4));
        assertFalse(Portals.inReach(3, 3, 0, 4));
        assertFalse(Portals.inReach(0, -5, 0, 4));
    }
}
