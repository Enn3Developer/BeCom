package com.enn3developer.becom.voice;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

import org.jetbrains.annotations.NotNull;

import com.enn3developer.becom.logic.DropoutModel;
import com.enn3developer.gtnhvoice.api.server.IVoiceSession;
import com.enn3developer.gtnhvoice.api.server.PlayerSnapshot;
import com.enn3developer.gtnhvoice.api.server.SourceState;
import com.enn3developer.gtnhvoice.api.server.group.IGroup;
import com.enn3developer.gtnhvoice.api.server.group.RoutingContext;

/**
 * {@code becom:call} (DESIGN.md 13.1, 13.3): carries every call's voice, flat, to the others in the call, minus those
 * who hear the speaker directly and frames the dropout model eats. A Videophone at the other end plays it from its
 * loudspeaker instead, positional from the block, to everyone around it (DESIGN.md 10.2). Flat routes go first: GTNH
 * Voice keeps the first send per player, so someone in the call on their own phone never also hears a loudspeaker.
 * <p>
 * {@link #route} runs on GTNH Voice's network thread. It reads the volatile snapshot, the routing context and its own
 * per-pair loss chains, and nothing else. It never calls {@code exclusive()}: proximity still routes the speaker's
 * voice to people standing nearby.
 */
public final class CallGroup implements IGroup {

    public static final String NAME = "becom:call";
    public static final int PRIORITY = 200;

    private final String displayName;
    private final double directRange;
    private final IntSupplier proximity;
    private final DoubleSupplier random;
    private volatile CallSnapshot snapshot = CallSnapshot.EMPTY;
    private final Map<Pair, DropoutModel.Chain> chains = new ConcurrentHashMap<>();

    /**
     * @param proximity GTNH Voice's proximity range in blocks, a cap on the direct range; called on the network thread
     * @param random    uniform numbers in [0, 1) for the dropout model; must be safe on the network thread
     */
    public CallGroup(String displayName, double directRange, IntSupplier proximity, DoubleSupplier random) {
        this.displayName = displayName;
        this.directRange = directRange;
        this.proximity = proximity;
        this.random = random;
    }

    /** A speaker and who hears them: a player's UUID, or a Videophone's number for its loudspeaker. */
    private record Pair(UUID speaker, Object listener) {}

    @Override
    public @NotNull String getName() {
        return NAME;
    }

    @Override
    public @NotNull String getDisplayName() {
        return displayName;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    /** Server thread: replaces what calls route from, and forgets loss chains of pairs that are gone. */
    public void publish(CallSnapshot next) {
        snapshot = next;
        chains.keySet()
            .removeIf(pair -> !next.hasPair(pair.speaker, pair.listener));
    }

    public CallSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public void route(@NotNull RoutingContext context) {
        CallSnapshot current = snapshot;
        UUID speaker = context.getSpeakerSession()
            .getPlayerUuid();
        CallSnapshot.Route[] routes = current.routesOf(speaker);
        CallSnapshot.Loudspeaker[] loudspeakers = current.loudspeakersOf(speaker);
        if (routes.length == 0 && loudspeakers.length == 0) return;
        Map<UUID, PlayerSnapshot> positions = context.getPositionSnapshot();
        Map<UUID, IVoiceSession> sessions = context.getSessionsByPlayerUuid();
        PlayerSnapshot from = positions.get(speaker);
        double range = DirectHearing.range(directRange, proximity.getAsInt());
        for (CallSnapshot.Route route : routes) {
            IVoiceSession listener = sessions.get(route.listener());
            if (listener == null) continue;
            if (DirectHearing.hears(from, positions.get(route.listener()), range)) continue;
            if (dropped(speaker, route.listener(), route.enterBad(), route.leaveBad())) continue;
            double x = from == null ? 0 : from.getX();
            double y = from == null ? 0 : from.getY();
            double z = from == null ? 0 : from.getZ();
            context.sendTo(listener, SourceState.FLAT, x, y, z);
        }
        for (CallSnapshot.Loudspeaker loudspeaker : loudspeakers) {
            if (dropped(speaker, loudspeaker.phone(), loudspeaker.enterBad(), loudspeaker.leaveBad())) continue;
            playFrom(context, speaker, from, range, loudspeaker);
        }
    }

    private boolean dropped(UUID speaker, Object listener, double enterBad, double leaveBad) {
        DropoutModel.Chain chain = chains
            .computeIfAbsent(new Pair(speaker, listener), pair -> new DropoutModel.Chain());
        return chain.drop(enterBad, leaveBad, random.getAsDouble());
    }

    /**
     * Positional from a Videophone to everyone within its loudspeaker's range, minus the speaker and whoever hears
     * them directly (D17). VOICE.md V3: GTNH Voice plays it with its own falloff, so BeCom can only stop sending past
     * the range.
     */
    private static void playFrom(RoutingContext context, UUID speaker, PlayerSnapshot from, double direct,
        CallSnapshot.Loudspeaker loudspeaker) {
        // VOICE.md V3
        double rangeSq = loudspeaker.range() * loudspeaker.range();
        Map<UUID, IVoiceSession> sessions = context.getSessionsByPlayerUuid();
        for (PlayerSnapshot p : context.getPositionSnapshot()
            .values()) {
            if (p.getDimensionId() != loudspeaker.dimension()) continue;
            UUID id = p.getUuid();
            if (id.equals(speaker)) continue;
            double dx = p.getX() - loudspeaker.x();
            double dy = p.getY() - loudspeaker.y();
            double dz = p.getZ() - loudspeaker.z();
            if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
            IVoiceSession session = sessions.get(id);
            if (session == null || DirectHearing.hears(from, p, direct)) continue;
            context.sendTo(session, SourceState.POSITIONAL, loudspeaker.x(), loudspeaker.y(), loudspeaker.z());
        }
    }

    @Override
    public void onPlayerRemoved(@NotNull UUID playerUuid) {
        chains.keySet()
            .removeIf(pair -> pair.speaker.equals(playerUuid) || playerUuid.equals(pair.listener));
    }

    @Override
    public void clear() {
        chains.clear();
        snapshot = CallSnapshot.EMPTY;
    }

    /** How many speaker-listener and speaker-loudspeaker loss chains are held; for tests and the self-test. */
    public int chainCount() {
        return chains.size();
    }
}
