package com.enn3developer.becom.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.packet.WireSyncPacket;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Draws pylon wires as sagging lines between linked heads (DESIGN.md 5.1). A blocked link (D20) renders broken: each
 * end hangs down limp from its head.
 */
public final class WireRenderer {

    // How far a wire sags at its middle, per block of span.
    private static final double SAG_PER_BLOCK = 0.03;
    private static final int SEGMENTS = 16;
    // Wires are drawn within about the default render distance.
    private static final double RANGE = 192;
    private static final float LINE_WIDTH = 2.0f;
    private static final int WIRE_COLOR = 0x2A2A2A;
    private static final int BROKEN_COLOR = 0x7A3A1A;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase view = mc.renderViewEntity;
        if (view == null || mc.theWorld == null) return;
        var wires = ClientWires.wires(mc.theWorld.provider.dimensionId);
        if (wires.isEmpty()) return;
        float pt = event.partialTicks;
        double cx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double cy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double cz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glLineWidth(LINE_WIDTH);
        Tessellator tess = Tessellator.instance;
        for (WireSyncPacket.Wire wire : wires) {
            double ax = BlockPos.x(wire.a()) + 0.5 - cx, ay = BlockPos.y(wire.a()) + 0.5 - cy;
            double az = BlockPos.z(wire.a()) + 0.5 - cz;
            double bx = BlockPos.x(wire.b()) + 0.5 - cx, by = BlockPos.y(wire.b()) + 0.5 - cy;
            double bz = BlockPos.z(wire.b()) + 0.5 - cz;
            if (ax * ax + ay * ay + az * az > RANGE * RANGE && bx * bx + by * by + bz * bz > RANGE * RANGE) continue;
            double length = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
            double sag = SAG_PER_BLOCK * length;
            if (wire.blocked()) {
                hang(tess, ax, ay, az, bx, by, bz, length);
                hang(tess, bx, by, bz, ax, ay, az, length);
                continue;
            }
            tess.startDrawing(GL11.GL_LINE_STRIP);
            tess.setColorOpaque_I(WIRE_COLOR);
            for (int i = 0; i <= SEGMENTS; i++) {
                double t = (double) i / SEGMENTS;
                tess.addVertex(ax + (bx - ax) * t, ay + (by - ay) * t - 4 * sag * t * (1 - t), az + (bz - az) * t);
            }
            tess.draw();
        }
        GL11.glPopAttrib();
    }

    /** A broken wire's end: a short run toward the other head, then straight down. */
    private static void hang(Tessellator tess, double x, double y, double z, double tx, double ty, double tz,
        double length) {
        double reach = Math.min(1.5, length / 4);
        double dx = (tx - x) / length * reach, dz = (tz - z) / length * reach;
        tess.startDrawing(GL11.GL_LINE_STRIP);
        tess.setColorOpaque_I(BROKEN_COLOR);
        tess.addVertex(x, y, z);
        tess.addVertex(x + dx, y - reach / 2, z + dz);
        tess.addVertex(x + dx, y - Math.min(length / 3, 6), z + dz);
        tess.draw();
    }
}
