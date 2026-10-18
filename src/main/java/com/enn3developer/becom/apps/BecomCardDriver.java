package com.enn3developer.becom.apps;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.enn3developer.becom.item.ItemBecomCard;

import li.cil.oc.api.driver.Item;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;

/** OpenComputers' driver for the BeCom Card: a tier 2 card, in a computer of any kind. */
final class BecomCardDriver implements Item {

    @Override
    public boolean worksWith(ItemStack stack) {
        return ItemBecomCard.is(stack);
    }

    @Override
    public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) {
        if (host == null || host.world() == null || host.world().isRemote) return null;
        return new BecomCard(host, ItemBecomCard.kind(stack), ItemBecomCard.meKeyOf(stack));
    }

    @Override
    public String slot(ItemStack stack) {
        return "card";
    }

    @Override
    public int tier(ItemStack stack) {
        return 1;
    }

    @Override
    public NBTTagCompound dataTag(ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey("oc:data")) tag.setTag("oc:data", new NBTTagCompound());
        return tag.getCompoundTag("oc:data");
    }
}
