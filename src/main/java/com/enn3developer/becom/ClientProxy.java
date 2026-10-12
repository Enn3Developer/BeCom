package com.enn3developer.becom;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraftforge.common.MinecraftForge;

import com.enn3developer.becom.block.BlockPhoneCable;
import com.enn3developer.becom.block.BlockTelephone;
import com.enn3developer.becom.client.ClientAudio;
import com.enn3developer.becom.client.ClientLevels;
import com.enn3developer.becom.client.ClientWires;
import com.enn3developer.becom.client.PhoneCableRenderer;
import com.enn3developer.becom.client.TelephoneRenderer;
import com.enn3developer.becom.client.ToneSounds;
import com.enn3developer.becom.client.WireRenderer;
import com.enn3developer.becom.packet.LevelPacket;
import com.enn3developer.becom.packet.TonePacket;
import com.enn3developer.becom.packet.WireSyncPacket;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

public class ClientProxy extends CommonProxy {

    // Packet handlers run on the network thread; anything touching the game waits here for the client tick.
    private final Queue<Runnable> mainThread = new ConcurrentLinkedQueue<>();

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        BlockTelephone.renderId = RenderingRegistry.getNextAvailableRenderId();
        BlockPhoneCable.renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new TelephoneRenderer(BlockTelephone.renderId));
        RenderingRegistry.registerBlockHandler(new PhoneCableRenderer(BlockPhoneCable.renderId));
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(new WireRenderer());
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        ClientAudio.register();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Runnable task;
        while ((task = mainThread.poll()) != null) task.run();
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        mainThread.add(() -> {
            ClientLevels.clear();
            ClientWires.clear();
            ToneSounds.stop();
        });
    }

    @Override
    public void onLevel(LevelPacket packet) {
        // The level table is concurrent and read on decode threads, so it's set right away.
        ClientLevels.set(packet.source(), packet.group(), packet.level());
    }

    @Override
    public void onTone(TonePacket packet) {
        mainThread.add(() -> ToneSounds.play(packet.tone()));
    }

    @Override
    public void onWires(WireSyncPacket packet) {
        mainThread.add(() -> ClientWires.set(packet.dimension(), packet.wires()));
    }
}
