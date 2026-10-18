package com.enn3developer.becom.logic.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void everyNodeChangeMovesTheVersion() {
        NetworkModel model = new NetworkModel();
        long a = BlockPos.pack(0, 64, 0);
        long v0 = model.version();
        model.add(new NetNode(a, NodeKind.ROUTER, null));
        long v1 = model.version();
        assertTrue(v1 > v0);
        model.remove(a);
        long v2 = model.version();
        assertTrue(v2 > v1);
        // Removing what isn't there changes nothing, so the version stays.
        model.remove(a);
        assertEquals(v2, model.version());
    }

    @Test
    void addingANodeWhereOneIsReplacesIt() {
        NetworkModel model = new NetworkModel();
        long a = BlockPos.pack(0, 64, 0);
        model.add(new NetNode(a, NodeKind.ROUTER, null));
        model.add(new NetNode(a, NodeKind.EXCHANGE, null));
        assertEquals(1, model.size());
        assertEquals(
            NodeKind.EXCHANGE,
            model.node(a)
                .kind());
        model.remove(a);
        assertNull(model.node(a));
    }
}
