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
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.PhoneHandle;

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
        if (!world.isRemote) GuiFactories.playerInventory()
            .openFromMainHand(player);
        return stack;
    }

    @Override
    public ModularPanel buildUI(PlayerInventoryGuiData data, PanelSyncManager sync, UISettings settings) {
        EntityPlayer player = data.getPlayer();
        // A phone used for the first time gets its number now, so the screen has one to show.
        BeComServer becom = player.worldObj.isRemote ? null : BeComServer.get();
        if (becom != null && player instanceof EntityPlayerMP mp) becom.carried.refresh(mp);
        ItemStack stack = data.getUsedItemStack();
        int number = stack == null || !is(stack) ? -1 : number(stack);
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
