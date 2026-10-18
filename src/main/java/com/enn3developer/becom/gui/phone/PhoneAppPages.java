package com.enn3developer.becom.gui.phone;

import static com.enn3developer.becom.gui.phone.PhoneUi.SH;
import static com.enn3developer.becom.gui.phone.PhoneUi.STATUS;
import static com.enn3developer.becom.gui.phone.PhoneUi.SW;
import static com.enn3developer.becom.gui.phone.PhoneUi.SX;
import static com.enn3developer.becom.gui.phone.PhoneUi.SY;
import static com.enn3developer.becom.gui.phone.PhoneUi.t;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.network.PacketBuffer;

import org.lwjgl.input.Keyboard;

import com.enn3developer.becom.gui.AppsView;
import com.enn3developer.becom.gui.PhoneView;
import com.enn3developer.becom.gui.paint.Canvas;
import com.enn3developer.becom.gui.paint.Glyphs;
import com.enn3developer.becom.gui.paint.Paint;
import com.enn3developer.becom.logic.app.AppManifest;
import com.enn3developer.becom.logic.app.AppTree;

/**
 * The apps' pages, from the app boards: a Lua app drawn from its widget tree (AppKit, Lua app), the phone's own states
 * around it, Settings › Installed apps and an app's settings (App settings), the App Store and an app's store page,
 * and the install or publish a computer offers (Install, Publish). Each box is where the board's layout puts it, and
 * each text where the board's font has its capitals.
 */
final class PhoneAppPages {

    private static final int STORE = 0x9f2f5a;
    private static final int STORE_SHADOW = 0x4a1428;
    private static final int STORE_TAB = 0xff6b9a;
    private static final int SETTINGS = 0x5a5d62;
    private static final int SETTINGS_SHADOW = 0x2a2c2f;
    private static final int DEFAULT_ACCENT = 0x24504a;
    // BeCom's mint, whatever the app's accent: the headings it draws an app's tree with, and its network mark.
    private static final int MINT = 0x55ffaa;
    // The edge of the boards' flat boxes on the dark screen, and a text box's inside.
    private static final int EDGE = 0x3a434e;
    private static final int FIELD = 0x06080a;
    private static final int MAX_TEXT = 256;
    private static final String SEARCH = "\0search";
    private static final String[] TABS = { "popular", "new", "installed" };

    private final PhoneUi ui;
    // The text box being typed in: an app's input by its id, or the store's search; null for none.
    private String focus;
    private final StringBuilder input = new StringBuilder();
    // The open app's first widget on screen, as the wheel scrolls it.
    private int first;
    private String query = "";
    private int tab;
    private int page;
    // The publish offer's choice: everyone, or the publisher's team.
    private boolean team;

    PhoneAppPages(PhoneUi ui) {
        this.ui = ui;
    }

    static void write(PacketBuffer buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, MAX_TEXT * 4);
        buf.writeVarIntToBuffer(length);
        buf.writeBytes(bytes, 0, length);
    }

    // region Opening

    void openApp(String id) {
        ui.app = id;
        first = 0;
        unfocus();
        ui.sender.send(PhoneActions.APP_OPEN, buf -> write(buf, id));
        ui.open(PhoneUi.Page.APP);
    }

    void openSettings(String id) {
        ui.app = id;
        ui.open(PhoneUi.Page.APP_SETTINGS);
    }

    void openStore() {
        unfocus();
        ask();
        ui.open(PhoneUi.Page.STORE);
    }

    /** Asks the server for the store's page for the search and the tab. */
    private void ask() {
        String q = query;
        int t = tab;
        int p = page;
        ui.sender.send(PhoneActions.STORE_QUERY, buf -> {
            write(buf, q);
            buf.writeByte(t);
            buf.writeShort(p);
        });
    }

    void unfocus() {
        focus = null;
        input.setLength(0);
    }

    boolean key(char typed, int code) {
        if (focus == null) return false;
        if (code == Keyboard.KEY_ESCAPE) {
            unfocus();
            return true;
        }
        if (code == Keyboard.KEY_BACK) {
            if (input.length() > 0) input.setLength(input.length() - 1);
            if (focus.equals(SEARCH)) search();
            return true;
        }
        if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
            if (!focus.equals(SEARCH)) {
                event(focus, input.toString());
                input.setLength(0);
            }
            return true;
        }
        if (typed >= ' ' && typed != 127 && input.length() < MAX_TEXT) {
            input.append(typed);
            if (focus.equals(SEARCH)) search();
            return true;
        }
        return false;
    }

    private void search() {
        query = input.toString();
        page = 0;
        ask();
    }

    void scroll(int amount) {
        if (ui.page == PhoneUi.Page.APP) first = Math.max(0, first - Integer.signum(amount));
        if (ui.page == PhoneUi.Page.STORE) {
            AppsView apps = ui.view.get()
                .apps();
            int pages = Math.max(1, (apps.total() + AppsView.STORE_ROWS - 1) / AppsView.STORE_ROWS);
            int next = Math.max(0, Math.min(pages - 1, page - Integer.signum(amount)));
            if (next != page) {
                page = next;
                ask();
            }
        }
    }

    private void event(String widget, String value) {
        String app = ui.app;
        ui.sender.send(PhoneActions.APP_EVENT, buf -> {
            write(buf, app);
            write(buf, widget);
            write(buf, value.length() > MAX_TEXT ? value.substring(0, MAX_TEXT) : value);
        });
    }

    // endregion

    // region The open app

    /** A Lua app: its bar in its accent with a Lua chip, and its widgets. */
    void paintApp(Canvas c, PhoneView v) {
        ui.paintStatus(c, v);
        AppsView.App app = PhoneUi.installed(v, ui.app);
        int accent = app == null ? DEFAULT_ACCENT : app.accent() & 0xffffff;
        float y = ui.paintBar(c, app == null ? "" : app.name(), accent, Paint.darker(accent, 0.55f));
        // The chip is BeCom's, not the app's: an 11-pixel line in a 1-pixel frame, 4 of padding each side, 8 in from
        // the bar's end.
        String chip = "Lua";
        float cw = c.width(chip, 11) + 10;
        float ch = lineOf(11) + 2;
        float cx = SX + SW - 8 - cw;
        float cy = SY + STATUS + (PhoneUi.BAR - ch) / 2;
        Paint.border(c, cx, cy, cw, ch, 1, PhoneUi.LUA_CHIP);
        Paint.centered(c, chip, cx + cw / 2, capitals(cy + 1, lineOf(11), 11), 11, PhoneUi.LUA_CHIP);
        float footY = SY + SH;
        AppsView.Screen screen = v.apps()
            .screen();
        AppsView.State state = screen.id()
            .equals(ui.app) ? screen.state() : AppsView.State.LOADING;
        switch (state) {
            case RUNNING -> paintWidgets(c, screen.widgets(), y, footY);
            case UNREACHABLE -> paintFailed(
                c,
                y,
                footY,
                t("becom.ui.app_state.unreachable"),
                Paint.ORANGE,
                t("becom.ui.app_state.unreachable.why"),
                true);
            case CRASHED -> paintFailed(c, y, footY, t("becom.ui.app_state.crashed"), Paint.RED, screen.error(), false);
            default -> paintLoading(c, y, footY);
        }
    }

    /** Loading, as the kit board draws it: three dots, the lit one going round, over a grey word, 6 apart. */
    private void paintLoading(Canvas c, float top, float bottom) {
        float cx = SX + SW / 2;
        float y = (top + bottom - 6 - 6 - lineOf(12)) / 2;
        long tick = System.currentTimeMillis() / 300 % 3;
        for (int i = 0; i < 3; i++) c.rect(cx - 12 + i * 9, y, 6, 6, i == tick ? MINT : 0x2a5a4a);
        Paint.centered(c, t("becom.ui.app_state.loading"), cx, capitals(y + 6 + 6, lineOf(12), 12), 12, Paint.GREY);
    }

    /**
     * Can't reach it or crashed, as the kit board's states: a coloured title and why under it, 6 apart; and Retry,
     * which opens the app again, where there's something to retry.
     */
    private void paintFailed(Canvas c, float top, float bottom, String title, int color, String why, boolean retry) {
        float cx = SX + SW / 2;
        List<String> lines = why.isEmpty() ? List.of() : Paint.wrap(c, why, 11, SW - 20);
        float h = lineOf(13) + (lines.isEmpty() ? 0 : 6 + lines.size() * lineOf(11)) + (retry ? 6 + 22 : 0);
        float y = (top + bottom - h) / 2;
        Paint.centered(c, title, cx, capitals(y, lineOf(13), 13), 13, color);
        y += lineOf(13);
        if (!lines.isEmpty()) y += 6;
        for (String line : lines) {
            Paint.centered(c, line, cx, capitals(y, lineOf(11), 11), 11, Paint.GREY);
            y += lineOf(11);
        }
        if (!retry) return;
        y += 6;
        // The kit's button, 22 tall: 12 of padding each side of its label and a 2-pixel bevel.
        String label = t("becom.ui.app_state.retry");
        float w = c.width(label, 12) + 28;
        button(c, cx - w / 2, y, w, 22, Paint.Style.PLAIN, label, 12);
        String id = ui.app;
        ui.hit(cx - w / 2, y, w, 22, () -> ui.sender.send(PhoneActions.APP_OPEN, buf -> write(buf, id)));
    }

    /** The app's widgets top to bottom from the first the wheel left on screen; buttons in a row share it. */
    private void paintWidgets(Canvas c, List<AppTree.Widget> widgets, float y, float bottom) {
        float x = SX + 10;
        float w = SW - 20;
        first = Math.min(first, Math.max(0, widgets.size() - 1));
        for (int i = first; i < widgets.size(); i++) {
            AppTree.Widget widget = widgets.get(i);
            if (widget.kind() == AppTree.Kind.BUTTON) {
                int end = i;
                while (end + 1 < widgets.size() && widgets.get(end + 1)
                    .kind() == AppTree.Kind.BUTTON) end++;
                float h = height(c, widget, w);
                if (y + h > bottom) break;
                // The board's grid: 10 of padding round buttons 32 tall, 8 apart.
                int count = end - i + 1;
                float bw = (w - (count - 1) * 8) / count;
                for (int b = 0; b <= end - i; b++) {
                    AppTree.Widget one = widgets.get(i + b);
                    float bx = x + b * (bw + 8);
                    button(c, bx, y + 10, bw, 32, style(one.style()), one.label(), 15);
                    ui.hit(bx, y + 10, bw, 32, () -> event(one.id(), ""));
                }
                y += h;
                i = end;
                continue;
            }
            float h = height(c, widget, w);
            if (y + h > bottom) break;
            paintWidget(c, widget, x, y, w, h);
            y += h;
        }
    }

    /**
     * A widget's height on the Lua board: a heading's 18-pixel line with 8 above and 2 under; a row's 17-pixel line, a
     * toggle's pill or an item's icon with 2 above and under; a bar's 16-pixel line, 3 and the bar's 8 with 4 round
     * them; a row of buttons' 32 with 10 round it.
     */
    private float height(Canvas c, AppTree.Widget widget, float w) {
        return switch (widget.kind()) {
            case HEADING -> 8 + lineOf(15) + 2;
            case TEXT -> 2 + Paint.wrap(c, widget.label(), 14, w)
                .size() * lineOf(14) + 2;
            case VALUE -> 2 + lineOf(14) + 2;
            case PROGRESS -> 4 + lineOf(13) + 3 + 8 + 4;
            case TOGGLE -> 2 + 18 + 2;
            case ITEM -> 2 + (widget.value()
                .isEmpty() ? lineOf(14) : 18) + 2;
            case INPUT -> 2 + 26 + 2;
            case BUTTON -> 10 + 32 + 10;
        };
    }

    private void paintWidget(Canvas c, AppTree.Widget widget, float x, float y, float w, float h) {
        switch (widget.kind()) {
            case HEADING -> c.text(Paint.fit(c, widget.label(), 15, w), x, capitals(y + 8, lineOf(15), 15), 15, MINT);
            case TEXT -> {
                for (String line : Paint.wrap(c, widget.label(), 14, w)) {
                    c.text(line, x, capitals(y + 2, lineOf(14), 14), 14, Paint.TEXT);
                    y += lineOf(14);
                }
            }
            case VALUE -> {
                float ty = capitals(y, h, 14);
                c.text(widget.label(), x, ty, 14, Paint.GREY);
                Paint.right(c, widget.value(), x + w, ty, 14, levelColour(widget.level(), Paint.TEXT));
            }
            case PROGRESS -> {
                // A plain bar is orange, as the kit board's.
                int colour = levelColour(widget.level(), Paint.ORANGE);
                float ty = capitals(y + 4, lineOf(13), 13);
                c.text(widget.label(), x, ty, 13, Paint.GREY);
                Paint.right(c, Math.round(widget.fraction() * 100) + "%", x + w, ty, 13, colour);
                float by = y + 4 + lineOf(13) + 3;
                c.rect(x, by, w, 8, Paint.LINE);
                c.rect(x, by, (float) (w * Math.max(0, Math.min(1, widget.fraction()))), 8, colour);
            }
            case TOGGLE -> {
                boolean on = "true".equals(widget.value());
                c.text(widget.label(), x, capitals(y, h, 14), 14, Paint.TEXT);
                Paint.pill(c, x + w - 36, y + 2, on);
                ui.hit(x, y, w, h, () -> event(widget.id(), String.valueOf(!on)));
            }
            case ITEM -> {
                // Its icon 8 before the name: the item, or the kit's grey square for one the game can't draw. A row
                // with no item is just a name and its value, as the Lua board's fuel rods.
                String item = widget.value();
                float lx = x;
                if (!item.isEmpty()) {
                    if (item.contains(":")) c.item(item, x + 1, y + 3, 16);
                    else {
                        c.rect(x, y + 2, 18, 18, 0x5a5a5a);
                        c.rect(x + 1, y + 3, 16, 16, 0x9a9a9a);
                    }
                    lx = x + 18 + 8;
                }
                String label = widget.label()
                    .isEmpty() ? item : widget.label();
                float ty = capitals(y, h, 14);
                float vw = c.width(widget.extra(), 14);
                c.text(Paint.fit(c, label, 14, x + w - vw - 8 - lx), lx, ty, 14, Paint.TEXT);
                Paint.right(c, widget.extra(), x + w, ty, 14, levelColour(widget.level(), Paint.GREY));
            }
            case INPUT -> {
                boolean focused = widget.id()
                    .equals(focus);
                paintBox(c, x, y + 2, w, 26, focused, focused ? input.toString() : "", widget.value());
                ui.hit(x, y + 2, w, 26, () -> {
                    if (!widget.id()
                        .equals(focus)) {
                        focus = widget.id();
                        input.setLength(0);
                    }
                });
            }
            case BUTTON -> {}
        }
    }

    /** A text box of the boards: dark in a 1-pixel edge, its hint grey while it's empty, and a cursor with focus. */
    private static void paintBox(Canvas c, float x, float y, float w, float h, boolean focused, String text,
        String hint) {
        c.rect(x, y, w, h, focused ? 0x4a5560 : EDGE);
        c.rect(x + 1, y + 1, w - 2, h - 2, FIELD);
        // 6 of padding inside the edge, the 13-pixel line in the middle.
        float tx = x + 1 + 6;
        float ty = capitals(y + 1, h - 2, 13);
        if (text.isEmpty() && !focused) {
            c.text(Paint.fit(c, hint, 13, w - 14), tx, ty, 13, Paint.DARK);
            return;
        }
        String shown = text;
        while (!shown.isEmpty() && c.width(shown + "_", 13) > w - 14) shown = shown.substring(1);
        c.text(shown, tx, ty, 13, Paint.WHITE);
        if (focused && System.currentTimeMillis() / 500 % 2 == 0)
            c.text("_", tx + c.width(shown, 13), ty, 13, Paint.GREEN);
    }

    private static Paint.Style style(AppTree.Style style) {
        return switch (style) {
            case GO -> Paint.Style.GO;
            case DANGER -> Paint.Style.DANGER;
            default -> Paint.Style.PLAIN;
        };
    }

    /**
     * A level's colour, or {@code plain} for none. The Lua board draws a low value red, the danger end of the scale;
     * the others are a signal's.
     */
    private static int levelColour(AppTree.Level level, int plain) {
        return switch (level) {
            case LOW -> Paint.RED;
            case MEDIUM -> Paint.YELLOW;
            case HIGH -> Paint.GREEN;
            case MAX -> Paint.CYAN;
            default -> plain;
        };
    }

    // endregion

    // region Settings › Installed apps, and an app's settings

    void paintInstalled(Canvas c, PhoneView v) {
        ui.paintStatus(c, v);
        float y = ui.paintBar(c, t("becom.ui.settings.installed_title"), SETTINGS, SETTINGS_SHADOW);
        List<AppsView.App> apps = v.apps()
            .installed();
        if (apps.isEmpty()) {
            Paint.centered(c, t("becom.ui.settings.apps.none"), SX + SW / 2, y + 30, 13, Paint.GREY);
            return;
        }
        for (AppsView.App app : apps) {
            if (y + 44 > SY + SH) break;
            boolean hovered = ui.hover(SX, y, SW, 44);
            if (hovered) c.rect(SX, y, SW, 44, 0x141a20);
            icon(c, app.icon(), app.accent(), SX + 10, y + 6, 32, 20);
            float ty = y + (44 - c.lineHeight(14) - 3 - c.lineHeight(11)) / 2;
            c.text(app.name() + " " + app.version(), SX + 52, ty, 14, Paint.WHITE);
            c.text(
                Paint.fit(
                    c,
                    t(app.store() ? "becom.ui.app.from_store" : "becom.ui.app.from_computer", app.author()),
                    11,
                    SW - 80),
                SX + 52,
                ty + c.lineHeight(14) + 3,
                11,
                Paint.GREY);
            c.rect(SX, y + 43, SW, 1, Paint.LINE);
            String id = app.id();
            ui.hit(SX, y, SW, 44, () -> openSettings(id));
            y += 44;
        }
    }

    void paintAppSettings(Canvas c, PhoneView v) {
        ui.paintStatus(c, v);
        AppsView.App app = PhoneUi.installed(v, ui.app);
        float y = ui.paintBar(c, t("becom.ui.settings.of", app == null ? "" : app.name()), SETTINGS, SETTINGS_SHADOW);
        if (app == null) {
            Paint.centered(c, t("becom.ui.app.gone"), SX + SW / 2, y + 30, 13, Paint.GREY);
            return;
        }
        // The board's head: 10 of padding round the icon, and 10 beside it its name's 19-pixel line over the grey
        // 14, in the icon's middle.
        float x = SX + 10;
        y += 10;
        icon(c, app.icon(), app.accent(), x, y, 36, 22);
        String from = app.store() ? t("becom.ui.app.by_from_store", app.author())
            : t("becom.ui.app.by_from_computer", app.author(), app.where());
        float ty = y + (36 - lineOf(16) - 14) / 2;
        c.text(app.name() + " " + app.version(), x + 46, capitals(ty, lineOf(16), 16), 16, Paint.WHITE);
        c.text(Paint.fit(c, from, 11, SW - 66), x + 46, capitals(ty + lineOf(16), 14, 11), 11, Paint.GREY);
        y += 36 + 10;
        boolean manage = v.mayManage();
        y = ui.section(c, y, t("becom.ui.app_settings.background"));
        float next = ui.paddedRow(
            c,
            y,
            t("becom.ui.app_settings.run_in_background"),
            t("becom.ui.app_settings.run_in_background.sub"));
        pill(c, y, next, app.background());
        if (manage) {
            boolean on = app.background();
            String id = app.id();
            ui.hit(SX, y, SW, next - y, () -> ui.sender.send(PhoneActions.APP_BACKGROUND, buf -> {
                write(buf, id);
                buf.writeBoolean(!on);
            }));
        }
        y = next;
        y = value(c, y, t("becom.ui.app_settings.woke"), t("becom.ui.app_settings.times", app.woke()));
        long max = v.battery()
            .max();
        String battery = max <= 0 ? PhoneSettingsPages.eu(app.euToday()) + " EU"
            : Math.round(app.euToday() * 100.0 / max) + "%";
        y = value(c, y, t("becom.ui.app_settings.battery"), battery);
        y = ui.section(c, y, t("becom.ui.app_settings.it_can"));
        boolean any = false;
        for (AppManifest.Use use : AppManifest.Use.VALUES) {
            if ((app.uses() & use.bit()) == 0 || use == AppManifest.Use.STORAGE) continue;
            any = true;
            next = ui.paddedRow(c, y, t("becom.ui.use." + use.word()), null);
            boolean on = (app.denied() & use.bit()) == 0;
            pill(c, y, next, on);
            if (manage) {
                String id = app.id();
                ui.hit(SX, y, SW, next - y, () -> ui.sender.send(PhoneActions.APP_ALLOWED, buf -> {
                    write(buf, id);
                    buf.writeByte(use.ordinal());
                    buf.writeBoolean(!on);
                }));
            }
            y = next;
        }
        if (!any) y = ui.paddedRow(c, y, t("becom.ui.use.nothing"), null);
        String storage = t("becom.ui.app_settings.storage_of", kib(app.stored()), kib(app.storage()));
        y = value(c, y, t("becom.ui.app_settings.storage"), storage);
        if (manage) {
            // At the foot inside 8 and 10 of padding: a flat button in a dark red edge.
            float by = SY + SH - 8 - 30;
            boolean hovered = ui.hover(x, by, SW - 20, 30);
            flat(
                c,
                x,
                by,
                SW - 20,
                30,
                t("becom.ui.app_settings.uninstall"),
                14,
                hovered ? Paint.brighter(Paint.LINE) : Paint.LINE,
                0x5a2a24,
                0xff6b55);
            String id = app.id();
            ui.hit(x, by, SW - 20, 30, () -> {
                ui.sender.send(PhoneActions.APP_UNINSTALL, buf -> write(buf, id));
                ui.goBack();
            });
        }
    }

    /** A row's pill, 10 in from the right and in the middle of the row inside its padding and over its line. */
    private static void pill(Canvas c, float y, float next, boolean on) {
        Paint.pill(c, SX + SW - 46, y + 2 + (next - y - 5 - 18) / 2, on);
    }

    /** A row with its value on the right in grey, as the board's "14 times". */
    private float value(Canvas c, float y, String title, String value) {
        float next = ui.paddedRow(c, y, title, null);
        Paint.right(c, value, SX + SW - 10, capitals(y + 2, next - y - 5, 13), 13, Paint.GREY);
        return next;
    }

    static String kb(long bytes) {
        return Math.max(1, (bytes + 1023) / 1024) + " KB";
    }

    /** Whole kilobytes, up: what "12 of 64 KB" counts in. */
    private static long kib(long bytes) {
        return (bytes + 1023) / 1024;
    }

    /**
     * An app's icon on its accent, {@code size} square: its own 8 by 8 art, or the store's default, {@code art} wide
     * in the middle as the boards size each.
     */
    static void icon(Canvas c, String art, int accent, float x, float y, float size, float glyph) {
        int colour = accent == 0 ? DEFAULT_ACCENT : accent & 0xffffff;
        c.rect(x, y, size, size, colour);
        float cell = glyph / 8;
        float inset = (size - glyph) / 2;
        if (!Paint.appIcon(c, x + inset, y + inset, cell, art)) {
            Paint.glyph(c, x + inset, y + inset, cell, Glyphs.APP, 0x8ab4ff, colour);
        }
    }

    // endregion

    // region App Store

    void paintStore(Canvas c, PhoneView v) {
        ui.paintStatus(c, v);
        float y = ui.paintBar(c, t("becom.ui.app.store"), STORE, STORE_SHADOW);
        AppsView apps = v.apps();
        if (noMirror(c, apps, y)) return;
        float x = SX + 10;
        float w = SW - 20;
        y += 8;
        boolean focused = SEARCH.equals(focus);
        paintBox(c, x, y, w, 26, focused, focused ? input.toString() : query, t("becom.ui.store.search"));
        ui.hit(x, y, w, 26, () -> {
            if (!SEARCH.equals(focus)) {
                focus = SEARCH;
                input.setLength(0);
                input.append(query);
            }
        });
        // The tabs 8 under the search, 26 tall with the chosen one's 2-pixel underline at their foot, over a line.
        y += 26 + 8;
        float tw = SW / 3f;
        for (int i = 0; i < TABS.length; i++) {
            float tx = SX + i * tw;
            boolean on = i == tab;
            Paint.centered(
                c,
                t("becom.ui.store.tab." + TABS[i]),
                tx + tw / 2,
                capitals(y, 24, 13),
                13,
                on ? Paint.WHITE : Paint.GREY);
            if (on) c.rect(tx, y + 24, tw, 2, STORE_TAB);
            int chosen = i;
            ui.hit(tx, y, tw, 26, () -> {
                tab = chosen;
                page = 0;
                ask();
            });
        }
        c.rect(SX, y + 26, SW, 1, Paint.LINE);
        y += 27;
        // The foot, while there's more than one page: a line over the page count, 6 of padding round it.
        int pages = Math.max(1, (apps.total() + AppsView.STORE_ROWS - 1) / AppsView.STORE_ROWS);
        float footY = pages > 1 ? SY + SH - 6 - 14 - 6 - 1 : SY + SH;
        if (apps.store()
            .isEmpty()) {
            Paint.centered(
                c,
                t(query.isEmpty() ? "becom.ui.store.empty" : "becom.ui.store.no_match"),
                SX + SW / 2,
                y + 24,
                13,
                Paint.GREY);
        }
        for (AppsView.StoreApp app : apps.store()) {
            // The board's row: 8 of padding round the icon, the text and the button, each in the row's middle; the
            // text the name's 17-pixel line, the grey 13 and the description's 14 each, 1 apart, in full.
            String label = installLabel(app, false);
            float bw = c.width(label, 12) + 18;
            float textW = w - 32 - 8 - 8 - bw;
            List<String> about = Paint.wrap(c, app.about(), 11, textW);
            float column = lineOf(14) + 1 + lineOf(11) + 1 + about.size() * 14;
            float content = Math.max(32, column);
            float h = 8 + content + 8 + 1;
            if (y + h > footY) break;
            icon(c, app.icon(), app.accent(), x, y + 8 + (content - 32) / 2, 32, 20);
            float tx = x + 32 + 8;
            float ty = y + 8 + (content - column) / 2;
            c.text(Paint.fit(c, app.name(), 14, textW), tx, capitals(ty, lineOf(14), 14), 14, Paint.WHITE);
            ty += lineOf(14) + 1;
            String by = t("becom.ui.store.by_installs", app.author(), app.installs());
            c.text(Paint.fit(c, by, 11, textW), tx, capitals(ty, lineOf(11), 11), 11, Paint.GREY);
            ty += lineOf(11) + 1;
            for (String line : about) {
                c.text(line, tx, capitals(ty, 14, 11), 11, Paint.DIM);
                ty += 14;
            }
            String id = app.id();
            ui.hit(SX, y, SW, h, () -> {
                ui.app = id;
                ui.open(PhoneUi.Page.STORE_APP);
            });
            paintInstallButton(c, apps, app, x + w - bw, y + 8 + (content - 22) / 2, bw, 22, 12, false);
            c.rect(SX, y + h - 1, SW, 1, Paint.LINE);
            y += h;
        }
        if (pages > 1) {
            c.rect(SX, footY, SW, 1, Paint.LINE);
            String label = (page + 1) + " / " + pages;
            Paint.right(c, label, x + w, capitals(footY + 1 + 6, 14, 12), 12, Paint.LIGHT);
            ui.hit(x + w - 50, footY, 50, SY + SH - footY, () -> {
                page = (page + 1) % pages;
                ask();
            });
        }
    }

    /** With no App Store mirror in reach, the store says so and shows nothing else; true then. */
    private static boolean noMirror(Canvas c, AppsView apps, float y) {
        if (apps.mirror() > 0) return false;
        Paint.centered(c, t("becom.ui.store.no_mirror"), SX + SW / 2, y + 30, 13, Paint.GREY);
        return true;
    }

    /** Install, Installed or Update for a store app; {@code withSize} adds its size, as its page has it. */
    private static String installLabel(AppsView.StoreApp app, boolean withSize) {
        if (app.installed()
            .equals(app.version())) return t("becom.ui.store.installed");
        String label = t(
            app.installed()
                .isEmpty() ? "becom.ui.store.install" : "becom.ui.store.update");
        return withSize ? t("becom.ui.store.with_size", label, kb(app.size())) : label;
    }

    /**
     * A store app's Install, Installed or Update: on its page {@code wide} and bevelled with its size, on the store
     * small and flat in a 1-pixel edge, each as its board's. Installed takes no click.
     */
    private void paintInstallButton(Canvas c, AppsView apps, AppsView.StoreApp app, float x, float y, float w, float h,
        float size, boolean wide) {
        if (app.id()
            .equals(apps.downloading())) {
            // Coming down: the button fills as the bytes arrive, with how much is here.
            flat(c, x, y, w, h, "", size, Paint.LINE, EDGE, Paint.GREY);
            c.rect(x + 1, y + 1, (w - 2) * Math.max(0, Math.min(1, apps.progress())), h - 2, Paint.Style.GO.face);
            String done = Math.round(apps.progress() * 100) + "%";
            Paint.centered(c, done, x + w / 2, y + Math.round((h - c.lineHeight(size)) / 2), size, Paint.WHITE);
            ui.hit(x, y, w, h, null);
            return;
        }
        String label = installLabel(app, wide);
        if (app.installed()
            .equals(app.version())) {
            flat(c, x, y, w, h, label, size, Paint.LINE, EDGE, Paint.GREY);
            ui.hit(x, y, w, h, null);
            return;
        }
        if (!apps.downloading()
            .isEmpty()) {
            // One download at a time: the others wait for it.
            flat(c, x, y, w, h, label, size, Paint.LINE, EDGE, Paint.GREY);
            ui.hit(x, y, w, h, null);
            return;
        }
        boolean update = !app.installed()
            .isEmpty();
        Paint.Style style = update ? Paint.Style.WARN : Paint.Style.GO;
        if (wide) button(c, x, y, w, h, style, label, size);
        else {
            int face = ui.hover(x, y, w, h) ? Paint.brighter(style.face) : style.face;
            // The store board's Update has a lighter edge than the bevel's.
            flat(c, x, y, w, h, label, size, face, update ? 0xffc060 : style.light, Paint.WHITE);
        }
        String id = app.id();
        ui.hit(x, y, w, h, () -> ui.sender.send(PhoneActions.STORE_INSTALL, buf -> write(buf, id)));
    }

    void paintStoreApp(Canvas c, PhoneView v) {
        ui.paintStatus(c, v);
        float y = ui.paintBar(c, t("becom.ui.app.store"), STORE, STORE_SHADOW);
        if (noMirror(c, v.apps(), y)) return;
        AppsView.StoreApp app = null;
        for (AppsView.StoreApp a : v.apps()
            .store())
            if (a.id()
                .equals(ui.app)) app = a;
        if (app == null) {
            Paint.centered(c, t("becom.ui.app.gone"), SX + SW / 2, y + 30, 13, Paint.GREY);
            return;
        }
        // The board's head inside 12 of padding: the icon, and 10 beside it the name's 22-pixel line and the grey 14,
        // 2 apart, in the icon's middle.
        float x = SX + 12;
        float w = SW - 24;
        y += 12;
        icon(c, app.icon(), app.accent(), x, y, 48, 28);
        float ty = y + (48 - lineOf(18) - 2 - lineOf(12)) / 2;
        c.text(Paint.fit(c, app.name(), 18, w - 58), x + 58, capitals(ty, lineOf(18), 18), 18, Paint.WHITE);
        c.text(
            Paint.fit(c, t("becom.ui.store.by_version", app.author(), app.version(), app.installs()), 12, w - 58),
            x + 58,
            capitals(ty + lineOf(18) + 2, lineOf(12), 12),
            12,
            Paint.GREY);
        y += 48 + 10;
        paintInstallButton(c, v.apps(), app, x, y, w, 32, 15, true);
        // The description 10 under the button in 17-pixel lines; then what it can and can't, and about it.
        y += 32 + 10;
        for (String line : Paint.wrap(c, app.about(), 13, w)) {
            c.text(line, x, capitals(y, 17, 13), 13, Paint.LIGHT);
            y += 17;
        }
        y = heading(c, x, y, t("becom.ui.app_settings.it_can"));
        for (AppManifest.Use use : AppManifest.Use.VALUES) {
            if ((app.uses() & use.bit()) != 0) y = useRow(c, x, y, w, use, t("becom.ui.use.long." + use.word()), true);
        }
        // What it can't, in the board's order.
        List<String> cannot = new ArrayList<>();
        for (AppManifest.Use use : List.of(AppManifest.Use.CAMERA, AppManifest.Use.CALL)) {
            if ((app.uses() & use.bit()) == 0) cannot.add(t("becom.ui.use.no." + use.word()));
        }
        if (!cannot.isEmpty()) {
            String none = String.join(", ", cannot);
            y = useRow(c, x, y, w, null, Character.toUpperCase(none.charAt(0)) + none.substring(1), false);
        }
        y = heading(c, x, y, t("becom.ui.store.about"));
        String about = t(app.days() <= 0 ? "becom.ui.store.updated_today" : "becom.ui.store.updated", app.days()) + " "
            + t(app.team() ? "becom.ui.store.team_only" : "becom.ui.store.public");
        for (String line : Paint.wrap(c, about, 12, w)) {
            c.text(line, x, capitals(y, 16, 12), 12, Paint.GREY);
            y += 16;
        }
        // The foot: a line over 6 of padding round Report, a 16-pixel button with 6 of its own each side.
        float footY = SY + SH - 6 - 16 - 6 - 1;
        c.rect(SX, footY, SW, 1, Paint.LINE);
        String report = t(app.reported() ? "becom.ui.store.reported" : "becom.ui.store.report");
        float rw = c.width(report, 12);
        float right = SX + SW - 12 - 6;
        Paint.right(c, report, right, capitals(footY + 1 + 6, 16, 12), 12, app.reported() ? Paint.DARK : 0xff6b55);
        if (!app.reported()) {
            String id = app.id();
            ui.hit(
                right - rw - 6,
                footY + 1 + 6,
                rw + 12,
                16,
                () -> ui.sender.send(PhoneActions.STORE_REPORT, buf -> write(buf, id)));
        }
    }

    /** A small heading of the store page: blue-grey capitals in an 11-pixel line 8 under what's above. */
    private static float heading(Canvas c, float x, float y, String title) {
        c.text(title.toUpperCase(Locale.ROOT), x, capitals(y + 8, lineOf(11), 11), 11, Paint.SECTION);
        return y + 8 + lineOf(11);
    }

    /**
     * A line of what an app can do, as the store page board's: its mark on a dark square, or none, beside the words'
     * 16-pixel lines, 3 above and under and grey for what it can't.
     */
    private static float useRow(Canvas c, float x, float y, float w, AppManifest.Use use, String text, boolean can) {
        List<String> lines = Paint.wrap(c, text, 13, w - 24);
        float content = Math.max(16, lines.size() * lineOf(13));
        float iy = y + 3 + (content - 16) / 2;
        c.rect(x, iy, 16, 16, Paint.LINE);
        if (use != null) {
            switch (use) {
                case NET -> Paint.glyph(c, x + 3, iy + 3, 2, Glyphs.NETWORK, MINT, Paint.LINE);
                case NOTIFY -> Paint.glyph(c, x + 3, iy + 3, 2, Glyphs.NOTIFY, Paint.ORANGE, Paint.LINE);
                case CAMERA -> c.rect(x + 5, iy + 5, 6, 6, Paint.CYAN);
                case CALL -> c.rect(x + 5, iy + 5, 6, 6, 0x8ab4ff);
                default -> c.rect(x + 5, iy + 5, 6, 6, Paint.LIGHT);
            }
        }
        float ty = y + 3 + (content - lines.size() * lineOf(13)) / 2;
        for (String line : lines) {
            c.text(line, x + 24, capitals(ty, lineOf(13), 13), 13, can ? Paint.TEXT : Paint.DARK);
            ty += lineOf(13);
        }
        return y + 3 + content + 3;
    }

    // endregion

    // region What a computer offers

    /** Install an app a computer offers, or publish one under this player's name: the Install and Publish boards. */
    void paintOffer(Canvas c, PhoneView v) {
        ui.paintStatus(c, v);
        AppsView.Offer offer = v.apps()
            .offer();
        boolean publish = offer.publish();
        float cx = SX + SW / 2;
        float x = SX + 12;
        float w = SW - 24;
        // The head: a column under 26 of padding, its parts 8 apart and the icon 6 more each side; the Publish board's
        // 24 and 6. Then the table, 14 under it on the Install board and 12 on Publish.
        float gap = publish ? 6 : 8;
        float y = SY + STATUS + (publish ? 24 : 26);
        Paint.centered(
            c,
            t(publish ? "becom.ui.offer.publish_title" : "becom.ui.offer.install_title"),
            cx,
            capitals(y, lineOf(14), 14),
            14,
            Paint.GREY);
        y += lineOf(14) + gap + 6;
        icon(c, offer.icon(), offer.accent(), cx - 28, y, 56, 32);
        y += 56 + 6 + gap;
        String name = publish ? offer.name() + " " + offer.version() : offer.name();
        Paint.centered(c, Paint.fit(c, name, 22, w), cx, capitals(y, lineOf(22), 22), 22, Paint.WHITE);
        y += lineOf(22) + gap;
        String byLine = publish ? t("becom.ui.offer.under_name", offer.author())
            : t("becom.ui.offer.version_by", offer.version(), offer.author());
        Paint.centered(c, byLine, cx, capitals(y, lineOf(13), 13), 13, Paint.GREY);
        y += lineOf(13) + (publish ? 12 : 14);
        y = tableRow(c, x, y, w, t("becom.ui.offer.from"), offer.where());
        if (publish) {
            y = tableRow(c, x, y, w, t("becom.ui.offer.uses"), usesWords(offer.uses()));
            // Who can find it, 12 under the table and 4 over the choice: two halves 26 tall, each in its own edge.
            y += 12;
            c.text(t("becom.ui.offer.who"), x, capitals(y, lineOf(12), 12), 12, Paint.GREY);
            y += lineOf(12) + 4;
            float half = w / 2;
            for (int i = 0; i < 2; i++) {
                boolean chosen = (i == 1) == team;
                float sx = x + i * half;
                c.rect(sx, y, half, 26, EDGE);
                c.rect(
                    sx + 1,
                    y + 1,
                    half - 2,
                    24,
                    chosen ? Paint.TEXT : ui.hover(sx, y, half, 26) ? 0x2a323c : Paint.LINE);
                Paint.centered(
                    c,
                    t(i == 0 ? "becom.ui.offer.everyone" : "becom.ui.offer.my_team"),
                    sx + half / 2,
                    capitals(y + 1, 24, 13),
                    13,
                    chosen ? Paint.SCREEN : Paint.LIGHT);
                boolean toTeam = i == 1;
                ui.hit(sx, y, half, 26, () -> team = toTeam);
            }
        } else {
            y = tableRow(c, x, y, w, t("becom.ui.offer.size"), kb(offer.size()));
            // The warning 12 under the table: amber 15-pixel lines in a dark amber box, 6 and 8 of padding, the
            // square 3 down beside them.
            y += 12;
            List<String> lines = Paint.wrap(c, t("becom.ui.offer.not_store"), 12, w - 34);
            float h = 1 + 6 + lines.size() * 15 + 6 + 1;
            c.rect(x, y, w, h, 0x6a5a1a);
            c.rect(x + 1, y + 1, w - 2, h - 2, 0x2a2210);
            c.rect(x + 1 + 8, y + 1 + 6 + 3, 8, 8, Paint.ORANGE);
            float ly = y + 1 + 6;
            for (String line : lines) {
                c.text(line, x + 1 + 8 + 8 + 8, capitals(ly, 15, 12), 12, 0xffcc66);
                ly += 15;
            }
        }
        float by = SY + SH - 14 - 34;
        float bw = (w - 8) / 2;
        button(c, x, by, bw, 34, Paint.Style.PLAIN, t("becom.ui.offer.cancel"), 15);
        long id = offer.id();
        ui.hit(x, by, bw, 34, () -> answer(id, false, false));
        float ax = x + bw + 8;
        button(
            c,
            ax,
            by,
            bw,
            34,
            publish ? Paint.Style.STORE : Paint.Style.GO,
            t(publish ? "becom.ui.offer.publish" : "becom.ui.offer.install"),
            15);
        ui.hit(ax, by, bw, 34, () -> answer(id, true, publish && team));
    }

    private void answer(long id, boolean accept, boolean toTeam) {
        ui.sender.send(PhoneActions.OFFER_ANSWER, buf -> {
            buf.writeLong(id);
            buf.writeBoolean(accept);
            buf.writeBoolean(toTeam);
        });
        team = false;
    }

    /** A row of the offer's table: the grey key and the value on the right in a 16-pixel line, 3 round it, a line. */
    private static float tableRow(Canvas c, float x, float y, float w, String key, String value) {
        float ty = capitals(y + 3, lineOf(13), 13);
        c.text(key, x, ty, 13, Paint.GREY);
        Paint.right(c, Paint.fit(c, value, 13, w - c.width(key, 13) - 8), x + w, ty, 13, Paint.TEXT);
        float foot = y + 3 + lineOf(13) + 3;
        c.rect(x, foot, w, 1, Paint.LINE);
        return foot + 1;
    }

    /** "network, notifications", or "nothing", for an app's AppManifest.Use bits. */
    static String usesWords(int bits) {
        List<String> words = new ArrayList<>();
        for (AppManifest.Use use : AppManifest.Use.VALUES)
            if ((bits & use.bit()) != 0) words.add(t("becom.ui.use.word." + use.word()));
        return words.isEmpty() ? t("becom.ui.use.word.nothing") : String.join(", ", words);
    }

    // endregion

    // region What the pages share

    /** A button of the boards: a face in a 2-pixel bevel, its label in the middle with a black shadow. */
    private void button(Canvas c, float x, float y, float w, float h, Paint.Style style, String label, float size) {
        int face = ui.hover(x, y, w, h) ? Paint.brighter(style.face) : style.face;
        Paint.box(c, x, y, w, h, 2, face, style.light, style.dark);
        float tx = x + (w - c.width(label, size)) / 2;
        Paint.shadowed(c, label, tx, capitals(y + 2, h - 4, size), size, Paint.WHITE, Paint.BLACK);
    }

    /** A flat button of the boards: a face in a 1-pixel edge, its label in the middle without a shadow. */
    private static void flat(Canvas c, float x, float y, float w, float h, String label, float size, int face, int edge,
        int ink) {
        c.rect(x, y, w, h, edge);
        c.rect(x + 1, y + 1, w - 2, h - 2, face);
        Paint.centered(c, label, x + w / 2, capitals(y + 1, h - 2, size), size, ink);
    }

    /** The boards' line at a font size: their font's normal one, a fifth more than the size, in whole pixels. */
    private static float lineOf(float size) {
        return Math.round(size * 1.2f);
    }

    /**
     * Where text at {@code size} starts in the middle of a line {@code box} tall at {@code top}: where the boards' font
     * has its capitals in its own line, since Minecraft's starts at the top of its capitals.
     */
    private static float capitals(float top, float box, float size) {
        int down = switch (Math.round(size)) {
            case 11, 12 -> 3;
            case 13, 15 -> 4;
            case 18, 22 -> 6;
            default -> 5;
        };
        return top + (box - lineOf(size)) / 2 + down;
    }

    // endregion
}
