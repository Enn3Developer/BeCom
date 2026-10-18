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
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.NetworkService;
import com.enn3developer.becom.server.NodeHost;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The world side of {@link CableWalk}: what a Line Terminal's or an Exchange's cable reaches, over cable of any tier.
 * Both find Telephones, Videophones and computers with a wired BeCom Card; an Exchange also finds the gateways cabled
 * to it. Only loaded chunks are walked.
 */
public final class PhoneCables {

    private PhoneCables() {}

    /**
     * The walk from the terminal or Exchange at x, y, z: the devices its cable reaches within {@code maxLength}
     * blocks, or that touch it directly, walking at most {@code maxCables} blocks of cable in all. Gateways count
     * only when {@code gateways}.
     */
    public static CableWalk.Result walk(World world, int x, int y, int z, int maxLength, int maxCables,
        boolean gateways) {
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
                TileEntity tile = world.getTileEntity(nx, ny, nz);
                if (isVideophone(tile)) return CableWalk.Block.PHONE;
                if (gateways && isGateway(tile)) return CableWalk.Block.PHONE;
                if (wiredComputer(world, BlockPos.pack(nx, ny, nz)) >= 0) return CableWalk.Block.PHONE;
                return CableWalk.Block.OTHER;
            }

            @Override
            public CableTier tier(int nx, int ny, int nz) {
                return BlockPhoneCable.tierOf(world.getBlockMetadata(nx, ny, nz));
            }
        });
    }

    /** The number of a computer with a wired BeCom Card at a spot, or -1. */
    private static int wiredComputer(World world, long pos) {
        BeComServer becom = BeComServer.get();
        return becom == null ? -1 : becom.apps.wiredComputerAt(world.provider.dimensionId, pos);
    }

    private static boolean isVideophone(TileEntity tile) {
        return tile instanceof IGregTechTileEntity gt && gt.getMetaTileEntity() instanceof MTEVideophone;
    }

    /** A Satellite Dish's controller, a Portal Relay or an ME Bridge. */
    private static boolean isGateway(TileEntity tile) {
        return tile instanceof IGregTechTileEntity gt && gt.getMetaTileEntity() instanceof NodeHost host
            && host.nodeKind()
                .cabled();
    }

    /**
     * The devices a walk found, nearest first, by number: -1 for a gateway, or for a device without a number yet or
     * saved by a newer BeCom, which still takes a port.
     */
    public static List<NetworkService.Device> devicesOf(World world, CableWalk.Result walk) {
        List<NetworkService.Device> devices = new ArrayList<>();
        for (CableWalk.Found found : walk.devices()) {
            long pos = found.pos();
            TileEntity tile = world.getTileEntity(BlockPos.x(pos), BlockPos.y(pos), BlockPos.z(pos));
            int number = -1;
            boolean gateway = false;
            if (tile instanceof TileTelephone phone) number = phone.number();
            else if (tile instanceof IGregTechTileEntity gt && gt.getMetaTileEntity() instanceof MTEVideophone phone)
                number = phone.number();
            else if (isGateway(tile)) gateway = true;
            else number = wiredComputer(world, pos);
            devices.add(new NetworkService.Device(number, pos, gateway, found.length(), found.cable()));
        }
        return devices;
    }
}
