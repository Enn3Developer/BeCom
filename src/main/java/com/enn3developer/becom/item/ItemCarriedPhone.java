package com.enn3developer.becom.item;

import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
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
import com.enn3developer.becom.data.BeComNbt;
import com.enn3developer.becom.gui.PhoneScreen;
import com.enn3developer.becom.logic.phone.BatteryUse;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.PhoneHandle;
import com.enn3developer.becom.server.PhoneScreenActions;

import baubles.api.BaubleType;
import baubles.api.expanded.BaubleExpandedSlots;
import baubles.api.expanded.IBaubleExpanded;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.modularui2.GTGuiThemes;

/**
 * A phone players carry (DESIGN.md 8, 8.2): the Mobile Phone and the ME Phone. It works from the inventory or a bauble
 * slot (D14) while switched on and charged, and drains its charge on standby and more in a call (D36). Its number,
 * given on first use, and whether it's on live in the stack; its owner and settings in the world's phone registry.
 */
public abstract class ItemCarriedPhone extends Item implements IBaubleExpanded, IGuiHolder<PlayerInventoryGuiData> {

    private static final String NUMBER = "becomNumber";
    private static final String ON = "becomOn";
    private static final String USE_DAY = "becomUseDay";
    private static final String USE = "becomUse";
    private static final String SAVER = "becomSaver";
    private static final String WARN = "becomWarn";
    public static final int FORMAT = 1;

    protected ItemCarriedPhone(String name) {
        setUnlocalizedName(BeCom.MODID + "." + name);
        setTextureName(BeCom.MODID + ":" + name);
        setMaxStackSize(1);
        setNoRepair();
    }

    /** Which kind of phone it is in the registry. */
    public abstract PhoneKind kind();

    /** What its battery holds now, in EU or what its own energy is worth in EU. */
    public abstract double charge(ItemStack stack);

    public abstract double maxCharge(ItemStack stack);

    /** Takes up to {@code eu} worth from its battery, and returns what it took. */
    public abstract double drain(ItemStack stack, double eu);

    // region State

    public static boolean is(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemCarriedPhone;
    }

    /** Its number, or -1 before its first use. */
    public static int number(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null || !tag.hasKey(NUMBER) ? -1 : tag.getInteger(NUMBER);
    }

    public static void setNumber(ItemStack stack, int number) {
        if (isTooNew(stack)) return;
        tag(stack).setInteger(NUMBER, number);
    }

    /** Switched on; a new phone is, and a flat battery switches it off (DESIGN.md 11.3). */
    public static boolean on(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null || !tag.hasKey(ON) || tag.getBoolean(ON);
    }

    public static void setOn(ItemStack stack, boolean on) {
        if (isTooNew(stack)) return;
        tag(stack).setBoolean(ON, on);
    }

    /** Where its charge went today (the Battery settings page). */
    public static BatteryUse use(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(USE)) return BatteryUse.empty(tag == null ? 0 : tag.getLong(USE_DAY));
        int[] saved = tag.getIntArray(USE);
        long[] eu = new long[saved.length];
        for (int i = 0; i < saved.length; i++) eu[i] = saved[i] & 0xFFFFFFFFL;
        return new BatteryUse(tag.getLong(USE_DAY), eu);
    }

    public static void setUse(ItemStack stack, BatteryUse use) {
        if (isTooNew(stack)) return;
        long[] eu = use.eu();
        int[] saved = new int[eu.length];
        // A day's drain stays far below 2^32 EU; past it, it stops counting rather than wrap.
        for (int i = 0; i < eu.length; i++) saved[i] = (int) Math.min(eu[i], 0xFFFFFFFFL);
        NBTTagCompound tag = tag(stack);
        tag.setLong(USE_DAY, use.day());
        tag.setIntArray(USE, saved);
    }

    /** The battery saver switched on by hand; it also comes on by itself when the charge runs low. */
    public static boolean saver(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.getBoolean(SAVER);
    }

    public static void setSaver(ItemStack stack, boolean on) {
        if (isTooNew(stack)) return;
        tag(stack).setBoolean(SAVER, on);
    }

    /** Warns its holder as the charge drops past 20% and 5%; a new phone does. */
    public static boolean warns(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null || !tag.hasKey(WARN) || tag.getBoolean(WARN);
    }

    public static void setWarns(ItemStack stack, boolean on) {
        if (isTooNew(stack)) return;
        tag(stack).setBoolean(WARN, on);
    }

    /** Its state was written by a newer BeCom: this one leaves it alone (X16). */
    public static boolean isTooNew(ItemStack stack) {
        return BeComNbt.tooNew(stack.getTagCompound(), FORMAT);
    }

    /** Switched on and charged: it has service where its holder is, can be called, and drains its charge. */
    public static boolean working(ItemStack stack) {
        return is(stack) && !isTooNew(stack) && on(stack) && ((ItemCarriedPhone) stack.getItem()).charge(stack) > 0;
    }

    /** Its tag, marked with BeCom's format as BeCom writes into it. */
    protected static NBTTagCompound tag(ItemStack stack) {
        if (stack.getTagCompound() == null) stack.setTagCompound(new NBTTagCompound());
        BeComNbt.write(stack.getTagCompound(), FORMAT);
        return stack.getTagCompound();
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

    // region Screen

    /** Right-click opens its phone screen (DESIGN.md 11.2); so does the Phone key, for the first phone carried. */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && PhoneScreenActions.mayOpenScreen(player)) GuiFactories.playerInventory()
            .openFromMainHand(player);
        return stack;
    }

    @Override
    public ModularPanel buildUI(PlayerInventoryGuiData data, PanelSyncManager sync, UISettings settings) {
        EntityPlayer player = data.getPlayer();
        // A phone used for the first time gets its number now, and a copy its own, so the screen shows its phone.
        BeComServer becom = player.worldObj.isRemote ? null : BeComServer.get();
        ItemStack stack = data.getUsedItemStack();
        int number;
        if (becom != null && player instanceof EntityPlayerMP mp) {
            number = becom.carried.numberFor(mp, stack);
            // An open screen costs charge.
            sync.addOpenListener(p -> becom.carried.screenOpen(number, true));
            sync.addCloseListener(p -> becom.carried.screenOpen(number, false));
        } else number = stack == null || !is(stack) ? -1 : number(stack);
        return PhoneScreen.build(new PhoneHandle.CarriedPhone(number), player, sync);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(PlayerInventoryGuiData data, ModularPanel mainPanel) {
        return new ModularScreen(BeCom.MODID, mainPanel).useTheme(GTGuiThemes.STANDARD.getId());
    }

    // endregion

    // region Looks

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        if (isTooNew(stack)) lines.add(StatCollector.translateToLocal("becom.info.too_new"));
        int number = number(stack);
        if (number >= 0) lines.add(StatCollector.translateToLocalFormatted("becom.mobile.number", number));
        lines.add(StatCollector.translateToLocal(on(stack) ? "becom.mobile.on" : "becom.mobile.off"));
        lines.add(chargeLine(stack));
        moreInformation(stack, lines);
        lines.add(StatCollector.translateToLocal("becom.mobile.help"));
    }

    /** Its charge, for the tooltip, in the units its battery uses. */
    protected String chargeLine(ItemStack stack) {
        return StatCollector
            .translateToLocalFormatted("becom.mobile.charge", (long) charge(stack), (long) maxCharge(stack));
    }

    /** Lines a kind of phone adds to the tooltip. */
    protected void moreInformation(ItemStack stack, List<String> lines) {}

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return true;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1 - charge(stack) / Math.max(1, maxCharge(stack));
    }

    // endregion
}
