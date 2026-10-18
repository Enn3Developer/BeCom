package com.enn3developer.becom.client;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.Stencil;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.enn3developer.becom.gui.paint.Canvas;

/**
 * The client's {@link Canvas}: board pixels scaled to the screen with GL. Text uses Minecraft's font at the nearest
 * whole number of screen pixels a font pixel, starting on a whole screen pixel, so it stays crisp.
 */
public final class GlCanvas implements Canvas {

    // A board's 13-pixel text is Minecraft's font at full size when a board pixel is 0.75 of a GUI pixel.
    private static final float BOARD_TEXT_AT_FULL_SIZE = 13f * 0.75f;
    private static final float GLYPH_HEIGHT = 8f;
    private static final Map<String, ItemStack> ITEMS = new HashMap<>();

    private final float scale;
    private final ModularGuiContext context;
    private final FontRenderer font = Minecraft.getMinecraft().fontRenderer;
    // Screen pixels a board pixel, and where the board's top left is on the screen, from the GL transform as it is:
    // whatever the GUI, ModularUI and the caller scaled it by.
    private final float pixels;
    private final float originX;
    private final float originY;

    /** @param scale GUI pixels a board pixel; the caller has its GL transform at the board's top left */
    public GlCanvas(float scale, ModularGuiContext context) {
        this.scale = scale;
        this.context = context;
        Minecraft mc = Minecraft.getMinecraft();
        int factor = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
        float m = matrix.get(0);
        pixels = (m > 0 ? m : scale) * factor;
        originX = matrix.get(12) * factor;
        originY = matrix.get(13) * factor;
    }

    /**
     * Board pixels a font pixel for board text of {@code size}: a whole number of screen pixels, as a fraction of one,
     * such as 2.5, draws the font's pixels uneven and grainy.
     */
    private float fontScale(float size) {
        float wanted = size * pixels / BOARD_TEXT_AT_FULL_SIZE;
        return Math.max(1, Math.round(wanted)) / pixels;
    }

    /** A board position on the nearest whole screen pixel. */
    private float snap(float at, float origin) {
        return (Math.round(origin + at * pixels) - origin) / pixels;
    }

    @Override
    public void rect(float x, float y, float w, float h, int argb) {
        if (w <= 0 || h <= 0) return;
        int color = (argb >>> 24) == 0 ? 0xFF000000 | argb : argb;
        GuiDraw.drawRect(x, y, w, h, color);
    }

    @Override
    public void text(String text, float x, float y, float size, int rgb) {
        if (text.isEmpty()) return;
        float s = fontScale(size);
        GL11.glPushMatrix();
        GL11.glTranslatef(snap(x, originX), snap(y, originY), 0);
        GL11.glScalef(s, s, 1);
        font.drawString(text, 0, 0, 0xFF000000 | rgb, false);
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    @Override
    public float width(String text, float size) {
        return font.getStringWidth(text) * fontScale(size);
    }

    @Override
    public float lineHeight(float size) {
        return GLYPH_HEIGHT * fontScale(size);
    }

    @Override
    public void face(String player, float x, float y, float size) {
        ResourceLocation skin = AbstractClientPlayer.getLocationSkin(player);
        AbstractClientPlayer.getDownloadImageSkin(skin, player);
        // The face, then the hat layer over it, from a 64 by 32 skin.
        GuiDraw.drawTexture(skin, x, y, x + size, y + size, 8 / 64f, 8 / 32f, 16 / 64f, 16 / 32f, false);
        GuiDraw.drawTexture(skin, x, y, x + size, y + size, 40 / 64f, 8 / 32f, 48 / 64f, 16 / 32f, true);
        GL11.glColor4f(1, 1, 1, 1);
    }

    @Override
    public void item(String item, float x, float y, float size) {
        ItemStack stack = ITEMS.computeIfAbsent(item, GlCanvas::stackOf);
        if (stack == null) return;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        GL11.glScalef(size / 16f, size / 16f, 1);
        GuiDraw.drawItem(stack, 0, 0, 16, 16, 0);
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    /** An item by "modid:name" or "modid:name:meta"; null for one the game doesn't have. */
    private static ItemStack stackOf(String name) {
        int meta = 0;
        String id = name;
        int colon = name.lastIndexOf(':');
        if (colon > 0 && name.indexOf(':') != colon) {
            try {
                meta = Integer.parseInt(name.substring(colon + 1));
                id = name.substring(0, colon);
            } catch (NumberFormatException e) {
                id = name;
            }
        }
        Object found = Item.itemRegistry.getObject(id);
        return found instanceof Item i ? new ItemStack(i, 1, meta) : null;
    }

    @Override
    public void drawable(IDrawable drawable, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        GL11.glScalef(1 / scale, 1 / scale, 1);
        drawable.draw(
            context,
            0,
            0,
            Math.round(w * scale),
            Math.round(h * scale),
            WidgetTheme.getDefault()
                .getTheme());
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    @Override
    public void clip(float x, float y, float w, float h) {
        Stencil.apply(Math.round(x), Math.round(y), Math.round(w), Math.round(h), null);
    }

    @Override
    public void unclip() {
        Stencil.remove();
    }
}
