package com.enn3developer.becom.client;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.video.VideoQuality;
import com.enn3developer.becom.packet.BeComPackets;
import com.enn3developer.becom.packet.RearUploadPacket;
import com.enn3developer.becom.packet.VideoEquipmentPacket;
import com.enn3developer.becom.packet.VideoPosePacket;
import com.enn3developer.becom.packet.VideoStatePacket;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Draws video calls on this client (DESIGN.md 10.1, 10.2, 11.2, D44).
 * <p>
 * The front camera: each person is drawn the way the inventory screen draws the player, from the poses the server
 * sends, into a small picture of the height their level allows, as often as it allows; Medium and High add noise,
 * frames lost on the way show a few frames of blocky glitches, and at Low the picture freezes. The pictures show in a
 * corner window, full size in the phone screen, and on the screen of the Videophone this player stands at.
 * <p>
 * The rear camera: this player's own view, copied just before the HUD is drawn, shrunk on the GPU, compressed to JPEG
 * on a worker and sent to the server, when the server says it goes somewhere.
 */
public final class VideoRenderer {

    // The front camera frames a person from the waist up: this many blocks of them fill the picture's height, with
    // their eyes this far down it.
    private static final float FRAMED_BLOCKS = 1.4f;
    private static final float EYE_LINE = 0.4f;
    private static final float EYE_HEIGHT = 1.62f;
    private static final float HEAD_TURN_LIMIT = 75f;
    // The talking animation: a nod this many degrees at full loudness, this many times a second.
    private static final float NOD_DEGREES = 7f;
    private static final double NOD_HZ = 3.5;
    private static final float TALKING = 0.15f;
    private static final int FULL_BRIGHT = 240;
    // Noise: the share of a picture's pixels speckled at Medium and at High (DESIGN.md 10.1: noisy, light noise).
    private static final float NOISE_MEDIUM = 0.08f;
    private static final float NOISE_HIGH = 0.02f;
    private static final int NOISE_ALPHA = 96;
    private static final int[] GLITCH_COLORS = { 0x1F5F2F, 0x7F2F7F, 0x6F6F6F, 0x2F3F7F };
    private static final int GLITCH_ALPHA = 200;
    private static final int BACKGROUND_TOP = 0xFF2B3440;
    private static final int BACKGROUND_BOTTOM = 0xFF15191F;
    private static final int TILE_BACKGROUND = 0xFF101418;
    private static final int FROZEN_SHADE = 0x80000000;
    private static final int NAME_BAR = 0xA0000000;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int TALK_BORDER = 0xFF40E060;
    private static final int WINDOW_BACKGROUND = 0xC0000000;
    // The corner window, in scaled GUI pixels.
    private static final int WINDOW_WIDTH = 96;
    private static final int WINDOW_MARGIN = 4;
    private static final float SMALL_TEXT = 0.5f;
    private static final int SMALL_TILE = 48;
    // The rear picture is shown while it's this fresh; older, the tiles take its place.
    private static final long REAR_FRESH_MILLIS = 3000;
    private static final float REAR_ASPECT = 16f / 9f;
    // The picture's share of a box that also holds the people; a Videophone's screen keeps a border.
    private static final float REAR_SHARE = 0.65f;
    private static final float SCREEN_HALF = 7f / 16f;
    // On the face, a tile's name bar takes this share of it, and text this share of the bar or of the tile's width.
    private static final float FACE_NAME_BAR = 0.16f;
    private static final float FACE_TEXT = 0.8f;
    private static final float SCREEN_OUT = 0.5f + 0.002f;
    private static final double SCREEN_RANGE = 64;

    private static final Random RANDOM = new Random();
    private static final Set<EntityLivingBase> BODIES = Collections.newSetFromMap(new WeakHashMap<>());
    private static Framebuffer rearTarget;
    private static long lastCapture;
    private static volatile boolean encoding;
    private static final List<Runnable> CLIENT_THREAD = Collections.synchronizedList(new ArrayList<>());
    private static Consumer<byte[]> uploads = jpeg -> BeComPackets.CHANNEL.sendToServer(new RearUploadPacket(jpeg));

    /** A body this renderer draws: its name tag stays hidden. */
    static void ourBody(EntityLivingBase body) {
        BODIES.add(body);
    }

    static void forgetBody(EntityLivingBase body) {
        if (body != null) BODIES.remove(body);
    }

    static void stateChanged() {
        if (ClientVideo.state()
            .rearSend() == SignalLevel.NONE) lastCapture = 0;
    }

    /** The client self-test keeps the rear pictures it captures instead of sending them. */
    public static void uploadsForSelfTest(Consumer<byte[]> sink) {
        uploads = sink;
    }

    /** The front picture last drawn of {@code source}, for the client self-test; null when none was drawn. */
    public static Framebuffer frameForSelfTest(java.util.UUID source) {
        ClientVideo.Feed feed = ClientVideo.existing(source);
        return feed == null || !feed.drawn ? null : feed.frame;
    }

    /** Whether this client can make the pictures at all: they need framebuffers (DESIGN.md 16.2). */
    public static boolean framebuffers() {
        return OpenGlHelper.isFramebufferEnabled();
    }

    @SubscribeEvent
    public void onSpecials(RenderLivingEvent.Specials.Pre event) {
        if (BODIES.contains(event.entity)) event.setCanceled(true);
    }

    // region Front camera

    /** Draws the front pictures that are due, before the frame: nothing else is drawn yet. */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        runClientTasks();
        if (mc.theWorld == null || ClientVideo.tiles()
            .isEmpty()) return;
        long now = System.currentTimeMillis();
        Tunables tunables = BeComConfig.get();
        boolean drew = false;
        for (VideoStatePacket.Tile tile : ClientVideo.tiles()) {
            if (!tile.front() || !tile.level()
                .atLeast(SignalLevel.MEDIUM)) continue;
            ClientVideo.Feed feed = ClientVideo.existing(tile.source());
            if (feed == null || feed.pose == null) continue;
            VideoQuality quality = VideoQuality.of(tunables, tile.level());
            int size = quality.frontHeight();
            boolean due = feed.frameSize != size || quality.frontFps() <= 0
                || now - feed.frameAt >= 1000L / quality.frontFps();
            if (!due) continue;
            feed.frameAt = now;
            if (!framebuffers()) {
                // DESIGN.md 16.2's fallback: drawn straight into the window, keeping its frame rate and freezes.
                feed.frameSize = size;
                feed.drawn = true;
                continue;
            }
            renderFront(feed, size, tile.level(), now);
            drew = true;
        }
        if (drew) mc.getFramebuffer()
            .bindFramebuffer(true);
    }

    private static void renderFront(ClientVideo.Feed feed, int size, SignalLevel level, long now) {
        EntityOtherPlayerMP body = ClientVideo.body(feed);
        // Bodies are drawn with the world's renderers, which have nothing to draw with before the world's first frame.
        if (body == null || RenderManager.instance.renderEngine == null) return;
        if (feed.frame == null || feed.frameSize != size) {
            if (feed.frame != null) feed.frame.deleteFramebuffer();
            feed.frame = new Framebuffer(size, size, true);
            feed.frameSize = size;
        }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, size, size, 0, 1000, 3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0, 0, -2000);
        feed.frame.bindFramebuffer(true);
        GL11.glClearColor(0, 0, 0, 1);
        GL11.glClearDepth(1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        gradient(0, 0, size, size, BACKGROUND_TOP, BACKGROUND_BOTTOM);
        pose(body, feed, now);
        drawBody(body, size / 2f, EYE_LINE * size, size / FRAMED_BLOCKS);
        noise(level, size);
        if (feed.glitches > 0) {
            glitch(size);
            feed.glitches--;
        }
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        feed.drawn = true;
    }

    /** Puts the person's latest pose on the body, eased between the last two (DESIGN.md 10.1: every frame at Max). */
    private static void pose(EntityOtherPlayerMP body, ClientVideo.Feed feed, long now) {
        VideoPosePacket.Pose to = feed.pose;
        VideoPosePacket.Pose from = feed.previous != null ? feed.previous : to;
        long interval = Math.max(1, feed.poseAt - feed.previousAt);
        float t = feed.previous == null ? 1 : MathHelper.clamp_float((now - feed.poseAt) / (float) interval, 0, 1);
        float head = from.headYaw() + MathHelper.wrapAngleTo180_float(to.headYaw() - from.headYaw()) * t;
        float pitch = from.pitch() + (to.pitch() - from.pitch()) * t;
        float talk = TalkMeter.loudness(feed.id);
        float nod = (float) Math.sin(now / 1000.0 * 2 * Math.PI * NOD_HZ) * talk * NOD_DEGREES;
        body.renderYawOffset = body.prevRenderYawOffset = 0;
        body.rotationYaw = body.prevRotationYaw = 0;
        body.rotationYawHead = body.prevRotationYawHead = MathHelper
            .clamp_float(head, -HEAD_TURN_LIMIT, HEAD_TURN_LIMIT);
        body.rotationPitch = body.prevRotationPitch = MathHelper.clamp_float(pitch + nod, -90, 90);
        body.swingProgress = body.prevSwingProgress = to.swing();
        body.setSneaking(to.sneaking());
        // Legs swing with walking speed, as Minecraft swings them: by the speed, every tick.
        float amount = Math.min(1f, to.speed() * 4f);
        long elapsed = feed.limbAt == 0 ? 0 : Math.min(1000, now - feed.limbAt);
        feed.limbAt = now;
        feed.limbSwing += amount * elapsed / 50f;
        body.limbSwing = feed.limbSwing;
        body.limbSwingAmount = body.prevLimbSwingAmount = amount;
        for (int slot = 0; slot < VideoEquipmentPacket.SLOTS; slot++) {
            body.setCurrentItemOrArmor(slot, feed.equipment[slot]);
        }
    }

    /**
     * Draws a body facing the viewer, the way the inventory screen draws the player, with its eyes at
     * {@code (x, eyeY)} and {@code scale} pixels to a block.
     */
    private static void drawBody(EntityOtherPlayerMP body, float x, float eyeY, float scale) {
        // The background was drawn flat: textures, alpha test and depth back on for the body.
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glPushMatrix();
        GL11.glTranslatef(x, eyeY + EYE_HEIGHT * scale, 50f);
        GL11.glScalef(-scale, scale, scale);
        GL11.glRotatef(180f, 0, 0, 1);
        GL11.glRotatef(135f, 0, 1, 0);
        RenderHelper.enableStandardItemLighting();
        GL11.glRotatef(-135f, 0, 1, 0);
        GL11.glTranslatef(0, body.yOffset, 0);
        // A body far from any loaded block would be drawn in the dark: the picture is lit like a screen.
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, FULL_BRIGHT, FULL_BRIGHT);
        RenderManager.instance.playerViewY = 180f;
        RenderManager.instance.renderEntityWithPosYaw(body, 0, 0, 0, 0, 1f);
        GL11.glPopMatrix();
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private static void noise(SignalLevel level, int size) {
        float share = level == SignalLevel.MEDIUM ? NOISE_MEDIUM : level == SignalLevel.HIGH ? NOISE_HIGH : 0;
        int dots = (int) (size * size * share);
        if (dots == 0) return;
        flat();
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        for (int i = 0; i < dots; i++) {
            int x = RANDOM.nextInt(size), y = RANDOM.nextInt(size), grey = RANDOM.nextInt(256);
            tess.setColorRGBA(grey, grey, grey, NOISE_ALPHA);
            quad(tess, x, y, 1, 1);
        }
        tess.draw();
    }

    /** A few frames of blocky glitches after frames were lost (DESIGN.md 10.1). */
    private static void glitch(int size) {
        int block = Math.max(2, size / 8);
        int cells = Math.max(1, size / block);
        flat();
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        int count = 6 + RANDOM.nextInt(6);
        for (int i = 0; i < count; i++) {
            int color = GLITCH_COLORS[RANDOM.nextInt(GLITCH_COLORS.length)];
            tess.setColorRGBA(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, GLITCH_ALPHA);
            int x = RANDOM.nextInt(cells) * block, y = RANDOM.nextInt(cells) * block;
            // Some blocks smear sideways, as a broken picture does.
            int width = RANDOM.nextBoolean() ? block : block * (2 + RANDOM.nextInt(3));
            quad(tess, x, y, width, RANDOM.nextBoolean() ? block : Math.max(1, block / 2));
        }
        tess.draw();
    }

    // endregion

    // region Drawing the call

    /**
     * The call in a box, in GUI pixels: the rear picture over the people when one comes in, the people in a grid
     * otherwise. Used by the corner window and the phone screen.
     */
    static void drawCall(int x, int y, int width, int height) {
        List<VideoStatePacket.Tile> tiles = ClientVideo.tiles();
        if (tiles.isEmpty()) return;
        ClientVideo.Feed rear = freshRear();
        int gridTop = y;
        int gridHeight = height;
        if (rear != null) {
            int rearHeight = Math.round(Math.min(width / REAR_ASPECT, height * REAR_SHARE));
            int rearWidth = Math.round(rearHeight * REAR_ASPECT);
            drawRear(rear, x + (width - rearWidth) / 2, y, rearWidth, rearHeight);
            gridTop = y + rearHeight + 1;
            gridHeight = height - rearHeight - 1;
        }
        int n = tiles.size();
        int cols = (int) Math.ceil(Math.sqrt(n));
        int rows = (n + cols - 1) / cols;
        int cell = Math.max(1, Math.min(width / cols, gridHeight / rows));
        int left = x + (width - cell * cols) / 2;
        for (int i = 0; i < n; i++) {
            drawTile(tiles.get(i), left + i % cols * cell, gridTop + i / cols * cell, cell);
        }
    }

    /** The height {@link #drawCall} needs at {@code width} for this many people. */
    private static int callHeight(int width) {
        int n = ClientVideo.tiles()
            .size();
        if (n == 0) return 0;
        int cols = (int) Math.ceil(Math.sqrt(n));
        int rows = (n + cols - 1) / cols;
        int height = rows * (width / cols);
        if (freshRear() != null) height += Math.round(width / REAR_ASPECT) + 1;
        return height;
    }

    private static ClientVideo.Feed freshRear() {
        long now = System.currentTimeMillis();
        for (VideoStatePacket.Tile tile : ClientVideo.tiles()) {
            if (!tile.rear()) continue;
            ClientVideo.Feed feed = ClientVideo.existing(tile.source());
            if (feed != null && feed.rear != null && now - feed.rearAt < REAR_FRESH_MILLIS) return feed;
        }
        return null;
    }

    private static void drawRear(ClientVideo.Feed feed, int x, int y, int width, int height) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, feed.rear.getGlTextureId());
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y + height, 0, 0, 1);
        tess.addVertexWithUV(x + width, y + height, 0, 1, 1);
        tess.addVertexWithUV(x + width, y, 0, 1, 0);
        tess.addVertexWithUV(x, y, 0, 0, 0);
        tess.draw();
    }

    /**
     * What a tile says over its picture, or null over a moving one: the camera's off, the signal is too weak and the
     * picture froze, or the first picture hasn't come yet.
     */
    private static String labelOf(VideoStatePacket.Tile tile, ClientVideo.Feed feed) {
        boolean live = tile.level()
            .atLeast(SignalLevel.MEDIUM);
        if (!tile.front()) return StatCollector.translateToLocal("becom.video.camera_off");
        if (feed != null && feed.drawn) return live ? null : StatCollector.translateToLocal("becom.video.weak_signal");
        return StatCollector.translateToLocal(live ? "becom.video.connecting" : "becom.video.weak_signal");
    }

    /** Whether a tile shows its last picture under a shade: frozen at Low. */
    private static boolean frozen(VideoStatePacket.Tile tile, ClientVideo.Feed feed) {
        return tile.front() && feed != null
            && feed.drawn
            && !tile.level()
                .atLeast(SignalLevel.MEDIUM);
    }

    /** One person: their picture, frozen with "weak signal" at Low, their name, and a border while they talk. */
    private static void drawTile(VideoStatePacket.Tile tile, int x, int y, int size) {
        Minecraft mc = Minecraft.getMinecraft();
        ClientVideo.Feed feed = ClientVideo.existing(tile.source());
        Gui.drawRect(x, y, x + size, y + size, TILE_BACKGROUND);
        String label = labelOf(tile, feed);
        if (tile.front() && feed != null && feed.drawn) {
            if (feed.frame != null) drawFramebuffer(feed.frame, x, y, size);
            else drawDirect(feed, x, y, size);
            if (frozen(tile, feed)) Gui.drawRect(x, y, x + size, y + size, FROZEN_SHADE);
        }
        float textScale = size < SMALL_TILE ? SMALL_TEXT : 1f;
        FontRenderer font = mc.fontRenderer;
        if (label != null)
            text(font, label, x + size / 2f, y + size / 2f - font.FONT_HEIGHT * textScale / 2, textScale, size);
        String name = feed != null ? feed.name : tile.name();
        int bar = Math.round(font.FONT_HEIGHT * textScale) + 2;
        Gui.drawRect(x, y + size - bar, x + size, y + size, NAME_BAR);
        text(font, name, x + size / 2f, y + size - bar + 1, textScale, size);
        if (TalkMeter.loudness(tile.source()) > TALKING) border(x, y, size, TALK_BORDER);
    }

    private static void drawFramebuffer(Framebuffer frame, int x, int y, int size) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
        frame.bindFramebufferTexture();
        float u = frame.framebufferWidth / (float) frame.framebufferTextureWidth;
        float v = frame.framebufferHeight / (float) frame.framebufferTextureHeight;
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        // A framebuffer's picture is upside down from the GUI's point of view.
        tess.addVertexWithUV(x, y + size, 0, 0, 0);
        tess.addVertexWithUV(x + size, y + size, 0, u, 0);
        tess.addVertexWithUV(x + size, y, 0, u, v);
        tess.addVertexWithUV(x, y, 0, 0, v);
        tess.draw();
        frame.unbindFramebufferTexture();
    }

    /** DESIGN.md 16.2's fallback without framebuffers: the body drawn straight into the tile, at full detail. */
    private static void drawDirect(ClientVideo.Feed feed, int x, int y, int size) {
        EntityOtherPlayerMP body = ClientVideo.body(feed);
        if (body == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.client.gui.ScaledResolution res = new net.minecraft.client.gui.ScaledResolution(
            mc,
            mc.displayWidth,
            mc.displayHeight);
        int factor = res.getScaleFactor();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_SCISSOR_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * factor, mc.displayHeight - (y + size) * factor, size * factor, size * factor);
        // Only the picture's own frames move it: its pose was taken when one was due.
        pose(body, feed, feed.frameAt);
        drawBody(body, x + size / 2f, y + EYE_LINE * size, size / FRAMED_BLOCKS);
        GL11.glPopAttrib();
    }

    // endregion

    // region Corner window

    /** The small window in a corner during a video call (D44), unless the player hid it. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!ClientVideo.windowShown() || ClientVideo.tiles()
            .isEmpty()) return;
        int height = callHeight(WINDOW_WIDTH);
        ClientConfig.Corner corner = ClientConfig.videoCorner();
        int screenWidth = event.resolution.getScaledWidth();
        int screenHeight = event.resolution.getScaledHeight();
        int x = corner.right() ? screenWidth - WINDOW_MARGIN - WINDOW_WIDTH : WINDOW_MARGIN;
        int y = corner.bottom() ? screenHeight - WINDOW_MARGIN - height : WINDOW_MARGIN;
        Gui.drawRect(x - 1, y - 1, x + WINDOW_WIDTH + 1, y + height + 1, WINDOW_BACKGROUND);
        drawCall(x, y, WINDOW_WIDTH, height);
        GL11.glColor4f(1, 1, 1, 1);
    }

    /** The phone screen's video page (D44): the call as large as the page allows, or why there's none. */
    public static void drawScreenView(int x, int y, int width, int height, boolean capable) {
        Minecraft mc = Minecraft.getMinecraft();
        Gui.drawRect(x, y, x + width, y + height, TILE_BACKGROUND);
        if (!capable || ClientVideo.tiles()
            .isEmpty()) {
            String key = capable ? "becom.video.none" : "becom.video.no_camera";
            text(mc.fontRenderer, StatCollector.translateToLocal(key), x + width / 2f, y + height / 2f - 4, 1f);
            return;
        }
        drawCall(x, y, width, height);
        GL11.glColor4f(1, 1, 1, 1);
    }

    // endregion

    // region Rear camera

    /** Copies this player's view just before the HUD is drawn, when the server says it goes somewhere. */
    @SubscribeEvent
    public void onOverlayPre(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        VideoStatePacket.State state = ClientVideo.state();
        if (!state.rear() || state.rearSend() == SignalLevel.NONE || !framebuffers() || encoding) return;
        Tunables tunables = BeComConfig.get();
        VideoQuality quality = VideoQuality.of(tunables, state.rearSend());
        if (!quality.rear()) return;
        long now = System.currentTimeMillis();
        long gap = Math.max(quality.rearGapMillis(), 1000L / Math.max(1, tunables.rearMaxFps()));
        if (now - lastCapture < gap) return;
        lastCapture = now;
        int width = quality.rearWidth(), height = quality.rearHeight();
        int[] pixels = capture(width, height);
        int maxBytes = tunables.rearMaxBytes();
        encoding = true;
        ClientVideo.worker()
            .execute(() -> {
                byte[] jpeg = null;
                try {
                    jpeg = RearJpeg.encode(pixels, width, height, maxBytes);
                } finally {
                    byte[] done = jpeg;
                    CLIENT_THREAD.add(() -> {
                        encoding = false;
                        if (done != null) uploads.accept(done);
                    });
                }
            });
    }

    /** The world image shrunk to {@code width} by {@code height} on the GPU, its middle 16:9, bottom row first. */
    private static int[] capture(int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer main = mc.getFramebuffer();
        if (rearTarget == null || rearTarget.framebufferWidth != width || rearTarget.framebufferHeight != height) {
            if (rearTarget != null) rearTarget.deleteFramebuffer();
            rearTarget = new Framebuffer(width, height, false);
        }
        float aspect = width / (float) height;
        float sourceWidth = main.framebufferWidth, sourceHeight = main.framebufferHeight;
        float cropWidth = Math.min(sourceWidth, sourceHeight * aspect);
        float cropHeight = Math.min(sourceHeight, sourceWidth / aspect);
        float u0 = (sourceWidth - cropWidth) / 2 / main.framebufferTextureWidth;
        float u1 = (sourceWidth + cropWidth) / 2 / main.framebufferTextureWidth;
        float v0 = (sourceHeight - cropHeight) / 2 / main.framebufferTextureHeight;
        float v1 = (sourceHeight + cropHeight) / 2 / main.framebufferTextureHeight;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, width, height, 0, 1000, 3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0, 0, -2000);
        rearTarget.bindFramebuffer(true);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
        // Shrunk smoothly, then the world's picture goes back to how Minecraft keeps it.
        main.setFramebufferFilter(GL11.GL_LINEAR);
        main.bindFramebufferTexture();
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(0, height, 0, u0, v0);
        tess.addVertexWithUV(width, height, 0, u1, v0);
        tess.addVertexWithUV(width, 0, 0, u1, v1);
        tess.addVertexWithUV(0, 0, 0, u0, v1);
        tess.draw();
        main.setFramebufferFilter(GL11.GL_NEAREST);
        IntBuffer buffer = BufferUtils.createIntBuffer(width * height);
        GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
        int[] pixels = new int[width * height];
        buffer.get(pixels);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        main.bindFramebuffer(false);
        return pixels;
    }

    private static void runClientTasks() {
        Runnable[] tasks;
        synchronized (CLIENT_THREAD) {
            if (CLIENT_THREAD.isEmpty()) return;
            tasks = CLIENT_THREAD.toArray(new Runnable[0]);
            CLIENT_THREAD.clear();
        }
        for (Runnable task : tasks) task.run();
    }

    // endregion

    // region Videophone screen

    /** The call on the screen of the Videophone this player stands at (DESIGN.md 10.2), as its room sees it. */
    @SubscribeEvent
    public void onWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        VideoStatePacket.Screen screen = ClientVideo.state()
            .screen();
        if (screen == null || mc.theWorld == null
            || mc.theWorld.provider.dimensionId != screen.dimension()
            || ClientVideo.tiles()
                .isEmpty())
            return;
        double dx = screen.x() + 0.5 - RenderManager.renderPosX;
        double dy = screen.y() + 0.5 - RenderManager.renderPosY;
        double dz = screen.z() + 0.5 - RenderManager.renderPosZ;
        if (dx * dx + dy * dy + dz * dz > SCREEN_RANGE * SCREEN_RANGE) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix();
        GL11.glTranslated(dx, dy, dz);
        GL11.glRotatef(faceAngle(screen.facing()), 0, 1, 0);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, FULL_BRIGHT, FULL_BRIGHT);
        drawFace();
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /** Turns the block's south face, where the screen is drawn, to the way the Videophone faces. */
    private static float faceAngle(int facing) {
        return switch (facing) {
            case 2 -> 180f; // north
            case 4 -> -90f; // west
            case 5 -> 90f; // east
            default -> 0f; // south
        };
    }

    /** The people, or the rear picture, on the face at z = 0.5, seen from +z: x to the right, y up. */
    private static void drawFace() {
        Tessellator tess = Tessellator.instance;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        tess.startDrawingQuads();
        tess.setColorOpaque_I(TILE_BACKGROUND & 0xFFFFFF);
        faceQuad(tess, -SCREEN_HALF, -SCREEN_HALF, SCREEN_HALF, SCREEN_HALF, 0, 0, 0, 0, SCREEN_OUT);
        tess.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
        float side = 2 * SCREEN_HALF;
        ClientVideo.Feed rear = freshRear();
        if (rear != null) {
            float h = side / REAR_ASPECT;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, rear.rear.getGlTextureId());
            tess.startDrawingQuads();
            faceQuad(tess, -SCREEN_HALF, -h / 2, SCREEN_HALF, h / 2, 0, 1, 1, 0, SCREEN_OUT + 0.001f);
            tess.draw();
            return;
        }
        List<VideoStatePacket.Tile> tiles = ClientVideo.tiles();
        int n = tiles.size();
        int cols = (int) Math.ceil(Math.sqrt(n));
        int rows = (n + cols - 1) / cols;
        float cell = side / Math.max(cols, rows);
        float left = -cell * cols / 2, top = cell * rows / 2;
        for (int i = 0; i < n; i++) {
            VideoStatePacket.Tile tile = tiles.get(i);
            ClientVideo.Feed feed = ClientVideo.existing(tile.source());
            float x0 = left + i % cols * cell, y1 = top - i / cols * cell;
            if (tile.front() && feed != null && feed.frame != null && feed.drawn) {
                Framebuffer frame = feed.frame;
                frame.bindFramebufferTexture();
                float u = frame.framebufferWidth / (float) frame.framebufferTextureWidth;
                float v = frame.framebufferHeight / (float) frame.framebufferTextureHeight;
                tess.startDrawingQuads();
                faceQuad(tess, x0, y1 - cell, x0 + cell, y1, 0, 0, u, v, SCREEN_OUT + 0.001f);
                tess.draw();
                frame.unbindFramebufferTexture();
            }
            faceLabels(tile, feed, x0, y1, cell);
        }
    }

    /** A tile's shade, label and name on the face, as {@link #drawTile} draws them in a screen. */
    private static void faceLabels(VideoStatePacket.Tile tile, ClientVideo.Feed feed, float x0, float y1, float cell) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        Tessellator tess = Tessellator.instance;
        float z = SCREEN_OUT + 0.002f;
        float bar = cell * FACE_NAME_BAR;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        tess.startDrawingQuads();
        if (frozen(tile, feed)) {
            tess.setColorRGBA_I(FROZEN_SHADE & 0xFFFFFF, FROZEN_SHADE >>> 24);
            faceQuad(tess, x0, y1 - cell, x0 + cell, y1, 0, 0, 0, 0, z);
        }
        tess.setColorRGBA_I(NAME_BAR & 0xFFFFFF, NAME_BAR >>> 24);
        faceQuad(tess, x0, y1 - cell, x0 + cell, y1 - cell + bar, 0, 0, 0, 0, z);
        tess.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        // The font draws downwards from its top, the face counts up: flipped, its quads face away, so no culling.
        GL11.glDisable(GL11.GL_CULL_FACE);
        String label = labelOf(tile, feed);
        if (label != null) faceText(font, label, x0 + cell / 2, y1 - cell / 2, bar * FACE_TEXT, cell, z + 0.001f);
        String name = feed != null ? feed.name : tile.name();
        faceText(font, name, x0 + cell / 2, y1 - cell + bar / 2, bar * FACE_TEXT, cell, z + 0.001f);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
    }

    /** Text on the face centred on (x, y), {@code height} blocks tall, or smaller to fit {@code width}. */
    private static void faceText(FontRenderer font, String text, float x, float y, float height, float width, float z) {
        int textWidth = font.getStringWidth(text);
        float scale = height / font.FONT_HEIGHT;
        if (textWidth * scale > width * FACE_TEXT) scale = width * FACE_TEXT / textWidth;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, z);
        GL11.glScalef(scale, -scale, scale);
        font.drawString(text, -textWidth / 2, -font.FONT_HEIGHT / 2, TEXT);
        GL11.glPopMatrix();
    }

    /** A quad on the face from (x0, y0) to (x1, y1), its texture from (u0, v0) at the bottom left to (u1, v1). */
    private static void faceQuad(Tessellator tess, float x0, float y0, float x1, float y1, float u0, float v0, float u1,
        float v1, float z) {
        tess.addVertexWithUV(x0, y0, z, u0, v0);
        tess.addVertexWithUV(x1, y0, z, u1, v0);
        tess.addVertexWithUV(x1, y1, z, u1, v1);
        tess.addVertexWithUV(x0, y1, z, u0, v1);
    }

    // endregion

    // region Small drawing helpers

    private static void flat() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
    }

    private static void quad(Tessellator tess, int x, int y, int width, int height) {
        tess.addVertex(x, y + height, 0);
        tess.addVertex(x + width, y + height, 0);
        tess.addVertex(x + width, y, 0);
        tess.addVertex(x, y, 0);
    }

    private static void gradient(int x0, int y0, int x1, int y1, int top, int bottom) {
        flat();
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorRGBA_I(top & 0xFFFFFF, top >>> 24);
        tess.addVertex(x1, y0, 0);
        tess.addVertex(x0, y0, 0);
        tess.setColorRGBA_I(bottom & 0xFFFFFF, bottom >>> 24);
        tess.addVertex(x0, y1, 0);
        tess.addVertex(x1, y1, 0);
        tess.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private static void border(int x, int y, int size, int color) {
        Gui.drawRect(x, y, x + size, y + 1, color);
        Gui.drawRect(x, y + size - 1, x + size, y + size, color);
        Gui.drawRect(x, y, x + 1, y + size, color);
        Gui.drawRect(x + size - 1, y, x + size, y + size, color);
    }

    /** Text centred on {@code centerX}, at {@code scale}, or smaller to fit within {@code width}. */
    private static void text(FontRenderer font, String text, float centerX, float y, float scale, int width) {
        int textWidth = font.getStringWidth(text);
        if (textWidth * scale > width - 2) scale = Math.max(0.25f, (width - 2) / (float) textWidth);
        text(font, text, centerX, y, scale);
    }

    /** Text centred on {@code centerX}, at {@code scale}. */
    private static void text(FontRenderer font, String text, float centerX, float y, float scale) {
        GL11.glPushMatrix();
        GL11.glTranslatef(centerX, y, 0);
        GL11.glScalef(scale, scale, 1);
        font.drawStringWithShadow(text, -font.getStringWidth(text) / 2, 0, TEXT);
        GL11.glPopMatrix();
    }

    // endregion
}
