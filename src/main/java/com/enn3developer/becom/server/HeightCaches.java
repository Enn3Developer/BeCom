package com.enn3developer.becom.server;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;

import com.enn3developer.becom.logic.radio.Terrain;

/**
 * Every loaded dimension's height cache (DESIGN.md 6.2): filled from chunks as they load and save, written next to the
 * dimension's own saves when the world saves. Server thread only.
 */
public final class HeightCaches {

    private static final String DIR = "becom_heights";
    // A region nobody read for ten minutes leaves memory after the next save; it's on disk.
    private static final long IDLE_TICKS = 10 * 60 * 20;

    private final Map<Integer, HeightCache> caches = new HashMap<>();
    private long tick;

    /** The cache of a loaded world's dimension, made on first use. */
    public HeightCache of(World world) {
        return caches.computeIfAbsent(world.provider.dimensionId, d -> create(world));
    }

    /** The terrain of a dimension; sea level everywhere when it isn't loaded. */
    public Terrain terrain(int dimension) {
        HeightCache cache = caches.get(dimension);
        if (cache != null) return cache;
        WorldServer world = DimensionManager.getWorld(dimension);
        return world != null ? of(world) : (x, z) -> 64;
    }

    private static HeightCache create(World world) {
        File dir = null;
        if (world instanceof WorldServer server && server.getChunkSaveLocation() != null) {
            dir = new File(server.getChunkSaveLocation(), DIR);
        }
        // "Terrain that was never loaded is assumed to be at sea level", as a heightmap would read it.
        return new HeightCache(dir, world.provider.getAverageGroundLevel());
    }

    public void chunkChanged(World world, Chunk chunk) {
        if (world.isRemote) return;
        of(world).update(chunk);
    }

    public void worldSaved(World world) {
        if (world.isRemote) return;
        HeightCache cache = caches.get(world.provider.dimensionId);
        if (cache == null) return;
        cache.save();
        cache.evict(IDLE_TICKS);
    }

    public void worldUnloaded(World world) {
        if (world.isRemote) return;
        HeightCache cache = caches.remove(world.provider.dimensionId);
        if (cache != null) cache.save();
    }

    public void tick() {
        tick++;
        for (HeightCache cache : caches.values()) cache.setNow(tick);
    }

    /** Writes everything, as the server stops. */
    public void saveAll() {
        for (HeightCache cache : caches.values()) cache.save();
    }

    public int dimensions() {
        return caches.size();
    }
}
