package com.enn3developer.becom.voice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.enn3developer.gtnhvoice.api.server.IVoiceSession;
import com.enn3developer.gtnhvoice.api.server.PlayerSnapshot;
import com.enn3developer.gtnhvoice.api.server.group.IGroup;
import com.enn3developer.gtnhvoice.api.server.group.RoutingTestKit;

/**
 * Hand-built sessions and positions for routing tests, routed by GTNH Voice's own {@link RoutingTestKit} the way its
 * server routes (CLAUDE.md: checking your work): groups in priority order on one context, one packet per player per
 * frame, and the same sends dropped.
 */
final class RoutingFixture {

    /** One packet a group sent: to whom, and how it plays, from where, whose voice and through which group. */
    record Sent(UUID recipient, RoutingTestKit.Delivery delivery) {}

    private final RoutingTestKit kit = new RoutingTestKit();
    private final Map<UUID, IVoiceSession> sessions = new HashMap<>();
    private final Map<UUID, PlayerSnapshot> positions = new HashMap<>();
    final List<Sent> sent = new ArrayList<>();
    private long sequence;

    UUID player(String name, double x, double y, double z, int dimension) {
        UUID id = playerWithoutVoice(name, x, y, z, dimension);
        sessions.put(id, kit.session(id, name));
        return id;
    }

    /** A player whose voice handshake is done but whose client hasn't sent its first datagram: sends to them drop. */
    UUID playerWithoutUdpAddress(String name, double x, double y, double z, int dimension) {
        UUID id = playerWithoutVoice(name, x, y, z, dimension);
        sessions.put(id, kit.sessionWithoutUdpAddress(id, name));
        return id;
    }

    /** A player in the world with no voice session at all. */
    UUID playerWithoutVoice(String name, double x, double y, double z, int dimension) {
        UUID id = UUID.randomUUID();
        positions.put(id, new PlayerSnapshot(id, name, x, y, z, dimension));
        return id;
    }

    /** Routes one frame from {@code speaker} through {@code group} alone. */
    void route(IGroup group, UUID speaker, UUID activation) {
        route(speaker, activation, group);
    }

    /**
     * Routes one frame from {@code speaker} through {@code groups}, highest priority first, everyone in all of them.
     */
    void route(UUID speaker, UUID activation, IGroup... groups) {
        List<RoutingTestKit.Delivery> deliveries = kit.route(
            sessions.get(speaker),
            RoutingTestKit.frame(++sequence, activation),
            positions,
            (player, group) -> true,
            groups);
        for (RoutingTestKit.Delivery delivery : deliveries) {
            sent.add(
                new Sent(
                    delivery.getRecipient()
                        .getPlayerUuid(),
                    delivery));
        }
    }

    List<UUID> recipients() {
        List<UUID> ids = new ArrayList<>();
        for (Sent s : sent) ids.add(s.recipient());
        return ids;
    }
}
