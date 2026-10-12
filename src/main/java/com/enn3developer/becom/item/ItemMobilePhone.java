package com.enn3developer.becom.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.logic.phone.PhoneKind;

import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItem;

/**
 * The Mobile Phone (DESIGN.md 8): early HV, with service through antennas on UHF and a cap of High. Its HV battery
 * charges in GT's chargers and battery buffers through IC2's electric item interface, like GT's own tools.
 */
public class ItemMobilePhone extends ItemCarriedPhone implements IElectricItem {

    private static final int TIER = 3;
    private static final double TRANSFER_LIMIT = 512;

    public ItemMobilePhone() {
        super("mobile_phone");
    }

    @Override
    public PhoneKind kind() {
        return PhoneKind.MOBILE;
    }

    @Override
    public double charge(ItemStack stack) {
        return ElectricItem.manager.getCharge(stack);
    }

    @Override
    public double maxCharge(ItemStack stack) {
        return getMaxCharge(stack);
    }

    @Override
    public double drain(ItemStack stack, double eu) {
        return ElectricItem.manager.discharge(stack, eu, Integer.MAX_VALUE, true, false, false);
    }

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
            .phoneBattery();
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

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(this));
        ItemStack full = new ItemStack(this);
        ElectricItem.manager.charge(full, getMaxCharge(full), Integer.MAX_VALUE, true, false);
        list.add(full);
    }
}
