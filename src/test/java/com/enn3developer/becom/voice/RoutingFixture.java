package com.enn3developer.becom.voice;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.enn3developer.gtnhvoice.api.server.PlayerSnapshot;
import com.enn3developer.gtnhvoice.api.server.group.IGroup;
import com.enn3developer.gtnhvoice.api.server.group.RoutingContext;
import com.enn3developer.gtnhvoice.core.encryption.aes.AesEncryption;
import com.enn3developer.gtnhvoice.core.proto.packets.udp.clientbound.SourceAudioPacket;
import com.enn3developer.gtnhvoice.core.proto.packets.udp.serverbound.PlayerAudioPacket;
import com.enn3developer.gtnhvoice.server.VoiceServerSession;

/**
 * Hand-built sessions and positions for routing tests, run through GTNH Voice's own {@link RoutingContext} with a
 * capturing packet sender (CLAUDE.md: checking your work).
 * <p>
 * VOICE.md V4: the builder only takes GTNH Voice's internal session and packet types, so this fixture, and only this
 * fixture, builds them.
 */
final class RoutingFixture {

    /** One packet a group sent: to whom, and the forwarded audio packet with its state and source position. */
    record Sent(UUID recipient, SourceAudioPacket packet) {}

    final Map<UUID, VoiceServerSession> sessions = new HashMap<>();
    final Map<UUID, PlayerSnapshot> positions = new HashMap<>();
    final Map<InetSocketAddress, UUID> byAddress = new HashMap<>();
    final List<Sent> sent = new ArrayList<>();
    private int nextPort = 40_000;

    UUID player(String name, double x, double y, double z, int dimension) {
        UUID id = UUID.randomUUID();
        VoiceServerSession session = new VoiceServerSession(
            id,
            name,
            UUID.randomUUID(),
            new AesEncryption(new byte[32]));
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", nextPort++);
        session.touch(address);
        sessions.put(id, session);
        byAddress.put(address, id);
        positions.put(id, new PlayerSnapshot(id, name, x, y, z, dimension));
        return id;
    }

    /** Routes one frame from {@code speaker} through {@code group} alone. */
    void route(IGroup group, UUID speaker, UUID activation) {
        RoutingContext context = RoutingContext.builder()
            .packetSender(
                (packet, sessionId, encryption, recipient) -> sent
                    .add(new Sent(byAddress.get(recipient), (SourceAudioPacket) packet)))
            .positionSnapshot(positions)
            .sessions(sessions)
            .speakerSession(sessions.get(speaker))
            .audio(new PlayerAudioPacket(1L, new byte[] { 1, 2, 3 }, activation, (short) 0, false))
            .group(group)
            .membershipTest((player, g) -> true)
            .groupIdResolver(g -> 0)
            .build();
        group.route(context);
    }

    List<UUID> recipients() {
        List<UUID> ids = new ArrayList<>();
        for (Sent s : sent) ids.add(s.recipient());
        return ids;
    }
}
