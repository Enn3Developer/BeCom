package com.enn3developer.becom.logic.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NetworkModelTest {

    @Test
    void packedPositionsRoundTripIncludingNegatives() {
        int[][] cases = { { 0, 0, 0 }, { -1, 5, -1 }, { 30_000_000, 255, -30_000_000 }, { -123456, 64, 654321 } };
        for (int[] c : cases) {
            long pos = BlockPos.pack(c[0], c[1], c[2]);
            assertEquals(c[0], BlockPos.x(pos));
            assertEquals(c[1], BlockPos.y(pos));
            assertEquals(c[2], BlockPos.z(pos));
        }
    }

    @Test
    void removingANodeRemovesEveryLinkToIt() {
        NetworkModel model = new NetworkModel();
        long a = BlockPos.pack(0, 64, 0);
        long b = BlockPos.pack(10, 64, 0);
        long c = BlockPos.pack(20, 64, 0);
        model.add(new NetNode(a, NodeKind.PYLON, null));
        model.add(new NetNode(b, NodeKind.PYLON, null));
        model.add(new NetNode(c, NodeKind.PYLON, null));
        model.link(a, b);
        model.link(b, c);
        model.setBlocked(b, c, true);
        model.remove(b);
        assertTrue(
            model.node(a)
                .links()
                .isEmpty());
        assertTrue(
            model.node(c)
                .links()
                .isEmpty());
        assertFalse(model.isBlocked(b, c));
        assertTrue(
            model.links()
                .isEmpty());
    }

    @Test
    void everyTopologyChangeMovesTheVersion() {
        NetworkModel model = new NetworkModel();
        long a = BlockPos.pack(0, 64, 0);
        long b = BlockPos.pack(10, 64, 0);
        long v0 = model.version();
        model.add(new NetNode(a, NodeKind.PYLON, null));
        model.add(new NetNode(b, NodeKind.PYLON, null));
        long v1 = model.version();
        assertTrue(v1 > v0);
        model.link(a, b);
        long v2 = model.version();
        assertTrue(v2 > v1);
        // Linking again changes nothing, so the version stays.
        model.link(a, b);
        assertEquals(v2, model.version());
        model.setBlocked(a, b, true);
        assertTrue(model.version() > v2);
        long v3 = model.version();
        model.setBlocked(a, b, true);
        assertEquals(v3, model.version());
    }

    @Test
    void replacingANodeDropsTheOldOnesLinks() {
        NetworkModel model = new NetworkModel();
        long a = BlockPos.pack(0, 64, 0);
        long b = BlockPos.pack(10, 64, 0);
        model.add(new NetNode(a, NodeKind.PYLON, null));
        model.add(new NetNode(b, NodeKind.PYLON, null));
        model.link(a, b);
        model.add(new NetNode(a, NodeKind.ROUTER, null));
        assertFalse(model.linked(a, b));
        assertTrue(
            model.node(b)
                .links()
                .isEmpty());
    }
}
