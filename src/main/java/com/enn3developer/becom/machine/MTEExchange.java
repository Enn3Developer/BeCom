package com.enn3developer.becom.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.item.ItemSwitchingRack;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.CableTier;
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
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * The Exchange (DESIGN.md 5.4): a ground-level building of LV machine casings, three blocks each way, that switches
 * calls between every line wired to it, up to fifteen. Switching Racks in its input buses set how many calls it
 * switches at once, up to {@link #RACK_SLOTS} racks; with none it switches nothing. Pylons, terminals, antennas and
 * gateways are wired to it like to any ground node.
 * <p>
 * It isn't a recipe machine: it checks its structure on GT's schedule, drains its power each tick and tells the
 * network. A missing tick of power means offline, never a shutdown to restart by hand.
 */
@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class MTEExchange extends MTEEnhancedMultiBlockBase<MTEExchange> implements ISurvivalConstructable, NodeHost {

    /** The most racks an Exchange counts, across all its input buses. */
    public static final int RACK_SLOTS = 8;

    private static final String PIECE_BUILDING = "building";
    private static final int TICKS_PER_SECOND = 20;
    private static final int SIDE = 3;
    private static final Casings CASING = Casings.LVMachineCasing;

    // Layers run top to bottom and rows from the controller's front backwards. H is a casing an energy hatch or an
    // input bus may take the place of.
    private static final String[][] SHAPE = { { "HHH", "HHH", "HHH" }, { "H~H", "HHH", "HHH" },
        { "HHH", "HHH", "HHH" } };

    private static final IStructureDefinition<MTEExchange> STRUCTURE = StructureDefinition.<MTEExchange>builder()
        .addShape(PIECE_BUILDING, transpose(SHAPE))
        .addElement(
            'H',
            buildHatchAdder(MTEExchange.class).atLeast(Energy, InputBus)
                .casingIndex(CASING.getTextureId())
                .hint(1)
                .buildAndChain(onElementPass(x -> {}, CASING.asElement())))
        .build();

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

    @Override
    public void checkMachine(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors) {
        if (!checkPiece(PIECE_BUILDING, SIDE / 2, 1, 0, errors)) return;
        checkHasEnergyHatch(errors);
    }

    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {
        buildPiece(PIECE_BUILDING, trigger, hintsOnly, SIDE / 2, 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack trigger, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(PIECE_BUILDING, trigger, SIDE / 2, 1, 0, elementBudget, env, false, true);
    }

    /** Runs the structure check now and tells the network; the self-test calls this directly. */
    public boolean refreshStructure() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        checkStructure(true, tile);
        BeComServer becom = BeComServer.get();
        if (becom != null && tile.isServerSide()) {
            becom.network.ensureNode(tile.getWorld(), pos(), nodeKind(), tile.getOwnerUuid());
            becom.network.setFormed(tile.getWorld(), pos(), mMachine, 0);
            registered = true;
        }
        return mMachine;
    }

    // endregion

    // region Racks

    /** The racks in its input buses, best first, at most {@link #RACK_SLOTS}. */
    public List<CableTier> racks() {
        List<CableTier> racks = new ArrayList<>();
        for (MTEHatchInputBus bus : mInputBusses) {
            if (!bus.isValid()) continue;
            for (ItemStack stack : bus.mInventory) {
                CableTier tier = ItemSwitchingRack.tierOf(stack);
                if (tier != null) for (int i = 0; i < stack.stackSize; i++) racks.add(tier);
            }
        }
        racks.sort(java.util.Comparator.reverseOrder());
        return racks.size() > RACK_SLOTS ? new ArrayList<>(racks.subList(0, RACK_SLOTS)) : racks;
    }

    /** The calls a set of racks switches at once. */
    public static int callsOf(List<CableTier> racks, Tunables t) {
        int calls = 0;
        for (CableTier rack : racks) calls += t.rackCalls(rack);
        return calls;
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
        calls = working ? callsOf(racks(), t) : 0;
        if (working) becom.network.exchange(world, pos(), calls);
        tile.setActive(working);
    }

    @Override
    public void onBlockDestroyed() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        BeComServer becom = BeComServer.get();
        if (becom != null && tile != null && tile.isServerSide()) {
            becom.links.dropCables(tile.getWorld(), pos());
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
        refreshStructure();
        becom.links.wrench(player, tile.getWorld(), pos());
        return true;
    }

    // endregion

    // region Screen

    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new MTEMultiBlockBaseGui<>(this) {

            @Override
            protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager sync, ModularPanel parent) {
                // The lines as the server has them, joined; each player's client translates its own copy.
                StringSyncValue text = new StringSyncValue(() -> String.join("\n", info().encode()));
                sync.syncValue("becomExchange", text);
                return super.createTerminalTextWidget(sync, parent).child(
                    IKey.dynamic(() -> String.join("\n", InfoLines.decode(text.getValue())))
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
            .addInfo(StatCollector.translateToLocal("becom.machine.exchange.info"))
            .addInfo(
                StatCollector.translateToLocalFormatted(
                    "becom.machine.exchange.racks",
                    RACK_SLOTS,
                    t.rackCallsLV(),
                    t.rackCallsMV(),
                    t.rackCallsHV()))
            .addInfo(
                StatCollector.translateToLocalFormatted("becom.machine.exchange.links", NodeKind.EXCHANGE.linkSlots()))
            .addInfo(StatCollector.translateToLocalFormatted("becom.machine.power", t.exchangePower(), "LV"))
            .beginStructureBlock(SIDE, SIDE, SIDE, false)
            .addController(StatCollector.translateToLocal("becom.machine.exchange.controller"))
            .addCasingInfoMin(CASING.getLocalizedName(), SIDE * SIDE * SIDE - 3, false)
            .addEnergyHatch(StatCollector.translateToLocal("becom.machine.exchange.hatch"), 1)
            .addInputBus(StatCollector.translateToLocal("becom.machine.exchange.hatch"), 1)
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
        info.add(working ? "becom.info.online" : "becom.info.offline");
        BeComServer becom = BeComServer.get();
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (!mMachine) return info;
        List<CableTier> racks = racks();
        if (racks.isEmpty()) info.add("becom.exchange.no_racks");
        else {
            int[] counts = new int[CableTier.values().length];
            for (CableTier rack : racks) counts[rack.ordinal()]++;
            info.add("becom.exchange.racks", racks.size(), RACK_SLOTS);
            for (CableTier tier : CableTier.values()) {
                if (counts[tier.ordinal()] > 0) info.detail(
                    "becom.exchange.rack",
                    counts[tier.ordinal()],
                    tier.voltage(),
                    BeComConfig.get()
                        .rackCalls(tier));
            }
        }
        if (becom == null || tile == null) return info;
        int dimension = tile.getWorld().provider.dimensionId;
        info.add("becom.exchange.calls", becom.calls.callsAt(dimension, pos()), calls);
        NetworkModel model = becom.network.model(dimension);
        NetNode node = model == null ? null : model.node(pos());
        if (node != null) {
            info.add(
                "becom.info.links",
                node.links()
                    .size(),
                NodeKind.EXCHANGE.linkSlots());
            for (long other : node.links()) InfoLines.span(info, model, node.pos(), other);
        }
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
