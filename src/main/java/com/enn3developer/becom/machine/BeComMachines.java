package com.enn3developer.becom.machine;

import java.util.LinkedHashMap;
import java.util.Map;

import com.enn3developer.becom.logic.net.NodeKind;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/**
 * BeCom's GregTech machines and their IDs (D18, DESIGN.md 14.1). The range is one of the three fixed tunables. Every
 * ID is checked before anything is registered, so another mod already using one stops the game with a clear error
 * instead of two machines quietly sharing an ID and corrupting worlds.
 */
public final class BeComMachines {

    public static final int FIRST_ID = 25000;
    public static final int LAST_ID = 25199;

    public static final int PYLON_HEAD_ID = 25000;
    public static final int REPEATER_HEAD_ID = 25001;
    public static final int ROUTER_HEAD_ID = 25002;
    public static final int LINE_TERMINAL_ID = 25003;
    public static final int ANTENNA_T1_ID = 25004;
    public static final int ANTENNA_T2_ID = 25005;
    public static final int ANTENNA_T3_ID = 25006;
    public static final int TRANSCEIVER_ID = 25007;
    public static final int ME_BRIDGE_ID = 25008;
    public static final int VIDEOPHONE_ID = 25009;
    public static final int LAUNCH_PAD_ID = 25010;
    public static final int SATELLITE_DISH_ID = 25011;
    public static final int PORTAL_RELAY_ID = 25012;

    public static MTEPylonHead pylonHead;
    public static MTEPylonHead repeaterHead;
    public static MTEPylonHead routerHead;
    public static MTELineTerminal lineTerminal;
    public static MTEAntenna antennaT1;
    public static MTEAntenna antennaT2;
    public static MTEAntenna antennaT3;
    public static MTETransceiver transceiver;
    public static MTEMeBridge meBridge;
    public static MTEVideophone videophone;
    public static MTELaunchPad launchPad;
    public static MTESatelliteDish satelliteDish;
    public static MTEPortalRelay portalRelay;

    private BeComMachines() {}

    /** Every ID BeCom registers, with the internal name of the machine that gets it. */
    public static Map<Integer, String> ids() {
        Map<Integer, String> ids = new LinkedHashMap<>();
        ids.put(PYLON_HEAD_ID, "becom.pylon_head");
        ids.put(REPEATER_HEAD_ID, "becom.repeater_head");
        ids.put(ROUTER_HEAD_ID, "becom.router_head");
        ids.put(LINE_TERMINAL_ID, "becom.line_terminal");
        ids.put(ANTENNA_T1_ID, "becom.antenna_t1");
        ids.put(ANTENNA_T2_ID, "becom.antenna_t2");
        ids.put(ANTENNA_T3_ID, "becom.antenna_t3");
        ids.put(TRANSCEIVER_ID, "becom.transceiver");
        ids.put(ME_BRIDGE_ID, "becom.me_bridge");
        ids.put(VIDEOPHONE_ID, "becom.videophone");
        ids.put(LAUNCH_PAD_ID, "becom.launch_pad");
        ids.put(SATELLITE_DISH_ID, "becom.satellite_dish");
        ids.put(PORTAL_RELAY_ID, "becom.portal_relay");
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

    public static void register() {
        checkIds();
        BeComTextures.init();
        pylonHead = new MTEPylonHead(PYLON_HEAD_ID, "becom.pylon_head", "Pylon Head", NodeKind.PYLON);
        repeaterHead = new MTEPylonHead(REPEATER_HEAD_ID, "becom.repeater_head", "Repeater Head", NodeKind.REPEATER);
        routerHead = new MTEPylonHead(ROUTER_HEAD_ID, "becom.router_head", "Router Head", NodeKind.ROUTER);
        lineTerminal = new MTELineTerminal(LINE_TERMINAL_ID, "becom.line_terminal", "Line Terminal");
        antennaT1 = new MTEAntenna(ANTENNA_T1_ID, "becom.antenna_t1", "Antenna T1 Head", 1);
        antennaT2 = new MTEAntenna(ANTENNA_T2_ID, "becom.antenna_t2", "Antenna T2 Head", 2);
        antennaT3 = new MTEAntenna(ANTENNA_T3_ID, "becom.antenna_t3", "Antenna T3 Head", 3);
        transceiver = new MTETransceiver(TRANSCEIVER_ID, "becom.transceiver", "Transceiver");
        meBridge = new MTEMeBridge(ME_BRIDGE_ID, "becom.me_bridge", "ME Bridge");
        videophone = new MTEVideophone(VIDEOPHONE_ID, "becom.videophone", "Videophone");
        launchPad = new MTELaunchPad(LAUNCH_PAD_ID, "becom.launch_pad", "Satellite Launch Pad");
        satelliteDish = new MTESatelliteDish(SATELLITE_DISH_ID, "becom.satellite_dish", "Satellite Dish");
        portalRelay = new MTEPortalRelay(PORTAL_RELAY_ID, "becom.portal_relay", "Portal Relay");
    }
}
