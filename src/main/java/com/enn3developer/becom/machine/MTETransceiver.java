package com.enn3developer.becom.machine;

import static gregtech.api.enums.GTValues.V;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.gui.TransceiverScreen;
import com.enn3developer.becom.item.Tips;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.NodeHost;
import com.enn3developer.becom.server.RadioService;
import com.enn3developer.becom.voice.RadioGroup;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.render.TextureFactory;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * The Transceiver: a radio on a block. Anyone within its talk range transmits through it on
 * its channel when they hold push-to-talk, and its loudspeaker plays the channel to everyone around. MV, 32 EU/t.
 * Like any radio, anyone may tune it: radio channels are public.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTETransceiver extends MTETieredMachineBlock implements NodeHost {

    private static final int TIER = 2;

    private int channel = 1;
    private boolean loudspeaker = true;
    private boolean working;
    private boolean registered;

    public MTETransceiver(int id, String name, String regionalName) {
        super(id, name, regionalName, TIER, 0, (String) null);
    }

    public MTETransceiver(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, 0, description, textures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTETransceiver(mName, mTier, mDescriptionArray, mTextures);
    }

    /** Its item is BeCom's, as its block is. */
    @Override
    public ItemStack getStackForm(long amount) {
        return BeComMachines.stack(this, amount);
    }

    @Override
    public NodeKind nodeKind() {
        return NodeKind.TRANSCEIVER;
    }

    public int channel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = Math.floorMod(channel - 1, RadioGroup.CHANNELS) + 1;
        markDirty();
    }

    public boolean loudspeaker() {
        return loudspeaker;
    }

    public void setLoudspeaker(boolean on) {
        loudspeaker = on;
        markDirty();
    }

    public boolean isWorking() {
        return working;
    }

    public NodeRef ref() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        return new NodeRef(
            tile.getWorld().provider.dimensionId,
            BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        if (!tile.isServerSide()) return;
        BeComServer becom = BeComServer.get();
        if (becom == null) return;
        World world = tile.getWorld();
        long pos = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
        if (!registered) {
            becom.network.ensureNode(world, pos, NodeKind.TRANSCEIVER, tile.getOwnerUuid());
            registered = true;
        }
        long power = BeComConfig.get()
            .transceiverPower();
        working = tile.isAllowedToWork() && tile.getStoredEU() >= power && tile.decreaseStoredEnergyUnits(power, false);
        if (working) {
            becom.network.heartbeat(world, pos);
            becom.radio.transceiverTick(
                ref(),
                channel,
                loudspeaker,
                RadioService.endOf(world, tile.getXCoord(), tile.getYCoord(), tile.getZCoord()),
                tile.getOwnerUuid());
        }
        tile.setActive(working);
    }

    @Override
    public void onBlockDestroyed() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (becom != null && tile != null && tile.isServerSide()) {
            becom.network
                .removeNode(tile.getWorld(), BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
            becom.radio.transceiverGone(ref());
        }
        super.onBlockDestroyed();
    }

    // region Screen

    @Override
    public boolean onRightclick(IGregTechTileEntity tile, EntityPlayer player) {
        if (tile.isServerSide()) openGui(player);
        return true;
    }

    @Override
    protected boolean forceUseMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return TransceiverScreen.build(this, data, sync);
    }

    // endregion

    // region Power

    @Override
    public boolean isElectric() {
        return true;
    }

    @Override
    public boolean isEnetInput() {
        return true;
    }

    @Override
    public boolean isInputFacing(ForgeDirection side) {
        return side != getBaseMetaTileEntity().getFrontFacing();
    }

    @Override
    public long maxEUInput() {
        return V[TIER];
    }

    @Override
    public long maxAmperesIn() {
        return 1;
    }

    @Override
    public long maxEUStore() {
        // Like GT's own hulls: room for a couple of seconds of input.
        return 512 + V[TIER] * 50L;
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return facing.offsetY == 0;
    }

    @Override
    public boolean isAccessAllowed(EntityPlayer player) {
        return true;
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
    public ITexture[][][] getTextureSet(ITexture[] textures) {
        // Textures are built per call in getTexture; nothing to precompute.
        return new ITexture[0][0][0];
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int colorIndex,
        boolean active, boolean redstoneLevel) {
        ITexture casing = Textures.BlockIcons.MACHINE_CASINGS[TIER][colorIndex + 1];
        if (side != facing) return new ITexture[] { casing };
        ITexture overlay = TextureFactory.builder()
            .addIcon(BeComTextures.TRANSCEIVER)
            .extFacing()
            .build();
        if (!active) return new ITexture[] { casing, overlay };
        return new ITexture[] { casing, overlay, TextureFactory.builder()
            .addIcon(BeComTextures.TRANSCEIVER_GLOW)
            .extFacing()
            .glow()
            .build() };
    }

    @Override
    public String[] getDescription() {
        Tunables t = BeComConfig.get();
        return Tips.wrap(
            Tips.MACHINE,
            StatCollector.translateToLocal("becom.machine.transceiver.info"),
            StatCollector.translateToLocalFormatted(
                "becom.machine.transceiver.range",
                (int) Math.round(t.rangeToLow(t.transceiverBudget())),
                (int) t.talkRange(),
                (int) t.loudspeakerRange()),
            Tips.power(t.transceiverPower(), "MV"));
    }

    private final WailaInfo waila = new WailaInfo(this::info);

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

    /** Its lines for WAILA and the scanner: its state, channel and loudspeaker. */
    private InfoLines info() {
        InfoLines info = new InfoLines();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        InfoLines.state(info, true, tile != null && tile.isAllowedToWork(), working);
        info.add("becom.info.value", channel)
            .row("becom.info.row.channel")
            .brief();
        info.add(loudspeaker ? "becom.info.on" : "becom.info.off")
            .row("becom.info.row.loudspeaker");
        return info;
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        nbt.setInteger("becomChannel", channel);
        nbt.setBoolean("becomLoudspeaker", loudspeaker);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        int saved = nbt.getInteger("becomChannel");
        channel = saved < 1 || saved > RadioGroup.CHANNELS ? 1 : saved;
        loudspeaker = !nbt.hasKey("becomLoudspeaker") || nbt.getBoolean("becomLoudspeaker");
    }

    // endregion
}
