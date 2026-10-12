package com.enn3developer.becom.logic.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;

import com.enn3developer.becom.logic.LineModel;

/**
 * Collapses the wired network into the hops a call travels (DESIGN.md 5.7): from each working powered node, along
 * passive pylons, to the next working powered node. Each stretch's SNR comes from its length since the powered node
 * that drove it, which is why passive pylons aren't vertices of their own.
 */
public final class WiredTopology {

    private WiredTopology() {}

    /** One stretch of line between two powered nodes, as seen from {@code from}. */
    public record Segment(long from, long to, double length, double snr) {}

    /**
     * Every stretch leaving every working powered node; a stretch between two working nodes shows up once from each
     * end.
     *
     * @param working whether the powered node at a position is formed, loaded and powered (D11)
     */
    public static List<Segment> segments(NetworkModel model, LongPredicate working, LineModel line) {
        List<Segment> segments = new ArrayList<>();
        for (NetNode start : model.nodes()) {
            if (!start.kind()
                .powered() || !working.test(start.pos())) continue;
            for (long first : start.links()) {
                Segment segment = walk(model, start, first, working, line);
                if (segment != null) segments.add(segment);
            }
        }
        return segments;
    }

    private static Segment walk(NetworkModel model, NetNode start, long first, LongPredicate working, LineModel line) {
        long prev = start.pos();
        long cur = first;
        double length = 0;
        // A chain of passive pylons visits each at most once; anything longer is a loop of pylons with no
        // powered node, which carries nothing.
        for (int steps = 0; steps <= model.size(); steps++) {
            if (model.isBlocked(prev, cur)) return null;
            NetNode node = model.node(cur);
            if (node == null) return null;
            length += BlockPos.distance(prev, cur);
            if (node.kind()
                .powered()) {
                return working.test(cur) ? new Segment(start.pos(), cur, length, line.snr(length)) : null;
            }
            if (!node.formed()) return null;
            long next = otherLink(node, prev);
            if (next == Long.MIN_VALUE) return null;
            prev = cur;
            cur = next;
        }
        return null;
    }

    /** The passive pylon's link that isn't {@code from}, or {@link Long#MIN_VALUE} at a dead end. */
    private static long otherLink(NetNode pylon, long from) {
        for (long other : pylon.links()) {
            if (other != from) return other;
        }
        return Long.MIN_VALUE;
    }
}
