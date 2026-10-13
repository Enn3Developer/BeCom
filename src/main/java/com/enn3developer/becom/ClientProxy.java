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
import com.enn3developer.becom.block.BlockPhoneCable;
import com.enn3developer.becom.block.BlockTelephone;
import com.enn3developer.becom.client.AnalyzerOverlay;
import com.enn3developer.becom.client.ClientAudio;
import com.enn3developer.becom.client.ClientConfig;
import com.enn3developer.becom.client.ClientLevels;
import com.enn3developer.becom.client.ClientPhone;
import com.enn3developer.becom.client.ClientRadio;
import com.enn3developer.becom.client.ClientVideo;
import com.enn3developer.becom.client.ClientWires;
import com.enn3developer.becom.client.CoverageMapDrawer;
import com.enn3developer.becom.client.DistanceFade;
import com.enn3developer.becom.client.MachineTooltips;
import com.enn3developer.becom.client.PhoneCableRenderer;
import com.enn3developer.becom.client.SignalBars;
import com.enn3developer.becom.client.TelephoneRenderer;
import com.enn3developer.becom.client.ToneSounds;
import com.enn3developer.becom.client.VideoRenderer;
import com.enn3developer.becom.client.WireRenderer;
import com.enn3developer.becom.config.BeComConfig;
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
import com.enn3developer.becom.packet.WireSyncPacket;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.quest.bq.BqGuideTags;
import com.enn3developer.becom.selftest.ClientSelfTest;

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
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(new WireRenderer());
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
        ClientSelfTest.startIfAsked();
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
            ClientWires.clear();
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

    @Override
    public void onWires(WireSyncPacket packet) {
        mainThread.add(() -> ClientWires.set(packet.dimension(), packet.wires()));
    }
}
