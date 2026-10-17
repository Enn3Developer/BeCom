package com.enn3developer.becom.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.chunk.Chunk;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.radio.HeightRegion;
import com.enn3developer.becom.logic.radio.Terrain;

/**
 * One dimension's height cache: what the radio model knows about terrain, without ever loading a
 * chunk. Each chunk's cells are taken from its heightmap when it loads and when it saves; regions live in memory once
 * touched and go to disk when the world saves. Terrain never seen reads as sea level.
 * <p>
 * Only a cache: a region file that can't be read, or has another format, is thrown away and filled
 * again as chunks load. Server thread only.
 */
public final class HeightCache implements Terrain {

    private static final int REGION_CELLS = HeightRegion.SIDE;

    private final File dir;
    private final int seaLevel;
    private final Map<Long, HeightRegion> regions = new HashMap<>();
    // When each region was last read or written, on the caller's clock, so idle ones can leave memory.
    private final Map<Long, Long> lastUsed = new HashMap<>();
    private long now;

    /** @param dir where this dimension's region files go; null keeps the cache in memory only */
    public HeightCache(File dir, int seaLevel) {
        this.dir = dir;
        this.seaLevel = seaLevel;
    }

    private static long key(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFF_FFFFL);
    }

    private File file(int rx, int rz) {
        return new File(dir, "r." + rx + "." + rz + ".bhc");
    }

    private HeightRegion region(int rx, int rz) {
        long key = key(rx, rz);
        HeightRegion region = regions.get(key);
        if (region == null) {
            region = load(rx, rz);
            regions.put(key, region);
        }
        lastUsed.put(key, now);
        return region;
    }

    /** Sets the clock regions' use is measured on; the server's tick. */
    public void setNow(long now) {
        this.now = now;
    }

    private HeightRegion load(int rx, int rz) {
        if (dir == null) return new HeightRegion();
        File file = file(rx, rz);
        if (!file.isFile()) return new HeightRegion();
        try {
            HeightRegion region = HeightRegion.fromBytes(Files.readAllBytes(file.toPath()));
            if (region != null) return region;
            BeCom.LOG.info("Height cache region {} has another format; rebuilding it", file);
        } catch (IOException e) {
            BeCom.LOG.warn("Couldn't read height cache region {}; rebuilding it", file, e);
        }
        return new HeightRegion();
    }

    @Override
    public double heightAt(int x, int z) {
        int cellX = Math.floorDiv(x, HeightRegion.CELL_BLOCKS);
        int cellZ = Math.floorDiv(z, HeightRegion.CELL_BLOCKS);
        HeightRegion region = region(Math.floorDiv(cellX, REGION_CELLS), Math.floorDiv(cellZ, REGION_CELLS));
        int height = region.get(Math.floorMod(cellX, REGION_CELLS), Math.floorMod(cellZ, REGION_CELLS));
        return height == 0 ? seaLevel : height;
    }

    /** Takes a chunk's cells from its heightmap. */
    public void update(Chunk chunk) {
        int[] cells = HeightRegion.cellsOf(chunk.heightMap);
        // Zero means "never seen", so a seen column with nothing in it is stored as 1.
        for (int i = 0; i < cells.length; i++) cells[i] = Math.max(1, cells[i]);
        int cx = chunk.xPosition;
        int cz = chunk.zPosition;
        region(Math.floorDiv(cx, HeightRegion.CHUNKS), Math.floorDiv(cz, HeightRegion.CHUNKS))
            .setChunk(Math.floorMod(cx, HeightRegion.CHUNKS), Math.floorMod(cz, HeightRegion.CHUNKS), cells);
    }

    /** Writes every changed region. Each file is replaced whole, so a crash mid-write leaves the old one. */
    public void save() {
        if (dir == null) return;
        for (Map.Entry<Long, HeightRegion> entry : regions.entrySet()) {
            HeightRegion region = entry.getValue();
            if (!region.dirty()) continue;
            int rx = (int) (entry.getKey() >> 32);
            int rz = (int) (long) entry.getKey();
            File file = file(rx, rz);
            try {
                Files.createDirectories(dir.toPath());
                File tmp = new File(dir, file.getName() + ".tmp");
                Files.write(tmp.toPath(), region.toBytes());
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                region.clean();
            } catch (IOException e) {
                BeCom.LOG.warn("Couldn't save height cache region {}", file, e);
            }
        }
    }

    /**
     * Drops saved regions nobody has touched for {@code idle} on the clock, so a long-running server that explored far
     * doesn't keep every region it ever saw in memory. They load again from disk when needed.
     */
    public void evict(long idle) {
        if (dir == null) return;
        regions.entrySet()
            .removeIf(entry -> {
                boolean evict = !entry.getValue()
                    .dirty() && now - lastUsed.getOrDefault(entry.getKey(), now) > idle;
                if (evict) lastUsed.remove(entry.getKey());
                return evict;
            });
    }

    public int regionsLoaded() {
        return regions.size();
    }
}
