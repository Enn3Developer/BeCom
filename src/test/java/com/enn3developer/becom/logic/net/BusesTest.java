package com.enn3developer.becom.logic.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Buses: runs of one tier joined through sides open both ways, their length, and working them out again. */
class BusesTest {

    // ForgeDirection ordinals: down, up, north, south, west, east.
    private static final int WEST = 4, EAST = 5, NORTH = 2, SOUTH = 3;
    private static final int Y = 64;

    private final Map<Long, CableTier> cables = new HashMap<>();
    private final Map<Long, Integer> masks = new HashMap<>();
    private final Map<Long, Buses.End> ends = new HashMap<>();
    private final Set<Long> unloadedChunks = new HashSet<>();
    private final Buses buses = new Buses();
    private int limit = 128;

    private final Buses.Probe probe = new Buses.Probe() {

        @Override
        public boolean loaded(int x, int y, int z) {
            return !unloadedChunks.contains(chunk(x >> 4, z >> 4));
        }

        @Override
        public CableTier cable(int x, int y, int z) {
            return cables.get(BlockPos.pack(x, y, z));
        }

        @Override
        public int mask(int x, int y, int z) {
            return masks.getOrDefault(BlockPos.pack(x, y, z), 0);
        }

        @Override
        public Buses.End end(int x, int y, int z, int side, CableTier tier) {
            return ends.get(BlockPos.pack(x, y, z));
        }
    };

    private static long chunk(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    private static long at(int x) {
        return BlockPos.pack(x, Y, 0);
    }

    /** Cable of a tier along x from {@code from} to {@code to}, each joined to the next. */
    private void run(int from, int to, CableTier tier) {
        for (int x = from; x <= to; x++) {
            cables.put(at(x), tier);
            int mask = 0;
            if (x > from) mask |= 1 << WEST;
            if (x < to) mask |= 1 << EAST;
            masks.put(at(x), mask);
            buses.changed(at(x));
        }
    }

    /** Opens a cable's side toward x - 1 or x + 1, the way joining a device there does. */
    private void open(int x, int side) {
        masks.merge(at(x), 1 << side, (a, b) -> a | b);
        buses.changed(at(x));
    }

    private void end(int x, Buses.Kind kind, int number) {
        ends.put(at(x), new Buses.End(at(x), kind, number));
        buses.changed(at(x));
    }

    /** Whether the cable at x is on a bus that carries: one within its length. */
    private boolean carries(int x) {
        Buses.Bus bus = buses.busAt(at(x));
        return bus != null && !bus.tooLong();
    }

    private boolean update() {
        return buses.update(probe, tier -> limit);
    }

    private void breakCable(int x) {
        cables.remove(at(x));
        masks.remove(at(x));
        buses.changed(at(x));
    }

    @Test
    void aRunJoinsWhatItsEndsAreOpenTo() {
        end(0, Buses.Kind.ROUTER, -1);
        run(1, 10, CableTier.PHONE);
        open(1, WEST);
        open(10, EAST);
        end(11, Buses.Kind.PHONE, 1234);
        assertTrue(update());
        Buses.Bus bus = buses.busAt(at(5));
        assertNotNull(bus);
        assertEquals(CableTier.PHONE, bus.tier());
        assertEquals(10, bus.cables());
        assertFalse(bus.tooLong());
        assertEquals(
            List.of(new Buses.End(at(0), Buses.Kind.ROUTER, -1), new Buses.End(at(11), Buses.Kind.PHONE, 1234)),
            bus.ends());
        assertEquals(List.of(bus), buses.busesOf(at(0)));
        assertEquals(at(1), bus.id());
    }

    @Test
    void aClosedSideReachesNothing() {
        end(0, Buses.Kind.ROUTER, -1);
        run(1, 3, CableTier.PHONE);
        end(4, Buses.Kind.PHONE, 1);
        open(3, EAST);
        update();
        assertEquals(
            List.of(new Buses.End(at(4), Buses.Kind.PHONE, 1)),
            buses.busAt(at(1))
                .ends());
    }

    @Test
    void tiersNeverJoin() {
        run(0, 2, CableTier.PHONE);
        run(3, 5, CableTier.COAXIAL);
        // Both open toward each other: still two buses.
        open(2, EAST);
        open(3, WEST);
        update();
        assertNotEquals(buses.busAt(at(0)), buses.busAt(at(5)));
        assertEquals(
            3,
            buses.busAt(at(0))
                .cables());
        assertTrue(
            buses.busAt(at(0))
                .ends()
                .isEmpty());
    }

    @Test
    void cableOpenOnOneSideOnlyDoesntJoin() {
        run(0, 2, CableTier.PHONE);
        run(3, 5, CableTier.PHONE);
        open(2, EAST);
        update();
        assertNotEquals(buses.busAt(at(0)), buses.busAt(at(5)));
        open(3, WEST);
        update();
        assertEquals(buses.busAt(at(0)), buses.busAt(at(5)));
        assertEquals(
            6,
            buses.busAt(at(0))
                .cables());
    }

    @Test
    void aBusLongerThanItsTierCarriesOverIsTooLong() {
        limit = 4;
        run(0, 3, CableTier.PHONE);
        update();
        assertTrue(carries(0));
        run(0, 4, CableTier.PHONE);
        update();
        assertFalse(carries(0));
        assertTrue(
            buses.busAt(at(0))
                .tooLong());
    }

    @Test
    void breakingACableSplitsTheBusAndMendingItJoinsThem() {
        run(0, 9, CableTier.PHONE);
        update();
        breakCable(5);
        assertTrue(update());
        assertNull(buses.busAt(at(5)));
        assertEquals(
            5,
            buses.busAt(at(0))
                .cables());
        assertEquals(
            4,
            buses.busAt(at(9))
                .cables());
        run(0, 9, CableTier.PHONE);
        update();
        assertEquals(
            10,
            buses.busAt(at(0))
                .cables());
        assertEquals(buses.busAt(at(0)), buses.busAt(at(9)));
    }

    @Test
    void nothingChangedIsNoChange() {
        end(0, Buses.Kind.ROUTER, -1);
        run(1, 5, CableTier.FIBRE);
        open(1, WEST);
        update();
        buses.changed(at(3));
        assertFalse(update());
        assertFalse(buses.pending());
    }

    @Test
    void aBusStopsAtAnUnloadedChunkAndJoinsUpAsItLoads() {
        // 10 to 25 along x crosses from chunk 0 into chunk 1 at x = 16.
        run(10, 25, CableTier.PHONE);
        unloadedChunks.add(chunk(1, 0));
        update();
        assertEquals(
            6,
            buses.busAt(at(10))
                .cables());
        assertNull(buses.busAt(at(20)));
        unloadedChunks.clear();
        buses.chunkChanged(1, 0);
        assertTrue(update());
        assertEquals(
            16,
            buses.busAt(at(10))
                .cables());
    }

    @Test
    void aChunkUnloadingCutsTheBusThere() {
        run(10, 25, CableTier.PHONE);
        end(26, Buses.Kind.ROUTER, -1);
        open(25, EAST);
        update();
        assertEquals(
            1,
            buses.busAt(at(10))
                .ends()
                .size());
        unloadedChunks.add(chunk(1, 0));
        buses.chunkChanged(1, 0);
        update();
        assertEquals(
            6,
            buses.busAt(at(10))
                .cables());
        assertTrue(
            buses.busAt(at(10))
                .ends()
                .isEmpty());
    }

    @Test
    void anEndTwoCablesReachIsListedOnce() {
        // A U of cable round a phone at x = 1, through z = 1: both its arms are open toward the phone.
        cables.put(at(0), CableTier.PHONE);
        cables.put(BlockPos.pack(0, Y, 1), CableTier.PHONE);
        cables.put(BlockPos.pack(1, Y, 1), CableTier.PHONE);
        cables.put(BlockPos.pack(2, Y, 1), CableTier.PHONE);
        cables.put(at(2), CableTier.PHONE);
        masks.put(at(0), (1 << SOUTH) | (1 << EAST));
        masks.put(BlockPos.pack(0, Y, 1), (1 << NORTH) | (1 << EAST));
        masks.put(BlockPos.pack(1, Y, 1), (1 << WEST) | (1 << EAST));
        masks.put(BlockPos.pack(2, Y, 1), (1 << WEST) | (1 << NORTH));
        masks.put(at(2), (1 << SOUTH) | (1 << WEST));
        ends.put(at(1), new Buses.End(at(1), Buses.Kind.PHONE, 7));
        buses.changed(at(0));
        update();
        assertEquals(
            1,
            buses.busAt(at(0))
                .ends()
                .size());
        assertEquals(
            5,
            buses.busAt(at(0))
                .cables());
    }

    @Test
    void aRunCutBackUnderItsLengthCarriesAgain() {
        // 200 cables where 128 carry: every part is too long until a cut leaves one under 128.
        run(0, 199, CableTier.PHONE);
        update();
        for (int x = 0; x < 200; x++) assertFalse(carries(x), "cable " + x);
        breakCable(150);
        update();
        for (int x = 0; x < 150; x++) assertFalse(carries(x), "cable " + x);
        assertTrue(carries(151));
        assertEquals(
            49,
            buses.busAt(at(151))
                .cables());
        breakCable(100);
        update();
        assertTrue(carries(0));
        assertEquals(
            100,
            buses.busAt(at(0))
                .cables());
        Buses.Bus middle = buses.busAt(at(101));
        assertFalse(middle.tooLong());
        assertEquals(49, middle.cables());
        assertEquals(middle, buses.busAt(at(149)));
    }

    @Test
    void aTooLongRunSettlesInOneUpdate() {
        limit = 8;
        run(0, 99, CableTier.PHONE);
        update();
        for (int x = 0; x < 100; x++) assertFalse(carries(x), "cable " + x);
        buses.changed(at(50));
        update();
        assertFalse(buses.pending());
    }
}
