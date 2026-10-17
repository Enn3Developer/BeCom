package com.enn3developer.becom.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.logic.net.CableTier;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Switching Racks: LV, MV and HV, one per cable tier by damage. An Exchange switches as many calls at
 * once as the racks in its input buses add up to. They do nothing in the hand.
 */
public class ItemSwitchingRack extends Item {

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemSwitchingRack() {
        setUnlocalizedName(BeCom.MODID + ".switching_rack");
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    /** The tier of a stack of racks, or null when it isn't one. */
    public static CableTier tierOf(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemSwitchingRack)) return null;
        return CableTier.byOrdinal(stack.getItemDamage());
    }

    public static ItemStack stack(CableTier tier, int count) {
        return new ItemStack(BeComItems.switchingRack, count, tier.ordinal());
    }

    private static String suffix(CableTier tier) {
        return tier.voltage()
            .toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        CableTier tier = tierOf(stack);
        return "item." + BeCom.MODID + ".switching_rack_" + suffix(tier == null ? CableTier.PHONE : tier);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        CableTier tier = tierOf(stack);
        if (tier == null) return;
        lines.add(
            StatCollector.translateToLocalFormatted(
                "becom.switching_rack.info",
                BeComConfig.get()
                    .rackCalls(tier)));
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (CableTier tier : CableTier.values()) list.add(new ItemStack(item, 1, tier.ordinal()));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        icons = new IIcon[CableTier.values().length];
        for (CableTier tier : CableTier.values()) {
            icons[tier.ordinal()] = register.registerIcon(BeCom.MODID + ":switching_rack_" + suffix(tier));
        }
        itemIcon = icons[0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        CableTier tier = CableTier.byOrdinal(damage);
        return icons[tier == null ? 0 : tier.ordinal()];
    }
}
