package com.enn3developer.becom.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the {@code becom:radio.N} groups route from (DESIGN.md 13.3), built on the server thread and handed to the
 * network thread whole. Immutable once published. A transmission worked out once is used as it is by the snapshots
 * after, until its radio is worked out again.
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

    /** A transmission on {@code channel} to {@code links}: walkie links and Transceiver links, each in their order. */
    public static Transmission transmission(int channel, List<Link> links) {
        List<Link> walkies = new ArrayList<>();
        List<Link> transceivers = new ArrayList<>();
        for (Link link : links) (link.holder() != null ? walkies : transceivers).add(link);
        return new Transmission(channel, walkies.toArray(new Link[0]), transceivers.toArray(new Link[0]));
    }

    private final Map<UUID, Transmission> transmissions;

    private RadioSnapshot(Map<UUID, Transmission> transmissions) {
        this.transmissions = transmissions;
    }

    /** {@code speaker}'s transmission, or null when they have no working radio. */
    public Transmission transmission(UUID speaker) {
        return transmissions.get(speaker);
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
        private final Map<UUID, Transmission> whole = new HashMap<>();

        /** {@code speaker}'s transmission as worked out before, used as it is; not for a speaker given links. */
        public Builder transmission(UUID speaker, Transmission transmission) {
            whole.put(speaker, transmission);
            return this;
        }

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
            Map<UUID, Transmission> frozen = new HashMap<>(whole);
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
