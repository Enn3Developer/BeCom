package com.enn3developer.becom.recipe;

import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.enn3developer.becom.block.BeComBlocks;
import com.enn3developer.becom.machine.BeComMachines;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;

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
    }
}
