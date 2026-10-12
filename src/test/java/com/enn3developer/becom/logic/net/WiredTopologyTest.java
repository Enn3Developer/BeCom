package com.enn3developer.becom.logic.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.LineModel;
import com.enn3developer.becom.logic.Tunables;

class WiredTopologyTest {

    private final LineModel line = Tunables.defaults()
        .lineModel();
    private final LinkRules rules = new LinkRules(
        Tunables.defaults()
            .pylonSpanFactor(),
        Tunables.defaults()
            .wrenchLinkDistance());
    private final NetworkModel model = new NetworkModel();
    private final Set<Long> offline = new HashSet<>();

    private long node(int x, NodeKind kind) {
        return node(x, 0, kind);
    }

    private long node(int x, int z, NodeKind kind) {
        long pos = BlockPos.pack(x, 100, z);
        NetNode node = new NetNode(pos, kind, null);
        node.setHeight(16);
        node.setFormed(true);
        model.add(node);
        return pos;
    }

    private List<WiredTopology.Segment> segments() {
        return WiredTopology.segments(model, pos -> !offline.contains(pos), line, rules);
    }

    private static WiredTopology.Segment from(List<WiredTopology.Segment> segments, long start) {
        return segments.stream()
            .filter(s -> s.from() == start)
            .findFirst()
            .orElseThrow();
    }

    /** The milestone's "Done when" line: 300 blocks, a repeater in the middle, a terminal at each end. */
    @Test
    void repeaterSplitsA300BlockLineIntoTwoEighteenDecibelStretches() {
        long a = node(0, NodeKind.TERMINAL);
        long p1 = node(30, NodeKind.PYLON);
        long p2 = node(90, NodeKind.PYLON);
        long r = node(150, NodeKind.REPEATER);
        long p3 = node(210, NodeKind.PYLON);
        long p4 = node(270, NodeKind.PYLON);
        long b = node(300, NodeKind.TERMINAL);
        model.link(a, p1);
        model.link(p1, p2);
        model.link(p2, r);
        model.link(r, p3);
        model.link(p3, p4);
        model.link(p4, b);
        List<WiredTopology.Segment> segments = segments();
        // From each end of each stretch: a->r, r->a, r->b, b->r.
        assertEquals(4, segments.size());
        WiredTopology.Segment first = from(segments, a);
        assertEquals(r, first.to());
        assertEquals(150, first.length(), 1e-9);
        assertEquals(18, first.snr(), 1e-9);
        WiredTopology.Segment last = from(segments, b);
        assertEquals(r, last.to());
        assertEquals(18, last.snr(), 1e-9);
    }

    @Test
    void passiveLineDegradesWithItsWholeLength() {
        long a = node(0, NodeKind.TERMINAL);
        long p1 = node(50, NodeKind.PYLON);
        long p2 = node(110, NodeKind.PYLON);
        long p3 = node(170, NodeKind.PYLON);
        long p4 = node(230, NodeKind.PYLON);
        long b = node(260, NodeKind.TERMINAL);
        model.link(a, p1);
        model.link(p1, p2);
        model.link(p2, p3);
        model.link(p3, p4);
        model.link(p4, b);
        WiredTopology.Segment segment = from(segments(), a);
        assertEquals(b, segment.to());
        assertEquals(260, segment.length(), 1e-9);
        assertEquals(30 - 0.08 * 260, segment.snr(), 1e-9);
    }

    @Test
    void anOfflineRepeaterCarriesNothing() {
        long a = node(0, NodeKind.TERMINAL);
        long r = node(60, NodeKind.REPEATER);
        long b = node(120, NodeKind.TERMINAL);
        model.link(a, r);
        model.link(r, b);
        offline.add(r);
        assertTrue(segments().isEmpty());
    }

    @Test
    void cuttingAPylonDownBreaksSpansItCanNoLongerHold() {
        // Two 16-high pylons 60 blocks apart are in span (4 x 16 = 64); cut one to 6 frames and the span is 24.
        long a = node(0, NodeKind.TERMINAL);
        long p1 = node(10, NodeKind.PYLON);
        long p2 = node(70, NodeKind.PYLON);
        long b = node(80, NodeKind.TERMINAL);
        model.link(a, p1);
        model.link(p1, p2);
        model.link(p2, b);
        assertEquals(2, segments().size());
        model.node(p2)
            .setHeight(6);
        assertTrue(segments().isEmpty());
    }

    @Test
    void aBlockedLinkBreaksTheLine() {
        long a = node(0, NodeKind.TERMINAL);
        long p = node(50, NodeKind.PYLON);
        long b = node(100, NodeKind.TERMINAL);
        model.link(a, p);
        model.link(p, b);
        assertEquals(2, segments().size());
        model.setBlocked(a, p, true);
        assertTrue(segments().isEmpty());
    }

    @Test
    void anUnformedPylonBreaksTheLine() {
        long a = node(0, NodeKind.TERMINAL);
        long p = node(50, NodeKind.PYLON);
        long b = node(100, NodeKind.TERMINAL);
        model.link(a, p);
        model.link(p, b);
        model.node(p)
            .setFormed(false);
        assertTrue(segments().isEmpty());
    }

    @Test
    void deadEndsAndLoopsOfPassivePylonsCarryNothing() {
        long a = node(0, NodeKind.TERMINAL);
        long p1 = node(50, NodeKind.PYLON);
        model.link(a, p1);
        assertTrue(segments().isEmpty());
        // A ring of passive pylons with no powered node anywhere in it.
        long q1 = node(500, NodeKind.PYLON);
        long q2 = node(550, NodeKind.PYLON);
        long q3 = node(600, NodeKind.PYLON);
        model.link(q1, q2);
        model.link(q2, q3);
        model.link(q3, q1);
        assertTrue(segments().isEmpty());
    }

    @Test
    void routerRegeneratesEachBranch() {
        long router = node(0, NodeKind.ROUTER);
        long a = node(-50, NodeKind.TERMINAL);
        long b = node(50, NodeKind.TERMINAL);
        // The third branch runs 200 blocks over pylons, within span.
        long p1 = node(0, 60, NodeKind.PYLON);
        long p2 = node(0, 120, NodeKind.PYLON);
        long p3 = node(0, 180, NodeKind.PYLON);
        long c = node(0, 200, NodeKind.TERMINAL);
        model.link(router, a);
        model.link(router, b);
        model.link(router, p1);
        model.link(p1, p2);
        model.link(p2, p3);
        model.link(p3, c);
        List<WiredTopology.Segment> segments = segments();
        assertEquals(6, segments.size());
        assertEquals(
            30 - 0.08 * 200,
            segments.stream()
                .filter(s -> s.from() == router && s.to() == c)
                .findFirst()
                .orElseThrow()
                .snr(),
            1e-9);
    }

    @Test
    void anAntennaBridgeDrivesTheLineAfreshLikeARepeater() {
        long a = node(0, NodeKind.TERMINAL);
        long p1 = node(30, NodeKind.PYLON);
        long antenna = node(40, NodeKind.ANTENNA_T1);
        model.link(a, p1);
        model.link(p1, antenna);
        List<WiredTopology.Segment> segments = segments();
        WiredTopology.Segment fromTerminal = from(segments, a);
        assertEquals(antenna, fromTerminal.to());
        assertEquals(40, fromTerminal.length(), 1e-9);
        assertEquals(line.snr(40), fromTerminal.snr(), 1e-9);
        assertEquals(a, from(segments, antenna).to());
    }

    @Test
    void aBridgeLongerThanTheBridgeDistanceCarriesNothing() {
        long a = node(0, NodeKind.TERMINAL);
        long antenna = node(10, NodeKind.ANTENNA_T2);
        model.link(a, antenna);
        assertEquals(2, segments().size());
        // The antenna moved off, or the distance shrank in the config since the link was made.
        model.remove(antenna);
        long far = node(17, NodeKind.ANTENNA_T2);
        model.link(a, far);
        assertTrue(segments().isEmpty());
    }
}
