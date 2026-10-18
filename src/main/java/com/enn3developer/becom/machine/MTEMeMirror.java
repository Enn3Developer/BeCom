package com.enn3developer.becom.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.lazy;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofFrame;

import java.util.EnumSet;
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
import net.minecraftforge.common.UsernameCache;
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
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.Ownership;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import appeng.api.AEApi;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import gregtech.api.casing.Casings;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * The ME App Store Mirror: the App Store's door into an ME network. Two server cabinets of EV casings either side of a
 * fluix column behind quartz glass; its controller is its link to ME, as GT's Large Molecular Assembler's is: while
 * formed it joins the ME cable on any side and takes one channel, and unformed it joins nothing. ME Phones and ME
 * cards online in that network, in any dimension it reaches, browse, download and publish through it, and the rest of
 * BeCom does through an ME Bridge on the same network. Its energy hatches overclock its rate, as GT's multiblocks
 * overclock a recipe. A screwdriver locks it to its owner's team.
 */
public class MTEMeMirror extends MTEEnhancedMultiBlockBase<MTEMeMirror>
    implements ISurvivalConstructable, IGridProxyable {

    private static final String PIECE_CABINETS = "cabinets";
    private static final int TICKS_PER_SECOND = 20;
    private static final Casings CASING = Casings.EVMachineCasing;
    private static final Materials FRAME = Materials.Titanium;
    private static final String NUMBER = "becomNumber";
    private static final String LOCKED = "becomLocked";
    // The NBT key AE2 saves its node under.
    private static final String PROXY = "proxy";

    // Layers run top to bottom and rows from the controller's front backwards. C is a casing an energy hatch may take
    // the place of, F a frame box, G quartz glass and X a fluix block: the column the glass shows.
    private static final String[][] SHAPE = { { "CCFCC", "CCCCC", "CCCCC" }, { "CCGCC", "CCXCC", "CCCCC" },
        { "CC~CC", "CCXCC", "CCCCC" } };
    private static final int CONTROLLER_COLUMN = 2;
    private static final int CONTROLLER_LAYER = 2;

    private static final IStructureDefinition<MTEMeMirror> STRUCTURE = StructureDefinition.<MTEMeMirror>builder()
        .addShape(PIECE_CABINETS, transpose(SHAPE))
        .addElement(
            'C',
            buildHatchAdder(MTEMeMirror.class).atLeast(Energy)
                .casingIndex(CASING.getTextureId())
                .hint(1)
                .buildAndChain(onElementPass(x -> {}, CASING.asElement())))
        .addElement('F', ofFrame(FRAME))
        // AE2's blocks, looked up once a check first needs them: AE2 may register them after this class loads.
        .addElement(
            'G',
            lazy(
                () -> ofBlock(
                    AEApi.instance()
                        .definitions()
                        .blocks()
                        .quartzGlass()
                        .maybeBlock()
                        .get(),
                    0)))
        .addElement(
            'X',
            lazy(
                () -> ofBlock(
                    AEApi.instance()
                        .definitions()
                        .blocks()
                        .fluix()
                        .maybeBlock()
                        .get(),
                    0)))
        .build();

    /** How many times {@code block} appears in it. */
    private static int count(char block) {
        int n = 0;
        for (String[] layer : SHAPE) {
            for (String row : layer) {
                for (int i = 0; i < row.length(); i++) if (row.charAt(i) == block) n++;
            }
        }
        return n;
    }

    private AENetworkProxy proxy;
    // How its energy hatches run it, as its last structure check found them.
    private EnergyHatches.Fit fit = EnergyHatches.Fit.NONE;
    private int number = -1;
    private boolean locked;
    private boolean working;
    private long settledAt = Long.MIN_VALUE;
    private int settledIn;

    public MTEMeMirror(int id, String name, String regionalName) {
        super(id, name, regionalName);
    }

    public MTEMeMirror(String name) {
        super(name);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEMeMirror(mName);
    }

    /** Its item is BeCom's, as its block is. */
    @Override
    public ItemStack getStackForm(long amount) {
        return BeComMachines.stack(this, amount);
    }

    /** The rate it serves at: its own, doubled for each overclock its energy hatches give it. */
    public int rate() {
        return BeComConfig.get()
            .meMirrorRate() << fit.overclocks();
    }

    private long pos() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        return BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
    }

    // region ME network

    @Override
    public AENetworkProxy getProxy() {
        if (proxy == null) {
            proxy = new AENetworkProxy(this, PROXY, getStackForm(1), true);
            proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
            // It joins ME cable only while formed: its structure check opens its sides.
            proxy.setValidSides(EnumSet.noneOf(ForgeDirection.class));
            proxy.setIdlePowerUsage(0);
        }
        return proxy;
    }

    @Override
    public DimensionalCoord getLocation() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        return new DimensionalCoord(tile.getWorld(), tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
    }

    @Override
    public void gridChanged() {}

    @Override
    public IGridNode getGridNode(ForgeDirection side) {
        return getProxy().getNode();
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection side) {
        return AECableType.SMART;
    }

    @Override
    public void securityBreak() {}

    /** Online in an ME network: formed, powered there and holding its channel. */
    public boolean meOnline() {
        AENetworkProxy p = getProxy();
        return p.getNode() != null && p.isActive();
    }

    // GT's own tile invalidates the proxy and unloads it with its chunk, as for GT's ME hatches.
    @Override
    public void onFirstTick(IGregTechTileEntity tile) {
        super.onFirstTick(tile);
        if (tile.isServerSide()) getProxy().onReady();
    }

    // endregion

    // region Structure

    @Override
    public IStructureDefinition<MTEMeMirror> getStructureDefinition() {
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

    @Override
    public void checkMachine(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors) {
        fit = EnergyHatches.Fit.NONE;
        if (!checkPiece(PIECE_CABINETS, CONTROLLER_COLUMN, CONTROLLER_LAYER, 0, errors)) return;
        checkHasEnergyHatch(errors);
        long power = BeComConfig.get()
            .meMirrorPower();
        fit = EnergyHatches.fit(this, power, power);
    }

    /** Its link to ME opens with its structure, as the Large Molecular Assembler's does. */
    @Override
    protected void onStructureCheckFinished(IGregTechTileEntity tile) {
        super.onStructureCheckFinished(tile);
        if (!tile.isServerSide()) return;
        AENetworkProxy p = getProxy();
        boolean open = !p.getConnectableSides()
            .isEmpty();
        if (mMachine && !open) p.setValidSides(EnumSet.complementOf(EnumSet.of(ForgeDirection.UNKNOWN)));
        else if (!mMachine && open) p.setValidSides(EnumSet.noneOf(ForgeDirection.class));
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {
        buildPiece(PIECE_CABINETS, trigger, hintsOnly, CONTROLLER_COLUMN, CONTROLLER_LAYER, 0);
    }

    @Override
    public int survivalConstruct(ItemStack trigger, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            PIECE_CABINETS,
            trigger,
            CONTROLLER_COLUMN,
            CONTROLLER_LAYER,
            0,
            elementBudget,
            env,
            false,
            true);
    }

    // endregion

    // region Ticking

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        if (!tile.isServerSide()) return;
        BeComServer becom = BeComServer.get();
        if (becom == null) return;
        World world = tile.getWorld();
        long here = pos();
        int dimension = world.provider.dimensionId;
        // On the first tick after loading, and again if a mover carried it somewhere else.
        if (here != settledAt || dimension != settledIn) {
            settledAt = here;
            settledIn = dimension;
            settle();
        }
        if (mUpdated) {
            if (mUpdate <= 0) mUpdate = 50;
            mUpdated = false;
        }
        if (--mUpdate == 0 || --mStartUpCheck == 0 || tick % (TICKS_PER_SECOND * 5) == 0) checkStructure(true, tile);
        working = mMachine && tile.isAllowedToWork()
            && fit.runs()
            && meOnline()
            && EnergyHatches.drain(
                this,
                fit.power(
                    BeComConfig.get()
                        .meMirrorPower()));
        tile.setActive(working);
        if (working && becom.phones.isHome(number, PhoneKind.MIRROR, dimension, here)) {
            becom.calls.meEnd(
                number,
                becom.meGrids.idOf(
                    getProxy().getNode()
                        .getGrid()));
            becom.apps.mirrorHeard(number, tile.getOwnerUuid(), locked, rate());
        } else if (number >= 0) becom.calls.meEndGone(number);
    }

    /**
     * Claims its number again where it stands now, or takes a number of its own: on its first tick after being placed,
     * when it lost its number, or as a copy of another mirror. Its number is only how the network finds it.
     */
    private void settle() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (becom == null || tile == null) return;
        World world = tile.getWorld();
        long pos = pos();
        PhoneRecord original = number < 0 ? null : becom.phones.record(number);
        if (original != null && original.kind() == PhoneKind.MIRROR) {
            if (becom.phones.relocate(PhoneKind.MIRROR, world, pos, number)) return;
            // A copy: the original still stands with the number.
            number = becom.phones.registerCopy(world, pos, original)
                .number();
        } else {
            UUID placer = tile.getOwnerUuid();
            String name = placer == null ? null : UsernameCache.getLastKnownUsername(placer);
            number = becom.phones.registerBlockPhone(PhoneKind.MIRROR, world, pos, name == null ? null : placer, name)
                .number();
        }
        markDirty();
    }

    @Override
    public void onBlockDestroyed() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        // Only the mirror that owns the number frees it; a copy with a stale number must not.
        if (becom != null && tile != null
            && tile.isServerSide()
            && number >= 0
            && becom.phones.isHome(number, PhoneKind.MIRROR, tile.getWorld().provider.dimensionId, pos())) {
            becom.calls.meEndGone(number);
            becom.apps.mirrorGone(number);
            becom.phones.unregister(number);
        }
        super.onBlockDestroyed();
    }

    // endregion

    // region Lock

    /** A screwdriver locks it to its owner's team, or opens it to everyone again: only its owner, team or an op. */
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer player, float x, float y, float z,
        ItemStack tool) {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile == null || !tile.isServerSide()) return;
        if (!Ownership.mayManage(player, tile.getOwnerUuid())) {
            player.addChatMessage(new ChatComponentTranslation("becom.mirror.not_yours"));
            return;
        }
        locked = !locked;
        markDirty();
        player.addChatMessage(new ChatComponentTranslation(locked ? "becom.mirror.locked" : "becom.mirror.unlocked"));
    }

    // endregion

    // region Looks and info

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int colorIndex,
        boolean active, boolean redstoneLevel) {
        ITexture casing = Textures.BlockIcons.getCasingTextureForId(CASING.getTextureId());
        if (side != facing) return new ITexture[] { casing };
        ITexture front = TextureFactory.builder()
            .addIcon(BeComTextures.APP_MIRROR)
            .extFacing()
            .build();
        if (!active) return new ITexture[] { casing, front };
        return new ITexture[] { casing, front, TextureFactory.builder()
            .addIcon(BeComTextures.APP_MIRROR_GLOW)
            .extFacing()
            .glow()
            .build() };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        Tunables t = BeComConfig.get();
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("becom.machine.me_mirror.type"))
            .addInfoAll(
                Tips.wrap(
                    Tips.MACHINE,
                    StatCollector.translateToLocal("becom.machine.me_mirror.info"),
                    StatCollector.translateToLocalFormatted(
                        "becom.machine.me_mirror.rate",
                        Tips.number(t.meMirrorRate() + " KB/s")),
                    Tips.power(t.meMirrorPower(), "EV"),
                    StatCollector.translateToLocal("becom.machine.me_mirror.overclock")))
            .beginStructureBlock(5, 3, 3, false)
            .addController(StatCollector.translateToLocal("becom.machine.me_mirror.controller"))
            .addCasingInfoMin(CASING.getLocalizedName(), count('C') - 1, false)
            .addCasingInfoExactly(FRAME.getLocalizedNameForItem("%material Frame Box"), count('F'), false)
            .addCasingInfoExactly(
                AEApi.instance()
                    .definitions()
                    .blocks()
                    .quartzGlass()
                    .maybeStack(1)
                    .get()
                    .getDisplayName(),
                count('G'),
                false)
            .addCasingInfoExactly(
                AEApi.instance()
                    .definitions()
                    .blocks()
                    .fluix()
                    .maybeStack(1)
                    .get()
                    .getDisplayName(),
                count('X'),
                false)
            .addEnergyHatch(StatCollector.translateToLocal("becom.machine.me_mirror.hatch"), 1)
            .toolTipFinisher();
        return tt;
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
                ViewSync<String> text = InfoScreen.lines(MTEMeMirror.this::info);
                sync.syncValue("becomMeMirror", text);
                return ReadoutUi.intoTerminal(
                    super.createTerminalTextWidget(sync, parent),
                    text::view,
                    getTerminalWidgetWidth() - 10,
                    getTerminalWidgetHeight() - 8,
                    null);
            }
        };
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

    /** What it says about itself: its state, its ME link, its rate, the downloads it serves, and who it serves. */
    private InfoLines info() {
        InfoLines info = new InfoLines();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        InfoLines.state(info, mMachine, tile != null && tile.isAllowedToWork(), working);
        if (!mMachine) return info;
        if (meOnline()) info.add("becom.bridge.channel_held")
            .row("becom.info.row.me_network");
        else info.problem("becom.bridge.no_channel");
        info.add("becom.mirror.rate", rate())
            .row("becom.info.row.rate");
        InfoLines.overclock(info, fit, 2, "becom.info.overclocked_rate");
        BeComServer becom = BeComServer.get();
        if (becom == null || tile == null) return info;
        int[] load = becom.apps.mirrorLoad(number);
        info.add("becom.mirror.downloads", load[0], load[1])
            .row("becom.info.row.downloads")
            .brief();
        String owner = tile.getOwnerName();
        if (locked && owner != null && !owner.isEmpty()) info.add("becom.info.serves_team", owner)
            .row("becom.info.row.serves");
        else info.add("becom.info.serves_all")
            .row("becom.info.row.serves");
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
        nbt.setInteger(NUMBER, number);
        nbt.setBoolean(LOCKED, locked);
        getProxy().writeToNBT(nbt);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        super.loadNBTData(nbt);
        number = nbt.hasKey(NUMBER) ? nbt.getInteger(NUMBER) : -1;
        locked = nbt.getBoolean(LOCKED);
        if (nbt.hasKey(PROXY)) getProxy().readFromNBT(nbt);
    }

    // endregion
}
