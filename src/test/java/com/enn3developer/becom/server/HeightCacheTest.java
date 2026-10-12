package com.enn3developer.becom.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Files;

import net.minecraft.world.chunk.Chunk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** DESIGN.md 6.2 and 14.2: cells from heightmaps, saved per region, rebuilt when unreadable, never unbounded. */
class HeightCacheTest {

    private static Chunk chunk(int cx, int cz, int height) {
        Chunk chunk = new Chunk(null, cx, cz);
        java.util.Arrays.fill(chunk.heightMap, height);
        return chunk;
    }

    @Test
    void neverSeenTerrainIsSeaLevelAndASeenChunkReadsItsHeights(@TempDir File dir) {
        HeightCache cache = new HeightCache(dir, 64);
        assertEquals(64, cache.heightAt(100, 100), 1e-9);
        Chunk chunk = chunk(6, 6, 80);
        chunk.heightMap[5 * 16 + 5] = 120;
        cache.update(chunk);
        // Block (101, 101) is in chunk 6, cell (1, 1), which holds the 120 at local (5, 5).
        assertEquals(120, cache.heightAt(101, 101), 1e-9);
        assertEquals(80, cache.heightAt(96, 96), 1e-9);
    }

    @Test
    void regionsSurviveASaveAndAnotherFormatIsThrownAway(@TempDir File dir) throws Exception {
        HeightCache cache = new HeightCache(dir, 64);
        cache.update(chunk(-1, -1, 90));
        cache.save();
        assertEquals(90, new HeightCache(dir, 64).heightAt(-5, -5), 1e-9);
        // A file from another format, or a damaged one, is only a cache: rebuilt from scratch.
        for (File f : dir.listFiles()) Files.write(f.toPath(), new byte[] { 1, 2, 3 });
        assertEquals(64, new HeightCache(dir, 64).heightAt(-5, -5), 1e-9);
    }

    @Test
    void idleSavedRegionsLeaveMemory(@TempDir File dir) {
        HeightCache cache = new HeightCache(dir, 64);
        cache.setNow(0);
        cache.update(chunk(0, 0, 70));
        cache.update(chunk(100, 100, 70));
        assertEquals(2, cache.regionsLoaded());
        // Unsaved regions stay whatever happens.
        cache.setNow(1_000_000);
        cache.evict(100);
        assertEquals(2, cache.regionsLoaded());
        cache.save();
        cache.heightAt(0, 0);
        cache.evict(100);
        assertEquals(1, cache.regionsLoaded());
        // Read back from disk when needed again.
        assertEquals(70, cache.heightAt(1600, 1600), 1e-9);
    }
}
