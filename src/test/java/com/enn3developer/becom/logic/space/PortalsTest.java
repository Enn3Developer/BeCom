package com.enn3developer.becom.logic.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

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
        // The mods' own class names, as their jars have them (Twilight Forest 2.7.44, Personal Space 1.0.41).
        assertEquals(
            Portals.Kind.TWILIGHT_FOREST,
            Portals.pairKind(0, OVERWORLD, 7, "twilightforest.world.WorldProviderTwilightForest"));
        assertEquals(
            Portals.Kind.PERSONAL_SPACE,
            Portals.pairKind(180, "me.eigenraven.personalspace.world.PersonalWorldProvider", 0, OVERWORLD));
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
    void aNetherPairStandsWhereOnePortalLeads() {
        // DESIGN.md 9.4: on opposite sides of a portal. The Nether's coordinates are an eighth of the Overworld's, and
        // Minecraft looks for the far portal within 128 blocks of where that puts you.
        assertTrue(Portals.sameLink(Portals.Kind.NETHER, 1, 800, 800, 8, 100, 100, 4));
        assertTrue(Portals.sameLink(Portals.Kind.NETHER, 8, 100, 100, 1, 800, 800, 4));
        assertTrue(Portals.sameLink(Portals.Kind.NETHER, 1, 1000, 0, 8, 0, 0, 4));
        // Two Overworld portals 8,000 blocks apart can't both lead to one Nether spot.
        assertFalse(Portals.sameLink(Portals.Kind.NETHER, 1, 8000, 0, 8, 100, 0, 4));
        assertFalse(Portals.sameLink(Portals.Kind.NETHER, 8, 100, 0, 1, 8000, 0, 4));
        assertFalse(Portals.sameLink(Portals.Kind.NETHER, 1, 0, 8000, 8, 0, 0, 4));
    }

    @Test
    void aWideNetherPortalsFarEndCounts() {
        // A portal 21 blocks wide: the search finds one end of it, and the relay stands by the other.
        assertTrue(Portals.sameLink(Portals.Kind.NETHER, 1, 800, 0, 8, 250, 0, 4));
        assertFalse(Portals.sameLink(Portals.Kind.NETHER, 1, 800, 0, 8, 260, 0, 4));
    }

    @Test
    void aTwilightForestPairStandsWhereTwilightForestPutsTheFarPortal() {
        // The Twilight Forest keeps the Overworld's scale, but looks 200 blocks around for the far portal.
        assertTrue(Portals.sameLink(Portals.Kind.TWILIGHT_FOREST, 1, 500, 500, 1, 600, 500, 4));
        assertTrue(Portals.sameLink(Portals.Kind.TWILIGHT_FOREST, 1, 500, 500, 1, 700, 500, 4));
        // A new portal moved out of a biome the player can't enter yet: up to 600 blocks, then built within 16.
        assertTrue(Portals.sameLink(Portals.Kind.TWILIGHT_FOREST, 1, 0, 0, 1, 616, 0, 4));
        assertTrue(Portals.sameLink(Portals.Kind.TWILIGHT_FOREST, 1, 616, -616, 1, 0, 0, 4));
        assertFalse(Portals.sameLink(Portals.Kind.TWILIGHT_FOREST, 1, 500, 500, 1, 1200, 500, 4));
    }

    @Test
    void theEndAndAPersonalDimensionAreReachedAtOnePlace() {
        assertTrue(Portals.sameLink(Portals.Kind.END, 1, 5000, -3000, 1, 0, 0, 4));
        assertTrue(Portals.sameLink(Portals.Kind.PERSONAL_SPACE, 1, 5000, -3000, 1, 0, 0, 4));
    }

    @Test
    void aPersonalSpacePairGoesThroughAPortalIntoTheFarRelaysDimension() {
        // Each personal dimension has portals of its own: the Overworld relay's must lead into the far relay's.
        assertTrue(Portals.leadsInto(Portals.Kind.PERSONAL_SPACE, Set.of(180, 181), 180));
        assertFalse(Portals.leadsInto(Portals.Kind.PERSONAL_SPACE, Set.of(181), 180));
        assertFalse(Portals.leadsInto(Portals.Kind.PERSONAL_SPACE, Set.of(), 180));
        // A portal tile BeCom can't read leads anywhere, as before it read them.
        assertTrue(Portals.leadsInto(Portals.Kind.PERSONAL_SPACE, Set.of(Portals.UNKNOWN_DIMENSION), 180));
        // The other kinds don't look at Personal Space portals.
        assertTrue(Portals.leadsInto(Portals.Kind.NETHER, Set.of(), -1));
        assertTrue(Portals.leadsInto(Portals.Kind.END, Set.of(181), 1));
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
