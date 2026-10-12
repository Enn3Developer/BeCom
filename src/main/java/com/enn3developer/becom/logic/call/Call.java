package com.enn3developer.becom.logic.call;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.enn3developer.becom.logic.DropTimer;
import com.enn3developer.becom.logic.SignalLevel;

/**
 * One call (DESIGN.md 8.2, 10): the phones in it, who is on each, and the level of every direction between them. A
 * call starts with a caller and one phone ringing; once connected, anyone in it can add more phones, up to the group
 * call size, and phones leave one by one until fewer than two are left. Server-thread only; the routing thread sees
 * calls only through the routing snapshot.
 */
public final class Call {

    public enum State {
        RINGING,
        CONNECTED,
        ENDED
    }

    private final long id;
    // Every phone that was ever in the call, the caller first; those that left stay, for the history.
    private final List<Leg> legs = new ArrayList<>();
    private final Map<Direction, LinkState> links = new HashMap<>();
    private State state = State.RINGING;
    private final long startedMillis;
    private long stateSinceMillis;
    private long connectedMillis = -1;

    Call(long id, Leg caller, Leg callee, long nowMillis) {
        this.id = id;
        this.legs.add(caller);
        this.legs.add(callee);
        this.startedMillis = nowMillis;
        this.stateSinceMillis = nowMillis;
    }

    public long id() {
        return id;
    }

    public State state() {
        return state;
    }

    void setState(State state, long nowMillis) {
        this.state = state;
        this.stateSinceMillis = nowMillis;
        if (state == State.CONNECTED && connectedMillis < 0) connectedMillis = nowMillis;
    }

    /** Whether anyone ever answered, so an ended call can tell a missed call from a finished one. */
    public boolean wasConnected() {
        return connectedMillis >= 0;
    }

    /** When it first connected, or -1 if it never did. */
    public long connectedMillis() {
        return connectedMillis;
    }

    public long startedMillis() {
        return startedMillis;
    }

    public long stateSinceMillis() {
        return stateSinceMillis;
    }

    /** Every phone that was ever in the call, the caller first, including those that have left. */
    public List<Leg> legs() {
        return Collections.unmodifiableList(legs);
    }

    void addLeg(Leg leg) {
        legs.add(leg);
    }

    /** The phones in the call now: the caller and everyone who answered and hasn't left. */
    public List<Leg> activeLegs() {
        List<Leg> active = new ArrayList<>();
        for (Leg leg : legs) if (leg.state == Leg.State.ACTIVE) active.add(leg);
        return active;
    }

    /** The phones ringing to join it. */
    public List<Leg> ringingLegs() {
        List<Leg> ringing = new ArrayList<>();
        for (Leg leg : legs) if (leg.state == Leg.State.RINGING) ringing.add(leg);
        return ringing;
    }

    public Leg caller() {
        return legs.get(0);
    }

    /** The phone that first rang: the one the caller dialled. */
    public Leg callee() {
        return legs.get(1);
    }

    /** The leg of {@code phone} that hasn't left, or null. */
    public Leg legOf(int phone) {
        for (Leg leg : legs) if (leg.phone == phone && leg.state != Leg.State.LEFT) return leg;
        return null;
    }

    /** The leg {@code player} is in the call on, or null. */
    public Leg legOf(UUID player) {
        for (Leg leg : legs) if (leg.state == Leg.State.ACTIVE && leg.players()
            .contains(player)) return leg;
        return null;
    }

    /** The players in the call: everyone on a phone that's in it, a speakerphone's room included. */
    public List<UUID> participants() {
        List<UUID> players = new ArrayList<>();
        for (Leg leg : legs) if (leg.state == Leg.State.ACTIVE) players.addAll(leg.players());
        return players;
    }

    /** People in the call and phones still ringing to join it: what the group call size counts. */
    public int size() {
        int size = 0;
        for (Leg leg : legs) {
            if (leg.state == Leg.State.ACTIVE) size += leg.players()
                .size();
            else if (leg.state == Leg.State.RINGING) size++;
        }
        return size;
    }

    /** The level what {@code listener} hears from {@code speaker} is at; {@link SignalLevel#NONE} if unknown. */
    public SignalLevel level(UUID speaker, UUID listener) {
        Leg from = legOf(speaker);
        Leg to = legOf(listener);
        return from == null || to == null ? SignalLevel.NONE : level(from, to);
    }

    /** The level a phone hears another at: what each player on {@code listener} hears each on {@code speaker} at. */
    public SignalLevel level(Leg speaker, Leg listener) {
        LinkState link = links.get(new Direction(speaker.phone, listener.phone));
        return link == null ? SignalLevel.NONE : link.level;
    }

    /** The SNR of the weakest hop from {@code speaker} to {@code listener}, as last evaluated. */
    public double snr(UUID speaker, UUID listener) {
        Leg from = legOf(speaker);
        Leg to = legOf(listener);
        return from == null || to == null ? Double.NEGATIVE_INFINITY : snr(from, to);
    }

    public double snr(Leg speaker, Leg listener) {
        LinkState link = links.get(new Direction(speaker.phone, listener.phone));
        return link == null ? Double.NEGATIVE_INFINITY : link.snr;
    }

    LinkState link(Direction direction, double thresholdSnr, long graceMillis) {
        return links.computeIfAbsent(direction, d -> new LinkState(new DropTimer(thresholdSnr, graceMillis)));
    }

    LinkState existingLink(Direction direction) {
        return links.get(direction);
    }

    /** Forgets every direction to or from a phone that left. */
    void forget(int phone) {
        links.keySet()
            .removeIf(d -> d.speaker == phone || d.listener == phone);
    }

    /**
     * One phone in a call, and who is on it: the player who dialled or answered, or for a speakerphone everyone in its
     * room.
     */
    public static final class Leg {

        public enum State {
            RINGING,
            ACTIVE,
            LEFT
        }

        private final int phone;
        private final int invitedBy;
        private final long ringSinceMillis;
        private UUID player;
        private Set<UUID> room;
        private State state;
        private boolean answered;
        private DropTimer ringTimer;

        /** The caller's own phone, in the call from the start. */
        static Leg caller(int phone, UUID player, long nowMillis) {
            Leg leg = new Leg(phone, -1, nowMillis);
            leg.player = player;
            leg.state = State.ACTIVE;
            return leg;
        }

        /** A phone ringing to join, rung by {@code invitedBy}'s phone. */
        static Leg ringing(int phone, int invitedBy, long nowMillis) {
            Leg leg = new Leg(phone, invitedBy, nowMillis);
            leg.state = State.RINGING;
            return leg;
        }

        private Leg(int phone, int invitedBy, long ringSinceMillis) {
            this.phone = phone;
            this.invitedBy = invitedBy;
            this.ringSinceMillis = ringSinceMillis;
        }

        public int phone() {
            return phone;
        }

        /** The phone that dialled or added this one; -1 for the caller's. */
        public int invitedBy() {
            return invitedBy;
        }

        public long ringSinceMillis() {
            return ringSinceMillis;
        }

        /** Who dialled or answered on this phone; null while it rings. */
        public UUID player() {
            return player;
        }

        public State state() {
            return state;
        }

        /** Whether someone answered this phone, so it counts as having talked. The caller's phone counts. */
        public boolean answered() {
            return answered || invitedBy < 0;
        }

        /** Whether it is a speakerphone, whose players are its room. */
        public boolean speakerphone() {
            return room != null;
        }

        /** The players on this phone: its room for a speakerphone, else whoever dialled or answered. */
        public Set<UUID> players() {
            if (state != State.ACTIVE) return Collections.emptySet();
            if (room != null) return Collections.unmodifiableSet(room);
            return player == null ? Collections.emptySet() : Collections.singleton(player);
        }

        void answer(UUID player, boolean speakerphone) {
            this.player = player;
            this.answered = true;
            this.state = State.ACTIVE;
            if (speakerphone) {
                room = new LinkedHashSet<>();
                if (player != null) room.add(player);
            }
        }

        void makeSpeakerphone() {
            if (room != null) return;
            room = new LinkedHashSet<>();
            if (player != null) room.add(player);
        }

        Set<UUID> room() {
            return room;
        }

        void leave() {
            state = State.LEFT;
        }

        DropTimer ringTimer(double thresholdSnr, long graceMillis) {
            if (ringTimer == null) ringTimer = new DropTimer(thresholdSnr, graceMillis);
            return ringTimer;
        }

        DropTimer ringTimerIfAny() {
            return ringTimer;
        }
    }

    /** One way between two phones of the call (DESIGN.md 10: levels are per direction). */
    public record Direction(int speaker, int listener) {}

    static final class LinkState {

        final DropTimer dropTimer;
        /** The level from the SNR alone, with hysteresis; {@link #level} is this after the phones' caps. */
        SignalLevel rawLevel = SignalLevel.NONE;
        SignalLevel level = SignalLevel.NONE;
        double snr = Double.NEGATIVE_INFINITY;
        boolean evaluated;
        boolean dropped;

        LinkState(DropTimer dropTimer) {
            this.dropTimer = dropTimer;
        }
    }
}
