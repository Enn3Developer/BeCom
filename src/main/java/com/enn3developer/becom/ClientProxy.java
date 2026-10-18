package com.enn3developer.becom;

import java.io.File;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.enn3developer.becom.block.BlockPhoneCable;
import com.enn3developer.becom.block.BlockTelephone;
import com.enn3developer.becom.client.AnalyzerOverlay;
import com.enn3developer.becom.client.AppIcon;
import com.enn3developer.becom.client.CableGrid;
import com.enn3developer.becom.client.ClientAudio;
import com.enn3developer.becom.client.ClientConfig;
import com.enn3developer.becom.client.ClientLevels;
import com.enn3developer.becom.client.ClientPhone;
import com.enn3developer.becom.client.ClientRadio;
import com.enn3developer.becom.client.ClientVideo;
import com.enn3developer.becom.client.CoverageMapDrawer;
import com.enn3developer.becom.client.DistanceFade;
import com.enn3developer.becom.client.GlCanvas;
import com.enn3developer.becom.client.MachineTooltips;
import com.enn3developer.becom.client.PhoneCableRenderer;
import com.enn3developer.becom.client.RenderLaunchRocket;
import com.enn3developer.becom.client.SignalBars;
import com.enn3developer.becom.client.TelephoneRenderer;
import com.enn3developer.becom.client.ToneSounds;
import com.enn3developer.becom.client.VideoRenderer;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.entity.EntityLaunchRocket;
import com.enn3developer.becom.gui.paint.CanvasScreen;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.packet.AnalyzerPacket;
import com.enn3developer.becom.packet.CallBarsPacket;
import com.enn3developer.becom.packet.ConfigSyncPacket;
import com.enn3developer.becom.packet.LevelPacket;
import com.enn3developer.becom.packet.PhoneStatusPacket;
import com.enn3developer.becom.packet.RearFramePacket;
import com.enn3developer.becom.packet.RingPacket;
import com.enn3developer.becom.packet.TonePacket;
import com.enn3developer.becom.packet.VideoEquipmentPacket;
import com.enn3developer.becom.packet.VideoPosePacket;
import com.enn3developer.becom.packet.VideoProfilePacket;
import com.enn3developer.becom.packet.VideoStatePacket;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.quest.bq.BqGuideTags;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

public class ClientProxy extends CommonProxy {

    // Game work from packet handlers, run at the end of the client tick. 1.7.10 already calls mod packet handlers on
    // the game thread, so this only orders the work after the tick; it stays safe if a handler ever runs off that
    // thread.
    private final Queue<Runnable> mainThread = new ConcurrentLinkedQueue<>();

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        ClientConfig.load(new File(event.getModConfigurationDirectory(), BeCom.MODID + "-client.cfg"));
        BlockTelephone.renderId = RenderingRegistry.getNextAvailableRenderId();
        BlockPhoneCable.renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new TelephoneRenderer(BlockTelephone.renderId));
        RenderingRegistry.registerBlockHandler(new PhoneCableRenderer(BlockPhoneCable.renderId));
        RenderingRegistry.registerEntityRenderingHandler(EntityLaunchRocket.class, new RenderLaunchRocket());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(new CableGrid());
        MinecraftForge.EVENT_BUS.register(new SignalBars());
        MinecraftForge.EVENT_BUS.register(new AnalyzerOverlay());
        MinecraftForge.EVENT_BUS.register(new ClientRadio());
        VideoRenderer video = new VideoRenderer();
        MinecraftForge.EVENT_BUS.register(video);
        FMLCommonHandler.instance()
            .bus()
            .register(video);
        ClientPhone phone = new ClientPhone();
        MinecraftForge.EVENT_BUS.register(phone);
        FMLCommonHandler.instance()
            .bus()
            .register(phone);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        ClientAudio.register();
        ClientRadio.register();
        ClientPhone.register();
        // GuideNH makes the quests' [guide] links into links; without it they show their label alone.
        if (Loader.isModLoaded(BeComQuests.BQ_MODID) && !Loader.isModLoaded("guidenh")) BqGuideTags.register();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Runnable task;
        while ((task = mainThread.poll()) != null) task.run();
        ClientRadio.tick();
        EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) DistanceFade.forget();
        else DistanceFade.listen(player.posX, player.boundingBox.minY, player.posZ, BeComConfig.get());
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        mainThread.add(() -> {
            ClientLevels.clear();
            ToneSounds.stop();
            BeComConfig.useDefaults();
            MachineTooltips.forget();
            ClientRadio.reset();
            ClientPhone.reset();
            AnalyzerOverlay.clear();
            ClientVideo.reset();
        });
    }

    @Override
    public void onConfig(ConfigSyncPacket packet) {
        if (packet.tunables() == null) return;
        mainThread.add(() -> {
            BeComConfig.set(packet.tunables());
            MachineTooltips.forget();
        });
    }

    @Override
    public void onLevel(LevelPacket packet) {
        // The level table is concurrent and read on decode threads, so it's set right away.
        ClientLevels.set(packet.source(), packet.group(), packet.level());
    }

    @Override
    public void onAnalyzer(AnalyzerPacket packet) {
        AnalyzerOverlay.set(packet);
    }

    @Override
    public String phoneKeyName() {
        return ClientPhone.keyName();
    }

    @Override
    public boolean isTransmittingOn(ItemStack stack) {
        return ClientRadio.transmittingOn(stack);
    }

    @Override
    public void onRing(RingPacket packet) {
        ClientPhone.ring(packet);
    }

    @Override
    public void onPhoneStatus(PhoneStatusPacket packet) {
        ClientPhone.service(packet.service());
    }

    @Override
    public void onCallBars(CallBarsPacket packet) {
        ClientPhone.callBars(packet.inCall() ? packet.level() : null);
    }

    @Override
    public void onTone(TonePacket packet) {
        mainThread.add(() -> ToneSounds.play(packet.tone()));
    }

    @Override
    public IDrawable signalBars(Supplier<SignalLevel> level) {
        return (context, x, y, width, height, theme) -> SignalBars.draw(x, y + height, level.get());
    }

    @Override
    public IDrawable coverageMap(Supplier<byte[]> map, Supplier<SignalLevel> cap) {
        return (context, x, y, width, height, theme) -> CoverageMapDrawer
            .draw(x, y, width, height, map.get(), cap.get());
    }

    @Override
    public IDrawable appIcon(Supplier<String> icon, IDrawable beside) {
        return (context, x, y, width, height, theme) -> {
            String hex = icon.get();
            if (hex == null || hex.length() != AppIcon.CELLS * AppIcon.CELLS) {
                beside.draw(context, x, y, width, height, theme);
                return;
            }
            // Whole pixels a cell, as big as the row lets it be.
            int cell = Math.max(1, Math.min(2, (height - 2) / AppIcon.CELLS));
            int size = cell * AppIcon.CELLS;
            AppIcon.draw(x + 3, y + (height - size) / 2, cell, hex);
            beside.draw(context, x + size + 5, y, width - size - 5, height, theme);
        };
    }

    @Override
    public long worldTime() {
        net.minecraft.client.multiplayer.WorldClient world = net.minecraft.client.Minecraft.getMinecraft().theWorld;
        return world == null ? 0 : world.getTotalWorldTime();
    }

    @Override
    public long dayTime() {
        net.minecraft.client.multiplayer.WorldClient world = net.minecraft.client.Minecraft.getMinecraft().theWorld;
        return world == null ? 0 : world.getWorldTime();
    }

    @Override
    public int guiHeight() {
        Minecraft mc = Minecraft.getMinecraft();
        return new net.minecraft.client.gui.ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaledHeight();
    }

    @Override
    public int videoCorner() {
        return ClientConfig.videoCorner()
            .ordinal();
    }

    @Override
    public void setVideoCorner(int corner) {
        ClientConfig.Corner[] corners = ClientConfig.Corner.values();
        ClientConfig.setVideoCorner(corners[Math.floorMod(corner, corners.length)]);
    }

    @Override
    public void paint(CanvasScreen screen, float scale, float mouseX, float mouseY, ModularGuiContext context) {
        org.lwjgl.opengl.GL11.glPushMatrix();
        org.lwjgl.opengl.GL11.glScalef(scale, scale, 1);
        screen.paint(new GlCanvas(scale, context), mouseX, mouseY);
        org.lwjgl.opengl.GL11.glPopMatrix();
    }

    @Override
    public IDrawable coverageLegend(Supplier<SignalLevel> cap) {
        return (context, x, y, width, height, theme) -> CoverageMapDrawer.legend(x, y, width, height, cap.get());
    }

    @Override
    public void onVideoState(VideoStatePacket packet) {
        mainThread.add(() -> ClientVideo.apply(packet));
    }

    @Override
    public void onVideoPoses(VideoPosePacket packet) {
        mainThread.add(() -> ClientVideo.apply(packet));
    }

    @Override
    public void onVideoEquipment(VideoEquipmentPacket packet) {
        mainThread.add(() -> ClientVideo.apply(packet));
    }

    @Override
    public void onVideoProfile(VideoProfilePacket packet) {
        mainThread.add(() -> ClientVideo.apply(packet));
    }

    @Override
    public void onRearFrame(RearFramePacket packet) {
        ClientVideo.apply(packet, mainThread::add);
    }

    @Override
    public IDrawable videoView(BooleanSupplier capable) {
        return (context, x, y, width, height, theme) -> VideoRenderer
            .drawScreenView(x, y, width, height, capable.getAsBoolean());
    }

    @Override
    public boolean inVideoCall() {
        return ClientVideo.state()
            .active();
    }

    @Override
    public boolean videoWindowShown() {
        return ClientVideo.windowShown();
    }

    @Override
    public void setVideoWindowShown(boolean shown) {
        ClientVideo.setWindowShown(shown);
    }

    @Override
    public boolean rearCameraWorks() {
        return VideoRenderer.framebuffers();
    }
}
