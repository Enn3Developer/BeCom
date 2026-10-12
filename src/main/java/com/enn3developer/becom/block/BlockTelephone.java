package com.enn3developer.becom.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.server.BeComServer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Telephone (DESIGN.md 5.6): right-click picks up the handset, answers a ringing phone, or hangs up. Sneak +
 * right-click opens its phone screen without touching the handset. Metadata: the low two bits are the facing, bit 2
 * is set while the handset is off the hook.
 */
public class BlockTelephone extends BlockContainer {

    private static final int FACING_MASK = 0b011;
    private static final int OFF_HOOK_BIT = 0b100;

    public static int renderId = -1;

    @SideOnly(Side.CLIENT)
    private IIcon body;
    @SideOnly(Side.CLIENT)
    private IIcon handset;
    @SideOnly(Side.CLIENT)
    private IIcon dial;

    public BlockTelephone() {
        super(Material.iron);
        setBlockName(BeCom.MODID + ".telephone");
        setBlockTextureName(BeCom.MODID + ":telephone_body");
        setHardness(1.5f);
        setResistance(5.0f);
        setStepSound(soundTypeMetal);
        setBlockBounds(2 / 16f, 0, 3 / 16f, 14 / 16f, 7 / 16f, 13 / 16f);
    }

    public static boolean isOffHook(int meta) {
        return (meta & OFF_HOOK_BIT) != 0;
    }

    public static int withOffHook(int meta, boolean offHook) {
        return offHook ? meta | OFF_HOOK_BIT : meta & ~OFF_HOOK_BIT;
    }

    /** 0 south, 1 west, 2 north, 3 east: where the phone's front faces. */
    public static int facing(int meta) {
        return meta & FACING_MASK;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileTelephone();
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
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        // Face the player who placed it.
        int facing = (MathHelper.floor_double(placer.rotationYaw * 4.0f / 360.0f + 0.5) + 2) & FACING_MASK;
        world.setBlockMetadataWithNotify(x, y, z, facing, 2);
        if (world.isRemote) return;
        BeComServer becom = BeComServer.get();
        if (becom == null || !(world.getTileEntity(x, y, z) instanceof TileTelephone tile)) return;
        PhoneRecord record = becom.phones.registerTelephone(
            world,
            BlockPos.pack(x, y, z),
            placer instanceof EntityPlayer player && !(placer instanceof FakePlayer) ? player : null);
        tile.setNumber(record.number());
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        BeComServer becom = BeComServer.get();
        if (becom == null || !(player instanceof EntityPlayerMP mp)
            || !(world.getTileEntity(x, y, z) instanceof TileTelephone tile)) return true;
        ensureNumber(becom, world, tile);
        claimIfOwnerless(becom, tile, player);
        if (player.isSneaking()) {
            GuiFactories.tileEntity()
                .open(player, tile);
            return true;
        }
        java.util.UUID holder = becom.calls.holderOf(tile.number());
        if (holder == null) {
            if (becom.calls.handsetOf(player.getUniqueID()) != null) {
                player.addChatComponentMessage(new ChatComponentTranslation("becom.phone.holding_other"));
            } else if (becom.calls.pickUp(mp, tile)) {
                GuiFactories.tileEntity()
                    .open(player, tile);
            }
        } else if (holder.equals(player.getUniqueID())) {
            becom.calls.hangUpHandset(holder);
        } else {
            player.addChatComponentMessage(new ChatComponentTranslation("becom.phone.in_use"));
        }
        return true;
    }

    /**
     * A Telephone whose number the registry lost (copied with a world editor, or placed by a machine) gets a fresh
     * one, so no two phones share a number.
     */
    static void ensureNumber(BeComServer becom, World world, TileTelephone tile) {
        long pos = BlockPos.pack(tile.xCoord, tile.yCoord, tile.zCoord);
        PhoneRecord record = tile.number() < 0 ? null : becom.phones.record(tile.number());
        if (record != null && record.dimension() == world.provider.dimensionId && record.pos() == pos) return;
        tile.setNumber(
            becom.phones.registerTelephone(world, pos, null)
                .number());
    }

    /**
     * A phone's owner is its first user (DESIGN.md 12.1): a Telephone that has none, because a machine placed it or its
     * number had to be renewed, goes to the first real player who uses it. Fake players never claim one.
     */
    static void claimIfOwnerless(BeComServer becom, TileTelephone tile, EntityPlayer player) {
        PhoneRecord record = becom.phones.record(tile.number());
        if (record == null || record.owner() != null || player instanceof FakePlayer) return;
        record.setOwner(player.getUniqueID(), player.getCommandSenderName());
        becom.phones.registry()
            .changed();
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote && world.getTileEntity(x, y, z) instanceof TileTelephone tile && tile.number() >= 0) {
            BeComServer becom = BeComServer.get();
            if (becom != null) {
                PhoneRecord record = becom.phones.record(tile.number());
                becom.calls.phoneGone(tile.number());
                // Only the phone that owns the number frees it; a copy with a stale number must not.
                if (record != null && record.dimension() == world.provider.dimensionId
                    && record.pos() == BlockPos.pack(x, y, z)) becom.phones.unregister(tile.number());
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        body = register.registerIcon(BeCom.MODID + ":telephone_body");
        handset = register.registerIcon(BeCom.MODID + ":telephone_handset");
        dial = register.registerIcon(BeCom.MODID + ":telephone_dial");
        blockIcon = body;
    }

    @SideOnly(Side.CLIENT)
    public IIcon bodyIcon() {
        return body;
    }

    @SideOnly(Side.CLIENT)
    public IIcon handsetIcon() {
        return handset;
    }

    @SideOnly(Side.CLIENT)
    public IIcon dialIcon() {
        return dial;
    }
}
