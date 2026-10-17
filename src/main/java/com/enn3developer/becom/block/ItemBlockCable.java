package com.enn3developer.becom.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlockWithMetadata;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.CableTier;

/** The cables' item: one per tier, named and described by it. */
public class ItemBlockCable extends ItemBlockWithMetadata {

    public ItemBlockCable(Block block) {
        super(block, block);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "tile." + BeCom.MODID + "." + BlockPhoneCable.nameOf(BlockPhoneCable.tierOf(stack.getItemDamage()));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        CableTier tier = BlockPhoneCable.tierOf(stack.getItemDamage());
        Tunables t = BeComConfig.get();
        lines.add(
            StatCollector.translateToLocalFormatted(
                "becom.cable.tooltip",
                tier.voltage(),
                t.cableRate(tier),
                t.cableReach(tier)));
        lines.add(StatCollector.translateToLocal("becom.cable.tooltip_span"));
    }
}
