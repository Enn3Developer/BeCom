package com.enn3developer.becom.logic.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.Tunables;

class WiredTopologyTest {

    private final Tunables cables = Tunables.defaults();
    private final LinkRules rules = new LinkRules(cables.pylonSpanFactor(), cables.wrenchLinkDistance());
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

    /** Links two nodes with one Phone Cable on the span. */
    private void link(long a, long b) {
        link(a, b, CableTier.PHONE);
    }

    private void link(long a, long b, CableTier... strung) {
        model.link(a, b, List.of(strung));
    }

    private List<WiredTopology.Segment> segments() {
        return WiredTopology.segments(model, pos -> !offline.contains(pos), cables, rules);
    }

    private static WiredTopology.Segment from(List<WiredTopology.Segment> segments, long start) {
        return segments.stream()
            .filter(s -> s.from() == start)
            .findFirst()
            .orElseThrow();
    }

    /** A 300-block line with pylons at 30, 90, 150, 210 and 270, every span strung with {@code strung}. */
    private long[] line300(CableTier... strung) {
        long[] nodes = { node(0, NodeKind.TERMINAL), node(30, NodeKind.PYLON), node(90, NodeKind.PYLON),
            node(150, NodeKind.PYLON), node(210, NodeKind.PYLON), node(270, NodeKind.PYLON),
            node(300, NodeKind.TERMINAL) };
        for (int i = 0; i + 1 < nodes.length; i++) link(nodes[i], nodes[i + 1], strung);
        return nodes;
    }

    @Test
    void aRunWithinItsCablesReachCarriesItsRateBothWays() {
        long a = node(0, NodeKind.TERMINAL);
        long p = node(50, NodeKind.PYLON);
        long b = node(100, NodeKind.TERMINAL);
        link(a, p);
        link(p, b);
        List<WiredTopology.Segment> segments = segments();
        assertEquals(2, segments.size());
        WiredTopology.Segment run = from(segments, a);
        assertEquals(b, run.to());
        assertEquals(100, run.length(), 1e-9);
        assertEquals(cables.cableRatePhone(), run.rate());
        assertEquals(cables.cableRatePhone(), from(segments, b).rate());
    }

    @Test
    void phoneCableCarriesNothingOnARunPastItsReach() {
        long[] line = line300(CableTier.PHONE);
        assertEquals(0, from(segments(), line[0]).rate());
    }

    @Test
    void fibreStrungAlongsideCarriesTheLongRunAndThePhoneCableStillCountsForNothing() {
        long[] line = line300(CableTier.FIBRE, CableTier.PHONE);
        assertEquals(cables.cableRateFibre(), from(segments(), line[0]).rate());
    }

    @Test
    void cablesOnOneSpanAddUp() {
        long a = node(0, NodeKind.TERMINAL);
        long p = node(50, NodeKind.PYLON);
        long b = node(100, NodeKind.TERMINAL);
        link(a, p, CableTier.PHONE, CableTier.PHONE, CableTier.COAXIAL);
        link(p, b, CableTier.PHONE, CableTier.PHONE, CableTier.COAXIAL);
        assertEquals(2 * cables.cableRatePhone() + cables.cableRateCoaxial(), from(segments(), a).rate());
    }

    @Test
    void theThinnestSpanSetsTheRate() {
        long a = node(0, NodeKind.TERMINAL);
        long p = node(40, NodeKind.PYLON);
        long b = node(80, NodeKind.TERMINAL);
        link(a, p, CableTier.COAXIAL);
        link(p, b, CableTier.PHONE);
        assertEquals(cables.cableRatePhone(), from(segments(), a).rate());
    }

    @Test
    void aSpanWithNoCableCarriesNothing() {
        long a = node(0, NodeKind.TERMINAL);
        long p = node(40, NodeKind.PYLON);
        long b = node(80, NodeKind.TERMINAL);
        link(a, p);
        link(p, b, new CableTier[0]);
        assertEquals(0, from(segments(), a).rate());
    }

    @Test
    void anOfflineExchangeCarriesNothing() {
        long a = node(0, NodeKind.TERMINAL);
        long p1 = node(30, NodeKind.PYLON);
        long exchange = node(60, NodeKind.EXCHANGE);
        long p2 = node(90, NodeKind.PYLON);
        long b = node(120, NodeKind.TERMINAL);
        link(a, p1);
        link(p1, exchange);
        link(exchange, p2);
        link(p2, b);
        assertEquals(4, segments().size());
        offline.add(exchange);
        assertTrue(segments().isEmpty());
    }

    @Test
    void cuttingAPylonDownBreaksSpansItCanNoLongerHold() {
        // Two 16-high pylons 60 blocks apart are in span (4 x 16 = 64); cut one to 6 frames and the span is 24.
        long a = node(0, NodeKind.TERMINAL);
        long p1 = node(10, NodeKind.PYLON);
        long p2 = node(70, NodeKind.PYLON);
        long b = node(80, NodeKind.TERMINAL);
        link(a, p1);
        link(p1, p2);
        link(p2, b);
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
        link(a, p);
        link(p, b);
        assertEquals(2, segments().size());
        model.setBlocked(a, p, true);
        assertTrue(segments().isEmpty());
    }

    @Test
    void anUnformedPylonBreaksTheLine() {
        long a = node(0, NodeKind.TERMINAL);
        long p = node(50, NodeKind.PYLON);
        long b = node(100, NodeKind.TERMINAL);
        link(a, p);
        link(p, b);
        model.node(p)
            .setFormed(false);
        assertTrue(segments().isEmpty());
    }

    @Test
    void deadEndsAndLoopsOfPassivePylonsCarryNothing() {
        long a = node(0, NodeKind.TERMINAL);
        long p1 = node(50, NodeKind.PYLON);
        link(a, p1);
        assertTrue(segments().isEmpty());
        // A ring of passive pylons with no powered node anywhere in it.
        long q1 = node(500, NodeKind.PYLON);
        long q2 = node(550, NodeKind.PYLON);
        long q3 = node(600, NodeKind.PYLON);
        link(q1, q2);
        link(q2, q3);
        link(q3, q1);
        assertTrue(segments().isEmpty());
    }

    @Test
    void anExchangeEndsEachBranchItsOwnRun() {
        long exchange = node(0, NodeKind.EXCHANGE);
        long p0 = node(30, NodeKind.PYLON);
        long a = node(60, NodeKind.TERMINAL);
        // The second branch runs 200 blocks over pylons: too far for Phone Cable, so it's strung with Coaxial.
        long p1 = node(0, 60, NodeKind.PYLON);
        long p2 = node(0, 120, NodeKind.PYLON);
        long p3 = node(0, 180, NodeKind.PYLON);
        long c = node(0, 200, NodeKind.TERMINAL);
        link(exchange, p0);
        link(p0, a);
        link(exchange, p1, CableTier.COAXIAL);
        link(p1, p2, CableTier.COAXIAL);
        link(p2, p3, CableTier.COAXIAL);
        link(p3, c, CableTier.COAXIAL);
        List<WiredTopology.Segment> segments = segments();
        assertEquals(4, segments.size());
        assertEquals(
            cables.cableRateCoaxial(),
            segments.stream()
                .filter(s -> s.from() == exchange && s.to() == c)
                .findFirst()
                .orElseThrow()
                .rate());
        assertEquals(
            cables.cableRatePhone(),
            segments.stream()
                .filter(s -> s.from() == exchange && s.to() == a)
                .findFirst()
                .orElseThrow()
                .rate());
    }

    @Test
    void anAntennaWiredToALineEndsItsRun() {
        long a = node(0, NodeKind.TERMINAL);
        long p1 = node(30, NodeKind.PYLON);
        long antenna = node(40, NodeKind.ANTENNA_T1);
        link(a, p1);
        link(p1, antenna);
        List<WiredTopology.Segment> segments = segments();
        WiredTopology.Segment fromTerminal = from(segments, a);
        assertEquals(antenna, fromTerminal.to());
        assertEquals(40, fromTerminal.length(), 1e-9);
        assertEquals(cables.cableRatePhone(), fromTerminal.rate());
        assertEquals(a, from(segments, antenna).to());
    }

    @Test
    void aBridgeLongerThanTheBridgeDistanceCarriesNothing() {
        long a = node(0, NodeKind.TERMINAL);
        long antenna = node(10, NodeKind.ANTENNA_T2);
        link(a, antenna);
        assertEquals(2, segments().size());
        // The antenna moved off, or the distance shrank in the config since the link was made.
        model.remove(antenna);
        long far = node(17, NodeKind.ANTENNA_T2);
        link(a, far);
        assertTrue(segments().isEmpty());
    }
}
