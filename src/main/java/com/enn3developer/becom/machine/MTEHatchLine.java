package com.enn3developer.becom.machine;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.enn3developer.becom.block.BlockPhoneCable;
import com.enn3developer.becom.block.CableEnd;
import com.enn3developer.becom.block.TileCable;
import com.enn3developer.becom.item.Tips;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.Buses;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.server.BeComServer;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * A Line Hatch, LV to HV: where a bus of cable joins a multiblock. It takes one cable, on its front face, up to its
 * tier's; the bus it's on then reaches the multiblock it's part of, by its controller: an Exchange or an antenna as a
 * router, a Satellite Dish as a gateway. The multiblock tells it it's theirs at each structure check ({@link
 * LineHatches}); out of a formed one, it reaches nothing. Its lamp is lit while that multiblock works.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTEHatchLine extends MTEHatch implements CableEnd {

    private static final int TICKS_PER_SECOND = 20;
    /** Not part of a formed multiblock. */
    private static final long NONE = Long.MIN_VALUE;

    private long controller = NONE;

    public MTEHatchLine(int id, String name, String regionalName, CableTier tier) {
        super(id, name, regionalName, tier.voltageTier(), 0, (String) null);
    }

    public MTEHatchLine(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, 0, description, textures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEHatchLine(mName, mTier, mDescriptionArray, mTextures);
    }

    /** Its item is BeCom's, as its block is. */
    @Override
    public ItemStack getStackForm(long amount) {
        return BeComMachines.stack(this, amount);
    }

    /** Its tier, which caps the cable it takes. */
    public CableTier tier() {
        return CableTier.ofVoltage(mTier);
    }

    // region Cable

    @Override
    public CableTier bestCable() {
        return tier();
    }

    /** One cable, on its front face. */
    @Override
    public boolean takesCableOn(ForgeDirection face) {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        return tile != null && face == tile.getFrontFacing();
    }

    @Override
    public Buses.Kind busKind() {
        LineHatchHost host = host();
        return host == null ? Buses.Kind.ROUTER : host.busKind();
    }

    /** What its bus reaches through it: the multiblock it's part of, where its controller stands; or nothing. */
    @Override
    public Buses.End busEnd(long pos) {
        LineHatchHost host = host();
        return host == null ? null : new Buses.End(controller, host.busKind(), -1);
    }

    /** The formed multiblock it's part of, or null. Never loads a chunk: an unloaded controller is none. */
    public LineHatchHost host() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (controller == NONE || tile == null) return null;
        World world = tile.getWorld();
        int x = BlockPos.x(controller), y = BlockPos.y(controller), z = BlockPos.z(controller);
        if (world == null || !world.blockExists(x, y, z)) return null;
        if (!(world.getTileEntity(x, y, z) instanceof IGregTechTileEntity gt)) return null;
        return gt.getMetaTileEntity() instanceof LineHatchHost host && host.formed() ? host : null;
    }

    /** Where its multiblock's controller stands, while it's part of one; -1 otherwise. */
    public long controller() {
        return host() == null ? -1 : controller;
    }

    /** The multiblock whose controller stands at {@code controller} formed with it. */
    void join(long controller) {
        this.controller = controller;
        busChanged();
    }

    /** The multiblock whose controller stands at {@code controller} broke, or formed without it. */
    void leave(long controller) {
        if (this.controller != controller || !isValid()) return;
        this.controller = NONE;
        busChanged();
    }

    private void busChanged() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile != null) TileCable.busChanged(tile.getWorld(), tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
    }

    /** Turned to face elsewhere: the cable at its old front closes, and one at its new front may join. */
    @Override
    public void onFacingChange() {
        super.onFacingChange();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile == null || !tile.isServerSide()) return;
        World world = tile.getWorld();
        int x = tile.getXCoord(), y = tile.getYCoord(), z = tile.getZCoord();
        world.notifyBlocksOfNeighborChange(x, y, z, world.getBlock(x, y, z));
        busChanged();
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        super.onPostTick(tile, tick);
        if (!tile.isServerSide() || tick % TICKS_PER_SECOND != 0) return;
        LineHatchHost host = host();
        tile.setActive(host != null && host.isWorking());
    }

    // endregion

    // region Inventory: none

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
    }

    @Override
    public boolean isValidSlot(int index) {
        return false;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity tile, int index, ForgeDirection side, ItemStack stack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity tile, int index, ForgeDirection side, ItemStack stack) {
        return false;
    }

    // endregion

    // region Looks and info

    @Override
    public ITexture[] getTexturesActive(ITexture base) {
        return new ITexture[] { base, TextureFactory.of(BeComTextures.LINE_HATCH), TextureFactory.builder()
            .addIcon(BeComTextures.LINE_HATCH_GLOW)
            .glow()
            .build() };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture base) {
        return new ITexture[] { base, TextureFactory.of(BeComTextures.LINE_HATCH) };
    }

    @Override
    public String[] getDescription() {
        return Tips.wrap(
            Tips.MACHINE,
            StatCollector.translateToLocal("becom.machine.line_hatch.info"),
            StatCollector.translateToLocalFormatted(
                "becom.machine.line_hatch.cable",
                StatCollector.translateToLocal("tile.becom." + BlockPhoneCable.nameOf(tier()) + ".name")));
    }

    private final WailaInfo waila = new WailaInfo(this::info);

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        return info().translateArray();
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        waila.write(tag, player, world, x, y, z);
    }

    @Override
    public void getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(stack, tip, accessor, config);
        InfoLines.read(accessor.getNBTData(), tip);
    }

    /** Its lines for WAILA and the scanner: the multiblock it's part of, and the bus of the cable at its front. */
    private InfoLines info() {
        InfoLines info = new InfoLines();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (tile == null || becom == null) return info;
        long at = controller();
        if (at < 0) info.problem("becom.info.not_part")
            .status();
        else info.add("becom.info.part_of", BlockPos.toString(at))
            .status();
        ForgeDirection front = tile.getFrontFacing();
        InfoLines.cableBus(
            info,
            becom,
            tile.getWorld().provider.dimensionId,
            BlockPos.pack(
                tile.getXCoord() + front.offsetX,
                tile.getYCoord() + front.offsetY,
                tile.getZCoord() + front.offsetZ));
        return info;
    }

    // endregion
}
