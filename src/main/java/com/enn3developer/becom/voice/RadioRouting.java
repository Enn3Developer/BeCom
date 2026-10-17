package com.enn3developer.becom.voice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

import com.enn3developer.becom.logic.DropoutModel;
import com.enn3developer.becom.logic.Positions;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.radio.Capture;
import com.enn3developer.gtnhvoice.api.server.PlayerSnapshot;

/**
 * State the sixteen radio groups share: the published snapshot, the capture holds, the burst-loss
 * chains, who is keyed, and the radio contacts {@code route()} noticed for the quests.
 * <p>
 * The network thread writes the concurrent maps from {@code route()}; the server thread publishes snapshots, reads
 * who is keyed, drains contacts and removes stale entries. Nothing here blocks.
 */
public final class RadioRouting {

    /** A speaker and someone BeCom carried their radio frame to. */
    public record Contact(UUID speaker, UUID listener) {}

    /** When a speaker's last radio frame went through, and on which channel. */
    public record Keyed(int channel, long nanos) {}

    final double directRange;
    final IntSupplier proximity;
    final double walkieSpeakerRange;
    final double loudspeakerRange;
    final DoubleSupplier random;
    final LongSupplier clock;
    // One speaker per receiver, twice over: each receiving device holds one speaker, and so does each player, whichever
    // devices their audio
    // reaches them through. Without the second, a player whose own walkie holds one speaker hears another through a
    // walkie or loudspeaker beside them.
    final Capture<Object> capture;
    final Capture<UUID> listeners;
    private volatile RadioSnapshot snapshot = RadioSnapshot.EMPTY;
    // Each speaker's burst-loss chains by receiving device, so a publish looks only at the speakers it changes.
    private final Map<UUID, Map<Object, DropoutModel.Chain>> chains = new ConcurrentHashMap<>();
    private final Map<UUID, Keyed> keyed = new ConcurrentHashMap<>();
    // When each contact was last reported. A pair is reported again after a while, so a contact made while its quest
    // was still locked isn't used up for good.
    private static final long REPORT_AGAIN_NANOS = 60_000_000_000L;
    private final Map<Contact, Long> reported = new ConcurrentHashMap<>();
    private final Queue<Contact> contacts = new ConcurrentLinkedQueue<>();

    /**
     * @param proximity GTNH Voice's proximity range in blocks, a cap on the direct range; called on the network thread
     * @param random    uniform numbers in [0, 1) for the dropout model; must be safe on the network thread
     * @param clock     nanoseconds, for the capture hold and who is keyed
     */
    public RadioRouting(double directRange, IntSupplier proximity, double walkieSpeakerRange, double loudspeakerRange,
        long holdNanos, double margin, DoubleSupplier random, LongSupplier clock) {
        this.directRange = directRange;
        this.proximity = proximity;
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

    /**
     * Server thread: replaces what the channels route from, and forgets the chains of pairs that are gone. A chain the
     * network thread makes from the snapshot before is kept until its speaker's transmission next changes.
     */
    public void publish(RadioSnapshot next) {
        RadioSnapshot before = snapshot;
        snapshot = next;
        // A transmission used as it was keeps the same receivers, whose chains stay: only a speaker gone or worked out
        // again is looked through, so publishing every tick doesn't look at every chain every tick.
        Iterator<Map.Entry<UUID, Map<Object, DropoutModel.Chain>>> it = chains.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Map<Object, DropoutModel.Chain>> entry = it.next();
            UUID speaker = entry.getKey();
            RadioSnapshot.Transmission now = next.transmission(speaker);
            if (now == null) it.remove();
            else if (now != before.transmission(speaker)) entry.getValue()
                .keySet()
                .retainAll(devices(now));
        }
    }

    /** The devices a transmission reaches. */
    private static Set<Object> devices(RadioSnapshot.Transmission transmission) {
        Set<Object> devices = new HashSet<>();
        for (RadioSnapshot.Link link : transmission.walkies()) devices.add(link.device());
        for (RadioSnapshot.Link link : transmission.transceivers()) devices.add(link.device());
        return devices;
    }

    public RadioSnapshot published() {
        return snapshot;
    }

    boolean drop(UUID speaker, RadioSnapshot.Link link) {
        // A chain added just as its speaker is forgotten goes with the map it was added to: the next frame starts anew.
        DropoutModel.Chain chain = chains.computeIfAbsent(speaker, s -> new ConcurrentHashMap<>())
            .computeIfAbsent(link.device(), d -> new DropoutModel.Chain());
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

    /** A device that could carry a speaker to a listener: its link from the speaker, and that link's level. */
    public record Carrier(Object device, double snr, SignalLevel level) {}

    /**
     * Server thread: for one transmission, the devices that could carry it to each listener, in the order
     * {@code route()} sends through them: their own walkie, then the walkies around them, then loudspeakers, each in
     * the snapshot's order. Found from each device out to the players around it, as route() does: looking at every
     * walkie for every listener grows with players squared, and so did a publish on a busy channel.
     *
     * @param positions every online player, by id
     * @param nearby    the same players, by cell
     * @param levelOf   the level a link's device hears the speaker at
     */
    public Map<UUID, List<Carrier>> carriers(UUID speaker, RadioSnapshot.Transmission tx,
        Map<UUID, PlayerSnapshot> positions, Nearby nearby, Function<RadioSnapshot.Link, SignalLevel> levelOf) {
        Map<UUID, List<Carrier>> carriers = new HashMap<>();
        for (RadioSnapshot.Link link : tx.walkies()) {
            if (link.holder()
                .equals(speaker) || Snapshots.of(positions, link.holder()) == null) continue;
            carry(carriers, link.holder(), link, levelOf);
        }
        for (RadioSnapshot.Link link : tx.walkies()) {
            PlayerSnapshot holder = Snapshots.of(positions, link.holder());
            if (holder == null) continue;
            for (UUID listener : around(
                nearby,
                speaker,
                holder.getDimensionId(),
                holder.getX(),
                holder.getY(),
                holder.getZ(),
                walkieSpeakerRange)) {
                if (!listener.equals(link.holder())) carry(carriers, listener, link, levelOf);
            }
        }
        for (RadioSnapshot.Link link : tx.transceivers()) {
            RadioSnapshot.Transceiver t = link.transceiver();
            if (!t.loudspeaker()) continue;
            for (UUID listener : around(nearby, speaker, t.dimension(), t.x(), t.y(), t.z(), loudspeakerRange)) {
                carry(carriers, listener, link, levelOf);
            }
        }
        return carriers;
    }

    /** The players within {@code range} of a device, but the speaker. */
    private static List<UUID> around(Nearby nearby, UUID speaker, int dimension, double x, double y, double z,
        double range) {
        List<UUID> found = new ArrayList<>();
        for (PlayerSnapshot p : nearby.around(dimension, x, z, range)) {
            if (Positions.within(p.getX() - x, p.getY() - y, p.getZ() - z, range) && !p.getUuid()
                .equals(speaker)) found.add(p.getUuid());
        }
        return found;
    }

    private static void carry(Map<UUID, List<Carrier>> carriers, UUID listener, RadioSnapshot.Link link,
        Function<RadioSnapshot.Link, SignalLevel> levelOf) {
        carriers.computeIfAbsent(listener, k -> new ArrayList<>())
            .add(new Carrier(link.device(), link.snr(), levelOf.apply(link)));
    }

    /**
     * Server thread: the level a listener hears {@code speaker} at, given the devices that could carry it to them in
     * the order {@code route()} sends through them (their own walkie, walkies around them, loudspeakers). route()
     * sends nothing through a device whose capture keeps another speaker, so the first device that would take this
     * speaker's frame is the one they hear. When every device is kept for now,
     * the first takes them once its hold runs out, which can come long before the next publish: its level.
     */
    public SignalLevel heardThrough(UUID speaker, int channel, List<Carrier> carriers) {
        if (carriers.isEmpty()) return SignalLevel.NONE;
        long now = clock.getAsLong();
        for (Carrier carrier : carriers) {
            if (capture.wouldAdmit(carrier.device(), channel, speaker, carrier.snr(), now)) return carrier.level();
        }
        return carriers.get(0)
            .level();
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
        chains.remove(player);
        for (Map<Object, DropoutModel.Chain> heard : chains.values()) heard.remove(player);
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
        int count = 0;
        for (Map<Object, DropoutModel.Chain> heard : chains.values()) count += heard.size();
        return count;
    }
}
