package com.enn3developer.becom.server;

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

    private static BeComServer instance;

    public final MinecraftServer server;
    public final Tunables tunables;
    public final HeightCaches heights;
    public final AntennaNetwork antennas;
    public final NetworkService network;
    public final LinkService links;
    public final PhoneService phones;
    public final CallService calls;

    private BeComServer(MinecraftServer server, Tunables tunables) {
        this.server = server;
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
        if (instance != null) {
            instance.calls.stop();
            instance.heights.saveAll();
        }
        instance = null;
    }

    public void tick() {
        heights.tick();
        // DESIGN.md 6.5: surveys get at most the survey budget per tick. Line re-checks walk the world too, so they
        // share it: up to half for them, the rest for surveys.
        long budget = (long) (tunables.surveyBudgetMillis() * 1_000_000);
        long start = System.nanoTime();
        network.tick(budget / 2);
        antennas.tick(Math.max(0, budget - (System.nanoTime() - start)), System::nanoTime);
        calls.tick();
        if (network.currentTick() % 20 == 0) links.expire();
    }
}
