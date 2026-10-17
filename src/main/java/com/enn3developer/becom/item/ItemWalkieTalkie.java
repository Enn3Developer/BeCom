package com.enn3developer.becom.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
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
import com.enn3developer.becom.data.BeComNbt;
import com.enn3developer.becom.gui.WalkieScreen;
import com.enn3developer.becom.voice.RadioGroup;

import baubles.api.BaubleType;
import baubles.api.expanded.BaubleExpandedSlots;
import baubles.api.expanded.IBaubleExpanded;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.modularui2.GTGuiThemes;
import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItem;

/**
 * The Walkie-Talkie: an MV radio on its own battery. It works from the inventory while switched on
 * and charged; right-click opens its screen, sneak + scroll changes the channel. GT's chargers and battery buffers
 * charge it through IC2's electric item interface, like GT's own tools.
 * <p>
 * A player transmits and receives on one walkie, the first working one: the one in hand, then the rest of the
 * inventory, then the bauble slots. Its state lives in the stack: channel, power, and IC2's charge.
 */
public class ItemWalkieTalkie extends Item
    implements IElectricItem, IGuiHolder<PlayerInventoryGuiData>, IBaubleExpanded {

    private static final String CHANNEL = "becomChannel";
    private static final String ON = "becomOn";
    private static final int TIER = 2;
    public static final int FORMAT = 1;
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

    /** Its state was written by a newer BeCom: this one leaves it alone. */
    public static boolean isTooNew(ItemStack stack) {
        return BeComNbt.tooNew(stack.getTagCompound(), FORMAT);
    }

    public static void setChannel(ItemStack stack, int channel) {
        if (isTooNew(stack)) return;
        tag(stack).setInteger(CHANNEL, Math.floorMod(channel - 1, RadioGroup.CHANNELS) + 1);
    }

    /** Switched on; a new walkie is, and a flat battery switches it off. */
    public static boolean on(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null || !tag.hasKey(ON) || tag.getBoolean(ON);
    }

    public static void setOn(ItemStack stack, boolean on) {
        if (isTooNew(stack)) return;
        tag(stack).setBoolean(ON, on);
    }

    public static double charge(ItemStack stack) {
        return ElectricItem.manager.getCharge(stack);
    }

    /** Switched on and charged: it receives, transmits and drains its battery. */
    public static boolean working(ItemStack stack) {
        return is(stack) && !isTooNew(stack) && on(stack) && charge(stack) > 0;
    }

    /** Takes up to {@code eu} from its battery, and returns what it took. */
    public static double drain(ItemStack stack, double eu) {
        return ElectricItem.manager.discharge(stack, eu, Integer.MAX_VALUE, true, false, false);
    }

    /** Where the walkie a player talks and listens on is, or null: the first working one, held first. */
    public static Carried.Slot active(EntityPlayer player) {
        return Carried.first(player, ItemWalkieTalkie::working);
    }

    /** Its tag, marked with BeCom's format as BeCom writes into it; IC2 keeps its charge there too. */
    private static NBTTagCompound tag(ItemStack stack) {
        if (stack.getTagCompound() == null) stack.setTagCompound(new NBTTagCompound());
        BeComNbt.write(stack.getTagCompound(), FORMAT);
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

    // region Baubles

    @Override
    public String[] getBaubleTypes(ItemStack stack) {
        return new String[] { BaubleExpandedSlots.universalType };
    }

    @Override
    public BaubleType getBaubleType(ItemStack stack) {
        return BaubleType.UNIVERSAL;
    }

    @Override
    public void onWornTick(ItemStack stack, EntityLivingBase player) {}

    @Override
    public void onEquipped(ItemStack stack, EntityLivingBase player) {}

    @Override
    public void onUnequipped(ItemStack stack, EntityLivingBase player) {}

    @Override
    public boolean canEquip(ItemStack stack, EntityLivingBase player) {
        return true;
    }

    @Override
    public boolean canUnequip(ItemStack stack, EntityLivingBase player) {
        return true;
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
        if (isTooNew(stack)) lines.add(StatCollector.translateToLocal("becom.info.too_new"));
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

    /** Its TX light shows while you transmit on it. */
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
