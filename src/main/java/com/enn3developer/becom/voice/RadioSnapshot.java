package com.enn3developer.becom.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the {@code becom:radio.N} groups route from (DESIGN.md 13.3), built on the server thread once a second and
 * handed to the network thread whole. Immutable once published.
 * <p>
 * For each player transmitting, it holds the channel they transmit on and every receiving device their signal reaches
 * at Low or better: walkie-talkie holders first, then Transceivers. The SNR rides along for the capture rule (D24),
 * the burst-loss chances for the dropout model (DESIGN.md 4.2).
 */
public final class RadioSnapshot {

    public static final RadioSnapshot EMPTY = new RadioSnapshot(Collections.emptyMap());

    /** A Transceiver, as a receiving device: where its loudspeaker plays from, and whether it's on. */
    public record Transceiver(int dimension, long pos, double x, double y, double z, boolean loudspeaker) {}

    /**
     * One receiving device of one transmission. Exactly one of {@code holder} (a walkie-talkie's holder) and
     * {@code transceiver} is set.
     */
    public record Link(UUID holder, Transceiver transceiver, double snr, double enterBad, double leaveBad) {

        public Object device() {
            return holder != null ? holder : transceiver;
        }
    }

    /** One player's transmission: the channel, then walkie links, then Transceiver links. */
    public record Transmission(int channel, Link[] walkies, Link[] transceivers) {}

    private final Map<UUID, Transmission> transmissions;

    private RadioSnapshot(Map<UUID, Transmission> transmissions) {
        this.transmissions = transmissions;
    }

    /** {@code speaker}'s transmission, or null when they have no working radio. */
    public Transmission transmission(UUID speaker) {
        return transmissions.get(speaker);
    }

    /** Whether {@code device} is still a receiver of {@code speaker}; used to drop stale per-pair state. */
    public boolean hasPair(UUID speaker, Object device) {
        Transmission t = transmissions.get(speaker);
        if (t == null) return false;
        for (Link link : t.walkies) if (link.device()
            .equals(device)) return true;
        for (Link link : t.transceivers) if (link.device()
            .equals(device)) return true;
        return false;
    }

    public int speakers() {
        return transmissions.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<UUID, Integer> channels = new HashMap<>();
        private final Map<UUID, List<Link>> walkies = new HashMap<>();
        private final Map<UUID, List<Link>> transceivers = new HashMap<>();

        /** Declares that {@code speaker} transmits on {@code channel}, even with nobody in reach. */
        public Builder transmitter(UUID speaker, int channel) {
            channels.put(speaker, channel);
            return this;
        }

        public Builder link(UUID speaker, Link link) {
            (link.holder() != null ? walkies : transceivers).computeIfAbsent(speaker, k -> new ArrayList<>())
                .add(link);
            return this;
        }

        public RadioSnapshot build() {
            Map<UUID, Transmission> frozen = new HashMap<>();
            for (Map.Entry<UUID, Integer> entry : channels.entrySet()) {
                UUID speaker = entry.getKey();
                frozen.put(
                    speaker,
                    new Transmission(
                        entry.getValue(),
                        walkies.getOrDefault(speaker, List.of())
                            .toArray(new Link[0]),
                        transceivers.getOrDefault(speaker, List.of())
                            .toArray(new Link[0])));
            }
            return new RadioSnapshot(Collections.unmodifiableMap(frozen));
        }
    }
}
