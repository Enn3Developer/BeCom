package com.enn3developer.becom.logic.app;

import com.enn3developer.becom.logic.Tunables;

/**
 * How much apps may take, from the server's config: apps per phone, a source's size, and the App Store's room in all
 * and per author. The hard ceilings ({@link #MAX_APPS_PER_PHONE}, {@link AppManifest#MAX_SOURCE}) bound what any config
 * can ask for, and what's already saved is never cut down to a lower limit.
 */
public record AppLimits(int appsPerPhone, int sourceBytes, int storeApps, int storeAppsPerAuthor) {

    /** What a phone's screen can list. */
    public static final int MAX_APPS_PER_PHONE = 64;

    public static final AppLimits DEFAULTS = of(Tunables.defaults());

    public static AppLimits of(Tunables t) {
        return new AppLimits(
            Math.min(t.appsPerPhone(), MAX_APPS_PER_PHONE),
            Math.min(t.appSourceKB() * 1024, AppManifest.MAX_SOURCE),
            t.storeApps(),
            t.storeAppsPerAuthor());
    }

    /** Whether a source fits. */
    public boolean fits(String source) {
        return source != null && source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= sourceBytes;
    }
}
