package com.enn3developer.becom.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import com.enn3developer.becom.block.BlockPhoneCable;

import codechicken.lib.vec.Rotation;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.GregTechAPI;
import gregtech.api.util.GTUtility;
import gregtech.common.config.Client;

/**
 * GT's wire cutter grid on BeCom's cables, which GT draws on its own pipes only: the block's outline, the grid on the
 * face aimed at, and a cross on each cell whose side is open. Drawn the way GT's {@code BlockOverlayRenderer} draws it,
 * in GT's overlay colour and line width, so it reads the same as on any GT wire.
 */
public final class CableGrid {

    // For each face aimed at, which grid cell stands for each side, by ordinal: 0 the middle, 1 to 4 the edges, 5 the
    // corners (the side behind). GT's own table.
    private static final int[][] CELL = { { 0, 5, 3, 1, 2, 4 }, { 5, 0, 1, 3, 2, 4 }, { 1, 3, 0, 5, 2, 4 },
        { 3, 1, 5, 0, 2, 4 }, { 4, 2, 3, 1, 0, 5 }, { 2, 4, 3, 1, 5, 0 }, };

    @SubscribeEvent
    public void onDrawBlockHighlight(DrawBlockHighlightEvent event) {
        MovingObjectPosition target = event.target;
        if (target == null || target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        World world = event.player.worldObj;
        if (!(world.getBlock(target.blockX, target.blockY, target.blockZ) instanceof BlockPhoneCable cable)) return;
        if (!GTUtility.isStackInList(event.currentItem, GregTechAPI.sWireCutterList)) return;
        event.setCanceled(true);
        draw(event, cable, BlockPhoneCable.maskAt(world, target.blockX, target.blockY, target.blockZ));
    }

    private static void draw(DrawBlockHighlightEvent event, BlockPhoneCable cable, int mask) {
        MovingObjectPosition target = event.target;
        EntityPlayer player = event.player;
        double camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
        double camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
        double camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;
        int red = Client.blockoverlay.red, green = Client.blockoverlay.green, blue = Client.blockoverlay.blue,
            alpha = Client.blockoverlay.alpha;

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        // Shaders off while drawing, as GT does.
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(0);
        GL11.glLineWidth(Client.blockoverlay.lineWidth * (Minecraft.getMinecraft().displayHeight / 1080f));

        cable.setBlockBoundsBasedOnState(player.worldObj, target.blockX, target.blockY, target.blockZ);
        AxisAlignedBB box = cable
            .getSelectedBoundingBoxFromPool(player.worldObj, target.blockX, target.blockY, target.blockZ)
            .expand(0.002, 0.002, 0.002)
            .getOffsetBoundingBox(-camX, -camY, -camZ);
        outline(box, red, green, blue, alpha);

        GL11.glTranslated(target.blockX - (int) camX, target.blockY - (int) camY, target.blockZ - (int) camZ);
        GL11.glTranslated(0.5 - (camX - (int) camX), 0.5 - (camY - (int) camY), 0.5 - (camZ - (int) camZ));
        Rotation.sideRotations[target.sideHit].glApply();
        GL11.glTranslated(0, -0.502, 0);
        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);
        tess.setColorRGBA(red, green, blue, alpha);
        tess.addVertex(+.50, 0, -.25);
        tess.addVertex(-.50, 0, -.25);
        tess.addVertex(+.50, 0, +.25);
        tess.addVertex(-.50, 0, +.25);
        tess.addVertex(+.25, 0, -.50);
        tess.addVertex(+.25, 0, +.50);
        tess.addVertex(-.25, 0, -.50);
        tess.addVertex(-.25, 0, +.50);
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if ((mask & side.flag) != 0) cross(tess, CELL[target.sideHit][side.ordinal()]);
        }
        tess.draw();

        GL20.glUseProgram(program);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    /** A cross over one grid cell, in the face's own coordinates. */
    private static void cross(Tessellator tess, int cell) {
        switch (cell) {
            case 0 -> diagonals(tess, -.25, -.25, +.25, +.25);
            case 1 -> diagonals(tess, -.25, +.25, +.25, +.50);
            case 2 -> diagonals(tess, -.50, -.25, -.25, +.25);
            case 3 -> diagonals(tess, -.25, -.50, +.25, -.25);
            case 4 -> diagonals(tess, +.25, -.25, +.50, +.25);
            default -> {
                diagonals(tess, +.25, +.25, +.50, +.50);
                diagonals(tess, +.25, -.50, +.50, -.25);
                diagonals(tess, -.50, +.25, -.25, +.50);
                diagonals(tess, -.50, -.50, -.25, -.25);
            }
        }
    }

    private static void diagonals(Tessellator tess, double x1, double z1, double x2, double z2) {
        tess.addVertex(x1, 0, z1);
        tess.addVertex(x2, 0, z2);
        tess.addVertex(x1, 0, z2);
        tess.addVertex(x2, 0, z1);
    }

    private static void outline(AxisAlignedBB box, int red, int green, int blue, int alpha) {
        Tessellator tess = Tessellator.instance;
        for (double y : new double[] { box.minY, box.maxY }) {
            tess.startDrawing(GL11.GL_LINE_STRIP);
            tess.setColorRGBA(red, green, blue, alpha);
            tess.addVertex(box.minX, y, box.minZ);
            tess.addVertex(box.maxX, y, box.minZ);
            tess.addVertex(box.maxX, y, box.maxZ);
            tess.addVertex(box.minX, y, box.maxZ);
            tess.addVertex(box.minX, y, box.minZ);
            tess.draw();
        }
        tess.startDrawing(GL11.GL_LINES);
        tess.setColorRGBA(red, green, blue, alpha);
        for (double x : new double[] { box.minX, box.maxX }) {
            for (double z : new double[] { box.minZ, box.maxZ }) {
                tess.addVertex(x, box.minY, z);
                tess.addVertex(x, box.maxY, z);
            }
        }
        tess.draw();
    }
}
