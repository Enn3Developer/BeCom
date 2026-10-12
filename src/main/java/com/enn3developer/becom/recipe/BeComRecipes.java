package com.enn3developer.becom.recipe;

import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.enn3developer.becom.block.BeComBlocks;
import com.enn3developer.becom.item.BeComItems;
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
 * BeCom's recipes, from DESIGN.md 3.1 and GT's conventions (D31): controllers are shaped crafting around a machine
 * hull, devices and parts come from the assembler.
 */
public final class BeComRecipes {

    // Recipe values come straight from DESIGN.md 3.1's table.
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
    private static final int SPACE_EUT = 480;
    private static final int SATELLITE_SECONDS = 40;
    private static final int SATELLITE_ROCKET_SECONDS = 60;
    private static final int RECEIVER_SECONDS = 30;

    private BeComRecipes() {}

    public static void register() {
        ItemStack cable = new ItemStack(BeComBlocks.phoneCable);

        // Phone Cable ×2 (D8): red alloy wire in rubber. GT's own Red Alloy Cable takes the same wire with circuit 24
        // and more rubber; without a circuit of its own, a machine that last made Phone Cable would keep making it
        // from GT's inputs (D31: GT keeps overlapping assembler recipes apart by circuit).
        GTValues.RA.stdBuilder()
            .itemInputs(GTOreDictUnificator.get(OrePrefixes.wireGt01, Materials.RedAlloy, 1))
            .circuit(PHONE_CABLE_CIRCUIT)
            .fluidInputs(Materials.Rubber.getMolten(PHONE_CABLE_RUBBER_L))
            .itemOutputs(new ItemStack(BeComBlocks.phoneCable, 2))
            .duration(PHONE_CABLE_SECONDS * SECONDS)
            .eut(PHONE_CABLE_EUT)
            .addTo(assemblerRecipes);

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

        // Pylon Head: a frame box with the line's steel wires and glass insulators.
        GTModHandler.addCraftingRecipe(
            BeComMachines.pylonHead.getStackForm(1),
            GTModHandler.RecipeBits.BUFFERED,
            new Object[] { "WPW", "GFG", "WPW", 'W', OrePrefixes.wireGt01.get(Materials.Steel), 'P',
                OrePrefixes.plate.get(Materials.Steel), 'G', OrePrefixes.plate.get(Materials.Glass), 'F',
                OrePrefixes.frameGt.get(Materials.Steel) });

        // Line Terminal: an LV hull with the circuit, two cables to the phone and two wires to the line.
        GTModHandler.addCraftingRecipe(
            BeComMachines.lineTerminal.getStackForm(1),
            GTModHandler.RecipeBits.BUFFERED,
            new Object[] { "WCW", "PHP", 'W', OrePrefixes.wireGt01.get(Materials.Steel), 'C',
                OrePrefixes.circuit.get(Materials.LV), 'P', cable.copy(), 'H', ItemList.Hull_LV });

        // Repeater Head: a Pylon Head on an LV hull, powered by tin cable.
        GTModHandler.addCraftingRecipe(
            BeComMachines.repeaterHead.getStackForm(1),
            GTModHandler.RecipeBits.BUFFERED,
            new Object[] { "CYC", "THT", 'C', OrePrefixes.circuit.get(Materials.LV), 'Y',
                BeComMachines.pylonHead.getStackForm(1), 'T', OrePrefixes.cableGt01.get(Materials.Tin), 'H',
                ItemList.Hull_LV });

        // Router Head: as the repeater, with twice the circuits for its fifteen links.
        GTModHandler.addCraftingRecipe(
            BeComMachines.routerHead.getStackForm(1),
            GTModHandler.RecipeBits.BUFFERED,
            new Object[] { "CYC", "THT", "C C", 'C', OrePrefixes.circuit.get(Materials.LV), 'Y',
                BeComMachines.pylonHead.getStackForm(1), 'T', OrePrefixes.cableGt01.get(Materials.Tin), 'H',
                ItemList.Hull_LV });

        // Antenna heads (DESIGN.md 3.1): a hull of the tier with an emitter to transmit and a sensor to receive, the
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
        // and two Phone Cables to its Line Terminal.
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

        // Mobile Phone (DESIGN.md 3.1): an MV emitter and sensor are enough for UHF; the screen, an HV circuit and
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

        // Satellite Rocket (D30): a Satellite on Galacticraft's Tier 1 rocket parts.
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

        // Satellite Launch Pad (DESIGN.md 3.1): an HV hull with pumps for the fuel, a robot arm to raise the rocket,
        // its circuits and gold cables.
        GTModHandler.addCraftingRecipe(
            BeComMachines.launchPad.getStackForm(1),
            GTModHandler.RecipeBits.BUFFERED,
            new Object[] { "PRP", "CHC", "TCT", 'P', ItemList.Electric_Pump_HV, 'R', ItemList.Robot_Arm_HV, 'C',
                OrePrefixes.circuit.get(Materials.HV), 'H', ItemList.Hull_HV, 'T',
                OrePrefixes.cableGt01.get(Materials.Gold) });

        // Satellite Dish (DESIGN.md 3.1): an HV hull with two emitters and two sensors, its circuits and gold cables.
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
