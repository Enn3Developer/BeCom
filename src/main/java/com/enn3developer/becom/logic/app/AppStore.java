package com.enn3developer.becom.logic.app;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The server-wide App Store: apps published from computers, kept in BeCom's world data. An app is its author's: only
 * they publish a new version of it. Operators moderate it, hiding an app from everyone or removing it. Pure, so the
 * rules are tested without a world.
 */
public final class AppStore {

    // The server's limits: the store's room, and each author's share of it so no one player fills it for everyone.
    private AppLimits limits = AppLimits.DEFAULTS;

    public void setLimits(AppLimits limits) {
        this.limits = limits;
    }

    /**
     * One published app, at its latest version: {@code published} when, in milliseconds, and {@code team} when only its
     * author's team sees it.
     */
    public record Entry(String id, String name, String version, int accent, String icon, String about, UUID author,
        String authorName, String source, long published, int installs, boolean hidden, boolean team) {

        public int size() {
            return source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }

        Entry with(int installs, boolean hidden) {
            return new Entry(
                id,
                name,
                version,
                accent,
                icon,
                about,
                author,
                authorName,
                source,
                published,
                installs,
                hidden,
                team);
        }

        public AppManifest manifest() {
            return AppManifest.parse(source);
        }
    }

    public enum Published {
        NEW,
        UPDATED,
        NOT_AN_APP,
        TOO_BIG,
        NOT_YOURS,
        FULL,
        /** An operator took that app out of the store: its id stays out. */
        REMOVED
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    // The ids operators removed, kept so nobody, its author or anyone else, publishes under them again.
    private final Set<String> removed = new java.util.TreeSet<>();
    // Who reported each app, for operators to look at; one report a player.
    private final Map<String, Set<UUID>> reports = new java.util.TreeMap<>();
    private long version;

    public Published publish(UUID author, String authorName, String source, long now) {
        return publish(author, authorName, source, now, false);
    }

    /** Publishes for everyone, or with {@code team} for its author's team only. */
    public Published publish(UUID author, String authorName, String source, long now, boolean team) {
        if (!limits.fits(source)) return Published.TOO_BIG;
        AppManifest manifest = AppManifest.parse(source);
        if (manifest == null) return Published.NOT_AN_APP;
        if (removed.contains(manifest.id())) return Published.REMOVED;
        Entry old = entries.get(manifest.id());
        if (old != null && !old.author()
            .equals(author)) return Published.NOT_YOURS;
        if (old == null && (entries.size() >= limits.storeApps() || byAuthor(author) >= limits.storeAppsPerAuthor()))
            return Published.FULL;
        entries.put(
            manifest.id(),
            new Entry(
                manifest.id(),
                manifest.name(),
                manifest.version(),
                manifest.accent(),
                manifest.icon(),
                manifest.about(),
                author,
                authorName,
                source,
                now,
                old == null ? 0 : old.installs(),
                old != null && old.hidden(),
                team));
        version++;
        return old == null ? Published.NEW : Published.UPDATED;
    }

    private int byAuthor(UUID author) {
        int count = 0;
        for (Entry e : entries.values()) if (e.author()
            .equals(author)) count++;
        return count;
    }

    public void restore(Entry entry) {
        entries.put(entry.id(), entry);
        version++;
    }

    public Entry get(String id) {
        return entries.get(id);
    }

    /** One more install of an app, for its page's count. */
    public void installed(String id) {
        Entry e = entries.get(id);
        if (e == null) return;
        entries.put(id, e.with(e.installs() + 1, e.hidden()));
        version++;
    }

    /** Hides or shows an app for everyone but operators; false when there's none. */
    public boolean setHidden(String id, boolean hidden) {
        Entry e = entries.get(id);
        if (e == null) return false;
        entries.put(id, e.with(e.installs(), hidden));
        version++;
        return true;
    }

    /** Takes an app out for good; false when there's none. */
    public boolean remove(String id) {
        if (entries.remove(id) == null) return false;
        removed.add(id);
        reports.remove(id);
        version++;
        return true;
    }

    /** The ids operators removed. */
    public Set<String> removed() {
        return java.util.Collections.unmodifiableSet(removed);
    }

    public void restoreRemoved(String id) {
        removed.add(id);
    }

    /** A player reports an app to the operators; false when there's none, or they already did. */
    public boolean report(String id, UUID player) {
        if (!entries.containsKey(id) || player == null) return false;
        boolean added = reports.computeIfAbsent(id, k -> new java.util.HashSet<>())
            .add(player);
        if (added) version++;
        return added;
    }

    public boolean reported(String id, UUID player) {
        Set<UUID> by = reports.get(id);
        return by != null && by.contains(player);
    }

    /** Who reported each app, by id; apps nobody reported aren't in it. */
    public Map<String, Set<UUID>> reports() {
        return java.util.Collections.unmodifiableMap(reports);
    }

    public void restoreReport(String id, UUID player) {
        reports.computeIfAbsent(id, k -> new java.util.HashSet<>())
            .add(player);
    }

    /** How a store list is ordered: most installed first, or most recently published. */
    public enum Order {
        POPULAR,
        NEW
    }

    /** What a phone's store shows: visible apps matching {@code query} by name or author, most installed first. */
    public List<Entry> search(String query) {
        return search(query, Order.POPULAR, e -> !e.team());
    }

    /**
     * Apps matching {@code query} by name or author that aren't hidden and that {@code sees} (a team's apps are for
     * its team), in {@code order}.
     */
    public List<Entry> search(String query, Order order, java.util.function.Predicate<Entry> sees) {
        String q = query == null ? ""
            : query.strip()
                .toLowerCase(Locale.ROOT);
        List<Entry> found = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (e.hidden() || !sees.test(e)) continue;
            if (q.isEmpty() || e.name()
                .toLowerCase(Locale.ROOT)
                .contains(q)
                || e.authorName()
                    .toLowerCase(Locale.ROOT)
                    .contains(q))
                found.add(e);
        }
        found.sort(
            order == Order.NEW ? Comparator.comparingLong(Entry::published)
                .reversed()
                .thenComparing(Entry::name)
                : Comparator.comparingInt(Entry::installs)
                    .reversed()
                    .thenComparing(Entry::name));
        return found;
    }

    public Collection<Entry> all() {
        return entries.values();
    }

    public long version() {
        return version;
    }
}
