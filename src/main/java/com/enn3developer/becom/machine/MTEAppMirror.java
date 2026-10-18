package com.enn3developer.becom.machine;

import static gregtech.api.enums.GTValues.V;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.UsernameCache;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.enn3developer.becom.block.CableEnd;
import com.enn3developer.becom.block.TileCable;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.gui.InfoScreen;
import com.enn3developer.becom.item.Tips;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.Buses;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.Ownership;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.render.TextureFactory;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * The App Store Mirror: an MV machine on a bus of cable, the App Store's door into the network. A phone browses and
 * downloads store apps only while it has a path to a working mirror, at that path's rate, and a computer publishes
 * only through one. The store itself is the server's, the same behind every mirror. A screwdriver locks it to its
 * owner's team.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTEAppMirror extends MTETieredMachineBlock implements CableEnd {

    private static final int TIER = 2;
    private static final String NUMBER = "becomNumber";
    private static final String LOCKED = "becomLocked";

    private int number = -1;
    private boolean locked;
    private boolean working;
    private long settledAt = Long.MIN_VALUE;
    private int settledIn;

    public MTEAppMirror(int id, String name, String regionalName) {
        super(id, name, regionalName, TIER, 0, (String) null);
    }

    public MTEAppMirror(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, 0, description, textures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEAppMirror(mName, mTier, mDescriptionArray, mTextures);
    }

    /** Its item is BeCom's, as its block is. */
    @Override
    public ItemStack getStackForm(long amount) {
        return BeComMachines.stack(this, amount);
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        if (!tile.isServerSide()) return;
        BeComServer becom = BeComServer.get();
        if (becom == null) return;
        World world = tile.getWorld();
        long here = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
        int dimension = world.provider.dimensionId;
        // On the first tick after loading, and again if a mover carried it somewhere else.
        if (here != settledAt || dimension != settledIn) {
            settledAt = here;
            settledIn = dimension;
            settle();
        }
        long power = BeComConfig.get()
            .mirrorPower();
        working = tile.isAllowedToWork() && tile.getStoredEU() >= power && tile.decreaseStoredEnergyUnits(power, false);
        tile.setActive(working);
        if (working && becom.phones.isHome(number, PhoneKind.MIRROR, dimension, here))
            becom.apps.mirrorHeard(number, tile.getOwnerUuid(), locked);
    }

    /**
     * Claims its number again where it stands now, or takes a number of its own: on its first tick after being placed,
     * when it lost its number, or as a copy of another mirror. Its number is only how the network finds it.
     */
    private void settle() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (becom == null || tile == null) return;
        World world = tile.getWorld();
        long pos = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
        PhoneRecord original = number < 0 ? null : becom.phones.record(number);
        if (original != null && original.kind() == PhoneKind.MIRROR) {
            if (becom.phones.relocate(PhoneKind.MIRROR, world, pos, number)) return;
            // A copy: the original still stands with the number.
            number = becom.phones.registerCopy(world, pos, original)
                .number();
        } else {
            UUID placer = tile.getOwnerUuid();
            String name = placer == null ? null : UsernameCache.getLastKnownUsername(placer);
            number = becom.phones.registerBlockPhone(PhoneKind.MIRROR, world, pos, name == null ? null : placer, name)
                .number();
        }
        markDirty();
        // Its bus lists it by number.
        TileCable.busChanged(world, tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
    }

    @Override
    public Buses.Kind busKind() {
        return Buses.Kind.PHONE;
    }

    @Override
    public int busNumber() {
        return number;
    }

    @Override
    public void onBlockDestroyed() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        // Only the mirror that owns the number frees it; a copy with a stale number must not.
        if (becom != null && tile != null
            && tile.isServerSide()
            && number >= 0
            && becom.phones.isHome(
                number,
                PhoneKind.MIRROR,
                tile.getWorld().provider.dimensionId,
                BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()))) {
            becom.apps.mirrorGone(number);
            becom.phones.unregister(number);
        }
        super.onBlockDestroyed();
    }

    // region Screen and lock

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
        return InfoScreen.build("becom_app_mirror", getLocalName(), null, this::info, sync);
    }

    /** A screwdriver locks it to its owner's team, or opens it to everyone again: only its owner, team or an op. */
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer player, float x, float y, float z,
        ItemStack tool) {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile == null || !tile.isServerSide()) return;
        if (!Ownership.mayManage(player, tile.getOwnerUuid())) {
            player.addChatMessage(new ChatComponentTranslation("becom.mirror.not_yours"));
            return;
        }
        locked = !locked;
        markDirty();
        player.addChatMessage(new ChatComponentTranslation(locked ? "becom.mirror.locked" : "becom.mirror.unlocked"));
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
            .addIcon(BeComTextures.APP_MIRROR)
            .extFacing()
            .build();
        if (!active) return new ITexture[] { casing, overlay };
        return new ITexture[] { casing, overlay, TextureFactory.builder()
            .addIcon(BeComTextures.APP_MIRROR_GLOW)
            .extFacing()
            .glow()
            .build() };
    }

    @Override
    public String[] getDescription() {
        return Tips.wrap(
            Tips.MACHINE,
            StatCollector.translateToLocal("becom.machine.app_mirror.info"),
            Tips.power(
                BeComConfig.get()
                    .mirrorPower(),
                "MV"));
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

    /** What it says about itself: its state, the downloads it serves, who it serves, and its buses. */
    private InfoLines info() {
        InfoLines info = new InfoLines();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        InfoLines.state(info, true, tile != null && tile.isAllowedToWork(), working);
        BeComServer becom = BeComServer.get();
        if (becom == null || tile == null) return info;
        int[] load = becom.apps.mirrorLoad(number);
        info.add("becom.mirror.downloads", load[0], load[1])
            .row("becom.info.row.downloads")
            .brief();
        String owner = tile.getOwnerName();
        if (locked && owner != null && !owner.isEmpty()) info.add("becom.info.serves_team", owner)
            .row("becom.info.row.serves");
        else info.add("becom.info.serves_all")
            .row("becom.info.row.serves");
        InfoLines.buses(
            info,
            becom,
            tile.getWorld().provider.dimensionId,
            BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
        return info;
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        nbt.setInteger(NUMBER, number);
        nbt.setBoolean(LOCKED, locked);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        number = nbt.hasKey(NUMBER) ? nbt.getInteger(NUMBER) : -1;
        locked = nbt.getBoolean(LOCKED);
    }

    // endregion
}
