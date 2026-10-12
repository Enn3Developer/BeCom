package com.enn3developer.becom;

import net.minecraftforge.common.MinecraftForge;

import com.enn3developer.becom.block.BeComBlocks;
import com.enn3developer.becom.command.CommandBeCom;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.machine.BeComMachines;
import com.enn3developer.becom.packet.BeComPackets;
import com.enn3developer.becom.packet.LevelPacket;
import com.enn3developer.becom.packet.TonePacket;
import com.enn3developer.becom.packet.WireSyncPacket;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.recipe.BeComRecipes;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.ServerEvents;
import com.enn3developer.becom.server.VoiceSessions;
import com.enn3developer.gtnhvoice.api.server.GtnhVoiceApi;

import cpw.mods.fml.common.FMLCommonHandler;
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
        // Before worlds load, so each dimension's network loads with its world.
        BeComServer.create(event.getServer());
    }

    public void serverStarting(FMLServerStartingEvent event) {
        BeComServer.start();
        event.registerServerCommand(new CommandBeCom());
        // CI's run-server job greps for this line: FML silently skips mod jars it can't read.
        BeCom.LOG.info("Better Communications {} started", Tags.VERSION);
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
        BeComQuests.backend()
            .serverStopped();
    }

    // Client hooks for packets; the dedicated server has no use for them.

    public void onLevel(LevelPacket packet) {}

    public void onTone(TonePacket packet) {}

    public void onWires(WireSyncPacket packet) {}
}
