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

import com.enn3developer.becom.block.PhoneCables;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.phone.CableWalk;
import com.enn3developer.becom.server.BeComServer;
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
 * The Line Terminal (DESIGN.md 5.2): where a line starts or ends. An LV machine drawing a few EU/t (D29) that links to
 * one Pylon Head and serves exactly one phone on its Phone Cable (D19), a Telephone or a Videophone (DESIGN.md 10.2).
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTELineTerminal extends MTETieredMachineBlock implements NodeHost {

    private static final int TIER = 1;
    private static final int TICKS_PER_SECOND = 20;

    private boolean working;
    private boolean registered;
    private int phone = -1;
    private int phonesSeen;
    private boolean tooMuchCable;

    public MTELineTerminal(int id, String name, String regionalName) {
        super(id, name, regionalName, TIER, 0, (String) null);
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

    /** The phone this terminal serves after its last scan, or -1. */
    public int phone() {
        return phone;
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
            registered = true;
        }
        long power = BeComConfig.get()
            .terminalPower();
        working = tile.isAllowedToWork() && tile.getStoredEU() >= power && tile.decreaseStoredEnergyUnits(power, false);
        if (working) becom.network.heartbeat(world, pos);
        tile.setActive(working);
        if (tick % TICKS_PER_SECOND == 0) scanCable();
    }

    /** Finds the phone on this terminal's cable and tells the network; the self-test calls this directly. */
    public void scanCable() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (becom == null || tile == null || !tile.isServerSide()) return;
        Tunables t = BeComConfig.get();
        CableWalk.Result walk = PhoneCables
            .walk(tile.getWorld(), tile.getXCoord(), tile.getYCoord(), tile.getZCoord(), t.phoneCableLength());
        tooMuchCable = walk.overflow();
        List<Integer> phones = PhoneCables.phonesOf(tile.getWorld(), walk);
        phonesSeen = phones.size();
        phone = phones.size() == 1 ? phones.get(0) : -1;
        becom.network.attach(
            tile.getWorld(),
            BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()),
            phone,
            phonesSeen);
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
            StatCollector.translateToLocalFormatted("becom.machine.terminal.cable", t.phoneCableLength()),
            StatCollector.translateToLocalFormatted("becom.machine.power", t.terminalPower(), "LV") };
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
        info.add(working ? "becom.info.online" : "becom.info.offline");
        BeComServer becom = BeComServer.get();
        if (phone >= 0 && becom != null && becom.network.terminalsOf(phone) > 1)
            info.add("becom.info.phone_shared", phone);
        else if (phone >= 0) info.add("becom.info.phone", phone);
        else if (tooMuchCable) info.add(
            "becom.info.too_much_cable",
            BeComConfig.get()
                .phoneCableLength() * PhoneCables.CABLES_PER_LENGTH);
        else if (phonesSeen > 1) info.add("becom.info.too_many_phones", phonesSeen);
        else info.add("becom.info.no_phone");
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (becom != null && tile != null) {
            NetNode node = becom.network.model(tile.getWorld())
                .node(BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
            if (node != null) {
                info.add(
                    "becom.info.links",
                    node.links()
                        .size(),
                    1);
                for (long other : node.links()) {
                    info.detail(
                        "becom.info.link",
                        BlockPos.toString(other),
                        (int) Math.round(BlockPos.distance(node.pos(), other)));
                }
            }
        }
        return info;
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        // Nothing to keep: the phone is found again by the next cable scan, the links live in the network data.
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {}

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    // endregion
}
