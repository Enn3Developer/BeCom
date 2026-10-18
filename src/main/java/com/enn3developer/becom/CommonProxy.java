package com.enn3developer.becom;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.enn3developer.becom.block.BeComBlocks;
import com.enn3developer.becom.command.CommandBeCom;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.entity.EntityLaunchRocket;
import com.enn3developer.becom.gui.paint.CanvasScreen;
import com.enn3developer.becom.item.BeComItems;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.machine.BeComMachines;
import com.enn3developer.becom.packet.AnalyzerPacket;
import com.enn3developer.becom.packet.BeComPackets;
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
import com.enn3developer.becom.recipe.BeComRecipes;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.Ownership;
import com.enn3developer.becom.server.ServerEvents;
import com.enn3developer.becom.server.VoiceSessions;
import com.enn3developer.gtnhvoice.api.server.GtnhVoiceApi;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.StartupQuery;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.registry.EntityRegistry;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        BeComConfig.load(event.getSuggestedConfigurationFile());
        BeComBlocks.register();
        BeComItems.register();
        BeComMachines.register();
        BeComPackets.register();
        // Seen from far off, and flown by each client itself, so the server sends no velocity.
        EntityRegistry.registerModEntity(EntityLaunchRocket.class, "launch_rocket", 0, BeCom.instance, 256, 20, false);
    }

    public void init(FMLInitializationEvent event) {
        ServerEvents events = new ServerEvents();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance()
            .bus()
            .register(events);
        // Session listeners outlive voice server restarts, so this is added once.
        GtnhVoiceApi.addSessionListener(new VoiceSessions());
        BeComQuests.init();
        Ownership.init();
        com.enn3developer.becom.apps.PhoneOs.init();
        // By name, so the class and WAILA's API load only if WAILA is there to call it. GT covers BeCom's machines.
        FMLInterModComms.sendMessage("Waila", "register", "com.enn3developer.becom.block.WailaBlocks.register");
    }

    public void postInit(FMLPostInitializationEvent event) {
        BeComRecipes.register();
    }

    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        // A server acts on its own config file; a client in this JVM had the defaults or a server's.
        String problem = BeComConfig.fileProblem();
        if (problem != null) refuseWorld("config: " + problem);
        BeComConfig.useFileValues();
        // Before worlds load, so each dimension's network loads with its world.
        BeComServer.create(event.getServer());
    }

    public void serverStarting(FMLServerStartingEvent event) {
        BeComServer.start();
        event.registerServerCommand(new CommandBeCom());
        // CI's run-server job greps for this line: FML silently skips mod jars it can't read.
        BeCom.LOG.info("Better Communications {} started", Tags.VERSION);
    }

    /**
     * Stops a world from starting, saying why. FML's own way (as for missing IDs): on a dedicated server the reason
     * goes
     * to the console; in single-player it shows on screen and leads back to the menu. Throwing from a server event
     * instead leaves a single-player client on "Loading world", or back at the menu without a word.
     */
    private static void refuseWorld(String reason) {
        BeCom.LOG.error("BeCom can't start this world: {}", reason);
        StartupQuery.notify(StatCollector.translateToLocalFormatted("becom.world.refused", reason));
        StartupQuery.abort();
    }

    public void serverStarted(FMLServerStartedEvent event) {
        BeComQuests.backend()
            .serverStarted();
    }

    public void serverStopping(FMLServerStoppingEvent event) {
        // While worlds are still loaded, so ending calls can still write their history.
        BeComServer.stop();
    }

    public void serverStopped(FMLServerStoppedEvent event) {
        // The quest backend is shared too: a newer world's questline stays.
        BeComServer.stopped(
            () -> BeComQuests.backend()
                .serverStopped());
    }

    // Client hooks for packets; the dedicated server has no use for them.

    public void onLevel(LevelPacket packet) {}

    public void onTone(TonePacket packet) {}

    public void onConfig(ConfigSyncPacket packet) {}

    public void onAnalyzer(AnalyzerPacket packet) {}

    public void onRing(RingPacket packet) {}

    public void onPhoneStatus(PhoneStatusPacket packet) {}

    public void onCallBars(CallBarsPacket packet) {}

    public void onVideoState(VideoStatePacket packet) {}

    public void onVideoPoses(VideoPosePacket packet) {}

    public void onVideoEquipment(VideoEquipmentPacket packet) {}

    public void onVideoProfile(VideoProfilePacket packet) {}

    public void onRearFrame(RearFramePacket packet) {}

    /** The Phone key's name, for tooltips: its default where there are no key bindings. */
    public String phoneKeyName() {
        return "N";
    }

    /** Whether this client is transmitting on that walkie, for its TX light; never on a server. */
    public boolean isTransmittingOn(ItemStack stack) {
        return false;
    }

    /** Signal bars for a screen; nothing to draw on a server. */
    public IDrawable signalBars(Supplier<SignalLevel> level) {
        return IDrawable.EMPTY;
    }

    /** An antenna's coverage map for its screen, drawn only on the client. */
    public IDrawable coverageMap(Supplier<byte[]> map, Supplier<SignalLevel> cap) {
        return IDrawable.EMPTY;
    }

    /** The other side of this player's video call, full size, for the phone screen; nothing on a server. */
    public IDrawable videoView(BooleanSupplier capable) {
        return IDrawable.EMPTY;
    }

    /** Whether this client sees anyone in a video call right now; never on a server. */
    public boolean inVideoCall() {
        return false;
    }

    /** Whether the corner video window shows: the client's own choice. */
    public boolean videoWindowShown() {
        return true;
    }

    public void setVideoWindowShown(boolean shown) {}

    /**
     * Whether this client can capture its rear camera; it needs framebuffers, else the fallback draws straight into the
     * window.
     */
    public boolean rearCameraWorks() {
        return true;
    }

    /** {@code beside}, after a Lua app's icon when it has one; just {@code beside} on a server. */
    public IDrawable appIcon(Supplier<String> icon, IDrawable beside) {
        return beside;
    }

    /** This client's world time, the world's total time, for how old a screen's data is; 0 on a server. */
    public long worldTime() {
        return 0;
    }

    /** This client's world's time of day, Minecraft's own clock; 0 on a server. */
    public long dayTime() {
        return 0;
    }

    /** Which corner the video window sits in, a ClientConfig.Corner ordinal. */
    /** How tall the game's screen is in GUI pixels; 0 on a server. */
    public int guiHeight() {
        return 0;
    }

    public int videoCorner() {
        return 1;
    }

    public void setVideoCorner(int corner) {}

    /** Paints a board-drawn screen, {@code scale} GUI pixels a board pixel; nothing to paint on a server. */
    public void paint(CanvasScreen screen, float scale, float mouseX, float mouseY, ModularGuiContext context) {}

    /** The coverage map's colour key, Low up to {@code cap}. */
    public IDrawable coverageLegend(Supplier<SignalLevel> cap) {
        return IDrawable.EMPTY;
    }
}
