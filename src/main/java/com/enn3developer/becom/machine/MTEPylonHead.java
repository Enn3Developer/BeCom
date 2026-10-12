package com.enn3developer.becom.machine;

import static gregtech.api.enums.GTValues.V;
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
 * A Pylon Head and its powered variants (DESIGN.md 5.1, 5.3, 5.4): the controller on top of a column of steel frame
 * boxes. A plain head is passive and keeps working unloaded; Repeater and Router heads take LV power straight from a
 * cable on any side (D29, DESIGN.md 16.2) and work only while loaded and powered (D11).
 * <p>
 * It isn't a recipe machine, so it skips GT's processing loop: it checks its structure on GT's schedule, drains its
 * power each tick, and tells the network. A missing tick of power means offline, never a shutdown to restart by hand.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTEPylonHead extends MTEEnhancedMultiBlockBase<MTEPylonHead> implements ISurvivalConstructable, NodeHost {

    private static final String PIECE_FRAME = "frame";
    // LV, as D29 has it: an LV machine drawing a few EU/t, not a ULV one.
    private static final int VOLTAGE_TIER = 1;
    // Like GT's own hulls: room for a couple of seconds of input.
    private static final long EU_BUFFER = 512 + V[VOLTAGE_TIER] * 50L;
    private static final int TICKS_PER_SECOND = 20;

    private static final IStructureDefinition<MTEPylonHead> STRUCTURE = StructureDefinition.<MTEPylonHead>builder()
        .addShape(PIECE_FRAME, new String[][] { { "F" } })
        .addElement('F', ofFrame(Materials.Steel))
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
        if (found < t.pylonMinHeight()) errors.add(StructureErrorRegistry.TOO_SHORT_HEIGHT);
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
    }

    @Override
    public int survivalConstruct(ItemStack trigger, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int target = buildHeight(trigger);
        int built = 0;
        for (int i = 1; i <= target; i++) {
            int placed = survivalBuildPiece(PIECE_FRAME, trigger, 0, -i, 0, elementBudget - built, env, false);
            if (placed > 0) built += placed;
            if (built >= elementBudget) break;
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
        working = mMachine && (!kind.powered() || tile.isAllowedToWork() && drainPower(tile));
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

    private boolean drainPower(IGregTechTileEntity tile) {
        long power = power();
        return tile.getStoredEU() >= power && tile.decreaseStoredEnergyUnits(power, false);
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

    // region Power

    @Override
    public boolean isElectric() {
        return kind.powered();
    }

    @Override
    public boolean isEnetInput() {
        return kind.powered();
    }

    @Override
    public boolean isInputFacing(ForgeDirection side) {
        // DESIGN.md 5.3: a cable on any side, the front included, since the head has no hatches.
        return kind.powered();
    }

    @Override
    public long maxEUInput() {
        return kind.powered() ? V[VOLTAGE_TIER] : 0;
    }

    @Override
    public long maxAmperesIn() {
        return kind.powered() ? 1 : 0;
    }

    @Override
    public long maxEUStore() {
        return kind.powered() ? EU_BUFFER : 0;
    }

    @Override
    public long getMinimumStoredEU() {
        return 0;
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
        tt.beginVariableStructureBlock(1, 1, t.pylonMinHeight() + 1, t.pylonMaxHeight() + 1, 1, 1, false)
            .addController(StatCollector.translateToLocal("becom.machine.pylon.controller"))
            .addCasingInfoRange(
                StatCollector.translateToLocal("becom.machine.pylon.frames"),
                t.pylonMinHeight(),
                t.pylonMaxHeight(),
                false)
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
