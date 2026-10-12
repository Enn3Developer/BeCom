package com.enn3developer.becom.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.enn3developer.becom.block.BlockPhoneCable;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

/** Phone Cable (DESIGN.md 15.1): a thin core with an arm toward each neighbour it joins. */
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
        renderer.renderFaceYNeg(block, 0, 0, 0, block.getIcon(0, 0));
        tess.setNormal(0, 1, 0);
        renderer.renderFaceYPos(block, 0, 0, 0, block.getIcon(1, 0));
        tess.setNormal(0, 0, -1);
        renderer.renderFaceZNeg(block, 0, 0, 0, block.getIcon(2, 0));
        tess.setNormal(0, 0, 1);
        renderer.renderFaceZPos(block, 0, 0, 0, block.getIcon(3, 0));
        tess.setNormal(-1, 0, 0);
        renderer.renderFaceXNeg(block, 0, 0, 0, block.getIcon(4, 0));
        tess.setNormal(1, 0, 0);
        renderer.renderFaceXPos(block, 0, 0, 0, block.getIcon(5, 0));
        tess.draw();
        GL11.glTranslatef(0.5f, 0.5f, 0.5f);
        renderer.setRenderBounds(0, 0, 0, 1, 1, 1);
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        float min = 0.5f - BlockPhoneCable.RADIUS, max = 0.5f + BlockPhoneCable.RADIUS;
        renderer.renderAllFaces = true;
        renderer.setRenderBounds(min, min, min, max, max, max);
        renderer.renderStandardBlock(block, x, y, z);
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (!BlockPhoneCable.connects(world, x, y, z, side)) continue;
            double x1 = side.offsetX < 0 ? 0 : side.offsetX > 0 ? max : min;
            double x2 = side.offsetX < 0 ? min : side.offsetX > 0 ? 1 : max;
            double y1 = side.offsetY < 0 ? 0 : side.offsetY > 0 ? max : min;
            double y2 = side.offsetY < 0 ? min : side.offsetY > 0 ? 1 : max;
            double z1 = side.offsetZ < 0 ? 0 : side.offsetZ > 0 ? max : min;
            double z2 = side.offsetZ < 0 ? min : side.offsetZ > 0 ? 1 : max;
            renderer.setRenderBounds(x1, y1, z1, x2, y2, z2);
            renderer.renderStandardBlock(block, x, y, z);
        }
        renderer.renderAllFaces = false;
        renderer.setRenderBounds(0, 0, 0, 1, 1, 1);
        return true;
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
