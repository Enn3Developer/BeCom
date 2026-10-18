package com.enn3developer.becom.logic.net;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * One dimension's cable buses. A bus is a connected run of cable blocks of one tier, joined through sides open both
 * ways, with what its other open sides reach: phones, routers and gateways. It carries its tier's rate, shared by every
 * call crossing it, over up to its tier's length in cable blocks, and nothing past that. Tiers never join: a router
 * between two buses joins them, and its own count starts again on each.
 * <p>
 * Buses are worked out again only where something changed: a cable placed, broken or cut, a block beside cable
 * placed or removed ({@link #changed}), a chunk loaded or unloaded ({@link #chunkChanged}). A bus stops at unloaded
 * chunks. Minecraft-free: the world is a {@link Probe}.
 */
public final class Buses {

    /** What a bus reaches through an open side. */
    public enum Kind {
        /** A Telephone, a Videophone or a wired computer. */
        PHONE,
        /** A Router or an Exchange: what makes a bus carry. */
        ROUTER,
        /** A Satellite Dish, a Portal Relay or an ME Bridge. */
        GATEWAY
    }

    /** One thing a bus reaches: where it stands, what it is, and a phone's number (-1 for none, and for the rest). */
    public record End(long pos, Kind kind, int number) {}

    /**
     * One bus: its id (the least packed position among its cables), its tier, its cable blocks, whether they're more
     * than its tier carries over (counting stops one past that), and what it reaches, each once, by position.
     */
    public record Bus(long id, CableTier tier, int cables, boolean tooLong, List<End> ends) {

        /** What it reaches of one kind. */
        public List<End> ends(Kind kind) {
            List<End> of = new ArrayList<>();
            for (End end : ends) if (end.kind() == kind) of.add(end);
            return of;
        }
    }

    /** The world as buses see it; no call may load a chunk. */
    public interface Probe {

        /** Whether a position's chunk is loaded. */
        boolean loaded(int x, int y, int z);

        /** The tier of the cable at a loaded position, or null when there's no cable. */
        CableTier cable(int x, int y, int z);

        /** The open sides of the cable at a loaded position, a bit per side by ForgeDirection ordinal. */
        int mask(int x, int y, int z);

        /**
         * What a cable of {@code tier} reaches at a loaded position that holds no cable, through its {@code side} (a
         * ForgeDirection ordinal: the end's own face is the opposite one), or null when nothing there takes it.
         */
        End end(int x, int y, int z, int side, CableTier tier);
    }

    private static final int[] DX = { 0, 0, 0, 0, -1, 1 };
    private static final int[] DY = { -1, 1, 0, 0, 0, 0 };
    private static final int[] DZ = { 0, 0, -1, 1, 0, 0 };

    /** A bus as kept: the bus, its cables, and the chunks it touches. */
    private record Kept(Bus bus, long[] cables, long[] chunks) {}

    private final Map<Long, Kept> buses = new HashMap<>();
    private final Map<Long, Long> byCable = new HashMap<>();
    private final Map<Long, Set<Long>> byChunk = new HashMap<>();
    private final Map<Long, List<Long>> byEnd = new HashMap<>();
    private final Set<Long> changed = new HashSet<>();
    private final Set<Long> changedChunks = new HashSet<>();

    /** Something at a position changed: a cable, or a block a cable may reach. */
    public void changed(long pos) {
        changed.add(pos);
    }

    /** A chunk loaded or unloaded: every bus touching it, or reaching into it, is worked out again. */
    public void chunkChanged(int chunkX, int chunkZ) {
        changedChunks.add(chunkKey(chunkX, chunkZ));
    }

    /** Whether anything changed since the last {@link #update}. */
    public boolean pending() {
        return !changed.isEmpty() || !changedChunks.isEmpty();
    }

    /** The bus a cable block is on, or null. */
    public Bus busAt(long pos) {
        Long id = byCable.get(pos);
        return id == null ? null
            : buses.get(id)
                .bus();
    }

    /** The buses reaching the block at a position: a router's, a phone's or a gateway's. */
    public List<Bus> busesOf(long pos) {
        List<Long> ids = byEnd.get(pos);
        if (ids == null) return List.of();
        List<Bus> of = new ArrayList<>(ids.size());
        for (long id : ids) of.add(
            buses.get(id)
                .bus());
        return of;
    }

    /** Every bus. */
    public Collection<Bus> all() {
        List<Bus> all = new ArrayList<>(buses.size());
        for (Kept kept : buses.values()) all.add(kept.bus());
        return all;
    }

    /**
     * Works out again every bus near something that changed, with {@code limit} cable blocks a tier's bus carries
     * over. Returns whether any bus came out different.
     */
    public boolean update(Probe probe, ToIntFunction<CableTier> limit) {
        if (!pending()) return false;
        ArrayDeque<Long> seeds = new ArrayDeque<>();
        Map<Long, Bus> gone = new HashMap<>();
        Map<Long, Bus> made = new HashMap<>();
        for (long chunk : changedChunks) {
            Set<Long> touching = byChunk.get(chunk);
            if (touching != null) for (long id : new ArrayList<>(touching)) dissolve(id, seeds, gone, made);
        }
        for (long pos : changed) {
            seeds.add(pos);
            int x = BlockPos.x(pos), y = BlockPos.y(pos), z = BlockPos.z(pos);
            for (int side = 0; side < 6; side++) seeds.add(BlockPos.pack(x + DX[side], y + DY[side], z + DZ[side]));
        }
        changed.clear();
        changedChunks.clear();
        for (long seed : new ArrayList<>(seeds)) {
            Long id = byCable.get(seed);
            if (id != null) dissolve(id, seeds, gone, made);
        }
        while (!seeds.isEmpty()) {
            long seed = seeds.poll();
            if (byCable.containsKey(seed)) continue;
            Kept kept = flood(seed, probe, limit, seeds, gone, made);
            if (kept != null) keep(kept, made);
        }
        return !new HashSet<>(gone.values()).equals(new HashSet<>(made.values()));
    }

    /** The bus from one cable, through every cable it joins, or null when there's no loaded cable there. */
    private Kept flood(long seed, Probe probe, ToIntFunction<CableTier> limit, ArrayDeque<Long> seeds,
        Map<Long, Bus> gone, Map<Long, Bus> made) {
        int sx = BlockPos.x(seed), sy = BlockPos.y(seed), sz = BlockPos.z(seed);
        if (!probe.loaded(sx, sy, sz)) return null;
        CableTier tier = probe.cable(sx, sy, sz);
        if (tier == null) return null;
        int max = limit.applyAsInt(tier);
        List<Long> cables = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Set<Long> chunks = new HashSet<>();
        Map<Long, End> ends = new LinkedHashMap<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        seen.add(seed);
        queue.add(seed);
        boolean tooLong = false;
        while (!queue.isEmpty()) {
            long pos = queue.poll();
            if (cables.size() == max) {
                // One past what it carries over: it carries nothing, so counting the rest is wasted work.
                tooLong = true;
                cables.add(pos);
                break;
            }
            cables.add(pos);
            int x = BlockPos.x(pos), y = BlockPos.y(pos), z = BlockPos.z(pos);
            chunks.add(chunkKey(x >> 4, z >> 4));
            int mask = probe.mask(x, y, z);
            for (int side = 0; side < 6; side++) {
                if ((mask & (1 << side)) == 0) continue;
                int nx = x + DX[side], ny = y + DY[side], nz = z + DZ[side];
                if (!probe.loaded(nx, ny, nz)) {
                    // Where it runs on into an unloaded chunk: that chunk loading works it out again.
                    chunks.add(chunkKey(nx >> 4, nz >> 4));
                    continue;
                }
                CableTier other = probe.cable(nx, ny, nz);
                if (other != null) {
                    long next = BlockPos.pack(nx, ny, nz);
                    if (other != tier || (probe.mask(nx, ny, nz) & (1 << (side ^ 1))) == 0 || !seen.add(next)) continue;
                    Long owner = byCable.get(next);
                    if (owner != null && made.containsKey(owner)) {
                        // Cable of a bus this update counted past its length: the same run, so too long as well.
                        if (made.get(owner)
                            .tooLong()) tooLong = true;
                        continue;
                    }
                    // Cable an older bus still holds: it's this one's now, and the rest of that one is looked at again.
                    if (owner != null) dissolve(owner, seeds, gone, made);
                    queue.add(next);
                    continue;
                }
                End end = probe.end(nx, ny, nz, side, tier);
                if (end != null) {
                    ends.putIfAbsent(end.pos(), end);
                    chunks.add(chunkKey(nx >> 4, nz >> 4));
                }
            }
        }
        long id = Long.MAX_VALUE;
        for (long cable : cables) id = Math.min(id, cable);
        List<End> sorted = new ArrayList<>(ends.values());
        sorted.sort(Comparator.comparingLong(End::pos));
        long[] cableArray = new long[cables.size()];
        for (int i = 0; i < cableArray.length; i++) cableArray[i] = cables.get(i);
        long[] chunkArray = new long[chunks.size()];
        int i = 0;
        for (long chunk : chunks) chunkArray[i++] = chunk;
        return new Kept(new Bus(id, tier, cables.size(), tooLong, List.copyOf(sorted)), cableArray, chunkArray);
    }

    private void keep(Kept kept, Map<Long, Bus> made) {
        long id = kept.bus()
            .id();
        buses.put(id, kept);
        made.put(id, kept.bus());
        for (long cable : kept.cables()) byCable.put(cable, id);
        for (long chunk : kept.chunks()) byChunk.computeIfAbsent(chunk, k -> new HashSet<>())
            .add(id);
        for (End end : kept.bus()
            .ends())
            byEnd.computeIfAbsent(end.pos(), k -> new ArrayList<>())
                .add(id);
    }

    /** Forgets a bus, and looks at its cables again. */
    private void dissolve(long id, ArrayDeque<Long> seeds, Map<Long, Bus> gone, Map<Long, Bus> made) {
        Kept kept = buses.remove(id);
        if (kept == null) return;
        gone.putIfAbsent(id, kept.bus());
        made.remove(id);
        for (long cable : kept.cables()) {
            byCable.remove(cable);
            seeds.add(cable);
        }
        for (long chunk : kept.chunks()) {
            Set<Long> touching = byChunk.get(chunk);
            if (touching == null) continue;
            touching.remove(id);
            if (touching.isEmpty()) byChunk.remove(chunk);
        }
        for (End end : kept.bus()
            .ends()) {
            List<Long> ids = byEnd.get(end.pos());
            if (ids == null) continue;
            ids.remove(Long.valueOf(id));
            if (ids.isEmpty()) byEnd.remove(end.pos());
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }
}
