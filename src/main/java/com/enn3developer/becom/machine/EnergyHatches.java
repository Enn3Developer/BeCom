package com.enn3developer.becom.machine;

import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** A multiblock's power drawn through its energy hatches, the one way GT multiblocks take it. */
final class EnergyHatches {

    private EnergyHatches() {}

    /** Takes {@code power} EU from the machine's energy hatches, or nothing at all when they hold less. */
    static boolean drain(MTEMultiBlockBase machine, long power) {
        long stored = 0;
        for (MTEHatchEnergy hatch : machine.mEnergyHatches) {
            if (hatch.isValid()) stored += hatch.getBaseMetaTileEntity()
                .getStoredEU();
        }
        // GT's drain takes what it can even when that's not enough; check first so a short tick is offline, not free.
        return stored >= power && machine.drainEnergyInput(power);
    }
}
