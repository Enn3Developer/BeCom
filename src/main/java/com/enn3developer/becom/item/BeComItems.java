package com.enn3developer.becom.item;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;

/** BeCom's items: the handhelds, the BeCom Cards, the parts for space, and the crafting parts. */
public final class BeComItems {

    public static ItemWalkieTalkie walkieTalkie;
    public static ItemSignalAnalyzer signalAnalyzer;
    public static ItemMobilePhone mobilePhone;
    public static ItemMePhone mePhone;
    public static ItemSpacePart satellite;
    public static ItemSpacePart satelliteRocket;
    public static ItemSpacePart rocketRadioReceiver;
    public static ItemBecomCard becomCard;
    public static ItemPart part;

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
        mePhone = new ItemMePhone();
        mePhone.setCreativeTab(GregTechAPI.TAB_GREGTECH);
        GameRegistry.registerItem(mePhone, "me_phone");
        satellite = new ItemSpacePart("satellite");
        satelliteRocket = new ItemSpacePart("satellite_rocket");
        rocketRadioReceiver = new ItemSpacePart("rocket_radio_receiver");
        for (ItemSpacePart space : new ItemSpacePart[] { satellite, satelliteRocket, rocketRadioReceiver }) {
            space.setCreativeTab(GregTechAPI.TAB_GREGTECH);
        }
        GameRegistry.registerItem(satellite, "satellite");
        GameRegistry.registerItem(satelliteRocket, "satellite_rocket");
        GameRegistry.registerItem(rocketRadioReceiver, "rocket_radio_receiver");
        becomCard = new ItemBecomCard();
        becomCard.setCreativeTab(GregTechAPI.TAB_GREGTECH);
        GameRegistry.registerItem(becomCard, "becom_card");
        part = new ItemPart();
        part.setCreativeTab(GregTechAPI.TAB_GREGTECH);
        GameRegistry.registerItem(part, "part");
    }
}
