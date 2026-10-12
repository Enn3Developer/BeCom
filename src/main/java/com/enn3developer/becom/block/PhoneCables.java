package com.enn3developer.becom.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.phone.CableWalk;

/**
 * The world side of {@link CableWalk}: what a Line Terminal's cable reaches (DESIGN.md 5.2, 5.5). Only loaded chunks
 * are walked.
 */
public final class PhoneCables {

    private PhoneCables() {}

    /** The walk from the terminal at x, y, z: the Telephones its cable reaches, or that touch it directly. */
    public static CableWalk.Result walk(World world, int x, int y, int z, int maxCables) {
        return CableWalk.walk(x, y, z, maxCables, (nx, ny, nz) -> {
            if (ny < 0 || ny >= world.getHeight()
                || !world.getChunkProvider()
                    .chunkExists(nx >> 4, nz >> 4))
                return CableWalk.Block.OTHER;
            Block block = world.getBlock(nx, ny, nz);
            if (block instanceof BlockTelephone) return CableWalk.Block.PHONE;
            if (block instanceof BlockPhoneCable) return CableWalk.Block.CABLE;
            return CableWalk.Block.OTHER;
        });
    }

    /** The Telephones a walk found, as tile entities. */
    public static List<TileTelephone> phonesOf(World world, CableWalk.Result walk) {
        List<TileTelephone> phones = new ArrayList<>();
        for (long pos : walk.phones()) {
            TileEntity tile = world.getTileEntity(BlockPos.x(pos), BlockPos.y(pos), BlockPos.z(pos));
            if (tile instanceof TileTelephone phone) phones.add(phone);
        }
        return phones;
    }
}
