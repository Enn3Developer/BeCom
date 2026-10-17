package com.enn3developer.becom.gui;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.PhoneNumbers;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.phone.BatteryUse;
import com.enn3developer.becom.server.PhoneHandle;
import com.enn3developer.becom.server.PhoneScreenActions;

/**
 * The phone screen: keypad, contacts, recent and missed calls, everyone in the call, video, and
 * settings, with the call's state on top. A Telephone and a Videophone show them as tabs. A carried phone is a phone:
 * a status bar with the time, its signal and its charge, a home screen of apps, each opening full screen, and Back and
 * Home buttons below; it adds power and silent mode, a Battery page and a Signal page. Built on both sides; the server
 * registers the actions and checks each one, the client only draws and asks.
 */
public final class PhoneScreen {

    // Wide enough for six tabs, and for video as large as the screen allows.
    private static final int WIDTH = 232;
    private static final int HEIGHT = 206;
    private static final int ROWS = 7;
    private static final int ROW_HEIGHT = 13;
    // The settings page has two toggles and a heading above its block list.
    private static final int BLOCKED_ROWS = ROWS - 4;
    private static final int KEY_SIZE = 22;
    private static final int TAB_COUNT = 6;
    private static final int VIDEO_PAGE = 4;
    // Six tabs share the width: their names shrink to fit, as "Contacts" and "Settings" wrapped at full size.
    private static final float TAB_TEXT = 0.75f;
    private static final int BARS_WIDTH = 14;

    private static final String DIAL = "dial";
    private static final String PICK_UP = "pickup";
    private static final String HANG_UP = "hangup";
    private static final String DND = "dnd";
    private static final String HIDE = "hide";
    private static final String BLOCK_CONTACT = "block_contact";
    private static final String BLOCK_RECENT = "block_recent";
    private static final String UNBLOCK = "unblock";
    private static final String POWER = "power";
    private static final String SILENT = "silent";
    private static final String FRONT = "front";
    private static final String REAR = "rear";
    private static final String SAVER = "saver";
    private static final String WARN = "warn";
    // A carried phone's pages after the six it shares with the desk phones, and the page each goes back to.
    private static final int SETTINGS_PAGE = 5;
    private static final int HOME_PAGE = 6;
    private static final int BATTERY_PAGE = 7;
    private static final int SIGNAL_PAGE = 8;
    private static final int APP_PAGE = 9;
    private static final int APPS_PAGE = 10;
    private static final int APP_SETTINGS_PAGE = 11;
    private static final int STORE_PAGE = 12;
    private static final int STORE_APP_PAGE = 13;
    private static final int OFFER_PAGE = 14;
    private static final String[] PAGE_NAMES = { "keypad", "contacts", "recent", "call", "video", "settings", "home",
        "battery", "signal", "app", "apps", "app_settings", "store", "store_app", "offer" };
    private static final int APP_COLUMNS = 3;
    private static final int APP_HEIGHT = 20;
    private static final long TICKS_PER_DAY = 24_000;
    private static final int TOGGLE_HEIGHT = 14;

    private PhoneScreen() {}

    /** @param handle the phone; on the client only its kind matters, the server checks every action against it */
    public static ModularPanel build(PhoneHandle tile, EntityPlayer player, PanelSyncManager sync) {
        PhoneViewSync viewSync = new PhoneViewSync(() -> PhoneScreenActions.view(player, tile));
        sync.syncValue("view", viewSync);
        sync.registerSyncedAction(DIAL, false, true, buf -> PhoneScreenActions.dial(player, tile, buf.readInt()));
        sync.registerSyncedAction(PICK_UP, false, true, buf -> PhoneScreenActions.pickUp(player, tile));
        sync.registerSyncedAction(HANG_UP, false, true, buf -> PhoneScreenActions.hangUp(player, tile));
        sync.registerSyncedAction(
            POWER,
            false,
            true,
            buf -> PhoneScreenActions.setPower(player, tile, buf.readBoolean()));
        sync.registerSyncedAction(
            SILENT,
            false,
            true,
            buf -> PhoneScreenActions.setSilent(player, tile, buf.readBoolean()));
        sync.registerSyncedAction(
            FRONT,
            false,
            true,
            buf -> PhoneScreenActions.setCamera(player, tile, false, buf.readBoolean()));
        sync.registerSyncedAction(
            REAR,
            false,
            true,
            buf -> PhoneScreenActions.setCamera(player, tile, true, buf.readBoolean()));
        sync.registerSyncedAction(
            DND,
            false,
            true,
            buf -> PhoneScreenActions.setDoNotDisturb(player, tile, buf.readBoolean()));
        sync.registerSyncedAction(
            HIDE,
            false,
            true,
            buf -> PhoneScreenActions.setHidden(player, tile, buf.readBoolean()));
        sync.registerSyncedAction(
            BLOCK_CONTACT,
            false,
            true,
            buf -> PhoneScreenActions.blockContact(player, tile, buf.readInt()));
        sync.registerSyncedAction(
            BLOCK_RECENT,
            false,
            true,
            buf -> PhoneScreenActions.blockRecent(player, tile, buf.readLong(), buf.readInt()));
        sync.registerSyncedAction(
            UNBLOCK,
            false,
            true,
            buf -> PhoneScreenActions.unblock(player, tile, new UUID(buf.readLong(), buf.readLong())));

        sync.registerSyncedAction(
            SAVER,
            false,
            true,
            buf -> PhoneScreenActions.setSaver(player, tile, buf.readBoolean()));
        sync.registerSyncedAction(
            WARN,
            false,
            true,
            buf -> PhoneScreenActions.setWarns(player, tile, buf.readBoolean()));

        Supplier<PhoneView> view = viewSync::view;
        StringBuilder typed = new StringBuilder();
        PagedWidget.Controller tabs = new PagedWidget.Controller();
        AppScreens.register(sync, player, tile);
        if (tile instanceof PhoneHandle.CarriedPhone) return shell(sync, view, typed, tabs, player);

        ModularPanel panel = new ModularPanel("becom_phone").size(WIDTH, HEIGHT);
        Flow column = Flow.column()
            .sizeRel(1f)
            .padding(6, 6, 6, 6)
            .childPadding(3);
        column.child(
            IKey.dynamic(
                () -> view.get()
                    .number() < 0 ? ""
                        : StatCollector.translateToLocalFormatted(
                            "becom.phone.title",
                            view.get()
                                .number(),
                            view.get()
                                .owner()))
                .asWidget()
                .widthRel(1f)
                .height(10));
        Flow statusRow = Flow.row()
            .widthRel(1f)
            .height(10);
        statusRow.child(
            IKey.dynamic(() -> status(view.get()))
                .asWidget()
                .width(WIDTH - 12 - BARS_WIDTH)
                .height(10));
        // The call's signal bars, or a carried phone's service, drawn only on the client.
        statusRow.child(
            BeCom.proxy.signalBars(
                () -> view.get()
                    .state() == PhoneView.State.CONNECTED ? view.get()
                        .hearing()
                        : view.get()
                            .service())
                .asWidget()
                .size(BARS_WIDTH, 10));
        column.child(statusRow);
        Flow tabRow = Flow.row()
            .widthRel(1f)
            .height(14)
            .childPadding(2);
        String[] tabNames = { "keypad", "contacts", "recent", "call", "video", "settings" };
        for (int i = 0; i < TAB_COUNT; i++) {
            tabRow.child(
                new PageButton(i, tabs).size((WIDTH - 12 - 2 * (TAB_COUNT - 1)) / TAB_COUNT, 14)
                    .overlay(
                        IKey.lang("becom.phone.tab." + tabNames[i])
                            .scale(TAB_TEXT)));
        }
        column.child(tabRow);
        column.child(
            pages(sync, view, typed, tabs, HEIGHT - 12 - 10 - 10 - 14 - 9, null)
                // During a video call the screen opens on the video, full size.
                .initialPage(BeCom.proxy.inVideoCall() ? VIDEO_PAGE : 0));
        panel.child(column);
        return panel;
    }

    /** The six pages every phone has, in tab order. */
    private static PagedWidget<?> pages(PanelSyncManager sync, Supplier<PhoneView> view, StringBuilder typed,
        PagedWidget.Controller tabs, int height, Flow settingsTop) {
        return new PagedWidget<>().controller(tabs)
            .widthRel(1f)
            .height(height)
            .addPage(keypad(sync, view, typed))
            .addPage(
                rows(
                    sync,
                    () -> view.get()
                        .contacts()
                        .size(),
                    i -> {
                        PhoneView.Contact c = view.get()
                            .contacts()
                            .get(i);
                        return c.name() + "  " + c.number();
                    },
                    i -> view.get()
                        .contacts()
                        .get(i)
                        .number(),
                    (i, n) -> sync.callSyncedAction(BLOCK_CONTACT, buf -> buf.writeInt(n)),
                    view))
            .addPage(
                rows(
                    sync,
                    () -> view.get()
                        .recent()
                        .size(),
                    i -> {
                        PhoneView.Recent r = view.get()
                            .recent()
                            .get(i);
                        return StatCollector.translateToLocal(
                            "becom.phone.recent." + r.direction()
                                .name()
                                .toLowerCase(java.util.Locale.ROOT))
                            + " "
                            + r.name()
                            + "  "
                            + r.number();
                    },
                    i -> view.get()
                        .recent()
                        .get(i)
                        .number(),
                    (i, n) -> {
                        long time = view.get()
                            .recent()
                            .get(i)
                            .time();
                        sync.callSyncedAction(BLOCK_RECENT, buf -> {
                            buf.writeLong(time);
                            buf.writeInt(n);
                        });
                    },
                    view))
            .addPage(members(view))
            .addPage(video(sync, view))
            .addPage(settings(sync, view, settingsTop));
    }

    // region Carried phone

    /** A carried phone's screen: status bar, the open app's name, the app, and Back and Home. */
    private static ModularPanel shell(PanelSyncManager sync, Supplier<PhoneView> view, StringBuilder typed,
        PagedWidget.Controller pages, EntityPlayer player) {
        ModularPanel panel = new ModularPanel("becom_phone").size(WIDTH, HEIGHT);
        Flow column = Flow.column()
            .sizeRel(1f)
            .padding(6, 6, 6, 6)
            .childPadding(3);
        Flow statusBar = Flow.row()
            .widthRel(1f)
            .height(10);
        int side = 40;
        statusBar.child(
            IKey.dynamic(() -> clock(player))
                .asWidget()
                .width(side)
                .height(10));
        statusBar.child(
            IKey.dynamic(() -> statusLine(view.get()))
                .asWidget()
                .width(WIDTH - 12 - 2 * side - BARS_WIDTH)
                .height(10));
        statusBar.child(
            BeCom.proxy.signalBars(
                () -> view.get()
                    .state() == PhoneView.State.CONNECTED ? view.get()
                        .hearing()
                        : view.get()
                            .service())
                .asWidget()
                .size(BARS_WIDTH, 10));
        statusBar.child(IKey.dynamic(() -> {
            PhoneView.Battery battery = view.get()
                .battery();
            return battery.max() <= 0 ? "" : battery.percent() + "%" + (battery.charging() ? "+" : "");
        })
            .alignment(com.cleanroommc.modularui.utils.Alignment.CenterRight)
            .asWidget()
            .width(side)
            .height(10));
        column.child(statusBar);
        AppScreens.Picked picked = new AppScreens.Picked();
        column.child(
            IKey.dynamic(() -> title(view.get(), pages, picked))
                .asWidget()
                .widthRel(1f)
                .height(10));
        int pageHeight = HEIGHT - 12 - 10 - 10 - 14 - 9;
        int inner = WIDTH - 12;
        int[] shown = { HOME_PAGE };
        PagedWidget<?> screen = pages(sync, view, typed, pages, pageHeight, settingsLinks(pages))
            // During a video call the screen opens on the video, full size.
            .initialPage(BeCom.proxy.inVideoCall() ? VIDEO_PAGE : HOME_PAGE)
            .onPageChange(page -> {
                // Leaving an app takes it off the screen; it keeps running only in the background.
                if (shown[0] == APP_PAGE && page != APP_PAGE) sync.callSyncedAction(AppScreens.CLOSE, buf -> {});
                shown[0] = page;
            });
        screen.addPage(home(sync, view, pages, player, picked));
        screen.addPage(battery(sync, view));
        screen.addPage(signal(view));
        screen.addPage(AppScreens.app(sync, view, picked, inner));
        screen.addPage(AppScreens.list(view, picked, () -> pages.setPage(APP_SETTINGS_PAGE), inner));
        screen.addPage(AppScreens.settings(sync, view, picked, () -> pages.setPage(APPS_PAGE), inner));
        screen.addPage(AppScreens.store(sync, view, picked, () -> pages.setPage(STORE_APP_PAGE), inner));
        screen.addPage(AppScreens.storeApp(sync, view, picked, inner));
        screen.addPage(AppScreens.offer(sync, view, () -> pages.setPage(HOME_PAGE), inner));
        column.child(screen);
        // The phone's two hardware buttons.
        Flow buttons = Flow.row()
            .widthRel(1f)
            .height(14)
            .childPadding(2);
        int half = (WIDTH - 12 - 2) / 2;
        buttons.child(
            new ButtonWidget<>().size(half, 14)
                .overlay(IKey.lang("becom.phone.back"))
                .onMousePressed(button -> {
                    if (pages.isInitialised()) pages.setPage(parent(pages.getActivePageIndex()));
                    return true;
                }));
        buttons.child(
            new ButtonWidget<>().size(half, 14)
                .overlay(IKey.lang("becom.phone.home"))
                .onMousePressed(button -> {
                    if (pages.isInitialised()) pages.setPage(HOME_PAGE);
                    return true;
                }));
        column.child(buttons);
        panel.child(column);
        return panel;
    }

    /** Where Back goes from a page: up a level in Settings and the store, everything else home. */
    private static int parent(int page) {
        return switch (page) {
            case BATTERY_PAGE, APPS_PAGE -> SETTINGS_PAGE;
            case APP_SETTINGS_PAGE -> APPS_PAGE;
            case STORE_APP_PAGE -> STORE_PAGE;
            default -> HOME_PAGE;
        };
    }

    /** The line over a page: its app's name, and where it sits in Settings or the store. */
    private static String title(PhoneView view, PagedWidget.Controller pages, AppScreens.Picked picked) {
        int page = pages.isInitialised() ? pages.getActivePageIndex() : HOME_PAGE;
        if (page == HOME_PAGE) return "";
        String name = app(page);
        return switch (page) {
            case BATTERY_PAGE, APPS_PAGE -> StatCollector
                .translateToLocalFormatted("becom.phone.app.sub", app(SETTINGS_PAGE), name);
            case APP_PAGE -> {
                AppsView.App a = AppScreens.installed(view, picked.app);
                yield a == null ? "" : AppScreens.accent(a.accent()) + a.name() + " \u00a77[Lua]";
            }
            case APP_SETTINGS_PAGE -> {
                AppsView.App a = AppScreens.installed(view, picked.app);
                yield StatCollector
                    .translateToLocalFormatted("becom.phone.app.sub", app(SETTINGS_PAGE), a == null ? "" : a.name());
            }
            case STORE_APP_PAGE -> {
                AppsView.StoreApp a = AppScreens.picked(view, picked);
                yield StatCollector
                    .translateToLocalFormatted("becom.phone.app.sub", app(STORE_PAGE), a == null ? "" : a.name());
            }
            default -> name;
        };
    }

    /** The links at the top of a carried phone's Settings: Battery and Apps. */
    private static Flow settingsLinks(PagedWidget.Controller pages) {
        int half = (WIDTH - 12 - 2) / 2;
        Flow row = Flow.row()
            .widthRel(1f)
            .height(14)
            .childPadding(2);
        row.child(
            new ButtonWidget<>().size(half, 14)
                .overlay(IKey.str(StatCollector.translateToLocal("becom.phone.app.battery") + " >"))
                .onMousePressed(b -> {
                    if (pages.isInitialised()) pages.setPage(BATTERY_PAGE);
                    return true;
                }));
        row.child(
            new ButtonWidget<>().size(half, 14)
                .overlay(IKey.str(StatCollector.translateToLocal("becom.phone.app.apps") + " >"))
                .onMousePressed(b -> {
                    if (pages.isInitialised()) pages.setPage(APPS_PAGE);
                    return true;
                }));
        return row;
    }

    private static String app(int page) {
        return StatCollector.translateToLocal("becom.phone.app." + PAGE_NAMES[page]);
    }

    /** The time of day where the player is, as a 24-hour clock: Minecraft's day starts at 06:00. */
    private static String clock(EntityPlayer player) {
        long time = Math.floorMod(player.worldObj.getWorldTime() + 6000, TICKS_PER_DAY);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", time / 1000, time % 1000 * 60 / 1000);
    }

    /** The status bar's middle: the call while there is one, else nothing, as the home screen says the rest. */
    private static String statusLine(PhoneView view) {
        return switch (view.state()) {
            case RINGING_IN, RINGING_OUT, CONNECTED -> status(view);
            default -> "";
        };
    }

    /** The home screen: the day and the number, the phone's state or its last notice, answer and hang up, and apps. */
    private static ParentWidget<?> home(PanelSyncManager sync, Supplier<PhoneView> view, PagedWidget.Controller pages,
        EntityPlayer player, AppScreens.Picked picked) {
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(2);
        column.child(
            IKey.dynamic(
                () -> StatCollector.translateToLocalFormatted(
                    "becom.phone.home.day",
                    player.worldObj.getWorldTime() / TICKS_PER_DAY + 1,
                    view.get()
                        .number() < 0 ? "-"
                            : String.valueOf(
                                view.get()
                                    .number())))
                .asWidget()
                .widthRel(1f)
                .height(10));
        column.child(IKey.dynamic(() -> {
            PhoneView v = view.get();
            AppsView.Notice notice = v.apps()
                .notice();
            if (v.state() == PhoneView.State.IDLE && !notice.app()
                .isEmpty()) {
                return "\u00a7e" + notice.app() + "\u00a7r " + notice.title() + " " + notice.text();
            }
            return status(v);
        })
            .asWidget()
            .widthRel(1f)
            .height(10));
        int half = (WIDTH - 12 - 2) / 2;
        Flow callRow = Flow.row()
            .widthRel(1f)
            .height(14)
            .childPadding(2);
        callRow.child(
            new ButtonWidget<>().size(half, 14)
                .overlay(IKey.lang("becom.phone.answer"))
                .setEnabledIf(
                    w -> view.get()
                        .state() == PhoneView.State.RINGING_IN)
                .onMousePressed(button -> {
                    sync.callSyncedAction(PICK_UP, buf -> {});
                    return true;
                }));
        callRow.child(
            new ButtonWidget<>().size(half, 14)
                .overlay(IKey.lang("becom.phone.hang_up"))
                .setEnabledIf(w -> {
                    PhoneView.State state = view.get()
                        .state();
                    return state == PhoneView.State.RINGING_IN || state == PhoneView.State.RINGING_OUT
                        || state == PhoneView.State.CONNECTED;
                })
                .onMousePressed(button -> {
                    sync.callSyncedAction(HANG_UP, buf -> {});
                    return true;
                }));
        column.child(callRow);
        int[] page = { 0 };
        int width = (WIDTH - 12 - 2 * (APP_COLUMNS - 1)) / APP_COLUMNS;
        Flow row = null;
        for (int i = 0; i < TILES; i++) {
            if (i % APP_COLUMNS == 0) {
                row = Flow.row()
                    .widthRel(1f)
                    .height(APP_HEIGHT)
                    .childPadding(2);
                column.child(row);
            }
            int slot = i;
            java.util.function.IntSupplier tile = () -> {
                List<Integer> all = tiles(view.get());
                int index = page[0] * TILES + slot;
                return index < all.size() ? all.get(index) : NO_TILE;
            };
            row.child(
                new ButtonWidget<>().size(width, APP_HEIGHT)
                    .overlay(
                        BeCom.proxy.appIcon(
                            () -> tileIcon(view.get(), tile.getAsInt()),
                            IKey.dynamic(() -> tileName(view.get(), tile.getAsInt()))))
                    .setEnabledIf(w -> tile.getAsInt() != NO_TILE)
                    .onMousePressed(button -> {
                        int t = tile.getAsInt();
                        if (t == NO_TILE || !pages.isInitialised()) return true;
                        if (t == OFFER_TILE) pages.setPage(OFFER_PAGE);
                        else if (t >= 0) pages.setPage(t);
                        else {
                            List<AppsView.App> apps = view.get()
                                .apps()
                                .installed();
                            int index = -1 - t;
                            if (index < apps.size()) {
                                picked.app = apps.get(index)
                                    .id();
                                sync.callSyncedAction(AppScreens.OPEN, buf -> AppScreens.write(buf, picked.app));
                                pages.setPage(APP_PAGE);
                            }
                        }
                        return true;
                    }));
        }
        column.child(pager(page, () -> tiles(view.get()).size(), TILES));
        return column;
    }

    // A home screen tile: a built-in app's page, OFFER_TILE, or -1 - i for the i-th Lua app.
    private static final int NO_TILE = Integer.MIN_VALUE;
    private static final int OFFER_TILE = Integer.MIN_VALUE + 1;
    private static final int TILES = 12;
    private static final int[] BUILT_IN = { 0, 1, 2, 3, 4, SIGNAL_PAGE, SETTINGS_PAGE, STORE_PAGE };

    private static List<Integer> tiles(PhoneView view) {
        List<Integer> tiles = new java.util.ArrayList<>();
        if (view.apps()
            .offer()
            .any()) tiles.add(OFFER_TILE);
        for (int page : BUILT_IN) tiles.add(page);
        int count = view.apps()
            .installed()
            .size();
        for (int i = 0; i < count; i++) tiles.add(-1 - i);
        return tiles;
    }

    /** A Lua app's tile shows its icon; the built-in ones have none. */
    private static String tileIcon(PhoneView view, int tile) {
        if (tile == NO_TILE || tile == OFFER_TILE || tile >= 0) return "";
        List<AppsView.App> apps = view.apps()
            .installed();
        int index = -1 - tile;
        return index < apps.size() ? apps.get(index)
            .icon() : "";
    }

    private static String tileName(PhoneView view, int tile) {
        if (tile == NO_TILE) return "";
        if (tile == OFFER_TILE) return "\u00a7e" + StatCollector.translateToLocal("becom.apps.offer_tile");
        if (tile >= 0) return app(tile);
        List<AppsView.App> apps = view.apps()
            .installed();
        int index = -1 - tile;
        if (index >= apps.size()) return "";
        AppsView.App a = apps.get(index);
        return AppScreens.accent(a.accent()) + a.name();
    }

    /** Settings › Battery: the charge, how long it lasts, where it went today, and the saver and warnings. */
    private static ParentWidget<?> battery(PanelSyncManager sync, Supplier<PhoneView> view) {
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(1);
        column.child(IKey.dynamic(() -> {
            PhoneView.Battery b = view.get()
                .battery();
            return StatCollector
                .translateToLocalFormatted("becom.phone.battery.charge", b.percent(), eu(b.charge()), eu(b.max()));
        })
            .asWidget()
            .widthRel(1f)
            .height(10));
        column.child(IKey.dynamic(() -> {
            PhoneView.Battery b = view.get()
                .battery();
            return StatCollector.translateToLocalFormatted(
                "becom.phone.battery.lasts",
                duration(b.standbyMinutes()),
                duration(b.callMinutes()));
        })
            .asWidget()
            .widthRel(1f)
            .height(10));
        column.child(
            IKey.dynamic(
                () -> StatCollector.translateToLocal(
                    view.get()
                        .battery()
                        .charging() ? "becom.phone.battery.charging" : "becom.phone.battery.not_charging"))
                .asWidget()
                .widthRel(1f)
                .height(10));
        column.child(
            IKey.lang("becom.phone.battery.today")
                .asWidget()
                .widthRel(1f)
                .height(10));
        BatteryUse.Use[] uses = BatteryUse.Use.values();
        for (BatteryUse.Use use : uses) {
            column.child(IKey.dynamic(() -> {
                List<Integer> shares = view.get()
                    .battery()
                    .shares();
                int share = use.ordinal() < shares.size() ? shares.get(use.ordinal()) : 0;
                return StatCollector.translateToLocalFormatted(
                    "becom.phone.battery.use",
                    StatCollector.translateToLocal(
                        "becom.phone.battery.use." + use.name()
                            .toLowerCase(java.util.Locale.ROOT)),
                    share,
                    bar(share));
            })
                .asWidget()
                .widthRel(1f)
                .height(9));
        }
        column.child(
            new ToggleButton().size(WIDTH - 12, 14)
                .overlay(
                    IKey.dynamic(
                        () -> StatCollector.translateToLocal(
                            view.get()
                                .battery()
                                .saving()
                                && !view.get()
                                    .battery()
                                    .saver() ? "becom.phone.battery.saver_auto" : "becom.phone.battery.saver")))
                .value(
                    new BoolValue.Dynamic(
                        () -> view.get()
                            .battery()
                            .saver(),
                        on -> sync.callSyncedAction(SAVER, buf -> buf.writeBoolean(on)))));
        column.child(
            new ToggleButton().size(WIDTH - 12, 14)
                .overlay(IKey.lang("becom.phone.battery.warn"))
                .value(
                    new BoolValue.Dynamic(
                        () -> view.get()
                            .battery()
                            .warns(),
                        on -> sync.callSyncedAction(WARN, buf -> buf.writeBoolean(on)))));
        return column;
    }

    /** EU as the battery page writes it: 744k, 1.2M. */
    static String eu(long eu) {
        if (eu >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", eu / 1_000_000.0);
        if (eu >= 1_000) return eu / 1_000 + "k";
        return String.valueOf(eu);
    }

    /** Minutes as hours and minutes, or "ever" for a drain of nothing. */
    private static String duration(long minutes) {
        if (minutes < 0) return StatCollector.translateToLocal("becom.phone.battery.forever");
        if (minutes < 60) return StatCollector.translateToLocalFormatted("becom.phone.battery.minutes", minutes);
        return StatCollector.translateToLocalFormatted("becom.phone.battery.hours", minutes / 60, minutes % 60);
    }

    /** A share as a bar of up to ten blocks. */
    private static String bar(int share) {
        return "|".repeat(Math.max(0, Math.min(10, (share + 5) / 10)));
    }

    /** The Signal app: the level, what it comes through, and the phone's number. */
    private static ParentWidget<?> signal(Supplier<PhoneView> view) {
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(2);
        column.child(
            IKey.dynamic(
                () -> StatCollector.translateToLocalFormatted(
                    "becom.phone.signal.level",
                    StatCollector.translateToLocal(
                        "becom.level." + view.get()
                            .service()
                            .name()
                            .toLowerCase(java.util.Locale.ROOT))))
                .asWidget()
                .widthRel(1f)
                .height(10));
        column.child(IKey.dynamic(() -> {
            PhoneView.Via via = view.get()
                .via();
            return switch (via.kind()) {
                case NONE -> StatCollector.translateToLocal("becom.phone.signal.via.none");
                case ANTENNA -> StatCollector.translateToLocalFormatted("becom.phone.signal.via.antenna", via.tier());
                case SATELLITES -> StatCollector.translateToLocal("becom.phone.signal.via.satellites");
                case ME_NETWORK -> StatCollector.translateToLocal("becom.phone.signal.via.me_network");
            };
        })
            .asWidget()
            .widthRel(1f)
            .height(10));
        column.child(
            IKey.dynamic(
                () -> view.get()
                    .number() < 0 ? ""
                        : StatCollector.translateToLocalFormatted(
                            "becom.phone.title",
                            view.get()
                                .number(),
                            view.get()
                                .owner()))
                .asWidget()
                .widthRel(1f)
                .height(10));
        return column;
    }

    // endregion

    private static String status(PhoneView view) {
        String other = view.otherName()
            .isEmpty() ? String.valueOf(view.otherNumber()) : view.otherName();
        if (view.carried() && !view.on()) return StatCollector.translateToLocal("becom.phone.state.off");
        if (view.offline()) return StatCollector.translateToLocal("becom.phone.state.offline");
        int more = view.members()
            .size() - 1;
        if (view.state() == PhoneView.State.CONNECTED && more > 0) {
            return StatCollector.translateToLocalFormatted("becom.phone.state.connected_group", other, more);
        }
        return switch (view.state()) {
            // A Telephone is on its hook; a carried phone or a Videophone, which has none, is ready.
            case IDLE -> StatCollector.translateToLocal(
                view.carried() || view.video()
                    .capable() ? "becom.phone.state.ready" : "becom.phone.state.idle");
            case OFF_HOOK -> StatCollector.translateToLocal("becom.phone.state.off_hook");
            case RINGING_OUT -> StatCollector.translateToLocalFormatted("becom.phone.state.ringing_out", other);
            case RINGING_IN -> StatCollector.translateToLocalFormatted("becom.phone.state.ringing_in", other);
            case CONNECTED -> StatCollector.translateToLocalFormatted("becom.phone.state.connected", other);
            case IN_USE -> StatCollector.translateToLocal("becom.phone.state.in_use");
        };
    }

    private static ParentWidget<?> keypad(PanelSyncManager sync, Supplier<PhoneView> view, StringBuilder typed) {
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(2);
        column.child(
            IKey.dynamic(
                () -> typed.length() == 0 ? StatCollector.translateToLocal("becom.phone.type_number")
                    : typed.toString())
                .asWidget()
                .widthRel(1f)
                .height(10));
        String[][] keys = { { "1", "2", "3" }, { "4", "5", "6" }, { "7", "8", "9" }, { "C", "0", "<" } };
        for (String[] rowKeys : keys) {
            Flow row = Flow.row()
                .coverChildren()
                .childPadding(2);
            for (String key : rowKeys) {
                row.child(
                    new ButtonWidget<>().size(KEY_SIZE, KEY_SIZE - 6)
                        .overlay(IKey.str(key))
                        .onMousePressed(button -> {
                            switch (key) {
                                case "C" -> typed.setLength(0);
                                case "<" -> {
                                    if (typed.length() > 0) typed.setLength(typed.length() - 1);
                                }
                                default -> {
                                    if (typed.length() < PhoneNumbers.DIGITS) typed.append(key);
                                }
                            }
                            return true;
                        }));
            }
            column.child(row);
        }
        Flow actions = Flow.row()
            .coverChildren()
            .childPadding(2);
        // During a call, the same button adds the number to it.
        actions.child(
            new ButtonWidget<>().size(54, 14)
                .overlay(
                    IKey.dynamic(
                        () -> StatCollector.translateToLocal(
                            view.get()
                                .state() == PhoneView.State.CONNECTED ? "becom.phone.add" : "becom.phone.call")))
                .onMousePressed(button -> {
                    int number = PhoneNumbers.parse(typed.toString());
                    if (number >= 0) sync.callSyncedAction(DIAL, buf -> buf.writeInt(number));
                    return true;
                }));
        actions.child(
            new ButtonWidget<>().size(54, 14)
                .overlay(IKey.lang("becom.phone.answer"))
                .setEnabledIf(
                    w -> view.get()
                        .state() == PhoneView.State.RINGING_IN)
                .onMousePressed(button -> {
                    sync.callSyncedAction(PICK_UP, buf -> {});
                    return true;
                }));
        actions.child(
            new ButtonWidget<>().size(54, 14)
                .overlay(IKey.lang("becom.phone.hang_up"))
                .onMousePressed(button -> {
                    sync.callSyncedAction(HANG_UP, buf -> {});
                    return true;
                }));
        column.child(actions);
        return column;
    }

    /**
     * A paged list: each row dials its number; for the phone's owner, a second button blocks whoever the row stands
     * for.
     * The server decides who that is from the row, so the client only names the row.
     */
    private static ParentWidget<?> rows(PanelSyncManager sync, IntSupplier size, IntFunction<String> label,
        IntFunction<Integer> number, BiConsumer<Integer, Integer> block, Supplier<PhoneView> view) {
        int[] page = { 0 };
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(1);
        for (int r = 0; r < ROWS; r++) {
            int row = r;
            Flow line = Flow.row()
                .widthRel(1f)
                .height(ROW_HEIGHT)
                .childPadding(2);
            line.child(
                new ButtonWidget<>().width(WIDTH - 12 - 34)
                    .height(ROW_HEIGHT)
                    .overlay(IKey.dynamic(() -> {
                        int i = page[0] * ROWS + row;
                        return i < size.getAsInt() ? label.apply(i) : "";
                    }))
                    .setEnabledIf(w -> page[0] * ROWS + row < size.getAsInt())
                    .onMousePressed(button -> {
                        int i = page[0] * ROWS + row;
                        if (i < size.getAsInt()) {
                            int n = number.apply(i);
                            sync.callSyncedAction(DIAL, buf -> buf.writeInt(n));
                        }
                        return true;
                    }));
            line.child(
                new ButtonWidget<>().width(32)
                    .height(ROW_HEIGHT)
                    .overlay(IKey.lang("becom.phone.block"))
                    .setEnabledIf(
                        w -> view.get()
                            .mayManage() && page[0] * ROWS + row < size.getAsInt())
                    .onMousePressed(button -> {
                        int i = page[0] * ROWS + row;
                        if (i < size.getAsInt()) block.accept(i, number.apply(i));
                        return true;
                    }));
            column.child(line);
        }
        column.child(pager(page, size, ROWS));
        return column;
    }

    static Flow pager(int[] page, IntSupplier size, int rows) {
        Flow pager = Flow.row()
            .coverChildren()
            .childPadding(4);
        pager.child(
            new ButtonWidget<>().size(20, 12)
                .overlay(IKey.str("<"))
                .onMousePressed(button -> {
                    if (page[0] > 0) page[0]--;
                    return true;
                }));
        pager.child(IKey.dynamic(() -> {
            // A list can shrink under an open page, after an unblock or a new view.
            int pages = Math.max(1, (size.getAsInt() + rows - 1) / rows);
            if (page[0] >= pages) page[0] = pages - 1;
            return (page[0] + 1) + "/" + pages;
        })
            .asWidget()
            .size(30, 12));
        pager.child(
            new ButtonWidget<>().size(20, 12)
                .overlay(IKey.str(">"))
                .onMousePressed(button -> {
                    if ((page[0] + 1) * rows < size.getAsInt()) page[0]++;
                    return true;
                }));
        return pager;
    }

    /** Everyone else in the call: who, at what level, or still ringing. */
    private static ParentWidget<?> members(Supplier<PhoneView> view) {
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(1);
        for (int r = 0; r < PhoneView.MAX_MEMBERS && r < ROWS + 3; r++) {
            int row = r;
            Flow line = Flow.row()
                .widthRel(1f)
                .height(ROW_HEIGHT - 3)
                .childPadding(2);
            line.child(IKey.dynamic(() -> {
                List<PhoneView.Member> members = view.get()
                    .members();
                if (row >= members.size()) return "";
                PhoneView.Member member = members.get(row);
                String name = member.name()
                    .isEmpty() ? String.valueOf(member.number()) : member.name();
                return member.ringing() ? StatCollector.translateToLocalFormatted("becom.phone.member_ringing", name)
                    : name;
            })
                .asWidget()
                .width(WIDTH - 12 - BARS_WIDTH - 2)
                .height(ROW_HEIGHT - 3));
            line.child(BeCom.proxy.signalBars(() -> {
                List<PhoneView.Member> members = view.get()
                    .members();
                return row < members.size() ? members.get(row)
                    .level() : SignalLevel.NONE;
            })
                .asWidget()
                .size(BARS_WIDTH, ROW_HEIGHT - 3));
            column.child(line);
        }
        return column;
    }

    /**
     * The other side full size, and this player's camera switches: each camera on or off, and
     * the corner window shown or hidden. The window is the client's own choice; the cameras are the server's.
     */
    private static ParentWidget<?> video(PanelSyncManager sync, Supplier<PhoneView> view) {
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(2);
        int pageHeight = HEIGHT - 12 - 10 - 10 - 14 - 9;
        column.child(
            BeCom.proxy.videoView(
                () -> view.get()
                    .video()
                    .capable())
                .asWidget()
                .widthRel(1f)
                .height(pageHeight - TOGGLE_HEIGHT - 4));
        int third = (WIDTH - 12 - 2 * 2) / 3;
        Flow toggles = Flow.row()
            .widthRel(1f)
            .height(TOGGLE_HEIGHT)
            .childPadding(2);
        toggles.child(
            new ToggleButton().size(third, TOGGLE_HEIGHT)
                .overlay(IKey.lang("becom.phone.video.front"))
                .value(
                    new BoolValue.Dynamic(
                        () -> view.get()
                            .video()
                            .front(),
                        on -> sync.callSyncedAction(FRONT, buf -> buf.writeBoolean(on))))
                .setEnabledIf(
                    w -> view.get()
                        .video()
                        .capable()
                        && view.get()
                            .state() == PhoneView.State.CONNECTED));
        toggles.child(
            new ToggleButton().size(third, TOGGLE_HEIGHT)
                .overlay(IKey.lang("becom.phone.video.rear"))
                .value(
                    new BoolValue.Dynamic(
                        () -> view.get()
                            .video()
                            .rear(),
                        on -> sync.callSyncedAction(REAR, buf -> buf.writeBoolean(on))))
                .setEnabledIf(
                    w -> view.get()
                        .video()
                        .rearPossible() && BeCom.proxy.rearCameraWorks()));
        toggles.child(
            new ToggleButton().size(third, TOGGLE_HEIGHT)
                .overlay(IKey.lang("becom.phone.video.window"))
                .value(new BoolValue.Dynamic(BeCom.proxy::videoWindowShown, BeCom.proxy::setVideoWindowShown))
                .setEnabledIf(
                    w -> view.get()
                        .video()
                        .capable()));
        column.child(toggles);
        return column;
    }

    private static ParentWidget<?> settings(PanelSyncManager sync, Supplier<PhoneView> view, Flow top) {
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(2);
        if (top != null) column.child(top);
        int half = (WIDTH - 12 - 2) / 2;
        Flow firstRow = Flow.row()
            .widthRel(1f)
            .height(14)
            .childPadding(2);
        firstRow.child(
            toggle(
                sync,
                DND,
                "becom.phone.dnd",
                () -> view.get()
                    .doNotDisturb(),
                view).width(half));
        firstRow.child(
            toggle(
                sync,
                HIDE,
                "becom.phone.hidden",
                () -> view.get()
                    .hidden(),
                view).width(half));
        column.child(firstRow);
        // A carried phone's own switches: power for whoever carries it, silent mode for its owner.
        Flow secondRow = Flow.row()
            .widthRel(1f)
            .height(14)
            .childPadding(2);
        secondRow.child(
            new ToggleButton().size(half, 14)
                .overlay(IKey.lang("becom.phone.power"))
                .value(
                    new BoolValue.Dynamic(
                        () -> view.get()
                            .on(),
                        on -> sync.callSyncedAction(POWER, buf -> buf.writeBoolean(on))))
                .setEnabledIf(
                    w -> view.get()
                        .carried()));
        secondRow.child(
            toggle(
                sync,
                SILENT,
                "becom.phone.silent",
                () -> view.get()
                    .silent(),
                view).width(half)
                    .setEnabledIf(
                        w -> view.get()
                            .carried()
                            && view.get()
                                .mayManage()));
        column.child(secondRow);
        column.child(
            IKey.lang("becom.phone.blocked")
                .asWidget()
                .widthRel(1f)
                .height(10));
        int[] page = { 0 };
        IntSupplier size = () -> view.get()
            .blocked()
            .size();
        for (int r = 0; r < BLOCKED_ROWS; r++) {
            int row = r;
            Flow line = Flow.row()
                .widthRel(1f)
                .height(ROW_HEIGHT)
                .childPadding(2);
            line.child(IKey.dynamic(() -> {
                int i = page[0] * BLOCKED_ROWS + row;
                List<PhoneView.Blocked> blocked = view.get()
                    .blocked();
                return i < blocked.size() ? blocked.get(i)
                    .name() : "";
            })
                .asWidget()
                .width(WIDTH - 12 - 50)
                .height(ROW_HEIGHT));
            line.child(
                new ButtonWidget<>().width(48)
                    .height(ROW_HEIGHT)
                    .overlay(IKey.lang("becom.phone.unblock"))
                    .setEnabledIf(w -> page[0] * BLOCKED_ROWS + row < size.getAsInt())
                    .onMousePressed(button -> {
                        int i = page[0] * BLOCKED_ROWS + row;
                        List<PhoneView.Blocked> blocked = view.get()
                            .blocked();
                        if (i < blocked.size()) {
                            UUID id = blocked.get(i)
                                .player();
                            sync.callSyncedAction(UNBLOCK, buf -> {
                                buf.writeLong(id.getMostSignificantBits());
                                buf.writeLong(id.getLeastSignificantBits());
                            });
                        }
                        return true;
                    }));
            column.child(line);
        }
        column.child(pager(page, size, BLOCKED_ROWS));
        column.child(
            IKey.dynamic(
                () -> view.get()
                    .mayManage() ? "" : StatCollector.translateToLocal("becom.phone.not_owner"))
                .asWidget()
                .widthRel(1f)
                .height(10));
        return column;
    }

    private static ToggleButton toggle(PanelSyncManager sync, String action, String langKey,
        java.util.function.BooleanSupplier current, Supplier<PhoneView> view) {
        return new ToggleButton().size(WIDTH - 12, 14)
            .overlay(IKey.lang(langKey))
            .value(new BoolValue.Dynamic(current, on -> sync.callSyncedAction(action, buf -> buf.writeBoolean(on))))
            .setEnabledIf(
                w -> view.get()
                    .mayManage());
    }
}
