package com.enn3developer.becom.server;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.enn3developer.becom.block.TileTelephone;
import com.enn3developer.becom.item.Carried;
import com.enn3developer.becom.item.ItemCarriedPhone;

/**
 * The phone a phone screen works (DESIGN.md 11.2): a Telephone where it stands, or a phone its player carries. Every
 * action checks again that the player can still use it (DESIGN.md 14: client input).
 */
public sealed interface PhoneHandle {

    /** Its number, or -1 when it has none. */
    int number();

    /** Whether {@code player} can use it right now. */
    boolean usableBy(EntityPlayer player);

    /** A Telephone, used from within the reach ModularUI keeps a block screen open at. */
    record Telephone(TileTelephone tile) implements PhoneHandle {

        // The same 8 blocks ModularUI allows a block screen to stay open from.
        private static final double REACH_SQ = 64;

        @Override
        public int number() {
            return tile.number();
        }

        @Override
        public boolean usableBy(EntityPlayer player) {
            return !tile.isInvalid() && player.worldObj == tile.getWorldObj()
                && player.getDistanceSq(tile.xCoord + 0.5, tile.yCoord + 0.5, tile.zCoord + 0.5) <= REACH_SQ;
        }
    }

    /** A phone with this number, used by whoever carries it. */
    record CarriedPhone(int number) implements PhoneHandle {

        @Override
        public boolean usableBy(EntityPlayer player) {
            return stack(player) != null;
        }

        /** The stack of this phone among what {@code player} carries, or null. */
        public ItemStack stack(EntityPlayer player) {
            if (number < 0) return null;
            Carried.Slot slot = Carried.first(
                player,
                stack -> ItemCarriedPhone.is(stack) && !ItemCarriedPhone.isTooNew(stack)
                    && ItemCarriedPhone.number(stack) == number);
            return slot == null ? null : slot.stack();
        }
    }
}
