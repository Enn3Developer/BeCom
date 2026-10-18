package com.enn3developer.becom.client;

import net.minecraft.item.ItemStack;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.Tags;
import com.enn3developer.becom.machine.BeComMachines;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import gregtech.api.GregTechAPI;

/**
 * NEI lists each of BeCom's machines once, as BeCom's: GregTech's machine block lists every machine there is, BeCom's
 * among them, so its copies are hidden. NEI finds this class by its name and loads it only when it's installed.
 */
public final class NEIBeComConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        for (int id : BeComMachines.ids()
            .keySet()) API.hideItem(new ItemStack(GregTechAPI.sBlockMachines, 1, id));
    }

    @Override
    public String getName() {
        return BeCom.NAME;
    }

    @Override
    public String getVersion() {
        return Tags.VERSION;
    }
}
