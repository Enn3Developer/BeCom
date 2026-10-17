package com.enn3developer.becom.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.app.AppManifest;
import com.enn3developer.becom.logic.app.AppTree;
import com.enn3developer.becom.server.PhoneHandle;
import com.enn3developer.becom.server.PhoneScreenActions;

/**
 * A carried phone's app pages (the phone app kit): the open Lua app, drawn from its widgets in BeCom's style with the
 * states the phone owns (loading, can't reach it, crashed); Settings › Apps and each app's settings; the App Store
 * and an app's page there; and an install a computer offers. The app never draws pixels: it sends widgets, and these
 * pages lay them out a row each, paging a long list.
 */
final class AppScreens {

    static final String OPEN = "app_open";
    static final String CLOSE = "app_close";
    static final String EVENT = "app_event";
    static final String BACKGROUND = "app_background";
    static final String UNINSTALL = "app_uninstall";
    static final String QUERY = "store_query";
    static final String INSTALL = "store_install";
    static final String ANSWER = "offer_answer";
    static final String REPORT = "store_report";

    private static final int MAX_ARG = 256;
    private static final int APP_ROWS = 8;
    private static final int LIST_ROWS = 7;
    private static final int ROW = 12;
    private static final int BUTTON = 60;

    private AppScreens() {}

    static void write(net.minecraft.network.PacketBuffer buf, String s) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, MAX_ARG * 4);
        buf.writeVarIntToBuffer(length);
        buf.writeBytes(bytes, 0, length);
    }

    static String read(net.minecraft.network.PacketBuffer buf) {
        int length = Math.min(buf.readVarIntFromBuffer(), MAX_ARG * 4);
        byte[] bytes = new byte[Math.max(0, length)];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** What the client remembers between pages: which app or store entry is picked. */
    static final class Picked {

        String app = "";
        String store = "";
    }

    static void register(PanelSyncManager sync, EntityPlayer player, PhoneHandle tile) {
        sync.registerSyncedAction(OPEN, false, true, buf -> PhoneScreenActions.appOpen(player, tile, read(buf)));
        sync.registerSyncedAction(CLOSE, false, true, buf -> PhoneScreenActions.appClose(player, tile));
        sync.registerSyncedAction(
            EVENT,
            false,
            true,
            buf -> PhoneScreenActions.appEvent(player, tile, read(buf), read(buf), read(buf)));
        sync.registerSyncedAction(
            BACKGROUND,
            false,
            true,
            buf -> PhoneScreenActions.appBackground(player, tile, read(buf), buf.readBoolean()));
        sync.registerSyncedAction(
            UNINSTALL,
            false,
            true,
            buf -> PhoneScreenActions.appUninstall(player, tile, read(buf)));
        sync.registerSyncedAction(
            QUERY,
            false,
            true,
            buf -> PhoneScreenActions.storeQuery(player, tile, read(buf), buf.readUnsignedByte(), buf.readShort()));
        sync.registerSyncedAction(REPORT, false, true, buf -> PhoneScreenActions.storeReport(player, tile, read(buf)));
        sync.registerSyncedAction(
            INSTALL,
            false,
            true,
            buf -> PhoneScreenActions.storeInstall(player, tile, read(buf)));
        sync.registerSyncedAction(
            ANSWER,
            false,
            true,
            buf -> PhoneScreenActions.answerOffer(player, tile, buf.readLong(), buf.readBoolean(), buf.readBoolean()));
    }

    // region Looks

    /** Minecraft's sixteen text colours, by their code. */
    private static final int[] COLOURS = { 0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00,
        0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF };

    /** The text colour code nearest an app's accent: the font draws only those. */
    static String accent(int rgb) {
        int best = 15;
        long bestDistance = Long.MAX_VALUE;
        // Black and the darkest greys don't read on the screen.
        for (int i = 1; i < COLOURS.length; i++) {
            if (i == 8) continue;
            int c = COLOURS[i];
            long dr = ((c >> 16) & 255) - ((rgb >> 16) & 255);
            long dg = ((c >> 8) & 255) - ((rgb >> 8) & 255);
            long db = (c & 255) - (rgb & 255);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return "§" + Integer.toHexString(best);
    }

    private static String level(AppTree.Level level) {
        return switch (level) {
            case PLAIN -> "";
            case LOW -> "§c";
            case MEDIUM -> "§e";
            case HIGH -> "§a";
            case MAX -> "§b";
        };
    }

    private static String style(AppTree.Style style) {
        return switch (style) {
            case PLAIN -> "";
            case GO -> "§a";
            case DANGER -> "§c";
        };
    }

    private static String bar(double fraction) {
        int filled = (int) Math.round(fraction * 10);
        return "|".repeat(filled) + "§8" + "|".repeat(10 - filled) + "§r";
    }

    /** One widget's text: what its row shows left of its button. */
    static String text(AppTree.Widget w) {
        return switch (w.kind()) {
            case HEADING -> "§l" + w.label();
            case TEXT -> w.label();
            case VALUE -> w.label() + ": " + level(w.level()) + w.value();
            case PROGRESS -> w.label() + " "
                + level(w.level())
                + bar(w.fraction())
                + " "
                + Math.round(w.fraction() * 100)
                + "%";
            case TOGGLE -> w.label();
            case BUTTON -> "";
            case ITEM -> w.label()
                .isEmpty() ? w.value() + "  " + w.extra() : w.label() + "  " + w.extra();
            case INPUT -> "§7" + w.value();
        };
    }

    /** The button a widget's row has, or null for none. */
    static String button(AppTree.Widget w) {
        return switch (w.kind()) {
            case TOGGLE -> StatCollector
                .translateToLocal("true".equals(w.value()) ? "becom.apps.toggle.on" : "becom.apps.toggle.off");
            case BUTTON -> style(w.style()) + w.label();
            default -> null;
        };
    }

    // endregion

    // region The open app

    static AppsView.App installed(PhoneView view, String id) {
        for (AppsView.App app : view.apps()
            .installed())
            if (app.id()
                .equals(id)) return app;
        return null;
    }

    /** The open app: its widgets a row each, the phone's own states, and one input line when the app asks for one. */
    static ParentWidget<?> app(PanelSyncManager sync, Supplier<PhoneView> view, Picked picked, int width) {
        int[] page = { 0 };
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(1);
        column.child(
            IKey.dynamic(() -> stateLine(view.get(), picked))
                .asWidget()
                .widthRel(1f)
                .height(10));
        Supplier<List<AppTree.Widget>> widgets = () -> {
            AppsView.Screen screen = view.get()
                .apps()
                .screen();
            return screen.id()
                .equals(picked.app) && screen.state() == AppsView.State.RUNNING ? screen.widgets() : List.of();
        };
        for (int r = 0; r < APP_ROWS; r++) {
            int row = r;
            Supplier<AppTree.Widget> at = () -> {
                List<AppTree.Widget> all = widgets.get();
                int i = page[0] * APP_ROWS + row;
                return i < all.size() ? all.get(i) : null;
            };
            Flow line = Flow.row()
                .widthRel(1f)
                .height(ROW)
                .childPadding(2);
            line.child(IKey.dynamic(() -> {
                AppTree.Widget w = at.get();
                return w == null ? "" : text(w);
            })
                .asWidget()
                .width(width - BUTTON - 2)
                .height(ROW));
            line.child(
                new ButtonWidget<>().size(BUTTON, ROW)
                    .overlay(IKey.dynamic(() -> {
                        AppTree.Widget w = at.get();
                        String label = w == null ? null : button(w);
                        return label == null ? "" : label;
                    }))
                    .setEnabledIf(b -> {
                        AppTree.Widget w = at.get();
                        return w != null && button(w) != null;
                    })
                    .onMousePressed(b -> {
                        AppTree.Widget w = at.get();
                        if (w == null || button(w) == null) return true;
                        String value = w.kind() == AppTree.Kind.TOGGLE ? String.valueOf(!"true".equals(w.value())) : "";
                        event(sync, picked.app, w.id(), value);
                        return true;
                    }));
            column.child(line);
        }
        column.child(
            PhoneScreen.pager(
                page,
                () -> widgets.get()
                    .size(),
                APP_ROWS));
        // The app's first input: what's typed goes to it on Send. When the app can't run or crashed, there are no
        // widgets, and the button tries it again instead, the state line above saying why.
        Flow input = Flow.row()
            .widthRel(1f)
            .height(ROW)
            .childPadding(2);
        StringValue typed = new StringValue("");
        input.child(
            new TextFieldWidget().value(typed)
                .setMaxLength(AppTree.MAX_TEXT * 4)
                .size(width - BUTTON - 2, ROW)
                .setEnabledIf(w -> firstInput(widgets.get()) != null));
        input.child(
            new ButtonWidget<>().size(BUTTON, ROW)
                .overlay(IKey.dynamic(() -> {
                    if (failed(view.get(), picked)) return StatCollector.translateToLocal("becom.apps.retry");
                    AppTree.Widget w = firstInput(widgets.get());
                    return w == null ? "" : StatCollector.translateToLocal("becom.apps.send");
                }))
                .setEnabledIf(w -> failed(view.get(), picked) || firstInput(widgets.get()) != null)
                .onMousePressed(b -> {
                    if (failed(view.get(), picked)) {
                        sync.callSyncedAction(OPEN, buf -> write(buf, picked.app));
                        return true;
                    }
                    AppTree.Widget w = firstInput(widgets.get());
                    if (w != null) {
                        event(sync, picked.app, w.id(), typed.getStringValue());
                        typed.setStringValue("");
                    }
                    return true;
                }));
        column.child(input);
        column.child(
            IKey.dynamic(() -> dataAge(view.get(), picked))
                .asWidget()
                .widthRel(1f)
                .height(9));
        return column;
    }

    private static boolean failed(PhoneView view, Picked picked) {
        AppsView.Screen screen = view.apps()
            .screen();
        return screen.id()
            .equals(picked.app)
            && (screen.state() == AppsView.State.CRASHED || screen.state() == AppsView.State.UNREACHABLE);
    }

    // Data is news for a few seconds; after that the footer says how old what's on screen may be.
    private static final long FRESH_TICKS = 5 * 20;

    private static String dataAge(PhoneView view, Picked picked) {
        AppsView.Screen screen = view.apps()
            .screen();
        long now = BeCom.proxy.worldTime();
        if (!screen.id()
            .equals(picked.app) || screen.data() <= 0 || now <= 0 || now - screen.data() < FRESH_TICKS) return "";
        long seconds = (now - screen.data()) / 20;
        return "§7" + (seconds < 120 ? StatCollector.translateToLocalFormatted("becom.apps.data_age", seconds)
            : StatCollector.translateToLocalFormatted("becom.apps.data_age_min", seconds / 60));
    }

    private static AppTree.Widget firstInput(List<AppTree.Widget> widgets) {
        for (AppTree.Widget w : widgets) if (w.kind() == AppTree.Kind.INPUT) return w;
        return null;
    }

    private static void event(PanelSyncManager sync, String app, String widget, String value) {
        sync.callSyncedAction(EVENT, buf -> {
            write(buf, app);
            write(buf, widget);
            write(buf, value.length() > MAX_ARG ? value.substring(0, MAX_ARG) : value);
        });
    }

    /** The line over an app: loading, can't reach it, crashed and why, or its name as a Lua app. */
    private static String stateLine(PhoneView view, Picked picked) {
        AppsView.Screen screen = view.apps()
            .screen();
        if (!screen.id()
            .equals(picked.app)) return StatCollector.translateToLocal("becom.apps.loading");
        return switch (screen.state()) {
            case NONE, LOADING -> StatCollector.translateToLocal("becom.apps.loading");
            case RUNNING -> "";
            case UNREACHABLE -> StatCollector.translateToLocal("becom.apps.unreachable");
            case CRASHED -> StatCollector.translateToLocalFormatted("becom.apps.crashed", screen.error());
        };
    }

    // endregion

    // region Settings › Apps

    /** Settings › Apps: every installed app; one opens its settings. */
    static ParentWidget<?> list(Supplier<PhoneView> view, Picked picked, Runnable openSettings, int width) {
        int[] page = { 0 };
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(1);
        column.child(
            IKey.dynamic(
                () -> view.get()
                    .apps()
                    .installed()
                    .isEmpty() ? StatCollector.translateToLocal("becom.apps.none") : "")
                .asWidget()
                .widthRel(1f)
                .height(10));
        for (int r = 0; r < LIST_ROWS; r++) {
            int row = r;
            Supplier<AppsView.App> at = () -> {
                List<AppsView.App> all = view.get()
                    .apps()
                    .installed();
                int i = page[0] * LIST_ROWS + row;
                return i < all.size() ? all.get(i) : null;
            };
            column.child(
                new ButtonWidget<>().size(width, ROW + 1)
                    .overlay(IKey.dynamic(() -> {
                        AppsView.App app = at.get();
                        return app == null ? "" : accent(app.accent()) + app.name() + "§r " + app.version();
                    }))
                    .setEnabledIf(b -> at.get() != null)
                    .onMousePressed(b -> {
                        AppsView.App app = at.get();
                        if (app != null) {
                            picked.app = app.id();
                            openSettings.run();
                        }
                        return true;
                    }));
        }
        column.child(
            PhoneScreen.pager(
                page,
                () -> view.get()
                    .apps()
                    .installed()
                    .size(),
                LIST_ROWS));
        return column;
    }

    /** An app's settings: who made it and where it came from, background, its day so far, and uninstall. */
    static ParentWidget<?> settings(PanelSyncManager sync, Supplier<PhoneView> view, Picked picked, Runnable back,
        int width) {
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(2);
        Supplier<AppsView.App> app = () -> installed(view.get(), picked.app);
        column.child(IKey.dynamic(() -> {
            AppsView.App a = app.get();
            return a == null ? ""
                : StatCollector.translateToLocalFormatted("becom.apps.by", a.name() + " " + a.version(), a.author());
        })
            .asWidget()
            .widthRel(1f)
            .height(10));
        column.child(IKey.dynamic(() -> {
            AppsView.App a = app.get();
            if (a == null) return "";
            return a.store() ? StatCollector.translateToLocal("becom.apps.from_store")
                : StatCollector.translateToLocalFormatted("becom.apps.from_computer", a.where());
        })
            .asWidget()
            .widthRel(1f)
            .height(10));
        column.child(
            new ToggleButton().size(width, 14)
                .overlay(IKey.lang("becom.apps.background"))
                .value(new BoolValue.Dynamic(() -> {
                    AppsView.App a = app.get();
                    return a != null && a.background();
                }, on -> sync.callSyncedAction(BACKGROUND, buf -> {
                    write(buf, picked.app);
                    buf.writeBoolean(on);
                }))));
        column.child(
            IKey.lang("becom.apps.background.info")
                .asWidget()
                .widthRel(1f)
                .height(10));
        column.child(IKey.dynamic(() -> {
            AppsView.App a = app.get();
            return a == null ? ""
                : StatCollector.translateToLocalFormatted("becom.apps.today", a.woke(), PhoneScreen.eu(a.euToday()));
        })
            .asWidget()
            .widthRel(1f)
            .height(10));
        column.child(IKey.dynamic(() -> {
            AppsView.App a = app.get();
            if (a == null) return "";
            String stored = StatCollector
                .translateToLocalFormatted("becom.apps.storage", kb(a.stored()), kb(a.storage()));
            return StatCollector.translateToLocalFormatted("becom.apps.size", kb(a.size())) + "  "
                + (a.stored() >= a.storage() ? "§c" + stored : stored);
        })
            .asWidget()
            .widthRel(1f)
            .height(10));
        column.child(IKey.dynamic(() -> {
            AppsView.App a = app.get();
            return a == null ? "" : uses(a.uses());
        })
            .asWidget()
            .widthRel(1f)
            .height(10));
        column.child(
            new ButtonWidget<>().size(width, 14)
                .overlay(IKey.str("§c" + StatCollector.translateToLocal("becom.apps.uninstall")))
                .onMousePressed(b -> {
                    if (app.get() != null) {
                        sync.callSyncedAction(UNINSTALL, buf -> write(buf, picked.app));
                        back.run();
                    }
                    return true;
                }));
        return column;
    }

    static String kb(long bytes) {
        return Math.max(1, (bytes + 1023) / 1024) + " KB";
    }

    /**
     * What an app may do, from its AppManifest.Use bits: "It can: data, notifications", or that it asks for nothing.
     */
    static String uses(int bits) {
        List<String> words = new ArrayList<>();
        for (AppManifest.Use use : AppManifest.Use.VALUES)
            if ((bits & use.bit()) != 0) words.add(StatCollector.translateToLocal("becom.apps.use." + use.word()));
        return words.isEmpty() ? StatCollector.translateToLocal("becom.apps.uses_none")
            : StatCollector.translateToLocalFormatted("becom.apps.uses", String.join(", ", words));
    }

    // endregion

    // region App Store

    private static final String[] TABS = { "becom.apps.tab.popular", "becom.apps.tab.new", "becom.apps.tab.installed" };

    /**
     * The App Store: a search line, the tabs (most installed, newest, what this phone has), and a page of the apps
     * that match. The server pages the results; the client only asks for another page.
     */
    static ParentWidget<?> store(PanelSyncManager sync, Supplier<PhoneView> view, Picked picked, Runnable openApp,
        int width) {
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(1);
        Flow search = Flow.row()
            .widthRel(1f)
            .height(ROW)
            .childPadding(2);
        StringValue query = new StringValue("");
        Supplier<AppsView> apps = () -> view.get()
            .apps();
        search.child(
            new TextFieldWidget().value(query)
                .setMaxLength(32)
                .size(width - BUTTON - 2, ROW));
        search.child(
            new ButtonWidget<>().size(BUTTON, ROW)
                .overlay(IKey.lang("becom.apps.search"))
                .onMousePressed(b -> {
                    query(
                        sync,
                        query.getStringValue(),
                        apps.get()
                            .tab(),
                        0);
                    return true;
                }));
        column.child(search);
        Flow tabs = Flow.row()
            .widthRel(1f)
            .height(ROW)
            .childPadding(2);
        int tabWidth = (width - 2 * (TABS.length - 1)) / TABS.length;
        for (int t = 0; t < TABS.length; t++) {
            int tab = t;
            tabs.child(
                new ButtonWidget<>().size(tabWidth, ROW)
                    .overlay(IKey.dynamic(() -> {
                        String name = StatCollector.translateToLocal(TABS[tab]);
                        return apps.get()
                            .tab() == tab ? "§n" + name : name;
                    }))
                    .onMousePressed(b -> {
                        query(sync, query.getStringValue(), tab, 0);
                        return true;
                    }));
        }
        column.child(tabs);
        column.child(
            IKey.dynamic(
                () -> apps.get()
                    .total() == 0 ? StatCollector.translateToLocal(
                        apps.get()
                            .tab() == 2 ? "becom.apps.store_none_installed" : "becom.apps.store_empty")
                        : "")
                .asWidget()
                .widthRel(1f)
                .height(9));
        for (int r = 0; r < AppsView.STORE_ROWS; r++) {
            int row = r;
            Supplier<AppsView.StoreApp> at = () -> {
                List<AppsView.StoreApp> all = apps.get()
                    .store();
                return row < all.size() ? all.get(row) : null;
            };
            column.child(
                new ButtonWidget<>().size(width, ROW + 2)
                    .overlay(BeCom.proxy.appIcon(() -> {
                        AppsView.StoreApp a = at.get();
                        return a == null ? "" : a.icon();
                    }, IKey.dynamic(() -> {
                        AppsView.StoreApp a = at.get();
                        return a == null ? ""
                            : StatCollector.translateToLocalFormatted(
                                "becom.apps.store_row",
                                a.name(),
                                a.version(),
                                a.author(),
                                a.installs());
                    })))
                    .setEnabledIf(b -> at.get() != null)
                    .onMousePressed(b -> {
                        AppsView.StoreApp a = at.get();
                        if (a != null) {
                            picked.store = a.id();
                            openApp.run();
                        }
                        return true;
                    }));
        }
        Flow pager = Flow.row()
            .coverChildren()
            .childPadding(4);
        pager.child(
            new ButtonWidget<>().size(20, 12)
                .overlay(IKey.str("<"))
                .onMousePressed(b -> {
                    AppsView a = apps.get();
                    if (a.page() > 0) query(sync, a.query(), a.tab(), a.page() - 1);
                    return true;
                }));
        pager.child(IKey.dynamic(() -> {
            AppsView a = apps.get();
            return (a.page() + 1) + "/" + pages(a);
        })
            .asWidget()
            .size(30, 12));
        pager.child(
            new ButtonWidget<>().size(20, 12)
                .overlay(IKey.str(">"))
                .onMousePressed(b -> {
                    AppsView a = apps.get();
                    if (a.page() + 1 < pages(a)) query(sync, a.query(), a.tab(), a.page() + 1);
                    return true;
                }));
        column.child(pager);
        return column;
    }

    private static int pages(AppsView apps) {
        return Math.max(1, (apps.total() + AppsView.STORE_ROWS - 1) / AppsView.STORE_ROWS);
    }

    private static void query(PanelSyncManager sync, String text, int tab, int page) {
        sync.callSyncedAction(QUERY, buf -> {
            write(buf, text);
            buf.writeByte(tab);
            buf.writeShort(page);
        });
    }

    static AppsView.StoreApp picked(PhoneView view, Picked picked) {
        for (AppsView.StoreApp a : view.apps()
            .store())
            if (a.id()
                .equals(picked.store)) return a;
        return null;
    }

    /**
     * An app's page in the store: what it is and who made it, what it may do, when it was updated and who sees it,
     * install or update, and a report to the operators.
     */
    static ParentWidget<?> storeApp(PanelSyncManager sync, Supplier<PhoneView> view, Picked picked, int width) {
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(2);
        Supplier<AppsView.StoreApp> app = () -> picked(view.get(), picked);
        column.child(IKey.dynamic(() -> {
            AppsView.StoreApp a = app.get();
            return a == null ? ""
                : StatCollector
                    .translateToLocalFormatted("becom.apps.by", "§l" + a.name() + "§r " + a.version(), a.author());
        })
            .asWidget()
            .widthRel(1f)
            .height(10));
        column.child(IKey.dynamic(() -> {
            AppsView.StoreApp a = app.get();
            return a == null ? "" : a.about();
        })
            .asWidget()
            .widthRel(1f)
            .height(20));
        column.child(IKey.dynamic(() -> {
            AppsView.StoreApp a = app.get();
            return a == null ? ""
                : StatCollector.translateToLocalFormatted("becom.apps.store_info", kb(a.size()), a.installs());
        })
            .asWidget()
            .widthRel(1f)
            .height(10));
        column.child(IKey.dynamic(() -> {
            AppsView.StoreApp a = app.get();
            if (a == null) return "";
            String when = a.days() == 0 ? StatCollector.translateToLocal("becom.apps.updated_today")
                : StatCollector.translateToLocalFormatted("becom.apps.updated_days", a.days());
            return when + " "
                + StatCollector
                    .translateToLocal(a.team() ? "becom.apps.visibility.team" : "becom.apps.visibility.public");
        })
            .asWidget()
            .widthRel(1f)
            .height(10));
        column.child(IKey.dynamic(() -> {
            AppsView.StoreApp a = app.get();
            return a == null ? "" : uses(a.uses());
        })
            .asWidget()
            .widthRel(1f)
            .height(10));
        int half = (width - 2) / 2;
        Flow buttons = Flow.row()
            .widthRel(1f)
            .height(14)
            .childPadding(2);
        buttons.child(
            new ButtonWidget<>().size(half, 14)
                .overlay(IKey.dynamic(() -> {
                    AppsView.StoreApp a = app.get();
                    if (a == null) return "";
                    return a.reported() ? "§7" + StatCollector.translateToLocal("becom.apps.reported_already")
                        : "§c" + StatCollector.translateToLocal("becom.apps.report");
                }))
                .setEnabledIf(b -> {
                    AppsView.StoreApp a = app.get();
                    return a != null && !a.reported();
                })
                .onMousePressed(b -> {
                    AppsView.StoreApp a = app.get();
                    if (a != null && !a.reported()) sync.callSyncedAction(REPORT, buf -> write(buf, a.id()));
                    return true;
                }));
        buttons.child(
            new ButtonWidget<>().size(half, 14)
                .overlay(IKey.dynamic(() -> {
                    AppsView.StoreApp a = app.get();
                    if (a == null) return "";
                    if (a.installed()
                        .isEmpty()) return "§a" + StatCollector.translateToLocal("becom.apps.install");
                    return a.installed()
                        .equals(a.version()) ? StatCollector.translateToLocal("becom.apps.installed")
                            : "§a" + StatCollector.translateToLocal("becom.apps.update");
                }))
                .setEnabledIf(b -> {
                    AppsView.StoreApp a = app.get();
                    return a != null && !a.installed()
                        .equals(a.version());
                })
                .onMousePressed(b -> {
                    AppsView.StoreApp a = app.get();
                    if (a != null) sync.callSyncedAction(INSTALL, buf -> write(buf, a.id()));
                    return true;
                }));
        column.child(buttons);
        return column;
    }

    // endregion

    // region Install from a computer

    /** Install an app, or publish it as you? What a computer asks, a warning, and Cancel or Install (or Publish). */
    static ParentWidget<?> offer(PanelSyncManager sync, Supplier<PhoneView> view, Runnable done, int width) {
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(2);
        Supplier<AppsView.Offer> offer = () -> view.get()
            .apps()
            .offer();
        column.child(IKey.dynamic(() -> {
            AppsView.Offer o = offer.get();
            if (!o.any()) return StatCollector.translateToLocal("becom.apps.no_offer");
            String app = "§l" + o.name() + "§r " + o.version();
            return o.publish() ? StatCollector.translateToLocalFormatted("becom.apps.publish_question", app)
                : StatCollector.translateToLocalFormatted("becom.apps.by", app, o.author());
        })
            .asWidget()
            .widthRel(1f)
            .height(10));
        column.child(IKey.dynamic(() -> {
            AppsView.Offer o = offer.get();
            return o.any() ? StatCollector.translateToLocalFormatted("becom.apps.from_computer", o.where()) : "";
        })
            .asWidget()
            .widthRel(1f)
            .height(10));
        column.child(IKey.dynamic(() -> {
            AppsView.Offer o = offer.get();
            return o.any() ? StatCollector.translateToLocalFormatted("becom.apps.size", kb(o.size())) : "";
        })
            .asWidget()
            .widthRel(1f)
            .height(10));
        column.child(IKey.dynamic(() -> {
            AppsView.Offer o = offer.get();
            return o.any() ? uses(o.uses()) : "";
        })
            .asWidget()
            .widthRel(1f)
            .height(10));
        column.child(
            IKey.dynamic(
                () -> offer.get()
                    .publish()
                        ? StatCollector.translateToLocalFormatted(
                            "becom.apps.publish_warning",
                            offer.get()
                                .author())
                        : StatCollector.translateToLocal("becom.apps.offer_warning"))
                .asWidget()
                .widthRel(1f)
                .height(30));
        // Install: Cancel or Install. Publish: Cancel, for the author's team only, or for everyone.
        int third = (width - 4) / 3;
        Flow buttons = Flow.row()
            .widthRel(1f)
            .height(14)
            .childPadding(2);
        buttons.child(
            answer(sync, offer, done, false, false).size(third, 14)
                .overlay(IKey.lang("becom.apps.cancel")));
        buttons.child(
            answer(sync, offer, done, true, true).size(third, 14)
                .overlay(
                    IKey.dynamic(
                        () -> offer.get()
                            .publish() ? StatCollector.translateToLocal("becom.apps.publish_team") : ""))
                .setEnabledIf(
                    b -> offer.get()
                        .publish()));
        buttons.child(
            answer(sync, offer, done, true, false).size(third, 14)
                .overlay(
                    IKey.dynamic(
                        () -> "§a" + StatCollector.translateToLocal(
                            offer.get()
                                .publish() ? "becom.apps.publish_everyone" : "becom.apps.install"))));
        column.child(buttons);
        return column;
    }

    private static ButtonWidget<?> answer(PanelSyncManager sync, Supplier<AppsView.Offer> offer, Runnable done,
        boolean accept, boolean team) {
        ButtonWidget<?> button = new ButtonWidget<>();
        button.setEnabledIf(
            b -> offer.get()
                .any())
            .onMousePressed(b -> {
                long id = offer.get()
                    .id();
                sync.callSyncedAction(ANSWER, buf -> {
                    buf.writeLong(id);
                    buf.writeBoolean(accept);
                    buf.writeBoolean(team);
                });
                done.run();
                return true;
            });
        return button;
    }

    // endregion

    /** The home screen's apps after the built-in ones: an offer first while there is one, then each Lua app. */
    static List<String> luaTiles(PhoneView view) {
        List<String> ids = new ArrayList<>();
        for (AppsView.App app : view.apps()
            .installed()) ids.add(app.id());
        return ids;
    }
}
