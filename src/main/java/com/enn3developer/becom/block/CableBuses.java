package com.enn3developer.becom.block;

import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.enn3developer.becom.logic.net.Buses;
import com.enn3developer.becom.logic.net.CableTier;

/** The world side of {@link Buses}: cable, its open sides and what they reach, never loading a chunk. */
public final class CableBuses implements Buses.Probe {

    private final World world;

    public CableBuses(World world) {
        this.world = world;
    }

    @Override
    public boolean loaded(int x, int y, int z) {
        return world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4);
    }

    @Override
    public CableTier cable(int x, int y, int z) {
        if (y < 0 || y >= world.getHeight() || !(world.getBlock(x, y, z) instanceof BlockPhoneCable)) return null;
        return BlockPhoneCable.tierOf(world.getBlockMetadata(x, y, z));
    }

    @Override
    public int mask(int x, int y, int z) {
        // A side's bit is its ForgeDirection flag, 1 << its ordinal, as the buses count sides.
        return BlockPhoneCable.maskAt(world, x, y, z);
    }

    @Override
    public Buses.End end(int x, int y, int z, int side, CableTier tier) {
        return BlockPhoneCable.endAt(
            world,
            x,
            y,
            z,
            ForgeDirection.getOrientation(side)
                .getOpposite(),
            tier);
    }
}
