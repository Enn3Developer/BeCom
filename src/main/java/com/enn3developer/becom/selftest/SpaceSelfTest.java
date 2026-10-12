package com.enn3developer.becom.selftest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEndPortal;
import net.minecraft.block.BlockPortal;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.enn3developer.becom.block.BeComBlocks;
import com.enn3developer.becom.block.TileTelephone;
import com.enn3developer.becom.command.CommandBeCom;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.data.NetworkSavedData;
import com.enn3developer.becom.item.BeComItems;
import com.enn3developer.becom.item.ItemCarriedPhone;
import com.enn3developer.becom.item.ItemWalkieTalkie;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.call.Call;
import com.enn3developer.becom.logic.call.CallBook;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.CallGraph;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.logic.radio.AntennaNetwork;
import com.enn3developer.becom.logic.space.Portals;
import com.enn3developer.becom.logic.space.Satellites;
import com.enn3developer.becom.machine.BeComMachines;
import com.enn3developer.becom.machine.MTELaunchPad;
import com.enn3developer.becom.machine.MTELineTerminal;
import com.enn3developer.becom.machine.MTEPortalRelay;
import com.enn3developer.becom.machine.MTESatelliteDish;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.quest.QuestTask;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.CallService;
import com.enn3developer.becom.server.LinkService;
import com.enn3developer.becom.server.SpaceService;
import com.enn3developer.becom.voice.RadioSnapshot;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.mojang.authlib.GameProfile;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import ic2.api.item.ElectricItem;
import micdoodle8.mods.galacticraft.api.entity.IRocketType;
import micdoodle8.mods.galacticraft.api.prefab.entity.EntityAutoRocket;
import micdoodle8.mods.galacticraft.api.tile.IFuelDock;
import micdoodle8.mods.galacticraft.core.blocks.GCBlocks;
import micdoodle8.mods.galacticraft.core.entities.EntityTier1Rocket;
import micdoodle8.mods.galacticraft.core.tile.TileEntityLandingPad;
import micdoodle8.mods.galacticraft.core.util.ConfigManagerCore;

/**
 * 0.4's checks in {@code /becom selftest} (DESIGN.md 16.3): the Satellite Launch Pad, satellites and their decay, sky
 * hops for phones and radios, the Satellite Dish, the Rocket Radio Receiver in a Tier 1 rocket on the Moon (16.2's
 * assumption), the Portal Relay, and 0.4's "Done when": a call from the Overworld to the Moon, and one to the Nether.
 */
final class SpaceSelfTest {

    // East of every other test group in the Overworld, high above the terrain, under open sky.
    private static final int SX = SelfTest.X0 + 2000;
    private static final int SZ = SelfTest.Z0;
    private static final int Y = SelfTest.Y;
    private static final int DISH_X = SX + 100;
    private static final int DISH_GAP = 300;
    private static final int RELAY_Z = SZ + 100;
    private static final int SKY_Z = SZ + 50;
    private static final int RADIO_Z = SZ + 300;
    // Far from where anyone lands on the Moon or walks the Nether, in the air.
    private static final int MX = 4000;
    private static final int MZ = 4000;
    private static final int MY = 200;
    // Where the Overworld relay's Nether portal leads: its coordinates divided by 8, as Minecraft scales a trip.
    private static final int NX = SX / 8;
    private static final int NZ = RELAY_Z / 8;
    private static final int NY = 100;
    // A Nether relay where a different portal leads, 1,000 blocks off.
    private static final int FAR_NX = NX + 1000;
    private static final int NETHER = -1;
    // A terminal stands this far from the relay or dish it's wrench-linked to, with its cable and Telephone beyond.
    private static final int TERMINAL_GAP = 10;
    private static final int RADIO_CHANNEL = 9;
    private static final int ROOF = 4;

    private final SelfTest t;
    private final BeComServer becom;
    private final WorldServer overworld;
    private final FakePlayer owner;
    private WorldServer moon;
    private WorldServer nether;
    private int moonId;

    private MTELaunchPad pad;
    private IGregTechTileEntity padBus;
    private MTEHatchInput padHatch;
    private MTESatelliteDish dishA;
    private MTESatelliteDish dishB;
    private MTELineTerminal dishTerminalA;
    private MTELineTerminal dishTerminalB;
    private TileTelephone dishPhoneA;
    private TileTelephone dishPhoneB;
    private MTEPortalRelay relayOverworld;
    private MTEPortalRelay relayNether;
    private MTEPortalRelay relayTwilight;
    private MTEPortalRelay relayPersonal;
    private MTEPortalRelay relayBare;
    private MTEPortalRelay relayFarNether;
    private MTEPortalRelay relayTwilightFar;
    private MTEPortalRelay relayEnd;
    private MTEPortalRelay relayEndFar;
    private WorldServer twilight;
    private WorldServer end;
    private LinkService.Outcome pairedTwilight;
    private LinkService.Outcome pairedEnd;
    private MTELineTerminal relayTerminalOverworld;
    private MTELineTerminal relayTerminalNether;
    private TileTelephone relayPhoneOverworld;
    private TileTelephone relayPhoneNether;
    private LinkService.Outcome paired;
    private boolean pairSaved;
    private boolean twilightPlaced;
    private boolean personalPlaced;
    private EntityTier1Rocket rocket;
    private FakePlayer netherHand;
    private FakePlayer skyCaller;
    private FakePlayer moonCaller;
    private FakePlayer walkieA;
    private FakePlayer walkieB;
    private int skyNumber = -1;
    private int moonNumber = -1;
    private int countBeforeDecay;

    SpaceSelfTest(SelfTest t, BeComServer becom) {
        this.t = t;
        this.becom = becom;
        this.overworld = DimensionManager.getWorld(0);
        this.owner = t.owner;
    }

    List<SelfTest.Step> steps() {
        List<SelfTest.Step> steps = new ArrayList<>();
        steps.add(this::checkRegistration);
        steps.add(this::build);
        steps.add(this::buildMoon);
        steps.add(this::landRocket);
        steps.add(this::buildNether);
        steps.add(this::buildTwilightAndEnd);
        steps.add(this::form);
        steps.add(this::startLaunch);
        steps.add(this::coverLaunch);
        steps.add(this::checkLaunchHeld);
        steps.add(this::checkLaunch);
        steps.add(this::checkNoSkyNoLaunch);
        steps.add(this::startDecay);
        steps.add(this::checkDecay);
        steps.add(this::checkSkyHops);
        steps.add(this::keySkyRadio);
        steps.add(this::checkSkyRadio);
        steps.add(this::checkDishes);
        steps.add(this::checkDishMaintenance);
        steps.add(this::checkDishSky);
        steps.add(this::checkDishTooFew);
        steps.add(this::checkReceiver);
        steps.add(this::checkMoonCall);
        steps.add(this::checkRelays);
        steps.add(this::checkNetherCall);
        steps.add(this::checkRelayPairRules);
        steps.add(this::checkSatellitesCommand);
        return steps;
    }

    // region Building

    private int checkRegistration() {
        t.checkAssemblerOnly(BeComItems.satellite, 3);
        t.checkAssemblerOnly(BeComItems.satelliteRocket, 3);
        t.checkAssemblerOnly(BeComItems.rocketRadioReceiver, 3);
        for (IMetaTileEntity machine : List
            .<IMetaTileEntity>of(BeComMachines.launchPad, BeComMachines.satelliteDish, BeComMachines.portalRelay)) {
            t.check(
                "crafting makes the " + machine.getMetaName(),
                SelfTest.hasCraftingRecipe(machine.getStackForm(1)),
                "");
        }
        return 1;
    }

    private int build() {
        clearWeather();
        for (int cx = (SX - 32) >> 4; cx <= (DISH_X + DISH_GAP + 32) >> 4; cx++) {
            for (int cz = (SZ - 32) >> 4; cz <= (RELAY_Z + 32) >> 4; cz++) t.keepLoaded(overworld, cx, cz);
        }
        clear(overworld, SX - 8, Y - 6, SZ - 16, DISH_X + DISH_GAP + 8, Y + 8, RELAY_Z + 24);
        forgetNodes(overworld, SX - 8, SZ - 16, DISH_X + DISH_GAP + 8, RELAY_Z + 24);
        // The Launch Pad, its four hatches on its far edge.
        pad = multiblock(overworld, BeComMachines.launchPad, SX, Y, SZ);
        padBus = hatch(overworld, pad, 4, -2, ItemList.Hatch_Input_Bus_HV).getBaseMetaTileEntity();
        padHatch = (MTEHatchInput) hatch(overworld, pad, 4, -1, ItemList.Hatch_Input_HV);
        t.power(hatch(overworld, pad, 4, 1, ItemList.Hatch_Energy_HV).getBaseMetaTileEntity());
        hatch(overworld, pad, 4, 2, ItemList.Hatch_Maintenance);
        // Two dishes 300 blocks apart, each with a Line Terminal and a Telephone in front.
        dishA = dish(DISH_X);
        dishB = dish(DISH_X + DISH_GAP);
        dishTerminalA = terminal(overworld, DISH_X, Y, SZ - 3, -1);
        dishPhoneA = telephone(overworld, DISH_X, Y, SZ - 5, owner);
        dishTerminalB = terminal(overworld, DISH_X + DISH_GAP, Y, SZ - 3, -1);
        dishPhoneB = telephone(overworld, DISH_X + DISH_GAP, Y, SZ - 5, owner);
        // A relay by a Nether portal, its terminal and Telephone; relays by the Twilight Forest's and Personal
        // Space's portals, and one by no portal at all.
        netherPortal(overworld, SX + 2, Y, RELAY_Z);
        relayOverworld = relay(overworld, SX, Y + 1, RELAY_Z);
        relayTerminalOverworld = terminal(overworld, SX, Y, RELAY_Z + TERMINAL_GAP, 1);
        relayPhoneOverworld = telephone(overworld, SX, Y, RELAY_Z + TERMINAL_GAP + 2, owner);
        relayTwilight = relay(overworld, SX + 40, Y, RELAY_Z);
        twilightPlaced = placeByClass(overworld, SX + 42, Y, RELAY_Z, Portals.TWILIGHT_PORTAL);
        relayPersonal = relay(overworld, SX + 60, Y, RELAY_Z);
        personalPlaced = placeByClass(overworld, SX + 62, Y, RELAY_Z, Portals.PERSONAL_PORTAL);
        relayBare = relay(overworld, SX + 80, Y, RELAY_Z);
        return 1;
    }

    private MTESatelliteDish dish(int x) {
        MTESatelliteDish dish = multiblock(overworld, BeComMachines.satelliteDish, x, Y, SZ);
        t.power(hatch(overworld, dish, 4, -2, ItemList.Hatch_Energy_HV).getBaseMetaTileEntity());
        hatch(overworld, dish, 4, 2, ItemList.Hatch_Maintenance);
        return dish;
    }

    private int buildMoon() {
        moonId = ConfigManagerCore.idDimensionMoon;
        moon = load(moonId);
        if (moon == null) {
            t.fail("the Moon loads in the dev server", "no world for dimension " + moonId);
            return 1;
        }
        for (int cx = (MX - 32) >> 4; cx <= (MX + 32) >> 4; cx++) {
            for (int cz = (MZ - 32) >> 4; cz <= (MZ + 32) >> 4; cz++) t.keepLoaded(moon, cx, cz);
        }
        clear(moon, MX - 4, MY - 2, MZ - 4, MX + 16, MY + 8, MZ + 4);
        // A rocket left by an earlier run would stand on the same pad.
        for (Object o : new ArrayList<>(moon.loadedEntityList)) {
            if (o instanceof EntityAutoRocket old && Math.abs(old.posX - MX) < 16 && Math.abs(old.posZ - MZ) < 16) {
                old.setDead();
            }
        }
        // Nine of Galacticraft's launch pads, which join into one landing pad as they tick.
        for (int x = MX - 1; x <= MX + 1; x++) {
            for (int z = MZ - 1; z <= MZ + 1; z++) moon.setBlock(x, MY, z, GCBlocks.landingPad, 0, 3);
        }
        return 5;
    }

    private int landRocket() {
        if (moon == null) return 1;
        t.check(
            "nine launch pads on the Moon join into Galacticraft's landing pad",
            moon.getTileEntity(MX, MY, MZ) instanceof TileEntityLandingPad,
            String.valueOf(moon.getTileEntity(MX, MY, MZ)));
        // Where Galacticraft's own Tier 1 rocket item stands it on a pad, with chests for cargo.
        rocket = new EntityTier1Rocket(moon, MX + 0.5, MY + 0.4, MZ + 0.5, IRocketType.EnumRocketType.INVENTORY27);
        rocket.setInventorySlotContents(0, new ItemStack(BeComItems.rocketRadioReceiver));
        moon.spawnEntityInWorld(rocket);
        moonCaller = phonePlayer(moon, "BeComMoonCaller", 1, MX + 10.5, MY, MZ + 0.5);
        moonNumber = ItemCarriedPhone.number(moonCaller.inventory.mainInventory[0]);
        // The pad docks it as it ticks; receivers are looked at once a second, and a new antenna surveys first.
        return SelfTest.SETTLE_TICKS;
    }

    private int buildNether() {
        nether = load(NETHER);
        if (nether == null) {
            t.fail("the Nether loads in the dev server", "no world for dimension " + NETHER);
            return 1;
        }
        for (int x : new int[] { NX, FAR_NX }) {
            for (int cx = (x - 32) >> 4; cx <= (x + 32) >> 4; cx++) {
                for (int cz = (NZ - 32) >> 4; cz <= (NZ + 32) >> 4; cz++) t.keepLoaded(nether, cx, cz);
            }
            clear(nether, x - 6, NY - 2, NZ - 6, x + 10, NY + 6, NZ + TERMINAL_GAP + 6);
            forgetNodes(nether, x - 6, NZ - 6, x + 10, NZ + TERMINAL_GAP + 6);
        }
        netherHand = FakePlayerFactory.get(nether, profile("BeComNetherHand", 0x10));
        netherPortal(nether, NX + 2, NY - 1, NZ);
        relayNether = relay(nether, NX, NY, NZ);
        relayFarNether = relay(nether, FAR_NX, NY, NZ);
        relayTerminalNether = terminal(nether, NX, NY, NZ + TERMINAL_GAP, 1);
        relayPhoneNether = telephone(nether, NX, NY, NZ + TERMINAL_GAP + 2, netherHand);
        // Machines join the network as they first tick.
        return 5;
    }

    /**
     * DESIGN.md 9.4, D46: relays at the Twilight Forest's and the End's portals, on both sides. The Twilight Forest
     * keeps the Overworld's coordinates, so its relay stands where the Overworld one's portal leads.
     */
    private int buildTwilightAndEnd() {
        twilight = null;
        for (Integer id : DimensionManager.getStaticDimensionIDs()) {
            String provider;
            try {
                provider = DimensionManager.createProviderFor(id)
                    .getClass()
                    .getName();
            } catch (RuntimeException e) {
                continue;
            }
            if (Portals.TWILIGHT_WORLD.equals(provider)) twilight = load(id);
        }
        if (twilight == null) {
            t.fail("the Twilight Forest loads in the dev server", "no dimension has its world provider");
        } else {
            for (int cx = (SX + 8) >> 4; cx <= (SX + 72) >> 4; cx++) {
                for (int cz = (RELAY_Z - 32) >> 4; cz <= (RELAY_Z + 32) >> 4; cz++) t.keepLoaded(twilight, cx, cz);
            }
            clear(twilight, SX + 32, Y - 2, RELAY_Z - 4, SX + 48, Y + 4, RELAY_Z + 4);
            forgetNodes(twilight, SX + 32, RELAY_Z - 4, SX + 48, RELAY_Z + 4);
            relayTwilightFar = relay(twilight, SX + 40, Y, RELAY_Z);
            placeByClass(twilight, SX + 42, Y, RELAY_Z, Portals.TWILIGHT_PORTAL);
        }
        // The End: any of its portals will do, so an Overworld relay by an End portal far from a stronghold counts.
        relayEnd = relay(overworld, SX + 100, Y, RELAY_Z);
        overworld.setBlock(SX + 102, Y, RELAY_Z, Blocks.end_portal, 0, 2);
        end = load(1);
        if (end == null) {
            t.fail("the End loads in the dev server", "no world for dimension 1");
        } else {
            for (int cx = (MX - 16) >> 4; cx <= (MX + 16) >> 4; cx++) {
                for (int cz = (MZ - 16) >> 4; cz <= (MZ + 16) >> 4; cz++) t.keepLoaded(end, cx, cz);
            }
            clear(end, MX - 4, MY - 2, MZ - 4, MX + 4, MY + 4, MZ + 4);
            forgetNodes(end, MX - 4, MZ - 4, MX + 4, MZ + 4);
            relayEndFar = relay(end, MX, MY, MZ);
            // Outside the Overworld an End portal block takes itself away unless it's the exit portal being made.
            BlockEndPortal.field_149948_a = true;
            end.setBlock(MX + 2, MY, MZ, Blocks.end_portal, 0, 2);
            BlockEndPortal.field_149948_a = false;
        }
        // Machines join the network as they first tick.
        return 5;
    }

    private int form() {
        for (MTEEnhancedMultiBlockBase<?> machine : List.<MTEEnhancedMultiBlockBase<?>>of(pad, dishA, dishB)) {
            machine.checkStructure(true, machine.getBaseMetaTileEntity());
            // A newly built GT multiblock needs its maintenance done once, as a player does at its hatch.
            machine.fixAllIssues();
            machine.getBaseMetaTileEntity()
                .enableWorking();
        }
        dishA.refreshStructure();
        dishB.refreshStructure();
        dishTerminalA.scanCable();
        dishTerminalB.scanCable();
        relayTerminalOverworld.scanCable();
        if (relayTerminalNether != null) relayTerminalNether.scanCable();
        link(owner, overworld, dishA, dishTerminalA);
        link(owner, overworld, dishB, dishTerminalB);
        link(owner, overworld, relayOverworld, relayTerminalOverworld);
        if (nether != null) {
            link(netherHand, nether, relayNether, relayTerminalNether);
            // DESIGN.md 9.4: wrench one relay, go through the portal, and wrench the other.
            becom.links.forget(owner.getUniqueID());
            becom.links.wrench(owner, overworld, pos(relayOverworld));
            paired = becom.links.wrench(owner, nether, pos(relayNether));
            pairSaved = !NetworkSavedData.get(overworld)
                .isDirty()
                && !NetworkSavedData.get(nether)
                    .isDirty();
        }
        if (twilight != null) {
            becom.links.forget(owner.getUniqueID());
            becom.links.wrench(owner, overworld, pos(relayTwilight));
            pairedTwilight = becom.links.wrench(owner, twilight, pos(relayTwilightFar));
        }
        if (end != null) {
            becom.links.forget(owner.getUniqueID());
            becom.links.wrench(owner, overworld, pos(relayEnd));
            pairedEnd = becom.links.wrench(owner, end, pos(relayEndFar));
        }
        for (WorldServer w : new WorldServer[] { overworld, moon, nether }) {
            if (w == null) continue;
            for (Object o : w.theChunkProviderServer.loadedChunks) {
                becom.heights.chunkChanged(w, (net.minecraft.world.chunk.Chunk) o);
            }
        }
        return SelfTest.SETTLE_TICKS;
    }

    // endregion

    // region Launch Pad and satellites

    /** DESIGN.md 9.1, 3.2, 11.4: the pad forms, takes a rocket and fuel, and the screen counts the queue. */
    private int startLaunch() {
        t.check(
            "the Satellite Launch Pad forms: 5 by 5 clean stainless steel casings with its four hatches (DESIGN.md 3.2)",
            pad.mMachine,
            "");
        becom.space.set(0, 4);
        // Renamed in an anvil, as players do: its tag mustn't keep it on the pad.
        padBus
            .setInventorySlotContents(0, new ItemStack(BeComItems.satelliteRocket, 1).setStackDisplayName("Ad Astra"));
        int filled = padHatch.fill(new FluidStack(FluidRegistry.getFluid("fuel"), 2 * fuel()), true);
        t.check(
            "the pad's input hatch takes Galacticraft's rocket fuel",
            filled == 2 * fuel(),
            filled + " L of " + 2 * fuel());
        t.check("its screen counts one rocket waiting to launch (DESIGN.md 11.4)", pad.rocketsWaiting() == 1, "");
        // GT looks for work every 5 s while idle.
        return 8 * SelfTest.TICKS_PER_SECOND;
    }

    private int launchHeldAt;

    /** DESIGN.md 9.1: a block over the pad during a launch holds it. */
    private int coverLaunch() {
        t.check("the launch starts", pad.mMaxProgresstime > 0, "progress " + pad.mProgresstime);
        launchHeldAt = pad.mProgresstime;
        overworld.setBlock(SX + 2, Y + 3, SZ + 1, Blocks.stone);
        return 3 * SelfTest.TICKS_PER_SECOND;
    }

    private int checkLaunchHeld() {
        // The pad looks at the sky once a second, so it may run on for up to a second first.
        t.check(
            "a block over the pad during a launch holds it, and nothing launches",
            pad.mMaxProgresstime > 0 && pad.mProgresstime - launchHeldAt <= SelfTest.TICKS_PER_SECOND
                && becom.space.count(0) == 4,
            "progress " + launchHeldAt + " then " + pad.mProgresstime);
        overworld.setBlockToAir(SX + 2, Y + 3, SZ + 1);
        // Cleared, the launch goes on for what's left of its 30 s.
        return BeComConfig.get()
            .launchSeconds() * SelfTest.TICKS_PER_SECOND + 2 * SelfTest.TICKS_PER_SECOND;
    }

    private int fuel() {
        return BeComConfig.get()
            .launchFuel();
    }

    private int checkLaunch() {
        FluidStack left = padHatch.getFluid();
        t.check(
            "a launch takes the Satellite Rocket, a renamed one too, and 1,000 L of fuel, and puts a satellite over the "
                + "pad's dimension",
            becom.space.count(0) == 5 && pad.rocketsWaiting() == 0 && left != null && left.amount == fuel(),
            "satellites " + becom.space
                .count(0) + ", rockets " + pad.rocketsWaiting() + ", fuel " + (left == null ? 0 : left.amount));
        UUID padOwner = pad.getBaseMetaTileEntity()
            .getOwnerUuid();
        t.check(
            "...completing First satellite for the pad's owner (DESIGN.md 15.5)",
            padOwner != null && BeComQuests.reportedForSelfTest(QuestTask.FIRST_SATELLITE, padOwner),
            "owner " + padOwner);
        t.check(
            "...and Five satellites, now that its dimension has 5",
            padOwner != null && BeComQuests.reportedForSelfTest(QuestTask.FIVE_SATELLITES, padOwner),
            "");
        // A roof over one corner of the pad, and another rocket: it waits (DESIGN.md 9.1: open sky).
        overworld.setBlock(SX - 2, Y + 3, SZ + 4, Blocks.stone);
        padBus.setInventorySlotContents(0, new ItemStack(BeComItems.satelliteRocket, 1));
        return 8 * SelfTest.TICKS_PER_SECOND;
    }

    private int checkNoSkyNoLaunch() {
        t.check(
            "without open sky over the whole pad, nothing launches",
            !pad.openSky() && becom.space.count(0) == 5 && pad.rocketsWaiting() == 1 && pad.mMaxProgresstime == 0,
            "open sky " + pad.openSky() + ", satellites " + becom.space.count(0) + ", running " + pad.mMaxProgresstime);
        padBus.setInventorySlotContents(0, null);
        overworld.setBlockToAir(SX - 2, Y + 3, SZ + 4);
        return 1;
    }

    /** DESIGN.md 9.1, D23: a satellite comes down when its time is up. */
    private int startDecay() {
        countBeforeDecay = becom.space.count(0);
        long now = SpaceService.now();
        becom.space.satellitesForSelfTest()
            .add(new Satellites.Satellite(0, now, now + 1));
        return 2 * SelfTest.TICKS_PER_SECOND;
    }

    private int checkDecay() {
        t.check(
            "a satellite comes down once its lifetime is up, by the Overworld's world time (D23)",
            becom.space.count(0) == countBeforeDecay,
            becom.space.count(0) + " now, " + countBeforeDecay + " before");
        return 1;
    }

    // endregion

    // region Sky hops

    /** DESIGN.md 9.1, 8.2: a Mobile Phone under open sky reaches the satellites; a roof weakens the hop. */
    private int checkSkyHops() {
        Tunables cfg = BeComConfig.get();
        becom.space.set(0, cfg.satellitesMax());
        skyCaller = phonePlayer(overworld, "BeComSkyCaller", 0, SX + 150.5, Y, SKY_Z + 0.5);
        skyNumber = ItemCarriedPhone.number(skyCaller.inventory.mainInventory[0]);
        t.near(
            "a Mobile Phone under open sky with 100 satellites over it reaches them at Max's 24 dB (DESIGN.md 9.1)",
            cfg.levelMax(),
            skyEntry(skyNumber));
        FakePlayer roofed = phonePlayer(overworld, "BeComRoofed", 2, SX + 170.5, Y, SKY_Z + 0.5);
        overworld.setBlock(SX + 170, Y + ROOF, SKY_Z, Blocks.stone);
        becom.carried.refreshForSelfTest(roofed);
        double depth = Y + ROOF + 1 - (Y + 1.62);
        t.near(
            "...under a roof its hop loses the cover's 2.5 dB a block on UHF (DESIGN.md 6.1)",
            cfg.levelMax() - depth * cfg.uhf()
                .coverPerBlock(),
            skyEntry(ItemCarriedPhone.number(roofed.inventory.mainInventory[0])));
        overworld.setBlockToAir(SX + 170, Y + ROOF, SKY_Z);
        // Out here the biome may not rain, so the rain check stands where 0.3's phone checks found one that does.
        if (!t.phoneRains()) {
            t.fail("rain costs a Mobile Phone's sky hop 3 dB where it falls (D26)", "no rainy biome found to test in");
            return 1;
        }
        startRain();
        FakePlayer rained = phonePlayer(overworld, "BeComRained", 5, SelfTest.X0 - 19.5, Y, t.phoneZ() + 1.5);
        t.near(
            "rain costs a Mobile Phone's sky hop 3 dB where it falls (D26)",
            cfg.levelMax() - cfg.weatherLoss(1, 0),
            skyEntry(ItemCarriedPhone.number(rained.inventory.mainInventory[0])));
        clearWeather();
        return 1;
    }

    /** The phone's sky hop, from its service; negative infinity with none. */
    private double skyEntry(int number) {
        for (CallGraph.Entry entry : becom.carried.serviceOf(number)
            .entries()) {
            if (entry.vertex() instanceof CallGraph.SkyVertex) return entry.snr();
        }
        return Double.NEGATIVE_INFINITY;
    }

    /** DESIGN.md 9.1: two walkies far out of each other's and every antenna's reach talk through the satellites. */
    private int keySkyRadio() {
        walkieA = walkiePlayer("BeComSkyWalkieA", 3, SX + 200.5, RADIO_Z + 0.5);
        walkieB = walkiePlayer("BeComSkyWalkieB", 4, SX + 1200.5, RADIO_Z + 0.5);
        becom.radio.keyForSelfTest(walkieA.getUniqueID());
        return 2;
    }

    private int checkSkyRadio() {
        becom.radio.publishForSelfTest();
        RadioSnapshot.Transmission tx = becom.radio.published()
            .transmission(walkieA.getUniqueID());
        RadioSnapshot.Link link = null;
        if (tx != null) for (RadioSnapshot.Link l : tx.walkies()) if (walkieB.getUniqueID()
            .equals(l.holder())) link = l;
        if (link == null) {
            t.fail("walkies 1,000 blocks apart under open sky hear each other through the satellites", "no link");
        } else {
            t.near(
                "walkies 1,000 blocks apart under open sky hear each other through the satellites, at their hop",
                BeComConfig.get()
                    .levelMax(),
                link.snr());
        }
        return 1;
    }

    // endregion

    // region Satellite Dish

    /** DESIGN.md 9.2: two networks far apart, each with a dish, become one. */
    private int checkDishes() {
        Tunables cfg = BeComConfig.get();
        t.check(
            "the Satellite Dish forms: a 5 by 5 dish on a stand of three frame boxes (DESIGN.md 3.2)",
            dishA.mMachine && dishB.mMachine && dishA.isWorking() && dishB.isWorking(),
            "formed " + dishA.mMachine
                + "/"
                + dishB.mMachine
                + ", working "
                + dishA.isWorking()
                + "/"
                + dishB.isWorking());
        int a = dishPhoneA.number(), b = dishPhoneB.number();
        t.near(
            "Telephones 300 blocks apart, each on a line with a dish, reach each other at the dishes' hop",
            cfg.levelMax(),
            becom.calls.width(a, b));
        t.check(
            "...and the path goes up to the satellites",
            becom.network.graph()
                .hops(a, b)
                .contains(CallGraph.HopKind.SKY),
            String.valueOf(
                becom.network.graph()
                    .hops(a, b)));
        dishA.causeMaintenanceIssue();
        return 2;
    }

    private int checkDishMaintenance() {
        Tunables cfg = BeComConfig.get();
        t.near(
            "each maintenance problem costs a dish's hop 2 dB (D40)",
            cfg.levelMax() - cfg.dishMaintenanceLoss(),
            becom.calls.width(dishPhoneA.number(), dishPhoneB.number()));
        dishA.fixAllIssues();
        // A block over one corner of dish B.
        overworld.setBlock(DISH_X + DISH_GAP - 2, Y + 3, SZ + 4, Blocks.stone);
        return 2 * SelfTest.TICKS_PER_SECOND;
    }

    private int checkDishSky() {
        t.check(
            "a dish without open sky above the whole dish stops (DESIGN.md 9.2)",
            !dishB.isWorking() && becom.calls.width(dishPhoneA.number(), dishPhoneB.number()) < BeComConfig.get()
                .levelLow(),
            "working " + dishB.isWorking());
        overworld.setBlockToAir(DISH_X + DISH_GAP - 2, Y + 3, SZ + 4);
        becom.space.set(
            0,
            BeComConfig.get()
                .satellitesLow() - 1);
        return 2 * SelfTest.TICKS_PER_SECOND;
    }

    private int checkDishTooFew() {
        t.check(
            "with fewer than 5 satellites over the dimension, dishes join nothing (DESIGN.md 9.2)",
            dishB.isWorking() && becom.calls.width(dishPhoneA.number(), dishPhoneB.number()) < BeComConfig.get()
                .levelLow(),
            "working " + dishB.isWorking());
        becom.space.set(
            0,
            BeComConfig.get()
                .satellitesMax());
        return 1;
    }

    // endregion

    // region Rocket Radio Receiver and the Moon

    private NodeRef receiverRef() {
        return new NodeRef(
            moonId,
            BlockPos.pack(
                (int) Math.floor(rocket.posX),
                (int) Math.floor(rocket.posY + rocket.height),
                (int) Math.floor(rocket.posZ)));
    }

    /** DESIGN.md 16.2's assumption for 0.4, with a Tier 1 rocket on a launch pad on the Moon. */
    private int checkReceiver() {
        if (moon == null || rocket == null) return 1;
        NodeRef ref = receiverRef();
        boolean docked = rocket.getLandingPad() != null;
        t.check(
            "16.2: BeCom finds a Tier 1 rocket as it joins the Moon, and counts it standing unignited on a pad with a "
                + "receiver in its cargo",
            docked && becom.rockets.countsForSelfTest(ref),
            "docked " + docked
                + ", phase "
                + rocket.launchPhase
                + ", in its world "
                + (moon.getEntityByID(rocket.getEntityId()) == rocket)
                + ", counting "
                + becom.rockets.countingForSelfTest());
        AntennaNetwork.Antenna antenna = becom.antennas.antenna(ref);
        t.check(
            "...as a T1 antenna there (DESIGN.md 9.3)",
            antenna != null && antenna.tier() == 1 && becom.antennas.working(antenna),
            String.valueOf(antenna));
        rocket.setInventorySlotContents(0, null);
        becom.rockets.lookForSelfTest();
        boolean without = becom.rockets.countsForSelfTest(ref);
        rocket.setInventorySlotContents(0, new ItemStack(BeComItems.rocketRadioReceiver));
        becom.rockets.lookForSelfTest();
        t.check(
            "...and not without the receiver in its cargo",
            !without && becom.rockets.countsForSelfTest(ref),
            "without " + without);
        // A rocket from another mod that throws when asked for its pad mustn't take the server tick down.
        EntityTier1Rocket broken = new EntityTier1Rocket(
            moon,
            MX + 6.5,
            MY + 2,
            MZ + 0.5,
            IRocketType.EnumRocketType.INVENTORY27) {

            @Override
            public IFuelDock getLandingPad() {
                throw new IllegalStateException("a rocket that can't say where it stands");
            }
        };
        moon.spawnEntityInWorld(broken);
        boolean threw = false;
        try {
            becom.rockets.lookForSelfTest();
        } catch (RuntimeException e) {
            threw = true;
        }
        t.check(
            "a rocket that throws when looked at is dropped, and the others go on counting",
            !threw && !becom.rockets.followsForSelfTest(broken) && becom.rockets.countsForSelfTest(ref),
            "threw " + threw);
        broken.setDead();
        return 1;
    }

    /** 0.4's "Done when", first half: a call connects from the Overworld to the Moon (DESIGN.md 9.5). */
    private int checkMoonCall() {
        if (moon == null || rocket == null) return 1;
        becom.calls.voiceForSelfTest(id -> true);
        becom.carried.refreshForSelfTest(skyCaller);
        becom.carried.refreshForSelfTest(moonCaller);
        NodeRef receiver = receiverRef();
        boolean served = false;
        for (CallGraph.Entry entry : becom.carried.serviceOf(moonNumber)
            .entries()) {
            served |= entry.vertex()
                .equals(new CallGraph.NodeVertex(receiver.dimension(), receiver.pos()));
        }
        t.check("a Mobile Phone on the Moon gets service from the landed rocket's receiver", served, "");
        CallBook.DialResult result = becom.calls.dialCarried(skyCaller, skyNumber, moonNumber);
        becom.calls.phoneKey(moonCaller, false);
        Call call = becom.calls.book()
            .callOfPhone(moonNumber);
        UUID s = skyCaller.getUniqueID(), m = moonCaller.getUniqueID();
        t.check(
            "0.4's \"Done when\": a call connects from the Overworld to the Moon, at the uplink's High",
            result == CallBook.DialResult.RINGING && call != null
                && call.state() == Call.State.CONNECTED
                && call.level(s, m) == SignalLevel.HIGH
                && call.level(m, s) == SignalLevel.HIGH,
            result + ", "
                + (call == null ? "no call" : call.state() + " " + call.level(s, m) + "/" + call.level(m, s)));
        t.check(
            "...through the uplink to the Overworld's satellites (DESIGN.md 9.3)",
            becom.network.graph()
                .hops(becom.calls.entriesOf(skyNumber), becom.calls.entriesOf(moonNumber))
                .contains(CallGraph.HopKind.UPLINK),
            "");
        t.check(
            "...completing Call between dimensions for both (DESIGN.md 15.5)",
            BeComQuests.reportedForSelfTest(QuestTask.CALL_BETWEEN_DIMENSIONS, s)
                && BeComQuests.reportedForSelfTest(QuestTask.CALL_BETWEEN_DIMENSIONS, m),
            "");
        becom.calls.phoneKey(skyCaller, false);
        // A whole network reaches the planet through an Overworld dish: the gateway is the lower of the dish's hop
        // and the uplink.
        t.near(
            "a Telephone on a line with an Overworld dish reaches the Moon phone at the uplink's High",
            BeComConfig.get()
                .levelScale()
                .threshold(SignalLevel.HIGH),
            becom.calls.width(dishPhoneA.number(), moonNumber));
        return 1;
    }

    // endregion

    // region Portal Relay and the Nether

    /** DESIGN.md 9.4, D46: which portals relays work at, and the pair through the Nether portal. */
    private int checkRelays() {
        t.check(
            "a relay by a Nether portal sees it, and works",
            relayOverworld.portals()
                .contains(Portals.Kind.NETHER) && relayOverworld.isWorking(),
            relayOverworld.portals() + ", working " + relayOverworld.isWorking());
        t.check(
            "a relay by a Twilight Forest portal knows it by its block class",
            twilightPlaced && relayTwilight.look()
                .contains(Portals.Kind.TWILIGHT_FOREST),
            "placed " + twilightPlaced + ", " + relayTwilight.portals());
        t.check(
            "a relay by a Personal Space portal knows it by its block class",
            personalPlaced && relayPersonal.look()
                .contains(Portals.Kind.PERSONAL_SPACE),
            "placed " + personalPlaced + ", " + relayPersonal.portals());
        if (twilight != null) {
            t.check(
                "relays at a Twilight Forest portal on both sides pair into the Twilight Forest, a High hop",
                pairedTwilight == LinkService.Outcome.LINKED && relayTwilightFar.isWorking()
                    && Math.abs(
                        becom.network.graph()
                            .width(vertex(relayTwilight), vertex(relayTwilightFar)) - high())
                        < SelfTest.EPS,
                pairedTwilight + ", working " + relayTwilightFar.isWorking());
        }
        if (end != null) {
            t.check(
                "relays at an End portal and at one in the End pair into the End, a High hop",
                pairedEnd == LinkService.Outcome.LINKED && relayEndFar.isWorking()
                    && relayEnd.look()
                        .contains(Portals.Kind.END)
                    && Math.abs(
                        becom.network.graph()
                            .width(vertex(relayEnd), vertex(relayEndFar)) - high())
                        < SelfTest.EPS,
                pairedEnd + ", working " + relayEndFar.isWorking());
        }
        t.check(
            "a relay by no portal doesn't work",
            relayBare.portals()
                .isEmpty() && !relayBare.isWorking(),
            relayBare.portals() + ", working " + relayBare.isWorking());
        if (nether == null) return 1;
        t.check(
            "relays on both sides of a Nether portal pair: wrench one, go through, wrench the other (DESIGN.md 9.4)",
            paired == LinkService.Outcome.LINKED && relayNether.ref()
                .equals(becom.network.partnerOf(relayOverworld.ref())),
            String.valueOf(paired));
        t.check("...and both halves of the pair are on disk at once, each in its dimension's file", pairSaved, "");
        t.check(
            "...completing Portal Relay pair for whoever linked them (DESIGN.md 15.5)",
            BeComQuests.reportedForSelfTest(QuestTask.PORTAL_RELAY_PAIR, owner.getUniqueID()),
            "");
        return 1;
    }

    /** 0.4's "Done when", second half: a call connects from the Overworld to the Nether (DESIGN.md 9.5). */
    private int checkNetherCall() {
        if (nether == null) return 1;
        CallService calls = becom.calls;
        int a = relayPhoneOverworld.number(), b = relayPhoneNether.number();
        calls.voiceForSelfTest(id -> true);
        calls.presentForSelfTest(owner);
        calls.presentForSelfTest(netherHand);
        owner.setPosition(
            relayPhoneOverworld.xCoord + 1.5,
            relayPhoneOverworld.yCoord,
            relayPhoneOverworld.zCoord + 0.5);
        netherHand.setPosition(relayPhoneNether.xCoord + 1.5, relayPhoneNether.yCoord, relayPhoneNether.zCoord + 0.5);
        CallService.Pickup taken = calls.takeHandset(owner, relayPhoneOverworld);
        CallBook.DialResult result = relayPhoneNether.ringing() ? CallBook.DialResult.RINGING : calls.dial(owner, a, b);
        CallService.Pickup answered = calls.takeHandset(netherHand, relayPhoneNether);
        Call call = calls.book()
            .callOfPhone(b);
        t.check(
            "0.4's \"Done when\": a call connects from a Telephone in the Overworld to one in the Nether",
            taken != CallService.Pickup.REFUSED && result == CallBook.DialResult.RINGING
                && answered == CallService.Pickup.ANSWERED
                && call != null
                && call.state() == Call.State.CONNECTED,
            taken + ", " + result + ", " + answered);
        t.check(
            "...through the relay pair (DESIGN.md 9.4)",
            becom.network.graph()
                .hops(a, b)
                .contains(CallGraph.HopKind.RELAY),
            String.valueOf(
                becom.network.graph()
                    .hops(a, b)));
        t.check(
            "...completing Call between dimensions for both",
            BeComQuests.reportedForSelfTest(QuestTask.CALL_BETWEEN_DIMENSIONS, netherHand.getUniqueID()),
            "");
        calls.hangUpHandset(netherHand.getUniqueID());
        calls.hangUpHandset(owner.getUniqueID());
        return 1;
    }

    /** DESIGN.md 9.4, 12.2, D37: one partner each, and unpairing. */
    private int checkRelayPairRules() {
        if (nether == null) return 1;
        becom.links.forget(owner.getUniqueID());
        becom.links.wrench(owner, overworld, pos(relayTwilight));
        LinkService.Outcome taken = becom.links.wrench(owner, nether, pos(relayNether));
        t.check("a relay already paired refuses a second partner", taken == LinkService.Outcome.REFUSED, taken + "");
        becom.links.wrench(owner, overworld, pos(relayOverworld));
        LinkService.Outcome unpaired = becom.links.wrench(owner, nether, pos(relayNether));
        t.check(
            "wrenching both relays of a pair again unpairs them",
            unpaired == LinkService.Outcome.UNLINKED && becom.network.partnerOf(relayOverworld.ref()) == null,
            unpaired + "");
        // DESIGN.md 9.4: on opposite sides of one portal. 1,000 blocks off in the Nether is where another one leads.
        becom.links.wrench(owner, overworld, pos(relayBare));
        LinkService.Outcome far = becom.links.wrench(owner, nether, pos(relayFarNether));
        becom.links.wrench(owner, overworld, pos(relayBare));
        LinkService.Outcome near = becom.links.wrench(owner, nether, pos(relayNether));
        t.check(
            "a relay pairs only with one where its portal leads: not 1,000 blocks off in the Nether, but at the spot",
            far == LinkService.Outcome.REFUSED && near == LinkService.Outcome.LINKED,
            far + ", " + near);
        becom.network.unpair(relayBare.ref(), relayNether.ref());
        return 1;
    }

    // endregion

    /** DESIGN.md 12.3: the admin command that sets a dimension's satellites for testing. */
    private int checkSatellitesCommand() {
        new CommandBeCom().processCommand(becom.server, new String[] { "satellites", "0", "7" });
        t.check("/becom satellites 0 7 puts 7 satellites over the Overworld", becom.space.count(0) == 7, "");
        // None left for the next run's checks before 0.4's.
        becom.space.set(0, 0);
        return 1;
    }

    // region Helpers

    private static CallGraph.NodeVertex vertex(MTEPortalRelay relay) {
        NodeRef ref = relay.ref();
        return new CallGraph.NodeVertex(ref.dimension(), ref.pos());
    }

    private static double high() {
        return BeComConfig.get()
            .levelScale()
            .threshold(SignalLevel.HIGH);
    }

    private static WorldServer load(int dimension) {
        WorldServer world = DimensionManager.getWorld(dimension);
        if (world == null && DimensionManager.isDimensionRegistered(dimension)) {
            DimensionManager.initDimension(dimension);
            world = DimensionManager.getWorld(dimension);
        }
        return world;
    }

    private static void clear(WorldServer world, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    if (!world.isAirBlock(x, y, z)) world.setBlockToAir(x, y, z);
                }
            }
        }
    }

    /** Drops the nodes an earlier run left in an area, so nothing of theirs links or pairs. */
    private void forgetNodes(WorldServer world, int x0, int z0, int x1, int z1) {
        NetworkModel model = becom.network.model(world);
        for (NetNode node : new ArrayList<>(model.nodes())) {
            if (node.x() >= x0 && node.x() <= x1 && node.z() >= z0 && node.z() <= z1) model.remove(node.pos());
        }
    }

    private <T extends IMetaTileEntity> T place(WorldServer world, T machine, int x, int y, int z) {
        ItemStack stack = machine.getStackForm(1);
        ((ItemBlock) stack.getItem()).placeBlockAt(stack, owner, world, x, y, z, 1, 0.5f, 0.5f, 0.5f, 0);
        TileEntity tile = world.getTileEntity(x, y, z);
        @SuppressWarnings("unchecked")
        T placed = (T) ((IGregTechTileEntity) tile).getMetaTileEntity();
        return placed;
    }

    /** A multiblock's controller facing north, its structure built behind it the way the build tool does. */
    private <T extends MTEEnhancedMultiBlockBase<?>> T multiblock(WorldServer world, T machine, int x, int y, int z) {
        T controller = place(world, machine, x, y, z);
        controller.getBaseMetaTileEntity()
            .setFrontFacing(ForgeDirection.NORTH);
        ((IConstructable) controller).construct(machine.getStackForm(1), false);
        return controller;
    }

    /** Swaps the casing {@code depth} behind and {@code across} beside a flat multiblock's controller for a hatch. */
    private IMetaTileEntity hatch(WorldServer world, MTEEnhancedMultiBlockBase<?> controller, int depth, int across,
        ItemList hatch) {
        IGregTechTileEntity tile = controller.getBaseMetaTileEntity();
        ForgeDirection back = controller.getExtendedFacing()
            .getDirection()
            .getOpposite();
        ForgeDirection left = back.getRotation(ForgeDirection.UP);
        int x = tile.getXCoord() + back.offsetX * depth + left.offsetX * across;
        int z = tile.getZCoord() + back.offsetZ * depth + left.offsetZ * across;
        world.setBlockToAir(x, tile.getYCoord(), z);
        IMetaTileEntity mte = GregTechAPI.METATILEENTITIES[hatch.get(1)
            .getItemDamage()];
        return place(world, mte, x, tile.getYCoord(), z);
    }

    private MTELineTerminal terminal(WorldServer world, int x, int y, int z, int towardsPhone) {
        MTELineTerminal terminal = place(world, BeComMachines.lineTerminal, x, y, z);
        t.power(terminal.getBaseMetaTileEntity());
        world.setBlock(x, y, z + towardsPhone, BeComBlocks.phoneCable);
        return terminal;
    }

    private MTEPortalRelay relay(WorldServer world, int x, int y, int z) {
        MTEPortalRelay relay = place(world, BeComMachines.portalRelay, x, y, z);
        t.power(relay.getBaseMetaTileEntity());
        return relay;
    }

    private TileTelephone telephone(WorldServer world, int x, int y, int z, FakePlayer placer) {
        world.setBlock(x, y, z, BeComBlocks.telephone);
        BeComBlocks.telephone.onBlockPlacedBy(world, x, y, z, placer, new ItemStack(BeComBlocks.telephone));
        TileTelephone tile = (TileTelephone) world.getTileEntity(x, y, z);
        // A fake player never owns a phone (X6); the self-test's owner stands in for the real player.
        PhoneRecord record = becom.phones.record(tile.number());
        if (record != null && record.owner() == null) {
            record.setOwner(owner.getUniqueID(), owner.getCommandSenderName());
        }
        return tile;
    }

    /** An obsidian frame 4 wide and 5 tall along x from {@code (x, y, z)}, lit the way flint and steel lights it. */
    private static void netherPortal(WorldServer world, int x, int y, int z) {
        for (int dx = 0; dx < 4; dx++) {
            for (int dy = 0; dy < 5; dy++) {
                boolean edge = dx == 0 || dx == 3 || dy == 0 || dy == 4;
                world.setBlock(x + dx, y + dy, z, edge ? Blocks.obsidian : Blocks.air);
            }
        }
        ((BlockPortal) Blocks.portal).func_150000_e(world, x + 1, y + 1, z);
    }

    /** Places the block of class {@code name} from another mod, if it's installed; false when it isn't. */
    private static boolean placeByClass(WorldServer world, int x, int y, int z, String name) {
        for (Object o : Block.blockRegistry) {
            if (o.getClass()
                .getName()
                .equals(name)) {
                // No neighbour updates: a portal block alone, without its pool or frame, would take itself down.
                return world.setBlock(x, y, z, (Block) o, 0, 2);
            }
        }
        return false;
    }

    private void link(FakePlayer player, WorldServer world, IMetaTileEntity a, IMetaTileEntity b) {
        if (becom.network.model(world)
            .linked(pos(a), pos(b))) return;
        becom.links.forget(player.getUniqueID());
        becom.links.wrench(player, world, pos(a));
        becom.links.wrench(player, world, pos(b));
    }

    private static long pos(IMetaTileEntity mte) {
        IGregTechTileEntity tile = mte.getBaseMetaTileEntity();
        return BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
    }

    private static GameProfile profile(String name, int n) {
        return new GameProfile(
            UUID.fromString(String.format(Locale.ROOT, "b3c0b3c0-0000-4000-8000-0000000004%02x", n)),
            name);
    }

    /** A fake player at {@code (x, y, z)} of {@code world}, in the air, carrying a charged Mobile Phone. */
    private FakePlayer phonePlayer(WorldServer world, String name, int n, double x, double y, double z) {
        FakePlayer player = FakePlayerFactory.get(world, profile(name, 0x20 + n));
        player.setPosition(x, y, z);
        java.util.Arrays.fill(player.inventory.mainInventory, null);
        player.inventory.currentItem = 0;
        ItemStack phone = new ItemStack(BeComItems.mobilePhone);
        ElectricItem.manager.charge(phone, BeComItems.mobilePhone.getMaxCharge(phone), Integer.MAX_VALUE, true, false);
        player.inventory.mainInventory[0] = phone;
        becom.carried.presentForSelfTest(player);
        becom.calls.presentForSelfTest(player);
        becom.carried.refreshForSelfTest(player);
        return player;
    }

    /** A fake player in the Overworld's sky with a charged walkie-talkie on the self-test's channel. */
    private FakePlayer walkiePlayer(String name, int n, double x, double z) {
        FakePlayer player = FakePlayerFactory.get(overworld, profile(name, 0x30 + n));
        player.setPosition(x, Y, z);
        java.util.Arrays.fill(player.inventory.mainInventory, null);
        player.inventory.currentItem = 0;
        ItemStack walkie = new ItemStack(BeComItems.walkieTalkie);
        ElectricItem.manager
            .charge(walkie, BeComItems.walkieTalkie.getMaxCharge(walkie), Integer.MAX_VALUE, true, false);
        ItemWalkieTalkie.setChannel(walkie, RADIO_CHANNEL);
        player.inventory.mainInventory[0] = walkie;
        becom.radio.refreshForSelfTest(player);
        return player;
    }

    /** Steady rain over the Overworld, no thunder, until {@link #clearWeather()}. */
    private void startRain() {
        WorldInfo info = overworld.getWorldInfo();
        info.setRaining(true);
        info.setRainTime(1_000_000);
        info.setThundering(false);
        info.setThunderTime(1_000_000);
        overworld.prevRainingStrength = 1;
        overworld.rainingStrength = 1;
        overworld.prevThunderingStrength = 0;
        overworld.thunderingStrength = 0;
    }

    private void clearWeather() {
        WorldInfo info = overworld.getWorldInfo();
        info.setRaining(false);
        info.setThundering(false);
        overworld.prevRainingStrength = 0;
        overworld.rainingStrength = 0;
        overworld.prevThunderingStrength = 0;
        overworld.thunderingStrength = 0;
    }

    // endregion
}
