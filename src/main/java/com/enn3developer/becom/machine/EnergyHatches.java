package com.enn3developer.becom.machine;

import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.OverclockCalculator;

/**
 * A multiblock's power drawn through its energy hatches, the one way GT multiblocks take it, and the overclocks they
 * give it, counted as GT counts a recipe's: from their average voltage and all their amps, each overclock four times
 * the power for more work, as far as they can give it.
 */
final class EnergyHatches {

    // Past this many overclocks, calls would outgrow an int; eight is already absurd.
    static final int MAX_OVERCLOCKS = 8;

    private EnergyHatches() {}

    /**
     * How a multiblock's hatches run it, at its last structure check: the overclocks they give it, or what GT would
     * refuse a recipe for. {@code needs} is the power they fall short of, or the voltage tier they fall below.
     */
    record Fit(int overclocks, Problem problem, long needs, long gives) {

        static final Fit NONE = new Fit(0, Problem.NONE, 0, 0);

        boolean runs() {
            return problem == Problem.NONE;
        }

        /** {@code power} after its overclocks: four times as much each. */
        long power(long power) {
            return power << 2 * overclocks;
        }
    }

    enum Problem {
        NONE,
        /** Its hatches give less EU/t than it draws, before any overclock. */
        POWER,
        /** Its hatches' voltage is more than a tier below what it draws, as GT allows one tier's skip. */
        VOLTAGE
    }

    /**
     * How the machine's hatches run what draws {@code base} at heart, the recipe's EU/t that sets the voltage it
     * needs, and {@code power} in all before overclocks, the recipe's times its parallels.
     */
    static Fit fit(MTEMultiBlockBase machine, long base, long power) {
        long voltage = machine.getAverageInputVoltage();
        // GT gives a lone hatch one amp, as a single-block machine has.
        long amps = machine.mEnergyHatches.size() == 1 ? 1 : machine.getMaxInputAmps();
        long gives = voltage * amps;
        if (power > gives) return new Fit(0, Problem.POWER, power, gives);
        if (base > OverclockCalculator.getMaxAllowedRecipeEUt(voltage, 1))
            return new Fit(0, Problem.VOLTAGE, lowestTier(base), voltage);
        int overclocks = new OverclockCalculator().setRecipeEUt(power)
            .setEUt(voltage)
            .setAmperage(amps)
            .setAmperageOC(true)
            .setDuration(1)
            .setMaxOverclocks(MAX_OVERCLOCKS)
            .calculate()
            .getPerformedOverclocks();
        return new Fit(overclocks, Problem.NONE, 0, gives);
    }

    /** The lowest voltage tier GT runs {@code eut} on, one tier's skip allowed. */
    private static int lowestTier(long eut) {
        int tier = 0;
        while (tier < GTValues.V.length - 1 && OverclockCalculator.getMaxAllowedRecipeEUt(GTValues.V[tier], 1) < eut)
            tier++;
        return tier;
    }

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
