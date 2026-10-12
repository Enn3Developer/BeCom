package com.enn3developer.becom.logic.call;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.enn3developer.becom.logic.DropTimer;
import com.enn3developer.becom.logic.SignalLevel;

/**
 * One call (DESIGN.md 10): the phones in it, who answered on each, and the level of every direction between them.
 * Server-thread only; the routing thread sees calls only through the routing snapshot.
 */
public final class Call {

    public enum State {
        RINGING,
        CONNECTED,
        ENDED
    }

    private final long id;
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
        if (state == State.CONNECTED) connectedMillis = nowMillis;
    }

    /** Whether anyone ever answered, so an ended call can tell a missed call from a finished one. */
    public boolean wasConnected() {
        return connectedMillis >= 0;
    }

    /** When it connected, or -1 if it never did. */
    public long connectedMillis() {
        return connectedMillis;
    }

    public long startedMillis() {
        return startedMillis;
    }

    public long stateSinceMillis() {
        return stateSinceMillis;
    }

    /** Every phone in the call, the caller first. */
    public List<Leg> legs() {
        return Collections.unmodifiableList(legs);
    }

    public Leg caller() {
        return legs.get(0);
    }

    public Leg legOf(int phone) {
        for (Leg leg : legs) if (leg.phone() == phone) return leg;
        return null;
    }

    public Leg legOf(UUID player) {
        for (Leg leg : legs) if (player.equals(leg.player())) return leg;
        return null;
    }

    /** The players who are in the call: the caller and everyone who answered. */
    public List<UUID> participants() {
        List<UUID> players = new ArrayList<>();
        for (Leg leg : legs) if (leg.player() != null) players.add(leg.player());
        return players;
    }

    /** The level what {@code listener} hears from {@code speaker} is at; {@link SignalLevel#NONE} if unknown. */
    public SignalLevel level(UUID speaker, UUID listener) {
        LinkState link = links.get(new Direction(speaker, listener));
        return link == null ? SignalLevel.NONE : link.level;
    }

    /** The SNR of the weakest hop from {@code speaker} to {@code listener}, as last evaluated. */
    public double snr(UUID speaker, UUID listener) {
        LinkState link = links.get(new Direction(speaker, listener));
        return link == null ? Double.NEGATIVE_INFINITY : link.snr;
    }

    LinkState link(Direction direction, double thresholdSnr, long graceMillis) {
        return links.computeIfAbsent(direction, d -> new LinkState(new DropTimer(thresholdSnr, graceMillis)));
    }

    void forget(UUID player) {
        links.keySet()
            .removeIf(d -> d.speaker.equals(player) || d.listener.equals(player));
    }

    /** One end of a call: a phone, and the player on it once they've picked up. */
    public static final class Leg {

        private final int phone;
        private UUID player;

        public Leg(int phone, UUID player) {
            this.phone = phone;
            this.player = player;
        }

        public int phone() {
            return phone;
        }

        /** Null until someone answers this phone. */
        public UUID player() {
            return player;
        }

        void setPlayer(UUID player) {
            this.player = player;
        }
    }

    /** What one listener hears from one speaker (DESIGN.md 10: levels are per direction). */
    public record Direction(UUID speaker, UUID listener) {}

    static final class LinkState {

        final DropTimer dropTimer;
        /** The level from the SNR alone, with hysteresis; {@link #level} is this after the phones' caps. */
        SignalLevel rawLevel = SignalLevel.NONE;
        SignalLevel level = SignalLevel.NONE;
        double snr = Double.NEGATIVE_INFINITY;
        boolean evaluated;

        LinkState(DropTimer dropTimer) {
            this.dropTimer = dropTimer;
        }
    }
}
