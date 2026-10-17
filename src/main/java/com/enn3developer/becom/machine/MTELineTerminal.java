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
import com.enn3developer.becom.block.PhoneCables;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.gui.LineTerminalScreen;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.phone.CableWalk;
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
 * The Line Terminal: where a building's line starts. An LV, MV or HV machine drawing a few EU/t
 * that links to one pylon and serves every device on its cable, Telephones and Videophones, as many as
 * its tier has ports: the nearest first. Its devices share its uplink's rate.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTELineTerminal extends MTETieredMachineBlock implements NodeHost {

    private static final int TICKS_PER_SECOND = 20;

    private boolean working;
    private boolean registered;
    private List<NetworkService.Device> devices = List.of();
    private boolean tooMuchCable;

    public MTELineTerminal(int id, String name, String regionalName, CableTier tier) {
        super(id, name, regionalName, tier.voltageTier(), 0, (String) null);
    }

    public MTELineTerminal(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, 0, description, textures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTELineTerminal(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public NodeKind nodeKind() {
        return NodeKind.TERMINAL;
    }

    public boolean isWorking() {
        return working;
    }

    /** Its tier, which sets its ports. */
    public CableTier tier() {
        return CableTier.ofVoltage(mTier);
    }

    /** The phones this terminal found on its cable at its last scan, nearest first, whether it serves them or not. */
    public List<Integer> phones() {
        List<Integer> phones = new java.util.ArrayList<>();
        for (NetworkService.Device device : devices) phones.add(device.phone());
        return phones;
    }

    /** The first phone this terminal found on its cable at its last scan, or -1. */
    public int phone() {
        return devices.isEmpty() ? -1
            : devices.get(0)
                .phone();
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        if (!tile.isServerSide()) return;
        BeComServer becom = BeComServer.get();
        if (becom == null) return;
        World world = tile.getWorld();
        long pos = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
        if (!registered) {
            becom.network.ensureNode(world, pos, NodeKind.TERMINAL, tile.getOwnerUuid());
            becom.network.terminalTier(world, pos, tier());
            registered = true;
        }
        long power = BeComConfig.get()
            .terminalPower();
        working = tile.isAllowedToWork() && tile.getStoredEU() >= power && tile.decreaseStoredEnergyUnits(power, false);
        if (working) becom.network.heartbeat(world, pos);
        tile.setActive(working);
        if (tick % TICKS_PER_SECOND == 0) scanCable();
    }

    /** Finds the devices on this terminal's cable and tells the network; the self-test calls this directly. */
    public void scanCable() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (becom == null || tile == null || !tile.isServerSide()) return;
        Tunables t = BeComConfig.get();
        CableWalk.Result walk = PhoneCables.walk(
            tile.getWorld(),
            tile.getXCoord(),
            tile.getYCoord(),
            tile.getZCoord(),
            t.cableReach(CableTier.FIBRE),
            t.terminalCableBudget());
        tooMuchCable = walk.overflow();
        devices = PhoneCables.devicesOf(tile.getWorld(), walk);
        long pos = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
        becom.network.terminalTier(tile.getWorld(), pos, tier());
        becom.network.attach(tile.getWorld(), pos, devices);
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
        becom.network.ensureNode(tile.getWorld(), pos, NodeKind.TERMINAL, tile.getOwnerUuid());
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
        return LineTerminalScreen.build(this, data, sync);
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
            .addIcon(BeComTextures.LINE_TERMINAL)
            .extFacing()
            .build();
        if (!active) return new ITexture[] { casing, overlay };
        return new ITexture[] { casing, overlay, TextureFactory.builder()
            .addIcon(BeComTextures.LINE_TERMINAL_GLOW)
            .extFacing()
            .glow()
            .build() };
    }

    @Override
    public String[] getDescription() {
        Tunables t = BeComConfig.get();
        return new String[] { StatCollector.translateToLocal("becom.machine.terminal.info"),
            StatCollector.translateToLocalFormatted("becom.machine.terminal.ports", t.terminalPorts(tier())),
            StatCollector.translateToLocalFormatted("becom.machine.power", t.terminalPower(), tier().voltage()) };
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

    private InfoLines info() {
        InfoLines info = new InfoLines();
        info.add(working ? "becom.info.online" : "becom.info.offline");
        BeComServer becom = BeComServer.get();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        Tunables t = BeComConfig.get();
        if (tooMuchCable) info.add("becom.info.too_much_cable", t.terminalCableBudget());
        else if (devices.isEmpty()) info.add("becom.info.no_phone");
        if (becom != null && tile != null) {
            NetworkService.Device[] all = devices.toArray(new NetworkService.Device[0]);
            NodeRef self = new NodeRef(
                tile.getWorld().provider.dimensionId,
                BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
            List<NetworkService.Device> served = becom.network.served(self, devices);
            if (!devices.isEmpty()) info.add("becom.info.devices", served.size(), t.terminalPorts(tier()));
            for (NetworkService.Device device : all) {
                if (!served.contains(device)) {
                    info.detail(
                        device.length() > t.cableReach(device.cable()) ? "becom.info.device_too_far"
                            : "becom.info.device_no_port",
                        device.phone(),
                        device.length(),
                        t.cableReach(device.cable()));
                } else if (!self.equals(becom.network.terminalOf(device.phone()))) {
                    info.detail("becom.info.device_nearer", device.phone());
                } else {
                    info.detail(
                        "becom.info.device",
                        device.phone(),
                        InfoLines.cableName(device.cable()),
                        device.length(),
                        t.cableReach(device.cable()));
                }
            }
            // Only a loaded dimension's model: asking by world would load one that isn't.
            NetworkModel model = becom.network.model(self.dimension());
            NetNode node = model == null ? null : model.node(self.pos());
            if (node != null) {
                info.add(
                    "becom.info.links",
                    node.links()
                        .size(),
                    1);
                for (long other : node.links()) InfoLines.span(info, model, node.pos(), other);
            }
        }
        return info;
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        // Nothing to keep: the devices are found again by the next cable scan, the links live in the network data.
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {}

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    // endregion
}
