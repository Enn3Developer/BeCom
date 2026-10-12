package com.enn3developer.becom.selftest;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.block.BeComBlocks;
import com.enn3developer.becom.block.TileTelephone;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.data.BeComNbt;
import com.enn3developer.becom.data.NetworkSavedData;
import com.enn3developer.becom.data.SavedDataFormat;
import com.enn3developer.becom.gui.AntennaScreen;
import com.enn3developer.becom.gui.AntennaView;
import com.enn3developer.becom.item.BeComItems;
import com.enn3developer.becom.item.ItemCarriedPhone;
import com.enn3developer.becom.item.ItemWalkieTalkie;
import com.enn3developer.becom.logic.DropoutModel;
import com.enn3developer.becom.logic.LevelScale;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.call.Call;
import com.enn3developer.becom.logic.call.CallBook;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.CallGraph;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.logic.phone.PhoneRegistry;
import com.enn3developer.becom.logic.radio.AntennaNetwork;
import com.enn3developer.becom.logic.radio.Coverage;
import com.enn3developer.becom.logic.radio.RadioModel;
import com.enn3developer.becom.logic.video.VideoPlan;
import com.enn3developer.becom.machine.BeComMachines;
import com.enn3developer.becom.machine.MTEAntenna;
import com.enn3developer.becom.machine.MTELineTerminal;
import com.enn3developer.becom.machine.MTEMeBridge;
import com.enn3developer.becom.machine.MTEPylonHead;
import com.enn3developer.becom.machine.MTETransceiver;
import com.enn3developer.becom.machine.MTEVideophone;
import com.enn3developer.becom.packet.AnalyzerPacket;
import com.enn3developer.becom.packet.TonePacket;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.quest.QuestTask;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.CallService;
import com.enn3developer.becom.server.CarriedPhones;
import com.enn3developer.becom.server.LinkService;
import com.enn3developer.becom.server.PhoneHandle;
import com.enn3developer.becom.server.PhoneScreenActions;
import com.enn3developer.becom.server.RadioService;
import com.enn3developer.becom.voice.CallGroup;
import com.enn3developer.becom.voice.CallSnapshot;
import com.enn3developer.becom.voice.RadioGroup;
import com.enn3developer.becom.voice.RadioSnapshot;
import com.enn3developer.gtnhvoice.api.server.GtnhVoiceApi;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.mojang.authlib.GameProfile;

import appeng.api.AEApi;
import appeng.api.definitions.IBlocks;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEColor;
import appeng.api.util.IOrientable;
import appeng.tile.misc.TileSecurity;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import ic2.api.item.ElectricItem;

/**
 * {@code /becom selftest} (DESIGN.md 16.3): the checks that need a real server. It builds what it needs in a scratch
 * area far from spawn, lets the machines tick, prints one line per check, then
 * {@code BeCom self-test: N passed, M failed}, and stops the server. Only exists in the development environment.
 */
public final class SelfTest {

    private static final String PREFIX = "BeCom self-test: ";
    // Far from spawn, high above the terrain, and laid out so separate test groups are out of each other's span.
    private static final int X0 = 30_000;
    private static final int Z0 = 30_000;
    private static final int Y = 210;
    private static final int GROUP_GAP = 80;
    private static final int TICKS_PER_SECOND = 20;
    private static final String RELOAD_DATABASE = "becom_selftest_reload";
    // Enough for every machine to tick, form, scan its cable and send heartbeats, with the 2 s offline window.
    private static final int SETTLE_TICKS = 60;
    private static final int TALLEST_STACK = 11;
    private static final double EPS = 1e-6;

    private static SelfTest running;

    private final BeComServer becom;
    private final ICommandSender sender;
    private final WorldServer world;
    private final FakePlayer owner;
    private final FakePlayer stranger;
    private boolean fakePlacedOwnerless;
    private final List<Step> steps = new ArrayList<>();
    private final List<IGregTechTileEntity> powered = new ArrayList<>();
    // The chunks it builds in. An autosave queues every chunk no player watches for unloading, so these are loaded
    // again after every tick's save, before the next tick unloads anything.
    private final Set<ChunkCoordIntPair> keepLoaded = new HashSet<>();
    private int index;
    private long edgeHoldStart;
    private boolean edgeDroppedEarly;
    private long tick;
    private long waitUntil;
    private int passed;
    private int failed;

    // What the steps build and later check.
    private final Line repeaterLine = new Line();
    private final Line passive260 = new Line();
    private final Line passive310 = new Line();
    private MTEPylonHead powerRepeater;
    private MTEPylonHead powerRouter;
    private MTEPylonHead shortPylon;
    private MTEPylonHead minPylon;
    private MTEPylonHead clearA;
    private MTEPylonHead clearB;
    private MTEPylonHead autoA;
    private MTEPylonHead autoB;
    private UUID questProbe;
    private final Map<String, Integer> sidesAccepted = new java.util.HashMap<>();

    // 0.2's radio checks, in their own areas: two T1 antennas with a line bridged onto each, and the walkies that talk
    // through them; walkies and a Transceiver out of every antenna's reach; and T2, T3 and a short mast out of
    // everyone's reach.
    private static final int RADIO_Z = Z0 + GROUP_GAP * 9;
    private static final int DIRECT_Z = RADIO_Z + 150;
    private static final int FORM_Z = RADIO_Z + 600;
    private static final int ANTENNA_GAP = 60;
    private MTEAntenna antennaA;
    private MTEAntenna antennaB;
    private MTEAntenna antennaT2;
    private MTEAntenna antennaT3;
    private MTEAntenna antennaShort;
    private MTELineTerminal bridgeTerminalA;
    private MTELineTerminal bridgeTerminalB;
    private TileTelephone bridgePhoneA;
    private TileTelephone bridgePhoneB;
    private MTETransceiver transceiver;
    private FakePlayer alice;
    private FakePlayer bob;
    private FakePlayer carol;
    private FakePlayer dave;
    private FakePlayer eve;
    private FakePlayer frank;
    private FakePlayer gina;
    private FakePlayer hank;
    private FakePlayer iris;

    /** One test line: terminals at both ends, heads between, and a Telephone on each terminal's cable. */
    private static final class Line {

        MTELineTerminal start;
        MTELineTerminal end;
        final List<MTEPylonHead> heads = new ArrayList<>();
        TileTelephone phoneStart;
        TileTelephone phoneEnd;
    }

    @FunctionalInterface
    private interface Step {

        /** Runs the step; returns how many ticks to wait before the next. */
        int run() throws Exception;
    }

    private SelfTest(BeComServer becom, ICommandSender sender) {
        this.becom = becom;
        this.sender = sender;
        this.world = DimensionManager.getWorld(0);
        this.owner = FakePlayerFactory
            .get(world, new GameProfile(UUID.fromString("b3c0b3c0-0000-4000-8000-000000000001"), "BeComSelfTest"));
        this.stranger = FakePlayerFactory
            .get(world, new GameProfile(UUID.fromString("b3c0b3c0-0000-4000-8000-000000000002"), "BeComStranger"));
        BeComQuests.recordForSelfTest();
        steps.add(this::checkRegistration);
        steps.add(this::build);
        steps.add(this::formAndLink);
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) steps.add(() -> powerFrom(side));
        steps.add(this::checkPower);
        steps.add(this::checkStructuresAndLines);
        steps.add(this::checkCall);
        steps.add(this::switchRepeaterOff);
        steps.add(this::checkRepeaterOff);
        steps.add(this::checkQuests);
        steps.add(this::checkQuestReload);
        steps.add(this::checkRadioRegistration);
        steps.add(this::buildRadio);
        steps.add(this::formRadio);
        steps.add(this::checkAntennas);
        steps.add(this::keyDirect);
        steps.add(this::checkDirect);
        steps.add(this::checkTransceiver);
        steps.add(this::checkLoudspeaker);
        steps.add(this::keyThroughAntennas);
        steps.add(this::checkThroughAntennas);
        steps.add(this::keyLocked);
        steps.add(this::checkLocked);
        steps.add(this::checkFlatWalkie);
        steps.add(this::checkOffWalkie);
        steps.add(this::keyEdge);
        steps.add(this::checkEdge);
        steps.add(this::startEdgeHold);
        // Evaluations with the link at 5.8 dB, inside the hysteresis but below Low, until the drop delay has passed.
        steps.add(this::holdEdge);
        steps.add(this::checkEdgeDrop);
        steps.add(this::checkNewerAntenna);
        steps.add(this::checkPhoneRegistration);
        steps.add(this::buildPhones);
        steps.add(this::formPhones);
        steps.add(this::checkMobileService);
        steps.add(this::checkMobileCall);
        steps.add(this::checkGroupCall);
        steps.add(this::checkPhonePower);
        steps.add(this::checkPhoneDrain);
        steps.add(this::checkTones);
        steps.add(this::checkBusyToneStops);
        steps.add(this::checkPhoneMoves);
        steps.add(this::startRain);
        steps.add(this::checkRain);
        steps.add(this::checkCoverage);
        steps.add(this::checkMeRegistration);
        steps.add(this::buildMe);
        steps.add(this::placeMeBridges);
        steps.add(this::linkMe);
        steps.add(this::checkMe);
        steps.add(this::checkVideophoneRegistration);
        steps.add(this::buildVideophone);
        steps.add(this::formVideophone);
        steps.add(this::checkVideophoneCall);
        steps.add(this::checkVideophoneRoom);
        steps.add(this::checkVideophoneEmpty);
        steps.add(this::holdVideophone);
        steps.add(this::checkVideophoneHungUp);
        steps.add(this::checkVideophonePower);
        steps.add(this::startVideoCall);
        steps.add(this::checkVideoCall);
        steps.add(this::checkProfileSeen);
        steps.add(this::checkProfileGone);
        steps.add(this::checkProfileAgain);
        steps.add(this::checkProfileResent);
        steps.add(this::checkVideoSwitches);
        steps.add(this::checkVideoEnded);
    }

    public static void run(BeComServer becom, ICommandSender sender) {
        if (running != null) {
            sender.addChatMessage(new ChatComponentText(PREFIX + "already running"));
            return;
        }
        running = new SelfTest(becom, sender);
        running.log("starting");
    }

    /** Server tick hook; does nothing unless a self-test is running. */
    public static void tick() {
        if (running == null) return;
        // One cut short by its world stopping must not carry on in the next world, against the old one's services.
        if (running.becom != BeComServer.get()) {
            running = null;
            return;
        }
        running.step();
    }

    private void keepLoaded(int cx, int cz) {
        world.theChunkProviderServer.loadChunk(cx, cz);
        keepLoaded.add(new ChunkCoordIntPair(cx, cz));
    }

    private void step() {
        tick++;
        // With no players in it, a world stops ticking entities and tile entities after a minute, and the self-test's
        // players are fake: without this, every machine it built would stop at 1200 ticks.
        world.resetUpdateEntityTick();
        for (ChunkCoordIntPair chunk : keepLoaded)
            world.theChunkProviderServer.loadChunk(chunk.chunkXPos, chunk.chunkZPos);
        // A test power supply: the line's machines are topped up every tick. Taking power from a real cable is
        // checked on its own, through the call GT cables make.
        for (IGregTechTileEntity tile : powered) {
            if (!tile.isInvalidTileEntity())
                tile.increaseStoredEnergyUnits(tile.getEUCapacity() - tile.getStoredEU(), true);
        }
        if (tick < waitUntil) return;
        if (index >= steps.size()) {
            finish();
            return;
        }
        Step step = steps.get(index++);
        try {
            waitUntil = tick + step.run();
        } catch (Throwable t) {
            BeCom.LOG.error(PREFIX + "step {} threw", index, t);
            fail("step " + index + " completes", t.toString());
            waitUntil = tick + 1;
        }
    }

    private void finish() {
        log(passed + " passed, " + failed + " failed");
        running = null;
        becom.server.initiateShutdown();
    }

    // region Reporting

    private void log(String line) {
        BeCom.LOG.info(PREFIX + line);
        if (!(sender instanceof net.minecraft.server.MinecraftServer)) {
            sender.addChatMessage(new ChatComponentText(PREFIX + line));
        }
    }

    private void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            log("PASS " + name);
        } else {
            fail(name, detail);
        }
    }

    private void fail(String name, String detail) {
        failed++;
        log("FAIL " + name + (detail == null || detail.isEmpty() ? "" : ": " + detail));
    }

    private void near(String name, double expected, double actual) {
        check(name, Math.abs(expected - actual) < EPS, "expected " + expected + ", got " + actual);
    }

    // endregion

    // region Steps

    private int checkRegistration() {
        for (Map.Entry<Integer, String> entry : BeComMachines.ids()
            .entrySet()) {
            IMetaTileEntity mte = GregTechAPI.METATILEENTITIES[entry.getKey()];
            check(
                "machine ID " + entry.getKey() + " is " + entry.getValue(),
                mte != null && entry.getValue()
                    .equals(mte.getMetaName())
                    && mte.getClass()
                        .getName()
                        .startsWith("com.enn3developer.becom"),
                mte == null ? "empty" : mte.getMetaName());
        }
        try {
            BeComMachines.checkIds();
            fail("machine ID check stops on a taken ID", "it passed with every ID taken");
        } catch (IllegalStateException e) {
            check(
                "machine ID check stops on a taken ID, naming the machine",
                e.getMessage()
                    .contains("becom.pylon_head"),
                e.getMessage());
        }
        check(
            "voice group becom:call is registered",
            GtnhVoiceApi.groupManager()
                .byName(CallGroup.NAME) == becom.calls.group(),
            "byName returned " + GtnhVoiceApi.groupManager()
                .byName(CallGroup.NAME));
        check(
            "config holds DESIGN.md 17's defaults",
            BeComConfig.get()
                .equals(Tunables.defaults()),
            BeComConfig.get()
                .toString());

        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger(SavedDataFormat.KEY, NetworkSavedData.FORMAT + 1);
        NetworkSavedData tooNew = new NetworkSavedData(NetworkSavedData.NAME);
        tooNew.readFromNBT(newer);
        check("saved data newer than BeCom is kept and never saved", tooNew.isTooNew() && !tooNew.isDirty(), "");

        boolean cable = false, telephone = false;
        for (GTRecipe recipe : RecipeMaps.assemblerRecipes.getAllRecipes()) {
            for (ItemStack out : recipe.mOutputs) {
                if (out == null) continue;
                if (out.getItem() == itemOf(BeComBlocks.phoneCable) && out.stackSize == 2) cable = true;
                if (out.getItem() == itemOf(BeComBlocks.telephone)) telephone = true;
            }
        }
        check("assembler makes Phone Cable x2", cable, "");
        checkCableRecipeDoesNotShadowGt();
        check("assembler makes the Telephone", telephone, "");
        for (MTEPylonHead head : List
            .of(BeComMachines.pylonHead, BeComMachines.repeaterHead, BeComMachines.routerHead)) {
            check("crafting recipe for " + head.getMetaName(), hasCraftingRecipe(head.getStackForm(1)), "");
        }
        check(
            "crafting recipe for " + BeComMachines.lineTerminal.getMetaName(),
            hasCraftingRecipe(BeComMachines.lineTerminal.getStackForm(1)),
            "");
        return 1;
    }

    /**
     * Phone Cable and GT's Red Alloy Cable both start from a red alloy wire and molten rubber. GT checks a machine's
     * last recipe first, so a Phone Cable recipe whose inputs are a subset of GT's would keep making Phone Cable from
     * GT's inputs. A programmed circuit of its own keeps them apart.
     */
    private void checkCableRecipeDoesNotShadowGt() {
        GTRecipe phoneCable = null;
        for (GTRecipe recipe : RecipeMaps.assemblerRecipes.getAllRecipes()) {
            for (ItemStack out : recipe.mOutputs) {
                if (out != null && out.getItem() == itemOf(BeComBlocks.phoneCable)) phoneCable = recipe;
            }
        }
        if (phoneCable == null) {
            fail("GT's red alloy cable recipe still wins after Phone Cable", "no Phone Cable recipe");
            return;
        }
        ItemStack wire = GTOreDictUnificator.get(OrePrefixes.wireGt01, Materials.RedAlloy, 1);
        ItemStack gtCable = GTOreDictUnificator.get(OrePrefixes.cableGt01, Materials.RedAlloy, 1);
        GTRecipe found = RecipeMaps.assemblerRecipes.findRecipeQuery()
            .items(wire, GTUtility.getIntegratedCircuit(24))
            .fluids(Materials.Rubber.getMolten(144))
            .voltage(GTValues.V[1])
            .cachedRecipe(phoneCable)
            .find();
        check(
            "GT's red alloy cable recipe still wins after Phone Cable",
            found != null && found != phoneCable
                && found.mOutputs[0] != null
                && gtCable != null
                && found.mOutputs[0].isItemEqual(gtCable),
            found == null ? "nothing found" : String.valueOf(found.mOutputs[0]));
        List<GTRecipe> ours = RecipeMaps.assemblerRecipes.findRecipeQuery()
            .items(phoneCable.mInputs)
            .fluids(phoneCable.mFluidInputs)
            .voltage(GTValues.V[1])
            .findAll()
            .collect(java.util.stream.Collectors.toList());
        StringBuilder matched = new StringBuilder();
        for (GTRecipe recipe : ours) matched.append(recipe.mOutputs.length > 0 ? recipe.mOutputs[0] : "?")
            .append(' ');
        check("Phone Cable's own inputs make only Phone Cable", ours.size() == 1, matched.toString());
    }

    private static net.minecraft.item.Item itemOf(Block block) {
        return net.minecraft.item.Item.getItemFromBlock(block);
    }

    @SuppressWarnings("unchecked")
    private static boolean hasCraftingRecipe(ItemStack output) {
        for (IRecipe recipe : (List<IRecipe>) CraftingManager.getInstance()
            .getRecipeList()) {
            ItemStack out = recipe.getRecipeOutput();
            if (out != null && out.getItem() == output.getItem() && out.getItemDamage() == output.getItemDamage()) {
                return true;
            }
        }
        return false;
    }

    private int build() {
        // Far from spawn nothing is loaded, and an unloaded chunk silently swallows setBlock; load the whole area.
        for (int cx = (X0 - 16) >> 4; cx <= (X0 + 330) >> 4; cx++) {
            for (int cz = (Z0 - 16) >> 4; cz <= (Z0 + GROUP_GAP * 7) >> 4; cz++) {
                keepLoaded(cx, cz);
            }
        }
        clearScratchArea();
        int z = Z0;
        // The milestone's "Done when" line: 300 blocks, one repeater halfway, a terminal and Telephone at each end.
        buildLine(repeaterLine, z, new int[] { 30, 90, 150, 210, 270 }, 300, 2);
        z += GROUP_GAP;
        buildLine(passive260, z, new int[] { 50, 110, 170, 230 }, 260, -1);
        z += GROUP_GAP;
        buildLine(passive310, z, new int[] { 30, 90, 150, 210, 270 }, 310, -1);
        z += GROUP_GAP;

        powerRepeater = head(BeComMachines.repeaterHead, X0, Y, z, 0);
        powerRouter = head(BeComMachines.routerHead, X0 + 4, Y, z, 0);
        z += GROUP_GAP;

        minPylon = head(BeComMachines.pylonHead, X0, Y, z, 1);
        shortPylon = head(BeComMachines.pylonHead, X0 + 70, Y, z, 1);
        // One frame short of the shortest pylon.
        world.setBlockToAir(
            X0 + 70,
            Y - BeComConfig.get()
                .pylonMinHeight(),
            z);
        z += GROUP_GAP;

        clearA = head(BeComMachines.pylonHead, X0, Y, z, TALLEST_STACK);
        clearB = head(BeComMachines.pylonHead, X0 + 40, Y, z, TALLEST_STACK);
        world.setBlock(X0 + 20, Y, z, Blocks.stone);
        z += GROUP_GAP;

        autoA = head(BeComMachines.pylonHead, X0, Y, z, TALLEST_STACK);
        autoB = place(BeComMachines.pylonHead, X0 + 40, Y, z);
        return 2;
    }

    /**
     * A dev world keeps what an earlier self-test built, links and switched-off machines included; the run starts
     * from nothing so its checks mean the same every time.
     */
    private void clearScratchArea() {
        NetworkModel model = becom.network.model(world);
        int zEnd = Z0 + GROUP_GAP * 7;
        for (NetNode node : new ArrayList<>(model.nodes())) {
            if (node.x() < X0 - 16 || node.x() > X0 + 330 || node.z() < Z0 - 16 || node.z() > zEnd) continue;
            world.setBlockToAir(node.x(), node.y(), node.z());
            model.remove(node.pos());
        }
        for (int z = Z0; z <= zEnd; z += GROUP_GAP) {
            for (int x : new int[] { X0, X0 + 260, X0 + 300, X0 + 310 }) {
                world.setBlockToAir(x, Y, z + 2);
                world.setBlockToAir(x, Y, z + 1);
            }
        }
    }

    /** Builds one line along x: a terminal at 0, pylon heads at {@code heads}, a terminal at {@code length}. */
    private void buildLine(Line line, int z, int[] heads, int length, int repeaterAt) {
        line.start = place(BeComMachines.lineTerminal, X0, Y, z);
        line.end = place(BeComMachines.lineTerminal, X0 + length, Y, z);
        powered.add(line.start.getBaseMetaTileEntity());
        powered.add(line.end.getBaseMetaTileEntity());
        for (int i = 0; i < heads.length; i++) {
            MTEPylonHead kind = i == repeaterAt ? BeComMachines.repeaterHead : BeComMachines.pylonHead;
            MTEPylonHead head = head(kind, X0 + heads[i], Y, z, TALLEST_STACK);
            line.heads.add(head);
            if (i == repeaterAt) powered.add(head.getBaseMetaTileEntity());
        }
        line.phoneStart = telephone(X0, Y, z + 2);
        world.setBlock(X0, Y, z + 1, BeComBlocks.phoneCable);
        line.phoneEnd = telephone(X0 + length, Y, z + 2);
        world.setBlock(X0 + length, Y, z + 1, BeComBlocks.phoneCable);
    }

    @SuppressWarnings("unchecked")
    private <T extends IMetaTileEntity> T place(T machine, int x, int y, int z) {
        return placeAs(machine, x, y, z, owner);
    }

    /** Places a machine the way {@code player} placing its item does. */
    private <T extends IMetaTileEntity> T placeAs(T machine, int x, int y, int z, FakePlayer player) {
        world.getChunkFromBlockCoords(x, z);
        ItemStack stack = machine.getStackForm(1);
        ((ItemBlock) stack.getItem()).placeBlockAt(stack, player, world, x, y, z, 1, 0.5f, 0.5f, 0.5f, 0);
        TileEntity tile = world.getTileEntity(x, y, z);
        return (T) ((IGregTechTileEntity) tile).getMetaTileEntity();
    }

    /** Places a head and builds its frame column the way the hologram projector does. */
    private MTEPylonHead head(MTEPylonHead machine, int x, int y, int z, int stackSize) {
        MTEPylonHead head = place(machine, x, y, z);
        if (stackSize > 0) {
            ItemStack trigger = machine.getStackForm(stackSize);
            ((IConstructable) head).construct(trigger, false);
        }
        return head;
    }

    private TileTelephone telephone(int x, int y, int z) {
        world.setBlock(x, y, z, BeComBlocks.telephone);
        BeComBlocks.telephone.onBlockPlacedBy(world, x, y, z, owner, new ItemStack(BeComBlocks.telephone));
        TileTelephone tile = (TileTelephone) world.getTileEntity(x, y, z);
        // A fake player, like a machine placing blocks, never owns a phone (X6). The self-test's owner stands in for
        // the real player who would place it.
        PhoneRecord record = becom.phones.record(tile.number());
        if (record != null && record.owner() == null) {
            fakePlacedOwnerless = true;
            record.setOwner(owner.getUniqueID(), owner.getCommandSenderName());
        } else {
            fakePlacedOwnerless = false;
        }
        return tile;
    }

    private int formAndLink() {
        for (Line line : List.of(repeaterLine, passive260, passive310)) {
            for (MTEPylonHead head : line.heads) head.refreshStructure();
        }
        for (MTEPylonHead head : List.of(powerRepeater, powerRouter, minPylon, shortPylon, clearA, clearB, autoA))
            head.refreshStructure();
        for (Line line : List.of(repeaterLine, passive260, passive310)) {
            line.start.scanCable();
            line.end.scanCable();
            link(line.start, line.heads.get(0));
            for (int i = 0; i + 1 < line.heads.size(); i++) link(line.heads.get(i), line.heads.get(i + 1));
            link(line.heads.get(line.heads.size() - 1), line.end);
        }
        return SETTLE_TICKS;
    }

    private static long pos(IMetaTileEntity mte) {
        IGregTechTileEntity tile = mte.getBaseMetaTileEntity();
        return BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
    }

    /** Links two nodes with the wrench flow, unless forming already linked them. */
    private LinkService.Outcome link(IMetaTileEntity a, IMetaTileEntity b) {
        NetworkModel model = becom.network.model(world);
        if (model.linked(pos(a), pos(b))) return LinkService.Outcome.LINKED;
        becom.links.forget(owner.getUniqueID());
        becom.links.wrench(owner, world, pos(a));
        return becom.links.wrench(owner, world, pos(b));
    }

    private int checkStructuresAndLines() {
        Tunables t = BeComConfig.get();
        NetworkModel model = becom.network.model(world);

        check(
            "pylon of " + t.pylonMinHeight() + " frames forms",
            minPylon.refreshStructure() && minPylon.height() == t.pylonMinHeight(),
            "height " + minPylon.height());
        check("pylon one frame short doesn't form", !shortPylon.refreshStructure(), "height " + shortPylon.height());
        boolean allTall = true;
        for (MTEPylonHead head : repeaterLine.heads) allTall &= head.height() == t.pylonMaxHeight();
        check("line pylons of " + t.pylonMaxHeight() + " frames form", allTall, "");

        check(
            "a pylon forming with one pylon in range links to it on its own",
            model.linked(pos(autoA), pos(autoB)) || formsAndAutoLinks(),
            "");

        for (Line line : List.of(repeaterLine, passive260, passive310)) {
            check(
                "terminal serves the one Telephone on its cable",
                line.start.phone() == line.phoneStart.number() && line.end.phone() == line.phoneEnd.number(),
                line.start.phone() + "/" + line.phoneStart.number());
            boolean chain = model.linked(pos(line.start), pos(line.heads.get(0)))
                && model.linked(pos(line.heads.get(line.heads.size() - 1)), pos(line.end));
            for (int i = 0; i + 1 < line.heads.size(); i++) {
                chain &= model.linked(pos(line.heads.get(i)), pos(line.heads.get(i + 1)));
            }
            check("wrench links the whole line", chain, "");
        }

        // Clearance (D20): a block on the line refuses the link; cleared, it links; blocked again, the re-check
        // marks it.
        LinkService.Outcome blocked = link(clearA, clearB);
        check("a solid block on the line refuses the link", blocked == LinkService.Outcome.BLOCKED, blocked.name());
        int cz = clearA.getBaseMetaTileEntity()
            .getZCoord();
        world.setBlockToAir(X0 + 20, Y, cz);
        LinkService.Outcome linked = link(clearA, clearB);
        check("with the block gone, it links", linked == LinkService.Outcome.LINKED, linked.name());
        world.setBlock(X0 + 20, Y, cz, Blocks.stone);
        becom.network.recheckAll();
        check("the re-check finds the line blocked again", model.isBlocked(pos(clearA), pos(clearB)), "");

        // Unlinking is the owner's (D37): a stranger's wrench can't cut it.
        becom.links.forget(stranger.getUniqueID());
        becom.links.wrench(stranger, world, pos(clearA));
        LinkService.Outcome cut = becom.links.wrench(stranger, world, pos(clearB));
        check(
            "a stranger can't unlink",
            cut == LinkService.Outcome.NOT_ALLOWED && model.linked(pos(clearA), pos(clearB)),
            cut.name());

        LevelScale levels = t.levelScale();
        DropoutModel dropouts = t.dropoutModel(Tunables.FRAME_MILLIS);
        double width = becom.network.graph()
            .width(repeaterLine.phoneStart.number(), repeaterLine.phoneEnd.number());
        near("300-block line with a repeater: weakest stretch 30 - 0.08 x 150 = 18 dB", 18, width);
        check(
            "...which is High, capped at Low by the Telephones",
            levels.levelOf(width) == SignalLevel.HIGH
                && SignalLevel.min(levels.levelOf(width), SignalLevel.LOW) == SignalLevel.LOW,
            "");
        near("...and loses no frames", 0, dropouts.lossRate(width));
        check(
            "...and each Telephone has the other in its contacts",
            becom.calls.reachableFrom(repeaterLine.phoneStart.number())
                .contains(repeaterLine.phoneEnd.number()),
            "");
        PhoneRegistry registry = becom.phones.registry();
        List<PhoneRegistry.Contact> contacts = registry.contacts(
            owner.getUniqueID(),
            repeaterLine.phoneStart.number(),
            becom.calls.reachableFrom(repeaterLine.phoneStart.number()));
        check(
            "contacts list the other phone by its owner's name",
            contacts.size() == 1 && contacts.get(0)
                .ownerName()
                .equals(owner.getCommandSenderName()),
            contacts.toString());

        double w260 = becom.network.graph()
            .width(passive260.phoneStart.number(), passive260.phoneEnd.number());
        near("260 blocks with no repeater: 30 - 0.08 x 260 = 9.2 dB", 9.2, w260);
        near("...which loses 14% of frames, in bursts", 0.14, dropouts.lossRate(w260));
        double w310 = becom.network.graph()
            .width(passive310.phoneStart.number(), passive310.phoneEnd.number());
        near("310 blocks with no repeater: 5.2 dB", 5.2, w310);
        check(
            "...below Low, so the far phone can't be reached",
            !becom.calls.reachableFrom(passive310.phoneStart.number())
                .contains(passive310.phoneEnd.number()),
            "");
        return 1;
    }

    /**
     * DESIGN.md 16.2: Repeater and Router heads take power straight from a cable. A GT cable hands power over with
     * IEnergyConnected.injectEnergyUnits on the receiving machine, once a tick, and the head takes 1 A a tick in all;
     * so this feeds one side per tick, the same call a cable makes, and counts the sides that took it.
     */
    private int powerFrom(ForgeDirection side) {
        for (MTEPylonHead head : List.of(powerRepeater, powerRouter)) {
            IGregTechTileEntity tile = head.getBaseMetaTileEntity();
            tile.decreaseStoredEnergyUnits(tile.getStoredEU(), true);
            long before = tile.getStoredEU();
            if (tile.injectEnergyUnits(side, gregtech.api.enums.GTValues.V[1], 1) > 0 && tile.getStoredEU() > before) {
                sidesAccepted.merge(head.getMetaName(), 1, Integer::sum);
            }
        }
        return 1;
    }

    private int checkPower() {
        for (MTEPylonHead head : List.of(powerRepeater, powerRouter)) {
            int accepted = sidesAccepted.getOrDefault(head.getMetaName(), 0);
            check(head.getMetaName() + " takes LV power from a cable on every side", accepted == 6, accepted + " of 6");
        }
        return 1;
    }

    /** autoB had no structure when autoA formed; building its column now must link it to autoA on its own. */
    private boolean formsAndAutoLinks() {
        ((IConstructable) autoB).construct(BeComMachines.pylonHead.getStackForm(TALLEST_STACK), false);
        autoB.refreshStructure();
        return becom.network.model(world)
            .linked(pos(autoA), pos(autoB));
    }

    /**
     * A call through the call service, as players make one but with fake players: picking up, dialling through every
     * check the service makes (voice, do-not-disturb, the block list, a busy handset, the path), answering, the levels
     * the Telephone's own cap gives, the routes from the very first frame, hanging up and the history it leaves.
     */
    private int checkCall() {
        CallService calls = becom.calls;
        CallBook book = calls.book();
        TileTelephone start = repeaterLine.phoneStart, end = repeaterLine.phoneEnd;
        int a = start.number(), b = end.number();
        UUID alice = owner.getUniqueID(), bob = stranger.getUniqueID();
        // The fake players have no voice session; the real check would refuse them before anything else.
        calls.voiceForSelfTest(id -> id.equals(alice) || id.equals(bob));
        standBy(owner, start);
        standBy(stranger, end);

        check("a Telephone placed by a fake player has no owner of its own", fakePlacedOwnerless, "");
        check("picking up takes the handset", calls.takeHandset(owner, start) == CallService.Pickup.TAKEN, "");
        CallBook.DialResult result = calls.dial(owner, a, b);
        check(
            "dialling rings the other Telephone",
            result == CallBook.DialResult.RINGING && end.ringing(),
            result.name());
        check("answering connects", calls.takeHandset(stranger, end) == CallService.Pickup.ANSWERED, "");
        // Both Telephones are the owner's; the screen names the player on the other end, as the history does.
        String shown = PhoneScreenActions.view(owner, new PhoneHandle.Telephone(start))
            .otherName();
        check(
            "the phone screen names the player on the other end, not the phone's owner",
            shown.equals(stranger.getCommandSenderName()),
            shown);
        Call call = book.callOf(alice);
        // No tick and no evaluation in between: a call carries voice from its first frame.
        check(
            "the routing snapshot carries each side to the other the moment it connects",
            calls.group()
                .snapshot()
                .hasPair(alice, bob)
                && calls.group()
                    .snapshot()
                    .hasPair(bob, alice),
            "");
        Collection<UUID> members = GtnhVoiceApi.groupManager()
            .membersOf(calls.group());
        check("both players join becom:call", members.contains(alice) && members.contains(bob), members.toString());
        check("the ring stops", !end.ringing(), "");
        // 18 dB a stretch is High on the line, so Low here is the Telephone's cap (D10).
        check(
            "levels are Low both ways, the Telephone's cap",
            call != null && call.level(alice, bob) == SignalLevel.LOW && call.level(bob, alice) == SignalLevel.LOW,
            call == null ? "no call" : call.level(alice, bob) + "/" + call.level(bob, alice));
        calls.hangUpHandset(bob);
        members = GtnhVoiceApi.groupManager()
            .membersOf(calls.group());
        check("hanging up leaves becom:call", !members.contains(alice) && !members.contains(bob), members.toString());
        check("the handset goes back on the hook", !end.offHook(), "");
        PhoneRecord ra = becom.phones.record(a), rb = becom.phones.record(b);
        check(
            "both phones remember the call, with who was on the other end",
            !ra.history()
                .isEmpty()
                && ra.history()
                    .get(0)
                    .direction() == PhoneRecord.Direction.OUTGOING
                && bob.equals(
                    ra.history()
                        .get(0)
                        .otherPlayer())
                && !rb.history()
                    .isEmpty()
                && rb.history()
                    .get(0)
                    .direction() == PhoneRecord.Direction.INCOMING,
            "");

        // Alice still holds her handset. Do-not-disturb, the block list and a busy handset refuse her (DESIGN.md 10,
        // 12.3).
        rb.setDoNotDisturb(true);
        result = calls.dial(owner, a, b);
        check("do-not-disturb makes the phone unreachable", result == CallBook.DialResult.UNREACHABLE, result.name());
        rb.setDoNotDisturb(false);
        rb.block(alice, owner.getCommandSenderName());
        result = calls.dial(owner, a, b);
        check(
            "a blocked caller can't reach the phone and doesn't see it",
            result == CallBook.DialResult.UNREACHABLE && registry().contacts(alice, a, calls.reachableFrom(a))
                .isEmpty(),
            result.name());
        rb.unblock(alice);
        check(
            "someone else picks up the other handset",
            calls.takeHandset(stranger, end) == CallService.Pickup.TAKEN,
            "");
        result = calls.dial(owner, a, b);
        check("a handset off the hook is busy", result == CallBook.DialResult.BUSY, result.name());
        calls.hangUpHandset(bob);
        // Whether a phone rings isn't saved with its chunk; a phone loading mid-ring rings again.
        calls.dial(owner, a, b);
        end.setRinging(false);
        end.restoreRinging();
        check("a phone that loads while a call rings it rings again", end.ringing(), "");
        calls.hangUpHandset(alice);
        // Nobody answered: the caller's row names the other phone's owner at the time, so a later block can't land on
        // whoever holds that number next.
        check(
            "an unanswered call records the other phone's owner",
            ra.history()
                .get(0)
                .direction() == PhoneRecord.Direction.OUTGOING && rb.owner()
                    .equals(
                        ra.history()
                            .get(0)
                            .otherPlayer()),
            String.valueOf(
                ra.history()
                    .get(0)
                    .otherPlayer()));
        checkCopyDuringCall(a, b, start, end, alice, bob);
        calls.voiceForSelfTest(
            id -> GtnhVoiceApi.session(id)
                .isPresent());

        checkMovedPhone(end.zCoord);
        checkCarriedTile(end.zCoord);

        // As after a restart: the off-hook bit was saved with the chunk, but nobody holds the handset any more.
        end.setOffHook(true);
        end.releaseStaleHandset();
        check("a handset nobody holds goes back on the hook when the phone loads", !end.offHook(), "");
        return 1;
    }

    /**
     * A copy still holding the original's number, before its first tick gives it its own: it doesn't ring with the
     * original, and breaking it doesn't end the original's call.
     */
    private void checkCopyDuringCall(int a, int b, TileTelephone start, TileTelephone end, UUID alice, UUID bob) {
        // Bob dials this time: Alice has used up her five attempts this minute (DESIGN.md 12.3).
        CallService calls = becom.calls;
        NBTTagCompound saved = new NBTTagCompound();
        start.writeToNBT(saved);
        calls.takeHandset(stranger, end);
        calls.dial(stranger, b, a);
        TileTelephone copy = placeSaved(saved, X0 + 11, Y + 1, end.zCoord);
        copy.restoreRinging();
        check("a copy holding a ringing phone's number stays silent", start.ringing() && !copy.ringing(), "");
        calls.takeHandset(owner, start);
        world.setBlockToAir(X0 + 11, Y + 1, end.zCoord);
        Call call = calls.book()
            .callOf(bob);
        check(
            "breaking a copy leaves the original's call up",
            call != null && call.state() == Call.State.CONNECTED,
            call == null ? "no call"
                : call.state()
                    .name());
        calls.hangUpHandset(alice);
        calls.hangUpHandset(bob);
    }

    /**
     * A Telephone carried elsewhere (a frame or a dolly moves its tile entity without breaking it) keeps its number;
     * a copy of one that still stands doesn't take it.
     */
    private void checkMovedPhone(int z) {
        int y = Y + 1;
        TileTelephone first = telephone(X0 + 5, y, z);
        int number = first.number();
        NBTTagCompound saved = new NBTTagCompound();
        first.writeToNBT(saved);
        world.removeTileEntity(X0 + 5, y, z);
        world.setBlockToAir(X0 + 5, y, z);
        TileTelephone moved = placeSaved(saved, X0 + 7, y, z);
        moved.settleAfterLoad();
        PhoneRecord record = registry().get(number);
        check(
            "a Telephone that was moved keeps its number",
            record != null && registry().get(number) == record && record.pos() == BlockPos.pack(X0 + 7, y, z),
            record == null ? "freed" : BlockPos.toString(record.pos()));
        // The owner's settings go with a copy, so a renumbered phone doesn't show to players it blocks.
        if (record != null) {
            record.block(stranger.getUniqueID(), stranger.getCommandSenderName());
            record.setHidden(true);
        }
        TileTelephone copy = placeSaved(saved, X0 + 9, y, z);
        copy.settleAfterLoad();
        PhoneRecord copied = registry().get(copy.number());
        check(
            "a copy of a Telephone that still stands gets its own number, with its owner and settings",
            record != null && record.pos() == BlockPos.pack(X0 + 7, y, z)
                && copy.number() != number
                && copied != null
                && owner.getUniqueID()
                    .equals(copied.owner())
                && copied.hidden()
                && copied.blocks(stranger.getUniqueID()),
            "copy holds " + copy.number());
        world.setBlockToAir(X0 + 9, y, z);
        // An original saved by a newer BeCom shows no number, but still stands: a copy doesn't take its record.
        NBTTagCompound newer = new NBTTagCompound();
        moved.writeToNBT(newer);
        newer.setInteger(SavedDataFormat.KEY, TileTelephone.FORMAT + 1);
        moved.readFromNBT(newer);
        TileTelephone second = placeSaved(saved, X0 + 9, y, z);
        second.settleAfterLoad();
        check(
            "an original saved by a newer BeCom keeps its number from a copy",
            record != null && registry().get(number) == record && record.pos() == BlockPos.pack(X0 + 7, y, z),
            record == null ? "freed" : BlockPos.toString(record.pos()));
        world.setBlockToAir(X0 + 9, y, z);
        world.removeTileEntity(X0 + 7, y, z);
        world.setBlockToAir(X0 + 7, y, z);
        registry().remove(number);
    }

    /** A mover that keeps the tile object re-seats it elsewhere: the Telephone settles again at its new spot. */
    private void checkCarriedTile(int z) {
        int y = Y + 1;
        TileTelephone tile = telephone(X0 + 13, y, z);
        int number = tile.number();
        tile.updateEntity();
        world.removeTileEntity(X0 + 13, y, z);
        world.setBlockToAir(X0 + 13, y, z);
        world.setBlock(X0 + 15, y, z, BeComBlocks.telephone);
        tile.validate();
        world.setTileEntity(X0 + 15, y, z, tile);
        tile.updateEntity();
        PhoneRecord record = registry().get(number);
        check(
            "a Telephone carried as the same tile settles at its new spot",
            record != null && record.pos() == BlockPos.pack(X0 + 15, y, z),
            record == null ? "freed" : BlockPos.toString(record.pos()));
        world.setBlockToAir(X0 + 15, y, z);
    }

    private TileTelephone placeSaved(NBTTagCompound saved, int x, int y, int z) {
        world.setBlock(x, y, z, BeComBlocks.telephone);
        NBTTagCompound nbt = (NBTTagCompound) saved.copy();
        nbt.setInteger("x", x);
        nbt.setInteger("y", y);
        nbt.setInteger("z", z);
        TileTelephone tile = (TileTelephone) world.getTileEntity(x, y, z);
        tile.readFromNBT(nbt);
        return tile;
    }

    /** Puts a fake player on the floor right next to a Telephone, within its cord. */
    private static void standBy(FakePlayer player, TileTelephone phone) {
        player.setPosition(phone.xCoord + 1.5, phone.yCoord, phone.zCoord + 0.5);
    }

    private PhoneRegistry registry() {
        return becom.phones.registry();
    }

    private int switchRepeaterOff() {
        MTEPylonHead repeater = repeaterLine.heads.get(2);
        repeater.getBaseMetaTileEntity()
            .disableWorking();
        return SETTLE_TICKS;
    }

    private int checkRepeaterOff() {
        int a = repeaterLine.phoneStart.number(), b = repeaterLine.phoneEnd.number();
        check(
            "a repeater switched off goes offline and the line goes dead",
            !becom.calls.reachableFrom(a)
                .contains(b)
                && becom.network.graph()
                    .width(a, b) == Double.NEGATIVE_INFINITY,
            "width " + becom.network.graph()
                .width(a, b));
        NetNode node = becom.network.model(world)
            .node(pos(repeaterLine.heads.get(2)));
        check(
            "...while it keeps its links",
            node != null && node.links()
                .size() == 2,
            "");
        return 1;
    }

    private int checkQuests() {
        BeComQuests.QuestBackend quests = BeComQuests.backend();
        if (!cpw.mods.fml.common.Loader.isModLoaded(BeComQuests.BQ_MODID)) {
            fail("BetterQuesting is in the dev server", "it isn't, so the questline can't be checked");
            return 1;
        }
        check("BeCom's questline is in the quest book after server start", quests.questlinePresent(), "");
        questProbe = UUID.fromString("b3c0b3c0-0000-4000-8000-0000000000aa");
        Object task = invoke(quests, "firstTask", QuestTask.FIRST_CALL);
        check("the first-call quest uses BeCom's task type", task != null, "");
        check(
            "the radio-contact quest uses BeCom's task type",
            invoke(quests, "firstTask", QuestTask.RADIO_CONTACT) != null,
            "");
        for (QuestTask kind : new QuestTask[] { QuestTask.VIDEO_CALL, QuestTask.MOBILE_CALL,
            QuestTask.ME_BRIDGE_CALL }) {
            check(
                "the " + kind.name()
                    .toLowerCase(Locale.ROOT)
                    .replace('_', ' ') + " quest uses BeCom's task type",
                invoke(quests, "firstTask", kind) != null,
                "");
        }
        // A database to reload, saved by BQ itself without BeCom's quests: what a pack's DefaultQuests looks like.
        quests.removeQuestlineForTest();
        command("/bq_admin default save " + RELOAD_DATABASE);
        quests.recheckNow();
        // Progress made now reaches BeCom's copy within a second; the reload comes after that.
        Object back = invoke(quests, "firstTask", QuestTask.FIRST_CALL);
        if (back != null) invoke(back, "setComplete", questProbe);
        return 2 * TICKS_PER_SECOND;
    }

    /** DESIGN.md 16.2: the questline comes back after BQ's own reload, the one that wipes anything not in the file. */
    private int checkQuestReload() {
        BeComQuests.QuestBackend quests = BeComQuests.backend();
        if (!cpw.mods.fml.common.Loader.isModLoaded(BeComQuests.BQ_MODID)) return 1;
        command("/bq_admin default load " + RELOAD_DATABASE);
        check("BQ's /bq_admin default load takes BeCom's questline out", !quests.questlinePresent(), "");
        quests.recheckNow();
        check("the periodic check puts it back", quests.questlinePresent(), "");
        Object back = invoke(quests, "firstTask", QuestTask.FIRST_CALL);
        check(
            "...with players' progress",
            back != null && Boolean.TRUE.equals(invoke(back, "isComplete", questProbe)),
            "");
        deleteTree(
            new File(
                cpw.mods.fml.common.Loader.instance()
                    .getConfigDir(),
                "betterquesting/saved_quests/" + RELOAD_DATABASE));
        return 1;
    }

    private static void command(String line) {
        MinecraftServer server = MinecraftServer.getServer();
        server.getCommandManager()
            .executeCommand(server, line);
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (file.exists() && !file.delete()) BeCom.LOG.warn(PREFIX + "couldn't delete {}", file);
    }

    /** BQ classes stay out of this class's signature, so it loads without BQ. */
    private static Object invoke(Object target, String method, Object arg) {
        try {
            for (java.lang.reflect.Method m : target.getClass()
                .getMethods()) {
                if (m.getName()
                    .equals(method) && m.getParameterCount() == 1) return m.invoke(target, arg);
            }
        } catch (ReflectiveOperationException e) {
            BeCom.LOG.error(PREFIX + "couldn't call {}", method, e);
        }
        return null;
    }

    // endregion

    // region Radio (0.2)

    private int checkRadioRegistration() {
        boolean groups = true;
        for (int channel = 1; channel <= RadioGroup.CHANNELS; channel++) {
            groups &= GtnhVoiceApi.groupManager()
                .byName(RadioGroup.nameOf(channel)) == becom.radio.group(channel);
        }
        check("voice groups becom:radio.1 to becom:radio.16 are registered", groups, "");
        for (MTEAntenna antenna : List.of(BeComMachines.antennaT1, BeComMachines.antennaT2, BeComMachines.antennaT3)) {
            check("crafting recipe for " + antenna.getMetaName(), hasCraftingRecipe(antenna.getStackForm(1)), "");
        }
        check(
            "crafting recipe for " + BeComMachines.transceiver.getMetaName(),
            hasCraftingRecipe(BeComMachines.transceiver.getStackForm(1)),
            "");
        for (net.minecraft.item.Item item : List.of(BeComItems.walkieTalkie, BeComItems.signalAnalyzer)) {
            checkAssemblerOnly(item, 2);
        }
        return 1;
    }

    /** The assembler makes {@code item} at voltage tier {@code tier}, and its inputs make nothing else there. */
    private void checkAssemblerOnly(net.minecraft.item.Item item, int tier) {
        GTRecipe ours = null;
        for (GTRecipe recipe : RecipeMaps.assemblerRecipes.getAllRecipes()) {
            for (ItemStack out : recipe.mOutputs) if (out != null && out.getItem() == item) ours = recipe;
        }
        String name = item.getUnlocalizedName();
        if (ours == null) {
            fail("assembler makes " + name, "no recipe");
            return;
        }
        List<GTRecipe> found = RecipeMaps.assemblerRecipes.findRecipeQuery()
            .items(ours.mInputs)
            .fluids(ours.mFluidInputs)
            .voltage(GTValues.V[tier])
            .findAll()
            .collect(java.util.stream.Collectors.toList());
        check("assembler makes " + name + ", and its inputs make nothing else", found.size() == 1, found.toString());
    }

    private int buildRadio() {
        for (int z : new int[] { RADIO_Z, DIRECT_Z, FORM_Z }) {
            for (int cx = (X0 - 40) >> 4; cx <= (X0 + 200) >> 4; cx++) {
                for (int cz = (z - 16) >> 4; cz <= (z + 16) >> 4; cz++) keepLoaded(cx, cz);
            }
        }
        clearRadioArea();
        antennaA = antenna(BeComMachines.antennaT1, X0, RADIO_Z, gregtech.api.enums.ItemList.Hatch_Energy_LV);
        antennaB = antenna(
            BeComMachines.antennaT1,
            X0 + ANTENNA_GAP,
            RADIO_Z,
            gregtech.api.enums.ItemList.Hatch_Energy_LV);
        antennaT2 = antenna(BeComMachines.antennaT2, X0, FORM_Z, gregtech.api.enums.ItemList.Hatch_Energy_MV);
        antennaT3 = antenna(BeComMachines.antennaT3, X0 + 40, FORM_Z, gregtech.api.enums.ItemList.Hatch_Energy_HV);
        antennaShort = antenna(BeComMachines.antennaT1, X0 + 80, FORM_Z, gregtech.api.enums.ItemList.Hatch_Energy_LV);
        // One frame short: the base moves up into the bottom frame's place.
        int mast = BeComConfig.get()
            .antennaMastMin();
        int sx = X0 + 80;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = world.getBlock(sx + dx, Y - mast - 1, FORM_Z + dz);
                int meta = world.getBlockMetadata(sx + dx, Y - mast - 1, FORM_Z + dz);
                if (world.getTileEntity(sx + dx, Y - mast - 1, FORM_Z + dz) == null) {
                    world.setBlock(sx + dx, Y - mast, FORM_Z + dz, block, meta, 3);
                } else {
                    world.setBlockToAir(sx + dx, Y - mast, FORM_Z + dz);
                }
                world.setBlockToAir(sx + dx, Y - mast - 1, FORM_Z + dz);
            }
        }
        bridgeTerminalA = place(BeComMachines.lineTerminal, X0, Y, RADIO_Z + 5);
        bridgeTerminalB = place(BeComMachines.lineTerminal, X0 + ANTENNA_GAP, Y, RADIO_Z + 5);
        powered.add(bridgeTerminalA.getBaseMetaTileEntity());
        powered.add(bridgeTerminalB.getBaseMetaTileEntity());
        world.setBlock(X0, Y, RADIO_Z + 6, BeComBlocks.phoneCable);
        bridgePhoneA = telephone(X0, Y, RADIO_Z + 7);
        world.setBlock(X0 + ANTENNA_GAP, Y, RADIO_Z + 6, BeComBlocks.phoneCable);
        bridgePhoneB = telephone(X0 + ANTENNA_GAP, Y, RADIO_Z + 7);
        transceiver = place(BeComMachines.transceiver, X0 + 100, Y, DIRECT_Z);
        powered.add(transceiver.getBaseMetaTileEntity());
        transceiver.setChannel(5);
        return 2;
    }

    /** Clears what an earlier run built here, so the checks mean the same every time. */
    private void clearRadioArea() {
        NetworkModel model = becom.network.model(world);
        for (NetNode node : new ArrayList<>(model.nodes())) {
            boolean here = node.x() >= X0 - 40 && node.x() <= X0 + 200
                && (Math.abs(node.z() - RADIO_Z) <= 16 || Math.abs(node.z() - DIRECT_Z) <= 16
                    || Math.abs(node.z() - FORM_Z) <= 16);
            if (!here) continue;
            world.setBlockToAir(node.x(), node.y(), node.z());
            model.remove(node.pos());
        }
        int[][] spots = { { X0, RADIO_Z }, { X0 + ANTENNA_GAP, RADIO_Z }, { X0, FORM_Z }, { X0 + 40, FORM_Z },
            { X0 + 80, FORM_Z }, { X0 + 100, DIRECT_Z } };
        for (int[] spot : spots) {
            for (int x = spot[0] - 1; x <= spot[0] + 1; x++) {
                for (int z = spot[1] - 1; z <= spot[1] + 8; z++) {
                    for (int y = Y - 8; y <= Y; y++) world.setBlockToAir(x, y, z);
                }
            }
        }
    }

    /** Places an antenna head, builds its shortest mast and base, and puts an energy hatch in a corner of the base. */
    private MTEAntenna antenna(MTEAntenna machine, int x, int z, gregtech.api.enums.ItemList hatch) {
        MTEAntenna head = place(machine, x, Y, z);
        ((IConstructable) head).construct(machine.getStackForm(1), false);
        int base = Y - BeComConfig.get()
            .antennaMastMin() - 1;
        world.setBlockToAir(x + 1, base, z + 1);
        IMetaTileEntity hatchMte = GregTechAPI.METATILEENTITIES[hatch.get(1)
            .getItemDamage()];
        IMetaTileEntity placed = place(hatchMte, x + 1, base, z + 1);
        powered.add(placed.getBaseMetaTileEntity());
        return head;
    }

    private int formRadio() {
        for (MTEAntenna antenna : List.of(antennaA, antennaB, antennaT2, antennaT3, antennaShort)) {
            antenna.refreshStructure();
        }
        bridgeTerminalA.scanCable();
        bridgeTerminalB.scanCable();
        link(bridgeTerminalA, antennaA);
        link(bridgeTerminalB, antennaB);
        // The height cache follows chunks as they load and save; these were changed after loading.
        for (int z : new int[] { RADIO_Z, DIRECT_Z, FORM_Z }) {
            for (int cx = (X0 - 40) >> 4; cx <= (X0 + 200) >> 4; cx++) {
                for (int cz = (z - 16) >> 4; cz <= (z + 16) >> 4; cz++) {
                    becom.heights.chunkChanged(world, world.getChunkFromChunkCoords(cx, cz));
                }
            }
        }
        return SETTLE_TICKS;
    }

    private int checkAntennas() {
        Tunables t = BeComConfig.get();
        for (MTEAntenna antenna : List.of(antennaA, antennaB, antennaT2, antennaT3)) {
            check(
                "antenna T" + antenna.tier() + " with a mast of " + t.antennaMastMin() + " forms and works",
                antenna.mMachine && antenna.mast() == t.antennaMastMin() && antenna.isWorking(),
                "formed " + antenna.mMachine + ", mast " + antenna.mast() + ", working " + antenna.isWorking());
            AntennaNetwork.Antenna known = becom.antennas.antenna(antenna.ref());
            check(
                "antenna T" + antenna.tier() + " has surveyed",
                known != null && known.survey() != null,
                known == null ? "unknown to the network" : "no survey yet");
        }
        antennaShort.refreshStructure();
        check(
            "an antenna one frame short of the shortest mast doesn't form",
            !antennaShort.mMachine && antennaShort.mast() == t.antennaMastMin() - 1,
            "formed " + antennaShort.mMachine + ", mast " + antennaShort.mast());

        AntennaNetwork.Link link = becom.antennas.links(0)
            .get(AntennaNetwork.LinkKey.of(pos(antennaA), pos(antennaB)));
        double expected = t.antennaBudget(1) - RadioModel.distanceLoss(ANTENNA_GAP);
        if (link == null) {
            fail("two T1 antennas " + ANTENNA_GAP + " blocks apart link", "no link");
        } else {
            near(
                "two T1 antennas " + ANTENNA_GAP + " blocks apart link on VHF at 42 - 20 log10(60) dB",
                expected,
                link.vhf()
                    .snr());
            near(
                "...and on UHF",
                expected,
                link.uhf()
                    .snr());
        }
        AntennaView view = AntennaScreen.view(antennaA, owner);
        check(
            "the antenna screen lists its link and its bridge, and lets its owner lock it",
            view.links()
                .size() == 1
                && view.bridges()
                    .size() == 1
                && view.mayManage()
                && view.surveyed(),
            view.links()
                .size() + " links, "
                + view.bridges()
                    .size()
                + " bridges, manage "
                + view.mayManage());
        // The stranger stands right at the antenna: only ownership stops them.
        stranger.setPosition(X0 + 1.5, Y, RADIO_Z + 0.5);
        AntennaScreen.setLocked(antennaA, stranger, true);
        check(
            "...and nobody else",
            !AntennaScreen.view(antennaA, stranger)
                .mayManage() && !antennaA.isLocked(),
            "");
        int a = bridgePhoneA.number(), b = bridgePhoneB.number();
        near(
            "a call between lines bridged onto them: its weakest hop is the UHF link",
            expected,
            becom.network.graph()
                .width(a, b));
        check(
            "...so each Telephone has the other in its contacts",
            becom.calls.reachableFrom(a)
                .contains(b),
            "");
        return 1;
    }

    /** A fake player at {@code (x, Y, z)}, in the air, carrying a charged walkie on {@code channel} if it's above 0. */
    private FakePlayer radioPlayer(String name, int n, double x, double z, int channel) {
        FakePlayer player = FakePlayerFactory.get(
            world,
            new GameProfile(
                UUID.fromString(String.format(Locale.ROOT, "b3c0b3c0-0000-4000-8000-0000000000%02x", 0xb0 + n)),
                name));
        player.setPosition(x, Y, z);
        java.util.Arrays.fill(player.inventory.mainInventory, null);
        player.inventory.currentItem = 0;
        if (channel > 0) {
            ItemStack walkie = new ItemStack(BeComItems.walkieTalkie);
            ElectricItem.manager
                .charge(walkie, BeComItems.walkieTalkie.getMaxCharge(walkie), Integer.MAX_VALUE, true, false);
            ItemWalkieTalkie.setChannel(walkie, channel);
            player.inventory.mainInventory[0] = walkie;
        }
        becom.radio.refreshForSelfTest(player);
        return player;
    }

    private static RadioSnapshot.Link linkTo(RadioSnapshot.Transmission tx, UUID holder) {
        if (tx == null) return null;
        for (RadioSnapshot.Link link : tx.walkies()) if (holder.equals(link.holder())) return link;
        return null;
    }

    private static double distance(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Walkies and a Transceiver out of every antenna's reach: only direct links (DESIGN.md 6.4, 8.1). */
    private int keyDirect() {
        alice = radioPlayer("BeComRadioAlice", 1, X0 + 0.5, DIRECT_Z + 0.5, 3);
        bob = radioPlayer("BeComRadioBob", 2, X0 + 30.5, DIRECT_Z + 0.5, 3);
        carol = radioPlayer("BeComRadioCarol", 3, X0 + 150.5, DIRECT_Z + 0.5, 5);
        // Dave has no walkie: he stands at the Transceiver, which transmits for him.
        dave = radioPlayer("BeComRadioDave", 4, X0 + 101.5, DIRECT_Z + 0.5, 0);
        becom.radio.keyForSelfTest(alice.getUniqueID());
        return 2;
    }

    private int checkDirect() {
        becom.radio.publishForSelfTest();
        Tunables t = BeComConfig.get();
        RadioSnapshot.Transmission tx = becom.radio.published()
            .transmission(alice.getUniqueID());
        check(
            "a speaker holding push-to-talk transmits on their walkie's channel",
            tx != null && tx.channel() == 3,
            "");
        RadioSnapshot.Link link = linkTo(tx, bob.getUniqueID());
        if (link == null) fail("a walkie 30 blocks away hears it directly", "no link");
        else near(
            "a walkie 30 blocks away hears it directly at 44 - 20 log10(30) dB",
            t.walkieBudget() - RadioModel.distanceLoss(30),
            link.snr());
        check("a walkie on another channel doesn't", linkTo(tx, carol.getUniqueID()) == null, "");
        becom.radio.keyForSelfTest(dave.getUniqueID());
        return 2;
    }

    private int checkTransceiver() {
        becom.radio.publishForSelfTest();
        Tunables t = BeComConfig.get();
        RadioSnapshot.Transmission tx = becom.radio.published()
            .transmission(dave.getUniqueID());
        check(
            "standing at a Transceiver, a player transmits through it on its channel",
            tx != null && tx.channel() == 5,
            tx == null ? "no transmission" : "channel " + tx.channel());
        RadioSnapshot.Link link = linkTo(tx, carol.getUniqueID());
        double d = distance(X0 + 100.5, Y + 1, DIRECT_Z + 0.5, X0 + 150.5, Y + 1.62, DIRECT_Z + 0.5);
        if (link == null) fail("...reaching a walkie 50 blocks away with the Transceiver's budget", "no link");
        else near(
            "...reaching a walkie 50 blocks away with the Transceiver's budget",
            t.transceiverBudget() - RadioModel.distanceLoss(d),
            link.snr());
        becom.radio.keyForSelfTest(carol.getUniqueID());
        return 2;
    }

    private int checkLoudspeaker() {
        becom.radio.publishForSelfTest();
        Tunables t = BeComConfig.get();
        RadioSnapshot.Transmission tx = becom.radio.published()
            .transmission(carol.getUniqueID());
        RadioSnapshot.Link heard = null;
        if (tx != null) for (RadioSnapshot.Link link : tx.transceivers()) heard = link;
        double d = distance(X0 + 150.5, Y + 1.62, DIRECT_Z + 0.5, X0 + 100.5, Y + 1, DIRECT_Z + 0.5);
        if (heard == null) fail("a Transceiver on the channel receives a walkie 50 blocks away", "no link");
        else near(
            "a Transceiver on the channel receives a walkie 50 blocks away, for its loudspeaker",
            t.walkieBudget() - RadioModel.distanceLoss(d),
            heard.snr());
        check(
            "...with its loudspeaker on",
            heard != null && heard.transceiver()
                .loudspeaker(),
            "");
        return 1;
    }

    /** Two walkies 100 blocks apart, each 20 blocks from one of two linked T1 antennas (DESIGN.md 7, 8.1). */
    private int keyThroughAntennas() {
        eve = radioPlayer("BeComRadioEve", 5, X0 - 19.5, RADIO_Z + 0.5, 7);
        frank = radioPlayer("BeComRadioFrank", 6, X0 + ANTENNA_GAP + 20.5, RADIO_Z + 0.5, 7);
        becom.radio.keyForSelfTest(eve.getUniqueID());
        return 2;
    }

    private int checkThroughAntennas() {
        becom.radio.publishForSelfTest();
        Tunables t = BeComConfig.get();
        check(
            "100 blocks is past a walkie's direct reach",
            t.rangeToLow(t.walkieBudget()) < 100,
            "reach " + t.rangeToLow(t.walkieBudget()));
        RadioSnapshot.Link link = linkTo(
            becom.radio.published()
                .transmission(eve.getUniqueID()),
            frank.getUniqueID());
        if (link == null) fail("two walkies talk through the antennas", "no link");
        else near(
            "two walkies talk through the antennas, at the weakest hop: the link between them",
            t.antennaBudget(1) - RadioModel.distanceLoss(ANTENNA_GAP),
            link.snr());
        AnalyzerPacket reading = becom.radio.reading(eve);
        double d = distance(X0 + 0.5, Y + 1, RADIO_Z + 0.5, X0 - 19.5, Y + 1.62, RADIO_Z + 0.5);
        check(
            "the Signal Analyzer names the T1 antenna serving the walkie",
            reading.served() && reading.tier() == 1 && reading.x() == X0 && reading.z() == RADIO_Z,
            reading.served() ? reading.x() + "," + reading.z() : "nothing in reach");
        near(
            "...at 42 - 20 log10(d) dB",
            t.antennaBudget(1) - RadioModel.distanceLoss(d),
            reading.served() ? reading.losses()
                .snr() : Double.NaN);
        check(
            "...with distance its biggest loss",
            reading.served() && reading.losses()
                .biggest() == RadioModel.Loss.DISTANCE,
            reading.served() ? String.valueOf(
                reading.losses()
                    .biggest())
                : "");
        antennaA.setLocked(true);
        return 3;
    }

    private int keyLocked() {
        becom.radio.refreshForSelfTest(eve);
        becom.radio.keyForSelfTest(eve.getUniqueID());
        return 2;
    }

    private int checkLocked() {
        becom.radio.publishForSelfTest();
        check(
            "an antenna locked to its owner's team refuses a stranger's walkie",
            linkTo(
                becom.radio.published()
                    .transmission(eve.getUniqueID()),
                frank.getUniqueID()) == null,
            "");
        antennaA.setLocked(false);
        return 1;
    }

    private int checkFlatWalkie() {
        Tunables t = BeComConfig.get();
        gina = radioPlayer("BeComRadioGina", 7, X0 + 0.5, FORM_Z + 40.5, 9);
        ItemStack walkie = gina.inventory.mainInventory[0];
        double before = ItemWalkieTalkie.charge(walkie);
        becom.radio.drainForSelfTest(gina);
        near(
            "a walkie switched on drains its standby charge each second",
            (double) t.walkieStandbyDrain() * TICKS_PER_SECOND,
            before - ItemWalkieTalkie.charge(walkie));
        ItemStack bobs = bob.inventory.mainInventory[0];
        ItemWalkieTalkie.drain(bobs, ItemWalkieTalkie.charge(bobs));
        ElectricItem.manager.charge(bobs, 1, Integer.MAX_VALUE, true, false);
        becom.radio.drainForSelfTest(bob);
        check("a flat walkie switches off", !ItemWalkieTalkie.on(bobs), "");
        becom.radio.refreshForSelfTest(bob);
        becom.radio.keyForSelfTest(alice.getUniqueID());
        return 2;
    }

    private int checkOffWalkie() {
        becom.radio.publishForSelfTest();
        check(
            "a walkie switched off receives nothing",
            linkTo(
                becom.radio.published()
                    .transmission(alice.getUniqueID()),
                bob.getUniqueID()) == null,
            "");
        return 1;
    }

    /** DESIGN.md 4.1: a link at Low leaves it only 2 dB below the threshold, at the edge of range too. */
    private int keyEdge() {
        hank = radioPlayer("BeComRadioHank", 8, X0 + 0.5, DIRECT_Z + 10.5, 11);
        iris = radioPlayer("BeComRadioIris", 9, X0 + 78.5, DIRECT_Z + 10.5, 11);
        becom.radio.keyForSelfTest(hank.getUniqueID());
        return 2;
    }

    private int checkEdge() {
        Tunables t = BeComConfig.get();
        becom.radio.publishForSelfTest();
        UUID speaker = hank.getUniqueID();
        check(
            "a walkie 78 blocks away hears at Low",
            linkTo(
                becom.radio.published()
                    .transmission(speaker),
                iris.getUniqueID()) != null,
            "");
        // 44 - 20 log10(81) is 5.8 dB: under Low's 6, above the 4 a link at Low needs to stay.
        iris.setPosition(X0 + 81.5, Y, DIRECT_Z + 10.5);
        becom.radio.refreshForSelfTest(iris);
        becom.radio.keyForSelfTest(speaker);
        becom.radio.publishForSelfTest();
        RadioSnapshot.Link held = linkTo(
            becom.radio.published()
                .transmission(speaker),
            iris.getUniqueID());
        check(
            "...and stays at Low 81 blocks away, 2 dB of hysteresis below the threshold",
            held != null && Math.abs(held.snr() - (t.walkieBudget() - RadioModel.distanceLoss(81))) < EPS,
            held == null ? "dropped" : String.valueOf(held.snr()));
        iris.setPosition(X0 + 101.5, Y, DIRECT_Z + 10.5);
        becom.radio.refreshForSelfTest(iris);
        becom.radio.keyForSelfTest(speaker);
        becom.radio.publishForSelfTest();
        check(
            "...and drops 101 blocks away, below the hysteresis",
            linkTo(
                becom.radio.published()
                    .transmission(speaker),
                iris.getUniqueID()) == null,
            "");
        return 1;
    }

    /** DESIGN.md 4.2: below Low for longer than the drop delay, a link drops, radio as well as wire. */
    private int startEdgeHold() {
        iris.setPosition(X0 + 78.5, Y, DIRECT_Z + 10.5);
        becom.radio.refreshForSelfTest(iris);
        becom.radio.keyForSelfTest(hank.getUniqueID());
        becom.radio.publishForSelfTest();
        iris.setPosition(X0 + 81.5, Y, DIRECT_Z + 10.5);
        publishEdge();
        // After the first evaluation below Low, which starts the drop timer: the hold is timed from no earlier.
        edgeHoldStart = System.nanoTime();
        edgeDroppedEarly = false;
        return 5;
    }

    /**
     * Repeats itself until the hold has lasted half a second past the drop delay, by the clock the drop timer reads:
     * a server catching up on lag runs ticks back to back. Until half a second short of it, the link must stay up.
     */
    private int holdEdge() {
        boolean up = publishEdge();
        long elapsed = System.nanoTime() - edgeHoldStart;
        long delay = (long) (becom.tunables.dropAfterSeconds() * 1_000_000_000L);
        long margin = 500_000_000L;
        if (!up && elapsed < delay - margin) edgeDroppedEarly = true;
        if (elapsed < delay + margin) steps.add(index, this::holdEdge);
        return 5;
    }

    /** Evaluates Iris's link from Hank where she stands; whether it's up. */
    private boolean publishEdge() {
        becom.radio.refreshForSelfTest(iris);
        becom.radio.keyForSelfTest(hank.getUniqueID());
        becom.radio.publishForSelfTest();
        return linkTo(
            becom.radio.published()
                .transmission(hank.getUniqueID()),
            iris.getUniqueID()) != null;
    }

    private int checkEdgeDrop() {
        check("a walkie link below Low but inside the hysteresis stays up for the drop delay", !edgeDroppedEarly, "");
        becom.radio.keyForSelfTest(hank.getUniqueID());
        becom.radio.publishForSelfTest();
        check(
            "...and drops after it",
            linkTo(
                becom.radio.published()
                    .transmission(hank.getUniqueID()),
                iris.getUniqueID()) == null,
            "");
        iris.setPosition(X0 + 78.5, Y, DIRECT_Z + 10.5);
        becom.radio.refreshForSelfTest(iris);
        becom.radio.keyForSelfTest(hank.getUniqueID());
        becom.radio.publishForSelfTest();
        check(
            "...and comes back at Low's threshold",
            linkTo(
                becom.radio.published()
                    .transmission(hank.getUniqueID()),
                iris.getUniqueID()) != null,
            "");
        return 1;
    }

    /** X16: an antenna whose BeCom keys a newer BeCom wrote is left alone, its keys kept. */
    private int checkNewerAntenna() {
        NBTTagCompound saved = new NBTTagCompound();
        antennaT2.saveNBTData(saved);
        saved.setInteger(BeComNbt.KEY, MTEAntenna.FORMAT + 1);
        saved.setString("becomFromTheFuture", "kept");
        becom.network.removeNode(world, pos(antennaT2));
        antennaT2.loadNBTData(saved);
        boolean formed = antennaT2.refreshStructure();
        becom.links.forget(owner.getUniqueID());
        antennaT2.onWrenchRightClick(ForgeDirection.NORTH, ForgeDirection.NORTH, owner, 0.5f, 0.5f, 0.5f, null);
        NBTTagCompound written = new NBTTagCompound();
        antennaT2.saveNBTData(written);
        check(
            "an antenna saved by a newer BeCom stays out of the network and keeps its keys",
            antennaT2.isTooNew() && !formed
                && becom.network.model(world)
                    .node(pos(antennaT2)) == null
                && "kept".equals(written.getString("becomFromTheFuture"))
                && written.getInteger(BeComNbt.KEY) == MTEAntenna.FORMAT + 1,
            "formed " + formed);
        // Back to this BeCom's format, for whatever runs after.
        NBTTagCompound current = new NBTTagCompound();
        current.setInteger(BeComNbt.KEY, MTEAntenna.FORMAT);
        antennaT2.loadNBTData(current);
        antennaT2.refreshStructure();
        return 1;
    }

    // endregion

    // region Phones (0.3)

    // 0.3's phone checks, in their own area: a T1 antenna bridged onto a pylon line with a Telephone at its end, and
    // mobile phones west of it, where rain falls (D26). EndlessIDs forbids rewriting a chunk's biomes, so the area is
    // the first stretch north of here whose biome rains.
    private static final int PHONE_Z_FROM = RADIO_Z + 1000;
    private static final int PHONE_Z_STEP = 64;
    private static final int PHONE_Z_TRIES = 64;
    private int phoneZ = PHONE_Z_FROM;
    private boolean phoneRains;
    private static final int PHONE_PYLON_DX = 10;
    private static final int PHONE_TERMINAL_DX = 40;
    private MTEAntenna phoneAntenna;
    private MTEPylonHead phonePylon;
    private MTELineTerminal phoneTerminal;
    private TileTelephone phoneTelephone;
    private FakePlayer mona;
    private FakePlayer nell;
    private int monaNumber = -1;
    private int nellNumber = -1;
    private double clearService = Double.NaN;

    private int checkPhoneRegistration() {
        checkAssemblerOnly(BeComItems.mobilePhone, 3);
        return 1;
    }

    private int buildPhones() {
        phoneZ = PHONE_Z_FROM;
        phoneRains = false;
        for (int i = 0; i < PHONE_Z_TRIES && !phoneRains; i++) {
            int z = PHONE_Z_FROM + i * PHONE_Z_STEP;
            phoneRains = rainsAround(z);
            if (phoneRains) phoneZ = z;
        }
        for (int cx = (X0 - 48) >> 4; cx <= (X0 + 64) >> 4; cx++) {
            for (int cz = (phoneZ - 16) >> 4; cz <= (phoneZ + 16) >> 4; cz++) keepLoaded(cx, cz);
        }
        NetworkModel model = becom.network.model(world);
        for (NetNode node : new ArrayList<>(model.nodes())) {
            if (node.x() < X0 - 48 || node.x() > X0 + 64 || Math.abs(node.z() - phoneZ) > 16) continue;
            world.setBlockToAir(node.x(), node.y(), node.z());
            model.remove(node.pos());
        }
        for (int x = X0 - 2; x <= X0 + PHONE_TERMINAL_DX + 2; x++) {
            for (int z = phoneZ - 2; z <= phoneZ + 3; z++) {
                for (int y = Y - TALLEST_STACK - 2; y <= Y + 1; y++) world.setBlockToAir(x, y, z);
            }
        }
        phoneAntenna = antenna(BeComMachines.antennaT1, X0, phoneZ, gregtech.api.enums.ItemList.Hatch_Energy_LV);
        phonePylon = head(BeComMachines.pylonHead, X0 + PHONE_PYLON_DX, Y, phoneZ, TALLEST_STACK);
        phoneTerminal = place(BeComMachines.lineTerminal, X0 + PHONE_TERMINAL_DX, Y, phoneZ);
        powered.add(phoneTerminal.getBaseMetaTileEntity());
        world.setBlock(X0 + PHONE_TERMINAL_DX, Y, phoneZ + 1, BeComBlocks.phoneCable);
        phoneTelephone = telephone(X0 + PHONE_TERMINAL_DX, Y, phoneZ + 2);
        clearWeather();
        return 2;
    }

    /**
     * Whether rain falls over the whole phone area if it is at {@code z}: the antenna, the pylon line and the phones.
     */
    private boolean rainsAround(int z) {
        for (int x = X0 - 24; x <= X0 + PHONE_TERMINAL_DX + 4; x += 4) {
            for (int dz = -4; dz <= 8; dz += 4) if (!BeComServer.rains(world, x, z + dz)) return false;
        }
        return true;
    }

    private int formPhones() {
        phoneAntenna.refreshStructure();
        phonePylon.refreshStructure();
        phoneTerminal.scanCable();
        link(phoneAntenna, phonePylon);
        link(phonePylon, phoneTerminal);
        for (int cx = (X0 - 48) >> 4; cx <= (X0 + 64) >> 4; cx++) {
            for (int cz = (phoneZ - 16) >> 4; cz <= (phoneZ + 16) >> 4; cz++) {
                becom.heights.chunkChanged(world, world.getChunkFromChunkCoords(cx, cz));
            }
        }
        return SETTLE_TICKS;
    }

    /** A fake player at {@code (x, Y, z)}, in the air, carrying a charged Mobile Phone, looked at once. */
    private FakePlayer phonePlayer(String name, int n, double x, double z) {
        FakePlayer player = FakePlayerFactory.get(
            world,
            new GameProfile(
                UUID.fromString(String.format(Locale.ROOT, "b3c0b3c0-0000-4000-8000-0000000000%02x", 0xd0 + n)),
                name));
        player.setPosition(x, Y, z);
        java.util.Arrays.fill(player.inventory.mainInventory, null);
        player.inventory.currentItem = 0;
        ItemStack phone = new ItemStack(BeComItems.mobilePhone);
        ElectricItem.manager.charge(phone, BeComItems.mobilePhone.getMaxCharge(phone), Integer.MAX_VALUE, true, false);
        player.inventory.mainInventory[0] = phone;
        becom.carried.refreshForSelfTest(player);
        return player;
    }

    /** DESIGN.md 8, 8.2, 12.1, D25: a carried phone's number, owner and service. */
    private int checkMobileService() {
        Tunables t = BeComConfig.get();
        // The fake players have no voice session; the real check would refuse them before anything else.
        becom.calls.voiceForSelfTest(id -> true);
        mona = phonePlayer("BeComMona", 0, X0 - 19.5, phoneZ + 0.5);
        nell = phonePlayer("BeComNell", 1, X0 - 19.5, phoneZ + 4.5);
        monaNumber = ItemCarriedPhone.number(mona.inventory.mainInventory[0]);
        nellNumber = ItemCarriedPhone.number(nell.inventory.mainInventory[0]);
        PhoneRecord record = monaNumber < 0 ? null : becom.phones.record(monaNumber);
        check(
            "a Mobile Phone gets its number on first use, owned by whoever used it",
            record != null && record.kind() == PhoneKind.MOBILE
                && mona.getUniqueID()
                    .equals(record.owner())
                && nellNumber >= 0
                && nellNumber != monaNumber,
            "number " + monaNumber + ", record " + record);
        AntennaNetwork.Antenna known = becom.antennas.antenna(phoneAntenna.ref());
        CarriedPhones.Service service = becom.carried.serviceOf(monaNumber);
        double expected = known == null ? Double.NaN
            : t.antennaBudget(1) - RadioModel.distanceLoss(
                known.end()
                    .distanceTo(RadioService.endOf(mona)));
        check(
            "...and service from an antenna T1 20 blocks away, on UHF at 42 - 20 log10(d) dB",
            service.entries()
                .size() == 1 && Math.abs(service.best() - expected) < EPS,
            service.entries()
                .size() + " entries, best "
                + service.best()
                + ", expected "
                + expected);
        clearService = service.best();
        return 1;
    }

    /**
     * DESIGN.md 16, 0.3's "Done when": a mobile phone calls a Telephone through an antenna bridged onto a pylon line.
     */
    private int checkMobileCall() {
        int telephone = phoneTelephone.number();
        CallBook.DialResult result = becom.calls.dialCarried(mona, monaNumber, telephone);
        check(
            "a Mobile Phone calls a Telephone through an antenna bridged onto a pylon line",
            result == CallBook.DialResult.RINGING && phoneTelephone.ringing(),
            result + ", ringing " + phoneTelephone.ringing());
        standBy(stranger, phoneTelephone);
        boolean answered = becom.calls.takeHandset(stranger, phoneTelephone) == CallService.Pickup.ANSWERED;
        Call call = becom.calls.book()
            .callOfPhone(telephone);
        UUID m = mona.getUniqueID(), s = stranger.getUniqueID();
        check(
            "...the Telephone answers, and each hears the other at the Telephone's Low",
            answered && call != null
                && call.state() == Call.State.CONNECTED
                && call.level(m, s) == SignalLevel.LOW
                && call.level(s, m) == SignalLevel.LOW,
            call == null ? "no call" : call.level(m, s) + " / " + call.level(s, m));
        near(
            "...over the phone's link to the antenna, the path's weakest hop",
            clearService,
            call == null ? Double.NaN : call.snr(m, s));
        return 1;
    }

    /** DESIGN.md 8.2, 11.1: group calls up to 8, the Phone key answering and hanging up. */
    private int checkGroupCall() {
        CallBook.DialResult added = becom.calls.dialCarried(mona, monaNumber, nellNumber);
        check(
            "anyone in a call can add a phone to it, which rings",
            added == CallBook.DialResult.RINGING && becom.calls.isRinging(nellNumber),
            String.valueOf(added));
        becom.calls.phoneKey(nell, false);
        Call call = becom.calls.book()
            .callOfPhone(nellNumber);
        check(
            "...the Phone key answers it, and three are in the call",
            call != null && call.participants()
                .size() == 3 && call.legOf(nell.getUniqueID()) != null,
            call == null ? "no call" : call.participants() + "");
        int routes = 0;
        if (call != null) {
            for (UUID player : call.participants()) routes += becom.calls.group()
                .snapshot()
                .routesOf(player).length;
        }
        check("...and each of them is heard by the two others", routes == 6, routes + " routes");
        becom.calls.phoneKey(nell, false);
        check(
            "...the Phone key hangs up, and the call goes on for the two others",
            becom.calls.book()
                .callOf(nell.getUniqueID()) == null && call != null
                && call.state() == Call.State.CONNECTED
                && call.participants()
                    .size() == 2,
            "");
        // The Telephone's own Add (DESIGN.md 11.2): its handset's holder adds Nell, who turns it down.
        becom.calls.logForSelfTest();
        PhoneScreenActions.dial(stranger, new PhoneHandle.Telephone(phoneTelephone), nellNumber);
        boolean rang = becom.calls.isRinging(nellNumber);
        becom.calls.phoneKey(nell, true);
        String told = becom.calls.lastMessageForSelfTest(stranger.getUniqueID());
        check(
            "...someone on a Telephone's handset adds a phone the same way, and hears when it's turned down",
            rang && !becom.calls.isRinging(nellNumber) && "becom.call.add_rejected".equals(told),
            "rang " + rang + ", told " + told);
        return 1;
    }

    /** DESIGN.md 10, 11.3: a phone switched off leaves its call and can't be called. */
    private int checkPhonePower() {
        Call call = becom.calls.book()
            .callOf(mona.getUniqueID());
        PhoneHandle handle = new PhoneHandle.CarriedPhone(monaNumber);
        PhoneScreenActions.setPower(mona, handle, false);
        check(
            "a phone switched off leaves its call",
            call != null && call.state() == Call.State.ENDED
                && becom.calls.book()
                    .callOf(mona.getUniqueID()) == null,
            "");
        becom.calls.hangUpHandset(stranger.getUniqueID());
        CallBook.DialResult result = becom.calls.dialCarried(nell, nellNumber, monaNumber);
        check("...and can't be called", result == CallBook.DialResult.UNREACHABLE, String.valueOf(result));
        PhoneScreenActions.setPower(mona, handle, true);
        return 1;
    }

    /** D36, DESIGN.md 17: a phone drains its battery on standby, and more in a call. */
    private int checkPhoneDrain() {
        Tunables t = BeComConfig.get();
        ItemStack phone = mona.inventory.mainInventory[0];
        double before = BeComItems.mobilePhone.charge(phone);
        becom.carried.drainForSelfTest(mona);
        double standby = before - BeComItems.mobilePhone.charge(phone);
        becom.calls.dialCarried(mona, monaNumber, phoneTelephone.number());
        before = BeComItems.mobilePhone.charge(phone);
        becom.carried.drainForSelfTest(mona);
        double calling = before - BeComItems.mobilePhone.charge(phone);
        becom.calls.hangUp(mona);
        check(
            "a phone switched on drains its standby charge each second, and its call charge in a call",
            standby == (double) t.phoneStandbyDrain() * TICKS_PER_SECOND
                && calling == (double) t.phoneCallDrain() * TICKS_PER_SECOND,
            "standby " + standby + ", in a call " + calling);
        return 1;
    }

    private TonePacket.Tone busyTone;

    /** DESIGN.md 10: a carried phone's ringback and busy tones stop, with no handset to put down. */
    private int checkTones() {
        UUID m = mona.getUniqueID(), n = nell.getUniqueID(), s = stranger.getUniqueID();
        becom.calls.logForSelfTest();
        becom.calls.dialCarried(nell, nellNumber, monaNumber);
        TonePacket.Tone ringback = becom.calls.lastToneForSelfTest(n);
        becom.calls.phoneKey(mona, true);
        TonePacket.Tone rejected = becom.calls.lastToneForSelfTest(n);
        becom.calls.dialCarried(nell, nellNumber, monaNumber);
        becom.calls.book()
            .tick(
                System.nanoTime() / 1_000_000L + BeComConfig.get()
                    .ringSeconds() * 1000L);
        TonePacket.Tone unanswered = becom.calls.lastToneForSelfTest(n);
        check(
            "a carried phone's ringback stops when its call is turned down or goes unanswered",
            ringback == TonePacket.Tone.RINGBACK && rejected == TonePacket.Tone.HANG_UP
                && unanswered == TonePacket.Tone.HANG_UP
                && "becom.call.no_answer".equals(becom.calls.lastMessageForSelfTest(n)),
            ringback + ", then "
                + rejected
                + ", then "
                + unanswered
                + ", told "
                + becom.calls.lastMessageForSelfTest(n));
        // Nell talks to the Telephone, and Mona calls her.
        becom.calls.dialCarried(nell, nellNumber, phoneTelephone.number());
        standBy(stranger, phoneTelephone);
        becom.calls.takeHandset(stranger, phoneTelephone);
        CallBook.DialResult busy = becom.calls.dialCarried(mona, monaNumber, nellNumber);
        busyTone = busy == CallBook.DialResult.BUSY ? becom.calls.lastToneForSelfTest(m) : null;
        becom.calls.hangUp(nell);
        TonePacket.Tone dead = becom.calls.lastToneForSelfTest(s);
        becom.calls.hangUpHandset(s);
        TonePacket.Tone down = becom.calls.lastToneForSelfTest(s);
        check(
            "...a Telephone's handset hears the line go dead when the other side hangs up, until it's put down",
            dead == TonePacket.Tone.BUSY && down == TonePacket.Tone.HANG_UP,
            dead + ", then " + down);
        return CallService.BRIEF_TONE_TICKS + 1;
    }

    private int checkBusyToneStops() {
        TonePacket.Tone now = becom.calls.lastToneForSelfTest(mona.getUniqueID());
        check(
            "...and a carried phone calling a busy one hears busy, which stops by itself",
            busyTone == TonePacket.Tone.BUSY && now == TonePacket.Tone.NONE,
            busyTone + ", then " + now);
        return 1;
    }

    /**
     * D14, DESIGN.md 11.2: a phone being moved with the mouse stays in its call, and a ringing phone that leaves its
     * player stops ringing for them, its notice included.
     */
    private int checkPhoneMoves() {
        FakePlayer olga = phonePlayer("BeComOlga", 2, X0 - 19.5, phoneZ + 1.5);
        FakePlayer pete = phonePlayer("BeComPete", 3, X0 - 19.5, phoneZ + 3.5);
        int olgaNumber = ItemCarriedPhone.number(olga.inventory.mainInventory[0]);
        int peteNumber = ItemCarriedPhone.number(pete.inventory.mainInventory[0]);
        becom.calls.dialCarried(olga, olgaNumber, peteNumber);
        becom.calls.answerCarried(pete, peteNumber);
        Call call = becom.calls.book()
            .callOf(olga.getUniqueID());
        // Olga moves her phone to another slot, and the once-a-second look comes while it's on her cursor.
        ItemStack phone = olga.inventory.mainInventory[0];
        olga.inventory.mainInventory[0] = null;
        olga.inventory.setItemStack(phone);
        becom.carried.refreshForSelfTest(olga);
        olga.inventory.setItemStack(null);
        olga.inventory.mainInventory[5] = phone;
        becom.carried.refreshForSelfTest(olga);
        check(
            "a phone moved to another slot with the mouse during a call stays in it",
            call != null && call.state() == Call.State.CONNECTED
                && becom.calls.book()
                    .callOf(olga.getUniqueID()) == call,
            call == null ? "no call" : call.state() + "");
        becom.calls.hangUp(olga);
        // Olga calls again, and Pete drops his ringing phone.
        becom.calls.dialCarried(olga, olgaNumber, peteNumber);
        boolean shown = becom.calls.noticeForSelfTest(pete.getUniqueID());
        pete.inventory.mainInventory[0] = null;
        becom.carried.refreshForSelfTest(pete);
        check(
            "...and a ringing phone that leaves its player stops ringing, its notice gone from their screen",
            shown && !becom.calls.noticeForSelfTest(pete.getUniqueID()) && !becom.calls.isRinging(peteNumber),
            "shown " + shown + ", still " + becom.calls.noticeForSelfTest(pete.getUniqueID()));
        becom.calls.hangUp(olga);
        return 1;
    }

    /** D26: rain costs calls and phones' service, where it falls. */
    private int startRain() {
        WorldInfo info = world.getWorldInfo();
        info.setRaining(true);
        info.setRainTime(1_000_000);
        info.setThundering(false);
        info.setThunderTime(1_000_000);
        world.prevRainingStrength = 1;
        world.rainingStrength = 1;
        world.prevThunderingStrength = 0;
        world.thunderingStrength = 0;
        // The antenna network looks at the weather once a second.
        return 2 * TICKS_PER_SECOND;
    }

    private int checkRain() {
        becom.carried.refreshForSelfTest(mona);
        if (!phoneRains) {
            fail("rain costs a Mobile Phone 3 dB of its service where it falls", "no rainy biome found to test in");
            clearWeather();
            return 1;
        }
        near(
            "rain costs a Mobile Phone 3 dB of its service where it falls",
            clearService - BeComConfig.get()
                .weatherLoss(1, 0),
            becom.carried.serviceOf(monaNumber)
                .best());
        clearWeather();
        return 1;
    }

    /** DESIGN.md 6.6, 11.4: the antenna screen's coverage map, on both bands, strongest at the antenna. */
    private int checkCoverage() {
        AntennaView view = AntennaScreen.view(phoneAntenna, owner);
        int cells = Coverage.CELLS * Coverage.CELLS;
        int middle = (Coverage.CELLS / 2) * Coverage.CELLS + Coverage.CELLS / 2;
        boolean strongest = view.uhfMap().length == cells;
        for (int i = 0; strongest && i < cells; i++) strongest = view.uhfMap()[i] <= view.uhfMap()[middle];
        check(
            "the antenna screen draws a coverage map on each band, strongest at the antenna",
            view.vhfMap().length == cells && strongest,
            view.vhfMap().length + " and " + view.uhfMap().length + " cells");
        return 1;
    }

    // region ME (0.3)

    // A small ME network north of the phone area: a creative cell powers a controller with a security station and a
    // wireless access point beside it, and glass cable runs to the ME Bridge, wrench-linked to the phone antenna. The
    // owner places the security station, so the network is secured, and their bridge joins it once its security is up;
    // a stranger's bridge on the same cable doesn't.
    private static final int ME_DZ = 8;
    private MTEMeBridge meBridge;
    private MTEMeBridge strangerBridge;
    private TileEntity meSecurity;
    private TileEntity meWireless;
    private FakePlayer odo;
    private FakePlayer pia;
    private int odoNumber = -1;
    private int piaNumber = -1;

    private int checkMeRegistration() {
        checkAssemblerOnly(BeComItems.mePhone, 4);
        check(
            "crafting recipe for " + BeComMachines.meBridge.getMetaName(),
            hasCraftingRecipe(BeComMachines.meBridge.getStackForm(1)),
            "");
        return 1;
    }

    private int buildMe() {
        int z = phoneZ + ME_DZ;
        for (int x = X0 + 2; x <= X0 + 9; x++) {
            for (int dz = -3; dz <= 2; dz++) {
                world.setBlockToAir(x, Y, z + dz);
                world.setBlockToAir(x, Y + 1, z + dz);
            }
        }
        IBlocks blocks = AEApi.instance()
            .definitions()
            .blocks();
        world.setBlock(
            X0 + 4,
            Y,
            z,
            blocks.energyCellCreative()
                .maybeBlock()
                .get());
        world.setBlock(
            X0 + 5,
            Y,
            z,
            blocks.controller()
                .maybeBlock()
                .get());
        ItemStack security = blocks.security()
            .maybeStack(1)
            .get();
        ((ItemBlock) security.getItem()).placeBlockAt(security, owner, world, X0 + 5, Y, z + 1, 1, 0.5f, 0.5f, 0.5f, 0);
        world.setBlock(
            X0 + 5,
            Y,
            z - 1,
            blocks.wireless()
                .maybeBlock()
                .get());
        meSecurity = world.getTileEntity(X0 + 5, Y, z + 1);
        meWireless = world.getTileEntity(X0 + 5, Y, z - 1);
        // An access point connects from its back: it faces away from the controller.
        if (meWireless instanceof IOrientable wap) wap.setOrientation(ForgeDirection.NORTH, ForgeDirection.UP);
        // A cable bus with a glass cable in it, as a player's cable placement makes one.
        world.setBlock(
            X0 + 6,
            Y,
            z,
            blocks.multiPart()
                .maybeBlock()
                .get());
        if (world.getTileEntity(X0 + 6, Y, z) instanceof IPartHost bus) {
            bus.addPart(
                AEApi.instance()
                    .definitions()
                    .parts()
                    .cableGlass()
                    .stack(AEColor.Transparent, 1),
                ForgeDirection.UNKNOWN,
                owner);
        }
        // AE2 boots a new network over a few ticks, and its security only after a second of them.
        return SETTLE_TICKS;
    }

    private int placeMeBridges() {
        int z = phoneZ + ME_DZ;
        meBridge = place(BeComMachines.meBridge, X0 + 7, Y, z);
        powered.add(meBridge.getBaseMetaTileEntity());
        strangerBridge = placeAs(BeComMachines.meBridge, X0 + 6, Y + 1, z, stranger);
        powered.add(strangerBridge.getBaseMetaTileEntity());
        return SETTLE_TICKS;
    }

    private int linkMe() {
        link(meBridge, phoneAntenna);
        return 2 * TICKS_PER_SECOND;
    }

    /** A fake player at {@code (x, Y, z)} carrying a charged ME Phone linked to the test's ME network. */
    private FakePlayer mePlayer(String name, int n, double x, double z) {
        FakePlayer player = FakePlayerFactory.get(
            world,
            new GameProfile(
                UUID.fromString(String.format(Locale.ROOT, "b3c0b3c0-0000-4000-8000-0000000000%02x", 0xe0 + n)),
                name));
        player.setPosition(x, Y, z);
        java.util.Arrays.fill(player.inventory.mainInventory, null);
        player.inventory.currentItem = 0;
        ItemStack phone = new ItemStack(BeComItems.mePhone);
        BeComItems.mePhone.injectAEPower(phone, BeComItems.mePhone.getAEMaxPower(phone));
        if (meSecurity instanceof TileSecurity security) {
            BeComItems.mePhone.setEncryptionKey(phone, String.valueOf(security.getLocatableSerial()), "");
        }
        player.inventory.mainInventory[0] = phone;
        becom.carried.refreshForSelfTest(player);
        return player;
    }

    /** DESIGN.md 8.3, D13, D45, and 16.2's ME Bridge row. */
    private int checkMe() {
        int z = phoneZ + ME_DZ;
        // 16.2: the fallback is a bridge holding a linked terminal, if this fails.
        check(
            "the ME Bridge joins a secured ME network on ME cable, placed by its owner, the way GT's ME hatches do",
            meBridge.meOnline() && meBridge.isWorking(),
            "online in ME " + meBridge.meOnline() + ", working " + meBridge.isWorking() + ", " + meDetail(z));
        check(
            "...one placed by a player the network's security doesn't let build stays out of it",
            !strangerBridge.meOnline(),
            "online in ME " + strangerBridge.meOnline());
        IGregTechTileEntity strangers = strangerBridge.getBaseMetaTileEntity();
        powered.remove(strangers);
        world.setBlockToAir(strangers.getXCoord(), strangers.getYCoord(), strangers.getZCoord());
        odo = mePlayer("BeComOdo", 0, X0 + 5.5, z - 6.5);
        pia = mePlayer("BeComPia", 1, X0 + 8.5, z - 6.5);
        odoNumber = ItemCarriedPhone.number(odo.inventory.mainInventory[0]);
        piaNumber = ItemCarriedPhone.number(pia.inventory.mainInventory[0]);
        CarriedPhones.Service service = becom.carried.serviceOf(odoNumber);
        check(
            "an ME Phone in its network's wireless range has service, at Max",
            service.entries()
                .size() == 1 && service.level() == SignalLevel.MAX,
            service.entries()
                .size() + " entries, "
                + service.level());
        CallBook.DialResult between = becom.calls.dialCarried(odo, odoNumber, piaNumber);
        becom.calls.answerCarried(pia, piaNumber);
        Call call = becom.calls.book()
            .callOf(odo.getUniqueID());
        check(
            "...two ME Phones on one ME network call each other at Max, through no BeCom network",
            between == CallBook.DialResult.RINGING && call != null
                && call.level(odo.getUniqueID(), pia.getUniqueID()) == SignalLevel.MAX,
            between + ", " + (call == null ? "no call" : call.level(odo.getUniqueID(), pia.getUniqueID())));
        checkRearCamera();
        becom.calls.hangUp(odo);
        int telephone = phoneTelephone.number();
        CallBook.DialResult result = becom.calls.dialCarried(odo, odoNumber, telephone);
        standBy(stranger, phoneTelephone);
        becom.calls.takeHandset(stranger, phoneTelephone);
        call = becom.calls.book()
            .callOf(odo.getUniqueID());
        List<CallGraph.HopKind> hops = becom.network.graph()
            .hops(becom.calls.entriesOf(odoNumber), becom.calls.entriesOf(telephone));
        check(
            "...and any other phone through an ME Bridge (D45)",
            result == CallBook.DialResult.RINGING && call != null
                && call.state() == Call.State.CONNECTED
                && hops.contains(CallGraph.HopKind.ME_BRIDGE),
            result + ", hops " + hops);
        check(
            "...which reports the ME Bridge call quest task for the ME Phone's user (DESIGN.md 15.5)",
            BeComQuests.reportedForSelfTest(QuestTask.ME_BRIDGE_CALL, odo.getUniqueID())
                && !BeComQuests.reportedForSelfTest(QuestTask.ME_BRIDGE_CALL, stranger.getUniqueID()),
            "");
        becom.calls.hangUpHandset(stranger.getUniqueID());
        becom.calls.hangUp(odo);
        odo.setPosition(X0 - 60.5, Y, z);
        becom.carried.refreshForSelfTest(odo);
        check(
            "...but has no service outside its network's wireless range",
            becom.carried.serviceOf(odoNumber)
                .entries()
                .isEmpty(),
            "");
        return 1;
    }

    /**
     * DESIGN.md 10.1, 14: in the ME Phones' one-to-one call at Max, the rear camera's pictures reach the other side
     * through the server's relay, within the size cap and no faster than the level allows.
     */
    private void checkRearCamera() {
        UUID o = odo.getUniqueID(), p = pia.getUniqueID();
        PhoneScreenActions.setCamera(odo, new PhoneHandle.CarriedPhone(odoNumber), true, true);
        SignalLevel sent = becom.video.plan()
            .rearSendOf(o);
        becom.video.rearFrame(
            odo,
            new byte[BeComConfig.get()
                .rearMaxBytes() + 1]);
        int oversized = becom.video.rearDelivered(o, p);
        becom.video.rearFrame(odo, new byte[100]);
        int first = becom.video.rearDelivered(o, p);
        check(
            "a rear picture goes on to the other side of a one-to-one call at Max, one over the size cap doesn't",
            sent == SignalLevel.MAX && oversized == 0 && first == 1,
            "sent at " + sent + ", oversized " + oversized + ", then " + first);
        for (int i = 0; i < 4; i++) becom.video.rearFrame(odo, new byte[100]);
        int burst = becom.video.rearDelivered(o, p);
        check(
            "...and pictures sent all at once don't go faster than the level allows",
            burst == 2,
            burst + " of 5 went on");
    }

    /** What the ME network and the bridge's node look like, for a failed check. */
    private String meDetail(int z) {
        StringBuilder detail = new StringBuilder();
        detail.append("cable block ")
            .append(
                world.getBlock(X0 + 6, Y, z)
                    .getUnlocalizedName());
        appeng.api.networking.IGridNode node = meBridge.getProxy()
            .getNode();
        if (node == null) return detail.append(", no node")
            .toString();
        detail.append(", node active ")
            .append(node.isActive())
            .append(", channels met ")
            .append(node.meetsChannelRequirements())
            .append(", connections ")
            .append(
                node.getConnections()
                    .size())
            .append(", grid nodes ")
            .append(
                node.getGrid()
                    .getNodes()
                    .size());
        if (meWireless instanceof appeng.api.networking.IGridHost host) {
            appeng.api.networking.IGridNode wap = host.getGridNode(ForgeDirection.UNKNOWN);
            detail.append(", same grid as the access point ")
                .append(wap != null && wap.getGrid() == node.getGrid());
        }
        return detail.toString();
    }

    // endregion

    // region Videophone (0.3)

    // A Videophone on a Line Terminal of its own, bridged onto the phone antenna 5 blocks north (DESIGN.md 10.2).
    private static final int VIDEOPHONE_DZ = -7;
    private MTEVideophone videophone;
    private MTELineTerminal videophoneTerminal;
    private FakePlayer vic;
    private FakePlayer wes;
    private boolean videophoneOwnerless;

    private int checkVideophoneRegistration() {
        check("crafting recipe for the Videophone", hasCraftingRecipe(BeComMachines.videophone.getStackForm(1)), "");
        return 1;
    }

    private int buildVideophone() {
        int z = phoneZ + VIDEOPHONE_DZ;
        for (int x = X0 - 3; x <= X0 + 3; x++) {
            for (int dz = -3; dz <= 2; dz++) {
                for (int y = Y - 1; y <= Y + 1; y++) world.setBlockToAir(x, y, z + dz);
            }
        }
        videophone = place(BeComMachines.videophone, X0, Y, z);
        powered.add(videophone.getBaseMetaTileEntity());
        world.setBlock(X0, Y, z + 1, BeComBlocks.phoneCable);
        videophoneTerminal = place(BeComMachines.lineTerminal, X0, Y, z + 2);
        powered.add(videophoneTerminal.getBaseMetaTileEntity());
        clearWeather();
        return 2;
    }

    private int formVideophone() {
        // Its number comes on its first tick; this one may not have had it yet.
        if (videophone.number() < 0) videophone.settle();
        // A fake player, like a machine, never owns a phone (X6). The self-test's owner stands in for the player who
        // would place it.
        PhoneRecord record = becom.phones.record(videophone.number());
        videophoneOwnerless = record != null && record.owner() == null;
        if (videophoneOwnerless) record.setOwner(owner.getUniqueID(), owner.getCommandSenderName());
        videophoneTerminal.scanCable();
        link(videophoneTerminal, phoneAntenna);
        return SETTLE_TICKS;
    }

    /** A fake player at {@code (x, Y, z)}, carrying nothing, who can stand at a Videophone. */
    private FakePlayer roomPlayer(String name, int n, double x, double z) {
        FakePlayer player = FakePlayerFactory.get(
            world,
            new GameProfile(
                UUID.fromString(String.format(Locale.ROOT, "b3c0b3c0-0000-4000-8000-0000000000%02x", 0xf0 + n)),
                name));
        player.setPosition(x, Y, z);
        java.util.Arrays.fill(player.inventory.mainInventory, null);
        becom.calls.presentForSelfTest(player);
        return player;
    }

    /** DESIGN.md 10.2, D19, D42: a Videophone is its Line Terminal's phone, rings, and answers as a speakerphone. */
    private int checkVideophoneCall() {
        int number = videophone.number();
        int z = phoneZ + VIDEOPHONE_DZ;
        PhoneRecord record = becom.phones.record(number);
        check(
            "a Videophone gets its number, and its Line Terminal serves it as its one phone (D19)",
            record != null && record.kind() == PhoneKind.VIDEOPHONE && videophoneTerminal.phone() == number,
            "number " + number + ", record " + record + ", the terminal serves " + videophoneTerminal.phone());
        check(
            "...a machine's fake player placing it doesn't own it (X6)",
            videophoneOwnerless,
            record == null ? "no record" : "owner " + record.owner());
        check(
            "...it works while powered, and is in a Mobile Phone's contacts",
            becom.calls.videophoneWorking(number) && becom.calls.reachableFrom(monaNumber)
                .contains(number),
            "working " + becom.calls.videophoneWorking(number));
        vic = roomPlayer("BeComVic", 0, X0 + 2.5, z + 0.5);
        wes = roomPlayer("BeComWes", 1, X0 - 1.5, z - 2.5);
        becom.carried.refreshForSelfTest(pia);
        CallBook.DialResult result = becom.calls.dialCarried(pia, piaNumber, number);
        check(
            "...it rings when called",
            result == CallBook.DialResult.RINGING && becom.calls.isRinging(number),
            String.valueOf(result));
        PhoneScreenActions.pickUp(vic, new PhoneHandle.Videophone(videophone));
        Call call = becom.calls.book()
            .callOfPhone(number);
        Call.Leg leg = call == null ? null : call.legOf(number);
        UUID p = pia.getUniqueID(), v = vic.getUniqueID(), w = wes.getUniqueID();
        check(
            "...its screen answers it, and everyone within 4 blocks is in the call",
            call != null && call.state() == Call.State.CONNECTED
                && leg != null
                && leg.speakerphone()
                && leg.players()
                    .containsAll(List.of(v, w))
                && call.participants()
                    .size() == 3,
            call == null ? "no call" : call.participants() + "");
        check(
            "...an ME Phone's Max comes through at the Videophone's High (D42)",
            call != null && call.level(p, v) == SignalLevel.HIGH && call.level(v, p) == SignalLevel.HIGH,
            call == null ? "no call" : call.level(p, v) + " / " + call.level(v, p));
        CallSnapshot snapshot = becom.calls.group()
            .snapshot();
        CallSnapshot.Loudspeaker[] loud = snapshot.loudspeakersOf(p);
        check(
            "...the call plays from its loudspeaker at the block, out to 16 blocks, and to nobody flat",
            loud.length == 1 && loud[0].phone() == number
                && loud[0].range() == BeComConfig.get()
                    .videophoneLoudspeaker()
                && loud[0].x() == X0 + 0.5
                && loud[0].z() == z + 0.5
                && snapshot.routesOf(p).length == 0,
            loud.length + " loudspeakers, " + snapshot.routesOf(p).length + " flat routes");
        check(
            "...and everyone in its room is heard by the caller",
            hasRoute(snapshot, v, p) && hasRoute(snapshot, w, p),
            "");
        // Wes walks 6 blocks off: out of the room, still within the loudspeaker's reach.
        wes.setPosition(X0 + 6.5, Y, z + 0.5);
        return TICKS_PER_SECOND + 5;
    }

    private static boolean hasRoute(CallSnapshot snapshot, UUID speaker, UUID listener) {
        for (CallSnapshot.Route route : snapshot.routesOf(speaker)) if (route.listener()
            .equals(listener)) return true;
        return false;
    }

    private int checkVideophoneRoom() {
        Call call = becom.calls.book()
            .callOfPhone(videophone.number());
        check(
            "...someone walking out of the room leaves the call, which goes on",
            call != null && call.state() == Call.State.CONNECTED
                && becom.calls.book()
                    .callOf(wes.getUniqueID()) == null
                && call.legOf(vic.getUniqueID()) != null,
            call == null ? "no call" : call.participants() + "");
        vic.setPosition(X0 + 10.5, Y, phoneZ + VIDEOPHONE_DZ + 0.5);
        return TICKS_PER_SECOND + 5;
    }

    private long videophoneEmptySince = -1;
    private boolean videophoneHungUpEarly;
    private long videophoneHungUpAfter = -1;

    private int checkVideophoneEmpty() {
        Call call = becom.calls.book()
            .callOfPhone(videophone.number());
        Call.Leg leg = call == null ? null : call.legOf(videophone.number());
        check(
            "...with nobody left in its room, it stays in the call for now",
            call != null && call.state() == Call.State.CONNECTED
                && leg != null
                && leg.players()
                    .isEmpty()
                && leg.emptySinceMillis() >= 0,
            call == null ? "no call" : call.participants() + "");
        videophoneEmptySince = leg == null ? -1 : leg.emptySinceMillis();
        videophoneHungUpEarly = false;
        videophoneHungUpAfter = -1;
        return VIDEOPHONE_POLL_TICKS;
    }

    // The room's clock is the call book's, in real time; the server's ticks can run slow, so this watches the clock.
    private static final int VIDEOPHONE_POLL_TICKS = 5;
    private static final long VIDEOPHONE_SLACK_MILLIS = 500;

    private int holdVideophone() {
        long limit = BeComConfig.get()
            .videophoneEmptySeconds() * 1000L;
        long elapsed = System.nanoTime() / 1_000_000L - videophoneEmptySince;
        boolean ended = videophoneEmptySince < 0 || becom.calls.book()
            .callOfPhone(videophone.number()) == null;
        if (ended) {
            videophoneHungUpAfter = elapsed;
            videophoneHungUpEarly = elapsed < limit - VIDEOPHONE_SLACK_MILLIS;
            return 1;
        }
        if (elapsed < limit + 2 * VIDEOPHONE_SLACK_MILLIS) steps.add(index, this::holdVideophone);
        return VIDEOPHONE_POLL_TICKS;
    }

    private int checkVideophoneHungUp() {
        check(
            "...and hangs up by itself once nobody has been within 4 blocks for " + BeComConfig.get()
                .videophoneEmptySeconds() + " s",
            !videophoneHungUpEarly && videophoneHungUpAfter >= 0
                && becom.calls.book()
                    .callOfPhone(videophone.number()) == null
                && becom.calls.book()
                    .callOf(pia.getUniqueID()) == null,
            "hung up after " + videophoneHungUpAfter + " ms");
        // Its power cut: what's in its buffer goes, and nothing comes in.
        IGregTechTileEntity tile = videophone.getBaseMetaTileEntity();
        powered.remove(tile);
        tile.decreaseStoredEnergyUnits(tile.getStoredEU(), true);
        return 3 * TICKS_PER_SECOND;
    }

    private int checkVideophonePower() {
        int number = videophone.number();
        CallBook.DialResult result = becom.calls.dialCarried(pia, piaNumber, number);
        check(
            "an unpowered Videophone isn't in contacts and can't be called (D11)",
            !videophone.isWorking() && !becom.calls.videophoneWorking(number)
                && !becom.calls.reachableFrom(piaNumber)
                    .contains(number)
                && result == CallBook.DialResult.UNREACHABLE,
            "working " + videophone.isWorking() + ", " + result);
        becom.calls.hangUp(pia);
        powered.add(videophone.getBaseMetaTileEntity());
        // Powered again, it reports in on its next ticks.
        return 5;
    }

    /** DESIGN.md 16, 0.3's second "Done when": a Mobile Phone calls the Videophone, and Vic answers at its screen. */
    private int startVideoCall() {
        int z = phoneZ + VIDEOPHONE_DZ;
        vic.setPosition(X0 + 2.5, Y, z + 0.5);
        becom.calls.presentForSelfTest(mona);
        becom.carried.refreshForSelfTest(mona);
        CallBook.DialResult result = becom.calls.dialCarried(mona, monaNumber, videophone.number());
        PhoneScreenActions.pickUp(vic, new PhoneHandle.Videophone(videophone));
        Call call = becom.calls.book()
            .callOfPhone(videophone.number());
        check(
            "a Mobile Phone calls the Videophone and Vic answers at its screen",
            result == CallBook.DialResult.RINGING && call != null
                && call.state() == Call.State.CONNECTED
                && call.legOf(vic.getUniqueID()) != null,
            result + ", " + (call == null ? "no call" : call.participants()));
        // A second or so of poses, about ten a second each way.
        return TICKS_PER_SECOND;
    }

    private int checkVideoCall() {
        UUID m = mona.getUniqueID(), v = vic.getUniqueID();
        VideoPlan plan = becom.video.plan();
        VideoPlan.Tile monaSeen = tileOf(plan, v, m);
        VideoPlan.Tile vicSeen = tileOf(plan, m, v);
        check(
            "a video call between a Mobile Phone and a Videophone shows both sides (DESIGN.md 16, 0.3)",
            becom.video.posesDelivered(m, v) > 0 && becom.video.posesDelivered(v, m) > 0,
            "poses Mona to Vic " + becom.video.posesDelivered(m, v)
                + ", Vic to Mona "
                + becom.video.posesDelivered(v, m));
        check(
            "...which reports the Video call quest task for both, and the Mobile call one for Mona (DESIGN.md 15.5)",
            BeComQuests.reportedForSelfTest(QuestTask.VIDEO_CALL, m)
                && BeComQuests.reportedForSelfTest(QuestTask.VIDEO_CALL, v)
                && BeComQuests.reportedForSelfTest(QuestTask.MOBILE_CALL, m)
                && !BeComQuests.reportedForSelfTest(QuestTask.MOBILE_CALL, v),
            "");
        check(
            "...each sees the other live, at the call's level, Medium or better (D43)",
            monaSeen != null && vicSeen != null
                && monaSeen.live()
                && vicSeen.live()
                && monaSeen.level() == becom.calls.book()
                    .callOf(m)
                    .level(m, v),
            monaSeen + " / " + vicSeen);
        return 1;
    }

    /**
     * DESIGN.md 10.1: a person seen again gets their profile sent again, since the viewer's client let go of it. Wes
     * walks into the Videophone's room during the video call, out of it, and back in.
     */
    private void walkWes(boolean in) {
        int z = phoneZ + VIDEOPHONE_DZ;
        if (in) wes.setPosition(X0 - 1.5, Y, z - 2.5);
        else wes.setPosition(X0 + 10.5, Y, z + 0.5);
    }

    private int checkProfileSeen() {
        walkWes(true);
        return TICKS_PER_SECOND + 5;
    }

    private int checkProfileGone() {
        UUID m = mona.getUniqueID(), w = wes.getUniqueID();
        check(
            "someone walking into the Videophone's room shows up on the other side, profile and all",
            tileOf(becom.video.plan(), m, w) != null && becom.video.profilesSent(w, m) == 1,
            "seen " + (tileOf(becom.video.plan(), m, w) != null) + ", profiles " + becom.video.profilesSent(w, m));
        walkWes(false);
        return TICKS_PER_SECOND + 5;
    }

    private boolean wesWentOut;

    private int checkProfileAgain() {
        wesWentOut = tileOf(becom.video.plan(), mona.getUniqueID(), wes.getUniqueID()) == null;
        walkWes(true);
        return TICKS_PER_SECOND + 5;
    }

    private int checkProfileResent() {
        UUID m = mona.getUniqueID(), w = wes.getUniqueID();
        check(
            "...and walking out and back in, their profile goes again, since the other side let go of it",
            wesWentOut && tileOf(becom.video.plan(), m, w) != null && becom.video.profilesSent(w, m) == 2,
            "out " + wesWentOut + ", profiles " + becom.video.profilesSent(w, m));
        walkWes(false);
        // Out of the room again before the rear camera's checks, which need a call of two.
        return TICKS_PER_SECOND + 5;
    }

    private static VideoPlan.Tile tileOf(VideoPlan plan, UUID viewer, UUID source) {
        for (VideoPlan.Tile tile : plan.tilesOf(viewer)) if (tile.source()
            .equals(source)) return tile;
        return null;
    }

    /** DESIGN.md 10.1: each camera switches on and off during a call; the rear camera needs High, one to one. */
    private int checkVideoSwitches() {
        UUID m = mona.getUniqueID(), v = vic.getUniqueID();
        PhoneHandle phone = new PhoneHandle.CarriedPhone(monaNumber);
        PhoneScreenActions.setCamera(mona, phone, true, true);
        SignalLevel level = becom.calls.book()
            .callOf(m)
            .level(m, v);
        boolean rearSent = becom.video.plan()
            .rearSendOf(m) != null;
        check(
            "...a rear camera switched on goes out only at High or better: here at " + level,
            becom.video.camerasOf(m)
                .rear() && rearSent == level.atLeast(SignalLevel.HIGH),
            "switched on " + becom.video.camerasOf(m)
                .rear() + ", sent " + rearSent);
        PhoneScreenActions.setCamera(mona, phone, false, false);
        VideoPlan.Tile tile = tileOf(becom.video.plan(), v, m);
        check(
            "...a front camera switched off still shows its tile, not moving",
            tile != null && !tile.front() && !tile.live(),
            String.valueOf(tile));
        becom.calls.hangUp(mona);
        // The plan follows the calls at its next round of poses.
        return 5;
    }

    private int checkVideoEnded() {
        UUID m = mona.getUniqueID(), v = vic.getUniqueID();
        check(
            "...and hanging up ends the video for both, the switches back to how a call starts",
            becom.video.plan()
                .tilesOf(v)
                .isEmpty()
                && becom.video.plan()
                    .tilesOf(m)
                    .isEmpty()
                && becom.video.camerasOf(m)
                    .equals(VideoPlan.Cameras.START),
            "");
        return 1;
    }

    // endregion

    private void clearWeather() {
        WorldInfo info = world.getWorldInfo();
        info.setRaining(false);
        info.setThundering(false);
        world.prevRainingStrength = 0;
        world.rainingStrength = 0;
        world.prevThunderingStrength = 0;
        world.thunderingStrength = 0;
    }

    // endregion

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "SelfTest[step %d of %d]", index, steps.size());
    }
}
