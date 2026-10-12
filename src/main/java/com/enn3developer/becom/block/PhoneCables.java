package com.enn3developer.becom.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.phone.CableWalk;
import com.enn3developer.becom.machine.MTEVideophone;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The world side of {@link CableWalk}: what a Line Terminal's cable reaches (DESIGN.md 5.2, 5.5), Telephones and
 * Videophones alike (DESIGN.md 10.2). Only loaded chunks are walked.
 */
public final class PhoneCables {

    private PhoneCables() {}

    /**
     * How much cable a terminal walks in all, past its length: room for branches, but a cap, so a blob of cable can't
     * make a scan read thousands of blocks (X15).
     */
    public static final int CABLES_PER_LENGTH = 4;

    /** The walk from the terminal at x, y, z: the phones its cable reaches, or that touch it directly. */
    public static CableWalk.Result walk(World world, int x, int y, int z, int maxLength) {
        return CableWalk.walk(x, y, z, maxLength, maxLength * CABLES_PER_LENGTH, (nx, ny, nz) -> {
            if (ny < 0 || ny >= world.getHeight()
                || !world.getChunkProvider()
                    .chunkExists(nx >> 4, nz >> 4))
                return CableWalk.Block.OTHER;
            Block block = world.getBlock(nx, ny, nz);
            if (block instanceof BlockTelephone) return CableWalk.Block.PHONE;
            if (block instanceof BlockPhoneCable) return CableWalk.Block.CABLE;
            if (isVideophone(world.getTileEntity(nx, ny, nz))) return CableWalk.Block.PHONE;
            return CableWalk.Block.OTHER;
        });
    }

    private static boolean isVideophone(TileEntity tile) {
        return tile instanceof IGregTechTileEntity gt && gt.getMetaTileEntity() instanceof MTEVideophone;
    }

    /**
     * The numbers of the phones a walk found, -1 for one without a number yet or saved by a newer BeCom: it still
     * counts against the terminal's one phone (D19).
     */
    public static List<Integer> phonesOf(World world, CableWalk.Result walk) {
        List<Integer> phones = new ArrayList<>();
        for (long pos : walk.phones()) {
            TileEntity tile = world.getTileEntity(BlockPos.x(pos), BlockPos.y(pos), BlockPos.z(pos));
            if (tile instanceof TileTelephone phone) phones.add(phone.number());
            else if (tile instanceof IGregTechTileEntity gt && gt.getMetaTileEntity() instanceof MTEVideophone phone)
                phones.add(phone.number());
        }
        return phones;
    }
}
