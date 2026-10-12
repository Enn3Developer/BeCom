package com.enn3developer.becom.logic;

/**
 * Line loss on the wired network (DESIGN.md 5.7): every powered node drives the line at the budget, and each block of
 * wire since the last powered node costs {@code lossPerBlock}.
 */
public final class LineModel {

    private final double budget;
    private final double lossPerBlock;

    public LineModel(double budget, double lossPerBlock) {
        if (lossPerBlock < 0) throw new IllegalArgumentException("line loss must not be negative: " + lossPerBlock);
        this.budget = budget;
        this.lossPerBlock = lossPerBlock;
    }

    /** SNR of a stretch of line {@code length} blocks long. */
    public double snr(double length) {
        return budget - lossPerBlock * length;
    }

    /** The longest stretch that still reaches {@code snr}. */
    public double lengthFor(double snr) {
        if (lossPerBlock == 0) return Double.POSITIVE_INFINITY;
        return (budget - snr) / lossPerBlock;
    }
}
