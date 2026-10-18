package com.enn3developer.becom.machine;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import gregtech.api.GregTechAPI;
import gregtech.api.casing.Casings;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.common.blocks.BlockFrameBox;

/**
 * The stock GT blocks BeCom's tiered structures are built of, by voltage tier, 1 LV to 3 HV: LV, MV and HV Machine
 * Casings, and steel, aluminium and stainless steel frame boxes.
 */
final class Tiers {

    static final int LOWEST = 1;
    static final int HIGHEST = 3;

    private Tiers() {}

    /** A tier's frame boxes: steel, aluminium, stainless steel. */
    static Materials frame(int tier) {
        return switch (tier) {
            case 1 -> Materials.Steel;
            case 2 -> Materials.Aluminium;
            default -> Materials.StainlessSteel;
        };
    }

    /** A tier's machine casings: LV, MV, HV. */
    static Casings casing(int tier) {
        return switch (tier) {
            case 1 -> Casings.LVMachineCasing;
            case 2 -> Casings.MVMachineCasing;
            default -> Casings.HVMachineCasing;
        };
    }

    /** The tier of a machine casing in the world, or null for any other block. */
    static Integer ofCasing(Block block, int meta) {
        return block == GregTechAPI.sBlockCasings1 && meta >= LOWEST && meta <= HIGHEST ? meta : null;
    }

    /** The tier of a frame box in the world, or null for any other block. */
    static Integer ofFrame(Block block, int meta) {
        if (!(block instanceof BlockFrameBox)) return null;
        Materials material = BlockFrameBox.getMaterial(meta);
        for (int tier = LOWEST; tier <= HIGHEST; tier++) if (frame(tier) == material) return tier;
        return null;
    }

    /** A tier's frame box, as an item. */
    static ItemStack frameStack(int tier) {
        return GTOreDictUnificator.get(OrePrefixes.frameGt, frame(tier), 1);
    }
}
