package com.enn3developer.becom.machine;

import static gregtech.api.enums.GTValues.V;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.data.BeComNbt;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.space.Portals;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.NodeHost;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.render.TextureFactory;

/**
 * The Portal Relay (DESIGN.md 9.4, D22, D46): works within a few blocks of a Nether portal, an End portal, the End's
 * exit
 * portal, a Twilight Forest portal or a Personal Space portal, telling the last two by their block classes. Two relays
 * on opposite sides of a portal pair the way pylons link, through the portal, and a pair is a fixed High hop between
 * the two dimensions' networks. Each joins its own network by a wrench link to an antenna, a Pylon Head or a Line
 * Terminal within 16 blocks (X36). Mid MV, 32 EU/t.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTEPortalRelay extends MTETieredMachineBlock implements NodeHost {

    private static final int TIER = 2;
    private static final int TICKS_PER_SECOND = 20;
    public static final int FORMAT = 1;

    // BeCom's keys from a newer BeCom: kept as they were, and the relay left alone (X16).
    private NBTTagCompound tooNew;
    private boolean working;
    private boolean registered;
    // The kinds of portal in reach at the last look, once a second.
    private Set<Portals.Kind> portals = EnumSet.noneOf(Portals.Kind.class);

    public MTEPortalRelay(int id, String name, String regionalName) {
        super(id, name, regionalName, TIER, 0, (String) null);
    }

    public MTEPortalRelay(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, 0, description, textures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEPortalRelay(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public NodeKind nodeKind() {
        return NodeKind.PORTAL_RELAY;
    }

    public boolean isWorking() {
        return working;
    }

    public boolean isTooNew() {
        return tooNew != null;
    }

    /** The kinds of portal in its reach at the last look. */
    public Set<Portals.Kind> portals() {
        return portals;
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
        if (becom == null || tooNew != null) return;
        World world = tile.getWorld();
        long pos = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
        if (!registered) {
            becom.network.ensureNode(world, pos, NodeKind.PORTAL_RELAY, tile.getOwnerUuid());
            registered = true;
            look();
        }
        if (tick % TICKS_PER_SECOND == 0) look();
        long power = BeComConfig.get()
            .relayPower();
        boolean powered = tile.isAllowedToWork() && tile.getStoredEU() >= power
            && tile.decreaseStoredEnergyUnits(power, false);
        working = powered && !portals.isEmpty();
        if (working) {
            becom.network.relay(world, pos, portals);
        } else {
            becom.network.relayOff(world, pos);
        }
        tile.setActive(working);
    }

    /** Looks for portals within its reach (DESIGN.md 9.4), in loaded chunks only. The self-test calls this directly. */
    public Set<Portals.Kind> look() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        World world = tile.getWorld();
        double reach = BeComConfig.get()
            .relayPortalDistance();
        int r = (int) Math.floor(reach);
        Set<Portals.Kind> found = EnumSet.noneOf(Portals.Kind.class);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!Portals.inReach(dx, dy, dz, reach)) continue;
                    int x = tile.getXCoord() + dx;
                    int y = tile.getYCoord() + dy;
                    int z = tile.getZCoord() + dz;
                    if (y < 0 || y >= world.getHeight() || !world.blockExists(x, y, z)) continue;
                    Portals.Kind kind = kindOf(world.getBlock(x, y, z));
                    if (kind != null) found.add(kind);
                }
            }
        }
        // The same set while nothing changed, so the network sees no change.
        if (!found.equals(portals)) portals = found;
        return portals;
    }

    /** The kind of portal a block is: vanilla's by their blocks, the others by class name (DESIGN.md 9.4). */
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

    @Override
    public boolean onWrenchRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer player, float x,
        float y, float z, ItemStack tool) {
        if (player.isSneaking()) return super.onWrenchRightClick(side, wrenchingSide, player, x, y, z, tool);
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (becom == null || !tile.isServerSide()) return false;
        if (tooNew != null) {
            player.addChatComponentMessage(new ChatComponentTranslation("becom.info.too_new"));
            return true;
        }
        long pos = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
        becom.network.ensureNode(tile.getWorld(), pos, NodeKind.PORTAL_RELAY, tile.getOwnerUuid());
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
        return new String[] { StatCollector.translateToLocal("becom.machine.portal_relay.info"),
            StatCollector.translateToLocalFormatted(
                "becom.machine.portal_relay.portal",
                String.format(Locale.ROOT, "%.0f", t.relayPortalDistance())),
            StatCollector.translateToLocalFormatted("becom.machine.portal_relay.pair", t.halfLinkSeconds() / 60),
            StatCollector.translateToLocalFormatted("becom.machine.portal_relay.link", (int) t.wrenchLinkDistance()),
            StatCollector.translateToLocalFormatted("becom.machine.power", t.relayPower(), "MV") };
    }

    @Override
    public String[] getInfoData() {
        List<String> info = new ArrayList<>();
        if (tooNew != null) info.add(StatCollector.translateToLocal("becom.info.too_new"));
        info.add(StatCollector.translateToLocal(working ? "becom.info.online" : "becom.info.offline"));
        if (portals.isEmpty()) {
            info.add(StatCollector.translateToLocal("becom.relay.no_portal"));
        } else {
            for (Portals.Kind kind : portals) {
                info.add(
                    StatCollector.translateToLocalFormatted(
                        "becom.relay.portal",
                        StatCollector.translateToLocal(
                            "becom.portal." + kind.name()
                                .toLowerCase(Locale.ROOT))));
            }
        }
        BeComServer becom = BeComServer.get();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (becom != null && tile != null) {
            NodeRef partner = becom.network.partnerOf(ref());
            info.add(
                partner == null ? StatCollector.translateToLocal("becom.relay.unpaired")
                    : StatCollector.translateToLocalFormatted(
                        "becom.relay.paired",
                        BlockPos.toString(partner.pos()),
                        partner.dimension()));
            NetworkModel model = becom.network.model(tile.getWorld().provider.dimensionId);
            NetNode node = model == null ? null : model.node(ref().pos());
            if (node != null && !node.links()
                .isEmpty()) {
                info.add(
                    StatCollector.translateToLocalFormatted(
                        "becom.dish.linked",
                        BlockPos.toString(
                            node.links()
                                .iterator()
                                .next())));
            }
        }
        return info.toArray(new String[0]);
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
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        tooNew = BeComNbt.tooNew(nbt, FORMAT) ? BeComNbt.ours(nbt) : null;
    }

    // endregion
}
