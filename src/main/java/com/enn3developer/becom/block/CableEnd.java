package com.enn3developer.becom.block;

import net.minecraftforge.common.util.ForgeDirection;

import com.enn3developer.becom.logic.net.Buses;
import com.enn3developer.becom.logic.net.CableTier;

/**
 * A machine a cable can end at: what it is to the bus (a phone, a router or a gateway), the best cable it takes, on
 * which faces, and whether only one at a time. A Line Hatch is one too, for the multiblock it's part of.
 */
public interface CableEnd {

    /** What it is to a bus. */
    Buses.Kind busKind();

    /** A phone's number, as its bus lists it; -1 for one without, and for anything that isn't a phone. */
    default int busNumber() {
        return -1;
    }

    /**
     * What a bus reaching this machine at {@code pos} lists: the machine itself, by default; or null when it joins
     * nothing.
     */
    default Buses.End busEnd(long pos) {
        return new Buses.End(pos, busKind(), busNumber());
    }

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
