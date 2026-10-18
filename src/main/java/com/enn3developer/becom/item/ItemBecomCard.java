package com.enn3developer.becom.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import com.enn3developer.becom.BeCom;

import appeng.api.features.INetworkEncodable;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The BeCom Card (joining a computer to BeCom): an OpenComputers card that gives its computer a number and the
 * {@code becom} component. The radio card has signal like a phone where the computer stands; the wired card joins
 * the line whose Phone Cable touches the computer, at the cable's rate; the ME card, linked to an ME network at a
 * Security Station like an ME Phone, joins that network at Max while the computer is in its wireless range.
 */
public class ItemBecomCard extends Item implements INetworkEncodable {

    public static final int RADIO = 0;
    public static final int WIRED = 1;
    public static final int ME = 2;
    private static final String[] NAMES = { "becom_card_radio", "becom_card_wired", "becom_card_me" };
    private static final String KEY = "becomMeKey";
    private static final String NETWORK_NAME = "becomMeName";

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemBecomCard() {
        setUnlocalizedName(BeCom.MODID + ".becom_card");
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    /** Which card a damage is: RADIO, WIRED or ME, and RADIO for any other. */
    public static int kindOf(int damage) {
        return damage == WIRED || damage == ME ? damage : RADIO;
    }

    /** The name of the card with this damage: becom_card_radio, becom_card_wired or becom_card_me. */
    public static String nameOf(int damage) {
        return NAMES[kindOf(damage)];
    }

    /** Which card a stack is: RADIO, WIRED or ME. */
    public static int kind(ItemStack stack) {
        return is(stack) ? kindOf(stack.getItemDamage()) : RADIO;
    }

    /** The ME card's link to its ME network; empty for none, or for another card. */
    public static String meKeyOf(ItemStack stack) {
        return kind(stack) == ME && stack.hasTagCompound() ? stack.getTagCompound()
            .getString(KEY) : "";
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
        return "item." + BeCom.MODID + "." + NAMES[kind(stack)];
    }

    // region Linking, at a Security Station: only the ME card takes a link

    @Override
    public String getEncryptionKey(ItemStack stack) {
        return meKeyOf(stack);
    }

    @Override
    public void setEncryptionKey(ItemStack stack, String key, String name) {
        if (kind(stack) != ME) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString(KEY, key == null ? "" : key);
        stack.getTagCompound()
            .setString(NETWORK_NAME, name == null ? "" : name);
    }

    // endregion

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        int kind = kind(stack);
        lines.add(
            StatCollector.translateToLocal(
                kind == WIRED ? "becom.card.wired" : kind == ME ? "becom.card.me" : "becom.card.radio"));
        if (kind == ME) {
            if (!meKeyOf(stack).isEmpty()) lines.add("\u00a7a" + StatCollector.translateToLocal("becom.tip.me_linked"));
            else lines.add("\u00a76" + StatCollector.translateToLocal("becom.tip.me_unlinked"));
        }
        if (stack.hasTagCompound() && stack.getTagCompound()
            .hasKey("oc:data")) {
            int number = stack.getTagCompound()
                .getCompoundTag("oc:data")
                .getInteger(com.enn3developer.becom.apps.BecomCard.NUMBER);
            if (number > 0) lines.add(Tips.row("becom.tip.number", number));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item, 1, RADIO));
        list.add(new ItemStack(item, 1, WIRED));
        list.add(new ItemStack(item, 1, ME));
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
        return icons[kindOf(damage)];
    }
}
