package com.enn3developer.becom.logic.net;

import java.util.function.BiPredicate;

/**
 * Who may link to whom on the wired network (DESIGN.md 5.1, 5.2). Ownership (D37) and clearance (D20) need the
 * world, so callers check those; everything else is here.
 */
public final class LinkRules {

    private final double spanFactor;
    private final double bridgeDistance;

    /**
     * @param spanFactor     how many times the shorter pylon's height two pylons may be apart (DESIGN.md 17)
     * @param bridgeDistance how far an antenna reaches to bridge a Pylon Head or Line Terminal (DESIGN.md 7)
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
     * on the ground borrows its pylon's height, since only the pylon's end is up in the air. An antenna's bridge
     * reaches
     * the bridge distance.
     */
    public double maxSpan(NetNode a, NetNode b) {
        if (a.kind()
            .antenna()
            || b.kind()
                .antenna())
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
        // An antenna bridges a Pylon Head or a Line Terminal (DESIGN.md 7); antennas reach each other by radio.
        if (a.kind()
            .antenna()
            || b.kind()
                .antenna()) {
            NetNode wired = a.kind()
                .antenna() ? b : a;
            if (!wired.kind()
                .pylonHead() && wired.kind() != NodeKind.TERMINAL) return Result.INCOMPATIBLE;
        } else if (!a.kind()
            .pylonHead()
            && !b.kind()
                .pylonHead()) {
                    // A line runs over pylon heads; a terminal only ends one (DESIGN.md 5.2).
                    return Result.INCOMPATIBLE;
                }
        if (!a.formed() || !b.formed()) return Result.NOT_FORMED;
        if (!a.hasFreeSlot() || !b.hasFreeSlot()) return Result.NO_FREE_SLOT;
        if (a.distanceTo(b) > maxSpan(a, b)) return Result.TOO_FAR;
        return Result.OK;
    }

    /**
     * The pylon a newly formed pylon links to on its own: the one formed pylon in range with a free slot, or null
     * when there are none or several (DESIGN.md 5.1).
     *
     * @param clear the clearance check between two heads
     */
    public NetNode autoLinkTarget(NetworkModel model, NetNode formed, BiPredicate<NetNode, NetNode> clear) {
        if (!formed.kind()
            .pylonHead()) return null;
        NetNode only = null;
        for (NetNode other : model.nodes()) {
            if (!other.kind()
                .pylonHead()) continue;
            if (check(model, formed, other) != Result.OK) continue;
            if (!clear.test(formed, other)) continue;
            if (only != null) return null;
            only = other;
        }
        return only;
    }
}
