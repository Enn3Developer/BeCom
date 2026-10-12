package com.enn3developer.becom.logic.call;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.enn3developer.becom.logic.LevelScale;
import com.enn3developer.becom.logic.RateLimiter;
import com.enn3developer.becom.logic.SignalLevel;

/**
 * Every call in the world and the rules they follow (DESIGN.md 8.2, 10, 12.3): one call per player and per phone,
 * group calls up to a size, ringing that gives up, levels per direction with hysteresis, links that drop after staying
 * weak, and the grace a timed-out voice session gets. Pure logic on the server thread; what a call does in the world
 * goes through {@link Listener}.
 */
public final class CallBook {

    private static final long MILLIS_PER_MINUTE = 60_000L;

    public enum DialResult {
        RINGING,
        BUSY,
        UNREACHABLE,
        RATE_LIMITED,
        /** The caller is already in a call, or dialled their own phone. */
        INVALID,
        /** The call already has as many people as a group call takes. */
        FULL
    }

    public enum EndReason {
        HUNG_UP,
        NO_ANSWER,
        REJECTED,
        DROPPED,
        LOGOUT,
        SESSION_LOST,
        PHONE_GONE,
        ADMIN
    }

    /** What the world says about a call before it rings. */
    public enum Check {
        OK,
        BUSY,
        UNREACHABLE
    }

    /** Side effects of calls, run by the world. */
    public interface Listener {

        /** {@code leg} starts ringing: the phone a call dialled, or one added to a call. */
        void ringing(Call call, Call.Leg leg);

        /** Someone answered {@code answered}: the call connects, or a phone joins it. */
        void connected(Call call, Call.Leg answered);

        /** {@code leg} left a call that goes on without it, or stopped ringing to join one. */
        void legLeft(Call call, Call.Leg leg, Set<UUID> players, EndReason reason);

        /** The call is over for everyone. */
        void ended(Call call, EndReason reason);

        /** A direction's level changed for one listener: tell their client. */
        void levelChanged(Call call, UUID speaker, UUID listener, SignalLevel level);

        /** Players came into or left a speakerphone's room, and so the call. */
        void roomChanged(Call call, Call.Leg leg, Set<UUID> joined, Set<UUID> left);
    }

    /** Where per-direction quality comes from, evaluated once a second. */
    public interface Quality {

        /** The weakest hop's SNR from the speaker's phone to the listener's. */
        double snr(Call call, Call.Leg speaker, Call.Leg listener);

        /** The best level the pair of phones allows (D10): the lower of the two caps. */
        SignalLevel cap(Call call, Call.Leg speaker, Call.Leg listener);
    }

    private final LevelScale levels;
    private final RateLimiter<UUID> attempts;
    private final long ringMillis;
    private final long dropGraceMillis;
    private final long sessionGraceMillis;
    private final int groupSize;
    private final Listener listener;

    private final Map<Long, Call> calls = new LinkedHashMap<>();
    private final Map<UUID, Call> byPlayer = new HashMap<>();
    private final Map<Integer, Call> byPhone = new HashMap<>();
    private final Map<UUID, Long> sessionLostAt = new HashMap<>();
    private long nextId = 1;
    private long version;

    public CallBook(LevelScale levels, int attemptsPerMinute, long ringMillis, long dropGraceMillis,
        long sessionGraceMillis, int groupSize, Listener listener) {
        this.levels = levels;
        this.attempts = new RateLimiter<>(attemptsPerMinute, MILLIS_PER_MINUTE);
        this.ringMillis = ringMillis;
        this.dropGraceMillis = dropGraceMillis;
        this.sessionGraceMillis = sessionGraceMillis;
        this.groupSize = groupSize;
        this.listener = listener;
    }

    public Collection<Call> calls() {
        return Collections.unmodifiableCollection(calls.values());
    }

    public Call call(long id) {
        return calls.get(id);
    }

    public Call callOf(UUID player) {
        return byPlayer.get(player);
    }

    public Call callOfPhone(int phone) {
        return byPhone.get(phone);
    }

    public int groupSize() {
        return groupSize;
    }

    /** Goes up whenever who's in which call, or any level, changes: the routing snapshot follows it. */
    public long version() {
        return version;
    }

    private double low() {
        return levels.threshold(SignalLevel.LOW);
    }

    /**
     * {@code caller} on phone {@code from} dials {@code to}. The rate limit counts every attempt that gets this far,
     * busy and unreachable ones included, since those are what a flood would be made of.
     */
    public DialResult dial(UUID caller, int from, int to, long nowMillis, Check worldCheck) {
        if (from == to || byPlayer.containsKey(caller) || byPhone.containsKey(from)) return DialResult.INVALID;
        if (!attempts.tryAcquire(caller, nowMillis)) return DialResult.RATE_LIMITED;
        if (byPhone.containsKey(to) || worldCheck == Check.BUSY) return DialResult.BUSY;
        if (worldCheck == Check.UNREACHABLE) return DialResult.UNREACHABLE;
        Call.Leg callee = Call.Leg.ringing(to, from, nowMillis);
        Call call = new Call(nextId++, Call.Leg.caller(from, caller, nowMillis), callee, nowMillis);
        calls.put(call.id(), call);
        byPlayer.put(caller, call);
        byPhone.put(from, call);
        byPhone.put(to, call);
        version++;
        listener.ringing(call, callee);
        return DialResult.RINGING;
    }

    /**
     * {@code player}, in a connected call, adds phone {@code to} to it (DESIGN.md 8.2: group calls), which rings like a
     * dialled phone. Counted against the same rate limit as dialling.
     */
    public DialResult add(UUID player, int to, long nowMillis, Check worldCheck) {
        Call call = byPlayer.get(player);
        if (call == null || call.state() != Call.State.CONNECTED) return DialResult.INVALID;
        Call.Leg own = call.legOf(player);
        if (own == null || own.phone() == to || call.legOf(to) != null) return DialResult.INVALID;
        if (call.size() >= groupSize) return DialResult.FULL;
        if (!attempts.tryAcquire(player, nowMillis)) return DialResult.RATE_LIMITED;
        if (byPhone.containsKey(to) || worldCheck == Check.BUSY) return DialResult.BUSY;
        if (worldCheck == Check.UNREACHABLE) return DialResult.UNREACHABLE;
        Call.Leg leg = Call.Leg.ringing(to, own.phone(), nowMillis);
        call.addLeg(leg);
        byPhone.put(to, call);
        version++;
        listener.ringing(call, leg);
        return DialResult.RINGING;
    }

    /** {@code player} picks up ringing phone {@code phone}. False when there's nothing to answer. */
    public boolean answer(UUID player, int phone, long nowMillis) {
        Call call = byPhone.get(phone);
        if (call == null || byPlayer.containsKey(player)) return false;
        Call.Leg leg = call.legOf(phone);
        if (leg == null || leg.state() != Call.Leg.State.RINGING) return false;
        leg.answer(player, false);
        byPlayer.put(player, call);
        if (call.state() == Call.State.RINGING) call.setState(Call.State.CONNECTED, nowMillis);
        // The ring held a path at Low (it was dialled at Low and kept within the drop grace), so each new direction
        // starts there and the hysteresis carries it just below, as it would a call that had been connected all along.
        for (Call.Leg other : call.activeLegs()) {
            if (other == leg) continue;
            startAtLow(call, new Call.Direction(other.phone(), leg.phone()), leg);
            startAtLow(call, new Call.Direction(leg.phone(), other.phone()), leg);
        }
        version++;
        listener.connected(call, leg);
        return true;
    }

    private void startAtLow(Call call, Call.Direction direction, Call.Leg answered) {
        Call.LinkState link = call.link(direction, low(), dropGraceMillis);
        link.rawLevel = SignalLevel.LOW;
        // The ring's time below Low counts: answering doesn't buy a weak line a fresh 3 s (DESIGN.md 4.2).
        link.dropTimer.continueFrom(answered.ringTimer(low(), dropGraceMillis));
    }

    /**
     * {@code player} hangs up: the phone they're on leaves the call, and the call ends once fewer than two phones are
     * left in it. A speakerphone hangs up for its whole room.
     */
    public boolean hangUp(UUID player) {
        Call call = byPlayer.get(player);
        if (call == null) return false;
        Call.Leg leg = call.legOf(player);
        if (leg == null) return false;
        leave(call, leg, EndReason.HUNG_UP);
        return true;
    }

    /** Hangs up phone {@code phone}, whoever is on it: a speakerphone's own button. */
    public boolean hangUpPhone(int phone) {
        Call call = byPhone.get(phone);
        if (call == null) return false;
        Call.Leg leg = call.legOf(phone);
        if (leg == null || leg.state() != Call.Leg.State.ACTIVE) return false;
        leave(call, leg, EndReason.HUNG_UP);
        return true;
    }

    /** Someone at a ringing phone turns the call down (D34's reject). */
    public boolean reject(int phone) {
        Call call = byPhone.get(phone);
        if (call == null) return false;
        Call.Leg leg = call.legOf(phone);
        if (leg == null || leg.state() != Call.Leg.State.RINGING) return false;
        leave(call, leg, EndReason.REJECTED);
        return true;
    }

    /** A phone left the world (its block was broken): it leaves its call. */
    public void phoneGone(int phone) {
        Call call = byPhone.get(phone);
        if (call == null) return;
        Call.Leg leg = call.legOf(phone);
        if (leg != null) leave(call, leg, EndReason.PHONE_GONE);
    }

    /** The player logged out (DESIGN.md 10): their phone leaves its call, or they leave a speakerphone's room. */
    public void logout(UUID player) {
        sessionLostAt.remove(player);
        drop(player, EndReason.LOGOUT);
    }

    private void drop(UUID player, EndReason reason) {
        Call call = byPlayer.get(player);
        if (call == null) return;
        Call.Leg leg = call.legOf(player);
        if (leg == null) return;
        if (leg.speakerphone()) {
            leaveRoom(call, leg, player);
        } else {
            leave(call, leg, reason);
        }
    }

    /** The player's voice session timed out: they leave their call after the grace period unless it comes back. */
    public void sessionLost(UUID player, long nowMillis) {
        if (byPlayer.containsKey(player)) sessionLostAt.put(player, nowMillis);
    }

    public void sessionBack(UUID player) {
        sessionLostAt.remove(player);
    }

    public void endByAdmin(Call call) {
        if (calls.containsKey(call.id())) end(call, EndReason.ADMIN);
    }

    /**
     * Who is in a speakerphone's room now (DESIGN.md 10.2): those within reach join its call unless they're in another
     * call or the call is full; those gone leave it. The phone must be in a call, ringing out or connected.
     */
    public void setRoom(int phone, Collection<UUID> wanted) {
        Call call = byPhone.get(phone);
        if (call == null) return;
        Call.Leg leg = call.legOf(phone);
        if (leg == null || leg.state() != Call.Leg.State.ACTIVE) return;
        leg.makeSpeakerphone();
        Set<UUID> room = leg.room();
        Set<UUID> left = new LinkedHashSet<>();
        for (UUID player : room) if (!wanted.contains(player)) left.add(player);
        for (UUID player : left) {
            room.remove(player);
            byPlayer.remove(player, call);
            sessionLostAt.remove(player);
        }
        Set<UUID> joined = new LinkedHashSet<>();
        for (UUID player : wanted) {
            if (room.contains(player) || byPlayer.containsKey(player)) continue;
            if (call.size() >= groupSize) break;
            room.add(player);
            byPlayer.put(player, call);
            joined.add(player);
        }
        if (joined.isEmpty() && left.isEmpty()) return;
        version++;
        listener.roomChanged(call, leg, joined, left);
    }

    private void leaveRoom(Call call, Call.Leg leg, UUID player) {
        if (!leg.room()
            .remove(player)) return;
        byPlayer.remove(player, call);
        sessionLostAt.remove(player);
        version++;
        listener.roomChanged(call, leg, Collections.emptySet(), Collections.singleton(player));
    }

    /** Ring timeouts and session grace; run every tick or so. */
    public void tick(long nowMillis) {
        for (Call call : new ArrayList<>(calls.values())) {
            for (Call.Leg leg : call.ringingLegs()) {
                if (nowMillis - leg.ringSinceMillis() >= ringMillis) leave(call, leg, EndReason.NO_ANSWER);
                if (call.state() == Call.State.ENDED) break;
            }
        }
        Iterator<Map.Entry<UUID, Long>> it = sessionLostAt.entrySet()
            .iterator();
        List<UUID> lost = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (nowMillis - entry.getValue() >= sessionGraceMillis) {
                lost.add(entry.getKey());
                it.remove();
            }
        }
        for (UUID player : lost) drop(player, EndReason.SESSION_LOST);
        attempts.sweep(nowMillis);
    }

    /**
     * Re-evaluates every call (DESIGN.md 4.1: once a second): each direction of a connected call, and whether each
     * ringing phone still has a path.
     */
    public void evaluate(long nowMillis, Quality quality) {
        for (Call call : new ArrayList<>(calls.values())) evaluate(call, nowMillis, quality);
    }

    /**
     * Re-evaluates one call. Ringing phones: each gives up once its path from the phone that rang it has been below
     * Low for the same grace a connected call gets, rather than ringing on for the full timeout over a line that's
     * gone. Connected: each direction's SNR, its level with hysteresis and the phones' caps, and whether it has been
     * too weak for too long. A pair of phones is cut off once either way between them has; a phone cut off from
     * everyone else drops out, and so does the call once fewer than two phones are left. The call service also runs
     * this the moment a phone answers, so its first words aren't lost waiting for the next second.
     */
    public void evaluate(Call call, long nowMillis, Quality quality) {
        for (Call.Leg leg : call.ringingLegs()) {
            if (call.state() == Call.State.ENDED) return;
            Call.Leg ringer = call.legOf(leg.invitedBy());
            double snr = ringer == null || ringer.state() != Call.Leg.State.ACTIVE ? Double.NEGATIVE_INFINITY
                : quality.snr(call, ringer, leg);
            if (leg.ringTimer(low(), dropGraceMillis)
                .update(snr, nowMillis)) leave(call, leg, EndReason.DROPPED);
        }
        if (call.state() != Call.State.CONNECTED) return;
        List<Call.Leg> active = call.activeLegs();
        for (Call.Leg speaker : active) {
            for (Call.Leg hearer : active) {
                if (hearer == speaker) continue;
                Call.LinkState link = call
                    .link(new Call.Direction(speaker.phone(), hearer.phone()), low(), dropGraceMillis);
                double snr = quality.snr(call, speaker, hearer);
                link.snr = snr;
                SignalLevel raw = levels.next(link.rawLevel, snr);
                link.rawLevel = raw;
                SignalLevel capped = SignalLevel.min(raw, quality.cap(call, speaker, hearer));
                if (!link.evaluated || capped != link.level) {
                    link.level = capped;
                    link.evaluated = true;
                    version++;
                    for (UUID from : speaker.players()) {
                        for (UUID to : hearer.players()) listener.levelChanged(call, from, to, capped);
                    }
                }
                link.dropped = link.dropTimer.update(snr, nowMillis);
            }
        }
        List<Call.Leg> gone = new ArrayList<>();
        for (Call.Leg leg : active) if (cutOff(call, leg, active)) gone.add(leg);
        if (gone.isEmpty()) return;
        if (active.size() - gone.size() < 2) {
            end(call, EndReason.DROPPED);
            return;
        }
        for (Call.Leg leg : gone) leave(call, leg, EndReason.DROPPED);
    }

    /** Whether every other phone in the call is cut off from {@code leg}, either way. */
    private static boolean cutOff(Call call, Call.Leg leg, List<Call.Leg> active) {
        boolean anyOther = false;
        for (Call.Leg other : active) {
            if (other == leg) continue;
            anyOther = true;
            Call.LinkState out = call.existingLink(new Call.Direction(leg.phone(), other.phone()));
            Call.LinkState in = call.existingLink(new Call.Direction(other.phone(), leg.phone()));
            if (!(out != null && out.dropped || in != null && in.dropped)) return false;
        }
        return anyOther;
    }

    /**
     * {@code leg} leaves: it hung up, stopped ringing, dropped, or its phone or player is gone. While the call still
     * rings its first phone, either end leaving ends it; once connected, it goes on while two phones are left in it.
     */
    private void leave(Call call, Call.Leg leg, EndReason reason) {
        if (call.state() == Call.State.ENDED || leg.state() == Call.Leg.State.LEFT) return;
        boolean firstRing = call.state() == Call.State.RINGING;
        boolean active = leg.state() == Call.Leg.State.ACTIVE;
        if (firstRing || active && call.activeLegs()
            .size() <= 2) {
            end(call, reason);
            return;
        }
        Set<UUID> players = new LinkedHashSet<>(leg.players());
        leg.leave();
        byPhone.remove(leg.phone(), call);
        for (UUID player : players) {
            byPlayer.remove(player, call);
            sessionLostAt.remove(player);
        }
        call.forget(leg.phone());
        version++;
        listener.legLeft(call, leg, players, reason);
    }

    private void end(Call call, EndReason reason) {
        if (call.state() == Call.State.ENDED) return;
        calls.remove(call.id());
        for (Call.Leg leg : call.legs()) {
            if (leg.state() == Call.Leg.State.LEFT) continue;
            byPhone.remove(leg.phone(), call);
            for (UUID player : leg.players()) {
                byPlayer.remove(player, call);
                sessionLostAt.remove(player);
            }
        }
        call.setState(Call.State.ENDED, call.stateSinceMillis());
        version++;
        listener.ended(call, reason);
    }
}
