package com.enn3developer.becom.machine;

import static gregtech.api.enums.GTValues.V;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
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
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.block.CableEnd;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.gui.InfoScreen;
import com.enn3developer.becom.item.Tips;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.Buses;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.space.Portals;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.NodeHost;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.render.TextureFactory;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * The Portal Relay: works within a few blocks of a Nether portal, an End portal, the End's exit portal, a Twilight
 * Forest portal or a Personal Space portal, telling the last two by their block classes. A relay placed by the far
 * side of a portal another relay stands by pairs with it by itself, and a pair is a fixed High hop between the two
 * dimensions' networks. Each joins its own network by cable to an Exchange. Built in MV, HV and EV: the tier sets the
 * calls it carries and the KB/s of its
 * pair, which works at its lower relay's tier.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTEPortalRelay extends MTETieredMachineBlock implements NodeHost, CableEnd {

    private static final int TICKS_PER_SECOND = 20;

    private boolean working;
    // Whether it had the power to work at its last tick, switched on.
    private boolean powered;
    private boolean registered;
    // The kinds of portal in reach at the last look, once a second.
    private Set<Portals.Kind> portals = EnumSet.noneOf(Portals.Kind.class);
    // How far the nearest portal block of each kind was, in blocks, at the last look; for its screen.
    private Map<Portals.Kind, Integer> nearest = new EnumMap<>(Portals.Kind.class);
    // Where the Personal Space portals in reach lead, at the last look; none yet, so the first look always tells the
    // network, which may still hold what this relay saw before its chunk unloaded.
    private Set<Integer> personalTargets;

    public MTEPortalRelay(int id, String name, String regionalName, int tier) {
        super(id, name, regionalName, tier, 0, (String) null);
    }

    public MTEPortalRelay(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, 0, description, textures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEPortalRelay(mName, mTier, mDescriptionArray, mTextures);
    }

    /** Its item is BeCom's, as its block is. */
    @Override
    public ItemStack getStackForm(long amount) {
        return BeComMachines.stack(this, amount);
    }

    /** A gateway on two buses would carry calls between them like a router: it takes a single cable. */
    @Override
    public boolean oneCable() {
        return true;
    }

    @Override
    public Buses.Kind busKind() {
        return Buses.Kind.GATEWAY;
    }

    @Override
    public NodeKind nodeKind() {
        return NodeKind.PORTAL_RELAY;
    }

    public boolean isWorking() {
        return working;
    }

    /** The kinds of portal in its reach at the last look. */
    public Set<Portals.Kind> portals() {
        return portals;
    }

    /** The dimensions the Personal Space portals in its reach lead to, at the last look. */
    public Set<Integer> personalTargets() {
        return personalTargets == null ? Set.of() : personalTargets;
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
            becom.network.ensureNode(world, pos, NodeKind.PORTAL_RELAY, tile.getOwnerUuid());
            registered = true;
            look();
        }
        if (tick % TICKS_PER_SECOND == 0) look();
        long power = power(BeComConfig.get());
        powered = tile.isAllowedToWork() && tile.getStoredEU() >= power && tile.decreaseStoredEnergyUnits(power, false);
        working = powered && !portals.isEmpty();
        if (working) {
            becom.network.relay(world, pos, portals, mTier);
        } else {
            becom.network.relayOff(world, pos);
        }
        tile.setActive(working);
    }

    /** Its GT voltage tier: 2 for MV to 4 for EV. */
    public int tier() {
        return mTier;
    }

    /** What it draws: relayPower at MV, four times as much each tier up, like GT's own machines. */
    private long power(Tunables t) {
        return (long) t.relayPower() << 2 * (mTier - Tunables.RELAY_MIN_TIER);
    }

    /** Looks for portals within its reach, in loaded chunks only. */
    public Set<Portals.Kind> look() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        World world = tile.getWorld();
        double reach = BeComConfig.get()
            .relayPortalDistance();
        int r = (int) Math.floor(reach);
        Set<Portals.Kind> found = EnumSet.noneOf(Portals.Kind.class);
        Map<Portals.Kind, Integer> near = new EnumMap<>(Portals.Kind.class);
        Set<Integer> targets = new HashSet<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!Portals.inReach(dx, dy, dz, reach)) continue;
                    int x = tile.getXCoord() + dx;
                    int y = tile.getYCoord() + dy;
                    int z = tile.getZCoord() + dz;
                    if (y < 0 || y >= world.getHeight() || !world.blockExists(x, y, z)) continue;
                    Portals.Kind kind = kindOf(world.getBlock(x, y, z));
                    if (kind != null) {
                        found.add(kind);
                        near.merge(kind, (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)), Math::min);
                    }
                    if (kind == Portals.Kind.PERSONAL_SPACE) {
                        Integer target = Portals.personalTarget(world.getTileEntity(x, y, z));
                        if (target != null) targets.add(target);
                        if (target != null && target == Portals.UNKNOWN_DIMENSION && !personalUnreadable) {
                            personalUnreadable = true;
                            BeCom.LOG.warn(
                                "Can't read where a Personal Space portal at {}, {}, {} leads, so relays take any as "
                                    + "leading anywhere",
                                x,
                                y,
                                z);
                        }
                    }
                }
            }
        }
        // The same set while nothing changed, so the network sees no change.
        if (!found.equals(portals)) portals = found;
        nearest = near;
        if (!targets.equals(personalTargets)) {
            personalTargets = Set.copyOf(targets);
            BeComServer becom = BeComServer.get();
            if (becom != null) becom.network.personalTargets(world, ref().pos(), personalTargets);
        }
        return portals;
    }

    // Logged once: a Personal Space whose portals BeCom can't read.
    private static boolean personalUnreadable;

    /** The kind of portal a block is: vanilla's by their blocks, the others by class name. */
    public static Portals.Kind kindOf(Block block) {
        if (block == Blocks.portal) return Portals.Kind.NETHER;
        if (block == Blocks.end_portal) return Portals.Kind.END;
        return Portals.ofBlockClass(
            block.getClass()
                .getName());
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
        return InfoScreen.build("becom_portal_relay", getLocalName(), GTValues.VN[mTier], this::info, sync);
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
        ITexture casing = Textures.BlockIcons.MACHINE_CASINGS[mTier][colorIndex + 1];
        if (side != facing) return new ITexture[] { casing };
        ITexture overlay = TextureFactory.builder()
            .addIcon(BeComTextures.PORTAL_RELAY)
            .extFacing()
            .build();
        if (!active) return new ITexture[] { casing, overlay };
        return new ITexture[] { casing, overlay, TextureFactory.builder()
            .addIcon(BeComTextures.PORTAL_RELAY_GLOW)
            .extFacing()
            .glow()
            .build() };
    }

    @Override
    public String[] getDescription() {
        Tunables t = BeComConfig.get();
        return Tips.wrap(
            Tips.MACHINE,
            StatCollector.translateToLocal("becom.machine.portal_relay.info"),
            StatCollector.translateToLocalFormatted(
                "becom.machine.portal_relay.portal",
                String.format(Locale.ROOT, "%.0f", t.relayPortalDistance())),
            StatCollector.translateToLocal("becom.machine.portal_relay.pair"),
            StatCollector.translateToLocal("becom.machine.portal_relay.link"),
            StatCollector
                .translateToLocalFormatted("becom.machine.portal_relay.calls", t.relayCalls(mTier), t.relayRate(mTier)),
            Tips.power(power(t), GTValues.VN[mTier]));
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

    /** What it says about itself: on WAILA and the scanner, and laid out as the Gateways board on its screen. */
    private InfoLines info() {
        InfoLines info = new InfoLines();
        BeComServer becom = BeComServer.get();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        NodeRef partner = becom == null || tile == null ? null : becom.network.partnerOf(ref());
        if (!powered) InfoLines.state(info, true, tile != null && tile.isAllowedToWork(), false);
        else if (!working) info.problem("becom.relay.status_no_portal")
            .status();
        else if (partner != null) info.add("becom.relay.status_paired")
            .status();
        else info.problem("becom.relay.status_unpaired")
            .status();
        info.add("becom.info.eu_t", working ? power(BeComConfig.get()) : 0)
            .right();
        if (portals.isEmpty()) {
            info.problem(
                "becom.relay.no_portal_within",
                (int) BeComConfig.get()
                    .relayPortalDistance());
        } else {
            for (Portals.Kind kind : portals) {
                info.add(
                    "becom.relay.portal_kind",
                    new InfoLines.Key(
                        "becom.portal_name." + kind.name()
                            .toLowerCase(Locale.ROOT)),
                    nearest.getOrDefault(kind, 0))
                    .row("becom.info.row.portal");
            }
            for (int target : personalTargets()) {
                if (target == Portals.UNKNOWN_DIMENSION) info.add("becom.relay.leads_anywhere")
                    .sub();
                else info.add("becom.relay.leads_into", target)
                    .sub();
            }
        }
        if (becom != null && tile != null) {
            NetworkModel model = becom.network.model(tile.getWorld().provider.dimensionId);
            NetNode node = model == null ? null : model.node(ref().pos());
            NodeRef kept = node == null ? null : node.partner();
            if (partner != null) {
                info.add(
                    "becom.relay.far_side",
                    GTValues.VN[becom.network.pairTier(partner, partner)],
                    becom.space.dimensionName(partner.dimension()))
                    .row("becom.info.row.far_side");
                SignalLevel hop = BeComConfig.get()
                    .relayHop();
                info.add(
                    "becom.relay.hop_fixed",
                    new InfoLines.Key(
                        "becom.level." + hop.name()
                            .toLowerCase(Locale.ROOT)))
                    .level("becom.info.row.hop", hop);
            }
            int slots = BeComConfig.get()
                .relayCalls(partner == null ? mTier : becom.network.pairTier(ref(), partner));
            if (InfoLines.calls(info, tile, slots)) info.warn("becom.info.calls_full", slots);
            InfoLines.backhaul(
                info,
                becom,
                ref().dimension(),
                ref().pos(),
                "becom.info.row.backhaul",
                InfoLines.Backhaul.RATE);
            if (partner != null && becom.network.model(partner.dimension()) != null) {
                InfoLines.backhaul(
                    info,
                    becom,
                    partner.dimension(),
                    partner.pos(),
                    "becom.info.row.far_backhaul",
                    InfoLines.Backhaul.RATE);
            }
            if (partner != null) {
                int pair = becom.network.pairTier(ref(), partner);
                if (pair < mTier) {
                    info.warn(
                        "becom.relay.lower_tier",
                        GTValues.VN[pair],
                        GTValues.VN[pair],
                        BeComConfig.get()
                            .relayCalls(pair),
                        BeComConfig.get()
                            .relayCalls(mTier));
                }
            } else if (kept != null && becom.network.model(kept.dimension()) == null) {
                // The far side's dimension isn't loaded, so the pair can't be checked, nor carry calls, until it is.
                info.problem("becom.relay.paired_unloaded", kept.dimension());
            } else {
                info.problem("becom.relay.unpaired");
            }
        }
        return info;
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {}

    @Override
    public void loadNBTData(NBTTagCompound nbt) {}

    // endregion
}
