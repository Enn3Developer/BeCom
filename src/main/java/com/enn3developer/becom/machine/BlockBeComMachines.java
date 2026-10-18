package com.enn3developer.becom.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.enn3developer.becom.BeCom;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.common.blocks.BlockMachines;
import gregtech.common.blocks.ItemMachines;
import gregtech.common.blocks.MaterialMachines;

/**
 * The block BeCom's machines stand in: GregTech's machine block, registered as BeCom's {@code becom:machine}, so a
 * machine's block and item are BeCom's while it stays a GregTech machine, its ID in GregTech's table. GregTech's own
 * block would host them as well; this one lists and drops only BeCom's.
 */
public final class BlockBeComMachines extends BlockMachines {

    // GregTech's machine block registers itself under its unlocalized name, so this is the registry name too.
    private static final String NAME = "machine";

    public BlockBeComMachines() {
        super(ItemMachines.class, NAME, new MaterialMachines());
    }

    @Override
    public String getUnlocalizedName() {
        return NAME;
    }

    @Override
    public String getLocalizedName() {
        return StatCollector.translateToLocal("tile." + BeCom.MODID + "." + NAME + ".name");
    }

    /** GregTech's machine block registers every machine's icons, BeCom's among them, so there's nothing left to do. */
    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister register) {}

    /** BeCom's machines only, where GregTech's block lists every machine there is. */
    @SideOnly(Side.CLIENT)
    @Override
    public void getSubBlocks(Item item, CreativeTabs tab, List<ItemStack> out) {
        for (int id : BeComMachines.ids()
            .keySet()) out.add(new ItemStack(item, 1, id));
    }

    /** GregTech's tile drops a machine as GregTech's item; here it comes out as BeCom's, with what it carried. */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> drops = super.getDrops(world, x, y, z, meta, fortune);
        Item gregtech = Item.getItemFromBlock(GregTechAPI.sBlockMachines);
        for (int i = 0; i < drops.size(); i++) {
            ItemStack drop = drops.get(i);
            if (drop == null || drop.getItem() != gregtech) continue;
            ItemStack ours = new ItemStack(this, drop.stackSize, drop.getItemDamage());
            ours.setTagCompound(drop.getTagCompound());
            drops.set(i, ours);
        }
        return drops;
    }
}
