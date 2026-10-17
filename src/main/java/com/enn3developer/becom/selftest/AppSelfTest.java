package com.enn3developer.becom.selftest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import com.enn3developer.becom.apps.AppService;
import com.enn3developer.becom.item.BeComItems;
import com.enn3developer.becom.logic.app.AppStore;
import com.enn3developer.becom.logic.app.AppTree;
import com.enn3developer.becom.logic.app.PhoneApps;
import com.enn3developer.becom.server.BeComServer;
import com.mojang.authlib.GameProfile;

import ic2.api.item.ElectricItem;

/**
 * Phone apps in a running server (how phone apps work): a carried phone's OpenComputers machine boots BeCom OS, runs
 * the example Notes app and shows its widgets, takes a button press, shows a crashing app as crashed, installs from
 * the App Store, and stops once nothing is on screen.
 */
final class AppSelfTest {

    private static final int TICKS_PER_SECOND = 20;
    // Booting OpenComputers' Lua takes a moment; the checks wait this long, then look.
    private static final int BOOT_TICKS = 5 * TICKS_PER_SECOND;

    private final SelfTest t;
    private final BeComServer becom;
    private final WorldServer overworld;
    private FakePlayer user;
    private int number = -1;

    AppSelfTest(SelfTest t, BeComServer becom) {
        this.t = t;
        this.becom = becom;
        this.overworld = DimensionManager.getWorld(0);
    }

    List<SelfTest.Step> steps() {
        List<SelfTest.Step> steps = new ArrayList<>();
        steps.add(this::openNotes);
        steps.add(this::checkNotesShown);
        steps.add(this::checkButton);
        steps.add(this::openMeddler);
        steps.add(this::reopenNotes);
        steps.add(this::checkOsUnharmed);
        steps.add(this::openNosy);
        steps.add(this::openHoarder);
        steps.add(this::checkHoarder);
        steps.add(this::openBoom);
        steps.add(this::checkCrashed);
        steps.add(this::closeApp);
        steps.add(this::checkStopped);
        steps.add(this::checkStore);
        return steps;
    }

    private int openNotes() {
        user = FakePlayerFactory
            .get(overworld, new GameProfile(UUID.fromString("b3c0b3c0-0000-4000-8000-000000000501"), "BeComAppUser"));
        user.setPosition(SelfTest.X0 - 300.5, SelfTest.Y, SelfTest.Z0 - 300.5);
        java.util.Arrays.fill(user.inventory.mainInventory, null);
        user.inventory.currentItem = 0;
        ItemStack phone = new ItemStack(BeComItems.mobilePhone);
        ElectricItem.manager.charge(phone, BeComItems.mobilePhone.getMaxCharge(phone), Integer.MAX_VALUE, true, false);
        user.inventory.mainInventory[0] = phone;
        becom.carried.presentForSelfTest(user);
        becom.calls.presentForSelfTest(user);
        becom.carried.refreshForSelfTest(user);
        number = becom.carried.numberFor(user, phone);
        String source = resource("/assets/becom/lua/tools/apps/notes.lua");
        PhoneApps.Installed installed = source == null ? null : becom.apps.installForSelfTest(number, source);
        t.check(
            "the example Notes app installs on a phone",
            installed == PhoneApps.Installed.NEW,
            String.valueOf(installed));
        becom.apps.open(user, number, "notes");
        t.check(
            "opening an app starts the phone's OpenComputers machine",
            becom.apps.runningForSelfTest(number),
            "number " + number);
        return BOOT_TICKS;
    }

    private int checkNotesShown() {
        AppService.Screen screen = becom.apps.screen(number);
        t.check(
            "BeCom OS boots and the app's widgets reach the phone screen",
            screen != null && screen.state() == AppService.State.RUNNING
                && !screen.widgets()
                    .isEmpty()
                && screen.widgets()
                    .get(0)
                    .kind() == AppTree.Kind.HEADING
                && "Notes".equals(
                    screen.widgets()
                        .get(0)
                        .label()),
            describe(screen));
        becom.apps.event(number, "notes", "add", "");
        return 2 * TICKS_PER_SECOND;
    }

    private int checkButton() {
        AppService.Screen screen = becom.apps.screen(number);
        String counter = valueOf(screen, "Counter");
        t.check("a button press reaches the app, which draws again", "1".equals(counter), describe(screen));
        return 1;
    }

    // An app that overwrites what the OS itself uses, in what used to be shared globals: the OS's table.pack reads
    // every signal, so a hook there would see other apps' data and could fake which app is on screen.
    private static final String MEDDLER = """
        --@name Meddler
        local fake = function() return { n = 2, "becom_open", "meddler" } end
        for _, lib in ipairs({ table, math, string }) do
          local mt = getmetatable(lib)
          if mt and type(mt.__index) == "table" then mt.__index.pack = fake mt.__index.floor = fake end
        end
        table.pack = fake
        math.floor = function() return 0 end
        string.rep = nil
        return { draw = function() return { { type = "text", text = "meddled" } } end }
        """;

    private int openMeddler() {
        becom.apps.installForSelfTest(number, MEDDLER.strip());
        becom.apps.close(number);
        becom.apps.open(user, number, "meddler");
        return 2 * TICKS_PER_SECOND;
    }

    private int reopenNotes() {
        AppService.Screen screen = becom.apps.screen(number);
        t.check(
            "an app that rewrites its own table and math libraries still runs",
            screen != null && screen.state() == AppService.State.RUNNING && "meddled".equals(valueOrLabel(screen)),
            describe(screen));
        becom.apps.close(number);
        becom.apps.open(user, number, "notes");
        becom.apps.event(number, "notes", "add", "");
        return 2 * TICKS_PER_SECOND;
    }

    private int checkOsUnharmed() {
        AppService.Screen screen = becom.apps.screen(number);
        t.check(
            "...and BeCom OS and the other apps don't see it: Notes opens and counts on",
            screen != null && screen.state() == AppService.State.RUNNING
                && "notes".equals(screen.id())
                && "2".equals(valueOf(screen, "Counter")),
            describe(screen));
        becom.apps.uninstall(number, "meddler");
        return 1;
    }

    // An app that doesn't say it uses storage, and one that does but writes more than an app may keep: each shows
    // what the phone told it.
    private static final String NOSY = """
        --@name Nosy
        local _, why = phone.write("note", "hello")
        return { draw = function() return { { type = "text", text = tostring(why) } } end }
        """;

    private static final String HOARDER = """
        --@name Hoarder
        --@uses storage
        local small = phone.write("small", "hello")
        local _, why = phone.write("big", string.rep("x", 65 * 1024))
        return { draw = function() return { { type = "text", text = tostring(small) .. " " .. tostring(why) } } end }
        """;

    private int openNosy() {
        becom.apps.installForSelfTest(number, NOSY.strip());
        becom.apps.close(number);
        becom.apps.open(user, number, "nosy");
        return 2 * TICKS_PER_SECOND;
    }

    private int openHoarder() {
        AppService.Screen screen = becom.apps.screen(number);
        String text = screen == null ? null : valueOrLabel(screen);
        t.check(
            "an app that doesn't say --@uses storage can't write a file",
            text != null && text.contains("not allowed"),
            describe(screen));
        becom.apps.uninstall(number, "nosy");
        becom.apps.installForSelfTest(number, HOARDER.strip());
        becom.apps.close(number);
        becom.apps.open(user, number, "hoarder");
        return 2 * TICKS_PER_SECOND;
    }

    private int checkHoarder() {
        AppService.Screen screen = becom.apps.screen(number);
        t.check(
            "an app that uses storage keeps a small file, but not more than its share",
            screen != null && "true storage full".equals(valueOrLabel(screen)),
            describe(screen));
        becom.apps.uninstall(number, "hoarder");
        return 1;
    }

    private static String valueOrLabel(AppService.Screen screen) {
        return screen.widgets()
            .isEmpty() ? null
                : screen.widgets()
                    .get(0)
                    .label();
    }

    private int openBoom() {
        becom.apps.installForSelfTest(number, "--@name Boom\nreturn { draw = function() error(\"kaboom\") end }");
        becom.apps.close(number);
        becom.apps.open(user, number, "boom");
        return 2 * TICKS_PER_SECOND;
    }

    private int checkCrashed() {
        AppService.Screen screen = becom.apps.screen(number);
        t.check(
            "an app that errors shows as crashed, with its error",
            screen != null && screen.state() == AppService.State.CRASHED
                && screen.error()
                    .contains("kaboom"),
            describe(screen));
        return 1;
    }

    private int closeApp() {
        becom.apps.close(number);
        return 2 * TICKS_PER_SECOND;
    }

    private int checkStopped() {
        t.check(
            "with nothing on screen and nothing in the background, the machine stops",
            !becom.apps.runningForSelfTest(number),
            "");
        return 1;
    }

    private int checkStore() {
        AppStore.Published published = becom.apps.store()
            .publish(user.getUniqueID(), "BeComAppUser", "--@name Self Test Lights\n--@version 2\nreturn {}", 0);
        becom.apps.installFromStore(user, number, "self_test_lights");
        t.check(
            "an app published to the App Store installs on a phone, and counts the install",
            (published == AppStore.Published.NEW || published == AppStore.Published.UPDATED)
                && becom.apps.app(number, "self_test_lights") != null
                && becom.apps.store()
                    .get("self_test_lights")
                    .installs() >= 1,
            String.valueOf(published));
        // Left in the test world's store: removing it would keep its name out for good, and the next run needs it.
        becom.apps.uninstall(number, "self_test_lights");
        becom.apps.uninstall(number, "boom");
        becom.apps.uninstall(number, "notes");
        user.inventory.mainInventory[0] = null;
        becom.carried.refreshForSelfTest(user);
        return 1;
    }

    private static String valueOf(AppService.Screen screen, String label) {
        if (screen == null) return null;
        for (AppTree.Widget w : screen.widgets()) if (label.equals(w.label())) return w.value();
        return null;
    }

    private static String describe(AppService.Screen screen) {
        if (screen == null) return "no screen";
        return screen.state() + " " + screen.error() + " " + screen.widgets();
    }

    private static String resource(String path) {
        try (InputStream in = AppSelfTest.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
