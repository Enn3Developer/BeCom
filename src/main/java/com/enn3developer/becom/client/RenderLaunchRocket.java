package com.enn3developer.becom.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import micdoodle8.mods.galacticraft.core.client.model.ModelRocketTier1;

/**
 * Draws a Launch Pad's rocket with Galacticraft's own Tier 1 rocket model and texture, the rocket GTNH players know,
 * the way Galacticraft draws it, with its fins standing on the entity's feet.
 */
public class RenderLaunchRocket extends Render {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        "galacticraftcore",
        "textures/model/rocketT1.png");
    // The model's fins reach 25 pixels below its origin.
    private static final float FINS = 25 / 16f;
    private static final float PIXEL = 1 / 16f;

    private final ModelBase model = new ModelRocketTier1();

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        GL11.glPushMatrix();
        GL11.glTranslatef((float) x, (float) y + FINS, (float) z);
        GL11.glRotatef(180.0f - yaw, 0.0f, 1.0f, 0.0f);
        bindEntityTexture(entity);
        GL11.glScalef(-1.0f, -1.0f, 1.0f);
        model.render(entity, 0.0f, 0.0f, -0.1f, 0.0f, 0.0f, PIXEL);
        GL11.glPopMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return TEXTURE;
    }
}
