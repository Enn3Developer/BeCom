package com.enn3developer.becom.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
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
import com.enn3developer.becom.item.Tips;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.Buses;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.radio.AntennaNetwork;
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
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * An antenna, T1 to T3: the head on top of a mast of frame boxes, built like a real radio mast. T1
 * and T2 are guyed masts, held by three guys of frame boxes down to casing anchors; T3 is a self-supporting lattice
 * tower. Dipoles or panel antennas hang under the head, and an equipment shelter in front of the mast's pad holds the
 * Energy Hatch. None of it changes what the antenna reaches, which goes by the top of the head. While formed and
 * powered it reports to the antenna network every tick, which surveys it and links it to other antennas; through its
 * Line Hatches it's also a router to the buses they join.
 * <p>
 * It isn't a recipe machine: it checks its structure on GT's schedule, drains its power each tick, and tells the
 * network. A missing tick of power means offline, never a shutdown to restart by hand.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTEAntenna extends MTEEnhancedMultiBlockBase<MTEAntenna>
    implements ISurvivalConstructable, NodeHost, LineHatchHost {

    private static final String PIECE_MAST = "mast";
    private static final String PIECE_TOP = "top";
    private static final String PIECE_FOOT = "foot";

    // Each tier's top and foot, by tier (index 0 unused). Layers run top to bottom and rows from the head's front
    // backwards. The top hangs right under the head; the foot stands on the ground, its top layer on the mast, so a
    // taller mast only adds frames between the two. F is a frame box, C a casing, S a shelter casing an Energy Hatch
    // or a Line Hatch may take the place of, and a space anything.
    private static final String[][][] TOPS = { null,
        // A dipole.
        { { "FFF" } },
        // Two dipoles, crossed.
        { { "     ", "     ", "FFFFF", "     ", "     " }, { "     ", "     ", "  F  ", "     ", "     " },
            { "  F  ", "  F  ", "  F  ", "  F  ", "  F  " } },
        // A platform with a panel antenna hanging on each side.
        { { "FFF", "FFF", "FFF" }, { " F ", "FFF", " F " }, { " F ", "FFF", " F " } } };
    private static final String[][][] FEET = { null,
        // Guys from the 3rd frame to anchors 3 out at the back, left and right; the pad; the shelter in front.
        { { "       ", "       ", "       ", "   F   ", "       ", "       ", "       " },
            { "       ", "       ", "       ", "  FFF  ", "   F   ", "       ", "       " },
            { "  SSS  ", "  SSS  ", "       ", " F F F ", "       ", "   F   ", "       " },
            { "  SSS  ", "  SSS  ", "  CCC  ", "C CCC C", "  CCC  ", "       ", "   C   " } },
        // Guys from the 4th frame to anchors 4 out.
        { { "         ", "         ", "         ", "    F    ", "         ", "         ", "         ", "         " },
            { "         ", "         ", "         ", "   FFF   ", "    F    ", "         ", "         ", "         " },
            { "         ", "         ", "         ", "  F F F  ", "         ", "    F    ", "         ", "         " },
            { "   SSS   ", "   SSS   ", "         ", " F  F  F ", "         ", "         ", "    F    ", "         " },
            { "   SSS   ", "   SSS   ", "   CCC   ", "C  CCC  C", "   CCC   ", "         ", "         ",
                "    C    " } },
        // Four legs splayed to the corners of a 5x5 pad, then a braced 3x3 lattice around the mast.
        { { "     ", "     ", "     ", " FFF ", " FFF ", " FFF ", "     " },
            { "     ", "     ", "     ", " F F ", "  F  ", " F F ", "     " },
            { "     ", "     ", "     ", " FFF ", " FFF ", " FFF ", "     " },
            { " SSS ", " SSS ", "F   F", "     ", "  F  ", "     ", "F   F" },
            { " SSS ", " SSS ", "CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC" } } };
    // Where the mast runs through each top and foot: its column and row.
    private static final int[][] TOP_MAST = { null, { 1, 0 }, { 2, 2 }, { 1, 1 } };
    private static final int[][] FOOT_MAST = { null, { 3, 3 }, { 4, 3 }, { 2, 4 } };

    private static final int TICKS_PER_SECOND = 20;
    private static final String[] VOLTAGE_NAMES = { "ULV", "LV", "MV", "HV" };

    @SuppressWarnings("unchecked")
    private static final IStructureDefinition<MTEAntenna>[] STRUCTURES = new IStructureDefinition[] { null,
        structure(1), structure(2), structure(3) };

    public static final int FORMAT = 1;

    private final int tier;
    private final LineHatches lineHatches = new LineHatches();
    // BeCom's keys from a newer BeCom: kept as they were, and the antenna left alone.
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

    /** Its item is BeCom's, as its block is. */
    @Override
    public ItemStack getStackForm(long amount) {
        return BeComMachines.stack(this, amount);
    }

    private static IStructureDefinition<MTEAntenna> structure(int tier) {
        Casings casing = Tiers.casing(tier);
        return StructureDefinition.<MTEAntenna>builder()
            .addShape(PIECE_MAST, new String[][] { { "F" } })
            .addShape(PIECE_TOP, transpose(TOPS[tier]))
            .addShape(PIECE_FOOT, transpose(FEET[tier]))
            .addElement('F', ofFrame(Tiers.frame(tier)))
            .addElement('C', casing.asElement())
            .addElement(
                'S',
                buildHatchAdder(MTEAntenna.class).atLeast(Energy, LineHatches.Element.LINE_HATCH)
                    .casingIndex(casing.textureId)
                    .hint(1)
                    .buildAndChain(onElementPass(x -> {}, casing.asElement())))
            .build();
    }

    /** How many times {@code block} appears in a shape. */
    private static int count(String[][] shape, char block) {
        int n = 0;
        for (String[] layer : shape) {
            for (String row : layer) {
                for (int i = 0; i < row.length(); i++) if (row.charAt(i) == block) n++;
            }
        }
        return n;
    }

    @Override
    public NodeKind nodeKind() {
        return NodeKind.antenna(tier);
    }

    /** A router to the buses its Line Hatches join, carrying their calls on by radio. */
    @Override
    public Buses.Kind busKind() {
        return Buses.Kind.ROUTER;
    }

    @Override
    public boolean formed() {
        return mMachine;
    }

    @Override
    public LineHatches lineHatches() {
        return lineHatches;
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
    public boolean getDefaultHasMaintenanceChecks() {
        // No maintenance hatch to fix them at: left on, GT counts all six problems forever and shows HAS PROBLEMS.
        return false;
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
        // Frames below the head sit at -1, -2, ...; the pad's casing under the mast ends the count.
        while (found < t.antennaMastMax() && checkPiece(PIECE_MAST, 0, -(found + 1), 0, null)) found++;
        mast = found;
        if (found < t.antennaMastMin(tier)) {
            errors.add(StructureErrorRegistry.TOO_SHORT_HEIGHT);
            return;
        }
        if (!checkPiece(PIECE_TOP, TOP_MAST[tier][0], -1, TOP_MAST[tier][1], errors)) return;
        if (!checkPiece(PIECE_FOOT, FOOT_MAST[tier][0], footOffset(found), FOOT_MAST[tier][1], errors)) return;
        checkHasEnergyHatch(errors);
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        lineHatches.clear();
    }

    @Override
    protected void onStructureCheckFinished(IGregTechTileEntity tile) {
        super.onStructureCheckFinished(tile);
        lineHatches.finished(tile, mMachine);
    }

    /** How many blocks in front of the mast the shelter's back row is, at the ground. */
    public int shelterAhead() {
        return FOOT_MAST[tier][1] - 1;
    }

    /** How many layers the foot has, the ground's included. */
    public int footLayers() {
        return FEET[tier].length;
    }

    /** Where the foot's top layer is, below the head: its bottom layer is the ground under the mast. */
    private int footOffset(int mast) {
        return -(mast + 2 - FEET[tier].length);
    }

    /** The mast a hologram or the build tool makes: the shortest, plus one per extra item in the stack. */
    private int buildMast(ItemStack trigger) {
        Tunables t = BeComConfig.get();
        int extra = trigger == null ? 0 : Math.max(0, trigger.stackSize - 1);
        return Math.min(t.antennaMastMax(), t.antennaMastMin(tier) + extra);
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {
        int target = buildMast(trigger);
        for (int i = 1; i <= target; i++) buildPiece(PIECE_MAST, trigger, hintsOnly, 0, -i, 0);
        buildPiece(PIECE_TOP, trigger, hintsOnly, TOP_MAST[tier][0], -1, TOP_MAST[tier][1]);
        buildPiece(PIECE_FOOT, trigger, hintsOnly, FOOT_MAST[tier][0], footOffset(target), FOOT_MAST[tier][1]);
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
                PIECE_TOP,
                trigger,
                TOP_MAST[tier][0],
                -1,
                TOP_MAST[tier][1],
                elementBudget - built,
                env,
                false);
            if (placed > 0) built += placed;
        }
        if (built < elementBudget) {
            int placed = survivalBuildPiece(
                PIECE_FOOT,
                trigger,
                FOOT_MAST[tier][0],
                footOffset(target),
                FOOT_MAST[tier][1],
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
     * Where it transmits from: the top of the head, how far below the surface that is, and the top of
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

    /** Runs the structure check now and tells the networks */
    public boolean refreshStructure() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        // Left alone: no node written for it here.
        if (tooNew != null) return false;
        checkStructure(true, tile);
        BeComServer becom = BeComServer.get();
        if (becom != null && tile.isServerSide()) {
            long pos = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
            becom.network.ensureNode(tile.getWorld(), pos, nodeKind(), tile.getOwnerUuid());
            if (!mMachine) becom.antennas.gone(ref());
            registered = true;
        }
        return mMachine;
    }

    private boolean drainPower() {
        return EnergyHatches.drain(
            this,
            BeComConfig.get()
                .antennaPower(tier));
    }

    @Override
    public void onBlockDestroyed() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (becom != null && tile != null && tile.isServerSide()) {
            becom.network
                .removeNode(tile.getWorld(), BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
            becom.antennas.gone(ref());
            lineHatches.dropped(tile);
        }
        super.onBlockDestroyed();
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
            .addInfoAll(
                Tips.wrap(
                    Tips.MACHINE,
                    StatCollector.translateToLocal("becom.machine.antenna.info"),
                    StatCollector.translateToLocalFormatted(
                        "becom.machine.antenna.range",
                        Tips.number(
                            "~" + StatCollector.translateToLocalFormatted(
                                "becom.info.blocks",
                                (int) Math.round(t.rangeToLow(t.antennaBudget(tier))))),
                        "\u00a78"
                            + StatCollector
                                .translateToLocalFormatted("becom.machine.antenna.budget", (int) t.antennaBudget(tier))
                            + "\u00a77"),
                    Tips.power(t.antennaPower(tier), VOLTAGE_NAMES[tier]),
                    Tips.howTo(
                        "becom.machine.antenna.looks." + tier,
                        Tiers.frame(tier)
                            .getLocalizedNameForItem("%material"))))
            .beginVariableStructureBlock(
                FEET[tier][0][0].length(),
                FEET[tier][0][0].length(),
                t.antennaMastMin(tier) + 2,
                t.antennaMastMax() + 2,
                FEET[tier][0].length,
                FEET[tier][0].length,
                false)
            .addController(StatCollector.translateToLocal("becom.machine.antenna.controller"))
            .addCasingInfoRange(
                StatCollector.translateToLocalFormatted(
                    "becom.machine.antenna.mast",
                    Tiers.frame(tier)
                        .getLocalizedNameForItem("%material Frame Box")),
                t.antennaMastMin(tier),
                t.antennaMastMax(),
                false)
            .addCasingInfoExactly(
                StatCollector.translateToLocalFormatted(
                    "becom.machine.antenna.rigging." + tier,
                    Tiers.frame(tier)
                        .getLocalizedNameForItem("%material Frame Box")),
                // The mast's own frames run through the top and every layer of the foot above the ground.
                count(TOPS[tier], 'F') - TOPS[tier].length + count(FEET[tier], 'F') - (FEET[tier].length - 1),
                false)
            .addCasingInfoExactly(
                StatCollector.translateToLocalFormatted(
                    "becom.machine.antenna.pad",
                    Tiers.casing(tier)
                        .getLocalizedName()),
                count(FEET[tier], 'C'),
                false)
            .addCasingInfoExactly(
                StatCollector.translateToLocalFormatted(
                    "becom.machine.antenna.shelter",
                    Tiers.casing(tier)
                        .getLocalizedName()),
                count(FEET[tier], 'S') - 1,
                false)
            .addEnergyHatch(StatCollector.translateToLocal("becom.machine.antenna.hatch"), 1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("becom.hatch.line"),
                StatCollector.translateToLocal("becom.machine.antenna.line_hatch"),
                1)
            .toolTipFinisher();
        return tt;
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
        // Skips GT's multiblock lines: a recipe machine's efficiency and progress, always 0% and Idle on an antenna.
        getMTEWailaBody(stack, tip, accessor, config);
        InfoLines.read(accessor.getNBTData(), tip);
    }

    /**
     * Its lines for WAILA and the scanner, as on the Scanner board: its state, mast, links, calls and who it serves.
     */
    private InfoLines info() {
        InfoLines info = new InfoLines();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tooNew != null) info.problem("becom.info.too_new");
        InfoLines.state(info, mMachine, tile != null && tile.isAllowedToWork(), working);
        info.add("becom.info.blocks", mMachine ? mast : 0)
            .row("becom.info.row.mast");
        BeComServer becom = BeComServer.get();
        if (becom == null || tile == null) return info;
        NodeRef ref = ref();
        int antennas = 0;
        for (AntennaNetwork.LinkKey key : becom.antennas.links(ref.dimension())
            .keySet()) if (key.a() == ref.pos() || key.b() == ref.pos()) antennas++;
        // Its backhaul: the best bus that carries to another working router.
        CableTier backhaul = null;
        for (Buses.Bus bus : becom.network.busesAt(ref.dimension(), ref.pos())) {
            if (!becom.network.carries(ref.dimension(), bus)) continue;
            boolean other = false;
            for (long router : becom.network.routersOn(ref.dimension(), bus)) other |= router != ref.pos();
            if (other && (backhaul == null || bus.tier()
                .compareTo(backhaul) > 0)) backhaul = bus.tier();
        }
        if (backhaul != null) info.add("becom.info.links_backhaul", antennas, InfoLines.shortName(backhaul))
            .row("becom.info.row.links");
        else info.add("becom.info.links_antennas", antennas)
            .row("becom.info.row.links");
        info.add(
            "becom.info.of",
            becom.calls.callsAt(ref.dimension(), ref.pos()),
            BeComConfig.get()
                .antennaCalls(tier))
            .row("becom.info.row.calls");
        UUID owner = owner();
        String name = owner == null ? null : net.minecraftforge.common.UsernameCache.getLastKnownUsername(owner);
        if (locked) info.add("becom.info.serves_team", name == null ? "?" : name)
            .row("becom.info.row.serves");
        else info.add("becom.info.serves_all")
            .row("becom.info.row.serves");
        // Radio alone needs no cable: the buses only once a Line Hatch is part of it.
        if (!lineHatches.hatches()
            .isEmpty()) InfoLines.buses(info, becom, ref.dimension(), ref.pos());
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
