package com.enn3developer.becom.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialLogic;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.net.CableTier;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.GTMod;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.SoundResource;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import li.cil.oc.api.internal.Case;
import li.cil.oc.api.internal.Microcontroller;
import li.cil.oc.api.internal.Rack;

/**
 * The cables, one block with a tier each by metadata: Phone Cable, Coaxial and Fibre. Like GT's own wires they run
 * through the middle of their blocks and join side by side, each side open or closed ({@link TileCable}): a cable
 * placed joins what it was placed against, and GT wire cutters open and close its sides. Cable joins cable of its own
 * tier only; it ends at devices, Line Terminals, the Exchange and gateways ({@link CableEnd}). Nothing bumps into it,
 * and water and lava flow around it instead of washing it away.
 */
public class BlockPhoneCable extends BlockContainer {

    /** Half the cable's thickness, from the middle of its block. */
    public static final float RADIUS = 2 / 16f;
    /** Where the cable's core starts and ends across its block. */
    public static final double LO = 0.5 - RADIUS, HI = 0.5 + RADIUS;

    public static int renderId = -1;

    /** Each tier's name, for its item and its texture, by ordinal. */
    private static final String[] NAMES = { "phone_cable", "coaxial_cable", "fibre_cable" };

    /**
     * Cable's material: not solid and letting light through, but liquids treat it as a wall, flowing around it
     * instead of washing it away, as they do GT's own cables.
     */
    private static final Material CABLE = new MaterialLogic(MapColor.airColor) {

        @Override
        public boolean blocksMovement() {
            return true;
        }

        @Override
        public boolean isOpaque() {
            return false;
        }
    };

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public BlockPhoneCable() {
        super(CABLE);
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

    /** A tier's name as a chat argument, translated by the reader. */
    public static ChatComponentTranslation chatName(CableTier tier) {
        return new ChatComponentTranslation("tile.becom." + nameOf(tier) + ".name");
    }

    /** The tier of a stack of cable, or null when it isn't cable. */
    public static CableTier tierOf(ItemStack stack) {
        if (stack == null || !(Block.getBlockFromItem(stack.getItem()) instanceof BlockPhoneCable)) return null;
        return CableTier.byOrdinal(stack.getItemDamage());
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileCable();
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

    /** What a cable finds across one of its sides, for joining there. */
    public enum Join {

        /** Cable of its own tier: they join both ways. */
        CABLE,
        /** A machine or device that takes it. */
        TAKES,
        /** Air: the side may stay open for what is put there. */
        AIR,
        /** An unloaded chunk, or a machine still being set up: left as it is. */
        UNKNOWN,
        /** Cable of another tier. */
        OTHER_TIER,
        /** A machine taking only poorer cable. */
        OVER_TIER,
        /** A machine taking a single cable, which another already joins. */
        TAKEN,
        /** Nothing that takes cable there. */
        NO;

        /** Whether the side may be open toward it. */
        public boolean joins() {
            return this == CABLE || this == TAKES || this == AIR;
        }
    }

    /** What the cable of {@code tier} at x, y, z finds across {@code side}. Never loads a chunk. */
    public static Join joinAt(World world, int x, int y, int z, ForgeDirection side, CableTier tier) {
        int nx = x + side.offsetX, ny = y + side.offsetY, nz = z + side.offsetZ;
        if (ny < 0 || ny >= world.getHeight()) return Join.NO;
        if (!world.blockExists(nx, ny, nz)) return Join.UNKNOWN;
        Block block = world.getBlock(nx, ny, nz);
        if (block instanceof BlockPhoneCable)
            return tierOf(world.getBlockMetadata(nx, ny, nz)) == tier ? Join.CABLE : Join.OTHER_TIER;
        if (block.isAir(world, nx, ny, nz)) return Join.AIR;
        if (block instanceof BlockTelephone) return Join.TAKES;
        TileEntity tile = world.getTileEntity(nx, ny, nz);
        if (tile instanceof IGregTechTileEntity gt) {
            IMetaTileEntity machine = gt.getMetaTileEntity();
            // A machine block GT has yet to fill in, as it is while being placed.
            if (machine == null) return Join.UNKNOWN;
            ForgeDirection face = side.getOpposite();
            if (!(machine instanceof CableEnd end) || !end.takesCableOn(face)) return Join.NO;
            if (tier.compareTo(end.bestCable()) > 0) return Join.OVER_TIER;
            if (end.oneCable() && joinedElsewhere(world, nx, ny, nz, face)) return Join.TAKEN;
            return Join.TAKES;
        }
        // OpenComputers' computers, for a wired BeCom Card.
        if (tile instanceof Case || tile instanceof Rack || tile instanceof Microcontroller) return Join.TAKES;
        return Join.NO;
    }

    /** Whether a cable joins the block at x, y, z on a face other than {@code face}. */
    private static boolean joinedElsewhere(World world, int x, int y, int z, ForgeDirection face) {
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (side == face) continue;
            int cx = x + side.offsetX, cy = y + side.offsetY, cz = z + side.offsetZ;
            if (world.blockExists(cx, cy, cz) && world.getTileEntity(cx, cy, cz) instanceof TileCable cable
                && cable.open(side.getOpposite())) return true;
        }
        return false;
    }

    /** The open sides of the cable at x, y, z; none when there's no cable there. */
    public static int maskAt(IBlockAccess world, int x, int y, int z) {
        return world.getTileEntity(x, y, z) instanceof TileCable cable ? cable.mask() : 0;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        ItemStack held = player.getCurrentEquippedItem();
        if (!GTUtility.isStackInList(held, GregTechAPI.sWireCutterList)) return false;
        if (world.isRemote || !(world.getTileEntity(x, y, z) instanceof TileCable cable)) return true;
        // The side GT's grid picks: the face clicked, or the one beside the edge clicked near.
        ForgeDirection toggled = GTUtility
            .determineWrenchingSide(ForgeDirection.getOrientation(side), hitX, hitY, hitZ);
        if (cable.open(toggled)) {
            if (!GTModHandler.damageOrDechargeItem(held, 1, 500, player)) return true;
            cable.disconnect(toggled);
            GTUtility.sendChatTrans(player, "GT5U.chat.disconnected");
        } else {
            Join join = joinAt(world, x, y, z, toggled, cable.tier());
            if (!join.joins()) {
                refused(player, join, world, x + toggled.offsetX, y + toggled.offsetY, z + toggled.offsetZ, cable);
                return true;
            }
            if (!GTModHandler.damageOrDechargeItem(held, 1, 500, player)) return true;
            cable.connect(toggled);
            GTUtility.sendChatTrans(player, "GT5U.chat.connected");
        }
        GTUtility.sendSoundToPlayers(world, SoundResource.GTCEU_OP_WIRECUTTER, 1.0f, 1, x + 0.5, y + 0.5, z + 0.5);
        return true;
    }

    /** Tells a player why the side they cut toward won't open. */
    private static void refused(EntityPlayer player, Join join, World world, int x, int y, int z, TileCable cable) {
        switch (join) {
            case OTHER_TIER -> GTUtility.sendChatTrans(
                player,
                "becom.cable.join.other_tier",
                chatName(cable.tier()),
                chatName(tierOf(world.getBlockMetadata(x, y, z))));
            case OVER_TIER -> GTUtility.sendChatTrans(
                player,
                "becom.cable.join.over_tier",
                chatName(
                    ((CableEnd) ((IGregTechTileEntity) world.getTileEntity(x, y, z)).getMetaTileEntity()).bestCable()));
            case TAKEN -> GTUtility.sendChatTrans(player, "becom.cable.join.taken");
            case UNKNOWN -> {}
            default -> GTUtility.sendChatTrans(player, "becom.cable.join.nothing");
        }
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbour) {
        if (!world.isRemote && world.getTileEntity(x, y, z) instanceof TileCable cable) cable.check();
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

    /** The cable's core in the middle of its block, {x1, y1, z1, x2, y2, z2} in blocks. */
    public static double[] core() {
        return new double[] { LO, LO, LO, HI, HI, HI };
    }

    /** The cable from its core out to {@code side}'s face, beside the core rather than through it. */
    public static double[] arm(ForgeDirection side) {
        double[] box = core();
        if (side.offsetX != 0) {
            box[0] = side.offsetX < 0 ? 0 : HI;
            box[3] = side.offsetX < 0 ? LO : 1;
        } else if (side.offsetY != 0) {
            box[1] = side.offsetY < 0 ? 0 : HI;
            box[4] = side.offsetY < 0 ? LO : 1;
        } else {
            box[2] = side.offsetZ < 0 ? 0 : HI;
            box[5] = side.offsetZ < 0 ? LO : 1;
        }
        return box;
    }

    /** The box around the core and every open side's arm, as GT's pipes have. */
    public static double[] hull(int mask) {
        double[] box = core();
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if ((mask & side.flag) == 0) continue;
            double[] arm = arm(side);
            for (int i = 0; i < 3; i++) box[i] = Math.min(box[i], arm[i]);
            for (int i = 3; i < 6; i++) box[i] = Math.max(box[i], arm[i]);
        }
        return box;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        // With a GT tool in hand the whole block is the target, so every cell of the wire cutters' grid can be hit.
        if (GTMod.GT.isClientSide() && GTMod.clientProxy()
            .forceFullBlockBB()) {
            setBlockBounds(0, 0, 0, 1, 1, 1);
            return;
        }
        double[] b = hull(maskAt(world, x, y, z));
        setBlockBounds((float) b[0], (float) b[1], (float) b[2], (float) b[3], (float) b[4], (float) b[5]);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null;
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBlockBounds(0, (float) LO, (float) LO, 1, (float) HI, (float) HI);
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
