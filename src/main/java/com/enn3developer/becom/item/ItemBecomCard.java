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

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The BeCom Card (joining a computer to BeCom): an OpenComputers card that gives its computer a number and the
 * {@code becom} component. The radio card has signal like a phone where the computer stands; the wired card joins
 * the line whose Phone Cable touches the computer, at the cable's rate.
 */
public class ItemBecomCard extends Item {

    public static final int RADIO = 0;
    public static final int WIRED = 1;
    private static final String[] NAMES = { "becom_card_radio", "becom_card_wired" };

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemBecomCard() {
        setUnlocalizedName(BeCom.MODID + ".becom_card");
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    /** The name of the card with this damage: becom_card_radio or becom_card_wired. */
    public static String nameOf(int damage) {
        return NAMES[damage == WIRED ? WIRED : RADIO];
    }

    public static boolean is(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemBecomCard;
    }

    public static boolean wired(ItemStack stack) {
        return is(stack) && stack.getItemDamage() == WIRED;
    }

    public static ItemStack stack(int kind, int count) {
        return new ItemStack(BeComItems.becomCard, count, kind);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "item." + BeCom.MODID + "." + NAMES[wired(stack) ? WIRED : RADIO];
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(StatCollector.translateToLocal("becom.card.info"));
        lines.add(StatCollector.translateToLocal(wired(stack) ? "becom.card.wired" : "becom.card.radio"));
        if (stack.hasTagCompound() && stack.getTagCompound()
            .hasKey("oc:data")) {
            int number = stack.getTagCompound()
                .getCompoundTag("oc:data")
                .getInteger(com.enn3developer.becom.apps.BecomCard.NUMBER);
            if (number > 0) lines.add(StatCollector.translateToLocalFormatted("becom.mobile.number", number));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item, 1, RADIO));
        list.add(new ItemStack(item, 1, WIRED));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        icons = new IIcon[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) icons[i] = register.registerIcon(BeCom.MODID + ":" + NAMES[i]);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        return icons[damage == WIRED ? WIRED : RADIO];
    }
}
