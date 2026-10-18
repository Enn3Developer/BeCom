package com.enn3developer.becom.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.entity.EntityLaunchRocket;
import com.enn3developer.becom.gui.InfoScreen;
import com.enn3developer.becom.gui.OrbitView;
import com.enn3developer.becom.gui.ViewSync;
import com.enn3developer.becom.gui.machine.OrbitUi;
import com.enn3developer.becom.gui.machine.ReadoutUi;
import com.enn3developer.becom.gui.paint.CanvasWidget;
import com.enn3developer.becom.item.BeComItems;
import com.enn3developer.becom.item.Tips;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.CallGraph;
import com.enn3developer.becom.logic.space.Forecast;
import com.enn3developer.becom.logic.space.Gateways;
import com.enn3developer.becom.logic.space.Satellites;
import com.enn3developer.becom.logic.space.SkyStrength;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.quest.QuestTask;
import com.enn3developer.becom.server.BeComServer;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.casing.Casings;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import micdoodle8.mods.galacticraft.core.util.FluidUtil;

/**
 * The Satellite Launch Pad: a late-HV launch complex of clean stainless steel casings
 * and
 * stainless steel frame boxes. A 7 by 7 pad with a flame pit in the middle, the controller on its front edge; a service
 * tower behind it with two swing arms reaching over the pad; lightning masts on its front corners; a propellant tank
 * beside it. Each launch takes a Satellite Rocket from the input bus in the tower's base and rocket fuel from the input
 * hatch in the tank, runs 30 s at 480 EU/t under open sky over the pit, and puts one satellite into orbit over the
 * pad's
 * dimension. The rocket stands in the pit while the launch counts down and lifts off when it ends. Maintenance problems
 * slow launches down.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTELaunchPad extends MTEEnhancedMultiBlockBase<MTELaunchPad> implements ISurvivalConstructable {

    private static final String PIECE_PAD = "pad";
    private static final int TICKS_PER_SECOND = 20;
    // The flame pit: three across, centred this far behind the controller.
    private static final int PIT = 3;
    private static final int PIT_DEPTH = 3;
    // The controller's column, and its layer counted from the top of the shape.
    private static final int CONTROLLER_COLUMN = 6;
    private static final int CONTROLLER_LAYER = 9;
    private static final Casings CASING = Casings.CleanStainlessSteelMachineCasing;
    private static final Materials FRAME = Materials.StainlessSteel;
    // GT's efficiency, out of 10000, loses 1000 per maintenance problem; a launch takes as much longer.
    private static final int FULL_EFFICIENCY = 10_000;
    private static final int EFFICIENCY_PER_PROBLEM = 1_000;
    private static final float LAUNCH_VOLUME = 4.0f;

    // Layers run top to bottom and rows from the controller's front backwards. C is a casing and F a frame box; the
    // casings a hatch may take the place of are B for the tower's base (input bus), T for the tank (input hatch) and P
    // for the pad's front edge (energy, maintenance). o must stay open: the flame pit. A space is anything.
    private static final String[][] SHAPE = {
        // y9
        { "          ", "          ", "          ", "          ", "          ", "          ", "          ",
            "     FFF  ", "     F F  ", "     FFF  " },
        // y8
        { "          ", "          ", "          ", "          ", "          ", "          ", "          ",
            "     F F  ", "          ", "     F F  " },
        // y7
        { "          ", "          ", "          ", "          ", "          ", "      F   ", "      F   ",
            "     FFF  ", "     F F  ", "     FFF  " },
        // y6
        { "   F     F", "          ", "          ", "          ", "          ", "          ", "          ",
            "     F F  ", "          ", "     F F  " },
        // y5
        { "   F     F", "          ", "          ", "          ", "          ", "          ", "          ",
            "     FFF  ", "     F F  ", "     FFF  " },
        // y4
        { "   F     F", "          ", "          ", "          ", "          ", "      F   ", "      F   ",
            "     F F  ", "          ", "     F F  " },
        // y3
        { "   F     F", "          ", "          ", "          ", "          ", "          ", "          ",
            "     FFF  ", "     F F  ", "     FFF  " },
        // y2
        { "   F     F", "          ", "          ", "TT        ", "TT        ", "          ", "          ",
            "     F F  ", "          ", "     F F  " },
        // y1
        { "   F     F", "          ", "          ", "TT        ", "TT        ", "          ", "          ",
            "     FFF  ", "     F F  ", "     FFF  " },
        // y0
        { "   CPP~PPC", "   CCCCCCC", "   CCoooCC", "TT CCoooCC", "TT CCoooCC", "   CCCCCCC", "   CCCCCCC",
            "     BBB  ", "     BBB  ", "     BBB  " },
        // y-1
        { "          ", "          ", "     CCC  ", "     CCC  ", "     CCC  ", "          ", "          ",
            "          ", "          ", "          " } };

    private static final IStructureDefinition<MTELaunchPad> STRUCTURE = StructureDefinition.<MTELaunchPad>builder()
        .addShape(PIECE_PAD, transpose(SHAPE))
        .addElement('C', CASING.asElement())
        .addElement('F', ofFrame(FRAME))
        .addElement('o', isAir())
        .addElement('B', hatches(1, InputBus))
        .addElement('T', hatches(2, InputHatch))
        .addElement('P', hatches(3, Energy, Maintenance))
        .build();

    // The rocket standing in the pit while a launch counts down; null when there's none.
    private EntityLaunchRocket rocket;

    public MTELaunchPad(int id, String name, String regionalName) {
        super(id, name, regionalName);
    }

    public MTELaunchPad(String name) {
        super(name);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTELaunchPad(mName);
    }

    /** Its item is BeCom's, as its block is. */
    @Override
    public ItemStack getStackForm(long amount) {
        return BeComMachines.stack(this, amount);
    }

    /** A casing, or one of {@code kinds} of hatch in its place, shown in the hologram with {@code hint}'s dot. */
    @SafeVarargs
    private static IStructureElement<MTELaunchPad> hatches(int hint, IHatchElement<? super MTELaunchPad>... kinds) {
        return buildHatchAdder(MTELaunchPad.class).atLeast(kinds)
            .casingIndex(CASING.getTextureId())
            .hint(hint)
            .buildAndChain(onElementPass(x -> {}, CASING.asElement()));
    }

    /** How many times {@code block} appears in the complex. */
    private static int count(char block) {
        int n = 0;
        for (String[] layer : SHAPE) {
            for (String row : layer) {
                for (int i = 0; i < row.length(); i++) if (row.charAt(i) == block) n++;
            }
        }
        return n;
    }

    // region Structure

    @Override
    public IStructureDefinition<MTELaunchPad> getStructureDefinition() {
        return STRUCTURE;
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        // Built on the ground: the controller faces sideways from the pad's edge, never turned on its side.
        return (direction, rotation, flip) -> direction.offsetY == 0 && rotation == Rotation.NORMAL
            && flip == Flip.NONE;
    }

    @Override
    public void checkMachine(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors) {
        if (!checkPiece(PIECE_PAD, CONTROLLER_COLUMN, CONTROLLER_LAYER, 0, errors)) return;
        if (mInputBusses.isEmpty()) errors.add(StructureErrors.missingHatch(InputBus));
        if (mInputHatches.isEmpty()) errors.add(StructureErrors.missingHatch(InputHatch));
        if (mMaintenanceHatches.size() != 1) errors.add(StructureErrors.missingHatch(Maintenance));
        checkHasEnergyHatch(errors);
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {
        buildPiece(PIECE_PAD, trigger, hintsOnly, CONTROLLER_COLUMN, CONTROLLER_LAYER, 0);
    }

    @Override
    public int survivalConstruct(ItemStack trigger, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            PIECE_PAD,
            trigger,
            CONTROLLER_COLUMN,
            CONTROLLER_LAYER,
            0,
            elementBudget,
            env,
            false,
            true);
    }

    /**
     * Whether the flame pit sees the sky, in a dimension that has one: the rocket's way up. The tower's
     * swing arms hang over the back of the pad, never over the pit.
     */
    public boolean openSky() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        World world = tile.getWorld();
        if (world.provider.hasNoSky) return false;
        ForgeDirection back = getExtendedFacing().getDirection()
            .getOpposite();
        ForgeDirection left = back.getRotation(ForgeDirection.UP);
        for (int depth = PIT_DEPTH - PIT / 2; depth <= PIT_DEPTH + PIT / 2; depth++) {
            for (int across = -PIT / 2; across <= PIT / 2; across++) {
                int x = tile.getXCoord() + back.offsetX * depth + left.offsetX * across;
                int z = tile.getZCoord() + back.offsetZ * depth + left.offsetZ * across;
                if (!world.canBlockSeeTheSky(x, tile.getYCoord(), z)) return false;
            }
        }
        return true;
    }

    // endregion

    // region Launching

    @Override
    public @NotNull CheckRecipeResult checkProcessing() {
        if (!openSky()) return SimpleCheckRecipeResult.ofFailure("becom_no_sky");
        skyOpen = true;
        Tunables t = BeComConfig.get();
        // Its hatches weighed as GT weighs a recipe's, before anything is taken.
        EnergyHatches.Fit fit = EnergyHatches.fit(this, t.launchPower(), t.launchPower());
        if (fit.problem() == EnergyHatches.Problem.POWER)
            return CheckRecipeResultRegistry.insufficientPower(t.launchPower());
        if (fit.problem() == EnergyHatches.Problem.VOLTAGE)
            return CheckRecipeResultRegistry.insufficientVoltage(t.launchPower());
        ItemStack rocket = null;
        for (ItemStack stack : getStoredInputs()) {
            if (stack != null && stack.getItem() == BeComItems.satelliteRocket) rocket = stack;
        }
        if (rocket == null) return SimpleCheckRecipeResult.ofFailure("becom_no_rocket");
        FluidStack fuel = null;
        for (FluidStack fluid : getStoredFluids()) {
            if (fluid != null && fluid.amount >= t.launchFuel()
                && FluidUtil.testFuel(FluidRegistry.getFluidName(fluid))) fuel = fluid;
        }
        // A server that sets launches to need no fuel launches from an empty hatch.
        if (fuel == null && t.launchFuel() > 0) return SimpleCheckRecipeResult.ofFailure("becom_no_fuel");
        // The very rocket and fuel found: GT takes only stacks equal down to their tags, so a renamed rocket counts.
        ItemStack one = rocket.copy();
        one.stackSize = 1;
        if (!depleteInput(one)) return CheckRecipeResultRegistry.NO_RECIPE;
        if (t.launchFuel() > 0) {
            FluidStack burn = fuel.copy();
            burn.amount = t.launchFuel();
            if (!depleteInput(burn)) return CheckRecipeResultRegistry.NO_RECIPE;
        }
        updateSlots();
        int problems = getIdealStatus() - getRepairStatus();
        mEfficiency = Math.max(EFFICIENCY_PER_PROBLEM, FULL_EFFICIENCY - problems * EFFICIENCY_PER_PROBLEM);
        mEfficiencyIncrease = FULL_EFFICIENCY;
        // Each overclock halves the launch for four times the power, as GT's imperfect overclocks do.
        long ticks = (long) t.launchSeconds() * TICKS_PER_SECOND * FULL_EFFICIENCY / mEfficiency >> fit.overclocks();
        mMaxProgresstime = (int) Math.max(1, ticks);
        mEUt = (int) -fit.power(t.launchPower());
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    // Whether the sky over the pad was open at the last look, taken once a second while a launch runs.
    private boolean skyOpen = true;

    /** A launch holds while anything covers the pad, drawing no power, and goes on once it's clear. */
    @Override
    public boolean onRunningTick(ItemStack stack) {
        if (getBaseMetaTileEntity().getTimer() % TICKS_PER_SECOND == 0) skyOpen = openSky();
        if (!skyOpen) return false;
        return super.onRunningTick(stack);
    }

    /** Whether a launch is under way but on hold, with something covering the pad. */
    public boolean held() {
        return mMaxProgresstime > 0 && !skyOpen;
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        super.onPostTick(tile, tick);
        if (!tile.isServerSide()) return;
        // GT lights a machine with work under way, and its screen calls it running: one on hold is neither.
        if (held()) tile.setActive(false);
        showRocket(tile);
    }

    /**
     * Stands a rocket in the pit while a launch counts down, held or not, and takes it away when none does. A rocket
     * isn't saved, so after a restart, or once its chunk was unloaded, the pad stands a new one.
     */
    private void showRocket(IGregTechTileEntity tile) {
        boolean counting = mMachine && mMaxProgresstime > 0;
        World world = tile.getWorld();
        if (!counting) {
            removeRocket();
            return;
        }
        if (rocket != null && !rocket.isDead && world.getEntityByID(rocket.getEntityId()) == rocket) return;
        ForgeDirection back = getExtendedFacing().getDirection()
            .getOpposite();
        rocket = new EntityLaunchRocket(
            world,
            tile.getXCoord() + back.offsetX * PIT_DEPTH + 0.5,
            tile.getYCoord(),
            tile.getZCoord() + back.offsetZ * PIT_DEPTH + 0.5);
        world.spawnEntityInWorld(rocket);
    }

    private void removeRocket() {
        if (rocket != null) rocket.setDead();
        rocket = null;
    }

    @Override
    public void onBlockDestroyed() {
        removeRocket();
        super.onBlockDestroyed();
    }

    // A launch draws its power whatever the maintenance; its problems cost time instead.
    @Override
    protected long getActualEnergyUsage() {
        return -mEUt;
    }

    /** The rocket has gone up: one more satellite over this dimension, and the owner's quest tasks. */
    @Override
    protected void outputAfterRecipe() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (becom == null || !tile.isServerSide()) return;
        int dimension = tile.getWorld().provider.dimensionId;
        becom.space.launch(dimension);
        if (rocket != null) rocket.liftOff();
        // Gone up: the next launch stands a rocket of its own.
        rocket = null;
        tile.getWorld()
            .playSoundEffect(
                tile.getXCoord() + 0.5,
                tile.getYCoord() + 1,
                tile.getZCoord() + 0.5,
                BeCom.MODID + ":rocket.launch",
                LAUNCH_VOLUME,
                1.0f);
        if (tile.getOwnerUuid() == null) return;
        BeComQuests.complete(QuestTask.FIRST_SATELLITE, List.of(tile.getOwnerUuid()));
        if (becom.space.count(dimension) >= becom.tunables.satellitesLow()) {
            BeComQuests.complete(QuestTask.FIVE_SATELLITES, List.of(tile.getOwnerUuid()));
        }
    }

    // endregion

    // region Screen

    /** The Satellite Rockets in its input buses, waiting to launch. */
    public int rocketsWaiting() {
        int rockets = 0;
        for (ItemStack stack : getStoredInputs()) {
            if (stack != null && stack.getItem() == BeComItems.satelliteRocket) rockets += stack.stackSize;
        }
        return rockets;
    }

    /**
     * Its screen, as the Machines board lays it out: the launch on top with its progress, the rockets waiting and the
     * fuel, then each dimension's satellites, how many calls they carry and when the next falls. Built on the server;
     * each client translates it.
     */
    public InfoLines readout() {
        return readout(true);
    }

    private InfoLines readout(boolean skies) {
        InfoLines info = new InfoLines();
        BeComServer becom = BeComServer.get();
        if (becom == null) return info;
        Tunables t = becom.tunables;
        int rockets = rocketsWaiting();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (!mMachine) info.problem("becom.info.state_unformed")
            .status();
        else if (tile != null && !tile.isAllowedToWork()) info.problem("becom.info.state_off")
            .status();
        else if (held()) info.problem("becom.launch_pad.status_held")
            .status();
        else if (mMaxProgresstime > 0) {
            info.add("becom.launch_pad.status_launching")
                .status();
            info.add("becom.launch_pad.seconds", mProgresstime / TICKS_PER_SECOND, mMaxProgresstime / TICKS_PER_SECOND)
                .right();
            info.add("")
                .bar("", mProgresstime / (double) mMaxProgresstime);
        } else info.add("becom.launch_pad.status_ready")
            .status();
        info.add("becom.launch_pad.rockets", rockets)
            .row("becom.info.row.waiting")
            .brief();
        long fuel = 0;
        for (FluidStack fluid : getStoredFluids()) {
            if (fluid != null && FluidUtil.testFuel(FluidRegistry.getFluidName(fluid))) fuel += fluid.amount;
        }
        if (t.launchFuel() > 0) {
            info.add("becom.launch_pad.litres", fuel)
                .row("becom.info.row.fuel")
                .brief();
            info.add("becom.launch_pad.launches", fuel / t.launchFuel())
                .sub();
        }
        if (!skies) {
            problems(info, rockets, fuel, t);
            return info;
        }
        info.add("becom.launch_pad.in_orbit")
            .section();
        SkyStrength sky = SkyStrength.of(t);
        for (Map.Entry<Integer, Integer> entry : becom.space.counts()
            .entrySet()) {
            int dimension = entry.getKey();
            int satellites = entry.getValue();
            SignalLevel level = sky.enough(satellites) ? t.levelScale()
                .levelOf(sky.snr(satellites)) : SignalLevel.NONE;
            info.add("becom.launch_pad.count", satellites)
                .value(becom.space.dimensionName(dimension), level);
            if (sky.enough(satellites)) {
                int[] calls = becom.calls.skyCalls(dimension);
                info.add(
                    "becom.launch_pad.orbit_calls",
                    calls[0],
                    calls[1],
                    String.format(
                        Locale.ROOT,
                        "%.0f",
                        Math.ceil(becom.space.untilNextDown(dimension) / (double) Satellites.DAY_TICKS)))
                    .sub();
            } else info.add("becom.launch_pad.dish_needs", t.satellitesLow())
                .sub();
        }
        if (becom.space.counts()
            .isEmpty()) info.add("becom.launch_pad.orbit_none");
        problems(info, rockets, fuel, t);
        return info;
    }

    /** What keeps it from launching, when it isn't. */
    private void problems(InfoLines info, int rockets, long fuel, Tunables t) {
        if (!mMachine || mMaxProgresstime != 0) return;
        if (!openSky()) info.problem("becom.info.no_sky");
        if (rockets == 0) info.warn("becom.launch_pad.no_rocket");
        else if (t.launchFuel() > 0 && fuel < t.launchFuel()) info.warn("becom.launch_pad.no_fuel", t.launchFuel());
    }

    /** The Orbit page over its GT screen; its Launches tab closes it. */
    private ModularPanel orbitPanel(PanelSyncManager sync, IPanelHandler handler) {
        ViewSync<OrbitView> view = new ViewSync<>(this::orbitView, OrbitView::write, OrbitView::read, OrbitView.EMPTY);
        sync.syncValue("becomOrbitView", view);
        CanvasWidget widget = new CanvasWidget(
            new OrbitUi(view::view, handler::closePanel),
            OrbitUi.scale(BeCom.proxy.guiHeight()));
        return new ModularPanel("becom_orbit").size(widget.width(), widget.height())
            .background(IDrawable.EMPTY)
            .child(widget);
    }

    /**
     * Its Orbit page, as the Orbit board lays it out: every sky with satellites or a receiver under it, the pad's own
     * first, and how the pad's sky thins out if nothing more goes up. Built on the server.
     */
    public OrbitView orbitView() {
        BeComServer becom = BeComServer.get();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (becom == null || tile == null) return OrbitView.EMPTY;
        Tunables t = becom.tunables;
        int here = tile.getWorld().provider.dimensionId;
        SkyStrength strength = SkyStrength.of(t);
        Map<Integer, Integer> dishes = new HashMap<>();
        for (Gateways.Dish dish : becom.network.dishes()) dishes.merge(dish.dimension(), 1, Integer::sum);
        Map<Integer, Integer> receivers = new TreeMap<>();
        for (Gateways.Receiver receiver : becom.network.receivers()) {
            if (receiver.dimension() != Gateways.OVERWORLD) receivers.merge(receiver.dimension(), 1, Integer::sum);
        }
        Map<Integer, Integer> underSky = new HashMap<>();
        for (int number : becom.carried.working()) {
            for (CallGraph.Entry entry : becom.carried.serviceOf(number)
                .entries()) {
                if (entry.vertex() instanceof CallGraph.SkyVertex sky) underSky.merge(sky.dimension(), 1, Integer::sum);
            }
        }
        Set<Integer> dimensions = new TreeSet<>(
            becom.space.counts()
                .keySet());
        dimensions.addAll(receivers.keySet());
        dimensions.addAll(dishes.keySet());
        List<Integer> order = new ArrayList<>(dimensions);
        order.remove((Integer) here);
        order.sort((a, b) -> Integer.compare(becom.space.count(b), becom.space.count(a)));
        order.add(0, here);
        String overworld = becom.space.dimensionName(Gateways.OVERWORLD);
        List<OrbitView.Sky> skies = new ArrayList<>();
        for (int dimension : order) {
            int up = becom.space.count(dimension);
            SignalLevel level = strength.enough(up) ? t.levelScale()
                .levelOf(strength.snr(up)) : SignalLevel.NONE;
            int[] calls = becom.calls.skyCalls(dimension);
            List<OrbitView.Uplink> uplinks = new ArrayList<>();
            if (dimension == Gateways.OVERWORLD) {
                for (Map.Entry<Integer, Integer> receiver : receivers.entrySet()) {
                    uplinks
                        .add(new OrbitView.Uplink(becom.space.dimensionName(receiver.getKey()), receiver.getValue()));
                }
            }
            skies.add(
                new OrbitView.Sky(
                    becom.space.dimensionName(dimension),
                    up,
                    level,
                    t.satellitesLow(),
                    calls[0],
                    calls[1],
                    dishes.getOrDefault(dimension, 0),
                    uplinks,
                    underSky.getOrDefault(dimension, 0),
                    receivers.containsKey(dimension) ? overworld : ""));
        }
        return new OrbitView(
            skies,
            becom.space.dimensionName(here),
            Forecast.of(t, becom.space.count(here), becom.space.ticksLeft(here), Forecast.DAYS),
            t.satelliteCalls(),
            (int) Math.round(t.satelliteLifetimeDays()));
    }

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
                ViewSync<String> text = InfoScreen.lines(MTELaunchPad.this::readout);
                sync.syncValue("becomPad", text);
                IPanelHandler orbit = sync
                    .panel("becomOrbit", (panelSync, handler) -> orbitPanel(panelSync, handler), true);
                return ReadoutUi.intoTerminal(
                    super.createTerminalTextWidget(sync, parent),
                    text::view,
                    getTerminalWidgetWidth() - 10,
                    getTerminalWidgetHeight() - 8,
                    orbit::openPanel);
            }
        };
    }

    // endregion

    // region Looks and info

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int colorIndex,
        boolean active, boolean redstoneLevel) {
        ITexture casing = Textures.BlockIcons.getCasingTextureForId(CASING.getTextureId());
        if (side != facing) return new ITexture[] { casing };
        ITexture front = TextureFactory.builder()
            .addIcon(BeComTextures.LAUNCH_PAD)
            .extFacing()
            .build();
        if (!active) return new ITexture[] { casing, front };
        return new ITexture[] { casing, front, TextureFactory.builder()
            .addIcon(BeComTextures.LAUNCH_PAD_GLOW)
            .extFacing()
            .glow()
            .build() };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        Tunables t = BeComConfig.get();
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("becom.machine.launch_pad.type"))
            .addInfoAll(
                Tips.wrap(
                    Tips.MACHINE,
                    StatCollector.translateToLocal("becom.machine.launch_pad.info"),
                    StatCollector.translateToLocalFormatted(
                        "becom.machine.launch_pad.launch",
                        t.launchSeconds(),
                        t.launchPower(),
                        t.launchFuel()),
                    StatCollector.translateToLocal("becom.machine.launch_pad.sky"),
                    StatCollector.translateToLocal("becom.machine.launch_pad.overclock")))
            .beginStructureBlock(SHAPE[0][0].length(), SHAPE.length, SHAPE[0].length, false)
            .addController(StatCollector.translateToLocal("becom.machine.launch_pad.controller"))
            .addCasingInfoMin(CASING.getLocalizedName(), count('C') + count('B') + count('T') + count('P') - 4, false)
            .addCasingInfoExactly(FRAME.getLocalizedNameForItem("%material Frame Box"), count('F'), false)
            .addInputBus(StatCollector.translateToLocal("becom.machine.launch_pad.bus"), 1)
            .addInputHatch(StatCollector.translateToLocal("becom.machine.launch_pad.tank"), 2)
            .addEnergyHatch(StatCollector.translateToLocal("becom.machine.launch_pad.front"), 3)
            .addMaintenanceHatch(StatCollector.translateToLocal("becom.machine.launch_pad.front"), 3)
            .toolTipFinisher();
        return tt;
    }

    private final WailaInfo waila = new WailaInfo(this::info);

    @Override
    public String[] getInfoData() {
        List<String> lines = new ArrayList<>(java.util.Arrays.asList(super.getInfoData()));
        lines.addAll(info().translate());
        return lines.toArray(new String[0]);
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

    /** Its lines for WAILA and the scanner: its screen's, without the skies. */
    private InfoLines info() {
        return readout(false);
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public boolean supportsVoidProtection() {
        return false;
    }

    // endregion
}
