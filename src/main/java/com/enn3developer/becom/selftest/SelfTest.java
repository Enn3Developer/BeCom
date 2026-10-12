package com.enn3developer.becom.selftest;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.block.BeComBlocks;
import com.enn3developer.becom.block.TileTelephone;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.data.NetworkSavedData;
import com.enn3developer.becom.data.SavedDataFormat;
import com.enn3developer.becom.logic.DropoutModel;
import com.enn3developer.becom.logic.LevelScale;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.call.Call;
import com.enn3developer.becom.logic.call.CallBook;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.logic.phone.PhoneRegistry;
import com.enn3developer.becom.machine.BeComMachines;
import com.enn3developer.becom.machine.MTELineTerminal;
import com.enn3developer.becom.machine.MTEPylonHead;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.quest.QuestTask;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.CallService;
import com.enn3developer.becom.server.LinkService;
import com.enn3developer.becom.server.PhoneScreenActions;
import com.enn3developer.becom.voice.CallGroup;
import com.enn3developer.gtnhvoice.api.server.GtnhVoiceApi;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.mojang.authlib.GameProfile;

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
    private int index;
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
        if (running != null) running.step();
    }

    private void step() {
        tick++;
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
                world.theChunkProviderServer.loadChunk(cx, cz);
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
        world.getChunkFromBlockCoords(x, z);
        ItemStack stack = machine.getStackForm(1);
        ((ItemBlock) stack.getItem()).placeBlockAt(stack, owner, world, x, y, z, 1, 0.5f, 0.5f, 0.5f, 0);
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
        String shown = PhoneScreenActions.view(owner, start)
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

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "SelfTest[step %d of %d]", index, steps.size());
    }
}
