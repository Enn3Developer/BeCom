package com.enn3developer.becom.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
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
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.MultiblockTooltipBuilder;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * A Pylon Head and its powered variants (DESIGN.md 5.1, 5.3, 5.4): the controller on top of a telephone pole, a column
 * of wood frame boxes with a crossarm across it right under the head. A plain head is passive and keeps working
 * unloaded. A Repeater stands a cabinet at the pole's foot, and a Router a wider one and a second crossarm; they take
 * LV power through an Energy Hatch in the cabinet, the way every GT multiblock takes it, and work only while loaded and
 * powered (D11).
 * <p>
 * It isn't a recipe machine, so it skips GT's processing loop: it checks its structure on GT's schedule, drains its
 * power each tick, and tells the network. A missing tick of power means offline, never a shutdown to restart by hand.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTEPylonHead extends MTEEnhancedMultiBlockBase<MTEPylonHead> implements ISurvivalConstructable, NodeHost {

    private static final String PIECE_FRAME = "frame";
    private static final String PIECE_ARM = "arm";
    private static final String PIECE_CABINET = "cabinet";
    private static final String PIECE_EXCHANGE = "exchange";
    // LV, as D29 has it: an LV machine drawing a few EU/t, not a ULV one.
    private static final int VOLTAGE_TIER = 1;
    private static final int TICKS_PER_SECOND = 20;
    // Each crossarm reaches this far out on either side of the pole.
    private static final int ARM_REACH = 2;
    // The Router's second crossarm hangs this far under the head; the first is right under it.
    private static final int SECOND_ARM = 3;
    private static final Casings CABINET_CASING = Casings.LVMachineCasing;

    // Layers run top to bottom and rows from the head's front backwards. A cabinet stands in front of the pole's two
    // lowest frames: one block for a Repeater, three across for a Router's exchange.
    private static final IStructureDefinition<MTEPylonHead> STRUCTURE = StructureDefinition.<MTEPylonHead>builder()
        .addShape(PIECE_FRAME, new String[][] { { "F" } })
        .addShape(PIECE_ARM, transpose(new String[][] { { "FFFFF" } }))
        .addShape(PIECE_CABINET, transpose(new String[][] { { "E" }, { "E" } }))
        .addShape(PIECE_EXCHANGE, transpose(new String[][] { { "EEE" }, { "EEE" } }))
        .addElement('F', ofFrame(Materials.Wood))
        .addElement(
            'E',
            buildHatchAdder(MTEPylonHead.class).atLeast(Energy)
                .casingIndex(CABINET_CASING.getTextureId())
                .hint(1)
                .buildAndChain(onElementPass(x -> {}, CABINET_CASING.asElement())))
        .build();

    private final NodeKind kind;
    private int height;
    private boolean working;
    private boolean registered;

    public MTEPylonHead(int id, String name, String regionalName, NodeKind kind) {
        super(id, name, regionalName);
        this.kind = kind;
    }

    public MTEPylonHead(String name, NodeKind kind) {
        super(name);
        this.kind = kind;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEPylonHead(mName, kind);
    }

    @Override
    public NodeKind nodeKind() {
        return kind;
    }

    /** The frame column's height at the last structure check. */
    public int height() {
        return height;
    }

    public boolean isWorking() {
        return working;
    }

    // region Structure

    @Override
    public IStructureDefinition<MTEPylonHead> getStructureDefinition() {
        return STRUCTURE;
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        // No maintenance hatch to fix them at: left on, GT counts all six problems forever and shows HAS PROBLEMS.
        return false;
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        // The column always hangs straight down, so the head may only face sideways, unrotated.
        return (direction, rotation, flip) -> direction.offsetY == 0 && rotation == Rotation.NORMAL
            && flip == Flip.NONE;
    }

    @Override
    public void checkMachine(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors) {
        Tunables t = BeComConfig.get();
        int found = 0;
        // B counts blocks above the controller, so the frames below it sit at -1, -2, ...
        while (found < t.pylonMaxHeight() && checkPiece(PIECE_FRAME, 0, -(found + 1), 0, null)) found++;
        height = found;
        if (found < t.pylonMinHeight()) {
            errors.add(StructureErrorRegistry.TOO_SHORT_HEIGHT);
            return;
        }
        if (!checkPiece(PIECE_ARM, ARM_REACH, -1, 0, errors)) return;
        if (kind == NodeKind.ROUTER && !checkPiece(PIECE_ARM, ARM_REACH, -SECOND_ARM, 0, errors)) return;
        if (!kind.powered()) return;
        if (!checkCabinet(found, errors)) return;
        checkHasEnergyHatch(errors);
    }

    /** The cabinet's top block sits in front of the pole's second-lowest frame. */
    private boolean checkCabinet(int poleHeight, List<StructureError> errors) {
        return kind == NodeKind.ROUTER ? checkPiece(PIECE_EXCHANGE, 1, -(poleHeight - 1), 1, errors)
            : checkPiece(PIECE_CABINET, 0, -(poleHeight - 1), 1, errors);
    }

    private String cabinetPiece() {
        return kind == NodeKind.ROUTER ? PIECE_EXCHANGE : PIECE_CABINET;
    }

    private int cabinetOffset() {
        return kind == NodeKind.ROUTER ? 1 : 0;
    }

    /** The height a hologram or the build tool makes: the shortest pylon, plus one per extra item in the stack. */
    private static int buildHeight(ItemStack trigger) {
        Tunables t = BeComConfig.get();
        int extra = trigger == null ? 0 : Math.max(0, trigger.stackSize - 1);
        return Math.min(t.pylonMaxHeight(), t.pylonMinHeight() + extra);
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {
        int target = buildHeight(trigger);
        for (int i = 1; i <= target; i++) buildPiece(PIECE_FRAME, trigger, hintsOnly, 0, -i, 0);
        buildPiece(PIECE_ARM, trigger, hintsOnly, ARM_REACH, -1, 0);
        if (kind == NodeKind.ROUTER) buildPiece(PIECE_ARM, trigger, hintsOnly, ARM_REACH, -SECOND_ARM, 0);
        if (kind.powered()) buildPiece(cabinetPiece(), trigger, hintsOnly, cabinetOffset(), -(target - 1), 1);
    }

    @Override
    public int survivalConstruct(ItemStack trigger, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int target = buildHeight(trigger);
        int built = 0;
        for (int i = 1; i <= target; i++) {
            int placed = survivalBuildPiece(PIECE_FRAME, trigger, 0, -i, 0, elementBudget - built, env, false);
            if (placed > 0) built += placed;
            if (built >= elementBudget) return built;
        }
        int placed = survivalBuildPiece(PIECE_ARM, trigger, ARM_REACH, -1, 0, elementBudget - built, env, false);
        if (placed > 0) built += placed;
        if (kind == NodeKind.ROUTER && built < elementBudget) {
            placed = survivalBuildPiece(
                PIECE_ARM,
                trigger,
                ARM_REACH,
                -SECOND_ARM,
                0,
                elementBudget - built,
                env,
                false);
            if (placed > 0) built += placed;
        }
        if (kind.powered() && built < elementBudget) {
            placed = survivalBuildPiece(
                cabinetPiece(),
                trigger,
                cabinetOffset(),
                -(target - 1),
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
        if (becom == null) return;
        World world = tile.getWorld();
        long pos = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
        if (!registered) {
            becom.network.ensureNode(world, pos, kind, tile.getOwnerUuid());
            registered = true;
        }
        // GT's structure check schedule: on start-up, when a nearby block changes, and every so often after.
        if (mUpdated) {
            if (mUpdate <= 0) mUpdate = 50;
            mUpdated = false;
        }
        if (--mUpdate == 0 || --mStartUpCheck == 0 || tick % (TICKS_PER_SECOND * 5) == 0) refreshStructure();
        working = mMachine && (!kind.powered() || tile.isAllowedToWork() && drainPower());
        if (working && kind.powered()) becom.network.heartbeat(world, pos);
        tile.setActive(working && kind.powered());
    }

    /** Runs the structure check now and tells the network; the self-test calls this directly. */
    public boolean refreshStructure() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        checkStructure(true, tile);
        BeComServer becom = BeComServer.get();
        if (becom != null && tile.isServerSide()) {
            long pos = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
            becom.network.ensureNode(tile.getWorld(), pos, kind, tile.getOwnerUuid());
            becom.network.setFormed(tile.getWorld(), pos, mMachine, mMachine ? height : 0);
            registered = true;
        }
        return mMachine;
    }

    private boolean drainPower() {
        return EnergyHatches.drain(this, power());
    }

    private long power() {
        Tunables t = BeComConfig.get();
        return kind == NodeKind.ROUTER ? t.routerPower() : t.repeaterPower();
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
        refreshStructure();
        becom.links
            .wrench(player, tile.getWorld(), BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
        return true;
    }

    // endregion

    // region Looks and info

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int colorIndex,
        boolean active, boolean redstoneLevel) {
        ITexture casing = kind.powered() ? Textures.BlockIcons.MACHINE_CASINGS[VOLTAGE_TIER][colorIndex + 1]
            : Casings.SolidSteelMachineCasing.getCasingTexture();
        if (side != facing) return new ITexture[] { casing };
        IIconContainer overlay = switch (kind) {
            case REPEATER -> BeComTextures.REPEATER_HEAD;
            case ROUTER -> BeComTextures.ROUTER_HEAD;
            default -> BeComTextures.PYLON_HEAD;
        };
        IIconContainer glow = switch (kind) {
            case REPEATER -> BeComTextures.REPEATER_HEAD_GLOW;
            case ROUTER -> BeComTextures.ROUTER_HEAD_GLOW;
            default -> null;
        };
        if (!active || glow == null) return new ITexture[] { casing, TextureFactory.builder()
            .addIcon(overlay)
            .extFacing()
            .build() };
        return new ITexture[] { casing, TextureFactory.builder()
            .addIcon(overlay)
            .extFacing()
            .build(),
            TextureFactory.builder()
                .addIcon(glow)
                .extFacing()
                .glow()
                .build() };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        Tunables t = BeComConfig.get();
        String key = "becom.machine." + kind.name()
            .toLowerCase(java.util.Locale.ROOT);
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal(key + ".type"))
            .addInfo(StatCollector.translateToLocal(key + ".info"))
            .addInfo(
                StatCollector
                    .translateToLocalFormatted("becom.machine.pylon.span", (int) t.pylonSpanFactor(), kind.linkSlots()))
            .addInfo(StatCollector.translateToLocal("becom.machine.pylon.wrench"));
        if (kind.powered()) {
            tt.addInfo(StatCollector.translateToLocalFormatted("becom.machine.power", power(), "LV"));
        }
        int width = 2 * ARM_REACH + 1;
        int depth = kind.powered() ? 2 : 1;
        int arms = kind == NodeKind.ROUTER ? 2 : 1;
        tt.beginVariableStructureBlock(
            width,
            width,
            t.pylonMinHeight() + 1,
            t.pylonMaxHeight() + 1,
            depth,
            depth,
            false)
            .addController(StatCollector.translateToLocal("becom.machine.pylon.controller"))
            .addCasingInfoRange(
                StatCollector.translateToLocal("becom.machine.pylon.frames"),
                t.pylonMinHeight(),
                t.pylonMaxHeight(),
                false)
            .addCasingInfoExactly(
                StatCollector.translateToLocal(arms == 1 ? "becom.machine.pylon.arm" : "becom.machine.pylon.arms"),
                arms * 2 * ARM_REACH,
                false);
        if (kind.powered()) {
            // A one-block cabinet, or a three-wide exchange, two high: all casings but the hatch.
            int cabinet = (kind == NodeKind.ROUTER ? 3 : 1) * 2;
            tt.addCasingInfoExactly(CABINET_CASING.getLocalizedName(), cabinet - 1, false)
                .addEnergyHatch(StatCollector.translateToLocal("becom.machine.pylon.hatch"), 1);
        }
        tt.toolTipFinisher();
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
        // Skips GT's multiblock lines: a recipe machine's efficiency and progress, always 0% and Idle on a pylon.
        getMTEWailaBody(stack, tip, accessor, config);
        InfoLines.read(accessor.getNBTData(), tip);
    }

    private InfoLines info() {
        InfoLines info = new InfoLines();
        info.add("becom.info.formed", mMachine ? height : 0);
        if (kind.powered()) info.add(working ? "becom.info.online" : "becom.info.offline");
        BeComServer becom = BeComServer.get();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (becom != null && tile != null) {
            // Only a loaded dimension's model: asking by world would load one that isn't.
            NetworkModel model = becom.network.model(tile.getWorld().provider.dimensionId);
            NetNode node = model == null ? null
                : model.node(BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
            if (node != null) {
                info.add(
                    "becom.info.links",
                    node.links()
                        .size(),
                    kind.linkSlots());
                for (long other : node.links()) {
                    info.detail(
                        model.isBlocked(node.pos(), other) ? "becom.info.link_blocked" : "becom.info.link",
                        BlockPos.toString(other),
                        (int) Math.round(BlockPos.distance(node.pos(), other)));
                }
            }
        }
        return info;
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    // endregion
}
