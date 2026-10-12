package com.enn3developer.becom.item;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;

/** BeCom's items (DESIGN.md 15.1): the handhelds. */
public final class BeComItems {

    public static ItemWalkieTalkie walkieTalkie;
    public static ItemSignalAnalyzer signalAnalyzer;
    public static ItemMobilePhone mobilePhone;

    private BeComItems() {}

    public static void register() {
        walkieTalkie = new ItemWalkieTalkie();
        signalAnalyzer = new ItemSignalAnalyzer();
        walkieTalkie.setCreativeTab(GregTechAPI.TAB_GREGTECH);
        signalAnalyzer.setCreativeTab(GregTechAPI.TAB_GREGTECH);
        GameRegistry.registerItem(walkieTalkie, "walkie_talkie");
        GameRegistry.registerItem(signalAnalyzer, "signal_analyzer");
        mobilePhone = new ItemMobilePhone();
        mobilePhone.setCreativeTab(GregTechAPI.TAB_GREGTECH);
        GameRegistry.registerItem(mobilePhone, "mobile_phone");
    }
}
