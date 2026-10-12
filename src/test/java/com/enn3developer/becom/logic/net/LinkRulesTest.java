package com.enn3developer.becom.logic.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** DESIGN.md 5.1, 5.2: spans, slots and who links to whom. */
class LinkRulesTest {

    private final LinkRules rules = new LinkRules(4, 16);
    private final NetworkModel model = new NetworkModel();

    private NetNode node(int x, int z, NodeKind kind, int height) {
        NetNode node = new NetNode(BlockPos.pack(x, 100, z), kind, null);
        node.setHeight(height);
        node.setFormed(true);
        model.add(node);
        return node;
    }

    @Test
    void spanIsFourTimesTheShorterPylon() {
        NetNode tall = node(0, 0, NodeKind.PYLON, 16);
        NetNode shorter = node(40, 0, NodeKind.PYLON, 10);
        assertEquals(40, rules.maxSpan(tall, shorter), 1e-9);
        assertEquals(LinkRules.Result.OK, rules.check(model, tall, shorter));
        NetNode tooFar = node(0, 41, NodeKind.PYLON, 10);
        assertEquals(LinkRules.Result.TOO_FAR, rules.check(model, tall, tooFar));
    }

    @Test
    void shortestAndTallestPylonsGive24And64Blocks() {
        NetNode a = node(0, 0, NodeKind.PYLON, 6);
        NetNode b = node(24, 0, NodeKind.PYLON, 6);
        assertEquals(LinkRules.Result.OK, rules.check(model, a, b));
        NetNode c = node(0, 200, NodeKind.PYLON, 16);
        NetNode d = node(64, 200, NodeKind.PYLON, 16);
        assertEquals(LinkRules.Result.OK, rules.check(model, c, d));
        NetNode e = node(65, 200, NodeKind.PYLON, 16);
        assertEquals(LinkRules.Result.TOO_FAR, rules.check(model, c, e));
    }

    @Test
    void plainHeadsHaveTwoSlotsRoutersFifteen() {
        NetNode head = node(0, 0, NodeKind.PYLON, 16);
        model.link(head.pos(), node(10, 0, NodeKind.PYLON, 16).pos());
        model.link(head.pos(), node(20, 0, NodeKind.PYLON, 16).pos());
        assertEquals(LinkRules.Result.NO_FREE_SLOT, rules.check(model, head, node(30, 0, NodeKind.PYLON, 16)));
        NetNode router = node(0, 100, NodeKind.ROUTER, 16);
        for (int i = 0; i < 15; i++) {
            NetNode other = node(5 + i, 110, NodeKind.PYLON, 16);
            assertEquals(LinkRules.Result.OK, rules.check(model, router, other));
            model.link(router.pos(), other.pos());
        }
        assertEquals(LinkRules.Result.NO_FREE_SLOT, rules.check(model, router, node(40, 110, NodeKind.PYLON, 16)));
    }

    @Test
    void terminalsLinkOnlyToPylonHeadsAndOnlyOne() {
        NetNode terminal = node(0, 0, NodeKind.TERMINAL, 0);
        NetNode otherTerminal = node(5, 0, NodeKind.TERMINAL, 0);
        assertEquals(LinkRules.Result.INCOMPATIBLE, rules.check(model, terminal, otherTerminal));
        NetNode head = node(10, 0, NodeKind.PYLON, 16);
        // A terminal borrows its pylon's height for the span.
        assertEquals(64, rules.maxSpan(terminal, head), 1e-9);
        assertEquals(LinkRules.Result.OK, rules.check(model, terminal, head));
        model.link(terminal.pos(), head.pos());
        assertEquals(LinkRules.Result.NO_FREE_SLOT, rules.check(model, terminal, node(20, 0, NodeKind.PYLON, 16)));
    }

    @Test
    void unformedPylonsAndDuplicatesAreRefused() {
        NetNode a = node(0, 0, NodeKind.PYLON, 16);
        NetNode b = node(10, 0, NodeKind.PYLON, 16);
        b.setFormed(false);
        assertEquals(LinkRules.Result.NOT_FORMED, rules.check(model, a, b));
        b.setFormed(true);
        model.link(a.pos(), b.pos());
        assertEquals(LinkRules.Result.ALREADY_LINKED, rules.check(model, a, b));
        assertEquals(LinkRules.Result.SAME_NODE, rules.check(model, a, a));
    }

    @Test
    void autoLinksOnlyWhenExactlyOnePylonIsInRange() {
        NetNode first = node(0, 0, NodeKind.PYLON, 16);
        NetNode formed = node(50, 0, NodeKind.PYLON, 16);
        assertEquals(first, rules.autoLinkTarget(model, formed, (x, y) -> true));
        // A second candidate in range makes it ambiguous: link by hand.
        node(50, 40, NodeKind.PYLON, 16);
        assertNull(rules.autoLinkTarget(model, formed, (x, y) -> true));
    }

    @Test
    void autoLinkSkipsBlockedAndFullPylons() {
        NetNode first = node(0, 0, NodeKind.PYLON, 16);
        NetNode formed = node(50, 0, NodeKind.PYLON, 16);
        assertNull(rules.autoLinkTarget(model, formed, (x, y) -> false));
        model.link(first.pos(), node(-20, 0, NodeKind.PYLON, 16).pos());
        model.link(first.pos(), node(-40, 0, NodeKind.PYLON, 16).pos());
        assertNull(rules.autoLinkTarget(model, formed, (x, y) -> true));
    }

    @Test
    void antennasBridgePylonHeadsAndTerminalsWithinTheBridgeDistance() {
        NetNode antenna = node(0, 0, NodeKind.ANTENNA_T1, 8);
        NetNode pylon = node(16, 0, NodeKind.PYLON, 6);
        NetNode terminal = node(0, 16, NodeKind.TERMINAL, 0);
        assertEquals(16, rules.maxSpan(antenna, pylon), 1e-9);
        assertEquals(LinkRules.Result.OK, rules.check(model, antenna, pylon));
        assertEquals(LinkRules.Result.OK, rules.check(model, terminal, antenna));
        assertEquals(LinkRules.Result.TOO_FAR, rules.check(model, antenna, node(17, 0, NodeKind.ROUTER, 16)));
    }

    @Test
    void antennasDontWrenchLinkEachOtherOrTransceivers() {
        NetNode a = node(0, 0, NodeKind.ANTENNA_T1, 8);
        assertEquals(LinkRules.Result.INCOMPATIBLE, rules.check(model, a, node(5, 0, NodeKind.ANTENNA_T2, 8)));
        assertEquals(LinkRules.Result.INCOMPATIBLE, rules.check(model, a, node(0, 5, NodeKind.TRANSCEIVER, 0)));
        assertEquals(
            LinkRules.Result.INCOMPATIBLE,
            rules.check(model, node(9, 9, NodeKind.PYLON, 6), node(9, 12, NodeKind.TRANSCEIVER, 0)));
    }

    @Test
    void anAntennaNeedsItsStructure() {
        NetNode antenna = node(0, 0, NodeKind.ANTENNA_T3, 8);
        antenna.setFormed(false);
        assertEquals(LinkRules.Result.NOT_FORMED, rules.check(model, antenna, node(4, 0, NodeKind.PYLON, 6)));
    }

    @Test
    void anMeBridgeBridgesAnyOtherNodeWithinTheBridgeDistance() {
        NetNode bridge = node(0, 0, NodeKind.ME_BRIDGE, 0);
        NetNode router = node(10, 0, NodeKind.ROUTER, 16);
        NetNode antenna = node(0, 12, NodeKind.ANTENNA_T1, 0);
        NetNode far = node(20, 0, NodeKind.TERMINAL, 0);
        NetNode other = node(0, 5, NodeKind.ME_BRIDGE, 0);
        assertEquals(LinkRules.Result.OK, rules.check(model, bridge, router));
        assertEquals(LinkRules.Result.OK, rules.check(model, antenna, bridge));
        assertEquals(LinkRules.Result.TOO_FAR, rules.check(model, bridge, far));
        // Two ME Bridges would join two ME networks, which is AE2's business.
        assertEquals(LinkRules.Result.INCOMPATIBLE, rules.check(model, bridge, other));
        model.link(bridge.pos(), router.pos());
        // One link only.
        assertEquals(LinkRules.Result.NO_FREE_SLOT, rules.check(model, bridge, antenna));
    }
}
