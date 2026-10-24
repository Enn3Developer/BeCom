package com.enn3developer.becom.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import com.enn3developer.becom.BeCom;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * BeCom's crafting parts, one item by damage: the audio parts, the RF Modules (a tier's emitter and sensor on its
 * board, the core of everything that transmits) and the switching parts. They do nothing on their own.
 */
public class ItemPart extends Item {

    public static final int LOUDSPEAKER = 0;
    public static final int MICROPHONE = 1;
    public static final int HANDSET = 2;
    public static final int RF_MODULE_LV = 3;
    public static final int RF_MODULE_MV = 4;
    public static final int RF_MODULE_HV = 5;
    public static final int RF_MODULE_EV = 6;
    public static final int CROSSBAR_SWITCH = 7;
    public static final int SWITCHING_BOARD = 8;
    public static final int PACKET_SWITCH = 9;
    private static final String[] NAMES = { "loudspeaker", "microphone", "handset", "rf_module_lv", "rf_module_mv",
        "rf_module_hv", "rf_module_ev", "crossbar_switch", "switching_board", "packet_switch" };

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemPart() {
        setUnlocalizedName(BeCom.MODID + ".part");
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    private static int kindOf(int damage) {
        return damage >= 0 && damage < NAMES.length ? damage : LOUDSPEAKER;
    }

    public static ItemStack stack(int kind, int count) {
        return new ItemStack(BeComItems.part, count, kind);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "item." + BeCom.MODID + "." + NAMES[kindOf(stack.getItemDamage())];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < NAMES.length; i++) list.add(new ItemStack(item, 1, i));
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
