package com.enn3developer.becom.block;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.machine.InfoLines;
import com.enn3developer.becom.machine.WailaInfo;
import com.enn3developer.becom.server.BeComServer;

/**
 * A cable block's open sides, as GT's cables keep them: one bit per side, its {@link ForgeDirection#flag}. The tier is
 * the block's metadata. Placing a cable opens the side it was placed against, wire cutters open and close the rest,
 * and the server opens a side only toward something that takes the cable ({@link BlockPhoneCable#joinAt}). Cable to
 * cable is always both ways. A side may stay open to air, GT6 style, and joins what is put there. The renderer, the
 * hitbox and the network all read the same mask; it never ticks.
 */
public class TileCable extends TileEntity {

    /** Every side open. */
    public static final int ALL = 0x3f;

    private int mask;
    private final WailaInfo waila = new WailaInfo(this::info);

    public int mask() {
        return mask;
    }

    public boolean open(ForgeDirection side) {
        return (mask & side.flag) != 0;
    }

    public CableTier tier() {
        return BlockPhoneCable.tierOf(getBlockMetadata());
    }

    /**
     * Opens a side toward what stands there, if it takes this cable; cable of the same tier opens back. Returns what
     * stands there: {@link BlockPhoneCable.Join#joins} when the side is open now.
     */
    public BlockPhoneCable.Join connect(ForgeDirection side) {
        BlockPhoneCable.Join join = BlockPhoneCable.joinAt(worldObj, xCoord, yCoord, zCoord, side, tier());
        if (!join.joins()) return join;
        setMask(mask | side.flag);
        if (join == BlockPhoneCable.Join.CABLE && beside(side) instanceof TileCable other)
            other.setMask(other.mask | side.getOpposite().flag);
        return join;
    }

    /** Closes a side, and the cable across it closes back. */
    public void disconnect(ForgeDirection side) {
        setMask(mask & ~side.flag);
        if (beside(side) instanceof TileCable other) other.setMask(other.mask & ~side.getOpposite().flag);
    }

    /** Opens every side toward something that takes this cable, leaving the ones facing air closed. */
    public void connectAll() {
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (BlockPhoneCable.joinAt(worldObj, xCoord, yCoord, zCoord, side, tier()) != BlockPhoneCable.Join.AIR)
                connect(side);
        }
    }

    /**
     * After a neighbour changed: closes each open side that faces something no longer taking this cable, and makes
     * cable of the tier open back toward a side left open for it. Sides facing an unloaded chunk stay as they are.
     */
    public void check() {
        if (worldObj == null || worldObj.isRemote) return;
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (!open(side)) continue;
            BlockPhoneCable.Join join = BlockPhoneCable.joinAt(worldObj, xCoord, yCoord, zCoord, side, tier());
            if (join == BlockPhoneCable.Join.CABLE) {
                if (beside(side) instanceof TileCable other) other.setMask(other.mask | side.getOpposite().flag);
            } else if (!join.joins() && join != BlockPhoneCable.Join.UNKNOWN) {
                setMask(mask & ~side.flag);
            }
        }
    }

    private TileEntity beside(ForgeDirection side) {
        return worldObj.getTileEntity(xCoord + side.offsetX, yCoord + side.offsetY, zCoord + side.offsetZ);
    }

    private void setMask(int mask) {
        mask &= ALL;
        if (mask == this.mask) return;
        this.mask = mask;
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        busChanged(worldObj, xCoord, yCoord, zCoord);
    }

    /** Tells the server's buses that something at x, y, z changed: a cable, or what a cable reaches. */
    public static void busChanged(World world, int x, int y, int z) {
        BeComServer becom = BeComServer.get();
        if (becom != null && world != null && !world.isRemote) becom.network.busChanged(world, BlockPos.pack(x, y, z));
    }

    // A cable joins its bus as it's placed or its chunk loads, and leaves it as it's broken or its chunk unloads.

    @Override
    public void validate() {
        super.validate();
        busChanged(worldObj, xCoord, yCoord, zCoord);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        busChanged(worldObj, xCoord, yCoord, zCoord);
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        busChanged(worldObj, xCoord, yCoord, zCoord);
    }

    @Override
    public boolean canUpdate() {
        return false;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setByte("mask", (byte) mask);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        mask = packet.func_148857_g()
            .getByte("mask") & ALL;
        // Redraws this cable and what's beside it: a Telephone draws its cord toward a cable open to it.
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setByte("mask", (byte) mask);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        mask = nbt.getByte("mask") & ALL;
    }

    public WailaInfo waila() {
        return waila;
    }

    /** Its lines for WAILA: its bus, whether that carries, and what's on it. */
    private InfoLines info() {
        InfoLines info = new InfoLines();
        BeComServer becom = BeComServer.get();
        if (becom != null)
            InfoLines.cable(info, becom, worldObj.provider.dimensionId, BlockPos.pack(xCoord, yCoord, zCoord));
        return info;
    }
}
