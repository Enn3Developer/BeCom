package com.enn3developer.becom.logic.radio;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * One region of the height cache: 32 x 32 chunks of 4 x 4 cells each, one byte per cell holding the
 * highest point of the chunk's heightmap in that cell. A zero byte means "never seen", read as sea level.
 * <p>
 * On disk a region is a short header (magic and format version) and the 16 KB of cells. The cache is only a cache:
 * a file with any other header is thrown away and rebuilt as chunks load.
 */
public final class HeightRegion {

    public static final int CHUNKS = 32;
    public static final int CELLS_PER_CHUNK = 4;
    public static final int CELL_BLOCKS = 16 / CELLS_PER_CHUNK;
    public static final int SIDE = CHUNKS * CELLS_PER_CHUNK;
    public static final int BYTES = SIDE * SIDE;
    public static final int FORMAT = 1;
    private static final int MAGIC = 0x4243_4843; // "BCHC"
    public static final int FILE_BYTES = 8 + BYTES;

    private final byte[] cells = new byte[BYTES];
    private boolean dirty;

    /** The cell at region-local cell coordinates; 0 when never seen. */
    public int get(int cx, int cz) {
        return cells[cz * SIDE + cx] & 0xFF;
    }

    public void set(int cx, int cz, int height) {
        byte value = (byte) Math.max(0, Math.min(255, height));
        int i = cz * SIDE + cx;
        if (cells[i] != value) {
            cells[i] = value;
            dirty = true;
        }
    }

    /** Stores a chunk's 16 cells, from {@link #cellsOf}. {@code chunkX}, {@code chunkZ} are region-local. */
    public void setChunk(int chunkX, int chunkZ, int[] chunkCells) {
        for (int z = 0; z < CELLS_PER_CHUNK; z++) {
            for (int x = 0; x < CELLS_PER_CHUNK; x++) {
                set(chunkX * CELLS_PER_CHUNK + x, chunkZ * CELLS_PER_CHUNK + z, chunkCells[z * CELLS_PER_CHUNK + x]);
            }
        }
    }

    public boolean dirty() {
        return dirty;
    }

    public void clean() {
        dirty = false;
    }

    /**
     * A chunk's 16 cells from its 256-entry heightmap (index {@code z * 16 + x}, as Minecraft stores it): the highest
     * value in each 4 x 4 block cell, so trees and buildings count.
     */
    public static int[] cellsOf(int[] heightMap) {
        int[] out = new int[CELLS_PER_CHUNK * CELLS_PER_CHUNK];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int cell = (z / CELL_BLOCKS) * CELLS_PER_CHUNK + x / CELL_BLOCKS;
                out[cell] = Math.max(out[cell], heightMap[z * 16 + x]);
            }
        }
        return out;
    }

    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(FILE_BYTES);
        buf.putInt(MAGIC);
        buf.putInt(FORMAT);
        buf.put(cells);
        return buf.array();
    }

    /** The region in {@code data}, or null when it isn't a region of this format (then it's rebuilt). */
    public static HeightRegion fromBytes(byte[] data) {
        if (data == null || data.length != FILE_BYTES) return null;
        ByteBuffer buf = ByteBuffer.wrap(data);
        if (buf.getInt() != MAGIC || buf.getInt() != FORMAT) return null;
        HeightRegion region = new HeightRegion();
        buf.get(region.cells);
        return region;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HeightRegion r && Arrays.equals(cells, r.cells);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(cells);
    }
}
