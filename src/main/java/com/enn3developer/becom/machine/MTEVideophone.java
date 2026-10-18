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
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.block.CableEnd;
import com.enn3developer.becom.block.TileCable;
import com.enn3developer.becom.block.TileTelephone;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.data.BeComNbt;
import com.enn3developer.becom.gui.PhoneScreen;
import com.enn3developer.becom.item.Tips;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.Buses;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.PhoneHandle;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.render.TextureFactory;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * The Videophone: an MV phone for wired networks, on a router's bus of cable like a Telephone. It's a speakerphone:
 * everyone within its room range is in its call,
 * which plays from its loudspeaker, and its screen shows the other side to the room. Works only while loaded and
 * powered: otherwise it can't be called and leaves its call.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTEVideophone extends MTETieredMachineBlock implements CableEnd {

    private static final int TIER = 2;
    public static final int FORMAT = 1;
    private static final String NUMBER = "becomNumber";

    // BeCom's keys from a newer BeCom: kept as they were, and the Videophone left alone.
    private NBTTagCompound tooNew;
    private int number = -1;
    private boolean working;
    private long settledAt = Long.MIN_VALUE;
    private int settledIn;
    private int ringTicks;

    public MTEVideophone(int id, String name, String regionalName) {
        super(id, name, regionalName, TIER, 0, (String) null);
    }

    public MTEVideophone(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, 0, description, textures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEVideophone(mName, mTier, mDescriptionArray, mTextures);
    }

    /** Its item is BeCom's, as its block is. */
    @Override
    public ItemStack getStackForm(long amount) {
        return BeComMachines.stack(this, amount);
    }

    /** Its number, or -1 before its first tick or when a newer BeCom saved it. */
    public int number() {
        return number;
    }

    public boolean isWorking() {
        return working;
    }

    public boolean isTooNew() {
        return tooNew != null;
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        if (!tile.isServerSide()) return;
        BeComServer becom = BeComServer.get();
        if (becom == null || tooNew != null) return;
        World world = tile.getWorld();
        int x = tile.getXCoord(), y = tile.getYCoord(), z = tile.getZCoord();
        // On the first tick after loading, and again if a mover carried it somewhere else.
        long here = BlockPos.pack(x, y, z);
        int dimension = world.provider.dimensionId;
        if (here != settledAt || dimension != settledIn) {
            settledAt = here;
            settledIn = dimension;
            settle();
        }
        long power = BeComConfig.get()
            .videophonePower();
        working = tile.isAllowedToWork() && tile.getStoredEU() >= power && tile.decreaseStoredEnergyUnits(power, false);
        tile.setActive(working);
        if (!working || !becom.phones.isHome(number, PhoneKind.VIDEOPHONE, dimension, here)) {
            ringTicks = 0;
            return;
        }
        becom.calls.videophoneHeard(
            number,
            dimension,
            x,
            y,
            z,
            tile.getFrontFacing()
                .ordinal());
        // It rings at the block, like a Telephone.
        if (!becom.calls.isRinging(number)) {
            ringTicks = 0;
        } else if (ringTicks++ % TileTelephone.RING_PERIOD_TICKS == 0) {
            world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, BeCom.MODID + ":phone.ring", 1.0f, 1.0f);
        }
    }

    /**
     * Claims its number again where it stands now, or takes a number of its own: on its first tick after being placed,
     * when it lost its number, or as a copy of another Videophone.
     */
    public void settle() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (becom == null || tile == null || tooNew != null) return;
        World world = tile.getWorld();
        long pos = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
        PhoneRecord original = number < 0 ? null : becom.phones.record(number);
        if (original != null && original.kind() == PhoneKind.VIDEOPHONE) {
            if (becom.phones.relocate(PhoneKind.VIDEOPHONE, world, pos, number)) return;
            // A copy: the original still stands with the number. Holding it would put it on two cables.
            number = becom.phones.registerCopy(world, pos, original)
                .number();
        } else {
            // GT records whoever placed it, a machine's fake player included, and a fake player never owns a phone:
            // only someone who has played on this server does. Otherwise its first user will.
            UUID placer = tile.getOwnerUuid();
            String name = placer == null ? null : UsernameCache.getLastKnownUsername(placer);
            number = becom.phones
                .registerBlockPhone(PhoneKind.VIDEOPHONE, world, pos, name == null ? null : placer, name)
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
        return tooNew != null ? -1 : number;
    }

    @Override
    public void onBlockDestroyed() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (becom != null && tile != null && tile.isServerSide() && number >= 0) {
            // Only the Videophone that owns the number ends its call and frees it; a copy with a stale number must not.
            if (becom.phones.isHome(
                number,
                PhoneKind.VIDEOPHONE,
                tile.getWorld().provider.dimensionId,
                BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()))) {
                becom.calls.phoneGone(number);
                becom.phones.unregister(number);
            }
        }
        super.onBlockDestroyed();
    }

    // region Screen

    /** Right-click opens its phone screen: dial, answer, hang up. */
    @Override
    public boolean onRightclick(IGregTechTileEntity tile, EntityPlayer player) {
        if (!tile.isServerSide()) return true;
        if (tooNew != null) {
            player.addChatComponentMessage(new ChatComponentTranslation("becom.phone.too_new"));
            return true;
        }
        BeComServer becom = BeComServer.get();
        if (becom != null && number >= 0) becom.phones.claimIfOwnerless(number, player);
        openGui(player);
        return true;
    }

    @Override
    protected boolean forceUseMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return PhoneScreen.build(new PhoneHandle.Videophone(this), data.getPlayer(), sync);
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
            .addIcon(BeComTextures.VIDEOPHONE)
            .extFacing()
            .build();
        if (!active) return new ITexture[] { casing, overlay };
        return new ITexture[] { casing, overlay, TextureFactory.builder()
            .addIcon(BeComTextures.VIDEOPHONE_GLOW)
            .extFacing()
            .glow()
            .build() };
    }

    @Override
    public String[] getDescription() {
        Tunables t = BeComConfig.get();
        return Tips.wrap(
            Tips.MACHINE,
            StatCollector.translateToLocal("becom.machine.videophone.info"),
            StatCollector.translateToLocalFormatted(
                "becom.machine.videophone.room",
                (int) t.videophoneRoom(),
                (int) t.videophoneLoudspeaker(),
                t.videophoneEmptySeconds()),
            Tips.power(t.videophonePower(), "MV"));
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

    /** Its lines for WAILA and the scanner: its state and number. */
    private InfoLines info() {
        InfoLines info = new InfoLines();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tooNew != null) info.problem("becom.info.too_new");
        InfoLines.state(info, true, tile != null && tile.isAllowedToWork(), working);
        if (number >= 0) info.add("becom.info.value", number)
            .row("becom.info.row.number");
        BeComServer becom = BeComServer.get();
        if (becom != null && tile != null) InfoLines.buses(
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
        if (tooNew != null) {
            BeComNbt.restore(nbt, tooNew);
            return;
        }
        BeComNbt.write(nbt, FORMAT);
        nbt.setInteger(NUMBER, number);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        if (BeComNbt.tooNew(nbt, FORMAT)) {
            tooNew = BeComNbt.ours(nbt);
            number = -1;
            return;
        }
        tooNew = null;
        number = nbt.hasKey(NUMBER) ? nbt.getInteger(NUMBER) : -1;
    }

    // endregion
}
