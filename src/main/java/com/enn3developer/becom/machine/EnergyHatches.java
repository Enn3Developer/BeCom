package com.enn3developer.becom.machine;

import java.util.List;

import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;

/**
 * A multiblock's power drawn through its energy hatches, the one way GT multiblocks take it, and the overclocks they
 * give it: a multiblock runs at its lowest hatch's tier, at least its own, and each tier above its own is one
 * overclock, four times the power for more work, as GT's own multiblocks.
 */
final class EnergyHatches {

    // Past this many overclocks, calls would outgrow an int; a hatch eight tiers above its machine is already absurd.
    static final int MAX_OVERCLOCKS = 8;

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

    /** The tier its energy hatches run it at: their lowest, or -1 with none. */
    static int tier(MTEMultiBlockBase machine) {
        int lowest = -1;
        for (MTEHatchEnergy hatch : machine.mEnergyHatches)
            if (hatch.isValid() && (lowest < 0 || hatch.mTier < lowest)) lowest = hatch.mTier;
        return lowest;
    }

    /** At a structure check: GT's error when an energy hatch is below the machine's own {@code tier}. */
    static void checkTier(MTEMultiBlockBase machine, int tier, List<StructureError> errors) {
        int lowest = tier(machine);
        if (lowest >= 0 && lowest < tier) errors.add(StructureErrors.energyHatchTierTooLow(tier));
    }

    /** How many tiers above its own {@code tier} its energy hatches run it. */
    static int overclocks(MTEMultiBlockBase machine, int tier) {
        return Math.max(0, Math.min(MAX_OVERCLOCKS, tier(machine) - tier));
    }

    /** {@code power} after {@code overclocks}: four times as much each. */
    static long power(long power, int overclocks) {
        return power << 2 * overclocks;
    }
}
