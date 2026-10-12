package com.enn3developer.becom.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What {@code becom:call} routes from, built on the server thread whenever a call changes and handed to the network
 * thread whole (DESIGN.md 13.3). Immutable: once published it is never touched again.
 */
public final class CallSnapshot {

    public static final CallSnapshot EMPTY = new CallSnapshot(Collections.emptyMap(), Collections.emptyMap());

    /**
     * One listener of one speaker: who, and the burst-loss chain's chances for the direction's SNR (DESIGN.md 4.2).
     * Only directions at Low or better get a route.
     */
    public record Route(UUID listener, double enterBad, double leaveBad) {}

    /**
     * A Videophone at the other end (DESIGN.md 10.2): the speaker plays from its loudspeaker, at the block, to everyone
     * within {@code range} of it. The dropout chain is the phone's, so everyone around it hears the same gaps.
     */
    public record Loudspeaker(int phone, int dimension, double x, double y, double z, double range, double enterBad,
        double leaveBad) {}

    private static final Route[] NO_ROUTES = new Route[0];
    private static final Loudspeaker[] NO_LOUDSPEAKERS = new Loudspeaker[0];

    private final Map<UUID, Route[]> routes;
    private final Map<UUID, Loudspeaker[]> loudspeakers;

    private CallSnapshot(Map<UUID, Route[]> routes, Map<UUID, Loudspeaker[]> loudspeakers) {
        this.routes = routes;
        this.loudspeakers = loudspeakers;
    }

    /** The flat routes for {@code speaker}'s frames, or an empty array. */
    public Route[] routesOf(UUID speaker) {
        Route[] found = routes.get(speaker);
        return found == null ? NO_ROUTES : found;
    }

    /** The loudspeakers {@code speaker}'s frames play from, or an empty array. */
    public Loudspeaker[] loudspeakersOf(UUID speaker) {
        Loudspeaker[] found = loudspeakers.get(speaker);
        return found == null ? NO_LOUDSPEAKERS : found;
    }

    /**
     * Whether {@code speaker} still reaches {@code listener}: a player's UUID for a flat route, a phone number for a
     * loudspeaker. Used to drop stale per-pair state.
     */
    public boolean hasPair(UUID speaker, Object listener) {
        if (listener instanceof UUID player) {
            for (Route route : routesOf(speaker)) if (route.listener()
                .equals(player)) return true;
        } else if (listener instanceof Integer phone) {
            for (Loudspeaker loudspeaker : loudspeakersOf(speaker)) if (loudspeaker.phone() == phone) return true;
        }
        return false;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<UUID, List<Route>> routes = new HashMap<>();
        private final Map<UUID, List<Loudspeaker>> loudspeakers = new HashMap<>();

        public Builder route(UUID speaker, Route route) {
            routes.computeIfAbsent(speaker, k -> new ArrayList<>())
                .add(route);
            return this;
        }

        public Builder loudspeaker(UUID speaker, Loudspeaker loudspeaker) {
            loudspeakers.computeIfAbsent(speaker, k -> new ArrayList<>())
                .add(loudspeaker);
            return this;
        }

        public CallSnapshot build() {
            Map<UUID, Route[]> frozenRoutes = new HashMap<>();
            for (Map.Entry<UUID, List<Route>> entry : routes.entrySet()) {
                frozenRoutes.put(
                    entry.getKey(),
                    entry.getValue()
                        .toArray(new Route[0]));
            }
            Map<UUID, Loudspeaker[]> frozenLoudspeakers = new HashMap<>();
            for (Map.Entry<UUID, List<Loudspeaker>> entry : loudspeakers.entrySet()) {
                frozenLoudspeakers.put(
                    entry.getKey(),
                    entry.getValue()
                        .toArray(new Loudspeaker[0]));
            }
            return new CallSnapshot(
                Collections.unmodifiableMap(frozenRoutes),
                Collections.unmodifiableMap(frozenLoudspeakers));
        }
    }
}
