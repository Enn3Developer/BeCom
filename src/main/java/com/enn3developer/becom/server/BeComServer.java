package com.enn3developer.becom.server;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StatCollector;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.data.GlobalSavedData;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.radio.AntennaNetwork;
import com.enn3developer.becom.logic.radio.Terrain;
import com.enn3developer.becom.voice.CallGroup;
import com.enn3developer.gtnhvoice.api.server.GtnhVoiceApi;

/**
 * Everything BeCom runs while a server is up, made at server start and dropped at server stop, so a singleplayer
 * world reload starts clean. Server thread only.
 */
public final class BeComServer {

    // Volatile and changed under the class lock: in single-player, a world's server thread can start while the last
    // one's is still stopping.
    private static volatile BeComServer instance;
    // The server between its stopping and stopped events.
    private static BeComServer stopping;
    // Work handed over from other threads, run at the start of the next server tick.
    private static final Queue<Runnable> LATER = new ConcurrentLinkedQueue<>();

    public final MinecraftServer server;
    // The server's own thread: Minecraft fires every lifecycle event of a server on it, so an event on another thread
    // belongs to another server.
    private final Thread thread;
    public final Tunables tunables;
    public final HeightCaches heights;
    public final AntennaNetwork antennas;
    public final NetworkService network;
    public final LinkService links;
    public final PhoneService phones;
    public final CallService calls;
    public final RadioService radio;

    private BeComServer(MinecraftServer server, Tunables tunables) {
        this.server = server;
        this.thread = Thread.currentThread();
        this.tunables = tunables;
        this.heights = new HeightCaches();
        this.antennas = new AntennaNetwork(tunables, new AntennaNetwork.Dimensions() {

            @Override
            public Terrain terrain(int dimension) {
                return heights.terrain(dimension);
            }

            @Override
            public boolean noSky(int dimension) {
                WorldServer world = DimensionManager.getWorld(dimension);
                return world != null && world.provider.hasNoSky;
            }
        }, Ownership::sameTeam);
        this.network = new NetworkService(tunables, antennas);
        this.links = new LinkService(network, tunables);
        this.phones = new PhoneService();
        CallGroup callGroup = new CallGroup(
            StatCollector.translateToLocal("becom.group.call"),
            tunables.directHearingRange(),
            () -> java.util.concurrent.ThreadLocalRandom.current()
                .nextDouble());
        this.calls = new CallService(server, network, phones, callGroup, tunables);
        this.radio = new RadioService(server, tunables, antennas, heights);
    }

    /** The running server's BeCom, or null on a client or between servers. */
    public static BeComServer get() {
        return instance;
    }

    /** This thread's server, or null when the current one belongs to another thread. */
    private static BeComServer mine() {
        BeComServer current = instance;
        return current != null && current.thread == Thread.currentThread() ? current : null;
    }

    /** Makes BeCom's services for a server about to start, before its worlds load, on its server thread. */
    public static synchronized BeComServer create(MinecraftServer server) {
        instance = new BeComServer(server, BeComConfig.get());
        return instance;
    }

    /**
     * Starts BeCom for a server whose worlds have loaded (DESIGN.md 13.1): GTNH Voice forgets every group when its
     * server stops, so the groups are registered again on every start. Loading the phone registry here stops the
     * server early if it was saved by a newer BeCom (DESIGN.md 14.2).
     */
    public static void start() {
        BeComServer becom = mine();
        if (becom == null) return;
        for (WorldServer world : DimensionManager.getWorlds()) becom.network.worldLoaded(world);
        becom.phones.attach(GlobalSavedData.get(DimensionManager.getWorld(0)));
        GtnhVoiceApi.groupManager()
            .registerGroup(becom.calls.group());
        becom.radio.start();
    }

    /**
     * The server is stopping: calls end and radios go quiet while the worlds are still loaded. The services stay
     * until {@link #stopped}, so the height cache follows the chunks the final world save writes.
     */
    public static synchronized void stop() {
        BeComServer becom = mine();
        if (becom == null) return;
        LATER.clear();
        stopping = becom;
        becom.calls.stop();
        becom.radio.stop();
    }

    /**
     * The worlds are saved and unloaded: the height cache's last regions go to disk, and BeCom forgets the server. In
     * single-player the next world can start while the last one's stopped handlers still run, and a world refused at
     * start gets no stopping event, so only this thread's server is forgotten, never a newer one.
     * {@code whenForgotten} runs under the same lock once it is, so the next server's start comes after it: for
     * state shared between servers.
     */
    public static synchronized void stopped(Runnable whenForgotten) {
        BeComServer old = stopping != null && stopping.thread == Thread.currentThread() ? stopping : mine();
        if (old == stopping) stopping = null;
        if (old == null) return;
        old.heights.saveAll();
        if (instance != old) return;
        instance = null;
        whenForgotten.run();
    }

    /** Runs {@code task} on the server thread at the start of the next tick; dropped if no server is running. */
    public static void later(Runnable task) {
        LATER.add(task);
    }

    public void tick() {
        Runnable task;
        while ((task = LATER.poll()) != null) task.run();
        heights.tick();
        // DESIGN.md 6.5: surveys get at most the survey budget per tick. Line re-checks walk the world too, so they
        // share it: up to half for them, the rest for surveys.
        long budget = (long) (tunables.surveyBudgetMillis() * 1_000_000);
        long start = System.nanoTime();
        network.tick(budget / 2);
        antennas.tick(Math.max(0, budget - (System.nanoTime() - start)), System::nanoTime);
        calls.tick();
        radio.tick();
        if (network.currentTick() % 20 == 0) links.expire();
    }
}
