package com.enn3developer.becom.block;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;

/** BeCom's own blocks: the parts that aren't GT machines (DESIGN.md 15.1). */
public final class BeComBlocks {

    public static BlockTelephone telephone;
    public static BlockPhoneCable phoneCable;

    private BeComBlocks() {}

    public static void register() {
        telephone = new BlockTelephone();
        phoneCable = new BlockPhoneCable();
        telephone.setCreativeTab(GregTechAPI.TAB_GREGTECH);
        phoneCable.setCreativeTab(GregTechAPI.TAB_GREGTECH);
        GameRegistry.registerBlock(telephone, "telephone");
        GameRegistry.registerBlock(phoneCable, "phone_cable");
        GameRegistry.registerTileEntity(TileTelephone.class, "becom.telephone");
    }
}
