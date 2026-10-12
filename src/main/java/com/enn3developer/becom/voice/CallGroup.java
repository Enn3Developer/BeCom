package com.enn3developer.becom.voice;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

import org.jetbrains.annotations.NotNull;

import com.enn3developer.becom.logic.DropoutModel;
import com.enn3developer.gtnhvoice.api.server.IVoiceSession;
import com.enn3developer.gtnhvoice.api.server.PlayerSnapshot;
import com.enn3developer.gtnhvoice.api.server.SourceState;
import com.enn3developer.gtnhvoice.api.server.group.IGroup;
import com.enn3developer.gtnhvoice.api.server.group.RoutingContext;

/**
 * {@code becom:call} (DESIGN.md 13.1, 13.3): carries every call's voice, flat, to the others in the call, minus those
 * who hear the speaker directly and frames the dropout model eats.
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
    private final DoubleSupplier random;
    private volatile CallSnapshot snapshot = CallSnapshot.EMPTY;
    private final Map<Pair, DropoutModel.Chain> chains = new ConcurrentHashMap<>();

    /**
     * @param random uniform numbers in [0, 1) for the dropout model; must be safe on the network thread
     */
    public CallGroup(String displayName, double directRange, DoubleSupplier random) {
        this.displayName = displayName;
        this.directRange = directRange;
        this.random = random;
    }

    private record Pair(UUID speaker, UUID listener) {}

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
        if (routes.length == 0) return;
        Map<UUID, PlayerSnapshot> positions = context.getPositionSnapshot();
        Map<UUID, IVoiceSession> sessions = context.getSessionsByPlayerUuid();
        PlayerSnapshot from = positions.get(speaker);
        double range = DirectHearing.range(directRange);
        for (CallSnapshot.Route route : routes) {
            IVoiceSession listener = sessions.get(route.listener());
            if (listener == null) continue;
            if (DirectHearing.hears(from, positions.get(route.listener()), range)) continue;
            DropoutModel.Chain chain = chains
                .computeIfAbsent(new Pair(speaker, route.listener()), pair -> new DropoutModel.Chain());
            if (chain.drop(route.enterBad(), route.leaveBad(), random.getAsDouble())) continue;
            double x = from == null ? 0 : from.getX();
            double y = from == null ? 0 : from.getY();
            double z = from == null ? 0 : from.getZ();
            context.sendTo(listener, SourceState.FLAT, x, y, z);
        }
    }

    @Override
    public void onPlayerRemoved(@NotNull UUID playerUuid) {
        chains.keySet()
            .removeIf(pair -> pair.speaker.equals(playerUuid) || pair.listener.equals(playerUuid));
    }

    @Override
    public void clear() {
        chains.clear();
        snapshot = CallSnapshot.EMPTY;
    }

    /** How many speaker-listener loss chains are held; for tests and the self-test. */
    public int chainCount() {
        return chains.size();
    }
}
