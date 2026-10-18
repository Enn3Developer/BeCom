package com.enn3developer.becom.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.List;
import java.util.Locale;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.gui.InfoScreen;
import com.enn3developer.becom.gui.ViewSync;
import com.enn3developer.becom.gui.machine.ReadoutUi;
import com.enn3developer.becom.item.Tips;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.Buses;
import com.enn3developer.becom.logic.net.NodeKind;
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
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.ErrorType;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.structure.error.TranslatableText;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * The Satellite Dish: a late-HV ground station under open sky. A stepped bowl of clean
 * stainless steel casings faces straight up, with a feed horn held over it by four struts of stainless steel frame
 * boxes, on a pedestal; the controller stands in a plinth at the foot with its energy, maintenance and Line Hatches.
 * Cabled from its Line Hatch to a router, it joins that router's network to the satellites over its dimension: to
 * every other dish there and, in the Overworld, to every planet's uplink. Rain at the dish weakens that
 * hop, and so does each maintenance problem, since it has no process to slow down.
 * <p>
 * Like the antennas, it isn't a recipe machine: it checks its structure on GT's schedule, drains its power each tick
 * and tells the network. A missing tick of power means offline, never a shutdown to restart by hand.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTESatelliteDish extends MTEEnhancedMultiBlockBase<MTESatelliteDish>
    implements ISurvivalConstructable, NodeHost, LineHatchHost {

    private static final String PIECE_DISH = "dish";
    private static final int TICKS_PER_SECOND = 20;
    // An HV machine; its energy hatches overclock it from there.
    private static final int TIER = 3;
    // The bowl's rim is this wide; the whole dish stands this high, plinth to feed horn.
    private static final int SIDE = 7;
    private static final int HEIGHT = 9;
    // The controller stands at the front of the plinth, one block in front of the dish's centre, at the bottom.
    private static final int CONTROLLER_ROW = 2;
    private static final Casings CASING = Casings.CleanStainlessSteelMachineCasing;
    private static final Materials FRAME = Materials.StainlessSteel;

    // Layers run top to bottom and rows from the controller's front backwards. C is a casing, F a frame box, H a plinth
    // casing an energy, maintenance or Line Hatch may take the place of, and a space anything.
    private static final String[][] SHAPE = {
        { "       ", "       ", "       ", "   C   ", "       ", "       ", "       " },
        { "       ", "       ", "   F   ", "  F F  ", "   F   ", "       ", "       " },
        { "       ", "   F   ", "       ", " F   F ", "       ", "   F   ", "       " },
        { " CCCCC ", "C     C", "C     C", "C     C", "C     C", "C     C", " CCCCC " },
        { "       ", " CCCCC ", " C   C ", " C   C ", " C   C ", " CCCCC ", "       " },
        { "       ", "       ", "  CCC  ", "  CCC  ", "  CCC  ", "       ", "       " },
        { "       ", "       ", "       ", "   F   ", "       ", "       ", "       " },
        { "       ", "       ", "       ", "   F   ", "       ", "       ", "       " },
        { "       ", "       ", "  H~H  ", "  HHH  ", "  HHH  ", "       ", "       " } };

    private static final IStructureDefinition<MTESatelliteDish> STRUCTURE = StructureDefinition
        .<MTESatelliteDish>builder()
        .addShape(PIECE_DISH, transpose(SHAPE))
        .addElement('C', CASING.asElement())
        .addElement(
            'H',
            buildHatchAdder(MTESatelliteDish.class).atLeast(Energy, Maintenance, LineHatches.Element.LINE_HATCH)
                .casingIndex(CASING.getTextureId())
                .hint(1)
                .buildAndChain(onElementPass(x -> {}, CASING.asElement())))
        .addElement('F', ofFrame(FRAME))
        .build();

    /** How many times {@code block} appears in the dish. */
    private static int count(char block) {
        int n = 0;
        for (String[] layer : SHAPE) {
            for (String row : layer) {
                for (int i = 0; i < row.length(); i++) if (row.charAt(i) == block) n++;
            }
        }
        return n;
    }

    private final LineHatches lineHatches = new LineHatches();
    private boolean registered;
    // Tiers its energy hatches run it above HV, as its last structure check found them.
    private int overclocks;
    private boolean working;
    private boolean sky;
    private boolean rains;

    public MTESatelliteDish(int id, String name, String regionalName) {
        super(id, name, regionalName);
    }

    public MTESatelliteDish(String name) {
        super(name);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTESatelliteDish(mName);
    }

    /** Its item is BeCom's, as its block is. */
    @Override
    public ItemStack getStackForm(long amount) {
        return BeComMachines.stack(this, amount);
    }

    /** A gateway to the bus its Line Hatch joins. */
    @Override
    public Buses.Kind busKind() {
        return Buses.Kind.GATEWAY;
    }

    @Override
    public boolean formed() {
        return mMachine;
    }

    @Override
    public LineHatches lineHatches() {
        return lineHatches;
    }

    @Override
    public NodeKind nodeKind() {
        return NodeKind.SATELLITE_DISH;
    }

    public boolean isWorking() {
        return working;
    }

    private long pos() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        return BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
    }

    // region Structure

    @Override
    public IStructureDefinition<MTESatelliteDish> getStructureDefinition() {
        return STRUCTURE;
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        // The dish faces the sky: the controller faces sideways from the plinth, never turned on its side.
        return (direction, rotation, flip) -> direction.offsetY == 0 && rotation == Rotation.NORMAL
            && flip == Flip.NONE;
    }

    @Override
    public void checkMachine(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors) {
        overclocks = 0;
        if (!checkPiece(PIECE_DISH, SIDE / 2, HEIGHT - 1, CONTROLLER_ROW, errors)) return;
        if (mMaintenanceHatches.size() != 1) errors.add(StructureErrors.missingHatch(Maintenance));
        checkHasEnergyHatch(errors);
        EnergyHatches.checkTier(this, TIER, errors);
        overclocks = EnergyHatches.overclocks(this, TIER);
        // A gateway on two buses would carry calls between them like a router: it takes a single Line Hatch.
        int lines = lineHatches.count();
        if (lines != 1) errors.add(
            StructureErrors.hatchCount(
                lines == 0 ? ErrorType.TOO_FEW : ErrorType.TOO_MANY,
                TranslatableText.lang(LineHatches.Element.LINE_HATCH.getDescriptionLangKey()),
                lines,
                1));
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

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {
        buildPiece(PIECE_DISH, trigger, hintsOnly, SIDE / 2, HEIGHT - 1, CONTROLLER_ROW);
    }

    @Override
    public int survivalConstruct(ItemStack trigger, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            PIECE_DISH,
            trigger,
            SIDE / 2,
            HEIGHT - 1,
            CONTROLLER_ROW,
            elementBudget,
            env,
            false,
            true);
    }

    /** Whether the whole dish sees the sky over its feed horn, in a dimension that has one. */
    public boolean openSky() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        World world = tile.getWorld();
        if (world.provider.hasNoSky) return false;
        ForgeDirection back = getExtendedFacing().getDirection()
            .getOpposite();
        ForgeDirection left = back.getRotation(ForgeDirection.UP);
        int above = tile.getYCoord() + HEIGHT;
        // The rim's rows run from its front edge, in front of the controller, to its back edge.
        for (int depth = -CONTROLLER_ROW; depth < SIDE - CONTROLLER_ROW; depth++) {
            for (int across = -SIDE / 2; across <= SIDE / 2; across++) {
                int x = tile.getXCoord() + back.offsetX * depth + left.offsetX * across;
                int z = tile.getZCoord() + back.offsetZ * depth + left.offsetZ * across;
                if (!world.canBlockSeeTheSky(x, above, z)) return false;
            }
        }
        return true;
    }

    /** Runs the structure check now and tells the network */
    public boolean refreshStructure() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        // Left alone: no node, no formed state, no bridges written for it here.
        checkStructure(true, tile);
        BeComServer becom = BeComServer.get();
        if (becom != null && tile.isServerSide()) {
            World world = tile.getWorld();
            becom.network.ensureNode(world, pos(), nodeKind(), tile.getOwnerUuid());
            registered = true;
            sky = mMachine && openSky();
            rains = BeComServer.rains(world, tile.getXCoord(), tile.getZCoord());
        }
        return mMachine;
    }

    // endregion

    // region Ticking

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        if (!tile.isServerSide()) return;
        BeComServer becom = BeComServer.get();
        if (becom == null) return;
        World world = tile.getWorld();
        if (!registered) {
            becom.network.ensureNode(world, pos(), nodeKind(), tile.getOwnerUuid());
            registered = true;
        }
        if (mUpdated) {
            if (mUpdate <= 0) mUpdate = 50;
            mUpdated = false;
        }
        if (--mUpdate == 0 || --mStartUpCheck == 0 || tick % (TICKS_PER_SECOND * 5) == 0) refreshStructure();
        // Blocks placed over the dish take it down within a second.
        if (mMachine && tick % TICKS_PER_SECOND == 0) sky = openSky();
        // The maintenance hatch's tools fix problems the way they do on any GT machine.
        if (mMachine) checkMaintenance();
        working = mMachine && sky && tile.isAllowedToWork() && drainPower() && doRandomMaintenanceDamage();
        if (working) {
            becom.network.heartbeat(world, pos());
            becom.network.overclocked(world, pos(), overclocks);
            becom.network.dish(world, pos(), loss());
        } else {
            becom.network.dishOff(world, pos());
        }
        tile.setActive(working);
    }

    /** Maintenance problems the dish has, of GT's six. */
    public int problems() {
        return getIdealStatus() - getRepairStatus();
    }

    /** What its hop up to the satellites loses: rain at the dish and its maintenance problems. */
    public double loss() {
        BeComServer becom = BeComServer.get();
        double weather = becom != null && rains
            ? becom.space.weather(getBaseMetaTileEntity().getWorld().provider.dimensionId)
            : 0;
        return weather + problems() * BeComConfig.get()
            .dishMaintenanceLoss();
    }

    private boolean drainPower() {
        return EnergyHatches.drain(
            this,
            EnergyHatches.power(
                BeComConfig.get()
                    .dishPower(),
                overclocks));
    }

    /** The calls it carries at once, doubled for each tier its energy hatches run it above HV. */
    public int calls() {
        return BeComConfig.get()
            .dishCalls() << overclocks;
    }

    @Override
    public void onBlockDestroyed() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (becom != null && tile != null && tile.isServerSide()) {
            becom.network.removeNode(tile.getWorld(), pos());
            lineHatches.dropped(tile);
        }
        super.onBlockDestroyed();
    }

    // endregion

    // region Screen

    /** Its hop now, for its screen, WAILA and the scanner; on the server. */
    private Hop view() {
        BeComServer becom = BeComServer.get();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (becom == null || tile == null) return new Hop(false, 0, false, false, 0, 0, SignalLevel.NONE, 0, 0);
        int dimension = tile.getWorld().provider.dimensionId;
        Tunables t = becom.tunables;
        int satellites = becom.space.count(dimension);
        double snr = becom.space.sky()
            .snr(satellites) - loss();
        return new Hop(
            mMachine,
            satellites,
            sky,
            becom.space.skyHops(dimension),
            t.satellitesLow(),
            snr,
            t.levelScale()
                .levelOf(snr),
            problems(),
            t.dishMaintenanceLoss());
    }

    /**
     * Its hop as its screen reads it: whether it's formed, the satellites over this dimension, whether open sky is
     * over the whole dish and there are satellites enough, how many that takes, the hop's SNR and level, and its
     * maintenance problems and what each costs.
     */
    private record Hop(boolean formed, int satellites, boolean sky, boolean enough, int satellitesLow, double snr,
        SignalLevel level, int problems, double problemLoss) {}

    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new MTEMultiBlockBaseGui<>(this) {

            @Override
            protected int getBasePanelWidth() {
                return super.getBasePanelWidth() + ReadoutUi.TERMINAL_EXTRA;
            }

            @Override
            protected int getTerminalRowWidth() {
                return super.getTerminalRowWidth() + ReadoutUi.TERMINAL_EXTRA;
            }

            @Override
            protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager sync, ModularPanel parent) {
                ViewSync<String> text = InfoScreen.lines(MTESatelliteDish.this::readout);
                sync.syncValue("becomDish", text);
                return ReadoutUi.intoTerminal(
                    super.createTerminalTextWidget(sync, parent),
                    text::view,
                    getTerminalWidgetWidth() - 10,
                    getTerminalWidgetHeight() - 8,
                    null);
            }
        };
    }

    /**
     * Its screen, as the Machines board lays it out: the hop on top with its calls, the satellites overhead, the sky,
     * its maintenance and its backhaul, then what's wrong. Built on the server; each client translates it.
     */
    private InfoLines readout() {
        InfoLines info = new InfoLines();
        BeComServer becom = BeComServer.get();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (becom == null || tile == null) return info;
        Hop view = view();
        int dimension = tile.getWorld().provider.dimensionId;
        Tunables t = becom.tunables;
        if (!view.formed()) info.problem("becom.info.state_unformed")
            .status();
        else if (!tile.isAllowedToWork()) info.problem("becom.info.state_off")
            .status();
        else if (!view.sky()) info.problem("becom.dish.status_no_sky")
            .status();
        else if (!view.enough()) info.problem("becom.dish.status_no_hop")
            .status();
        else if (!working) info.problem("becom.info.state_no_power")
            .status();
        else info
            .add(
                "becom.dish.hop_status",
                String.format(Locale.ROOT, "%.0f", view.snr()),
                new InfoLines.Key(
                    "becom.level." + view.level()
                        .name()
                        .toLowerCase(Locale.ROOT)))
            .status(view.level());
        int calls = becom.calls.callsAt(dimension, pos());
        info.add("becom.dish.calls_right", calls, calls())
            .right();
        if (calls >= calls()) info.warn("becom.info.calls_full", calls());
        InfoLines.overclock(info, TIER, overclocks, 2);
        int[] sky = becom.calls.skyCalls(dimension);
        if (sky[1] > 0) info.add("becom.dish.overhead_calls", view.satellites(), sky[0], sky[1])
            .row("becom.info.row.satellites")
            .brief();
        else info.add("becom.dish.overhead", view.satellites())
            .row("becom.info.row.satellites")
            .brief();
        if (view.sky()) info.add("becom.dish.sky_clear")
            .good("becom.info.row.sky");
        else info.problem("becom.dish.sky_covered")
            .row("becom.info.row.sky");
        if (view.problems() > 0) info
            .problem(
                "becom.dish.problems_short",
                view.problems(),
                String.format(Locale.ROOT, "%.0f", view.problems() * view.problemLoss()))
            .row("becom.info.row.maintenance");
        else info.add("becom.dish.no_problems")
            .good("becom.info.row.maintenance");
        InfoLines.backhaul(info, becom, dimension, pos(), "becom.info.row.backhaul", InfoLines.Backhaul.CABLE);
        if (view.formed() && view.sky() && !view.enough()) {
            info.problem("becom.dish.too_few_short", view.satellites(), view.satellitesLow());
        }
        return info;
    }

    // endregion

    // region Looks and info

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int colorIndex,
        boolean active, boolean redstoneLevel) {
        ITexture casing = Textures.BlockIcons.getCasingTextureForId(CASING.getTextureId());
        if (side != facing) return new ITexture[] { casing };
        ITexture front = TextureFactory.builder()
            .addIcon(BeComTextures.SATELLITE_DISH)
            .extFacing()
            .build();
        if (!active) return new ITexture[] { casing, front };
        return new ITexture[] { casing, front, TextureFactory.builder()
            .addIcon(BeComTextures.SATELLITE_DISH_GLOW)
            .extFacing()
            .glow()
            .build() };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        Tunables t = BeComConfig.get();
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("becom.machine.satellite_dish.type"))
            .addInfoAll(
                Tips.wrap(
                    Tips.MACHINE,
                    StatCollector.translateToLocal("becom.machine.satellite_dish.info"),
                    StatCollector.translateToLocalFormatted("becom.machine.satellite_dish.link", t.satellitesLow()),
                    StatCollector.translateToLocalFormatted(
                        "becom.machine.satellite_dish.maintenance",
                        String.format(Locale.ROOT, "%.0f", t.dishMaintenanceLoss())),
                    Tips.power(t.dishPower(), "HV"),
                    StatCollector.translateToLocal("becom.machine.overclock")))
            .beginStructureBlock(SIDE, HEIGHT, SIDE, false)
            .addController(StatCollector.translateToLocal("becom.machine.satellite_dish.controller"))
            .addCasingInfoExactly(
                StatCollector.translateToLocalFormatted("becom.machine.satellite_dish.bowl", CASING.getLocalizedName()),
                count('C'),
                false)
            .addCasingInfoMin(
                StatCollector
                    .translateToLocalFormatted("becom.machine.satellite_dish.plinth", CASING.getLocalizedName()),
                count('H') - 2,
                false)
            .addCasingInfoExactly(
                StatCollector.translateToLocalFormatted(
                    "becom.machine.satellite_dish.frames",
                    FRAME.getLocalizedNameForItem("%material Frame Box")),
                count('F'),
                false)
            .addEnergyHatch(StatCollector.translateToLocal("becom.machine.satellite_dish.hatch"), 1)
            .addMaintenanceHatch(StatCollector.translateToLocal("becom.machine.satellite_dish.hatch"), 1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("becom.hatch.line"),
                StatCollector.translateToLocal("becom.machine.satellite_dish.line_hatch"),
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
        super.getWailaBody(stack, tip, accessor, config);
        InfoLines.read(accessor.getNBTData(), tip);
    }

    private InfoLines info() {
        // GT's scanner reads on the server and sends its text as it is; WAILA translates on the client.
        InfoLines info = readout();
        return info;
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public boolean supportsVoidProtection() {
        return false;
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        super.saveNBTData(nbt);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
    }

    // endregion
}
