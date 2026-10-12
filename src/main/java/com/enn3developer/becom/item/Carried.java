package com.enn3developer.becom.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import baubles.api.BaublesApi;

/**
 * Where a player carries a walkie-talkie or a phone (D14, DESIGN.md 8): anywhere in their inventory or in a bauble
 * slot. Devices are chosen in one order everywhere: the one in hand, then the rest of the inventory, then the bauble
 * slots.
 */
public final class Carried {

    private Carried() {}

    /** One slot a device can be carried in. */
    public record Slot(IInventory inventory, int index) {

        public ItemStack stack() {
            return inventory.getStackInSlot(index);
        }
    }

    /** Every slot, in the order a device is chosen. */
    public static List<Slot> slots(EntityPlayer player) {
        List<Slot> slots = new ArrayList<>();
        IInventory main = player.inventory;
        int held = player.inventory.currentItem;
        slots.add(new Slot(main, held));
        for (int i = 0; i < player.inventory.mainInventory.length; i++) if (i != held) slots.add(new Slot(main, i));
        IInventory baubles = baubles(player);
        if (baubles != null) for (int i = 0; i < baubles.getSizeInventory(); i++) slots.add(new Slot(baubles, i));
        return slots;
    }

    /**
     * Every stack the player carries, in the order a device is chosen, then the one they're moving with the mouse in an
     * open inventory: it's in no slot for that moment, but it hasn't left them.
     */
    public static List<ItemStack> stacks(EntityPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Slot slot : slots(player)) {
            ItemStack stack = slot.stack();
            if (stack != null) stacks.add(stack);
        }
        ItemStack moving = player.inventory.getItemStack();
        if (moving != null) stacks.add(moving);
        return stacks;
    }

    /** The first slot whose stack {@code wanted} accepts, or null. */
    public static Slot first(EntityPlayer player, Predicate<ItemStack> wanted) {
        for (Slot slot : slots(player)) {
            ItemStack stack = slot.stack();
            if (stack != null && wanted.test(stack)) return slot;
        }
        return null;
    }

    private static IInventory baubles(EntityPlayer player) {
        try {
            return BaublesApi.getBaubles(player);
        } catch (RuntimeException e) {
            // A player Baubles can't keep slots for, such as a fake one: they carry nothing there.
            return null;
        }
    }
}
