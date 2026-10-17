package com.enn3developer.becom.client;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;

import com.enn3developer.becom.packet.RearFramePacket;
import com.enn3developer.becom.packet.VideoEquipmentPacket;
import com.enn3developer.becom.packet.VideoPosePacket;
import com.enn3developer.becom.packet.VideoProfilePacket;
import com.enn3developer.becom.packet.VideoStatePacket;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

/**
 * This client's side of its video call: what the server says it sees, each person's latest
 * poses, profile and equipment, and the rear pictures coming in. Everything here is touched on the client thread
 * only: packets are handed over there, and pictures are decoded on a worker and handed back.
 */
public final class ClientVideo {

    /** How many frames of blocky glitches show after frames were lost. */
    static final int GLITCH_FRAMES = 3;

    /** One person this player sees. */
    static final class Feed {

        final UUID id;
        String name = "";
        GameProfile profile;
        EntityOtherPlayerMP entity;
        final ItemStack[] equipment = new ItemStack[VideoEquipmentPacket.SLOTS];
        VideoPosePacket.Pose pose;
        VideoPosePacket.Pose previous;
        long poseAt;
        long previousAt;
        float limbSwing;
        long limbAt;
        int glitches;
        // The front camera's picture: drawn offscreen at the level's height, as often as the level allows.
        Framebuffer frame;
        int frameSize;
        long frameAt = Long.MIN_VALUE;
        boolean drawn;
        // The rear camera's latest picture.
        DynamicTexture rear;
        int rearWidth;
        int rearHeight;
        long rearAt;

        Feed(UUID id) {
            this.id = id;
        }
    }

    private static VideoStatePacket.State state = VideoStatePacket.State.NONE;
    private static final Map<UUID, Feed> FEEDS = new HashMap<>();
    private static boolean windowShown = true;
    // Decoding and encoding pictures stays off the render thread.
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "BeCom video");
        thread.setDaemon(true);
        return thread;
    });

    private ClientVideo() {}

    static ExecutorService worker() {
        return WORKER;
    }

    public static VideoStatePacket.State state() {
        return state;
    }

    public static boolean windowShown() {
        return windowShown;
    }

    public static void setWindowShown(boolean shown) {
        windowShown = shown;
    }

    /** The feed of someone this player sees, made on first sight. */
    static Feed feed(UUID id) {
        return FEEDS.computeIfAbsent(id, Feed::new);
    }

    /** The feed of someone this player sees, or null. */
    static Feed existing(UUID id) {
        return FEEDS.get(id);
    }

    // region Packets, on the client thread

    public static void apply(VideoStatePacket packet) {
        state = packet.state();
        for (VideoStatePacket.Tile tile : state.tiles()) feed(tile.source()).name = tile.name();
        // Feeds of people no longer seen let go of their pictures.
        Iterator<Map.Entry<UUID, Feed>> it = FEEDS.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Feed> entry = it.next();
            if (seen(entry.getKey())) continue;
            release(entry.getValue());
            it.remove();
        }
        VideoRenderer.stateChanged();
    }

    private static boolean seen(UUID id) {
        for (VideoStatePacket.Tile tile : state.tiles()) if (tile.source()
            .equals(id)) return true;
        return false;
    }

    public static void apply(VideoPosePacket packet) {
        long now = System.currentTimeMillis();
        for (VideoPosePacket.Pose pose : packet.poses()) {
            if (!seen(pose.source())) continue;
            Feed feed = feed(pose.source());
            // A gap in the count means frames were lost on the way: the picture glitches as it catches up.
            if (feed.pose != null && pose.seq() != ((feed.pose.seq() + 1) & 0xFF)) feed.glitches = GLITCH_FRAMES;
            feed.previous = feed.pose;
            feed.previousAt = feed.poseAt;
            feed.pose = pose;
            feed.poseAt = now;
        }
    }

    public static void apply(VideoEquipmentPacket packet) {
        if (!seen(packet.source())) return;
        Feed feed = feed(packet.source());
        System.arraycopy(packet.stacks(), 0, feed.equipment, 0, VideoEquipmentPacket.SLOTS);
    }

    public static void apply(VideoProfilePacket packet) {
        Feed feed = feed(packet.source());
        GameProfile profile = new GameProfile(packet.source(), packet.name());
        if (!packet.textures()
            .isEmpty()) {
            profile.getProperties()
                .put(
                    "textures",
                    packet.signature()
                        .isEmpty() ? new Property("textures", packet.textures())
                            : new Property("textures", packet.textures(), packet.signature()));
        }
        feed.profile = profile;
        feed.name = packet.name();
        // A new body, so the skin loads from the new profile.
        feed.entity = null;
    }

    /** A rear picture arrived: decoded on the worker, shown from the client thread. */
    public static void apply(RearFramePacket packet, java.util.function.Consumer<Runnable> clientThread) {
        byte[] jpeg = packet.jpeg();
        UUID source = packet.source();
        if (jpeg.length == 0) return;
        WORKER.execute(() -> {
            BufferedImage image = RearJpeg.decode(jpeg);
            if (image != null) clientThread.accept(() -> showRear(source, image));
        });
    }

    private static void showRear(UUID source, BufferedImage image) {
        if (!seen(source)) return;
        Feed feed = feed(source);
        if (feed.rear == null || feed.rearWidth != image.getWidth() || feed.rearHeight != image.getHeight()) {
            if (feed.rear != null) feed.rear.deleteGlTexture();
            feed.rear = new DynamicTexture(image.getWidth(), image.getHeight());
            feed.rearWidth = image.getWidth();
            feed.rearHeight = image.getHeight();
        }
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), feed.rear.getTextureData(), 0, image.getWidth());
        feed.rear.updateDynamicTexture();
        feed.rearAt = System.currentTimeMillis();
    }

    // endregion

    /** The body a feed's picture is drawn with, made in this world from the person's profile. */
    static EntityOtherPlayerMP body(Feed feed) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return null;
        if (feed.entity == null || feed.entity.worldObj != mc.theWorld) {
            GameProfile profile = feed.profile != null ? feed.profile : new GameProfile(feed.id, feed.name);
            feed.entity = new EntityOtherPlayerMP(mc.theWorld, profile);
            VideoRenderer.ourBody(feed.entity);
        }
        return feed.entity;
    }

    /** The people this player sees, in the server's order. */
    static List<VideoStatePacket.Tile> tiles() {
        return state.tiles();
    }

    private static void release(Feed feed) {
        if (feed.frame != null) feed.frame.deleteFramebuffer();
        if (feed.rear != null) feed.rear.deleteGlTexture();
        feed.frame = null;
        feed.rear = null;
        VideoRenderer.forgetBody(feed.entity);
    }

    /** Leaving a server forgets everything, the pictures' GL objects included. */
    public static void reset() {
        state = VideoStatePacket.State.NONE;
        for (Feed feed : FEEDS.values()) release(feed);
        FEEDS.clear();
        TalkMeter.clear();
        VideoRenderer.stateChanged();
    }
}
