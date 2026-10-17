package com.enn3developer.becom.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.data.BeComNbt;

/** A walkie's channel and power live in its stack, versioned (14.2). */
class ItemWalkieTalkieTest {

    private static final ItemWalkieTalkie WALKIE = new ItemWalkieTalkie();

    @Test
    void channelsWrapRoundOneToSixteen() {
        ItemStack stack = new ItemStack(WALKIE);
        assertEquals(1, ItemWalkieTalkie.channel(stack));
        ItemWalkieTalkie.setChannel(stack, 0);
        assertEquals(16, ItemWalkieTalkie.channel(stack));
        ItemWalkieTalkie.setChannel(stack, 17);
        assertEquals(1, ItemWalkieTalkie.channel(stack));
        assertEquals(
            ItemWalkieTalkie.FORMAT,
            stack.getTagCompound()
                .getInteger(BeComNbt.KEY));
    }

    @Test
    void aNewWalkieIsSwitchedOn() {
        ItemStack stack = new ItemStack(WALKIE);
        assertTrue(ItemWalkieTalkie.on(stack));
        ItemWalkieTalkie.setOn(stack, false);
        assertFalse(ItemWalkieTalkie.on(stack));
    }

    @Test
    void aWalkieFromANewerBeComIsLeftAlone() {
        ItemStack stack = new ItemStack(WALKIE);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(BeComNbt.KEY, ItemWalkieTalkie.FORMAT + 1);
        tag.setInteger("becomChannel", 9);
        stack.setTagCompound(tag);
        assertTrue(ItemWalkieTalkie.isTooNew(stack));
        ItemWalkieTalkie.setChannel(stack, 3);
        ItemWalkieTalkie.setOn(stack, false);
        assertEquals(9, tag.getInteger("becomChannel"));
        assertFalse(tag.hasKey("becomOn"));
        assertEquals(ItemWalkieTalkie.FORMAT + 1, tag.getInteger(BeComNbt.KEY));
    }
}
