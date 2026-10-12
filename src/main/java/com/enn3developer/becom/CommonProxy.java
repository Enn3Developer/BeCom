package com.enn3developer.becom;

import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.enn3developer.becom.block.BeComBlocks;
import com.enn3developer.becom.command.CommandBeCom;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.item.BeComItems;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.machine.BeComMachines;
import com.enn3developer.becom.packet.AnalyzerPacket;
import com.enn3developer.becom.packet.BeComPackets;
import com.enn3developer.becom.packet.ConfigSyncPacket;
import com.enn3developer.becom.packet.LevelPacket;
import com.enn3developer.becom.packet.PhoneStatusPacket;
import com.enn3developer.becom.packet.RingPacket;
import com.enn3developer.becom.packet.TonePacket;
import com.enn3developer.becom.packet.WireSyncPacket;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.recipe.BeComRecipes;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.ServerEvents;
import com.enn3developer.becom.server.VoiceSessions;
import com.enn3developer.gtnhvoice.api.server.GtnhVoiceApi;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.StartupQuery;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        BeComConfig.load(event.getSuggestedConfigurationFile());
        BeComBlocks.register();
        BeComItems.register();
        BeComMachines.register();
        BeComPackets.register();
    }

    public void init(FMLInitializationEvent event) {
        ServerEvents events = new ServerEvents();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance()
            .bus()
            .register(events);
        // Session listeners outlive voice server restarts, so this is added once (DESIGN.md 13.2).
        GtnhVoiceApi.addSessionListener(new VoiceSessions());
        BeComQuests.init();
    }

    public void postInit(FMLPostInitializationEvent event) {
        BeComRecipes.register();
    }

    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        // A server acts on its own config file (DESIGN.md 14.2); a client in this JVM had the defaults or a server's.
        String problem = BeComConfig.fileProblem();
        if (problem != null) refuseWorld("config: " + problem);
        BeComConfig.useFileValues();
        // Before worlds load, so each dimension's network loads with its world.
        BeComServer.create(event.getServer());
    }

    public void serverStarting(FMLServerStartingEvent event) {
        try {
            BeComServer.start();
        } catch (IllegalStateException e) {
            // Saved data BeCom won't overwrite (DESIGN.md 14.2).
            refuseWorld(e.getMessage());
        }
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

    public void onWires(WireSyncPacket packet) {}

    public void onConfig(ConfigSyncPacket packet) {}

    public void onAnalyzer(AnalyzerPacket packet) {}

    public void onRing(RingPacket packet) {}

    public void onPhoneStatus(PhoneStatusPacket packet) {}

    /** Whether this client is transmitting on that walkie, for its TX light (DESIGN.md 11.3); never on a server. */
    public boolean isTransmittingOn(ItemStack stack) {
        return false;
    }

    /** Signal bars for a screen (DESIGN.md 11.2); nothing to draw on a server. */
    public IDrawable signalBars(Supplier<SignalLevel> level) {
        return IDrawable.EMPTY;
    }

    /** An antenna's coverage map for its screen, drawn only on the client (DESIGN.md 6.6). */
    public IDrawable coverageMap(Supplier<byte[]> map, Supplier<SignalLevel> cap) {
        return IDrawable.EMPTY;
    }

    /** The coverage map's colour key, Low up to {@code cap}. */
    public IDrawable coverageLegend(Supplier<SignalLevel> cap) {
        return IDrawable.EMPTY;
    }
}
