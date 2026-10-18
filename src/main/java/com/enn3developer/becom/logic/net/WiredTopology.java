package com.enn3developer.becom.logic.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;

import com.enn3developer.becom.logic.Tunables;

/**
 * Collapses the wired network into the runs a call travels: from each working powered node, along
 * passive pylons, to the next working powered node. Wire is digital, so a run loses nothing; what it has is a data
 * rate, from the cables on its spans. A cable counts on a run only if the whole run is within its reach, and a run
 * carries what its thinnest span does, which is why passive pylons aren't vertices of their own.
 */
public final class WiredTopology {

    private WiredTopology() {}

    /**
     * One run between two powered nodes, as seen from {@code from}: {@code rate} is what it carries in KB/s, 0 when
     * no span has a cable that reaches the whole run; {@code cables} are those strung on its first span.
     */
    public record Segment(long from, long to, double length, int rate, List<CableTier> cables) {}

    /**
     * Every run leaving every working powered node; a run between two working nodes shows up once from each end.
     *
     * @param working whether the powered node at a position is formed, loaded and powered
     * @param rules   the span limit, checked against each pylon's height now rather than when it was linked
     */
    public static List<Segment> segments(NetworkModel model, LongPredicate working, Tunables cables, LinkRules rules) {
        List<Segment> segments = new ArrayList<>();
        for (NetNode start : model.nodes()) {
            if (!start.kind()
                .powered() || !working.test(start.pos())) continue;
            for (long first : start.links()) {
                Segment segment = walk(model, start, first, working, cables, rules);
                if (segment != null) segments.add(segment);
            }
        }
        return segments;
    }

    private static Segment walk(NetworkModel model, NetNode start, long first, LongPredicate working, Tunables cables,
        LinkRules rules) {
        long prev = start.pos();
        long cur = first;
        double length = 0;
        List<List<CableTier>> spans = new ArrayList<>();
        // A chain of passive pylons visits each at most once; anything longer is a loop of pylons with no
        // powered node, which carries nothing.
        for (int steps = 0; steps <= model.size(); steps++) {
            if (model.isBlocked(prev, cur)) return null;
            NetNode node = model.node(cur);
            NetNode from = model.node(prev);
            if (node == null || from == null) return null;
            double span = BlockPos.distance(prev, cur);
            // A pylon cut down after linking can't hold the spans it held when it was taller.
            if (span > rules.maxSpan(from, node)) return null;
            length += span;
            spans.add(model.cables(prev, cur));
            if (node.kind()
                .powered()) {
                return working.test(cur)
                    ? new Segment(start.pos(), cur, length, rate(spans, length, cables), spans.get(0))
                    : null;
            }
            if (!node.formed()) return null;
            long next = otherLink(node, prev);
            if (next == Long.MIN_VALUE) return null;
            prev = cur;
            cur = next;
        }
        return null;
    }

    /** What a run of {@code length} blocks over these spans carries: its thinnest span's cables that reach it all. */
    public static int rate(List<List<CableTier>> spans, double length, Tunables cables) {
        int rate = Integer.MAX_VALUE;
        for (List<CableTier> span : spans) rate = Math.min(rate, spanRate(span, length, cables));
        return spans.isEmpty() ? 0 : rate;
    }

    /** What one span's cables carry on a run of {@code length} blocks. */
    public static int spanRate(List<CableTier> span, double length, Tunables cables) {
        int rate = 0;
        for (CableTier cable : span) if (length <= cables.cableReach(cable)) rate += cables.cableRate(cable);
        return rate;
    }

    /** The passive pylon's link that isn't {@code from}, or {@link Long#MIN_VALUE} at a dead end. */
                                                                                                     /**
                                                                                                      * The run of wire
                                                                                                      * through a
                                                                                                      * passive pylon,
                                                                                                      * out both ways to
                                                                                                      * the powered
                                                                                                      * nodes or dead
                                                                                                      * ends there: its
                                                                                                      * length, and
                                                                                                      * the best cable
                                                                                                      * strung on every
                                                                                                      * span of it, or
                                                                                                      * null when no one
                                                                                                      * cable is.
                                                                                                      */
    public record Through(double length, CableTier cable) {}

    public static Through through(NetworkModel model, long pos) {
        NetNode pylon = model.node(pos);
        if (pylon == null) return new Through(0, null);
        double length = 0;
        java.util.EnumSet<CableTier> common = java.util.EnumSet.allOf(CableTier.class);
        boolean any = false;
        for (long first : pylon.links()) {
            long prev = pos;
            long cur = first;
            for (int steps = 0; steps <= model.size(); steps++) {
                length += BlockPos.distance(prev, cur);
                List<CableTier> span = model.cables(prev, cur);
                common.retainAll(
                    span.isEmpty() ? java.util.EnumSet.noneOf(CableTier.class) : java.util.EnumSet.copyOf(span));
                any = true;
                NetNode node = model.node(cur);
                if (node == null || node.kind()
                    .powered()) break;
                long next = otherLink(node, prev);
                if (next == Long.MIN_VALUE) break;
                prev = cur;
                cur = next;
            }
        }
        CableTier best = null;
        for (CableTier tier : common) best = tier;
        return new Through(length, any ? best : null);
    }

    private static long otherLink(NetNode pylon, long from) {
        for (long other : pylon.links()) {
            if (other != from) return other;
        }
        return Long.MIN_VALUE;
    }
}
