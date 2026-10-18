package com.enn3developer.becom.logic.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Lua apps installed on one phone, in install order, and each one's settings: whether it runs in the background,
 * and how often it woke today and what it cost. Pure, so the rules are tested without a world.
 */
public final class PhoneApps {

    /** Where an app came from: the App Store, or a computer at a position. */
    public record Origin(boolean store, String where) {

        public static final Origin STORE = new Origin(true, "");
    }

    /**
     * One installed app. {@code denied} holds the bits of the {@link AppManifest.Use}s its holder switched off in its
     * settings: it says it uses them, but may not.
     */
    public record App(AppManifest manifest, String source, String author, Origin origin, boolean background, long day,
        int woke, long euToday, int denied) {

        /** Whether it may use {@code use}: it says so, and its holder didn't switch it off. */
        public boolean allows(AppManifest.Use use) {
            return manifest.uses(use) && (denied & use.bit()) == 0;
        }

        public App withAllowed(AppManifest.Use use, boolean on) {
            int bits = on ? denied & ~use.bit() : denied | use.bit();
            return new App(manifest, source, author, origin, background, day, woke, euToday, bits);
        }

        public String id() {
            return manifest.id();
        }

        public int size() {
            return source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }

        public App withBackground(boolean on) {
            return new App(manifest, source, author, origin, on, day, woke, euToday, denied);
        }

        /** Counts a wake on {@code today}, starting over on a new day. */
        public App woke(long today) {
            return today == day ? new App(manifest, source, author, origin, background, day, woke + 1, euToday, denied)
                : new App(manifest, source, author, origin, background, today, 1, 0, denied);
        }

        public App used(long today, long eu) {
            return today == day ? new App(manifest, source, author, origin, background, day, woke, euToday + eu, denied)
                : new App(manifest, source, author, origin, background, today, 0, eu, denied);
        }
    }

    public enum Installed {
        NEW,
        UPDATED,
        FULL,
        NOT_AN_APP,
        TOO_BIG,
        /** An app of that name by someone else is installed: it's left alone, uninstall it first. */
        OTHER_AUTHOR
    }

    private final List<App> apps = new ArrayList<>();
    private AppLimits limits = AppLimits.DEFAULTS;

    /** The server's limits, for installs from now on. */
    public void setLimits(AppLimits limits) {
        this.limits = limits;
    }

    public static App app(String source, String author, Origin origin) {
        AppManifest manifest = AppManifest.parse(source);
        return manifest == null ? null : new App(manifest, source, author, origin, false, 0, 0, 0, 0);
    }

    /**
     * Installs {@code app}, replacing one of its id by the same author and keeping that one's settings and place. One
     * by
     * someone else stays: an app named like another must not take its place, nor its background permission.
     */
    public Installed install(String source, String author, Origin origin) {
        if (!limits.fits(source)) return Installed.TOO_BIG;
        App app = app(source, author, origin);
        if (app == null) return Installed.NOT_AN_APP;
        for (int i = 0; i < apps.size(); i++) {
            App old = apps.get(i);
            if (old.id()
                .equals(app.id())) {
                if (!old.author()
                    .equals(author)) return Installed.OTHER_AUTHOR;
                apps.set(
                    i,
                    new App(
                        app.manifest(),
                        source,
                        author,
                        origin,
                        old.background(),
                        old.day(),
                        old.woke(),
                        old.euToday(),
                        old.denied()));
                return Installed.UPDATED;
            }
        }
        if (apps.size() >= limits.appsPerPhone()) return Installed.FULL;
        apps.add(app);
        return Installed.NEW;
    }

    public void restore(App app) {
        if (apps.size() < AppLimits.MAX_APPS_PER_PHONE && get(app.id()) == null) apps.add(app);
    }

    public App get(String id) {
        for (App app : apps) if (app.id()
            .equals(id)) return app;
        return null;
    }

    public void replace(App app) {
        for (int i = 0; i < apps.size(); i++) if (apps.get(i)
            .id()
            .equals(app.id())) apps.set(i, app);
    }

    public boolean uninstall(String id) {
        return apps.removeIf(
            app -> app.id()
                .equals(id));
    }

    public List<App> all() {
        return Collections.unmodifiableList(apps);
    }

    public boolean isEmpty() {
        return apps.isEmpty();
    }
}
