package com.enn3developer.becom.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.enn3developer.becom.block.BlockPhoneCable;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

/**
 * The cables: a core through the middle of the block in its tier's colour, with an arm out to each open side.
 */
public final class PhoneCableRenderer implements ISimpleBlockRenderingHandler {

    private final int id;

    public PhoneCableRenderer(int id) {
        this.id = id;
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        float min = 0.5f - BlockPhoneCable.RADIUS, max = 0.5f + BlockPhoneCable.RADIUS;
        renderer.setRenderBounds(0, min, min, 1, max, max);
        Tessellator tess = Tessellator.instance;
        GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
        tess.startDrawingQuads();
        tess.setNormal(0, -1, 0);
        renderer.renderFaceYNeg(block, 0, 0, 0, block.getIcon(0, metadata));
        tess.setNormal(0, 1, 0);
        renderer.renderFaceYPos(block, 0, 0, 0, block.getIcon(1, metadata));
        tess.setNormal(0, 0, -1);
        renderer.renderFaceZNeg(block, 0, 0, 0, block.getIcon(2, metadata));
        tess.setNormal(0, 0, 1);
        renderer.renderFaceZPos(block, 0, 0, 0, block.getIcon(3, metadata));
        tess.setNormal(-1, 0, 0);
        renderer.renderFaceXNeg(block, 0, 0, 0, block.getIcon(4, metadata));
        tess.setNormal(1, 0, 0);
        renderer.renderFaceXPos(block, 0, 0, 0, block.getIcon(5, metadata));
        tess.draw();
        GL11.glTranslatef(0.5f, 0.5f, 0.5f);
        renderer.setRenderBounds(0, 0, 0, 1, 1, 1);
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        renderer.renderAllFaces = true;
        box(renderer, block, BlockPhoneCable.core(), x, y, z);
        int mask = BlockPhoneCable.maskAt(world, x, y, z);
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if ((mask & side.flag) != 0) box(renderer, block, BlockPhoneCable.arm(side), x, y, z);
        }
        renderer.renderAllFaces = false;
        renderer.setRenderBounds(0, 0, 0, 1, 1, 1);
        return true;
    }

    /** Draws one box of cord, {x1, y1, z1, x2, y2, z2} in blocks; the Telephone draws its stub with it too. */
    static void box(RenderBlocks renderer, Block block, double[] b, int x, int y, int z) {
        renderer.setRenderBounds(b[0], b[1], b[2], b[3], b[4], b[5]);
        renderer.renderStandardBlock(block, x, y, z);
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
