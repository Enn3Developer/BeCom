package com.enn3developer.becom.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.config.BeComConfig;

/**
 * BeCom's parts for space: the Satellite, the Satellite Rocket a Launch Pad puts into orbit, and
 * the Rocket Radio Receiver a rocket carries in its cargo. They do nothing in the hand; what holds them does.
 */
public class ItemSpacePart extends Item {

    // A Launch Pad's input bus can hold a stack of rockets to launch one by one.
    private static final int STACK = 16;

    private final String name;

    public ItemSpacePart(String name) {
        this.name = name;
        setUnlocalizedName(BeCom.MODID + "." + name);
        setTextureName(BeCom.MODID + ":" + name);
        setMaxStackSize(STACK);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(
            StatCollector.translateToLocalFormatted(
                "becom." + name + ".info",
                BeComConfig.get()
                    .launchFuel()));
    }
}
