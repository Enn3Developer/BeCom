package com.enn3developer.becom.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.logic.phone.CableWalk;
import com.enn3developer.becom.machine.MTEVideophone;
import com.enn3developer.becom.server.NetworkService;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The world side of {@link CableWalk}: what a Line Terminal's cable reaches (DESIGN.md 5.2, 5.5), Telephones and
 * Videophones alike (DESIGN.md 10.2), over cable of any tier. Only loaded chunks are walked.
 */
public final class PhoneCables {

    private PhoneCables() {}

    /**
     * The walk from the terminal at x, y, z: the devices its cable reaches within {@code maxLength} blocks, or that
     * touch it directly, walking at most {@code maxCables} blocks of cable in all (X15).
     */
    public static CableWalk.Result walk(World world, int x, int y, int z, int maxLength, int maxCables) {
        return CableWalk.walk(x, y, z, maxLength, maxCables, new CableWalk.Probe() {

            @Override
            public CableWalk.Block at(int nx, int ny, int nz) {
                if (ny < 0 || ny >= world.getHeight()
                    || !world.getChunkProvider()
                        .chunkExists(nx >> 4, nz >> 4))
                    return CableWalk.Block.OTHER;
                Block block = world.getBlock(nx, ny, nz);
                if (block instanceof BlockTelephone) return CableWalk.Block.PHONE;
                if (block instanceof BlockPhoneCable) return CableWalk.Block.CABLE;
                if (isVideophone(world.getTileEntity(nx, ny, nz))) return CableWalk.Block.PHONE;
                return CableWalk.Block.OTHER;
            }

            @Override
            public CableTier tier(int nx, int ny, int nz) {
                return BlockPhoneCable.tierOf(world.getBlockMetadata(nx, ny, nz));
            }
        });
    }

    private static boolean isVideophone(TileEntity tile) {
        return tile instanceof IGregTechTileEntity gt && gt.getMetaTileEntity() instanceof MTEVideophone;
    }

    /**
     * The devices a walk found, nearest first, by number: -1 for one without a number yet or saved by a newer BeCom,
     * which still takes a port.
     */
    public static List<NetworkService.Device> devicesOf(World world, CableWalk.Result walk) {
        List<NetworkService.Device> devices = new ArrayList<>();
        for (CableWalk.Found found : walk.devices()) {
            long pos = found.pos();
            TileEntity tile = world.getTileEntity(BlockPos.x(pos), BlockPos.y(pos), BlockPos.z(pos));
            int number = -1;
            if (tile instanceof TileTelephone phone) number = phone.number();
            else if (tile instanceof IGregTechTileEntity gt && gt.getMetaTileEntity() instanceof MTEVideophone phone)
                number = phone.number();
            devices.add(new NetworkService.Device(number, found.length(), found.cable()));
        }
        return devices;
    }
}
