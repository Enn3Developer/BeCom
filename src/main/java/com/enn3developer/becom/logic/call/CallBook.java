package com.enn3developer.becom.logic.call;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.enn3developer.becom.logic.LevelScale;
import com.enn3developer.becom.logic.RateLimiter;
import com.enn3developer.becom.logic.SignalLevel;

/**
 * Every call in the world and the rules they follow (DESIGN.md 10, 12.3): one call per player and per phone, ringing
 * that gives up, levels per direction with hysteresis, links that drop after staying weak, and the grace a timed-out
 * voice session gets. Pure logic on the server thread; what a call does in the world goes through {@link Listener}.
 */
public final class CallBook {

    private static final long MILLIS_PER_MINUTE = 60_000L;

    public enum DialResult {
        RINGING,
        BUSY,
        UNREACHABLE,
        RATE_LIMITED,
        /** The caller is already in a call, or dialled their own phone. */
        INVALID
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

        void ringing(Call call);

        void connected(Call call, Call.Leg answered);

        void ended(Call call, EndReason reason);

        /** A direction's level changed: tell the listener's client. */
        void levelChanged(Call call, UUID speaker, UUID listener, SignalLevel level);
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
    private final Listener listener;

    private final Map<Long, Call> calls = new LinkedHashMap<>();
    private final Map<UUID, Call> byPlayer = new HashMap<>();
    private final Map<Integer, Call> byPhone = new HashMap<>();
    private final Map<UUID, Long> sessionLostAt = new HashMap<>();
    private long nextId = 1;
    private long version;

    public CallBook(LevelScale levels, int attemptsPerMinute, long ringMillis, long dropGraceMillis,
        long sessionGraceMillis, Listener listener) {
        this.levels = levels;
        this.attempts = new RateLimiter<>(attemptsPerMinute, MILLIS_PER_MINUTE);
        this.ringMillis = ringMillis;
        this.dropGraceMillis = dropGraceMillis;
        this.sessionGraceMillis = sessionGraceMillis;
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

    /** Goes up whenever who's in which call, or any level, changes: the routing snapshot follows it. */
    public long version() {
        return version;
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
        Call call = new Call(nextId++, new Call.Leg(from, caller), new Call.Leg(to, null), nowMillis);
        calls.put(call.id(), call);
        byPlayer.put(caller, call);
        byPhone.put(from, call);
        byPhone.put(to, call);
        version++;
        listener.ringing(call);
        return DialResult.RINGING;
    }

    /** {@code player} picks up ringing phone {@code phone}. False when there's nothing to answer. */
    public boolean answer(UUID player, int phone, long nowMillis) {
        Call call = byPhone.get(phone);
        if (call == null || call.state() != Call.State.RINGING || byPlayer.containsKey(player)) return false;
        Call.Leg leg = call.legOf(phone);
        if (leg == null || leg.player() != null) return false;
        leg.setPlayer(player);
        byPlayer.put(player, call);
        call.setState(Call.State.CONNECTED, nowMillis);
        // The ring held a path at Low (it was dialled at Low and kept within the drop grace), so each direction starts
        // there and the hysteresis carries it just below, as it would a call that had been connected all along.
        for (Call.Leg speaker : call.legs()) {
            for (Call.Leg hearer : call.legs()) {
                if (speaker == hearer || speaker.player() == null || hearer.player() == null) continue;
                call.link(
                    new Call.Direction(speaker.player(), hearer.player()),
                    levels.threshold(SignalLevel.LOW),
                    dropGraceMillis).rawLevel = SignalLevel.LOW;
            }
        }
        version++;
        listener.connected(call, leg);
        return true;
    }

    /** {@code player} hangs up. One-to-one, so this ends the call for everyone. */
    public boolean hangUp(UUID player) {
        Call call = byPlayer.get(player);
        if (call == null) return false;
        end(call, EndReason.HUNG_UP);
        return true;
    }

    /** Someone at a ringing phone turns the call down (D34's reject). */
    public boolean reject(int phone) {
        Call call = byPhone.get(phone);
        if (call == null || call.state() != Call.State.RINGING
            || call.caller()
                .phone() == phone)
            return false;
        end(call, EndReason.REJECTED);
        return true;
    }

    /** A phone left the world (its block was broken): its call ends. */
    public void phoneGone(int phone) {
        Call call = byPhone.get(phone);
        if (call != null) end(call, EndReason.PHONE_GONE);
    }

    /** The player logged out (DESIGN.md 10). */
    public void logout(UUID player) {
        sessionLostAt.remove(player);
        Call call = byPlayer.get(player);
        if (call != null) end(call, EndReason.LOGOUT);
    }

    /** The player's voice session timed out: their call ends after the grace period unless it comes back. */
    public void sessionLost(UUID player, long nowMillis) {
        if (byPlayer.containsKey(player)) sessionLostAt.put(player, nowMillis);
    }

    public void sessionBack(UUID player) {
        sessionLostAt.remove(player);
    }

    public void endByAdmin(Call call) {
        if (calls.containsKey(call.id())) end(call, EndReason.ADMIN);
    }

    /** Ring timeouts and session grace; run every tick or so. */
    public void tick(long nowMillis) {
        for (Call call : new ArrayList<>(calls.values())) {
            if (call.state() == Call.State.RINGING && nowMillis - call.stateSinceMillis() >= ringMillis) {
                end(call, EndReason.NO_ANSWER);
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
        for (UUID player : lost) {
            Call call = byPlayer.get(player);
            if (call != null) end(call, EndReason.SESSION_LOST);
        }
        attempts.sweep(nowMillis);
    }

    /**
     * Re-evaluates every call (DESIGN.md 4.1: once a second): each direction of a connected call, and whether a ringing
     * call still has a path.
     */
    public void evaluate(long nowMillis, Quality quality) {
        for (Call call : new ArrayList<>(calls.values())) evaluate(call, nowMillis, quality);
    }

    /**
     * Re-evaluates one call. Connected: each direction's SNR, its level with hysteresis and the phones' caps, and
     * whether it has been too weak for too long. Ringing: it ends once its path has been below Low for the same grace
     * a connected call gets, rather than ringing on for the full timeout over a line that's gone. The call service
     * also runs this the moment a call connects, so its first words aren't lost waiting for the next second.
     */
    public void evaluate(Call call, long nowMillis, Quality quality) {
        if (call.state() == Call.State.RINGING) {
            Call.Leg callee = call.legs()
                .get(1);
            double snr = quality.snr(call, call.caller(), callee);
            if (call.ringTimer(levels.threshold(SignalLevel.LOW), dropGraceMillis)
                .update(snr, nowMillis)) end(call, EndReason.DROPPED);
            return;
        }
        if (call.state() != Call.State.CONNECTED) return;
        boolean dropped = false;
        for (Call.Leg speaker : call.legs()) {
            if (speaker.player() == null) continue;
            for (Call.Leg hearer : call.legs()) {
                if (hearer == speaker || hearer.player() == null) continue;
                Call.LinkState link = call.link(
                    new Call.Direction(speaker.player(), hearer.player()),
                    levels.threshold(SignalLevel.LOW),
                    dropGraceMillis);
                double snr = quality.snr(call, speaker, hearer);
                link.snr = snr;
                SignalLevel raw = levels.next(link.rawLevel, snr);
                link.rawLevel = raw;
                SignalLevel capped = SignalLevel.min(raw, quality.cap(call, speaker, hearer));
                if (!link.evaluated || capped != link.level) {
                    link.level = capped;
                    link.evaluated = true;
                    version++;
                    listener.levelChanged(call, speaker.player(), hearer.player(), capped);
                }
                if (link.dropTimer.update(snr, nowMillis)) dropped = true;
            }
        }
        if (dropped) end(call, EndReason.DROPPED);
    }

    private void end(Call call, EndReason reason) {
        if (call.state() == Call.State.ENDED) return;
        calls.remove(call.id());
        for (Call.Leg leg : call.legs()) {
            byPhone.remove(leg.phone(), call);
            if (leg.player() != null) {
                byPlayer.remove(leg.player(), call);
                sessionLostAt.remove(leg.player());
            }
        }
        call.setState(Call.State.ENDED, call.stateSinceMillis());
        version++;
        listener.ended(call, reason);
    }
}
