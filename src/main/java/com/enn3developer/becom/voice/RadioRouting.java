package com.enn3developer.becom.voice;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

import com.enn3developer.becom.logic.DropoutModel;
import com.enn3developer.becom.logic.radio.Capture;

/**
 * State the sixteen radio groups share (DESIGN.md 13.3): the published snapshot, the capture holds, the burst-loss
 * chains, who is keyed, and the radio contacts {@code route()} noticed for the quests (DESIGN.md 15.5).
 * <p>
 * The network thread writes the concurrent maps from {@code route()}; the server thread publishes snapshots, reads
 * who is keyed, drains contacts and removes stale entries. Nothing here blocks.
 */
public final class RadioRouting {

    /** A speaker and someone BeCom carried their radio frame to. */
    public record Contact(UUID speaker, UUID listener) {}

    /** When a speaker's last radio frame went through, and on which channel. */
    public record Keyed(int channel, long nanos) {}

    private record Pair(UUID speaker, Object device) {}

    final double directRange;
    final double walkieSpeakerRange;
    final double loudspeakerRange;
    final DoubleSupplier random;
    final LongSupplier clock;
    // D24 twice over: each receiving device holds one speaker, and so does each player, whichever devices their audio
    // reaches them through. Without the second, a player whose own walkie holds one speaker hears another through a
    // walkie or loudspeaker beside them.
    final Capture<Object> capture;
    final Capture<UUID> listeners;
    private volatile RadioSnapshot snapshot = RadioSnapshot.EMPTY;
    private final Map<Pair, DropoutModel.Chain> chains = new ConcurrentHashMap<>();
    private final Map<UUID, Keyed> keyed = new ConcurrentHashMap<>();
    // When each contact was last reported. A pair is reported again after a while, so a contact made while its quest
    // was still locked isn't used up for good.
    private static final long REPORT_AGAIN_NANOS = 60_000_000_000L;
    private final Map<Contact, Long> reported = new ConcurrentHashMap<>();
    private final Queue<Contact> contacts = new ConcurrentLinkedQueue<>();

    /**
     * @param random uniform numbers in [0, 1) for the dropout model; must be safe on the network thread
     * @param clock  nanoseconds, for the capture hold and who is keyed
     */
    public RadioRouting(double directRange, double walkieSpeakerRange, double loudspeakerRange, long holdNanos,
        double margin, DoubleSupplier random, LongSupplier clock) {
        this.directRange = directRange;
        this.walkieSpeakerRange = walkieSpeakerRange;
        this.loudspeakerRange = loudspeakerRange;
        this.random = random;
        this.clock = clock;
        this.capture = new Capture<>(holdNanos, margin);
        this.listeners = new Capture<>(holdNanos, margin);
    }

    RadioSnapshot snapshot() {
        return snapshot;
    }

    /** Server thread: replaces what the channels route from, and forgets chains of pairs that are gone. */
    public void publish(RadioSnapshot next) {
        snapshot = next;
        chains.keySet()
            .removeIf(pair -> !next.hasPair(pair.speaker, pair.device));
    }

    public RadioSnapshot published() {
        return snapshot;
    }

    boolean drop(UUID speaker, RadioSnapshot.Link link) {
        DropoutModel.Chain chain = chains
            .computeIfAbsent(new Pair(speaker, link.device()), p -> new DropoutModel.Chain());
        return chain.drop(link.enterBad(), link.leaveBad(), random.getAsDouble());
    }

    /** {@code route()} marks a speaker keyed with each frame that goes through; the self-test stands in for a frame. */
    public void keyed(UUID speaker, int channel, long nanos) {
        keyed.put(speaker, new Keyed(channel, nanos));
    }

    void contact(UUID speaker, UUID listener, long nanos) {
        Contact contact = new Contact(speaker, listener);
        Long last = reported.get(contact);
        if (last != null && nanos - last < REPORT_AGAIN_NANOS) return;
        reported.put(contact, nanos);
        contacts.add(contact);
    }

    /**
     * Server thread: whether {@code player} was last hearing {@code speaker} on {@code channel}, through any device.
     */
    public boolean listenerHeldBy(UUID player, int channel, UUID speaker) {
        return speaker.equals(listeners.holder(player, channel));
    }

    /** Server thread: whether a receiving device's capture is held by {@code speaker} on {@code channel}. */
    public boolean deviceHeldBy(Object device, int channel, UUID speaker) {
        return speaker.equals(capture.holder(device, channel));
    }

    /** Server thread: who has been keyed, and when their last frame went through. */
    public Map<UUID, Keyed> keyedSpeakers() {
        return keyed;
    }

    /** Server thread: the next radio contact to complete quests for, or null. */
    public Contact pollContact() {
        return contacts.poll();
    }

    /** Server thread: forgets keyed speakers silent for longer than {@code maxNanos}, and long-gone capture holds. */
    public void sweep(long nowNanos, long maxNanos) {
        keyed.values()
            .removeIf(k -> nowNanos - k.nanos() > maxNanos);
        capture.sweep(nowNanos);
        listeners.sweep(nowNanos);
        reported.values()
            .removeIf(last -> nowNanos - last >= REPORT_AGAIN_NANOS);
    }

    void forget(UUID player) {
        capture.forgetListener(player);
        listeners.forgetListener(player);
        chains.keySet()
            .removeIf(pair -> pair.speaker.equals(player) || player.equals(pair.device));
        keyed.remove(player);
        reported.keySet()
            .removeIf(
                c -> c.speaker()
                    .equals(player)
                    || c.listener()
                        .equals(player));
    }

    public void clear() {
        snapshot = RadioSnapshot.EMPTY;
        chains.clear();
        keyed.clear();
        reported.clear();
        contacts.clear();
        capture.clear();
        listeners.clear();
    }

    public int chainCount() {
        return chains.size();
    }
}
