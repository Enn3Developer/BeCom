package com.enn3developer.becom.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
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
 * A Pylon Head: the controller on top of a telephone pole, a column of wood frame boxes with a crossarm
 * across it right under the head. It's passive and keeps working unloaded: it holds the cables strung on its two
 * spans, up to a bundle each.
 * <p>
 * It isn't a recipe machine, so it skips GT's processing loop: it checks its structure on GT's schedule and tells the
 * network.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTEPylonHead extends MTEEnhancedMultiBlockBase<MTEPylonHead> implements ISurvivalConstructable, NodeHost {

    private static final String PIECE_FRAME = "frame";
    private static final String PIECE_ARM = "arm";
    private static final int TICKS_PER_SECOND = 20;
    // Each crossarm reaches this far out on either side of the pole.
    private static final int ARM_REACH = 2;
    private static final NodeKind KIND = NodeKind.PYLON;

    // Layers run top to bottom and rows from the head's front backwards.
    private static final IStructureDefinition<MTEPylonHead> STRUCTURE = StructureDefinition.<MTEPylonHead>builder()
        .addShape(PIECE_FRAME, new String[][] { { "F" } })
        .addShape(PIECE_ARM, transpose(new String[][] { { "FFFFF" } }))
        .addElement('F', ofFrame(Materials.Wood))
        .build();

    private int height;
    private boolean working;
    private boolean registered;

    public MTEPylonHead(int id, String name, String regionalName) {
        super(id, name, regionalName);
    }

    public MTEPylonHead(String name) {
        super(name);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEPylonHead(mName);
    }

    @Override
    public NodeKind nodeKind() {
        return KIND;
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
        checkPiece(PIECE_ARM, ARM_REACH, -1, 0, errors);
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
            becom.network.ensureNode(world, pos, KIND, tile.getOwnerUuid());
            registered = true;
        }
        // GT's structure check schedule: on start-up, when a nearby block changes, and every so often after.
        if (mUpdated) {
            if (mUpdate <= 0) mUpdate = 50;
            mUpdated = false;
        }
        if (--mUpdate == 0 || --mStartUpCheck == 0 || tick % (TICKS_PER_SECOND * 5) == 0) refreshStructure();
        working = mMachine;
    }

    /** Runs the structure check now and tells the network; the self-test calls this directly. */
    public boolean refreshStructure() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        checkStructure(true, tile);
        BeComServer becom = BeComServer.get();
        if (becom != null && tile.isServerSide()) {
            long pos = BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
            becom.network.ensureNode(tile.getWorld(), pos, KIND, tile.getOwnerUuid());
            becom.network.setFormed(tile.getWorld(), pos, mMachine, mMachine ? height : 0);
            registered = true;
        }
        return mMachine;
    }

    @Override
    public void onBlockDestroyed() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (becom != null && tile != null && tile.isServerSide()) {
            becom.links
                .dropCables(tile.getWorld(), BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()));
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
        ITexture casing = Casings.SolidSteelMachineCasing.getCasingTexture();
        if (side != facing) return new ITexture[] { casing };
        return new ITexture[] { casing, TextureFactory.builder()
            .addIcon(BeComTextures.PYLON_HEAD)
            .extFacing()
            .build() };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        Tunables t = BeComConfig.get();
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("becom.machine.pylon.type"))
            .addInfo(StatCollector.translateToLocal("becom.machine.pylon.info"))
            .addInfo(
                StatCollector.translateToLocalFormatted(
                    "becom.machine.pylon.span",
                    (int) t.pylonSpanFactor(),
                    KIND.linkSlots(),
                    t.bundleSize()))
            .addInfo(StatCollector.translateToLocal("becom.machine.pylon.wrench"));
        int width = 2 * ARM_REACH + 1;
        tt.beginVariableStructureBlock(width, width, t.pylonMinHeight() + 1, t.pylonMaxHeight() + 1, 1, 1, false)
            .addController(StatCollector.translateToLocal("becom.machine.pylon.controller"))
            .addCasingInfoRange(
                StatCollector.translateToLocal("becom.machine.pylon.frames"),
                t.pylonMinHeight(),
                t.pylonMaxHeight(),
                false)
            .addCasingInfoExactly(StatCollector.translateToLocal("becom.machine.pylon.arm"), 2 * ARM_REACH, false);
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
                    KIND.linkSlots());
                for (long other : node.links()) InfoLines.span(info, model, node.pos(), other);
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
