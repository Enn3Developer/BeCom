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

    public static final CallSnapshot EMPTY = new CallSnapshot(Collections.emptyMap());

    /**
     * One listener of one speaker: who, and the burst-loss chain's chances for the direction's SNR (DESIGN.md 4.2).
     * Only directions at Low or better get a route.
     */
    public record Route(UUID listener, double enterBad, double leaveBad) {}

    private final Map<UUID, Route[]> routes;

    private CallSnapshot(Map<UUID, Route[]> routes) {
        this.routes = routes;
    }

    /** The routes for {@code speaker}'s frames, or an empty array. */
    public Route[] routesOf(UUID speaker) {
        Route[] found = routes.get(speaker);
        return found == null ? new Route[0] : found;
    }

    /** Whether {@code speaker} still has {@code listener} as a route; used to drop stale per-pair state. */
    public boolean hasPair(UUID speaker, UUID listener) {
        Route[] found = routes.get(speaker);
        if (found == null) return false;
        for (Route route : found) if (route.listener()
            .equals(listener)) return true;
        return false;
    }

    public int speakers() {
        return routes.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<UUID, List<Route>> routes = new HashMap<>();

        public Builder route(UUID speaker, Route route) {
            routes.computeIfAbsent(speaker, k -> new ArrayList<>())
                .add(route);
            return this;
        }

        public CallSnapshot build() {
            Map<UUID, Route[]> frozen = new HashMap<>();
            for (Map.Entry<UUID, List<Route>> entry : routes.entrySet()) {
                frozen.put(
                    entry.getKey(),
                    entry.getValue()
                        .toArray(new Route[0]));
            }
            return new CallSnapshot(Collections.unmodifiableMap(frozen));
        }
    }
}
