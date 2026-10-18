package com.enn3developer.becom.machine;

import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.item.Tips;
import com.enn3developer.becom.logic.net.CableTier;

import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.IGTHatchAdder;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * A Switching Rack, LV to HV: a hatch on an Exchange's side walls that switches its tier's calls at once. Its lamp is
 * lit while calls use it, as its Exchange fills its racks in order; an Exchange that stops telling it goes dark.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTEHatchRack extends MTEHatch {

    /** The Switching Rack, for a structure's hatch adder. */
    public enum Element implements IHatchElement<MTEExchange> {

        RACK;

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return Collections.singletonList(MTEHatchRack.class);
        }

        @Override
        public IGTHatchAdder<? super MTEExchange> adder() {
            return MTEExchange::addRack;
        }

        @Override
        public long count(MTEExchange exchange) {
            return exchange.rackCount();
        }

        @Override
        public String getDisplayName() {
            return StatCollector.translateToLocal(getDescriptionLangKey());
        }

        @Override
        public String getDescriptionLangKey() {
            return "becom.hatch.rack";
        }
    }

    private static final int TICKS_PER_SECOND = 20;
    /** How long its lamp stays as its Exchange last set it, with no word since. */
    private static final int LIT_TICKS = 3 * TICKS_PER_SECOND;

    private long toldAt = Long.MIN_VALUE;

    public MTEHatchRack(int id, String name, String regionalName, CableTier tier) {
        super(id, name, regionalName, tier.voltageTier(), 0, (String) null);
    }

    public MTEHatchRack(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, 0, description, textures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEHatchRack(mName, mTier, mDescriptionArray, mTextures);
    }

    /** Its item is BeCom's, as its block is. */
    @Override
    public ItemStack getStackForm(long amount) {
        return BeComMachines.stack(this, amount);
    }

    /** Its tier, which an Exchange's casings cap. */
    public CableTier tier() {
        return CableTier.ofVoltage(mTier);
    }

    /** The calls it switches at once. */
    public int calls() {
        return BeComConfig.get()
            .rackCalls(tier());
    }

    /** Whether its lamp is lit: calls use it now. */
    public boolean lit() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        return tile != null && tile.isActive();
    }

    /** Its Exchange, each second: whether calls use it now. */
    void light(boolean lit) {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile == null) return;
        toldAt = tile.getTimer();
        tile.setActive(lit);
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        super.onPostTick(tile, tick);
        if (tile.isServerSide() && tick % TICKS_PER_SECOND == 0 && tile.getTimer() - toldAt > LIT_TICKS)
            tile.setActive(false);
    }

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
        return new ITexture[] { base, TextureFactory.of(BeComTextures.rack(mTier)), TextureFactory.builder()
            .addIcon(BeComTextures.rackGlow(mTier))
            .glow()
            .build() };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture base) {
        return new ITexture[] { base, TextureFactory.of(BeComTextures.rack(mTier)) };
    }

    @Override
    public String[] getDescription() {
        return Tips.wrap(
            Tips.MACHINE,
            StatCollector.translateToLocal("becom.machine.rack.info"),
            StatCollector.translateToLocalFormatted("becom.machine.rack.calls", calls()));
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

    /** Its lines for WAILA and the scanner: whether calls use it, and how many it switches. */
    private InfoLines info() {
        InfoLines info = new InfoLines();
        if (lit()) info.add("becom.rack.in_use")
            .status();
        else info.add("becom.rack.idle")
            .status();
        info.add("becom.rack.calls", calls())
            .row("becom.info.row.calls");
        return info;
    }

    // endregion
}
