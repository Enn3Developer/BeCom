package com.enn3developer.becom.machine;

import com.enn3developer.becom.BeCom;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;

/**
 * BeCom's front overlays, drawn over GT's own casing textures the way GT's overlays are (DESIGN.md 15.1). Registered
 * with GT's icon loader, so they're stitched with GT's block atlas.
 */
public final class BeComTextures {

    public static final IIconContainer PYLON_HEAD = icon("OVERLAY_PYLON_HEAD");
    public static final IIconContainer REPEATER_HEAD = icon("OVERLAY_REPEATER_HEAD");
    public static final IIconContainer REPEATER_HEAD_GLOW = icon("OVERLAY_REPEATER_HEAD_GLOW");
    public static final IIconContainer ROUTER_HEAD = icon("OVERLAY_ROUTER_HEAD");
    public static final IIconContainer ROUTER_HEAD_GLOW = icon("OVERLAY_ROUTER_HEAD_GLOW");
    public static final IIconContainer LINE_TERMINAL = icon("OVERLAY_LINE_TERMINAL");
    public static final IIconContainer LINE_TERMINAL_GLOW = icon("OVERLAY_LINE_TERMINAL_GLOW");

    private BeComTextures() {}

    private static IIconContainer icon(String name) {
        return Textures.BlockIcons.custom(BeCom.MODID, "iconsets/" + name);
    }

    /** Loads the class, so every overlay is registered before textures are stitched. */
    public static void init() {}
}
