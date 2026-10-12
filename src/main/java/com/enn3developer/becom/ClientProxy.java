package com.enn3developer.becom;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.enn3developer.becom.block.BlockPhoneCable;
import com.enn3developer.becom.block.BlockTelephone;
import com.enn3developer.becom.client.AnalyzerOverlay;
import com.enn3developer.becom.client.ClientAudio;
import com.enn3developer.becom.client.ClientLevels;
import com.enn3developer.becom.client.ClientPhone;
import com.enn3developer.becom.client.ClientRadio;
import com.enn3developer.becom.client.ClientWires;
import com.enn3developer.becom.client.MachineTooltips;
import com.enn3developer.becom.client.PhoneCableRenderer;
import com.enn3developer.becom.client.SignalBars;
import com.enn3developer.becom.client.TelephoneRenderer;
import com.enn3developer.becom.client.ToneSounds;
import com.enn3developer.becom.client.WireRenderer;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.packet.AnalyzerPacket;
import com.enn3developer.becom.packet.ConfigSyncPacket;
import com.enn3developer.becom.packet.LevelPacket;
import com.enn3developer.becom.packet.PhoneStatusPacket;
import com.enn3developer.becom.packet.RingPacket;
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
        MinecraftForge.EVENT_BUS.register(new SignalBars());
        MinecraftForge.EVENT_BUS.register(new AnalyzerOverlay());
        MinecraftForge.EVENT_BUS.register(new ClientRadio());
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
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Runnable task;
        while ((task = mainThread.poll()) != null) task.run();
        ClientRadio.tick();
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
    public void onTone(TonePacket packet) {
        mainThread.add(() -> ToneSounds.play(packet.tone()));
    }

    @Override
    public IDrawable signalBars(Supplier<SignalLevel> level) {
        return (context, x, y, width, height, theme) -> SignalBars.draw(x, y + height, level.get());
    }

    @Override
    public void onWires(WireSyncPacket packet) {
        mainThread.add(() -> ClientWires.set(packet.dimension(), packet.wires()));
    }
}
