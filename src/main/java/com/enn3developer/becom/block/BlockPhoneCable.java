package com.enn3developer.becom.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.machine.MTELineTerminal;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Phone Cable (DESIGN.md 5.5): connects a Telephone to a Line Terminal, losslessly, over a limited length. It joins
 * other cable, Telephones and Line Terminals on any side.
 */
public class BlockPhoneCable extends Block {

    /** Half the cable's thickness, from the block's centre. */
    public static final float RADIUS = 2 / 16f;

    public static int renderId = -1;

    public BlockPhoneCable() {
        super(Material.circuits);
        setBlockName(BeCom.MODID + ".phone_cable");
        setBlockTextureName(BeCom.MODID + ":phone_cable");
        setHardness(0.5f);
        setStepSound(soundTypeCloth);
    }

    /** Whether the cable at x, y, z joins whatever is on {@code side}. */
    public static boolean connects(IBlockAccess world, int x, int y, int z, ForgeDirection side) {
        int nx = x + side.offsetX, ny = y + side.offsetY, nz = z + side.offsetZ;
        Block block = world.getBlock(nx, ny, nz);
        if (block instanceof BlockPhoneCable || block instanceof BlockTelephone) return true;
        TileEntity tile = world.getTileEntity(nx, ny, nz);
        return tile instanceof IGregTechTileEntity gt && gt.getMetaTileEntity() instanceof MTELineTerminal;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        return renderId;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        float min = 0.5f - RADIUS, max = 0.5f + RADIUS;
        setBlockBounds(
            connects(world, x, y, z, ForgeDirection.WEST) ? 0 : min,
            connects(world, x, y, z, ForgeDirection.DOWN) ? 0 : min,
            connects(world, x, y, z, ForgeDirection.NORTH) ? 0 : min,
            connects(world, x, y, z, ForgeDirection.EAST) ? 1 : max,
            connects(world, x, y, z, ForgeDirection.UP) ? 1 : max,
            connects(world, x, y, z, ForgeDirection.SOUTH) ? 1 : max);
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBlockBounds(0, 0.5f - RADIUS, 0.5f - RADIUS, 1, 0.5f + RADIUS, 0.5f + RADIUS);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List list,
        Entity entity) {
        setBlockBoundsBasedOnState(world, x, y, z);
        super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        blockIcon = register.registerIcon(BeCom.MODID + ":phone_cable");
    }
}
