package com.enn3developer.becom.server;

import java.util.ArrayList;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StatCollector;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.data.GlobalSavedData;
import com.enn3developer.becom.logic.Sweep;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.voice.CallGroup;
import com.enn3developer.gtnhvoice.api.server.GtnhVoiceApi;

/**
 * Everything BeCom runs while a server is up, made at server start and dropped at server stop, so a singleplayer
 * world reload starts clean. Server thread only.
 */
public final class BeComServer {

    private static BeComServer instance;

    public final MinecraftServer server;
    public final Tunables tunables;
    public final NetworkService network;
    public final LinkService links;
    public final PhoneService phones;
    public final CallService calls;

    private BeComServer(MinecraftServer server, Tunables tunables) {
        this.server = server;
        this.tunables = tunables;
        this.network = new NetworkService(tunables);
        this.links = new LinkService(network, tunables);
        this.phones = new PhoneService();
        CallGroup callGroup = new CallGroup(
            StatCollector.translateToLocal("becom.group.call"),
            tunables.directHearingRange(),
            () -> java.util.concurrent.ThreadLocalRandom.current()
                .nextDouble());
        this.calls = new CallService(server, network, phones, callGroup, tunables);
    }

    /** The running server's BeCom, or null on a client or between servers. */
    public static BeComServer get() {
        return instance;
    }

    /** Makes BeCom's services for a server about to start, before its worlds load. */
    public static BeComServer create(MinecraftServer server) {
        instance = new BeComServer(server, BeComConfig.get());
        return instance;
    }

    /**
     * Starts BeCom for a server whose worlds have loaded (DESIGN.md 13.1): GTNH Voice forgets every group when its
     * server stops, so the groups are registered again on every start. Loading the phone registry here stops the
     * server early if it was saved by a newer BeCom (DESIGN.md 14.2).
     */
    public static void start() {
        if (instance == null) return;
        for (WorldServer world : DimensionManager.getWorlds()) instance.network.worldLoaded(world);
        instance.phones.attach(GlobalSavedData.get(DimensionManager.getWorld(0)));
        GtnhVoiceApi.groupManager()
            .registerGroup(instance.calls.group());
    }

    public static void stop() {
        if (instance != null) instance.calls.stop();
        instance = null;
    }

    public void tick() {
        network.tick();
        calls.tick();
        if (network.currentTick() % 20 == 0) links.expire();
        if (network.currentTick() % PHONE_CHECK_TICKS == 0) phoneCheck.add(
            new ArrayList<>(
                phones.registry()
                    .all()));
        phoneCheck.run(this::checkPhone, System::nanoTime, PHONE_CHECK_BUDGET_NANOS);
    }

    // Stale phone records are rare, so a check every minute in small slices is plenty.
    private static final long PHONE_CHECK_TICKS = 60 * 20;
    private static final long PHONE_CHECK_BUDGET_NANOS = 250_000;
    private final Sweep<PhoneRecord> phoneCheck = new Sweep<>();

    /** Frees the number of a Telephone that vanished without being broken, ending its call. */
    public void checkPhone(PhoneRecord record) {
        if (phones.record(record.number()) != record || !phones.isStale(record)) return;
        BeCom.LOG.info("Freeing phone number {}: its Telephone is gone", record.number());
        calls.phoneGone(record.number());
        phones.unregister(record.number());
    }
}
