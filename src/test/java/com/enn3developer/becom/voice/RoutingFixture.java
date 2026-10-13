package com.enn3developer.becom.voice;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.enn3developer.gtnhvoice.api.server.IAudioFrame;
import com.enn3developer.gtnhvoice.api.server.PlayerSnapshot;
import com.enn3developer.gtnhvoice.api.server.group.IGroup;
import com.enn3developer.gtnhvoice.api.server.group.RoutingContext;
import com.enn3developer.gtnhvoice.core.encryption.aes.AesEncryption;
import com.enn3developer.gtnhvoice.core.proto.packets.udp.clientbound.SourceAudioPacket;
import com.enn3developer.gtnhvoice.server.VoiceServerSession;

/**
 * Hand-built sessions and positions for routing tests, run through GTNH Voice's own {@link RoutingContext} with a
 * capturing packet sender (CLAUDE.md: checking your work).
 * <p>
 * VOICE.md V4: the builder only takes GTNH Voice's internal session type, and the sender gets its internal packets, so
 * this fixture, and only this fixture, touches them. The frame is the API's own {@link IAudioFrame}.
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
        UUID id = playerWithoutUdpAddress(name, x, y, z, dimension);
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", nextPort++);
        sessions.get(id)
            .touch(address);
        byAddress.put(address, id);
        return id;
    }

    /** A player whose voice handshake is done but whose client hasn't sent its first datagram: sends to them drop. */
    UUID playerWithoutUdpAddress(String name, double x, double y, double z, int dimension) {
        UUID id = UUID.randomUUID();
        sessions.put(id, new VoiceServerSession(id, name, UUID.randomUUID(), new AesEncryption(new byte[32])));
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
            .audio(frame(activation))
            .group(group)
            .membershipTest((player, g) -> true)
            .groupIdResolver(g -> 0)
            .build();
        group.route(context);
    }

    /** One inbound frame sent under {@code activation}. GTNH Voice forwards the payload unread, so any bytes do. */
    private static IAudioFrame frame(UUID activation) {
        return new IAudioFrame() {

            @Override
            public long getSequenceNumber() {
                return 1L;
            }

            @Override
            public byte[] getData() {
                return new byte[] { 1, 2, 3 };
            }

            @Override
            public UUID getActivationId() {
                return activation;
            }
        };
    }

    List<UUID> recipients() {
        List<UUID> ids = new ArrayList<>();
        for (Sent s : sent) ids.add(s.recipient());
        return ids;
    }
}
