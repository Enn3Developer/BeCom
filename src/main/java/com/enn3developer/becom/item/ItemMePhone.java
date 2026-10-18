package com.enn3developer.becom.item;

import java.util.Collections;
import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.logic.phone.PhoneKind;

import appeng.api.config.AccessRestriction;
import appeng.api.config.PowerUnits;
import appeng.api.features.INetworkEncodable;
import appeng.api.implementations.items.IAEItemPowerStorage;

/**
 * The ME Phone: late EV, linked to an ME network at a Security Terminal like a wireless terminal,
 * with service only inside that network's wireless range, at Max. It runs on AE power like a wireless terminal and
 * charges in an ME charger; its battery and drain are the Mobile Phone's, in AE.
 */
public class ItemMePhone extends ItemCarriedPhone implements INetworkEncodable, IAEItemPowerStorage {

    private static final String KEY = "becomMeKey";
    private static final String NETWORK_NAME = "becomMeName";
    private static final String POWER = "becomAePower";

    public ItemMePhone() {
        super("me_phone");
    }

    @Override
    public PhoneKind kind() {
        return PhoneKind.ME_PHONE;
    }

    // region Charge, as EU worth

    @Override
    public double charge(ItemStack stack) {
        return PowerUnits.AE.convertTo(PowerUnits.EU, getAECurrentPower(stack));
    }

    @Override
    public double maxCharge(ItemStack stack) {
        return BeComConfig.get()
            .phoneBattery();
    }

    @Override
    public double drain(ItemStack stack, double eu) {
        double ae = PowerUnits.EU.convertTo(PowerUnits.AE, eu);
        return PowerUnits.AE.convertTo(PowerUnits.EU, extractAEPower(stack, ae));
    }

    // endregion

    // region AE power

    @Override
    public double injectAEPower(ItemStack stack, double amount) {
        // One a newer BeCom saved keeps its charge as it was: a charger keeps the power it would have lost.
        if (isTooNew(stack)) return amount;
        double stored = getAECurrentPower(stack);
        double taken = Math.min(amount, getAEMaxPower(stack) - stored);
        setPower(stack, stored + taken);
        return amount - taken;
    }

    @Override
    public double extractAEPower(ItemStack stack, double amount) {
        // Nor does it give any away, which it couldn't take off its charge.
        if (isTooNew(stack)) return 0;
        double stored = getAECurrentPower(stack);
        double given = Math.min(amount, stored);
        setPower(stack, stored - given);
        return given;
    }

    @Override
    public double getAEMaxPower(ItemStack stack) {
        return PowerUnits.EU.convertTo(PowerUnits.AE, maxCharge(stack));
    }

    @Override
    public double getAECurrentPower(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? 0 : tag.getDouble(POWER);
    }

    @Override
    public AccessRestriction getPowerFlow(ItemStack stack) {
        return AccessRestriction.WRITE;
    }

    private static void setPower(ItemStack stack, double power) {
        if (isTooNew(stack)) return;
        tag(stack).setDouble(POWER, power);
    }

    // endregion

    // region Linking

    /** The ME network it's linked to, as AE2's Security Terminal wrote it: a serial, or empty. */
    @Override
    public String getEncryptionKey(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? "" : tag.getString(KEY);
    }

    @Override
    public void setEncryptionKey(ItemStack stack, String key, String name) {
        if (isTooNew(stack)) return;
        tag(stack).setString(KEY, key == null ? "" : key);
        tag(stack).setString(NETWORK_NAME, name == null ? "" : name);
    }

    /** The name of the ME network it's linked to, as its Security Terminal gave it; empty for none. */
    public String networkName(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? "" : tag.getString(NETWORK_NAME);
    }

    // endregion

    @Override
    protected String batteryLine(ItemStack stack) {
        return Tips.battery(getAECurrentPower(stack), getAEMaxPower(stack));
    }

    @Override
    protected boolean moreInformation(ItemStack stack, List<String> lines) {
        if (!getEncryptionKey(stack).isEmpty()) {
            lines.add("\u00a7a" + StatCollector.translateToLocal("becom.tip.me_linked"));
            return false;
        }
        lines.add("\u00a76" + StatCollector.translateToLocal("becom.tip.me_unlinked"));
        Collections.addAll(lines, Tips.wrap(Tips.ITEM, Tips.howTo("becom.tip.me_link")));
        return true;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(this));
        ItemStack full = new ItemStack(this);
        injectAEPower(full, getAEMaxPower(full));
        list.add(full);
    }
}
