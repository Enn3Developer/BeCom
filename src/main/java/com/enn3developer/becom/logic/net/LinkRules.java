package com.enn3developer.becom.logic.net;

/**
 * Who may link to whom on the wired network. Ownership and clearance need the
 * world, so callers check those; everything else is here.
 */
public final class LinkRules {

    private final double spanFactor;
    private final double bridgeDistance;

    /**
     * @param spanFactor     how many times the shorter pylon's height two pylons may be apart
     * @param bridgeDistance how far an antenna is wired to a pylon, a terminal or an Exchange
     */
    public LinkRules(double spanFactor, double bridgeDistance) {
        this.spanFactor = spanFactor;
        this.bridgeDistance = bridgeDistance;
    }

    public enum Result {
        OK,
        SAME_NODE,
        ALREADY_LINKED,
        INCOMPATIBLE,
        NOT_FORMED,
        NO_FREE_SLOT,
        TOO_FAR
    }

    /**
     * The longest link {@code a} and {@code b} may have: the span factor times the shorter pylon's height. A terminal
     * or an Exchange on the ground borrows its pylon's height, since only the pylon's end is up in the air. An
     * antenna's wiring reaches the bridge distance.
     */
    public double maxSpan(NetNode a, NetNode b) {
        if (a.kind()
            .bridge()
            || b.kind()
                .bridge())
            return bridgeDistance;
        if (a.kind()
            .pylonHead()
            && b.kind()
                .pylonHead())
            return spanFactor * Math.min(a.height(), b.height());
        if (a.kind()
            .pylonHead()) return spanFactor * a.height();
        if (b.kind()
            .pylonHead()) return spanFactor * b.height();
        return 0;
    }

    public Result check(NetworkModel model, NetNode a, NetNode b) {
        if (a.pos() == b.pos()) return Result.SAME_NODE;
        if (model.linked(a.pos(), b.pos())) return Result.ALREADY_LINKED;
        if (a.kind()
            .linkSlots() == 0
            || b.kind()
                .linkSlots() == 0)
            return Result.INCOMPATIBLE;
        if (a.kind()
            .bridge()
            || b.kind()
                .bridge()) {
            // Antennas are wired to a pylon, a terminal or an Exchange; they reach each other by radio.
            if (a.kind()
                .bridge()
                && b.kind()
                    .bridge())
                return Result.INCOMPATIBLE;
        } else if (!a.kind()
            .pylonHead()
            && !b.kind()
                .pylonHead()) {
                    // A line runs over pylon heads; terminals and Exchanges only end one.
                    return Result.INCOMPATIBLE;
                }
        if (!a.formed() || !b.formed()) return Result.NOT_FORMED;
        if (!a.hasFreeSlot() || !b.hasFreeSlot()) return Result.NO_FREE_SLOT;
        if (a.distanceTo(b) > maxSpan(a, b)) return Result.TOO_FAR;
        return Result.OK;
    }

}
