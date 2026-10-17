package com.enn3developer.becom.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.machine.MTELineTerminal;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The cables, one block with a tier each by metadata: Phone Cable, Coaxial and Fibre. They connect
 * devices to a Line Terminal, losslessly, each over its own reach and at its own rate, and are what pylon spans are
 * strung with. Cable joins cable of any tier, Telephones, Videophones and Line Terminals on any side. It's a thin cord
 * lying on the floor, climbing straight up the middle of its block where it joins something above; nothing bumps into
 * it.
 */
public class BlockPhoneCable extends Block {

    /** Half the cable's thickness in its item's picture, from the centre. */
    public static final float RADIUS = 2 / 16f;
    // The cord: this far either side of the block's middle, this high off the floor.
    private static final double CORD = 1 / 16.0;
    private static final double CORD_HEIGHT = 2 / 16.0;
    // What a player aims at: a little wider and taller than the cord, so it can be hit.
    private static final double AIM = 3 / 16.0;

    public static int renderId = -1;

    /** Each tier's name, for its item and its texture, by ordinal. */
    private static final String[] NAMES = { "phone_cable", "coaxial_cable", "fibre_cable" };

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public BlockPhoneCable() {
        super(Material.circuits);
        setBlockName(BeCom.MODID + ".phone_cable");
        setBlockTextureName(BeCom.MODID + ":phone_cable");
        setHardness(0.5f);
        setStepSound(soundTypeCloth);
    }

    /** The tier of cable a block's metadata stands for; Phone Cable for any this BeCom doesn't know. */
    public static CableTier tierOf(int meta) {
        CableTier tier = CableTier.byOrdinal(meta);
        return tier == null ? CableTier.PHONE : tier;
    }

    /** The name of a tier's item and texture: phone_cable, coaxial_cable, fibre_cable. */
    public static String nameOf(CableTier tier) {
        return NAMES[tier.ordinal()];
    }

    /** An item stack of {@code count} cables of a tier. */
    public static ItemStack stack(CableTier tier, int count) {
        return new ItemStack(BeComBlocks.phoneCable, count, tier.ordinal());
    }

    /** The tier of a stack of cable, or null when it isn't cable. */
    public static CableTier tierOf(ItemStack stack) {
        if (stack == null || !(Block.getBlockFromItem(stack.getItem()) instanceof BlockPhoneCable)) return null;
        return CableTier.byOrdinal(stack.getItemDamage());
    }

    @Override
    public int damageDropped(int meta) {
        return tierOf(meta).ordinal();
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (CableTier tier : CableTier.values()) list.add(new ItemStack(item, 1, tier.ordinal()));
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

    /** The cord's knot in the middle of the floor, {x1, y1, z1, x2, y2, z2} in blocks. */
    public static double[] core() {
        return new double[] { 0.5 - CORD, 0, 0.5 - CORD, 0.5 + CORD, CORD_HEIGHT, 0.5 + CORD };
    }

    /**
     * The cord from the middle of the floor to {@code side}: along the floor to a side's edge, or straight up to the
     * top. Down needs none, the knot lying on whatever is below. A Telephone draws the same piece toward its cable.
     */
    public static double[] arm(ForgeDirection side) {
        if (side == ForgeDirection.DOWN) return null;
        if (side == ForgeDirection.UP) return new double[] { 0.5 - CORD, 0, 0.5 - CORD, 0.5 + CORD, 1, 0.5 + CORD };
        double[] box = core();
        if (side.offsetX < 0) box[0] = 0;
        if (side.offsetX > 0) box[3] = 1;
        if (side.offsetZ < 0) box[2] = 0;
        if (side.offsetZ > 0) box[5] = 1;
        return box;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        double[] aim = { 0.5 - AIM, 0, 0.5 - AIM, 0.5 + AIM, AIM, 0.5 + AIM };
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            double[] arm = connects(world, x, y, z, side) ? arm(side) : null;
            if (arm == null) continue;
            for (int i = 0; i < 3; i++) aim[i] = Math.min(aim[i], arm[i]);
            for (int i = 3; i < 6; i++) aim[i] = Math.max(aim[i], arm[i]);
        }
        setBlockBounds((float) aim[0], (float) aim[1], (float) aim[2], (float) aim[3], (float) aim[4], (float) aim[5]);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null;
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBlockBounds(0, 0.5f - RADIUS, 0.5f - RADIUS, 1, 0.5f + RADIUS, 0.5f + RADIUS);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        icons = new IIcon[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) icons[i] = register.registerIcon(BeCom.MODID + ":" + NAMES[i]);
        blockIcon = icons[0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return icons[tierOf(meta).ordinal()];
    }
}
