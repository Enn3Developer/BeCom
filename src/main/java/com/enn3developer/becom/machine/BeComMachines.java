package com.enn3developer.becom.machine;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.enn3developer.becom.logic.net.CableTier;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/**
 * BeCom's GregTech machines and their IDs, and the block they stand in, {@code becom:machine}. The range is one of the
 * three fixed tunables. Every ID is checked before anything is registered, so another mod already using one stops the
 * game with a clear error instead of two machines quietly sharing an ID and corrupting worlds.
 */
public final class BeComMachines {

    public static final int FIRST_ID = 25000;
    public static final int LAST_ID = 25199;

    public static final int EXCHANGE_ID = 25001;
    public static final int ROUTER_MV_ID = 25002;
    public static final int ROUTER_ID = 25003;
    public static final int ANTENNA_T1_ID = 25004;
    public static final int ANTENNA_T2_ID = 25005;
    public static final int ANTENNA_T3_ID = 25006;
    public static final int TRANSCEIVER_ID = 25007;
    public static final int ME_BRIDGE_ID = 25008;
    public static final int VIDEOPHONE_ID = 25009;
    public static final int LAUNCH_PAD_ID = 25010;
    public static final int SATELLITE_DISH_ID = 25011;
    public static final int PORTAL_RELAY_ID = 25012;
    public static final int ROUTER_HV_ID = 25013;
    public static final int PORTAL_RELAY_HV_ID = 25014;
    public static final int PORTAL_RELAY_EV_ID = 25015;
    public static final int LINE_HATCH_ID = 25016;
    public static final int LINE_HATCH_MV_ID = 25017;
    public static final int LINE_HATCH_HV_ID = 25018;
    public static final int RACK_ID = 25019;
    public static final int RACK_MV_ID = 25020;
    public static final int RACK_HV_ID = 25021;
    public static final int APP_MIRROR_ID = 25022;
    public static final int ME_MIRROR_ID = 25023;

    public static BlockBeComMachines block;
    public static MTEExchange exchange;
    public static MTERouter router;
    public static MTERouter routerMV;
    public static MTERouter routerHV;
    public static MTEAntenna antennaT1;
    public static MTEAntenna antennaT2;
    public static MTEAntenna antennaT3;
    public static MTETransceiver transceiver;
    public static MTEMeBridge meBridge;
    public static MTEVideophone videophone;
    public static MTELaunchPad launchPad;
    public static MTESatelliteDish satelliteDish;
    public static MTEPortalRelay portalRelay;
    public static MTEPortalRelay portalRelayHV;
    public static MTEPortalRelay portalRelayEV;
    public static MTEHatchLine lineHatch;
    public static MTEHatchLine lineHatchMV;
    public static MTEHatchLine lineHatchHV;
    public static MTEHatchRack rack;
    public static MTEHatchRack rackMV;
    public static MTEHatchRack rackHV;
    public static MTEAppMirror appMirror;
    public static MTEMeMirror meMirror;

    private BeComMachines() {}

    /** Every ID BeCom registers, with the internal name of the machine that gets it. */
    public static Map<Integer, String> ids() {
        Map<Integer, String> ids = new LinkedHashMap<>();
        ids.put(EXCHANGE_ID, "becom.exchange");
        ids.put(ROUTER_MV_ID, "becom.router_mv");
        ids.put(ROUTER_ID, "becom.router");
        ids.put(ANTENNA_T1_ID, "becom.antenna_t1");
        ids.put(ANTENNA_T2_ID, "becom.antenna_t2");
        ids.put(ANTENNA_T3_ID, "becom.antenna_t3");
        ids.put(TRANSCEIVER_ID, "becom.transceiver");
        ids.put(ME_BRIDGE_ID, "becom.me_bridge");
        ids.put(VIDEOPHONE_ID, "becom.videophone");
        ids.put(LAUNCH_PAD_ID, "becom.launch_pad");
        ids.put(SATELLITE_DISH_ID, "becom.satellite_dish");
        ids.put(PORTAL_RELAY_ID, "becom.portal_relay");
        ids.put(ROUTER_HV_ID, "becom.router_hv");
        ids.put(PORTAL_RELAY_HV_ID, "becom.portal_relay_hv");
        ids.put(PORTAL_RELAY_EV_ID, "becom.portal_relay_ev");
        ids.put(LINE_HATCH_ID, "becom.line_hatch");
        ids.put(LINE_HATCH_MV_ID, "becom.line_hatch_mv");
        ids.put(LINE_HATCH_HV_ID, "becom.line_hatch_hv");
        ids.put(RACK_ID, "becom.switching_rack");
        ids.put(RACK_MV_ID, "becom.switching_rack_mv");
        ids.put(RACK_HV_ID, "becom.switching_rack_hv");
        ids.put(APP_MIRROR_ID, "becom.app_mirror");
        ids.put(ME_MIRROR_ID, "becom.me_mirror");
        return ids;
    }

    /**
     * Throws naming the first ID another mod already uses.
     *
     * @throws IllegalStateException when an ID is out of BeCom's range or taken
     */
    public static void checkIds() {
        for (Map.Entry<Integer, String> entry : ids().entrySet()) {
            int id = entry.getKey();
            if (id < FIRST_ID || id > LAST_ID) {
                throw new IllegalStateException(
                    "BeCom machine " + entry
                        .getValue() + " has ID " + id + ", outside BeCom's range " + FIRST_ID + "-" + LAST_ID);
            }
            IMetaTileEntity existing = GregTechAPI.METATILEENTITIES[id];
            if (existing != null) {
                throw new IllegalStateException(
                    "BeCom needs GregTech machine ID " + id
                        + " for "
                        + entry.getValue()
                        + ", but "
                        + existing.getMetaName()
                        + " ("
                        + existing.getClass()
                            .getName()
                        + ") already uses it. Another mod took an ID in BeCom's range "
                        + FIRST_ID
                        + "-"
                        + LAST_ID
                        + "; BeCom stops here rather than let two machines share an ID.");
            }
        }
    }

    /** A machine's item: BeCom's machine block at the machine's ID. */
    public static ItemStack stack(IMetaTileEntity machine, long amount) {
        return new ItemStack(
            block,
            (int) amount,
            machine.getBaseMetaTileEntity()
                .getMetaTileID());
    }

    public static void register() {
        checkIds();
        block = new BlockBeComMachines();
        BeComTextures.init();
        exchange = new MTEExchange(EXCHANGE_ID, "becom.exchange", "Exchange");
        router = new MTERouter(ROUTER_ID, "becom.router", "Router", CableTier.PHONE);
        routerMV = new MTERouter(ROUTER_MV_ID, "becom.router_mv", "Router (MV)", CableTier.COAXIAL);
        routerHV = new MTERouter(ROUTER_HV_ID, "becom.router_hv", "Router (HV)", CableTier.FIBRE);
        antennaT1 = new MTEAntenna(ANTENNA_T1_ID, "becom.antenna_t1", "Antenna T1 Head", 1);
        antennaT2 = new MTEAntenna(ANTENNA_T2_ID, "becom.antenna_t2", "Antenna T2 Head", 2);
        antennaT3 = new MTEAntenna(ANTENNA_T3_ID, "becom.antenna_t3", "Antenna T3 Head", 3);
        transceiver = new MTETransceiver(TRANSCEIVER_ID, "becom.transceiver", "Transceiver");
        meBridge = new MTEMeBridge(ME_BRIDGE_ID, "becom.me_bridge", "ME Bridge");
        videophone = new MTEVideophone(VIDEOPHONE_ID, "becom.videophone", "Videophone");
        launchPad = new MTELaunchPad(LAUNCH_PAD_ID, "becom.launch_pad", "Satellite Launch Pad");
        satelliteDish = new MTESatelliteDish(SATELLITE_DISH_ID, "becom.satellite_dish", "Satellite Dish");
        portalRelay = new MTEPortalRelay(PORTAL_RELAY_ID, "becom.portal_relay", "Portal Relay", 2);
        portalRelayHV = new MTEPortalRelay(PORTAL_RELAY_HV_ID, "becom.portal_relay_hv", "Portal Relay (HV)", 3);
        portalRelayEV = new MTEPortalRelay(PORTAL_RELAY_EV_ID, "becom.portal_relay_ev", "Portal Relay (EV)", 4);
        lineHatch = new MTEHatchLine(LINE_HATCH_ID, "becom.line_hatch", "Line Hatch (LV)", CableTier.PHONE);
        lineHatchMV = new MTEHatchLine(LINE_HATCH_MV_ID, "becom.line_hatch_mv", "Line Hatch (MV)", CableTier.COAXIAL);
        lineHatchHV = new MTEHatchLine(LINE_HATCH_HV_ID, "becom.line_hatch_hv", "Line Hatch (HV)", CableTier.FIBRE);
        rack = new MTEHatchRack(RACK_ID, "becom.switching_rack", "Switching Rack (LV)", CableTier.PHONE);
        rackMV = new MTEHatchRack(RACK_MV_ID, "becom.switching_rack_mv", "Switching Rack (MV)", CableTier.COAXIAL);
        rackHV = new MTEHatchRack(RACK_HV_ID, "becom.switching_rack_hv", "Switching Rack (HV)", CableTier.FIBRE);
        appMirror = new MTEAppMirror(APP_MIRROR_ID, "becom.app_mirror", "App Store Mirror");
        meMirror = new MTEMeMirror(ME_MIRROR_ID, "becom.me_mirror", "ME App Store Mirror");
    }
}
