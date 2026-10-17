package com.enn3developer.becom.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.enn3developer.becom.block.BeComBlocks;
import com.enn3developer.becom.block.BlockPhoneCable;
import com.enn3developer.becom.block.BlockTelephone;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

/**
 * The Telephone's model (DESIGN.md 5.6, 15.1): a desk set built from boxes, with the handset across its cradle while
 * it's on the hook and gone from the model while someone holds it.
 */
public final class TelephoneRenderer implements ISimpleBlockRenderingHandler {

    private final int id;

    public TelephoneRenderer(int id) {
        this.id = id;
    }

    /** Boxes in sixteenths, for a phone facing south: {x1, y1, z1, x2, y2, z2}. */
    private static final float[][] BODY = { { 2, 0, 3, 14, 3, 13 }, { 3, 3, 4, 13, 5, 12 } };
    private static final float[][] DIAL = { { 6, 5, 7, 10, 5.5f, 11 } };
    private static final ForgeDirection[] HORIZONTAL = { ForgeDirection.NORTH, ForgeDirection.SOUTH,
        ForgeDirection.WEST, ForgeDirection.EAST };
    private static final float[][] HANDSET = { { 2.5f, 5, 4.5f, 4.5f, 7, 6.5f }, { 11.5f, 5, 4.5f, 13.5f, 7, 6.5f },
        { 2.5f, 6.5f, 5, 13.5f, 7.5f, 6 } };

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        if (!(block instanceof BlockTelephone phone)) return;
        GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
        draw(renderer, phone, BODY, phone.bodyIcon(), 0, true);
        draw(renderer, phone, DIAL, phone.dialIcon(), 0, true);
        draw(renderer, phone, HANDSET, phone.handsetIcon(), 0, true);
        GL11.glTranslatef(0.5f, 0.5f, 0.5f);
        renderer.setRenderBounds(0, 0, 0, 1, 1, 1);
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        if (!(block instanceof BlockTelephone phone)) return false;
        int meta = world.getBlockMetadata(x, y, z);
        int facing = BlockTelephone.facing(meta);
        renderer.renderAllFaces = true;
        for (float[] box : BODY) world(renderer, phone, box, phone.bodyIcon(), facing, x, y, z);
        for (float[] box : DIAL) world(renderer, phone, box, phone.dialIcon(), facing, x, y, z);
        if (!BlockTelephone.isOffHook(meta)) {
            for (float[] box : HANDSET) world(renderer, phone, box, phone.handsetIcon(), facing, x, y, z);
        }
        renderer.clearOverrideBlockTexture();
        // Its cord: from under the body out to each Phone Cable beside it, the way the cable's own arms run.
        for (ForgeDirection side : HORIZONTAL) {
            if (world.getBlock(x + side.offsetX, y, z + side.offsetZ) instanceof BlockPhoneCable) {
                PhoneCableRenderer.box(renderer, BeComBlocks.phoneCable, BlockPhoneCable.arm(side), x, y, z);
            }
        }
        renderer.renderAllFaces = false;
        renderer.setRenderBounds(0, 0, 0, 1, 1, 1);
        return true;
    }

    private static void world(RenderBlocks renderer, Block block, float[] box, IIcon icon, int facing, int x, int y,
        int z) {
        float[] r = rotate(box, facing);
        renderer.setOverrideBlockTexture(icon);
        renderer.setRenderBounds(r[0] / 16, r[1] / 16, r[2] / 16, r[3] / 16, r[4] / 16, r[5] / 16);
        renderer.renderStandardBlock(block, x, y, z);
    }

    /** Turns a south-facing box about the block's vertical axis, a quarter turn per facing step. */
    private static float[] rotate(float[] b, int facing) {
        float x1 = b[0], z1 = b[2], x2 = b[3], z2 = b[5];
        for (int i = 0; i < facing; i++) {
            float nx1 = 16 - z2, nz1 = x1, nx2 = 16 - z1, nz2 = x2;
            x1 = nx1;
            z1 = nz1;
            x2 = nx2;
            z2 = nz2;
        }
        return new float[] { x1, b[1], z1, x2, b[4], z2 };
    }

    private static void draw(RenderBlocks renderer, Block block, float[][] boxes, IIcon icon, int facing,
        boolean inventory) {
        Tessellator tess = Tessellator.instance;
        for (float[] box : boxes) {
            float[] r = rotate(box, facing);
            renderer.setRenderBounds(r[0] / 16, r[1] / 16, r[2] / 16, r[3] / 16, r[4] / 16, r[5] / 16);
            tess.startDrawingQuads();
            tess.setNormal(0, -1, 0);
            renderer.renderFaceYNeg(block, 0, 0, 0, icon);
            tess.setNormal(0, 1, 0);
            renderer.renderFaceYPos(block, 0, 0, 0, icon);
            tess.setNormal(0, 0, -1);
            renderer.renderFaceZNeg(block, 0, 0, 0, icon);
            tess.setNormal(0, 0, 1);
            renderer.renderFaceZPos(block, 0, 0, 0, icon);
            tess.setNormal(-1, 0, 0);
            renderer.renderFaceXNeg(block, 0, 0, 0, icon);
            tess.setNormal(1, 0, 0);
            renderer.renderFaceXPos(block, 0, 0, 0, icon);
            tess.draw();
        }
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return id;
    }
}
