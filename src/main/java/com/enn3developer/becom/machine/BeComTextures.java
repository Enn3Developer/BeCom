package com.enn3developer.becom.machine;

import com.enn3developer.becom.BeCom;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;

/**
 * BeCom's front overlays, drawn over GT's own casing textures the way GT's overlays are. Registered
 * with GT's icon loader, so they're stitched with GT's block atlas.
 */
public final class BeComTextures {

    public static final IIconContainer PYLON_HEAD = icon("OVERLAY_PYLON_HEAD");
    public static final IIconContainer EXCHANGE = icon("OVERLAY_EXCHANGE");
    public static final IIconContainer EXCHANGE_GLOW = icon("OVERLAY_EXCHANGE_GLOW");
    public static final IIconContainer ROUTER = icon("OVERLAY_ROUTER");
    public static final IIconContainer ROUTER_GLOW = icon("OVERLAY_ROUTER_GLOW");
    public static final IIconContainer LINE_HATCH = icon("OVERLAY_LINE_HATCH");
    public static final IIconContainer LINE_HATCH_GLOW = icon("OVERLAY_LINE_HATCH_GLOW");
    public static final IIconContainer ANTENNA_T1 = icon("OVERLAY_ANTENNA_T1");
    public static final IIconContainer ANTENNA_T1_GLOW = icon("OVERLAY_ANTENNA_T1_GLOW");
    public static final IIconContainer ANTENNA_T2 = icon("OVERLAY_ANTENNA_T2");
    public static final IIconContainer ANTENNA_T2_GLOW = icon("OVERLAY_ANTENNA_T2_GLOW");
    public static final IIconContainer ANTENNA_T3 = icon("OVERLAY_ANTENNA_T3");
    public static final IIconContainer ANTENNA_T3_GLOW = icon("OVERLAY_ANTENNA_T3_GLOW");
    public static final IIconContainer TRANSCEIVER = icon("OVERLAY_TRANSCEIVER");
    public static final IIconContainer TRANSCEIVER_GLOW = icon("OVERLAY_TRANSCEIVER_GLOW");
    public static final IIconContainer ME_BRIDGE = icon("OVERLAY_ME_BRIDGE");
    public static final IIconContainer ME_BRIDGE_GLOW = icon("OVERLAY_ME_BRIDGE_GLOW");
    public static final IIconContainer VIDEOPHONE = icon("OVERLAY_VIDEOPHONE");
    public static final IIconContainer VIDEOPHONE_GLOW = icon("OVERLAY_VIDEOPHONE_GLOW");
    public static final IIconContainer LAUNCH_PAD = icon("OVERLAY_LAUNCH_PAD");
    public static final IIconContainer LAUNCH_PAD_GLOW = icon("OVERLAY_LAUNCH_PAD_GLOW");
    public static final IIconContainer SATELLITE_DISH = icon("OVERLAY_SATELLITE_DISH");
    public static final IIconContainer SATELLITE_DISH_GLOW = icon("OVERLAY_SATELLITE_DISH_GLOW");
    public static final IIconContainer PORTAL_RELAY = icon("OVERLAY_PORTAL_RELAY");
    public static final IIconContainer PORTAL_RELAY_GLOW = icon("OVERLAY_PORTAL_RELAY_GLOW");

    private BeComTextures() {}

    private static IIconContainer icon(String name) {
        return Textures.BlockIcons.custom(BeCom.MODID, "iconsets/" + name);
    }

    /** Loads the class, so every overlay is registered before textures are stitched. */
    public static void init() {}
}
