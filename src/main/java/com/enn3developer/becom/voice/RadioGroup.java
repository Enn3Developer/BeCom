package com.enn3developer.becom.voice;

import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import com.enn3developer.gtnhvoice.api.server.ActivationIds;
import com.enn3developer.gtnhvoice.api.server.IVoiceSession;
import com.enn3developer.gtnhvoice.api.server.PlayerSnapshot;
import com.enn3developer.gtnhvoice.api.server.SourceState;
import com.enn3developer.gtnhvoice.api.server.group.IGroup;
import com.enn3developer.gtnhvoice.api.server.group.RoutingContext;

/**
 * {@code becom:radio.N} (DESIGN.md 8.1, 13.1, 13.3): one radio channel. Only frames sent while the speaker holds
 * push-to-talk count. Each receiving device the speaker reaches at Low or better gets the frame if it passes the
 * capture rule (D24) and the dropout model. Then, in this order, since GTNH Voice keeps the first send per player:
 * flat to walkie holders, positional from each walkie that got it to players within 6 blocks of its holder (D21),
 * positional from each Transceiver that got it to players within its loudspeaker range. Whoever hears the speaker
 * directly (D17) gets nothing from BeCom, and each player hears one speaker at a time, whichever device it comes
 * through (D24).
 * <p>
 * {@link #route} runs on GTNH Voice's network thread and reads only the snapshot, the routing context and the shared
 * concurrent state in {@link RadioRouting}.
 */
public final class RadioGroup implements IGroup {

    public static final String PREFIX = "becom:radio.";
    public static final int PRIORITY = 100;
    public static final int CHANNELS = 16;
    public static final UUID ACTIVATION = ActivationIds.of("BeCom", "radio");

    private final int channel;
    private final String name;
    private final String displayName;
    private final RadioRouting shared;

    public RadioGroup(int channel, String displayName, RadioRouting shared) {
        this.channel = channel;
        this.name = PREFIX + channel;
        this.displayName = displayName;
        this.shared = shared;
    }

    public static String nameOf(int channel) {
        return PREFIX + channel;
    }

    public int channel() {
        return channel;
    }

    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public @NotNull String getDisplayName() {
        return displayName;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public void route(@NotNull RoutingContext context) {
        if (!ACTIVATION.equals(
            context.getAudio()
                .getActivationId()))
            return;
        UUID speaker = context.getSpeakerSession()
            .getPlayerUuid();
        long now = shared.clock.getAsLong();
        shared.keyed(speaker, channel, now);
        RadioSnapshot.Transmission tx = shared.snapshot()
            .transmission(speaker);
        if (tx == null || tx.channel() != channel) return;

        Map<UUID, PlayerSnapshot> positions = context.getPositionSnapshot();
        Map<UUID, IVoiceSession> sessions = context.getSessionsByPlayerUuid();
        PlayerSnapshot from = positions.get(speaker);
        double direct = DirectHearing.range(shared.directRange);
        double fx = from == null ? 0 : from.getX();
        double fy = from == null ? 0 : from.getY();
        double fz = from == null ? 0 : from.getZ();

        // A device holds the speaker even through a burst of dropped frames, and so do the players hearing them on
        // it: otherwise a burst would hand them to the next speaker with no margin (D24). Only the send is skipped.
        boolean[] walkieHeld = new boolean[tx.walkies().length];
        boolean[] walkieGot = receive(speaker, tx.walkies(), now, walkieHeld);
        boolean[] transceiverHeld = new boolean[tx.transceivers().length];
        boolean[] transceiverGot = receive(speaker, tx.transceivers(), now, transceiverHeld);

        for (int i = 0; i < tx.walkies().length; i++) {
            if (!walkieHeld[i]) continue;
            RadioSnapshot.Link link = tx.walkies()[i];
            UUID holder = link.holder();
            if (holder.equals(speaker)) continue;
            // Without voice they hear nothing, so nobody holds them.
            IVoiceSession session = sessions.get(holder);
            if (session == null) continue;
            // Hearing the speaker in person (D17) counts as hearing them, for the hold, though BeCom sends nothing.
            if (!shared.listeners.admit(holder, channel, speaker, link.snr(), now)) continue;
            if (!walkieGot[i] || DirectHearing.hears(from, positions.get(holder), direct)) continue;
            context.sendTo(session, SourceState.FLAT, fx, fy, fz);
            // VOICE.md V5: GTNH Voice doesn't say whether this send went through or a higher group had served them.
            shared.contact(speaker, holder, now);
        }
        for (int i = 0; i < tx.walkies().length; i++) {
            if (!walkieHeld[i]) continue;
            UUID holder = tx.walkies()[i].holder();
            PlayerSnapshot at = positions.get(holder);
            if (at == null) continue;
            playAround(
                context,
                speaker,
                from,
                direct,
                holder,
                at.getDimensionId(),
                at.getX(),
                at.getY(),
                at.getZ(),
                shared.walkieSpeakerRange,
                tx.walkies()[i].snr(),
                now,
                walkieGot[i]);
        }
        for (int i = 0; i < tx.transceivers().length; i++) {
            RadioSnapshot.Transceiver t = tx.transceivers()[i].transceiver();
            if (!transceiverHeld[i] || !t.loudspeaker()) continue;
            playAround(
                context,
                speaker,
                from,
                direct,
                null,
                t.dimension(),
                t.x(),
                t.y(),
                t.z(),
                shared.loudspeakerRange,
                tx.transceivers()[i].snr(),
                now,
                transceiverGot[i]);
        }
    }

    /**
     * Runs each receiving device through capture (D24) and then the dropout model: {@code held[i]} says the device
     * holds this speaker, the result whether this frame got through to it.
     */
    private boolean[] receive(UUID speaker, RadioSnapshot.Link[] links, long now, boolean[] held) {
        boolean[] got = new boolean[links.length];
        for (int i = 0; i < links.length; i++) {
            RadioSnapshot.Link link = links[i];
            held[i] = shared.capture.admit(link.device(), channel, speaker, link.snr(), now);
            got[i] = held[i] && !shared.drop(speaker, link);
        }
        return got;
    }

    /**
     * Positional from a device to the players around it, minus the speaker, its holder, direct hearers and anyone
     * already held by another speaker (D24). Players around a device holding the speaker keep hearing them for the
     * hold even when this frame didn't get through ({@code send} false), or they hear the speaker in person. VOICE.md
     * V3: GTNH Voice plays it with its own falloff, so BeCom can only stop sending past the device's range.
     */
    private void playAround(RoutingContext context, UUID speaker, PlayerSnapshot from, double direct, UUID holder,
        int dimension, double x, double y, double z, double range, double snr, long now, boolean send) {
        // VOICE.md V3
        double rangeSq = range * range;
        Map<UUID, IVoiceSession> sessions = context.getSessionsByPlayerUuid();
        for (PlayerSnapshot p : context.getPositionSnapshot()
            .values()) {
            if (p.getDimensionId() != dimension) continue;
            UUID id = p.getUuid();
            if (id.equals(speaker) || id.equals(holder)) continue;
            double dx = p.getX() - x;
            double dy = p.getY() - y;
            double dz = p.getZ() - z;
            if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
            IVoiceSession session = sessions.get(id);
            if (session == null) continue;
            if (!shared.listeners.admit(id, channel, speaker, snr, now)) continue;
            if (!send || DirectHearing.hears(from, p, direct)) continue;
            context.sendTo(session, SourceState.POSITIONAL, x, y, z);
            // VOICE.md V5
            shared.contact(speaker, id, now);
        }
    }

    @Override
    public void onPlayerRemoved(@NotNull UUID playerUuid) {
        shared.forget(playerUuid);
    }

    @Override
    public void clear() {
        shared.clear();
    }
}
