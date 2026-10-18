package com.enn3developer.becom.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.lazy;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.gui.ExchangeScreen;
import com.enn3developer.becom.gui.ExchangeView;
import com.enn3developer.becom.gui.RouterView;
import com.enn3developer.becom.item.Tips;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.Buses;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.logic.net.CallGraph;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.NetworkService;
import com.enn3developer.becom.server.NodeHost;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.GregTechTileClientEvents;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.ErrorType;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.structure.error.TranslatableText;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.misc.GTStructureChannels;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * The Exchange: a hall of Switching Racks that switches calls between every bus of cable its Line Hatches join, as a
 * router. A front cap with the controller, then 1 to {@link #MAX_SLICES} rack slices with a slice of frame-box posts
 * before and after each, a back cap, and a frame-box cable ladder along the ridge: 3 wide, 5 tall, 5 to 19 deep.
 * <p>
 * Its tier is its casings': LV, MV or HV Machine Casings, all of one tier, with that tier's frame boxes. Its racks and
 * Line Hatches go up to that tier, and it switches as many calls at once as its racks add up to; with none it
 * switches nothing. Racks fill from the front, their lamps lit while calls use them.
 * <p>
 * It isn't a recipe machine: it checks its structure on GT's schedule, drains its power each tick and tells the
 * network. A missing tick of power means offline, never a shutdown to restart by hand.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTEExchange extends MTEEnhancedMultiBlockBase<MTEExchange>
    implements ISurvivalConstructable, NodeHost, LineHatchHost {

    /** The most rack slices a hall has. */
    public static final int MAX_SLICES = 8;
    /** The racks a slice holds: two on each side wall. */
    public static final int RACKS_PER_SLICE = 4;

    private static final String PIECE_FRONT = "front";
    private static final String PIECE_BAY = "bay";
    private static final String PIECE_PROBE = "probe";
    private static final String PIECE_BACK = "back";
    private static final int TICKS_PER_SECOND = 20;
    private static final int NO_TIER = -1;
    // Where the controller is in the front piece: its middle column, four rows down from the ridge's.
    private static final int ACROSS = 1;
    private static final int DOWN = 3;

    // Each piece runs slice by slice from the front, each slice's rows from the ridge down. H is a casing an energy
    // hatch or a Line Hatch may take the place of, R one a Switching Rack may, C a casing, F a frame box.
    private static final String[] FRONT_CAP = { "   ", "HHH", "HHH", "H~H", "HHH" };
    private static final String[] POSTS = { " F ", "HHH", "FCF", "FCF", "HHH" };
    private static final String[] RACKS = { " F ", "HHH", "RCR", "RCR", "HHH" };
    private static final String[] BACK_CAP = { "   ", "HHH", "HHH", "HHH", "HHH" };
    // A bay's frames alone: whether a bay stands there, asked without adding its hatches.
    private static final String[][] PROBE = { { " F ", "   ", "   ", "   ", "   " },
        { " F ", "   ", "F F", "F F", "   " } };

    private static final IStructureDefinition<MTEExchange> STRUCTURE = StructureDefinition.<MTEExchange>builder()
        .addShape(PIECE_FRONT, new String[][] { FRONT_CAP, POSTS })
        .addShape(PIECE_BAY, new String[][] { RACKS, POSTS })
        .addShape(PIECE_PROBE, PROBE)
        .addShape(PIECE_BACK, new String[][] { BACK_CAP })
        .addElement(
            'H',
            buildHatchAdder(MTEExchange.class).atLeast(Energy, LineHatches.Element.LINE_HATCH)
                .casingIndex(
                    Tiers.casing(Tiers.LOWEST)
                        .getTextureId())
                .hint(1)
                .buildAndChain(casing()))
        .addElement(
            'R',
            buildHatchAdder(MTEExchange.class).anyOf(MTEHatchRack.Element.RACK)
                .casingIndex(
                    Tiers.casing(Tiers.LOWEST)
                        .getTextureId())
                .hint(2)
                .buildAndChain(casing()))
        .addElement('C', casing())
        .addElement('F', frame())
        .build();

    /** A machine casing of the hall's tier; the first one found sets it. */
    private static IStructureElement<MTEExchange> casing() {
        return GTStructureChannels.TIER_MACHINE_CASING.use(
            lazy(
                () -> ofBlocksTiered(
                    Tiers::ofCasing,
                    tiers(
                        tier -> Pair.of(
                            Tiers.casing(tier)
                                .getBlock(),
                            Tiers.casing(tier)
                                .getBlockMeta())),
                    NO_TIER,
                    (MTEExchange e, Integer tier) -> e.tier = tier,
                    e -> e.tier)));
    }

    /** A frame box of the hall's tier's material. */
    private static IStructureElement<MTEExchange> frame() {
        return GTStructureChannels.TIER_MACHINE_CASING.use(lazy(() -> ofBlocksTiered(Tiers::ofFrame, tiers(tier -> {
            ItemStack frame = Tiers.frameStack(tier);
            return Pair.of(Block.getBlockFromItem(frame.getItem()), frame.getItemDamage());
        }), NO_TIER, (MTEExchange e, Integer tier) -> e.tier = tier, e -> e.tier)));
    }

    private static List<Pair<Block, Integer>> tiers(IntFunction<Pair<Block, Integer>> block) {
        List<Pair<Block, Integer>> blocks = new ArrayList<>();
        for (int tier = Tiers.LOWEST; tier <= Tiers.HIGHEST; tier++) blocks.add(block.apply(tier));
        return blocks;
    }

    private final LineHatches lineHatches = new LineHatches();
    private final List<MTEHatchRack> racks = new ArrayList<>();
    // The racks by slot, slice by slice from the front: each slice's left wall top and bottom, then its right wall's,
    // seen from the front. Null where a casing stands.
    private MTEHatchRack[] slots = new MTEHatchRack[0];
    private int tier = NO_TIER;
    private int slices;
    // The looks' tier the clients were last told.
    private int toldLooks;
    // Why it didn't form, as a lang key and its arguments; empty when it did.
    private List<String> why = List.of();
    private boolean registered;
    private boolean working;
    private int calls;

    public MTEExchange(int id, String name, String regionalName) {
        super(id, name, regionalName);
    }

    public MTEExchange(String name) {
        super(name);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEExchange(mName);
    }

    /** Its item is BeCom's, as its block is. */
    @Override
    public ItemStack getStackForm(long amount) {
        return BeComMachines.stack(this, amount);
    }

    /** A router to the buses its Line Hatches join. */
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

    @Override
    public NodeKind nodeKind() {
        return NodeKind.EXCHANGE;
    }

    public boolean isWorking() {
        return working;
    }

    /** How many calls its racks switch at once, at the last tick. */
    public int calls() {
        return calls;
    }

    /** Its casings' tier, 1 LV to 3 HV, as its last structure check found it; -1 for none. */
    public int tier() {
        return mMachine ? tier : NO_TIER;
    }

    /** Its rack slices, as its last structure check found them. */
    public int slices() {
        return mMachine ? slices : 0;
    }

    /** Its racks by slot, slice by slice from the front; null for a free slot. */
    public List<MTEHatchRack> slots() {
        return java.util.Arrays.asList(slots.clone());
    }

    private long pos() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        return BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
    }

    // region Structure

    @Override
    public IStructureDefinition<MTEExchange> getStructureDefinition() {
        return STRUCTURE;
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (direction, rotation, flip) -> direction.offsetY == 0 && rotation == Rotation.NORMAL
            && flip == Flip.NONE;
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        // No maintenance hatch to fix them at: left on, GT counts all six problems forever and shows HAS PROBLEMS.
        return false;
    }

    /** How far behind the controller bay {@code n} starts, from 1; the back cap stands where bay slices + 1 would. */
    private static int depthOf(int bay) {
        return 2 * bay;
    }

    @Override
    public void checkMachine(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors) {
        tier = NO_TIER;
        slices = 0;
        why = List.of("becom.exchange.why.shape");
        if (!checkPiece(PIECE_FRONT, ACROSS, DOWN, 0, errors)) return;
        // A bay stands wherever its frames do; anything else there is the back cap.
        while (slices < MAX_SLICES && checkPiece(PIECE_PROBE, ACROSS, DOWN, -depthOf(slices + 1), null)) {
            if (!checkPiece(PIECE_BAY, ACROSS, DOWN, -depthOf(slices + 1), errors)) return;
            slices++;
        }
        if (slices == 0) {
            errors.add(StructureErrorRegistry.TOO_SHORT_LENGTH);
            why = List.of("becom.exchange.why.short");
            return;
        }
        if (!checkPiece(PIECE_BACK, ACROSS, DOWN, -depthOf(slices + 1), errors)) return;
        why = List.of();
        String hall = CableTier.ofVoltage(tier)
            .voltage();
        for (MTEHatchRack rack : racks) {
            if (rack.tier()
                .voltageTier() > tier)
                above(
                    errors,
                    "becom.exchange.why.rack_tier",
                    rack.tier()
                        .voltage(),
                    hall);
        }
        for (MTEHatchLine hatch : lineHatches.found()) {
            if (hatch.tier()
                .voltageTier() > tier)
                above(
                    errors,
                    "becom.exchange.why.line_tier",
                    hatch.tier()
                        .voltage(),
                    hall);
        }
        if (lineHatches.count() == 0) {
            errors.add(
                StructureErrors.hatchCount(
                    ErrorType.TOO_FEW,
                    TranslatableText.lang(LineHatches.Element.LINE_HATCH.getDescriptionLangKey()),
                    0,
                    1));
            if (why.isEmpty()) why = List.of("becom.exchange.why.no_line_hatch");
        }
        if (mEnergyHatches.isEmpty()) {
            checkHasEnergyHatch(errors);
            if (why.isEmpty()) why = List.of("becom.exchange.why.no_energy_hatch");
        }
    }

    /** A rack or Line Hatch above the hall's tier: an error, and the first one is why it didn't form. */
    private void above(List<StructureError> errors, String key, String part, String hall) {
        errors.add(StructureErrors.of(key, TranslatableText.literal(part), TranslatableText.literal(hall)));
        if (why.isEmpty()) why = List.of(key, part, hall);
    }

    /** GT's hatch adder: a Switching Rack where the structure takes one. */
    boolean addRack(IGregTechTileEntity tile, Short casing) {
        if (tile == null || !(tile.getMetaTileEntity() instanceof MTEHatchRack rack)) return false;
        rack.updateTexture(casing == null ? 0 : casing);
        if (!racks.contains(rack)) racks.add(rack);
        return true;
    }

    /** How many racks the last structure check found so far. */
    int rackCount() {
        return racks.size();
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        lineHatches.clear();
        racks.clear();
    }

    @Override
    protected void onStructureCheckFinished(IGregTechTileEntity tile) {
        super.onStructureCheckFinished(tile);
        lineHatches.finished(tile, mMachine);
        slots = mMachine ? slotsOf(tile) : new MTEHatchRack[0];
        if (mMachine) {
            // The hatches took the LV casing's look while the check found them; they take the hall's now.
            int casing = Tiers.casing(tier)
                .getTextureId();
            List<MTEHatch> hatches = new ArrayList<>(mEnergyHatches);
            hatches.addAll(lineHatches.found());
            hatches.addAll(racks);
            for (MTEHatch hatch : hatches) if (hatch.isValid()) hatch.updateTexture(casing);
        }
        if (looks() != toldLooks) {
            toldLooks = looks();
            tile.sendBlockEvent(GregTechTileClientEvents.CHANGE_CUSTOM_DATA, getUpdateData());
        }
    }

    /** Each rack the check found, by slot, from where it stands. */
    private MTEHatchRack[] slotsOf(IGregTechTileEntity tile) {
        MTEHatchRack[] found = new MTEHatchRack[slices * RACKS_PER_SLICE];
        ForgeDirection back = tile.getFrontFacing()
            .getOpposite();
        // Seen from the front, looking in.
        ForgeDirection right = back.getRotation(ForgeDirection.UP);
        for (MTEHatchRack rack : racks) {
            IGregTechTileEntity at = rack.getBaseMetaTileEntity();
            int dx = at.getXCoord() - tile.getXCoord(), dz = at.getZCoord() - tile.getZCoord();
            int depth = dx * back.offsetX + dz * back.offsetZ;
            int across = dx * right.offsetX + dz * right.offsetZ;
            int slice = depth / 2 - 1;
            if (depth % 2 != 0 || slice < 0 || slice >= slices || across == 0) continue;
            int up = at.getYCoord() - tile.getYCoord();
            found[slice * RACKS_PER_SLICE + (across < 0 ? 0 : 2) + (up > 0 ? 0 : 1)] = rack;
        }
        return found;
    }

    /** Its looks' tier: its casings' as its last check found them, LV before any. */
    private int looks() {
        return tier >= Tiers.LOWEST && tier <= Tiers.HIGHEST ? tier : Tiers.LOWEST;
    }

    @Override
    public byte getUpdateData() {
        return (byte) looks();
    }

    @Override
    public void receiveClientEvent(byte id, byte value) {
        super.receiveClientEvent(id, value);
        if (id == GregTechTileClientEvents.CHANGE_CUSTOM_DATA && value >= Tiers.LOWEST && value <= Tiers.HIGHEST)
            tier = value;
    }

    /** The hall a hologram or the build tool makes: the length and tier channels, or else the stack's size. */
    private static int buildSlices(ItemStack trigger) {
        return trigger == null ? 1 : GTStructureChannels.STRUCTURE_LENGTH.getValueClamped(trigger, 1, MAX_SLICES);
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {
        int target = buildSlices(trigger);
        buildPiece(PIECE_FRONT, trigger, hintsOnly, ACROSS, DOWN, 0);
        for (int bay = 1; bay <= target; bay++) buildPiece(PIECE_BAY, trigger, hintsOnly, ACROSS, DOWN, -depthOf(bay));
        buildPiece(PIECE_BACK, trigger, hintsOnly, ACROSS, DOWN, -depthOf(target + 1));
    }

    @Override
    public int survivalConstruct(ItemStack trigger, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int target = buildSlices(trigger);
        int built = 0;
        for (int piece = 0; piece <= target + 1 && built < elementBudget; piece++) {
            String name = piece == 0 ? PIECE_FRONT : piece <= target ? PIECE_BAY : PIECE_BACK;
            int placed = survivalBuildPiece(
                name,
                trigger,
                ACROSS,
                DOWN,
                -depthOf(piece),
                elementBudget - built,
                env,
                false,
                true);
            if (placed > 0) built += placed;
        }
        return built == 0 ? -1 : built;
    }

    /** Runs the structure check now and tells the network */
    public boolean refreshStructure() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        checkStructure(true, tile);
        BeComServer becom = BeComServer.get();
        if (becom != null && tile.isServerSide()) {
            becom.network.ensureNode(tile.getWorld(), pos(), nodeKind(), tile.getOwnerUuid());
            registered = true;
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
        Tunables t = BeComConfig.get();
        working = mMachine && tile.isAllowedToWork() && EnergyHatches.drain(this, t.exchangePower());
        int switching = 0;
        if (working) for (MTEHatchRack rack : slots) if (rack != null && rack.isValid()) switching += rack.calls();
        calls = switching;
        if (working) becom.network.exchange(world, pos(), calls);
        tile.setActive(working);
        if (tick % TICKS_PER_SECOND == 0)
            lightRacks(working ? becom.calls.callsAt(world.provider.dimensionId, pos()) : 0);
    }

    /** Racks fill from the front: each is lit while some of the calls now are on it. */
    private void lightRacks(int now) {
        for (MTEHatchRack rack : slots) {
            if (rack == null || !rack.isValid()) continue;
            rack.light(now > 0);
            now -= rack.calls();
        }
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

    /**
     * Its screen, as the Router board lays it out: its calls, data and reach, its racks slice by slice, and a row for
     * each Line Hatch with the bus at its front. Built on the server.
     */
    public ExchangeView view() {
        BeComServer becom = BeComServer.get();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (becom == null || tile == null) return ExchangeView.EMPTY;
        Tunables t = becom.tunables;
        int dimension = tile.getWorld().provider.dimensionId;
        List<Integer> racks = new ArrayList<>();
        for (MTEHatchRack rack : slots) racks.add(
            rack == null || !rack.isValid() ? -1
                : rack.tier()
                    .ordinal());
        List<ExchangeView.Line> lines = new ArrayList<>();
        for (MTEHatchLine hatch : lineHatches.hatches()) {
            lines.add(
                new ExchangeView.Line(
                    hatch.tier()
                        .ordinal(),
                    bus(becom, t, dimension, hatch)));
        }
        int[] data = becom.apps.dataAt(new CallGraph.NodeVertex(dimension, pos()));
        String owner = tile.getOwnerName();
        return new ExchangeView(
            working,
            mMachine,
            owner == null ? "" : owner,
            tile.getXCoord(),
            tile.getYCoord(),
            tile.getZCoord(),
            Math.max(0, tier()),
            becom.calls.callsAt(dimension, pos()),
            calls,
            data[0],
            data[1],
            working ? becom.calls.reachFrom(dimension, pos()) : 0,
            working ? becom.calls.exchangesFrom(dimension, pos()) : 0,
            racks,
            lines,
            mMachine ? List.of() : why);
    }

    /** The bus of the cable at a Line Hatch's front, as the Router board shows one; null with none joined there. */
    private RouterView.Bus bus(BeComServer becom, Tunables t, int dimension, MTEHatchLine hatch) {
        IGregTechTileEntity at = hatch.getBaseMetaTileEntity();
        ForgeDirection front = at.getFrontFacing();
        Buses.Bus bus = becom.network.busOfCable(
            dimension,
            BlockPos
                .pack(at.getXCoord() + front.offsetX, at.getYCoord() + front.offsetY, at.getZCoord() + front.offsetZ));
        if (bus == null) return null;
        int phones = 0, routers = 0, gateways = 0;
        boolean joined = false;
        for (Buses.End end : bus.ends()) {
            if (end.pos() == pos()) {
                joined = true;
                continue;
            }
            switch (end.kind()) {
                case PHONE -> phones++;
                case ROUTER -> routers++;
                case GATEWAY -> gateways++;
            }
        }
        if (!joined) return null;
        Object link = NetworkService.busLink(dimension, bus);
        return new RouterView.Bus(
            bus.tier()
                .ordinal(),
            bus.cables(),
            t.cableReach(bus.tier()),
            becom.network.carries(dimension, bus) ? t.cableRate(bus.tier()) : 0,
            becom.calls.usedOn(link),
            becom.apps.dataOn(link),
            phones,
            routers,
            gateways);
    }

    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new MTEMultiBlockBaseGui<>(this) {

            /** The Router board's screen in place of Gregtech's terminal. */
            @Override
            public ModularPanel build(PosGuiData data, PanelSyncManager sync, UISettings settings) {
                return ExchangeScreen.build(MTEExchange.this::view, sync);
            }
        };
    }

    // endregion

    // region Looks and info

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int colorIndex,
        boolean active, boolean redstoneLevel) {
        ITexture casing = Textures.BlockIcons.getCasingTextureForId(
            Tiers.casing(looks())
                .getTextureId());
        if (side != facing) return new ITexture[] { casing };
        ITexture front = TextureFactory.builder()
            .addIcon(BeComTextures.EXCHANGE)
            .extFacing()
            .build();
        if (!active) return new ITexture[] { casing, front };
        return new ITexture[] { casing, front, TextureFactory.builder()
            .addIcon(BeComTextures.EXCHANGE_GLOW)
            .extFacing()
            .glow()
            .build() };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        Tunables t = BeComConfig.get();
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("becom.machine.exchange.type"))
            .addInfoAll(
                Tips.wrap(
                    Tips.MACHINE,
                    StatCollector.translateToLocal("becom.machine.exchange.info"),
                    StatCollector.translateToLocalFormatted(
                        "becom.machine.exchange.racks",
                        t.rackCallsLV(),
                        t.rackCallsMV(),
                        t.rackCallsHV()),
                    StatCollector.translateToLocal("becom.machine.exchange.tier"),
                    Tips.power(t.exchangePower(), "LV")))
            .beginVariableStructureBlock(3, 3, 5, 5, depthOf(2) + 1, depthOf(MAX_SLICES + 1) + 1, false)
            .addController(StatCollector.translateToLocal("becom.machine.exchange.controller"))
            .addCasing("45+", StatCollector.translateToLocal("becom.machine.exchange.casing"), true)
            .addCasing("11-53", StatCollector.translateToLocal("becom.machine.exchange.frame"), true)
            .addOtherStructurePart(
                StatCollector.translateToLocal("becom.hatch.rack"),
                StatCollector.translateToLocal("becom.machine.exchange.rack_hatch"),
                2)
            .addOtherStructurePart(
                StatCollector.translateToLocal("becom.hatch.line"),
                StatCollector.translateToLocal("becom.machine.exchange.line_hatch"),
                1)
            .addEnergyHatch(StatCollector.translateToLocal("becom.machine.exchange.hatch"), 1)
            .addSubChannelUsage(GTStructureChannels.STRUCTURE_LENGTH)
            .addSubChannelUsage(GTStructureChannels.TIER_MACHINE_CASING)
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
        getMTEWailaBody(stack, tip, accessor, config);
        InfoLines.read(accessor.getNBTData(), tip);
    }

    private InfoLines info() {
        InfoLines info = new InfoLines();
        BeComServer becom = BeComServer.get();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        InfoLines.state(info, mMachine, tile != null && tile.isAllowedToWork(), working);
        if (!mMachine) {
            if (!why.isEmpty()) info.problem(
                why.get(0),
                why.subList(1, why.size())
                    .toArray());
            return info;
        }
        String voltage = CableTier.ofVoltage(tier)
            .voltage();
        (slices == 1 ? info.add("becom.exchange.hall_one", voltage) : info.add("becom.exchange.hall", voltage, slices))
            .row("becom.info.row.hall");
        int[] counts = new int[CableTier.values().length];
        int count = 0;
        for (MTEHatchRack rack : slots) {
            if (rack == null || !rack.isValid()) continue;
            counts[rack.tier()
                .ordinal()]++;
            count++;
        }
        if (count == 0) info.problem("becom.exchange.no_racks");
        else {
            info.add("becom.info.of", count, slots.length)
                .row("becom.info.row.racks");
            for (CableTier rack : CableTier.values()) {
                if (counts[rack.ordinal()] > 0) info.detail(
                    "becom.exchange.rack",
                    counts[rack.ordinal()],
                    rack.voltage(),
                    BeComConfig.get()
                        .rackCalls(rack));
            }
        }
        if (becom == null || tile == null) return info;
        int dimension = tile.getWorld().provider.dimensionId;
        int now = becom.calls.callsAt(dimension, pos());
        info.add("becom.info.of", now, calls)
            .row("becom.info.row.calls");
        if (calls > 0 && now >= calls) info.warn("becom.info.calls_full", calls);
        int[] data = becom.apps.dataAt(new CallGraph.NodeVertex(dimension, pos()));
        if (data[1] > 0) info.add("becom.exchange.data_value", data[0], data[1])
            .row("becom.info.row.data");
        if (working) info.add("becom.exchange.numbers", becom.calls.reachFrom(dimension, pos()))
            .row("becom.info.row.reaches");
        InfoLines.buses(info, becom, dimension, pos());
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

    // endregion
}
