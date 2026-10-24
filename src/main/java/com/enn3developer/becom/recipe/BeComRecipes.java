package com.enn3developer.becom.recipe;

import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.recipe.RecipeMaps.circuitAssemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.enn3developer.becom.block.BeComBlocks;
import com.enn3developer.becom.block.BlockPhoneCable;
import com.enn3developer.becom.item.BeComItems;
import com.enn3developer.becom.item.ItemBecomCard;
import com.enn3developer.becom.item.ItemPart;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.machine.BeComMachines;

import appeng.api.AEApi;
import appeng.api.definitions.IDefinitions;
import appeng.api.util.AEColor;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.objects.SubstituteFluidStack;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeBuilder;
import micdoodle8.mods.galacticraft.core.items.GCItems;

/**
 * BeCom's recipes, after GT's conventions. Parts come from the Assembler or the Circuit Assembler, a Circuit Assembler
 * part one voltage tier below the part's own; handhelds, hatches and racks from the Assembler. What you place is
 * shaped crafting around a machine hull, and the Assembler makes it too at its tier's voltage, a little cheaper: half
 * the circuits, cables and plates. Every input is what the thing is made of or what gates it to its tier. Plastic,
 * rubber and solder take GTNH's substitutes: PE, or PTFE at half, or PBI at a sixteenth; Rubber, or SBR at three
 * quarters, or Silicone Rubber at half; GT's soldering alloys.
 */
public final class BeComRecipes {

    private static final int LV = 30;
    private static final int MV = 120;
    private static final int HV = 480;
    private static final int EV = 1920;
    private static final int CABLE_SECONDS = 5;
    private static final int PART_SECONDS = 10;
    private static final int CARD_SECONDS = 15;
    private static final int HANDHELD_SECONDS = 20;
    private static final int RACK_SECONDS = 20;
    private static final int MOBILE_SECONDS = 30;
    private static final int RECEIVER_SECONDS = 30;
    private static final int SATELLITE_SECONDS = 40;
    private static final int SATELLITE_ROCKET_SECONDS = 60;
    // A craft of cable gives 4: it's laid by the block over buses of up to 128, 512 and 4096 blocks, so it takes a
    // quarter of a GT cable's insulation.
    private static final int CABLES = 4;
    private static final int PLASTIC_L = 144;
    private static final int SOLDER_L = 72;
    // GT's way out of a collision: without it, Fibre Cable would take GT's Optical Fiber Cable inputs, and the HV
    // RF Module OpenComputers' Wireless Network Card (Tier 2) inputs.
    private static final int OWN_CIRCUIT = 2;

    private BeComRecipes() {}

    private static SubstituteFluidStack plastic(int litres) {
        return new SubstituteFluidStack(
            Materials.Polyethylene.getMolten(litres),
            Materials.Polytetrafluoroethylene.getMolten(litres / 2),
            Materials.Polybenzimidazole.getMolten(litres / 16));
    }

    private static SubstituteFluidStack rubber(int litres) {
        return new SubstituteFluidStack(
            Materials.Rubber.getMolten(litres),
            Materials.StyreneButadieneRubber.getMolten(litres * 3 / 4),
            Materials.RubberSilicone.getMolten(litres / 2));
    }

    private static SubstituteFluidStack solder() {
        return SubstituteFluidStack.soldering(SOLDER_L);
    }

    private static ItemStack get(OrePrefixes prefix, Materials material, int count) {
        return GTOreDictUnificator.get(prefix, material, count);
    }

    private static ItemStack part(int kind) {
        return ItemPart.stack(kind, 1);
    }

    private static ItemStack oc(String name, int count) {
        return li.cil.oc.api.Items.get(name)
            .createItemStack(count);
    }

    private static IDefinitions ae() {
        return AEApi.instance()
            .definitions();
    }

    private static ItemStack fluixCable(int count) {
        return ae().parts()
            .cableGlass()
            .stack(AEColor.Transparent, count);
    }

    /** An Assembler recipe of PART_SECONDS, for the twin of a grid craft. */
    private static void assembler(ItemStack output, int eut, ItemStack... inputs) {
        GTValues.RA.stdBuilder()
            .itemInputs(inputs)
            .itemOutputs(output)
            .duration(PART_SECONDS * SECONDS)
            .eut(eut)
            .addTo(assemblerRecipes);
    }

    private static void shaped(ItemStack output, Object... recipe) {
        GTModHandler.addCraftingRecipe(output, GTModHandler.RecipeBits.BUFFERED, recipe);
    }

    public static void register() {
        parts();
        cables();
        phones();
        wired();
        radio();
        space();
        computersAndMe();
    }

    private static void cables() {
        // Phone Cable: a twisted pair of fine copper wire in rubber.
        GTValues.RA.stdBuilder()
            .itemInputs(get(OrePrefixes.wireFine, Materials.Copper, 8))
            .fluidInputs(rubber(PLASTIC_L))
            .itemOutputs(BlockPhoneCable.stack(CableTier.PHONE, CABLES))
            .duration(CABLE_SECONDS * SECONDS)
            .eut(LV)
            .addTo(assemblerRecipes);
        // Coaxial: an annealed copper core, an aluminium foil shield, polyethylene between and around.
        GTValues.RA.stdBuilder()
            .itemInputs(
                get(OrePrefixes.wireFine, Materials.AnnealedCopper, 4),
                get(OrePrefixes.foil, Materials.Aluminium, 1))
            .fluidInputs(plastic(PLASTIC_L))
            .itemOutputs(BlockPhoneCable.stack(CableTier.COAXIAL, CABLES))
            .duration(CABLE_SECONDS * SECONDS)
            .eut(MV)
            .addTo(assemblerRecipes);
        // Fibre: glass fibre in a plastic jacket.
        GTValues.RA.stdBuilder()
            .itemInputs(ItemList.Circuit_Parts_GlassFiber.get(4))
            .circuit(OWN_CIRCUIT)
            .fluidInputs(plastic(PLASTIC_L))
            .itemOutputs(BlockPhoneCable.stack(CableTier.FIBRE, CABLES))
            .duration(CABLE_SECONDS * SECONDS)
            .eut(HV)
            .addTo(assemblerRecipes);
    }

    private static void parts() {
        // Loudspeaker and Microphone: a magnet, a coil and a diaphragm.
        GTValues.RA.stdBuilder()
            .itemInputs(
                get(OrePrefixes.stick, Materials.SteelMagnetic, 1),
                get(OrePrefixes.wireFine, Materials.Copper, 4),
                get(OrePrefixes.foil, Materials.Steel, 2))
            .itemOutputs(part(ItemPart.LOUDSPEAKER))
            .duration(PART_SECONDS * SECONDS)
            .eut(LV)
            .addTo(assemblerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                get(OrePrefixes.stick, Materials.SteelMagnetic, 1),
                get(OrePrefixes.wireFine, Materials.Copper, 2),
                get(OrePrefixes.foil, Materials.Tin, 1))
            .itemOutputs(part(ItemPart.MICROPHONE))
            .duration(PART_SECONDS * SECONDS)
            .eut(LV)
            .addTo(assemblerRecipes);
        // Handset: both, with a cord, in a plastic shell.
        GTValues.RA.stdBuilder()
            .itemInputs(
                part(ItemPart.LOUDSPEAKER),
                part(ItemPart.MICROPHONE),
                BlockPhoneCable.stack(CableTier.PHONE, 1))
            .fluidInputs(plastic(PLASTIC_L))
            .itemOutputs(part(ItemPart.HANDSET))
            .duration(PART_SECONDS * SECONDS)
            .eut(LV)
            .addTo(assemblerRecipes);

        // RF Modules: a tier's emitter and sensor on its board. LV in the Assembler, the rest in the Circuit Assembler
        // a voltage tier below.
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Circuit_Board_Coated_Basic.get(1),
                ItemList.Emitter_LV.get(1),
                ItemList.Sensor_LV.get(1))
            .fluidInputs(solder())
            .itemOutputs(part(ItemPart.RF_MODULE_LV))
            .duration(PART_SECONDS * SECONDS)
            .eut(LV)
            .addTo(assemblerRecipes);
        rfModule(ItemPart.RF_MODULE_MV, ItemList.Circuit_Board_Plastic, ItemList.Emitter_MV, ItemList.Sensor_MV, LV, 0);
        rfModule(
            ItemPart.RF_MODULE_HV,
            ItemList.Circuit_Board_Epoxy_Advanced,
            ItemList.Emitter_HV,
            ItemList.Sensor_HV,
            MV,
            OWN_CIRCUIT);
        rfModule(
            ItemPart.RF_MODULE_EV,
            ItemList.Circuit_Board_Fiberglass_Advanced,
            ItemList.Emitter_EV,
            ItemList.Sensor_EV,
            HV,
            0);

        // Switching: relays at LV, logic at MV, packets at HV.
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Circuit_Board_Coated_Basic.get(1),
                get(OrePrefixes.springSmall, Materials.Steel, 4),
                get(OrePrefixes.wireGt01, Materials.RedAlloy, 4),
                get(OrePrefixes.circuit, Materials.LV, 1))
            .itemOutputs(part(ItemPart.CROSSBAR_SWITCH))
            .duration(PART_SECONDS * SECONDS)
            .eut(LV)
            .addTo(assemblerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Circuit_Board_Plastic.get(1),
                ItemList.Circuit_Chip_ILC.get(2),
                get(OrePrefixes.circuit, Materials.MV, 1))
            .fluidInputs(solder())
            .itemOutputs(part(ItemPart.SWITCHING_BOARD))
            .duration(PART_SECONDS * SECONDS)
            .eut(LV)
            .addTo(circuitAssemblerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Circuit_Board_Epoxy_Advanced.get(1),
                ItemList.Circuit_Chip_Simple_SoC.get(1),
                ItemList.Circuit_Chip_Ram.get(2),
                get(OrePrefixes.circuit, Materials.HV, 1))
            .fluidInputs(solder())
            .itemOutputs(part(ItemPart.PACKET_SWITCH))
            .duration(PART_SECONDS * SECONDS)
            .eut(MV)
            .addTo(circuitAssemblerRecipes);
    }

    /** circuit: a Programmed Circuit's configuration, for the one module that needs its own; 0 for none. */
    private static void rfModule(int kind, ItemList board, ItemList emitter, ItemList sensor, int eut, int circuit) {
        GTRecipeBuilder recipe = GTValues.RA.stdBuilder()
            .itemInputs(board.get(1), emitter.get(1), sensor.get(1));
        if (circuit > 0) recipe.circuit(circuit);
        recipe.fluidInputs(solder())
            .itemOutputs(part(kind))
            .duration(PART_SECONDS * SECONDS)
            .eut(eut)
            .addTo(circuitAssemblerRecipes);
    }

    private static void phones() {
        // Telephone: the handset, a note block for the bell, the circuit, a steel body and the cord.
        ItemStack telephone = new ItemStack(BeComBlocks.telephone);
        shaped(
            telephone,
            "PHP",
            "NCW",
            'P',
            OrePrefixes.plate.get(Materials.Steel),
            'H',
            part(ItemPart.HANDSET),
            'N',
            new ItemStack(Blocks.noteblock),
            'C',
            OrePrefixes.circuit.get(Materials.LV),
            'W',
            BlockPhoneCable.stack(CableTier.PHONE, 1));
        assembler(
            telephone,
            LV,
            part(ItemPart.HANDSET),
            new ItemStack(Blocks.noteblock),
            get(OrePrefixes.circuit, Materials.LV, 1),
            get(OrePrefixes.plate, Materials.Steel, 1),
            BlockPhoneCable.stack(CableTier.PHONE, 1));

        // Videophone: an MV hull with a screen, the handset, a sensor for the camera, its circuits and two Phone
        // Cables to its Router.
        ItemStack videophone = BeComMachines.videophone.getStackForm(1);
        shaped(
            videophone,
            "DHS",
            "CMC",
            "W W",
            'D',
            ItemList.Cover_Screen,
            'H',
            part(ItemPart.HANDSET),
            'S',
            ItemList.Sensor_MV,
            'C',
            OrePrefixes.circuit.get(Materials.MV),
            'M',
            ItemList.Hull_MV,
            'W',
            BlockPhoneCable.stack(CableTier.PHONE, 1));
        assembler(
            videophone,
            MV,
            ItemList.Cover_Screen.get(1),
            part(ItemPart.HANDSET),
            ItemList.Sensor_MV.get(1),
            get(OrePrefixes.circuit, Materials.MV, 1),
            ItemList.Hull_MV.get(1),
            BlockPhoneCable.stack(CableTier.PHONE, 1));

        // Walkie-Talkie: the MV RF Module, a loudspeaker and a microphone, GT's MV battery, an aluminium rod for the
        // antenna, in a plastic case.
        GTValues.RA.stdBuilder()
            .itemInputs(
                part(ItemPart.RF_MODULE_MV),
                part(ItemPart.LOUDSPEAKER),
                part(ItemPart.MICROPHONE),
                ItemList.Battery_RE_MV_Lithium.get(1),
                get(OrePrefixes.stick, Materials.Aluminium, 1))
            .fluidInputs(plastic(PLASTIC_L))
            .itemOutputs(new ItemStack(BeComItems.walkieTalkie))
            .duration(HANDHELD_SECONDS * SECONDS)
            .eut(MV)
            .addTo(assemblerRecipes);

        // Mobile Phone: the HV RF Module, GT's screen, a loudspeaker and a microphone, an HV circuit and battery, in a
        // plastic case.
        GTValues.RA.stdBuilder()
            .itemInputs(
                part(ItemPart.RF_MODULE_HV),
                ItemList.Cover_Screen.get(1),
                part(ItemPart.LOUDSPEAKER),
                part(ItemPart.MICROPHONE),
                get(OrePrefixes.circuit, Materials.HV, 1),
                ItemList.Battery_RE_HV_Lithium.get(1))
            .fluidInputs(plastic(PLASTIC_L))
            .itemOutputs(new ItemStack(BeComItems.mobilePhone))
            .duration(MOBILE_SECONDS * SECONDS)
            .eut(HV)
            .addTo(assemblerRecipes);

        // ME Phone: a Mobile Phone with an ME Terminal and AE2's Wireless Receiver, the EV RF Module and an EV
        // circuit. Not AE2's Wireless Terminal: the phone has its battery, the terminal's Dense Energy Cell would cost
        // fifteen HV circuits.
        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(BeComItems.mobilePhone),
                ae().parts()
                    .terminal()
                    .maybeStack(1)
                    .get(),
                ae().materials()
                    .wireless()
                    .maybeStack(1)
                    .get(),
                part(ItemPart.RF_MODULE_EV),
                get(OrePrefixes.circuit, Materials.EV, 1))
            .itemOutputs(new ItemStack(BeComItems.mePhone))
            .duration(MOBILE_SECONDS * SECONDS)
            .eut(EV)
            .addTo(assemblerRecipes);

        // Signal Analyzer: it only listens, so a sensor and no emitter, a long rod for the antenna and a screen, in
        // an aluminium case.
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Sensor_MV.get(1),
                get(OrePrefixes.stickLong, Materials.Aluminium, 1),
                ItemList.Cover_Screen.get(1),
                get(OrePrefixes.circuit, Materials.MV, 1),
                get(OrePrefixes.itemCasing, Materials.Aluminium, 2))
            .itemOutputs(new ItemStack(BeComItems.signalAnalyzer))
            .duration(HANDHELD_SECONDS * SECONDS)
            .eut(MV)
            .addTo(assemblerRecipes);
    }

    private static void wired() {
        // Routers: a hull of the tier with its switching part and circuits, two of its cables to the devices and two
        // GT cables for power.
        router(
            BeComMachines.router,
            part(ItemPart.CROSSBAR_SWITCH),
            CableTier.PHONE,
            Materials.LV,
            ItemList.Hull_LV,
            Materials.Tin,
            LV);
        router(
            BeComMachines.routerMV,
            part(ItemPart.SWITCHING_BOARD),
            CableTier.COAXIAL,
            Materials.MV,
            ItemList.Hull_MV,
            Materials.Copper,
            MV);
        router(
            BeComMachines.routerHV,
            part(ItemPart.PACKET_SWITCH),
            CableTier.FIBRE,
            Materials.HV,
            ItemList.Hull_HV,
            Materials.Gold,
            HV);

        // Line Hatches: like GT's hatches, a hull of the tier, its cable and circuit, and plastic growing with the
        // tier.
        lineHatch(BeComMachines.lineHatch, CableTier.PHONE, ItemList.Hull_LV, Materials.LV, PLASTIC_L, LV);
        lineHatch(BeComMachines.lineHatchMV, CableTier.COAXIAL, ItemList.Hull_MV, Materials.MV, 2 * PLASTIC_L, MV);
        lineHatch(BeComMachines.lineHatchHV, CableTier.FIBRE, ItemList.Hull_HV, Materials.HV, 3 * PLASTIC_L, HV);

        // Switching Racks: two of the tier's switching parts on a rack of its plates in a hull of the tier, patched
        // with its cable.
        rack(
            BeComMachines.rack,
            part(ItemPart.CROSSBAR_SWITCH),
            CableTier.PHONE,
            ItemList.Hull_LV,
            Materials.Steel,
            LV);
        rack(
            BeComMachines.rackMV,
            part(ItemPart.SWITCHING_BOARD),
            CableTier.COAXIAL,
            ItemList.Hull_MV,
            Materials.Aluminium,
            MV);
        rack(
            BeComMachines.rackHV,
            part(ItemPart.PACKET_SWITCH),
            CableTier.FIBRE,
            ItemList.Hull_HV,
            Materials.StainlessSteel,
            HV);

        // Exchange: an LV hull among circuits, its crossbar switches and the lines it switches.
        ItemStack exchange = BeComMachines.exchange.getStackForm(1);
        shaped(
            exchange,
            "CXC",
            "WHW",
            "CXC",
            'C',
            OrePrefixes.circuit.get(Materials.LV),
            'X',
            part(ItemPart.CROSSBAR_SWITCH),
            'W',
            BlockPhoneCable.stack(CableTier.PHONE, 1),
            'H',
            ItemList.Hull_LV);
        assembler(
            exchange,
            LV,
            get(OrePrefixes.circuit, Materials.LV, 2),
            ItemPart.stack(ItemPart.CROSSBAR_SWITCH, 2),
            BlockPhoneCable.stack(CableTier.PHONE, 1),
            ItemList.Hull_LV.get(1));
    }

    private static void router(MetaTileEntity machine, ItemStack switching, CableTier tier, Materials circuit,
        ItemList hull, Materials power, int eut) {
        ItemStack out = machine.getStackForm(1);
        shaped(
            out,
            "CSC",
            "WHW",
            "T T",
            'C',
            OrePrefixes.circuit.get(circuit),
            'S',
            switching,
            'W',
            BlockPhoneCable.stack(tier, 1),
            'H',
            hull,
            'T',
            OrePrefixes.cableGt01.get(power));
        assembler(
            out,
            eut,
            switching,
            BlockPhoneCable.stack(tier, 1),
            get(OrePrefixes.circuit, circuit, 1),
            hull.get(1),
            get(OrePrefixes.cableGt01, power, 1));
    }

    private static void lineHatch(MetaTileEntity machine, CableTier tier, ItemList hull, Materials circuit, int plastic,
        int eut) {
        GTValues.RA.stdBuilder()
            .itemInputs(hull.get(1), BlockPhoneCable.stack(tier, 2), get(OrePrefixes.circuit, circuit, 1))
            .fluidInputs(plastic(plastic))
            .itemOutputs(machine.getStackForm(1))
            .duration(PART_SECONDS * SECONDS)
            .eut(eut)
            .addTo(assemblerRecipes);
    }

    private static void rack(MetaTileEntity machine, ItemStack switching, CableTier tier, ItemList hull,
        Materials plate, int eut) {
        ItemStack parts = switching.copy();
        parts.stackSize = 2;
        GTValues.RA.stdBuilder()
            .itemInputs(hull.get(1), parts, get(OrePrefixes.plate, plate, 4), BlockPhoneCable.stack(tier, 4))
            .itemOutputs(machine.getStackForm(1))
            .duration(RACK_SECONDS * SECONDS)
            .eut(eut)
            .addTo(assemblerRecipes);
    }

    private static void radio() {
        // Antenna heads: a hull of the tier with its RF Module, two long rods for the elements on a frame of the same
        // metal, its circuits and GT cables.
        antenna(
            BeComMachines.antennaT1,
            ItemPart.RF_MODULE_LV,
            Materials.LV,
            ItemList.Hull_LV,
            Materials.Tin,
            Materials.Steel,
            LV);
        antenna(
            BeComMachines.antennaT2,
            ItemPart.RF_MODULE_MV,
            Materials.MV,
            ItemList.Hull_MV,
            Materials.Copper,
            Materials.Aluminium,
            MV);
        antenna(
            BeComMachines.antennaT3,
            ItemPart.RF_MODULE_HV,
            Materials.HV,
            ItemList.Hull_HV,
            Materials.Gold,
            Materials.StainlessSteel,
            HV);

        // Transceiver: an MV hull with the MV RF Module between a loudspeaker and a microphone, its circuits and
        // cables.
        ItemStack transceiver = BeComMachines.transceiver.getStackForm(1);
        shaped(
            transceiver,
            "LRM",
            "CHC",
            "T T",
            'L',
            part(ItemPart.LOUDSPEAKER),
            'R',
            part(ItemPart.RF_MODULE_MV),
            'M',
            part(ItemPart.MICROPHONE),
            'C',
            OrePrefixes.circuit.get(Materials.MV),
            'H',
            ItemList.Hull_MV,
            'T',
            OrePrefixes.cableGt01.get(Materials.Copper));
        assembler(
            transceiver,
            MV,
            part(ItemPart.LOUDSPEAKER),
            part(ItemPart.RF_MODULE_MV),
            part(ItemPart.MICROPHONE),
            get(OrePrefixes.circuit, Materials.MV, 1),
            ItemList.Hull_MV.get(1),
            get(OrePrefixes.cableGt01, Materials.Copper, 1));

        // Portal Relays: a hull of the tier with its RF Module and switching part, its circuits and cables, and two
        // of what finds a portal: Ender Pearls, Eyes of Ender, Quantum Eyes.
        relay(
            BeComMachines.portalRelay,
            new ItemStack(Items.ender_pearl),
            ItemPart.RF_MODULE_MV,
            part(ItemPart.SWITCHING_BOARD),
            Materials.MV,
            ItemList.Hull_MV,
            Materials.Copper,
            MV);
        relay(
            BeComMachines.portalRelayHV,
            new ItemStack(Items.ender_eye),
            ItemPart.RF_MODULE_HV,
            part(ItemPart.PACKET_SWITCH),
            Materials.HV,
            ItemList.Hull_HV,
            Materials.Gold,
            HV);
        relay(
            BeComMachines.portalRelayEV,
            ItemList.QuantumEye.get(1),
            ItemPart.RF_MODULE_EV,
            part(ItemPart.PACKET_SWITCH),
            Materials.EV,
            ItemList.Hull_EV,
            Materials.Aluminium,
            EV);
    }

    private static void antenna(MetaTileEntity head, int rf, Materials circuit, ItemList hull, Materials power,
        Materials rod, int eut) {
        ItemStack out = head.getStackForm(1);
        shaped(
            out,
            "RMR",
            "CHC",
            "TFT",
            'R',
            OrePrefixes.stickLong.get(rod),
            'M',
            part(rf),
            'C',
            OrePrefixes.circuit.get(circuit),
            'H',
            hull,
            'T',
            OrePrefixes.cableGt01.get(power),
            'F',
            OrePrefixes.frameGt.get(rod));
        assembler(
            out,
            eut,
            get(OrePrefixes.stickLong, rod, 2),
            part(rf),
            get(OrePrefixes.circuit, circuit, 1),
            hull.get(1),
            get(OrePrefixes.cableGt01, power, 1),
            get(OrePrefixes.frameGt, rod, 1));
    }

    private static void relay(MetaTileEntity machine, ItemStack finder, int rf, ItemStack switching, Materials circuit,
        ItemList hull, Materials power, int eut) {
        ItemStack out = machine.getStackForm(1);
        shaped(
            out,
            "YRY",
            "CHC",
            "TST",
            'Y',
            finder,
            'R',
            part(rf),
            'S',
            switching,
            'C',
            OrePrefixes.circuit.get(circuit),
            'H',
            hull,
            'T',
            OrePrefixes.cableGt01.get(power));
        ItemStack finders = finder.copy();
        finders.stackSize = 2;
        assembler(
            out,
            eut,
            finders,
            part(rf),
            switching,
            get(OrePrefixes.circuit, circuit, 1),
            hull.get(1),
            get(OrePrefixes.cableGt01, power, 1));
    }

    private static void space() {
        // Satellite: the HV RF Module, two HV circuits, two of GT's solar panels for power in orbit, and two long
        // stainless rods for the antenna on a stainless frame.
        GTValues.RA.stdBuilder()
            .itemInputs(
                part(ItemPart.RF_MODULE_HV),
                get(OrePrefixes.circuit, Materials.HV, 2),
                ItemList.Cover_SolarPanel.get(2),
                get(OrePrefixes.stickLong, Materials.StainlessSteel, 2),
                get(OrePrefixes.frameGt, Materials.StainlessSteel, 1))
            .itemOutputs(new ItemStack(BeComItems.satellite))
            .duration(SATELLITE_SECONDS * SECONDS)
            .eut(HV)
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
            .eut(HV)
            .addTo(assemblerRecipes);

        // Rocket Radio Receiver: the HV RF Module, a long rod for the antenna and an HV circuit, in stainless steel to
        // fly in a rocket.
        GTValues.RA.stdBuilder()
            .itemInputs(
                part(ItemPart.RF_MODULE_HV),
                get(OrePrefixes.stickLong, Materials.StainlessSteel, 1),
                get(OrePrefixes.circuit, Materials.HV, 1),
                get(OrePrefixes.plate, Materials.StainlessSteel, 4))
            .itemOutputs(new ItemStack(BeComItems.rocketRadioReceiver))
            .duration(RECEIVER_SECONDS * SECONDS)
            .eut(HV)
            .addTo(assemblerRecipes);

        // Satellite Launch Pad: an HV hull with pumps for the fuel, a robot arm to raise the rocket, the HV RF Module
        // for the launch, its circuits and gold cables.
        ItemStack launchPad = BeComMachines.launchPad.getStackForm(1);
        shaped(
            launchPad,
            "PAP",
            "CHC",
            "TRT",
            'P',
            ItemList.Electric_Pump_HV,
            'A',
            ItemList.Robot_Arm_HV,
            'C',
            OrePrefixes.circuit.get(Materials.HV),
            'H',
            ItemList.Hull_HV,
            'T',
            OrePrefixes.cableGt01.get(Materials.Gold),
            'R',
            part(ItemPart.RF_MODULE_HV));
        assembler(
            launchPad,
            HV,
            ItemList.Electric_Pump_HV.get(2),
            ItemList.Robot_Arm_HV.get(1),
            get(OrePrefixes.circuit, Materials.HV, 1),
            ItemList.Hull_HV.get(1),
            part(ItemPart.RF_MODULE_HV),
            get(OrePrefixes.cableGt01, Materials.Gold, 1));

        // Satellite Dish: an HV hull under a dish of stainless plates, the HV RF Module, its circuits and gold cables.
        ItemStack dish = BeComMachines.satelliteDish.getStackForm(1);
        shaped(
            dish,
            "PPP",
            "CHC",
            "TRT",
            'P',
            OrePrefixes.plate.get(Materials.StainlessSteel),
            'C',
            OrePrefixes.circuit.get(Materials.HV),
            'H',
            ItemList.Hull_HV,
            'T',
            OrePrefixes.cableGt01.get(Materials.Gold),
            'R',
            part(ItemPart.RF_MODULE_HV));
        assembler(
            dish,
            HV,
            get(OrePrefixes.plate, Materials.StainlessSteel, 1),
            part(ItemPart.RF_MODULE_HV),
            get(OrePrefixes.circuit, Materials.HV, 1),
            ItemList.Hull_HV.get(1),
            get(OrePrefixes.cableGt01, Materials.Gold, 1));
    }

    private static void computersAndMe() {
        // App Store Mirror: an HV hull with two of OpenComputers' hard disks for the store, its memory, the Packet
        // Switch that serves it, an HV circuit and two Fibre Cables to its bus.
        ItemStack mirror = BeComMachines.appMirror.getStackForm(1);
        shaped(
            mirror,
            "DSD",
            "RHR",
            "WCW",
            'D',
            oc("hdd1", 1),
            'S',
            part(ItemPart.PACKET_SWITCH),
            'R',
            oc("ram1", 1),
            'H',
            ItemList.Hull_HV,
            'W',
            BlockPhoneCable.stack(CableTier.FIBRE, 1),
            'C',
            OrePrefixes.circuit.get(Materials.HV));
        assembler(
            mirror,
            HV,
            oc("hdd1", 2),
            part(ItemPart.PACKET_SWITCH),
            oc("ram1", 2),
            ItemList.Hull_HV.get(1),
            get(OrePrefixes.circuit, Materials.HV, 1),
            BlockPhoneCable.stack(CableTier.FIBRE, 1));

        // BeCom Cards, like OpenComputers' own: its card base, a microchip, the part and solder. The radio card takes
        // the MV RF Module; the wired one Coaxial Cable and, like OC's Network Card, eight electrum bolts.
        GTValues.RA.stdBuilder()
            .itemInputs(oc("card", 1), part(ItemPart.RF_MODULE_MV), oc("chip1", 1))
            .fluidInputs(solder())
            .itemOutputs(ItemBecomCard.stack(ItemBecomCard.RADIO, 1))
            .duration(CARD_SECONDS * SECONDS)
            .eut(MV)
            .addTo(circuitAssemblerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                oc("card", 1),
                BlockPhoneCable.stack(CableTier.COAXIAL, 2),
                oc("chip1", 1),
                get(OrePrefixes.bolt, Materials.Electrum, 8))
            .fluidInputs(solder())
            .itemOutputs(ItemBecomCard.stack(ItemBecomCard.WIRED, 1))
            .duration(CARD_SECONDS * SECONDS)
            .eut(MV)
            .addTo(circuitAssemblerRecipes);

        // ME: AE2 opens in early EV, so everything ME is EV and made at EV.
        // BeCom Card (ME): the radio card turned to an ME network, with AE2's Wireless Receiver and an Engineering
        // Processor, on titanium foil.
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemBecomCard.stack(ItemBecomCard.RADIO, 1),
                ae().materials()
                    .wireless()
                    .maybeStack(1)
                    .get(),
                ae().materials()
                    .engProcessor()
                    .maybeStack(1)
                    .get(),
                get(OrePrefixes.foil, Materials.Titanium, 4))
            .fluidInputs(solder())
            .itemOutputs(ItemBecomCard.stack(ItemBecomCard.ME, 1))
            .duration(CARD_SECONDS * SECONDS)
            .eut(EV)
            .addTo(circuitAssemblerRecipes);

        // ME Bridge: an EV hull with an AE2 Wireless Access Point, the EV RF Module, its circuits and ME glass cable
        // to the AE2 side.
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_EV.get(1),
                ae().blocks()
                    .wireless()
                    .maybeStack(1)
                    .get(),
                part(ItemPart.RF_MODULE_EV),
                get(OrePrefixes.circuit, Materials.EV, 2),
                fluixCable(2))
            .itemOutputs(BeComMachines.meBridge.getStackForm(1))
            .duration(MOBILE_SECONDS * SECONDS)
            .eut(EV)
            .addTo(assemblerRecipes);

        // ME App Store Mirror: the App Store Mirror with an ME Drive, its circuits and ME glass cable.
        GTValues.RA.stdBuilder()
            .itemInputs(
                BeComMachines.appMirror.getStackForm(1),
                ae().blocks()
                    .drive()
                    .maybeStack(1)
                    .get(),
                get(OrePrefixes.circuit, Materials.EV, 2),
                fluixCable(2))
            .itemOutputs(BeComMachines.meMirror.getStackForm(1))
            .duration(MOBILE_SECONDS * SECONDS)
            .eut(EV)
            .addTo(assemblerRecipes);
    }
}
