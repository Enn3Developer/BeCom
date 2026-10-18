package com.enn3developer.becom.recipe;

import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.enn3developer.becom.block.BeComBlocks;
import com.enn3developer.becom.block.BlockPhoneCable;
import com.enn3developer.becom.item.BeComItems;
import com.enn3developer.becom.item.ItemSwitchingRack;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.machine.BeComMachines;

import appeng.api.AEApi;
import appeng.api.util.AEColor;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import micdoodle8.mods.galacticraft.core.items.GCItems;

/**
 * BeCom's recipes, after GT's conventions: controllers are shaped crafting around a machine
 * hull, devices and parts come from the assembler.
 */
public final class BeComRecipes {

    // Recipe values: the tier each machine belongs to, and GT's usual durations and EU/t for it.
    private static final int PHONE_CABLE_EUT = 8;
    private static final int PHONE_CABLE_SECONDS = 5;
    private static final int PHONE_CABLE_RUBBER_L = 72;
    private static final int PHONE_CABLE_CIRCUIT = 2;
    private static final int TELEPHONE_EUT = 30;
    private static final int TELEPHONE_SECONDS = 10;
    private static final int HANDHELD_EUT = 120;
    private static final int HANDHELD_SECONDS = 20;
    private static final int MOBILE_EUT = 480;
    private static final int MOBILE_SECONDS = 30;
    private static final int ME_PHONE_EUT = 1920;
    private static final int ME_PHONE_SECONDS = 30;
    private static final int COAXIAL_EUT = 120;
    private static final int FIBRE_EUT = 480;
    private static final int CABLE_TIER_SECONDS = 10;
    private static final int RACK_SECONDS = 20;
    private static final int SPACE_EUT = 480;
    private static final int SATELLITE_SECONDS = 40;
    private static final int SATELLITE_ROCKET_SECONDS = 60;
    private static final int RECEIVER_SECONDS = 30;

    private BeComRecipes() {}

    private static void router(gregtech.api.metatileentity.MetaTileEntity machine, CableTier tier, Materials circuit,
        ItemList hull) {
        GTModHandler.addCraftingRecipe(
            machine.getStackForm(1),
            GTModHandler.RecipeBits.BUFFERED,
            new Object[] { "WCW", "PHP", 'W', OrePrefixes.wireGt01.get(Materials.Steel), 'C',
                OrePrefixes.circuit.get(circuit), 'P', BlockPhoneCable.stack(tier, 1), 'H', hull });
    }

    private static void lineHatch(gregtech.api.metatileentity.MetaTileEntity machine, CableTier tier, ItemList hull) {
        GTModHandler.addCraftingRecipe(
            machine.getStackForm(1),
            GTModHandler.RecipeBits.BUFFERED,
            new Object[] { "P", "H", 'P', BlockPhoneCable.stack(tier, 1), 'H', hull });
    }

    private static void relay(gregtech.api.metatileentity.MetaTileEntity machine, ItemList emitter, Materials circuit,
        ItemList hull, Materials cable, ItemList sensor) {
        GTModHandler.addCraftingRecipe(
            machine.getStackForm(1),
            GTModHandler.RecipeBits.BUFFERED,
            new Object[] { "YEY", "CHC", "TST", 'Y', new ItemStack(Items.ender_eye), 'E', emitter, 'C',
                OrePrefixes.circuit.get(circuit), 'H', hull, 'T', OrePrefixes.cableGt01.get(cable), 'S', sensor });
    }

    private static void rack(CableTier tier, Materials circuit, Materials plate, int eut) {
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.circuit, circuit, 2),
                GTOreDictUnificator.get(OrePrefixes.plate, plate, 4),
                BlockPhoneCable.stack(tier, 4))
            .itemOutputs(ItemSwitchingRack.stack(tier, 1))
            .duration(RACK_SECONDS * SECONDS)
            .eut(eut)
            .addTo(assemblerRecipes);
    }

    public static void register() {
        ItemStack cable = new ItemStack(BeComBlocks.phoneCable);

        // BeCom Cards: OpenComputers' card base with an MV circuit; the radio card adds an emitter and a sensor, the
        // wired one Coaxial Cable.
        li.cil.oc.api.detail.ItemInfo base = li.cil.oc.api.Items.get("card");
        Object card = base == null ? OrePrefixes.circuit.get(Materials.LV) : base.createItemStack(1);
        GTModHandler.addCraftingRecipe(
            com.enn3developer.becom.item.ItemBecomCard.stack(com.enn3developer.becom.item.ItemBecomCard.RADIO, 1),
            GTModHandler.RecipeBits.BUFFERED,
            new Object[] { "ECS", " B ", 'E', ItemList.Emitter_MV, 'C', OrePrefixes.circuit.get(Materials.MV), 'S',
                ItemList.Sensor_MV, 'B', card });
        GTModHandler.addCraftingRecipe(
            com.enn3developer.becom.item.ItemBecomCard.stack(com.enn3developer.becom.item.ItemBecomCard.WIRED, 1),
            GTModHandler.RecipeBits.BUFFERED,
            new Object[] { "WCW", " B ", 'W', BlockPhoneCable.stack(CableTier.COAXIAL, 1), 'C',
                OrePrefixes.circuit.get(Materials.MV), 'B', card });

        // Phone Cable ×2: red alloy wire in rubber. GT's own Red Alloy Cable takes the same wire with circuit 24
        // and more rubber; without a circuit of its own, a machine that last made Phone Cable would keep making it
        // from GT's inputs.
        GTValues.RA.stdBuilder()
            .itemInputs(GTOreDictUnificator.get(OrePrefixes.wireGt01, Materials.RedAlloy, 1))
            .circuit(PHONE_CABLE_CIRCUIT)
            .fluidInputs(Materials.Rubber.getMolten(PHONE_CABLE_RUBBER_L))
            .itemOutputs(new ItemStack(BeComBlocks.phoneCable, 2))
            .duration(PHONE_CABLE_SECONDS * SECONDS)
            .eut(PHONE_CABLE_EUT)
            .addTo(assemblerRecipes);

        // Coaxial ×2: an annealed copper core, an aluminium foil shield, polyethylene between and around.
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.wireGt01, Materials.AnnealedCopper, 1),
                GTOreDictUnificator.get(OrePrefixes.foil, Materials.Aluminium, 1))
            .circuit(PHONE_CABLE_CIRCUIT)
            .fluidInputs(Materials.Polyethylene.getMolten(PHONE_CABLE_RUBBER_L))
            .itemOutputs(BlockPhoneCable.stack(CableTier.COAXIAL, 2))
            .duration(CABLE_TIER_SECONDS * SECONDS)
            .eut(COAXIAL_EUT)
            .addTo(assemblerRecipes);

        // Fibre ×2: drawn glass for the core, aluminium foil for strength, a PVC jacket.
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Circuit_Parts_Glass_Tube.get(1),
                GTOreDictUnificator.get(OrePrefixes.foil, Materials.Aluminium, 2))
            .circuit(PHONE_CABLE_CIRCUIT)
            .fluidInputs(Materials.PolyvinylChloride.getMolten(PHONE_CABLE_RUBBER_L))
            .itemOutputs(BlockPhoneCable.stack(CableTier.FIBRE, 2))
            .duration(CABLE_TIER_SECONDS * SECONDS)
            .eut(FIBRE_EUT)
            .addTo(assemblerRecipes);

        // Switching Racks: the tier's circuits on a rack of its plates, patched with its cable.
        rack(CableTier.PHONE, Materials.LV, Materials.Steel, TELEPHONE_EUT);
        rack(CableTier.COAXIAL, Materials.MV, Materials.Aluminium, COAXIAL_EUT);
        rack(CableTier.FIBRE, Materials.HV, Materials.StainlessSteel, FIBRE_EUT);

        // Telephone: the circuit, a note block for the bell, a steel body and the cord.
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.LV, 1),
                new ItemStack(Blocks.noteblock),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Steel, 2),
                cable.copy())
            .itemOutputs(new ItemStack(BeComBlocks.telephone))
            .duration(TELEPHONE_SECONDS * SECONDS)
            .eut(TELEPHONE_EUT)
            .addTo(assemblerRecipes);

        // Routers: a hull of the tier with its circuit, two of its cables to the devices and two wires to the
        // line.
        router(BeComMachines.router, CableTier.PHONE, Materials.LV, ItemList.Hull_LV);
        router(BeComMachines.routerMV, CableTier.COAXIAL, Materials.MV, ItemList.Hull_MV);
        router(BeComMachines.routerHV, CableTier.FIBRE, Materials.HV, ItemList.Hull_HV);

        // Line Hatches: a hull of the tier with a socket for its cable.
        lineHatch(BeComMachines.lineHatch, CableTier.PHONE, ItemList.Hull_LV);
        lineHatch(BeComMachines.lineHatchMV, CableTier.COAXIAL, ItemList.Hull_MV);
        lineHatch(BeComMachines.lineHatchHV, CableTier.FIBRE, ItemList.Hull_HV);

        // Exchange: an LV hull among circuits and the lines it switches.
        GTModHandler.addCraftingRecipe(
            BeComMachines.exchange.getStackForm(1),
            GTModHandler.RecipeBits.BUFFERED,
            new Object[] { "CPC", "PHP", "CPC", 'C', OrePrefixes.circuit.get(Materials.LV), 'P', cable.copy(), 'H',
                ItemList.Hull_LV });

        // Antenna heads: a hull of the tier with an emitter to transmit and a sensor to receive, the
        // tier's circuits and cable, and two long rods for the elements.
        antenna(BeComMachines.antennaT1, 1, Materials.LV, Materials.Tin, Materials.Steel);
        antenna(BeComMachines.antennaT2, 2, Materials.MV, Materials.Copper, Materials.Aluminium);
        antenna(BeComMachines.antennaT3, 3, Materials.HV, Materials.Gold, Materials.StainlessSteel);

        // Transceiver: an MV hull with an emitter and a sensor, its circuits and cable, and a note block for the
        // loudspeaker.
        GTModHandler.addCraftingRecipe(
            BeComMachines.transceiver.getStackForm(1),
            GTModHandler.RecipeBits.BUFFERED,
            new Object[] { "ENS", "CHC", "W W", 'E', ItemList.Emitter_MV, 'N', new ItemStack(Blocks.noteblock), 'S',
                ItemList.Sensor_MV, 'C', OrePrefixes.circuit.get(Materials.MV), 'H', ItemList.Hull_MV, 'W',
                OrePrefixes.cableGt01.get(Materials.Copper) });

        // Videophone: an MV hull with a screen, a sensor for the camera, its circuits, a note block for the loudspeaker
        // and two Phone Cables to its Router.
        GTModHandler.addCraftingRecipe(
            BeComMachines.videophone.getStackForm(1),
            GTModHandler.RecipeBits.BUFFERED,
            new Object[] { "DSN", "CHC", "P P", 'D', ItemList.Cover_Screen, 'S', ItemList.Sensor_MV, 'N',
                new ItemStack(Blocks.noteblock), 'C', OrePrefixes.circuit.get(Materials.MV), 'H', ItemList.Hull_MV, 'P',
                cable.copy() });

        // Signal Analyzer: it only listens, so a sensor and no emitter, behind a screen in an aluminium case.
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Sensor_MV.get(1),
                ItemList.Cover_Screen.get(1),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.MV, 1),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Aluminium, 4))
            .itemOutputs(new ItemStack(BeComItems.signalAnalyzer))
            .duration(HANDHELD_SECONDS * SECONDS)
            .eut(HANDHELD_EUT)
            .addTo(assemblerRecipes);

        // Walkie-Talkie: emitter and sensor, its circuit and battery, in a polyethylene case. GT's own MV tools take
        // the lithium battery.
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Emitter_MV.get(1),
                ItemList.Sensor_MV.get(1),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.MV, 1),
                ItemList.Battery_RE_MV_Lithium.get(1),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Polyethylene, 2))
            .itemOutputs(new ItemStack(BeComItems.walkieTalkie))
            .duration(HANDHELD_SECONDS * SECONDS)
            .eut(HANDHELD_EUT)
            .addTo(assemblerRecipes);

        // Mobile Phone: an MV emitter and sensor are enough for UHF; the screen, an HV circuit and
        // battery make it a phone, in the walkie's polyethylene case.
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Emitter_MV.get(1),
                ItemList.Sensor_MV.get(1),
                ItemList.Cover_Screen.get(1),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.HV, 1),
                ItemList.Battery_RE_HV_Lithium.get(1),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Polyethylene, 2))
            .itemOutputs(new ItemStack(BeComItems.mobilePhone))
            .duration(MOBILE_SECONDS * SECONDS)
            .eut(MOBILE_EUT)
            .addTo(assemblerRecipes);

        // ME Phone: a Mobile Phone with an AE2 Wireless Terminal's link, an EV emitter and two EV circuits.
        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(BeComItems.mobilePhone),
                AEApi.instance()
                    .definitions()
                    .items()
                    .wirelessTerminal()
                    .maybeStack(1)
                    .get(),
                ItemList.Emitter_EV.get(1),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 2))
            .itemOutputs(new ItemStack(BeComItems.mePhone))
            .duration(ME_PHONE_SECONDS * SECONDS)
            .eut(ME_PHONE_EUT)
            .addTo(assemblerRecipes);

        // Satellite: an HV emitter to send and a sensor to receive, two HV circuits, two solar panels for power in
        // orbit
        // and an aluminium frame.
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Emitter_HV.get(1),
                ItemList.Sensor_HV.get(1),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.HV, 2),
                ItemList.Cover_SolarPanel.get(2),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Aluminium, 4))
            .itemOutputs(new ItemStack(BeComItems.satellite))
            .duration(SATELLITE_SECONDS * SECONDS)
            .eut(SPACE_EUT)
            .addTo(assemblerRecipes);

        // Satellite Rocket: a Satellite on Galacticraft's Tier 1 rocket parts.
        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(BeComItems.satellite),
                new ItemStack(GCItems.partNoseCone),
                new ItemStack(GCItems.rocketEngine, 1, 0),
                new ItemStack(GCItems.partFins, 4),
                new ItemStack(GCItems.heavyPlatingTier1, 4))
            .itemOutputs(new ItemStack(BeComItems.satelliteRocket))
            .duration(SATELLITE_ROCKET_SECONDS * SECONDS)
            .eut(SPACE_EUT)
            .addTo(assemblerRecipes);

        // Rocket Radio Receiver: an HV emitter and sensor, two HV circuits, in stainless steel to fly in a rocket.
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Emitter_HV.get(1),
                ItemList.Sensor_HV.get(1),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.HV, 2),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.StainlessSteel, 4))
            .itemOutputs(new ItemStack(BeComItems.rocketRadioReceiver))
            .duration(RECEIVER_SECONDS * SECONDS)
            .eut(SPACE_EUT)
            .addTo(assemblerRecipes);

        // Satellite Launch Pad: an HV hull with pumps for the fuel, a robot arm to raise the rocket,
        // its circuits and gold cables.
        GTModHandler.addCraftingRecipe(
            BeComMachines.launchPad.getStackForm(1),
            GTModHandler.RecipeBits.BUFFERED,
            new Object[] { "PRP", "CHC", "TCT", 'P', ItemList.Electric_Pump_HV, 'R', ItemList.Robot_Arm_HV, 'C',
                OrePrefixes.circuit.get(Materials.HV), 'H', ItemList.Hull_HV, 'T',
                OrePrefixes.cableGt01.get(Materials.Gold) });

        // Portal Relays: a hull of the tier with an emitter and a sensor, its circuits and cables, and
        // two Eyes of Ender to find the portal.
        relay(
            BeComMachines.portalRelay,
            ItemList.Emitter_MV,
            Materials.MV,
            ItemList.Hull_MV,
            Materials.Copper,
            ItemList.Sensor_MV);
        relay(
            BeComMachines.portalRelayHV,
            ItemList.Emitter_HV,
            Materials.HV,
            ItemList.Hull_HV,
            Materials.Gold,
            ItemList.Sensor_HV);
        relay(
            BeComMachines.portalRelayEV,
            ItemList.Emitter_EV,
            Materials.EV,
            ItemList.Hull_EV,
            Materials.Aluminium,
            ItemList.Sensor_EV);

        // Satellite Dish: an HV hull with two emitters and two sensors, its circuits and gold cables.
        GTModHandler.addCraftingRecipe(
            BeComMachines.satelliteDish.getStackForm(1),
            GTModHandler.RecipeBits.BUFFERED,
            new Object[] { "ECE", "SHS", "TCT", 'E', ItemList.Emitter_HV, 'C', OrePrefixes.circuit.get(Materials.HV),
                'S', ItemList.Sensor_HV, 'H', ItemList.Hull_HV, 'T', OrePrefixes.cableGt01.get(Materials.Gold) });

        // ME Bridge: an EV hull with an AE2 Wireless Access Point, an emitter and a sensor, its circuits, two cables
        // to the BeCom side and ME glass cable to the AE2 side.
        GTModHandler.addCraftingRecipe(
            BeComMachines.meBridge.getStackForm(1),
            GTModHandler.RecipeBits.BUFFERED,
            new Object[] { "EAS", "CHC", "TGT", 'E', ItemList.Emitter_EV, 'A', AEApi.instance()
                .definitions()
                .blocks()
                .wireless()
                .maybeStack(1)
                .get(), 'S', ItemList.Sensor_EV, 'C', OrePrefixes.circuit.get(Materials.EV), 'H', ItemList.Hull_EV, 'T',
                OrePrefixes.cableGt01.get(Materials.Aluminium), 'G', AEApi.instance()
                    .definitions()
                    .parts()
                    .cableGlass()
                    .stack(AEColor.Transparent, 1) });
    }

    private static final ItemList[] HULLS = { null, ItemList.Hull_LV, ItemList.Hull_MV, ItemList.Hull_HV };
    private static final ItemList[] EMITTERS = { null, ItemList.Emitter_LV, ItemList.Emitter_MV, ItemList.Emitter_HV };
    private static final ItemList[] SENSORS = { null, ItemList.Sensor_LV, ItemList.Sensor_MV, ItemList.Sensor_HV };

    private static void antenna(gregtech.api.metatileentity.MetaTileEntity head, int tier, Materials circuit,
        Materials cable, Materials rod) {
        GTModHandler.addCraftingRecipe(
            head.getStackForm(1),
            GTModHandler.RecipeBits.BUFFERED,
            new Object[] { "RER", "CHC", "TST", 'R', OrePrefixes.stickLong.get(rod), 'E', EMITTERS[tier], 'C',
                OrePrefixes.circuit.get(circuit), 'H', HULLS[tier], 'T', OrePrefixes.cableGt01.get(cable), 'S',
                SENSORS[tier] });
    }
}
