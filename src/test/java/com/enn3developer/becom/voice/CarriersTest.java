package com.enn3developer.becom.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.gtnhvoice.api.server.PlayerSnapshot;

/**
 * A radio publish tells each listener the level they hear each speaker at, through the devices route() would send
 * through. Found from the devices out, those devices and their order must be what a look at every
 * walkie and loudspeaker for every listener finds.
 */
class CarriersTest {

    private static final double WALKIE_RANGE = 6, LOUDSPEAKER_RANGE = 16;
    private static final SignalLevel[] LEVELS = SignalLevel.values();

    private final RadioRouting routing = new RadioRouting(
        16,
        () -> 48,
        WALKIE_RANGE,
        LOUDSPEAKER_RANGE,
        100_000_000L,
        3,
        () -> 0.5,
        () -> 0);

    /** Every listener but the speaker, each device in turn: what sendLevels did before. */
    private static Map<UUID, List<RadioRouting.Carrier>> everyListener(UUID speaker, RadioSnapshot.Transmission tx,
        List<PlayerSnapshot> online, Function<RadioSnapshot.Link, SignalLevel> levelOf) {
        Map<UUID, PlayerSnapshot> byId = new HashMap<>();
        for (PlayerSnapshot p : online) byId.put(p.getUuid(), p);
        Map<UUID, List<RadioRouting.Carrier>> found = new HashMap<>();
        for (PlayerSnapshot listener : online) {
            UUID id = listener.getUuid();
            if (id.equals(speaker)) continue;
            List<RadioRouting.Carrier> carriers = new ArrayList<>();
            for (RadioSnapshot.Link link : tx.walkies()) {
                if (link.holder()
                    .equals(id)) carriers.add(new RadioRouting.Carrier(link.device(), link.snr(), levelOf.apply(link)));
            }
            for (RadioSnapshot.Link link : tx.walkies()) {
                PlayerSnapshot holder = byId.get(link.holder());
                if (holder != null && holder != listener
                    && holder.getDimensionId() == listener.getDimensionId()
                    && distanceSq(holder, listener.getX(), listener.getY(), listener.getZ())
                        <= WALKIE_RANGE * WALKIE_RANGE) {
                    carriers.add(new RadioRouting.Carrier(link.device(), link.snr(), levelOf.apply(link)));
                }
            }
            for (RadioSnapshot.Link link : tx.transceivers()) {
                RadioSnapshot.Transceiver t = link.transceiver();
                if (t.loudspeaker() && t.dimension() == listener.getDimensionId()
                    && distanceSq(listener, t.x(), t.y(), t.z()) <= LOUDSPEAKER_RANGE * LOUDSPEAKER_RANGE) {
                    carriers.add(new RadioRouting.Carrier(link.device(), link.snr(), levelOf.apply(link)));
                }
            }
            if (!carriers.isEmpty()) found.put(id, carriers);
        }
        return found;
    }

    private static double distanceSq(PlayerSnapshot p, double x, double y, double z) {
        double dx = p.getX() - x, dy = p.getY() - y, dz = p.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Test
    void theDevicesAroundEachListenerAreFoundInRouteOrder() {
        Random random = new Random(42);
        for (int round = 0; round < 50; round++) {
            // Crowded enough that walkies and loudspeakers overlap, across two dimensions and below zero.
            List<PlayerSnapshot> online = new ArrayList<>();
            for (int i = 0; i < 120; i++) {
                online.add(
                    new PlayerSnapshot(
                        new UUID(round, i),
                        "p" + i,
                        random.nextDouble() * 96 - 48,
                        60 + random.nextDouble() * 8,
                        random.nextDouble() * 96 - 48,
                        random.nextInt(2)));
            }
            UUID speaker = online.get(0)
                .getUuid();
            RadioSnapshot.Builder builder = RadioSnapshot.builder()
                .transmitter(speaker, 1);
            for (PlayerSnapshot p : online.subList(1, online.size())) {
                if (random.nextDouble() < 0.6) {
                    builder.link(speaker, new RadioSnapshot.Link(p.getUuid(), null, random.nextDouble() * 30, 0, 1));
                }
            }
            // A walkie held by someone offline carries to nobody, and a loudspeaker that's off plays to nobody.
            builder.link(speaker, new RadioSnapshot.Link(new UUID(-1, round), null, 12, 0, 1));
            for (int i = 0; i < 6; i++) {
                RadioSnapshot.Transceiver t = new RadioSnapshot.Transceiver(
                    random.nextInt(2),
                    i,
                    random.nextDouble() * 96 - 48,
                    64,
                    random.nextDouble() * 96 - 48,
                    i != 3);
                builder.link(speaker, new RadioSnapshot.Link(null, t, random.nextDouble() * 30, 0, 1));
            }
            RadioSnapshot.Transmission tx = builder.build()
                .transmission(speaker);
            Function<RadioSnapshot.Link, SignalLevel> levelOf = link -> LEVELS[Math.floorMod(
                link.device()
                    .hashCode(),
                LEVELS.length)];
            Map<UUID, PlayerSnapshot> positions = new HashMap<>();
            for (PlayerSnapshot p : online) positions.put(p.getUuid(), p);

            Map<UUID, List<RadioRouting.Carrier>> found = routing
                .carriers(speaker, tx, positions, new Nearby(online), levelOf);
            assertEquals(everyListener(speaker, tx, online, levelOf), found, "round " + round);
        }
    }
}
