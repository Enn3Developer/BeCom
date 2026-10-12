package com.enn3developer.becom.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.enn3developer.becom.logic.net.BlockPos;

/**
 * Follows Phone Cable out from a Line Terminal to the phones on it (DESIGN.md 5.2, 5.5): a breadth-first walk over
 * cable blocks, at most {@code maxLength} cables from the terminal. Only loaded chunks are walked.
 */
public final class PhoneCables {

    private PhoneCables() {}

    /** Every Telephone the terminal at x, y, z reaches through cable, or that touches it directly. */
    public static List<TileTelephone> phonesOf(World world, int x, int y, int z, int maxLength) {
        List<TileTelephone> phones = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<long[]> queue = new ArrayDeque<>();
        seen.add(BlockPos.pack(x, y, z));
        // Each entry: packed position and how many cables lead to it.
        queue.add(new long[] { BlockPos.pack(x, y, z), 0 });
        while (!queue.isEmpty()) {
            long[] entry = queue.poll();
            long pos = entry[0];
            int length = (int) entry[1];
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                int nx = BlockPos.x(pos) + side.offsetX;
                int ny = BlockPos.y(pos) + side.offsetY;
                int nz = BlockPos.z(pos) + side.offsetZ;
                if (ny < 0 || ny >= world.getHeight()) continue;
                long next = BlockPos.pack(nx, ny, nz);
                if (!seen.add(next)) continue;
                if (!world.getChunkProvider()
                    .chunkExists(nx >> 4, nz >> 4)) continue;
                if (world.getBlock(nx, ny, nz) instanceof BlockTelephone) {
                    TileEntity tile = world.getTileEntity(nx, ny, nz);
                    if (tile instanceof TileTelephone phone) phones.add(phone);
                } else if (world.getBlock(nx, ny, nz) instanceof BlockPhoneCable && length < maxLength) {
                    queue.add(new long[] { next, length + 1 });
                }
            }
        }
        return phones;
    }
}
