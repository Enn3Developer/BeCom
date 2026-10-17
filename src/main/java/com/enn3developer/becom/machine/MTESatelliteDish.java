package com.enn3developer.becom.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.List;
import java.util.Locale;

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

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.data.BeComNbt;
import com.enn3developer.becom.gui.DishView;
import com.enn3developer.becom.gui.ViewSync;
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
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * The Satellite Dish (DESIGN.md 9.2, 3.2, D40): a late-HV ground station under open sky. A stepped bowl of clean
 * stainless steel casings faces straight up, with a feed horn held over it by four struts of stainless steel frame
 * boxes, on a pedestal; the controller stands in a plinth at the foot with its energy hatch and maintenance hatch.
 * Wrench-linked to an
 * antenna, a Pylon Head or a Line Terminal within 16 blocks, it joins that network to the satellites over its
 * dimension: to every other dish there and, in the Overworld, to every planet's uplink. Rain at the dish weakens that
 * hop, and so does each maintenance problem, since it has no process to slow down.
 * <p>
 * Like the antennas, it isn't a recipe machine: it checks its structure on GT's schedule, drains its power each tick
 * and tells the network. A missing tick of power means offline, never a shutdown to restart by hand.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTESatelliteDish extends MTEEnhancedMultiBlockBase<MTESatelliteDish>
    implements ISurvivalConstructable, NodeHost {

    private static final String PIECE_DISH = "dish";
    private static final int TICKS_PER_SECOND = 20;
    // The bowl's rim is this wide; the whole dish stands this high, plinth to feed horn.
    private static final int SIDE = 7;
    private static final int HEIGHT = 9;
    // The controller stands at the front of the plinth, one block in front of the dish's centre, at the bottom.
    private static final int CONTROLLER_ROW = 2;
    private static final Casings CASING = Casings.CleanStainlessSteelMachineCasing;
    private static final Materials FRAME = Materials.StainlessSteel;

    public static final int FORMAT = 1;

    // Layers run top to bottom and rows from the controller's front backwards. C is a casing, F a frame box, H a plinth
    // casing an energy or maintenance hatch may take the place of, and a space anything.
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
            buildHatchAdder(MTESatelliteDish.class).atLeast(Energy, Maintenance)
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

    // BeCom's keys from a newer BeCom: kept as they were, and the dish left alone (X16).
    private NBTTagCompound tooNew;
    private boolean registered;
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

    @Override
    public NodeKind nodeKind() {
        return NodeKind.SATELLITE_DISH;
    }

    public boolean isWorking() {
        return working;
    }

    /** Saved by a newer BeCom: this one leaves it alone. */
    public boolean isTooNew() {
        return tooNew != null;
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
        if (!checkPiece(PIECE_DISH, SIDE / 2, HEIGHT - 1, CONTROLLER_ROW, errors)) return;
        if (mMaintenanceHatches.size() != 1) errors.add(StructureErrors.missingHatch(Maintenance));
        checkHasEnergyHatch(errors);
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

    /** Whether the whole dish sees the sky over its feed horn, in a dimension that has one (DESIGN.md 9.2). */
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

    /** Runs the structure check now and tells the network; the self-test calls this directly. */
    public boolean refreshStructure() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        // Left alone: no node, no formed state, no bridges written for it here (X16).
        if (tooNew != null) return false;
        checkStructure(true, tile);
        BeComServer becom = BeComServer.get();
        if (becom != null && tile.isServerSide()) {
            World world = tile.getWorld();
            becom.network.ensureNode(world, pos(), nodeKind(), tile.getOwnerUuid());
            becom.network.setFormed(world, pos(), mMachine, 0);
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
        if (becom == null || tooNew != null) return;
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
        // The maintenance hatch's tools fix problems the way they do on any GT machine (D40).
        if (mMachine) checkMaintenance();
        working = mMachine && sky && tile.isAllowedToWork() && drainPower() && doRandomMaintenanceDamage();
        if (working) {
            becom.network.heartbeat(world, pos());
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

    /** What its hop up to the satellites loses: rain at the dish (D26) and its maintenance problems (D40). */
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
            BeComConfig.get()
                .dishPower());
    }

    @Override
    public void onBlockDestroyed() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (becom != null && tile != null && tile.isServerSide()) {
            becom.network.removeNode(tile.getWorld(), pos());
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
        becom.links.wrench(player, tile.getWorld(), pos());
        return true;
    }

    // endregion

    // region Screen

    /**
     * What the screen and a scanner show (DESIGN.md 9.2): the satellites over this dimension, the hop up to them, its
     * maintenance problems and what it's linked to. Built on the server only: the view sync never asks for it on a
     * client.
     */
    private DishView view() {
        BeComServer becom = BeComServer.get();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (becom == null || tile == null) return DishView.EMPTY;
        int dimension = tile.getWorld().provider.dimensionId;
        Tunables t = becom.tunables;
        int satellites = becom.space.count(dimension);
        double snr = becom.space.sky()
            .snr(satellites) - loss();
        NetworkModel model = becom.network.model(dimension);
        NetNode node = model == null ? null : model.node(pos());
        String linked = node == null || node.links()
            .isEmpty() ? ""
                : BlockPos.toString(
                    node.links()
                        .iterator()
                        .next());
        return new DishView(
            mMachine,
            satellites,
            sky,
            becom.space.skyHops(dimension),
            t.satellitesLow(),
            snr,
            t.levelScale()
                .levelOf(snr),
            problems(),
            t.dishMaintenanceLoss(),
            linked);
    }

    /** The view's lines, which whoever reads them translates: the client on the screen and in WAILA. */
    private static InfoLines lines(DishView view, InfoLines lines) {
        lines.add("becom.info.satellites", view.satellites());
        if (!view.formed()) return lines;
        if (!view.sky()) {
            lines.add("becom.info.no_sky");
        } else if (!view.enough()) {
            lines.add("becom.dish.too_few", view.satellitesLow());
        } else {
            lines.add(
                "becom.dish.hop",
                String.format(Locale.ROOT, "%.1f", view.snr()),
                new InfoLines.Key(
                    "becom.level." + view.level()
                        .name()
                        .toLowerCase(Locale.ROOT)));
        }
        if (view.problems() > 0) {
            lines.add(
                "becom.dish.problems",
                view.problems(),
                String.format(Locale.ROOT, "%.0f", view.problems() * view.problemLoss()));
        }
        if (view.linked()
            .isEmpty()) lines.add("becom.dish.unlinked");
        else lines.add("becom.dish.linked", view.linked());
        return lines;
    }

    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new MTEMultiBlockBaseGui<>(this) {

            @Override
            protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager sync, ModularPanel parent) {
                ViewSync<DishView> view = new ViewSync<>(
                    MTESatelliteDish.this::view,
                    DishView::write,
                    DishView::read,
                    DishView.EMPTY);
                sync.syncValue("becomDish", view);
                return super.createTerminalTextWidget(sync, parent).child(
                    IKey.dynamic(() -> String.join("\n", lines(view.view(), new InfoLines()).translate()))
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
            .addInfo(StatCollector.translateToLocal("becom.machine.satellite_dish.info"))
            .addInfo(
                StatCollector.translateToLocalFormatted(
                    "becom.machine.satellite_dish.link",
                    (int) t.wrenchLinkDistance(),
                    t.satellitesLow()))
            .addInfo(
                StatCollector.translateToLocalFormatted(
                    "becom.machine.satellite_dish.maintenance",
                    String.format(Locale.ROOT, "%.0f", t.dishMaintenanceLoss())))
            .addInfo(StatCollector.translateToLocalFormatted("becom.machine.power", t.dishPower(), "HV"))
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
        if (tooNew != null) info.add("becom.info.too_new");
        info.add(working ? "becom.info.online" : "becom.info.offline");
        // GT's scanner reads on the server and sends its text as it is; WAILA translates on the client.
        lines(view(), info);
        InfoLines.calls(
            info,
            getBaseMetaTileEntity(),
            BeComConfig.get()
                .dishCalls());
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
        if (tooNew != null) {
            BeComNbt.restore(nbt, tooNew);
            return;
        }
        BeComNbt.write(nbt, FORMAT);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        tooNew = BeComNbt.tooNew(nbt, FORMAT) ? BeComNbt.ours(nbt) : null;
    }

    // endregion
}
