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
 * The Telephone: right-click picks up the handset, answers a ringing phone, or hangs up. Sneak +
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
        if (tile.isTooNew()) {
            player.addChatComponentMessage(new ChatComponentTranslation("becom.phone.too_new"));
            return true;
        }
        ensureNumber(becom, world, tile);
        becom.phones.claimIfOwnerless(tile.number(), player);
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
     * A Telephone whose number the registry lost, or a copy of one (made with a world editor, say), gets a fresh
     * number, so no two phones share one. A Telephone that was moved keeps its number (PhoneService.relocate).
     */
    static void ensureNumber(BeComServer becom, World world, TileTelephone tile) {
        long pos = BlockPos.pack(tile.xCoord, tile.yCoord, tile.zCoord);
        if (becom.phones.relocate(tile)) return;
        tile.setNumber(
            becom.phones.registerTelephone(world, pos, null)
                .number());
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote && world.getTileEntity(x, y, z) instanceof TileTelephone tile && tile.number() >= 0) {
            BeComServer becom = BeComServer.get();
            if (becom != null) {
                PhoneRecord record = becom.phones.record(tile.number());
                // Only the phone that owns the number ends its call and frees it; a copy with a stale number must not.
                if (record != null && record.dimension() == world.provider.dimensionId
                    && record.pos() == BlockPos.pack(x, y, z)) {
                    becom.calls.phoneGone(tile.number());
                    becom.phones.unregister(tile.number());
                }
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
