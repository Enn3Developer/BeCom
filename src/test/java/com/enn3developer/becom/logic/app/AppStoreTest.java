package com.enn3developer.becom.logic.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AppStoreTest {

    private static final UUID STEVE = new UUID(1, 1);
    private static final UUID ALEX = new UUID(2, 2);
    private static final String REACTOR = "--@name Reactor\n--@version 1.2\nreturn {}";

    @Test
    void anAuthorPublishesAndUpdatesTheirOwnApp() {
        AppStore store = new AppStore();
        assertEquals(AppStore.Published.NEW, store.publish(STEVE, "Steve", REACTOR, 1));
        store.installed("reactor");
        assertEquals(
            AppStore.Published.UPDATED,
            store.publish(STEVE, "Steve", "--@name Reactor\n--@version 1.3\nreturn {}", 2));
        assertEquals(
            "1.3",
            store.get("reactor")
                .version());
        // An update keeps its installs.
        assertEquals(
            1,
            store.get("reactor")
                .installs());
        assertEquals(AppStore.Published.NOT_YOURS, store.publish(ALEX, "Alex", REACTOR, 3));
    }

    @Test
    void notAnAppOrTooBigIsRefused() {
        AppStore store = new AppStore();
        assertEquals(AppStore.Published.NOT_AN_APP, store.publish(STEVE, "Steve", "print(1)", 1));
        assertEquals(
            AppStore.Published.TOO_BIG,
            store.publish(STEVE, "Steve", "--@name Big\n" + "x".repeat(AppManifest.MAX_SOURCE), 1));
    }

    @Test
    void hiddenAppsLeaveTheSearchAndPopularOnesComeFirst() {
        AppStore store = new AppStore();
        store.publish(STEVE, "Steve", REACTOR, 1);
        store.publish(ALEX, "Alex", "--@name Base Lights\nreturn {}", 1);
        store.installed("base_lights");
        assertEquals(
            "base_lights",
            store.search("")
                .get(0)
                .id());
        assertEquals(
            1,
            store.search("steve")
                .size());
        assertTrue(store.setHidden("reactor", true));
        assertEquals(
            1,
            store.search("")
                .size());
        assertTrue(store.remove("reactor"));
        assertFalse(store.remove("reactor"));
    }

    @Test
    void aPhoneKeepsAnAppsSettingsAcrossAnUpdate() {
        PhoneApps apps = new PhoneApps();
        assertEquals(PhoneApps.Installed.NEW, apps.install(REACTOR, "Steve", PhoneApps.Origin.STORE));
        apps.replace(
            apps.get("reactor")
                .withBackground(true));
        assertEquals(
            PhoneApps.Installed.UPDATED,
            apps.install("--@name Reactor\n--@version 2\n", "Steve", PhoneApps.Origin.STORE));
        assertTrue(
            apps.get("reactor")
                .background());
        assertEquals(
            "2",
            apps.get("reactor")
                .manifest()
                .version());
        assertTrue(apps.uninstall("reactor"));
        assertTrue(apps.isEmpty());
    }

    @Test
    void aPhoneHoldsSoManyApps() {
        PhoneApps apps = new PhoneApps();
        for (int i = 0; i < AppLimits.DEFAULTS.appsPerPhone(); i++) {
            assertEquals(PhoneApps.Installed.NEW, apps.install("--@name A" + i + "\n", "x", PhoneApps.Origin.STORE));
        }
        assertEquals(PhoneApps.Installed.FULL, apps.install("--@name Extra\n", "x", PhoneApps.Origin.STORE));
    }

    @Test
    void wakesCountPerDay() {
        PhoneApps.App app = PhoneApps.app(REACTOR, "Steve", PhoneApps.Origin.STORE)
            .woke(5)
            .woke(5);
        assertEquals(2, app.woke());
        assertEquals(
            1,
            app.woke(6)
                .woke());
    }

    @Test
    void aRemovedAppsNameStaysOutForEveryone() {
        AppStore store = new AppStore();
        store.publish(STEVE, "Steve", REACTOR, 1);
        assertTrue(store.remove("reactor"));
        assertEquals(AppStore.Published.REMOVED, store.publish(STEVE, "Steve", REACTOR, 2));
        assertEquals(AppStore.Published.REMOVED, store.publish(ALEX, "Alex", REACTOR, 3));
        assertFalse(store.remove("reactor"));
    }

    @Test
    void oneAuthorCantFillTheStore() {
        AppStore store = new AppStore();
        for (int i = 0; i < AppLimits.DEFAULTS.storeAppsPerAuthor(); i++) {
            assertEquals(AppStore.Published.NEW, store.publish(STEVE, "Steve", "--@name App " + i + "\nreturn {}", i));
        }
        assertEquals(AppStore.Published.FULL, store.publish(STEVE, "Steve", "--@name One more\nreturn {}", 99));
        // Updating one of them still works, and others can still publish.
        assertEquals(AppStore.Published.UPDATED, store.publish(STEVE, "Steve", "--@name App 0\nreturn {}", 100));
        assertEquals(AppStore.Published.NEW, store.publish(ALEX, "Alex", REACTOR, 101));
    }

    @Test
    void anAppOfTheSameNameBySomeoneElseDoesntReplaceAnInstalledOne() {
        PhoneApps apps = new PhoneApps();
        apps.install(REACTOR, "Steve", PhoneApps.Origin.STORE);
        apps.replace(
            apps.get("reactor")
                .withBackground(true));
        assertEquals(
            PhoneApps.Installed.OTHER_AUTHOR,
            apps.install("--@name Reactor\n--@version 9\n", "Mallory", new PhoneApps.Origin(false, "1, 2, 3")));
        assertEquals(
            "Steve",
            apps.get("reactor")
                .author());
        assertTrue(
            apps.get("reactor")
                .background());
    }

    @Test
    void anAppSaysWhatItUses() {
        AppManifest m = AppManifest.parse("--@name Lights\n--@uses net,notify  camera bogus\nreturn {}");
        assertTrue(m.uses(AppManifest.Use.NET));
        assertTrue(m.uses(AppManifest.Use.NOTIFY));
        assertTrue(m.uses(AppManifest.Use.CAMERA));
        assertFalse(m.uses(AppManifest.Use.STORAGE));
        assertFalse(m.uses(AppManifest.Use.CALL));
        // An app that doesn't say gets nothing.
        assertEquals(
            0,
            AppManifest.parse(REACTOR)
                .uses());
    }

    @Test
    void aTeamsAppIsForItsTeamAndNewestComesFirst() {
        AppStore store = new AppStore();
        store.publish(STEVE, "Steve", REACTOR, 1);
        store.publish(ALEX, "Alex", "--@name Base Lights\nreturn {}", 5, true);
        // The plain search is everyone's: no team apps in it.
        assertEquals(
            List.of("reactor"),
            store.search("")
                .stream()
                .map(AppStore.Entry::id)
                .toList());
        assertEquals(
            List.of("base_lights", "reactor"),
            store.search("", AppStore.Order.NEW, e -> true)
                .stream()
                .map(AppStore.Entry::id)
                .toList());
    }

    @Test
    void aPlayerReportsAnAppOnceAndRemovingItClearsTheReports() {
        AppStore store = new AppStore();
        store.publish(STEVE, "Steve", REACTOR, 1);
        assertTrue(store.report("reactor", ALEX));
        assertFalse(store.report("reactor", ALEX));
        assertFalse(store.report("nothing", ALEX));
        assertTrue(store.reported("reactor", ALEX));
        assertFalse(store.reported("reactor", STEVE));
        store.remove("reactor");
        assertTrue(
            store.reports()
                .isEmpty());
    }
}
