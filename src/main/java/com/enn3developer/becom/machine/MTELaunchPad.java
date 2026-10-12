package com.enn3developer.becom.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.item.BeComItems;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.space.Satellites;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.quest.QuestTask;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.SpaceService;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.casing.Casings;
import gregtech.api.enums.Textures;
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
import micdoodle8.mods.galacticraft.core.util.FluidUtil;

/**
 * The Satellite Launch Pad (DESIGN.md 9.1, 3.2, D16, D40): a late-HV floor of 5 by 5 clean stainless steel casings
 * under open sky, with the controller on one edge. Each launch takes a Satellite Rocket from its input bus and rocket
 * fuel from its input hatch, runs 30 s at 480 EU/t, and puts one satellite into orbit over the pad's dimension.
 * Maintenance problems slow launches down.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTELaunchPad extends MTEEnhancedMultiBlockBase<MTELaunchPad> implements ISurvivalConstructable {

    private static final String PIECE_PAD = "pad";
    private static final int TICKS_PER_SECOND = 20;
    private static final int SIDE = 5;
    private static final Casings CASING = Casings.CleanStainlessSteelMachineCasing;
    // GT's efficiency, out of 10000, loses 1000 per maintenance problem; a launch takes as much longer.
    private static final int FULL_EFFICIENCY = 10_000;
    private static final int EFFICIENCY_PER_PROBLEM = 1_000;
    private static final float LAUNCH_VOLUME = 4.0f;
    // What the screen shows of each dimension's satellites: name, count and ticks to the next fall, one per line.
    private static final String FIELD = "\t";
    private static final String LINE = "\n";

    private static final IStructureDefinition<MTELaunchPad> STRUCTURE = StructureDefinition.<MTELaunchPad>builder()
        .addShape(PIECE_PAD, transpose(new String[][] { { "CC~CC", "CCCCC", "CCCCC", "CCCCC", "CCCCC" } }))
        .addElement(
            'C',
            buildHatchAdder(MTELaunchPad.class).atLeast(InputBus, InputHatch, Energy, Maintenance)
                .casingIndex(CASING.getTextureId())
                .hint(1)
                .buildAndChain(onElementPass(x -> {}, CASING.asElement())))
        .build();

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

    // region Structure

    @Override
    public IStructureDefinition<MTELaunchPad> getStructureDefinition() {
        return STRUCTURE;
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        // A floor under open sky: the controller faces sideways from the pad's edge, never turned on its side.
        return (direction, rotation, flip) -> direction.offsetY == 0 && rotation == Rotation.NORMAL
            && flip == Flip.NONE;
    }

    @Override
    public void checkMachine(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors) {
        if (!checkPiece(PIECE_PAD, SIDE / 2, 0, 0, errors)) return;
        if (mInputBusses.isEmpty()) errors.add(StructureErrors.missingHatch(InputBus));
        if (mInputHatches.isEmpty()) errors.add(StructureErrors.missingHatch(InputHatch));
        if (mMaintenanceHatches.size() != 1) errors.add(StructureErrors.missingHatch(Maintenance));
        checkHasEnergyHatch(errors);
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {
        buildPiece(PIECE_PAD, trigger, hintsOnly, SIDE / 2, 0, 0);
    }

    @Override
    public int survivalConstruct(ItemStack trigger, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(PIECE_PAD, trigger, SIDE / 2, 0, 0, elementBudget, env, false, true);
    }

    /** Whether every block of the pad sees the sky, in a dimension that has one (DESIGN.md 9.1). */
    public boolean openSky() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        World world = tile.getWorld();
        if (world.provider.hasNoSky) return false;
        ForgeDirection back = getExtendedFacing().getDirection()
            .getOpposite();
        ForgeDirection left = back.getRotation(ForgeDirection.UP);
        for (int depth = 0; depth < SIDE; depth++) {
            for (int across = -SIDE / 2; across <= SIDE / 2; across++) {
                int x = tile.getXCoord() + back.offsetX * depth + left.offsetX * across;
                int z = tile.getZCoord() + back.offsetZ * depth + left.offsetZ * across;
                if (!world.canBlockSeeTheSky(x, tile.getYCoord() + 1, z)) return false;
            }
        }
        return true;
    }

    // endregion

    // region Launching

    @Override
    public @NotNull CheckRecipeResult checkProcessing() {
        if (!openSky()) return SimpleCheckRecipeResult.ofFailure("becom_no_sky");
        Tunables t = BeComConfig.get();
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
        if (fuel == null) return SimpleCheckRecipeResult.ofFailure("becom_no_fuel");
        if (!depleteInput(new ItemStack(BeComItems.satelliteRocket, 1))) return CheckRecipeResultRegistry.NO_RECIPE;
        if (t.launchFuel() > 0 && !depleteInput(new FluidStack(fuel.getFluid(), t.launchFuel()))) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        updateSlots();
        int problems = getIdealStatus() - getRepairStatus();
        mEfficiency = Math.max(EFFICIENCY_PER_PROBLEM, FULL_EFFICIENCY - problems * EFFICIENCY_PER_PROBLEM);
        mEfficiencyIncrease = FULL_EFFICIENCY;
        mMaxProgresstime = (int) ((long) t.launchSeconds() * TICKS_PER_SECOND * FULL_EFFICIENCY / mEfficiency);
        mEUt = -t.launchPower();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    // A launch draws its power whatever the maintenance; its problems cost time instead (DESIGN.md 9.1).
    @Override
    protected long getActualEnergyUsage() {
        return -mEUt;
    }

    /** The rocket has gone up: one more satellite over this dimension, and the owner's quest tasks (DESIGN.md 15.5). */
    @Override
    protected void outputAfterRecipe() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (becom == null || !tile.isServerSide()) return;
        int dimension = tile.getWorld().provider.dimensionId;
        becom.space.launch(dimension);
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

    /** The Satellite Rockets in its input buses, waiting to launch (DESIGN.md 11.4). */
    public int rocketsWaiting() {
        int rockets = 0;
        for (ItemStack stack : getStoredInputs()) {
            if (stack != null && stack.getItem() == BeComItems.satelliteRocket) rockets += stack.stackSize;
        }
        return rockets;
    }

    /** Each dimension's satellites, for the screen: its name, how many, and ticks until the next comes down. */
    private String orbit() {
        BeComServer becom = BeComServer.get();
        if (becom == null) return "";
        StringBuilder text = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : becom.space.counts()
            .entrySet()) {
            if (text.length() > 0) text.append(LINE);
            text.append(SpaceService.dimensionName(entry.getKey()))
                .append(FIELD)
                .append(entry.getValue())
                .append(FIELD)
                .append(becom.space.untilNextDown(entry.getKey()));
        }
        return text.toString();
    }

    /** The orbit text as the client shows it, each dimension on a line. */
    private static String showOrbit(String orbit) {
        if (orbit == null || orbit.isEmpty()) return StatCollector.translateToLocal("becom.launch_pad.orbit_none");
        StringBuilder text = new StringBuilder(StatCollector.translateToLocal("becom.launch_pad.orbit"));
        for (String line : orbit.split(LINE)) {
            String[] fields = line.split(FIELD);
            if (fields.length != 3) continue;
            double days;
            try {
                days = Long.parseLong(fields[2]) / (double) Satellites.DAY_TICKS;
            } catch (NumberFormatException e) {
                continue;
            }
            text.append(LINE)
                .append(
                    StatCollector.translateToLocalFormatted(
                        "becom.launch_pad.dimension",
                        fields[0],
                        fields[1],
                        String.format("%.1f", days)));
        }
        return text.toString();
    }

    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new MTEMultiBlockBaseGui<>(this) {

            @Override
            protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager sync, ModularPanel parent) {
                StringSyncValue orbit = new StringSyncValue(MTELaunchPad.this::orbit);
                IntSyncValue queue = new IntSyncValue(MTELaunchPad.this::rocketsWaiting);
                sync.syncValue("becomOrbit", orbit);
                sync.syncValue("becomQueue", queue);
                return super.createTerminalTextWidget(sync, parent)
                    .child(
                        IKey.dynamic(
                            () -> StatCollector.translateToLocalFormatted("becom.launch_pad.queue", queue.getValue()))
                            .asWidget()
                            .marginBottom(2)
                            .fullWidth())
                    .child(
                        IKey.dynamic(() -> showOrbit(orbit.getValue()))
                            .asWidget()
                            .marginBottom(2)
                            .fullWidth());
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
            .addInfo(StatCollector.translateToLocal("becom.machine.launch_pad.info"))
            .addInfo(
                StatCollector.translateToLocalFormatted(
                    "becom.machine.launch_pad.launch",
                    t.launchSeconds(),
                    t.launchPower(),
                    t.launchFuel()))
            .addInfo(StatCollector.translateToLocal("becom.machine.launch_pad.sky"))
            .beginStructureBlock(SIDE, 1, SIDE, false)
            .addController(StatCollector.translateToLocal("becom.machine.launch_pad.controller"))
            .addCasingInfoMin(CASING.getLocalizedName(), SIDE * SIDE - 5, false)
            .addInputBus(StatCollector.translateToLocal("becom.machine.launch_pad.hatch"), 1)
            .addInputHatch(StatCollector.translateToLocal("becom.machine.launch_pad.hatch"), 1)
            .addEnergyHatch(StatCollector.translateToLocal("becom.machine.launch_pad.hatch"), 1)
            .addMaintenanceHatch(StatCollector.translateToLocal("becom.machine.launch_pad.hatch"), 1)
            .toolTipFinisher();
        return tt;
    }

    @Override
    public String[] getInfoData() {
        List<String> info = new ArrayList<>(java.util.Arrays.asList(super.getInfoData()));
        BeComServer becom = BeComServer.get();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (becom != null && tile != null) {
            int dimension = tile.getWorld().provider.dimensionId;
            info.add(StatCollector.translateToLocalFormatted("becom.info.satellites", becom.space.count(dimension)));
        }
        if (mMachine && !openSky()) info.add(StatCollector.translateToLocal("becom.info.no_sky"));
        return info.toArray(new String[0]);
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
