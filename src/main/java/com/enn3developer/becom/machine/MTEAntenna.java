package com.enn3developer.becom.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;

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
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.data.BeComNbt;
import com.enn3developer.becom.gui.AntennaScreen;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.radio.RadioHops;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.NodeHost;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.casing.Casings;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * An antenna, T1 to T3 (DESIGN.md 7, 3.2): the head on top of a mast of frame boxes standing on a 3×3 base of machine
 * casings with an energy hatch. While formed and powered it reports to the antenna network every tick, which surveys
 * it and links it to other antennas; it also bridges wired lines it's wrench-linked to (X19).
 * <p>
 * Like the Pylon Head, it isn't a recipe machine: it checks its structure on GT's schedule, drains its power each tick,
 * and tells the network. A missing tick of power means offline, never a shutdown to restart by hand.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTEAntenna extends MTEEnhancedMultiBlockBase<MTEAntenna> implements ISurvivalConstructable, NodeHost {

    private static final String PIECE_MAST = "mast";
    private static final String PIECE_BASE = "base";
    private static final int TICKS_PER_SECOND = 20;
    private static final String[] VOLTAGE_NAMES = { "ULV", "LV", "MV", "HV" };

    @SuppressWarnings("unchecked")
    private static final IStructureDefinition<MTEAntenna>[] STRUCTURES = new IStructureDefinition[] { null,
        structure(1), structure(2), structure(3) };

    public static final int FORMAT = 1;

    private final int tier;
    // BeCom's keys from a newer BeCom: kept as they were, and the antenna left alone (X16).
    private NBTTagCompound tooNew;
    private int mast;
    private boolean working;
    private boolean registered;
    private boolean locked;

    public MTEAntenna(int id, String name, String regionalName, int tier) {
        super(id, name, regionalName);
        this.tier = tier;
    }

    public MTEAntenna(String name, int tier) {
        super(name);
        this.tier = tier;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEAntenna(mName, tier);
    }

    /** Its mast's frame boxes (DESIGN.md 3.2): steel, aluminium, stainless steel. */
    static Materials frameOf(int tier) {
        return switch (tier) {
            case 1 -> Materials.Steel;
            case 2 -> Materials.Aluminium;
            default -> Materials.StainlessSteel;
        };
    }

    /** Its base's machine casings (DESIGN.md 3.2): LV, MV, HV. */
    static Casings casingOf(int tier) {
        return switch (tier) {
            case 1 -> Casings.LVMachineCasing;
            case 2 -> Casings.MVMachineCasing;
            default -> Casings.HVMachineCasing;
        };
    }

    private static IStructureDefinition<MTEAntenna> structure(int tier) {
        Casings casing = casingOf(tier);
        return StructureDefinition.<MTEAntenna>builder()
            .addShape(PIECE_MAST, new String[][] { { "F" } })
            .addShape(PIECE_BASE, transpose(new String[][] { { "CCC", "CCC", "CCC" } }))
            .addElement('F', ofFrame(frameOf(tier)))
            .addElement(
                'C',
                buildHatchAdder(MTEAntenna.class).atLeast(Energy)
                    .casingIndex(casing.textureId)
                    .hint(1)
                    .buildAndChain(onElementPass(x -> {}, casing.asElement())))
            .build();
    }

    @Override
    public NodeKind nodeKind() {
        return NodeKind.antenna(tier);
    }

    public int tier() {
        return tier;
    }

    /** The mast's height at the last structure check. */
    public int mast() {
        return mast;
    }

    public boolean isWorking() {
        return working;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        if (tooNew != null) return;
        this.locked = locked;
        markDirty();
    }

    /** Saved by a newer BeCom: this one leaves it alone. */
    public boolean isTooNew() {
        return tooNew != null;
    }

    public UUID owner() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        return tile == null ? null : tile.getOwnerUuid();
    }

    public NodeRef ref() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        return new NodeRef(
            tile.getWorld().provider.dimensionId,
            BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
    }

    // region Structure

    @Override
    public IStructureDefinition<MTEAntenna> getStructureDefinition() {
        return STRUCTURES[tier];
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        // The mast always hangs straight down, so the head may only face sideways, unrotated.
        return (direction, rotation, flip) -> direction.offsetY == 0 && rotation == Rotation.NORMAL
            && flip == Flip.NONE;
    }

    @Override
    public void checkMachine(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors) {
        Tunables t = BeComConfig.get();
        int found = 0;
        // Frames below the head sit at -1, -2, ...; the base comes right under the last one.
        while (found < t.antennaMastMax() && checkPiece(PIECE_MAST, 0, -(found + 1), 0, null)) found++;
        mast = found;
        if (found < t.antennaMastMin()) {
            errors.add(StructureErrorRegistry.TOO_SHORT_HEIGHT);
            return;
        }
        if (!checkPiece(PIECE_BASE, 1, -(found + 1), 1, errors)) return;
        checkHasEnergyHatch(errors);
    }

    /** The mast a hologram or the build tool makes: the shortest, plus one per extra item in the stack. */
    private static int buildMast(ItemStack trigger) {
        Tunables t = BeComConfig.get();
        int extra = trigger == null ? 0 : Math.max(0, trigger.stackSize - 1);
        return Math.min(t.antennaMastMax(), t.antennaMastMin() + extra);
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {
        int target = buildMast(trigger);
        for (int i = 1; i <= target; i++) buildPiece(PIECE_MAST, trigger, hintsOnly, 0, -i, 0);
        buildPiece(PIECE_BASE, trigger, hintsOnly, 1, -(target + 1), 1);
    }

    @Override
    public int survivalConstruct(ItemStack trigger, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int target = buildMast(trigger);
        int built = 0;
        for (int i = 1; i <= target && built < elementBudget; i++) {
            int placed = survivalBuildPiece(PIECE_MAST, trigger, 0, -i, 0, elementBudget - built, env, false);
            if (placed > 0) built += placed;
        }
        if (built < elementBudget) {
            int placed = survivalBuildPiece(
                PIECE_BASE,
                trigger,
                1,
                -(target + 1),
                1,
                elementBudget - built,
                env,
                false,
                true);
            if (placed > 0) built += placed;
        }
        return built > 0 ? built : -1;
    }

    // endregion

    // region Ticking

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        if (!tile.isServerSide()) return;
        BeComServer becom = BeComServer.get();
        if (becom == null || tooNew != null) return;
        World world = tile.getWorld();
        long pos = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
        if (!registered) {
            becom.network.ensureNode(world, pos, nodeKind(), tile.getOwnerUuid());
            registered = true;
        }
        if (mUpdated) {
            if (mUpdate <= 0) mUpdate = 50;
            mUpdated = false;
        }
        if (--mUpdate == 0 || --mStartUpCheck == 0 || tick % (TICKS_PER_SECOND * 5) == 0) refreshStructure();
        working = mMachine && tile.isAllowedToWork() && drainPower();
        if (working) {
            becom.network.heartbeat(world, pos);
            becom.antennas.seen(ref(), tier, head(), locked, tile.getOwnerUuid());
        }
        tile.setActive(working);
    }

    /**
     * Where it transmits from (DESIGN.md 6.1): the top of the head, how far below the surface that is, and the top of
     * its casings, the ground it stands on.
     */
    public RadioHops.End head() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        int x = tile.getXCoord();
        int y = tile.getYCoord();
        int z = tile.getZCoord();
        double eye = y + 1;
        int surface = tile.getWorld()
            .getHeightValue(x, z);
        return new RadioHops.End(x + 0.5, eye, z + 0.5, surface - eye, y - mast);
    }

    /** Runs the structure check now and tells the networks; the self-test calls this directly. */
    public boolean refreshStructure() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        // Left alone: no node, no formed state, no bridges written for it here (X16).
        if (tooNew != null) return false;
        checkStructure(true, tile);
        BeComServer becom = BeComServer.get();
        if (becom != null && tile.isServerSide()) {
            long pos = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
            becom.network.ensureNode(tile.getWorld(), pos, nodeKind(), tile.getOwnerUuid());
            becom.network.setFormed(tile.getWorld(), pos, mMachine, mMachine ? mast : 0);
            if (!mMachine) becom.antennas.gone(ref());
            registered = true;
        }
        return mMachine;
    }

    private boolean drainPower() {
        long power = BeComConfig.get()
            .antennaPower(tier);
        long stored = 0;
        for (MTEHatchEnergy hatch : mEnergyHatches) {
            if (hatch.isValid()) stored += hatch.getBaseMetaTileEntity()
                .getStoredEU();
        }
        // GT's drain takes what it can even when that's not enough; check first so a short tick is offline, not free.
        return stored >= power && drainEnergyInput(power);
    }

    @Override
    public void onBlockDestroyed() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (becom != null && tile != null && tile.isServerSide()) {
            becom.network
                .removeNode(tile.getWorld(), BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
            becom.antennas.gone(ref());
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
        refreshStructure();
        becom.links
            .wrench(player, tile.getWorld(), BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
        return true;
    }

    // endregion

    // region Screen

    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new MTEMultiBlockBaseGui<>(this) {

            @Override
            public ModularPanel build(PosGuiData data, PanelSyncManager sync, UISettings settings) {
                return AntennaScreen.build(MTEAntenna.this, data, sync);
            }
        };
    }

    @Override
    public boolean doesBindPlayerInventory() {
        return false;
    }

    // endregion

    // region Looks and info

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int colorIndex,
        boolean active, boolean redstoneLevel) {
        ITexture casing = Textures.BlockIcons.MACHINE_CASINGS[tier][colorIndex + 1];
        if (side != facing) return new ITexture[] { casing };
        IIconContainer overlay = switch (tier) {
            case 1 -> BeComTextures.ANTENNA_T1;
            case 2 -> BeComTextures.ANTENNA_T2;
            default -> BeComTextures.ANTENNA_T3;
        };
        IIconContainer glow = switch (tier) {
            case 1 -> BeComTextures.ANTENNA_T1_GLOW;
            case 2 -> BeComTextures.ANTENNA_T2_GLOW;
            default -> BeComTextures.ANTENNA_T3_GLOW;
        };
        ITexture front = TextureFactory.builder()
            .addIcon(overlay)
            .extFacing()
            .build();
        if (!active) return new ITexture[] { casing, front };
        return new ITexture[] { casing, front, TextureFactory.builder()
            .addIcon(glow)
            .extFacing()
            .glow()
            .build() };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        Tunables t = BeComConfig.get();
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("becom.machine.antenna.type"))
            .addInfo(StatCollector.translateToLocal("becom.machine.antenna.info"))
            .addInfo(
                StatCollector.translateToLocalFormatted(
                    "becom.machine.antenna.range",
                    (int) Math.round(t.rangeToLow(t.antennaBudget(tier))),
                    (int) t.antennaBudget(tier)))
            .addInfo(
                StatCollector
                    .translateToLocalFormatted("becom.machine.antenna.bridge", (int) t.wrenchLinkDistance(), 15))
            .addInfo(
                StatCollector
                    .translateToLocalFormatted("becom.machine.power", t.antennaPower(tier), VOLTAGE_NAMES[tier]))
            .beginVariableStructureBlock(3, 3, t.antennaMastMin() + 2, t.antennaMastMax() + 2, 3, 3, false)
            .addController(StatCollector.translateToLocal("becom.machine.antenna.controller"))
            .addCasingInfoRange(
                StatCollector.translateToLocalFormatted(
                    "becom.machine.antenna.mast",
                    frameOf(tier).getLocalizedNameForItem("%material Frame Box")),
                t.antennaMastMin(),
                t.antennaMastMax(),
                false)
            .addCasingInfoMin(casingOf(tier).getLocalizedName(), 8, false)
            .addEnergyHatch(StatCollector.translateToLocal("becom.machine.antenna.hatch"), 1)
            .toolTipFinisher();
        return tt;
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
        if (tooNew != null) info.add("becom.info.too_new");
        info.add("becom.info.mast", mMachine ? mast : 0);
        info.add(working ? "becom.info.online" : "becom.info.offline");
        if (locked) info.add("becom.info.locked");
        return info;
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        super.saveNBTData(nbt);
        if (tooNew != null) {
            BeComNbt.restore(nbt, tooNew);
            return;
        }
        BeComNbt.write(nbt, FORMAT);
        nbt.setBoolean("becomLocked", locked);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        if (BeComNbt.tooNew(nbt, FORMAT)) {
            tooNew = BeComNbt.ours(nbt);
            return;
        }
        tooNew = null;
        locked = nbt.getBoolean("becomLocked");
    }

    // endregion
}
