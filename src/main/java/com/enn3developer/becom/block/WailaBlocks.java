package com.enn3developer.becom.block;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.enn3developer.becom.machine.InfoLines;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/**
 * WAILA's box for the Telephone and cable, which aren't GT machines, so GT's own WAILA support doesn't reach them.
 * WAILA is optional: BeCom names this class to WAILA through IMC, and nothing else loads it.
 */
public final class WailaBlocks implements IWailaDataProvider {

    public static void register(IWailaRegistrar registrar) {
        WailaBlocks provider = new WailaBlocks();
        for (Class<?> block : new Class<?>[] { BlockTelephone.class, BlockPhoneCable.class }) {
            registrar.registerBodyProvider(provider, block);
            registrar.registerNBTProvider(provider, block);
        }
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public List<String> getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        InfoLines.read(accessor.getNBTData(), tip);
        return tip;
    }

    @Override
    public List<String> getWailaTail(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x,
        int y, int z) {
        if (tile instanceof TileTelephone phone) phone.waila()
            .write(tag, player, world, x, y, z);
        else if (tile instanceof TileCable cable) cable.waila()
            .write(tag, player, world, x, y, z);
        return tag;
    }
}
