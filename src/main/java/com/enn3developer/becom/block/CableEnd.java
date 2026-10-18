package com.enn3developer.becom.block;

import net.minecraftforge.common.util.ForgeDirection;

import com.enn3developer.becom.logic.net.CableTier;

/** A machine a cable can end at: the best cable it takes, on which faces, and whether only one at a time. */
public interface CableEnd {

    /** The best cable this machine takes; any tier unless it says otherwise. */
    default CableTier bestCable() {
        return CableTier.FIBRE;
    }

    /** Whether cable may end at {@code face} of this machine; any face unless it says otherwise. */
    default boolean takesCableOn(ForgeDirection face) {
        return true;
    }

    /** Whether it takes a single cable, so a second one can't join it. */
    default boolean oneCable() {
        return false;
    }
}
