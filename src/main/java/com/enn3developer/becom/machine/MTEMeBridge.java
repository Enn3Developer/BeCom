package com.enn3developer.becom.machine;

import static gregtech.api.enums.GTValues.V;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.data.BeComNbt;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.NodeHost;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.render.TextureFactory;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * The ME Bridge (DESIGN.md 8.3, D45): joins an ME network to a BeCom network, so ME Phones on it can call any phone.
 * It connects to ME cable on any side the way GT's ME hatches do, with an AE2 grid proxy of its own that takes one
 * channel, and is wrench-linked to a Pylon Head, a Line Terminal or an antenna within 16 blocks (X40). Late EV, 512
 * EU/t; works only while loaded, powered and online in its ME network (D11).
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTEMeBridge extends MTETieredMachineBlock implements NodeHost, IGridProxyable {

    private static final int TIER = 4;
    public static final int FORMAT = 1;

    // BeCom's keys from a newer BeCom: kept as they were, and the bridge left alone (X16).
    private NBTTagCompound tooNew;
    // The NBT key AE2 saves the bridge's node under.
    public static final String PROXY = "proxy";

    private AENetworkProxy proxy;
    private boolean working;
    private boolean registered;

    public MTEMeBridge(int id, String name, String regionalName) {
        super(id, name, regionalName, TIER, 0, (String) null);
    }

    public MTEMeBridge(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, 0, description, textures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEMeBridge(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public NodeKind nodeKind() {
        return NodeKind.ME_BRIDGE;
    }

    public boolean isWorking() {
        return working;
    }

    public boolean isTooNew() {
        return tooNew != null;
    }

    public NodeRef ref() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        return new NodeRef(
            tile.getWorld().provider.dimensionId,
            BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
    }

    // region ME network

    @Override
    public AENetworkProxy getProxy() {
        if (proxy == null) {
            proxy = new AENetworkProxy(this, PROXY, getStackForm(1), true);
            if (BeComConfig.get()
                .meBridgeChannels() > 0) proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
            proxy.setValidSides(EnumSet.complementOf(EnumSet.of(ForgeDirection.UNKNOWN)));
            proxy.setIdlePowerUsage(0);
        }
        return proxy;
    }

    @Override
    public DimensionalCoord getLocation() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        return new DimensionalCoord(tile.getWorld(), tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
    }

    @Override
    public void gridChanged() {}

    @Override
    public IGridNode getGridNode(ForgeDirection side) {
        return getProxy().getNode();
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection side) {
        return AECableType.SMART;
    }

    @Override
    public void securityBreak() {}

    /** Online in an ME network: powered there and holding its channel. */
    public boolean meOnline() {
        AENetworkProxy p = getProxy();
        return p.getNode() != null && p.isActive();
    }

    // GT's own tile invalidates the proxy and unloads it with its chunk, as for GT's ME hatches. A bridge from a newer
    // BeCom stays out of ME, so its node isn't remade without the data it was saved with.
    @Override
    public void onFirstTick(IGregTechTileEntity tile) {
        super.onFirstTick(tile);
        if (tile.isServerSide() && tooNew == null) getProxy().onReady();
    }

    // endregion

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        if (!tile.isServerSide()) return;
        BeComServer becom = BeComServer.get();
        if (becom == null || tooNew != null) return;
        World world = tile.getWorld();
        long pos = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
        if (!registered) {
            becom.network.ensureNode(world, pos, NodeKind.ME_BRIDGE, tile.getOwnerUuid());
            registered = true;
        }
        long power = BeComConfig.get()
            .meBridgePower();
        boolean powered = tile.isAllowedToWork() && tile.getStoredEU() >= power
            && tile.decreaseStoredEnergyUnits(power, false);
        working = powered && meOnline();
        if (working) {
            becom.network.heartbeat(world, pos);
            becom.network.meBridge(
                world,
                pos,
                becom.meGrids.idOf(
                    getProxy().getNode()
                        .getGrid()));
        } else {
            becom.network.meBridgeOff(world, pos);
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
        }
        super.onBlockDestroyed();
    }

    @Override
    public boolean onWrenchRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer player, float x,
        float y, float z, ItemStack tool) {
        if (player.isSneaking()) return super.onWrenchRightClick(side, wrenchingSide, player, x, y, z, tool);
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (becom == null || !tile.isServerSide() || tooNew != null) return false;
        long pos = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
        becom.network.ensureNode(tile.getWorld(), pos, NodeKind.ME_BRIDGE, tile.getOwnerUuid());
        becom.links.wrench(player, tile.getWorld(), pos);
        return true;
    }

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
        return true;
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
            .addIcon(BeComTextures.ME_BRIDGE)
            .extFacing()
            .build();
        if (!active) return new ITexture[] { casing, overlay };
        return new ITexture[] { casing, overlay, TextureFactory.builder()
            .addIcon(BeComTextures.ME_BRIDGE_GLOW)
            .extFacing()
            .glow()
            .build() };
    }

    @Override
    public String[] getDescription() {
        Tunables t = BeComConfig.get();
        return new String[] { StatCollector.translateToLocal("becom.machine.me_bridge.info"),
            StatCollector.translateToLocalFormatted("becom.machine.me_bridge.link", (int) t.wrenchLinkDistance()),
            StatCollector.translateToLocalFormatted("becom.machine.power", t.meBridgePower(), "EV") };
    }

    private final InfoSnapshot waila = new InfoSnapshot(this::info);

    @Override
    public String[] getInfoData() {
        return info().translateArray();
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        waila.write(tag);
    }

    @Override
    public void getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(stack, tip, accessor, config);
        InfoLines.read(accessor.getNBTData(), tip);
    }

    private InfoLines info() {
        InfoLines info = new InfoLines();
        if (tooNew != null) info.add("becom.info.too_new");
        info.add(working ? "becom.info.online" : "becom.info.offline");
        info.add(meOnline() ? "becom.info.me_online" : "becom.info.me_offline");
        return info;
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        if (tooNew != null) {
            BeComNbt.restore(nbt, tooNew);
            return;
        }
        BeComNbt.write(nbt, FORMAT);
        getProxy().writeToNBT(nbt);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        if (BeComNbt.tooNew(nbt, FORMAT)) {
            // Its node too: AE2's owner, security key and grid storage come back with the newer BeCom.
            tooNew = BeComNbt.ours(nbt);
            if (nbt.hasKey(PROXY)) tooNew.setTag(
                PROXY,
                nbt.getTag(PROXY)
                    .copy());
            return;
        }
        tooNew = null;
        // A bridge placed from an item that kept some of its data, its paint or a cover, has no node saved: AE2 would
        // read the missing one as AE player 0's and drop the placer GT just gave it. GT's own ME hatches check too.
        if (nbt.hasKey(PROXY)) getProxy().readFromNBT(nbt);
    }

    // endregion
}
