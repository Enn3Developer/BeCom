package com.enn3developer.becom.selftest;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.client.ClientVideo;
import com.enn3developer.becom.client.RearJpeg;
import com.enn3developer.becom.client.VideoRenderer;
import com.enn3developer.becom.command.CommandBeCom;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.item.BeComItems;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.packet.RearFramePacket;
import com.enn3developer.becom.packet.VideoEquipmentPacket;
import com.enn3developer.becom.packet.VideoPosePacket;
import com.enn3developer.becom.packet.VideoStatePacket;
import com.enn3developer.becom.server.BeComServer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A smoke test of the client's video drawing, for the development environment only. The run has no game client to
 * look with (DESIGN.md 16.3), so this is the nearest it gets: started with {@code -Dbecom.clienttest=<dir>}, the
 * client opens a flat world, plays a made-up video call through the real drawing code, checks what it drew, saves
 * screenshots and every picture into {@code <dir>}, prints {@code BeCom client test: N passed, M failed} and quits.
 * It proves the drawing runs and what it draws; how it looks, and whether it works under Angelica, stays the owner's
 * to judge (DESIGN.md 16.2).
 */
@SideOnly(Side.CLIENT)
public final class ClientSelfTest {

    private static final String PROPERTY = "becom.clienttest";
    private static final String PREFIX = "BeCom client test: ";
    private static final int SETTLE_TICKS = 100;
    private static final int POSE_TICKS = 2;
    private static final int REAR_TICKS = 4;
    private static final UUID MEDIUM = UUID.fromString("b3c0b3c0-0000-4000-8000-0000000000a1");
    private static final UUID HIGH = UUID.fromString("b3c0b3c0-0000-4000-8000-0000000000a2");
    private static final UUID MAX = UUID.fromString("b3c0b3c0-0000-4000-8000-0000000000a3");
    private static final UUID LOW = UUID.fromString("b3c0b3c0-0000-4000-8000-0000000000a4");
    private static final UUID OFF = UUID.fromString("b3c0b3c0-0000-4000-8000-0000000000a5");
    // Background colours a picture is cleared to; a pixel far from both is part of the person.
    private static final int BACKGROUND_TOP = 0x2B3440;
    private static final int BACKGROUND_BOTTOM = 0x15191F;

    private final File out;
    private boolean launched;
    private int ticks;
    private int seq;
    private int passed;
    private int failed;
    private byte[] captured;
    private int glErrors;
    // Run after the next frame is drawn; rear pictures decoded on the video worker are handed in here too.
    private final Queue<Runnable> afterFrame = new ConcurrentLinkedQueue<>();

    private ClientSelfTest(File out) {
        this.out = out;
    }

    /** Starts the test when the client was launched for it, in the development environment. */
    public static void startIfAsked() {
        String dir = System.getProperty(PROPERTY);
        if (dir == null || !CommandBeCom.devEnvironment()) return;
        ClientSelfTest test = new ClientSelfTest(new File(dir));
        test.out.mkdirs();
        MinecraftForge.EVENT_BUS.register(test);
        FMLCommonHandler.instance()
            .bus()
            .register(test);
        test.log("waiting for the main menu");
    }

    private void log(String line) {
        BeCom.LOG.info(PREFIX + line);
    }

    private void check(String name, boolean ok, String detail) {
        if (ok) passed++;
        else failed++;
        log((ok ? "PASS " : "FAIL ") + name + (ok || detail.isEmpty() ? "" : ": " + detail));
    }

    /** At the main menu, once the client has finished starting: a flat creative world. */
    private void launch(Minecraft mc) {
        launched = true;
        log("opening a flat world");
        String name = "becom-clienttest-" + System.currentTimeMillis();
        WorldSettings settings = new WorldSettings(1L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT);
        mc.launchIntegratedServer(name, name, settings);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END) return;
        if (!launched && mc.currentScreen instanceof GuiMainMenu) launch(mc);
        if (mc.thePlayer == null || mc.theWorld == null) return;
        ticks++;
        if (ticks == SETTLE_TICKS) startCall(mc);
        if (ticks > SETTLE_TICKS && ticks % POSE_TICKS == 0) poses();
        // Rear pictures stop a while before the last screenshot, so the Videophone's screen shows the people again.
        if (ticks > SETTLE_TICKS && ticks < SETTLE_TICKS + 140 && ticks % REAR_TICKS == 0) rearPicture();
        if (ticks == SETTLE_TICKS + 60) afterFrame.add(this::checkFront);
        if (ticks == SETTLE_TICKS + 80) startRear();
        if (ticks == SETTLE_TICKS + 140) afterFrame.add(this::checkRear);
        if (ticks == SETTLE_TICKS + 150) givePhone(mc);
        if (ticks == SETTLE_TICKS + 160) openPhone(mc);
        if (ticks == SETTLE_TICKS + 190) afterFrame.add(() -> screenshot("phone-video-page"));
        if (ticks == SETTLE_TICKS + 195) mc.displayGuiScreen(null);
        if (ticks == SETTLE_TICKS + 220) afterFrame.add(() -> screenshot("videophone-screen"));
        if (ticks == SETTLE_TICKS + 240) finish(mc);
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (ticks > SETTLE_TICKS) {
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) glErrors++;
        }
        for (int n = afterFrame.size(); n > 0; n--) {
            Runnable task = afterFrame.poll();
            if (task != null) task.run();
        }
    }

    /** A call with five people at every level: Medium, High and Max live, Low frozen, one camera off. */
    private void startCall(Minecraft mc) {
        log("starting a made-up video call");
        // A Videophone's screen three blocks in front of the player, facing them, so the room's view is drawn too.
        int facing = facingToward(mc.thePlayer.rotationYaw);
        ForgeDirection away = ForgeDirection.getOrientation(facing)
            .getOpposite();
        int x = (int) Math.floor(mc.thePlayer.posX) + 3 * away.offsetX;
        int y = (int) Math.floor(mc.thePlayer.posY);
        int z = (int) Math.floor(mc.thePlayer.posZ) + 3 * away.offsetZ;
        List<VideoStatePacket.Tile> tiles = List.of(
            new VideoStatePacket.Tile(MEDIUM, "Medium", SignalLevel.MEDIUM, true, false),
            new VideoStatePacket.Tile(HIGH, "High", SignalLevel.HIGH, true, false),
            new VideoStatePacket.Tile(MAX, "Max", SignalLevel.MAX, true, true),
            new VideoStatePacket.Tile(LOW, "Low", SignalLevel.LOW, true, false),
            new VideoStatePacket.Tile(OFF, "Off", SignalLevel.HIGH, false, false));
        ClientVideo.apply(
            new VideoStatePacket(
                new VideoStatePacket.State(
                    true,
                    false,
                    SignalLevel.NONE,
                    tiles,
                    new VideoStatePacket.Screen(mc.theWorld.provider.dimensionId, x, y, z, facing))));
        ItemStack[] equipment = new ItemStack[VideoEquipmentPacket.SLOTS];
        equipment[0] = new ItemStack(Items.diamond_sword);
        equipment[4] = new ItemStack(Items.diamond_helmet);
        ClientVideo.apply(new VideoEquipmentPacket(MAX, equipment));
    }

    /** The side of a block that faces a player looking along {@code yaw}: back toward them. */
    private static int facingToward(float yaw) {
        int quarter = MathHelper.floor_double(yaw * 4.0 / 360.0 + 0.5) & 3;
        // 0 looks south, so the block in front faces north; then west, north and east in turn.
        return switch (quarter) {
            case 0 -> ForgeDirection.NORTH.ordinal();
            case 1 -> ForgeDirection.EAST.ordinal();
            case 2 -> ForgeDirection.SOUTH.ordinal();
            default -> ForgeDirection.WEST.ordinal();
        };
    }

    /** A rear picture from Max, as one would arrive a few times a second: a made-up view, sky over grass. */
    private void rearPicture() {
        Tunables t = BeComConfig.get();
        int w = t.rearWidthMax(), h = t.rearHeightMax();
        int[] pixels = new int[w * h];
        int sun = (ticks * 3) % w;
        for (int i = 0; i < pixels.length; i++) {
            int row = i / w, col = i % w;
            pixels[i] = row < h / 2 ? 0xFF3F7F2F : Math.abs(col - sun) < 6 && row > h * 3 / 4 ? 0xFFFFE070 : 0xFF7FAFEF;
        }
        byte[] jpeg = RearJpeg.encode(pixels, w, h, t.rearMaxBytes());
        ClientVideo.apply(new RearFramePacket(MAX, jpeg), afterFrame::add);
    }

    /** Poses ten times a second: heads turning, an arm swinging, and a gap in High's count for the glitches. */
    private void poses() {
        seq++;
        double t = ticks / 20.0;
        List<VideoPosePacket.Pose> poses = new ArrayList<>();
        for (UUID id : new UUID[] { MEDIUM, HIGH, MAX, LOW }) {
            int count = id.equals(HIGH) && seq % 20 == 0 ? seq + 3 : seq;
            poses.add(
                new VideoPosePacket.Pose(
                    id,
                    count & 0xFF,
                    (float) (40 * Math.sin(t)),
                    (float) (15 * Math.sin(t * 0.7)),
                    (float) ((t % 1.5) / 1.5),
                    false,
                    0.1f));
        }
        ClientVideo.apply(new VideoPosePacket(poses));
    }

    private void checkFront() {
        Tunables t = BeComConfig.get();
        check("the client draws pictures with framebuffers here", VideoRenderer.framebuffers(), "");
        screenshot("corner-window");
        int[][] levels = { { 0, t.videoHeightMedium() }, { 1, t.videoHeightHigh() }, { 2, t.videoHeightMax() } };
        UUID[] ids = { MEDIUM, HIGH, MAX };
        for (int i = 0; i < ids.length; i++) {
            Framebuffer frame = VideoRenderer.frameForSelfTest(ids[i]);
            int size = levels[i][1];
            boolean sized = frame != null && frame.framebufferWidth == size && frame.framebufferHeight == size;
            double person = frame == null ? 0 : personShare(frame, "front-" + i);
            check(
                "the front picture at level " + i + " is " + size + " px tall and shows a person",
                sized && person > 0.05,
                (frame == null ? "no picture" : frame.framebufferWidth + " px") + ", person "
                    + String.format("%.3f", person));
        }
        check(
            "nothing moves at Low: no picture is drawn for the frozen tile",
            VideoRenderer.frameForSelfTest(LOW) == null,
            "");
        check("a switched-off camera draws no picture", VideoRenderer.frameForSelfTest(OFF) == null, "");
    }

    /** The share of a picture's pixels that are neither background colour: the person drawn on it. */
    private double personShare(Framebuffer frame, String name) {
        int w = frame.framebufferWidth, h = frame.framebufferHeight;
        frame.bindFramebuffer(false);
        IntBuffer buffer = BufferUtils.createIntBuffer(w * h);
        GL11.glReadPixels(0, 0, w, h, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
        Minecraft.getMinecraft()
            .getFramebuffer()
            .bindFramebuffer(true);
        int[] pixels = new int[w * h];
        buffer.get(pixels);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int person = 0;
        for (int row = 0; row < h; row++) {
            for (int x = 0; x < w; x++) {
                int rgb = pixels[row * w + x] & 0xFFFFFF;
                image.setRGB(x, h - 1 - row, rgb);
                if (far(rgb, BACKGROUND_TOP) && far(rgb, BACKGROUND_BOTTOM) && far(rgb, mix(row, h))) person++;
            }
        }
        try {
            ImageIO.write(image, "png", new File(out, name + ".png"));
        } catch (IOException e) {
            log("couldn't save " + name + ": " + e);
        }
        return person / (double) (w * h);
    }

    private static int mix(int row, int height) {
        // The gradient runs top to bottom; rows come bottom first.
        float t = 1 - row / (float) Math.max(1, height - 1);
        int r = Math.round((BACKGROUND_TOP >> 16 & 0xFF) * (1 - t) + (BACKGROUND_BOTTOM >> 16 & 0xFF) * t);
        int g = Math.round((BACKGROUND_TOP >> 8 & 0xFF) * (1 - t) + (BACKGROUND_BOTTOM >> 8 & 0xFF) * t);
        int b = Math.round((BACKGROUND_TOP & 0xFF) * (1 - t) + (BACKGROUND_BOTTOM & 0xFF) * t);
        return r << 16 | g << 8 | b;
    }

    private static boolean far(int a, int b) {
        int dr = (a >> 16 & 0xFF) - (b >> 16 & 0xFF);
        int dg = (a >> 8 & 0xFF) - (b >> 8 & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return dr * dr + dg * dg + db * db > 40 * 40;
    }

    /** The rear camera, as if the server said this player's view goes out at Max. */
    private void startRear() {
        VideoStatePacket.State state = ClientVideo.state();
        VideoRenderer.uploadsForSelfTest(jpeg -> captured = jpeg);
        ClientVideo.apply(
            new VideoStatePacket(
                new VideoStatePacket.State(state.front(), true, SignalLevel.MAX, state.tiles(), state.screen())));
    }

    private void checkRear() {
        Tunables t = BeComConfig.get();
        BufferedImage image = captured == null ? null : RearJpeg.decode(captured);
        check(
            "the rear camera copies the view, " + t
                .rearWidthMax() + " by " + t.rearHeightMax() + ", within " + t.rearMaxBytes() + " bytes",
            image != null && image.getWidth() == t.rearWidthMax()
                && image.getHeight() == t.rearHeightMax()
                && captured.length <= t.rearMaxBytes(),
            captured == null ? "nothing captured" : captured.length + " bytes");
        if (captured != null) {
            try {
                Files.write(new File(out, "rear-capture.jpg").toPath(), captured);
            } catch (IOException e) {
                log("couldn't save the rear capture: " + e);
            }
        }
        screenshot("rear-and-window");
    }

    /** A Mobile Phone in the player's hand, given on the server, a few ticks before the screen opens from it. */
    private void givePhone(Minecraft mc) {
        onServer(
            mc,
            player -> player.inventory.mainInventory[player.inventory.currentItem] = new ItemStack(
                BeComItems.mobilePhone));
    }

    /** The phone screen, opened by the server as a right-click with the phone would: it opens on the video. */
    private void openPhone(Minecraft mc) {
        onServer(
            mc,
            player -> GuiFactories.playerInventory()
                .openFromMainHand(player));
    }

    private static void onServer(Minecraft mc, Consumer<EntityPlayerMP> task) {
        String name = mc.thePlayer.getCommandSenderName();
        BeComServer.later(() -> {
            EntityPlayerMP player = MinecraftServer.getServer()
                .getConfigurationManager()
                .func_152612_a(name);
            if (player != null) task.accept(player);
        });
    }

    private void screenshot(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        ScreenShotHelper.saveScreenshot(out, name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }

    private void finish(Minecraft mc) {
        check("drawing raised no OpenGL errors", glErrors == 0, glErrors + " frames with an error");
        log(passed + " passed, " + failed + " failed");
        mc.shutdown();
    }
}
