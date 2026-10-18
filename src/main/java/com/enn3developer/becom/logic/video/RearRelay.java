package com.enn3developer.becom.logic.video;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleBiFunction;

import com.enn3developer.becom.logic.DropoutModel;
import com.enn3developer.becom.logic.RateLimiter;
import com.enn3developer.becom.logic.Tunables;

/**
 * The rear camera's pictures on their way through the server: each sender's are held to the
 * per-client byte and rate caps, then go on to each viewer their rear camera reaches, as often as that viewer's level
 * allows and when the dropout chain lets them through. A picture is never looked into. Pure, so it's tested without a
 * world. Server thread only.
 */
public final class RearRelay {

    private static final double MILLIS_PER_SECOND = 1000.0;
    // A picture samples the dropout chain run on since the one before; past this many voice frames the chain has long
    // forgotten where it was.
    private static final int MAX_CHAIN_STEPS = 50;
    // A viewer's allowance fills at their level's frame rate and holds this many pictures. The sender times its
    // pictures to that rate, but they reach the server on its ticks, each up to a tick late: one that comes early
    // after a late one still goes on, and the average stays at the level's rate.
    private static final double ALLOWANCE = 2;

    private final Tunables tunables;
    private final DropoutModel dropouts;
    private final DoubleSupplier random;
    private final RateLimiter<UUID> uploads;
    private final Map<Pair, Link> links = new HashMap<>();

    private record Pair(UUID sender, UUID viewer) {}

    /** One sender and one viewer: the pictures they may still get, their loss chain, and how many went. */
    private static final class Link {

        final DropoutModel.Chain chain = new DropoutModel.Chain();
        long chainMillis;
        double allowance = ALLOWANCE;
        long allowanceMillis;

        Link(long nowMillis) {
            chainMillis = nowMillis;
            allowanceMillis = nowMillis;
        }

        /** Whether a picture is due at {@code nowMillis}, the allowance filling at {@code fps}; 0 for every one. */
        boolean due(long nowMillis, int fps) {
            if (fps <= 0) return true;
            allowance = Math.min(ALLOWANCE, allowance + (nowMillis - allowanceMillis) * fps / MILLIS_PER_SECOND);
            allowanceMillis = nowMillis;
            return allowance >= 1;
        }
    }

    public RearRelay(Tunables tunables, DoubleSupplier random) {
        this.tunables = tunables;
        this.dropouts = tunables.dropoutModel(Tunables.FRAME_MILLIS);
        this.random = random;
        this.uploads = new RateLimiter<>(tunables.rearMaxFps(), (long) MILLIS_PER_SECOND);
    }

    /**
     * The viewers a picture of {@code bytes} bytes from {@code sender} goes on to at {@code nowMillis}, a monotonic
     * clock. {@code snr} gives the SNR a viewer gets the sender at, for the dropout model.
     */
    public List<UUID> relay(UUID sender, int bytes, long nowMillis, VideoPlan plan,
        ToDoubleBiFunction<UUID, UUID> snr) {
        if (bytes <= 0 || bytes > tunables.rearMaxBytes()) return List.of();
        uploads.sweep(nowMillis);
        if (!uploads.tryAcquire(sender, nowMillis)) return List.of();
        if (plan.rearSendOf(sender) == null) return List.of();
        List<UUID> to = new ArrayList<>();
        for (UUID viewer : plan.viewers()) {
            for (VideoPlan.Tile tile : plan.tilesOf(viewer)) {
                if (!tile.source()
                    .equals(sender) || !tile.rear()) continue;
                Link link = links.computeIfAbsent(new Pair(sender, viewer), p -> new Link(nowMillis));
                int fps = VideoQuality.of(tunables, tile.level())
                    .rearFps();
                if (!link.due(nowMillis, fps)) continue;
                int steps = (int) Math
                    .max(1, Math.min(MAX_CHAIN_STEPS, (nowMillis - link.chainMillis) / Tunables.FRAME_MILLIS));
                link.chainMillis = nowMillis;
                double loss = dropouts.lossRate(snr.applyAsDouble(sender, viewer));
                if (link.chain.dropAfter(steps, dropouts.enterBadChance(loss), dropouts.leaveBadChance(), random)) {
                    continue;
                }
                if (fps > 0) link.allowance -= 1;
                to.add(viewer);
            }
        }
        return to;
    }

    /** Forgets every sender and viewer the plan no longer sends rear pictures between. */
    public void keep(VideoPlan plan) {
        links.keySet()
            .removeIf(pair -> !sendsRear(plan, pair.sender(), pair.viewer()));
    }

    private static boolean sendsRear(VideoPlan plan, UUID sender, UUID viewer) {
        for (VideoPlan.Tile tile : plan.tilesOf(viewer)) {
            if (tile.source()
                .equals(sender) && tile.rear()) return true;
        }
        return false;
    }

    public void clear() {
        links.clear();
    }
}
