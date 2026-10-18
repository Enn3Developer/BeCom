package com.enn3developer.becom.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.enn3developer.becom.BeCom;

/**
 * The Signal Analyzer: while held, an overlay shows SNR, level, the serving antenna and the
 * biggest loss, refreshed by the server every second. Needs no power.
 */
public class ItemSignalAnalyzer extends Item {

    public ItemSignalAnalyzer() {
        setUnlocalizedName(BeCom.MODID + ".signal_analyzer");
        setTextureName(BeCom.MODID + ":signal_analyzer");
        setMaxStackSize(1);
    }

    public static boolean is(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemSignalAnalyzer;
    }
}
