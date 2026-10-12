package com.enn3developer.becom.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.GuiFactories;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.gui.WalkieScreen;
import com.enn3developer.becom.voice.RadioGroup;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.modularui2.GTGuiThemes;
import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItem;

/**
 * The Walkie-Talkie (DESIGN.md 8, 11.3): an MV radio on its own battery. It works from the inventory while switched on
 * and charged; right-click opens its screen, sneak + scroll changes the channel. GT's chargers and battery buffers
 * charge it through IC2's electric item interface, like GT's own tools.
 * <p>
 * A player transmits and receives on one walkie, the first working one: the one in hand, then the hotbar, then the
 * rest of the inventory (X21). Its state lives in the stack: channel, power, and IC2's charge.
 */
public class ItemWalkieTalkie extends Item implements IElectricItem, IGuiHolder<PlayerInventoryGuiData> {

    private static final String CHANNEL = "becomChannel";
    private static final String ON = "becomOn";
    private static final int TIER = 2;
    private static final double TRANSFER_LIMIT = 128;

    @SideOnly(Side.CLIENT)
    private IIcon transmitting;

    public ItemWalkieTalkie() {
        setUnlocalizedName(BeCom.MODID + ".walkie_talkie");
        setTextureName(BeCom.MODID + ":walkie_talkie");
        setMaxStackSize(1);
        setNoRepair();
    }

    // region State

    public static boolean is(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemWalkieTalkie;
    }

    public static int channel(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        int channel = tag == null ? 1 : tag.getInteger(CHANNEL);
        return channel < 1 || channel > RadioGroup.CHANNELS ? 1 : channel;
    }

    public static void setChannel(ItemStack stack, int channel) {
        tag(stack).setInteger(CHANNEL, Math.floorMod(channel - 1, RadioGroup.CHANNELS) + 1);
    }

    /** Switched on; a new walkie is, and a flat battery switches it off (DESIGN.md 11.3). */
    public static boolean on(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null || !tag.hasKey(ON) || tag.getBoolean(ON);
    }

    public static void setOn(ItemStack stack, boolean on) {
        tag(stack).setBoolean(ON, on);
    }

    public static double charge(ItemStack stack) {
        return ElectricItem.manager.getCharge(stack);
    }

    /** Switched on and charged: it receives, transmits and drains its battery. */
    public static boolean working(ItemStack stack) {
        return is(stack) && on(stack) && charge(stack) > 0;
    }

    /** Takes up to {@code eu} from its battery, and returns what it took. */
    public static double drain(ItemStack stack, double eu) {
        return ElectricItem.manager.discharge(stack, eu, Integer.MAX_VALUE, true, false, false);
    }

    /** The slot of the walkie a player talks and listens on, or -1: the first working one, held first (X21). */
    public static int activeSlot(InventoryPlayer inventory) {
        if (working(inventory.getCurrentItem())) return inventory.currentItem;
        for (int i = 0; i < inventory.mainInventory.length; i++) {
            if (working(inventory.mainInventory[i])) return i;
        }
        return -1;
    }

    private static NBTTagCompound tag(ItemStack stack) {
        if (stack.getTagCompound() == null) stack.setTagCompound(new NBTTagCompound());
        return stack.getTagCompound();
    }

    // endregion

    // region IElectricItem

    @Override
    public boolean canProvideEnergy(ItemStack stack) {
        return false;
    }

    @Override
    public Item getChargedItem(ItemStack stack) {
        return this;
    }

    @Override
    public Item getEmptyItem(ItemStack stack) {
        return this;
    }

    @Override
    public double getMaxCharge(ItemStack stack) {
        return BeComConfig.get()
            .walkieBattery();
    }

    @Override
    public int getTier(ItemStack stack) {
        return TIER;
    }

    @Override
    public double getTransferLimit(ItemStack stack) {
        return TRANSFER_LIMIT;
    }

    // endregion

    // region Use and looks

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) GuiFactories.playerInventory()
            .openFromMainHand(player);
        return stack;
    }

    @Override
    public ModularPanel buildUI(PlayerInventoryGuiData data, PanelSyncManager sync, UISettings settings) {
        return WalkieScreen.build(data, sync);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(PlayerInventoryGuiData data, ModularPanel mainPanel) {
        return new ModularScreen(BeCom.MODID, mainPanel).useTheme(GTGuiThemes.STANDARD.getId());
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        ItemStack empty = new ItemStack(this);
        list.add(empty);
        ItemStack full = new ItemStack(this);
        ElectricItem.manager.charge(full, getMaxCharge(full), Integer.MAX_VALUE, true, false);
        list.add(full);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(StatCollector.translateToLocalFormatted("becom.walkie.channel", channel(stack)));
        lines.add(StatCollector.translateToLocal(on(stack) ? "becom.walkie.on" : "becom.walkie.off"));
        lines.add(
            StatCollector
                .translateToLocalFormatted("becom.walkie.charge", (long) charge(stack), (long) getMaxCharge(stack)));
        lines.add(StatCollector.translateToLocal("becom.walkie.help"));
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return true;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1 - charge(stack) / Math.max(1, getMaxCharge(stack));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        super.registerIcons(register);
        transmitting = register.registerIcon(BeCom.MODID + ":walkie_talkie_tx");
    }

    /** Its TX light shows while you transmit on it (DESIGN.md 11.3, 13.4). */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconIndex(ItemStack stack) {
        return BeCom.proxy.isTransmittingOn(stack) ? transmitting : itemIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack stack, int pass) {
        return getIconIndex(stack);
    }

    // endregion
}
