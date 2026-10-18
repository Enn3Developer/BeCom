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
import com.enn3developer.becom.block.BlockPhoneCable;
import com.enn3developer.becom.block.CableEnd;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.gui.RouterScreen;
import com.enn3developer.becom.item.Tips;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.Buses;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.NetworkService;
import com.enn3developer.becom.server.NodeHost;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.render.TextureFactory;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * The Router: where a building's line starts. An LV, MV or HV machine drawing a few EU/t that links
 * to one pylon and carries the calls of every bus of cable joining it, up to its tier's cable, as many at once as its
 * tier carries. The phones on a bus share its rate.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTERouter extends MTETieredMachineBlock implements NodeHost, CableEnd {

    private static final int TICKS_PER_SECOND = 20;

    private boolean working;
    private boolean registered;

    public MTERouter(int id, String name, String regionalName, CableTier tier) {
        super(id, name, regionalName, tier.voltageTier(), 0, (String) null);
    }

    public MTERouter(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, 0, description, textures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTERouter(mName, mTier, mDescriptionArray, mTextures);
    }

    /** Its item is BeCom's, as its block is. */
    @Override
    public ItemStack getStackForm(long amount) {
        return BeComMachines.stack(this, amount);
    }

    @Override
    public Buses.Kind busKind() {
        return Buses.Kind.ROUTER;
    }

    @Override
    public NodeKind nodeKind() {
        return NodeKind.ROUTER;
    }

    public boolean isWorking() {
        return working;
    }

    /** Its tier, which sets its ports. */
    public CableTier tier() {
        return CableTier.ofVoltage(mTier);
    }

    /** Cable up to its own tier, on any face. */
    @Override
    public CableTier bestCable() {
        return tier();
    }

    /** The phones on the buses joining it, by number, each once. */
    public List<Integer> phones() {
        List<Integer> phones = new java.util.ArrayList<>();
        BeComServer becom = BeComServer.get();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (becom == null || tile == null) return phones;
        for (Buses.Bus bus : becom.network.busesAt(tile.getWorld().provider.dimensionId, pos(tile))) {
            for (Buses.End end : bus.ends(Buses.Kind.PHONE)) {
                if (end.number() >= 0 && !phones.contains(end.number())) phones.add(end.number());
            }
        }
        phones.sort(null);
        return phones;
    }

    /** The lowest-numbered phone on the buses joining it, or -1. */
    public int phone() {
        List<Integer> phones = phones();
        return phones.isEmpty() ? -1 : phones.get(0);
    }

    private static long pos(IGregTechTileEntity tile) {
        return BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        if (!tile.isServerSide()) return;
        BeComServer becom = BeComServer.get();
        if (becom == null) return;
        World world = tile.getWorld();
        long pos = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
        if (!registered) {
            becom.network.ensureNode(world, pos, NodeKind.ROUTER, tile.getOwnerUuid());
            becom.network.routerTier(world, pos, tier());
            registered = true;
        }
        long power = BeComConfig.get()
            .routerPower();
        working = tile.isAllowedToWork() && tile.getStoredEU() >= power && tile.decreaseStoredEnergyUnits(power, false);
        if (working) becom.network.heartbeat(world, pos);
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
        if (becom == null || !tile.isServerSide()) return false;
        long pos = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
        becom.network.ensureNode(tile.getWorld(), pos, NodeKind.ROUTER, tile.getOwnerUuid());
        becom.links.wrench(player, tile.getWorld(), pos);
        return true;
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
        return RouterScreen.build(this, data, sync);
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
        return V[mTier];
    }

    @Override
    public long maxAmperesIn() {
        return 1;
    }

    @Override
    public long maxEUStore() {
        // Like GT's own hulls: room for a couple of seconds of input.
        return 512 + V[mTier] * 50L;
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
        ITexture casing = Textures.BlockIcons.MACHINE_CASINGS[mTier][colorIndex + 1];
        if (side != facing) return new ITexture[] { casing };
        ITexture overlay = TextureFactory.builder()
            .addIcon(BeComTextures.ROUTER)
            .extFacing()
            .build();
        if (!active) return new ITexture[] { casing, overlay };
        return new ITexture[] { casing, overlay, TextureFactory.builder()
            .addIcon(BeComTextures.ROUTER_GLOW)
            .extFacing()
            .glow()
            .build() };
    }

    @Override
    public String[] getDescription() {
        Tunables t = BeComConfig.get();
        return Tips.wrap(
            Tips.MACHINE,
            StatCollector.translateToLocal("becom.machine.router.info"),
            StatCollector.translateToLocalFormatted("becom.machine.router.calls", t.routerCalls(tier())),
            StatCollector.translateToLocalFormatted(
                "becom.machine.router.cable",
                StatCollector.translateToLocal("tile.becom." + BlockPhoneCable.nameOf(tier()) + ".name")),
            Tips.power(t.routerPower(), tier().voltage()));
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

    /**
     * Its lines for WAILA and the scanner, as on the Scanner board: its state, the calls it carries, each bus of cable
     * joining it, and its uplink. The scanner adds each phone on its buses and each span.
     */
    private InfoLines info() {
        InfoLines info = new InfoLines();
        BeComServer becom = BeComServer.get();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        Tunables t = BeComConfig.get();
        if (becom == null || tile == null || !working) {
            InfoLines.state(info, true, tile != null && tile.isAllowedToWork(), working);
            if (becom == null || tile == null) return info;
        } else info.add("becom.info.online")
            .status();
        NodeRef self = new NodeRef(tile.getWorld().provider.dimensionId, pos(tile));
        InfoLines.calls(info, tile, t.routerCalls(tier()));
        InfoLines.buses(info, becom, self.dimension(), self.pos());
        // Only a loaded dimension's model: asking by world would load one that isn't.
        NetworkModel model = becom.network.model(self.dimension());
        NetNode node = model == null ? null : model.node(self.pos());
        if (node == null || node.links()
            .isEmpty()) {
            info.problem("becom.ui.router.no_uplink")
                .row("becom.info.row.uplink");
        } else {
            List<NetworkService.Run> runs = becom.network.runsFrom(self.dimension(), self.pos());
            if (runs.isEmpty()) info.problem("becom.ui.router.uplink_down")
                .row("becom.info.row.uplink");
            else {
                NetworkService.Run run = runs.get(0);
                if (run.rate() <= 0) info.problem("becom.ui.router.too_long")
                    .row("becom.info.row.uplink");
                else info
                    .bundle(
                        run.cables(),
                        "becom.info.uplink",
                        becom.calls.usedOn(run.link()) + becom.apps.dataOn(run.link()),
                        run.rate())
                    .row("becom.info.row.uplink");
            }
        }
        for (int phone : phones()) info.detail("becom.info.device", phone);
        if (node != null) for (long other : node.links()) InfoLines.span(info, model, node.pos(), other);
        return info;
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        // Nothing to keep: its buses are the world's cable, its links live in the network data.
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {}

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    // endregion
}
