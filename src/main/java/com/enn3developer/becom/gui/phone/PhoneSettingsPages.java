package com.enn3developer.becom.gui.phone;

import static com.enn3developer.becom.gui.phone.PhoneUi.SH;
import static com.enn3developer.becom.gui.phone.PhoneUi.SW;
import static com.enn3developer.becom.gui.phone.PhoneUi.SX;
import static com.enn3developer.becom.gui.phone.PhoneUi.SY;
import static com.enn3developer.becom.gui.phone.PhoneUi.t;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.client.SignalTerms;
import com.enn3developer.becom.gui.AppsView;
import com.enn3developer.becom.gui.PhoneView;
import com.enn3developer.becom.gui.paint.Canvas;
import com.enn3developer.becom.gui.paint.Glyphs;
import com.enn3developer.becom.gui.paint.Paint;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.phone.BatteryUse;
import com.enn3developer.becom.logic.radio.RadioModel;
import com.enn3developer.becom.packet.AnalyzerPacket;

/** Settings and what it opens (Battery, Video window), from the Settings and Battery boards, and the Signal app. */
final class PhoneSettingsPages {

    private static final int SETTINGS = 0x5a5d62;
    private static final int SETTINGS_SHADOW = 0x2a2c2f;
    private static final String[] CORNERS = { "top_left", "top_right", "bottom_left", "bottom_right" };

    private final PhoneUi ui;

    PhoneSettingsPages(PhoneUi ui) {
        this.ui = ui;
    }

    // region Settings

    void paintSettings(Canvas c, PhoneView v) {
        ui.paintStatus(c, v);
        float y = ui.paintBar(c, t("becom.ui.app.settings"), SETTINGS, SETTINGS_SHADOW);
        boolean manage = v.mayManage();
        y = ui.section(c, y, t("becom.ui.settings.this_phone"));
        y = pillRow(c, y, t("becom.ui.settings.power"), null, v.on(), manage, on -> send(PhoneActions.POWER, on));
        y = pillRow(c, y, t("becom.ui.settings.silent"), null, v.silent(), manage, on -> send(PhoneActions.SILENT, on));
        y = pillRow(c, y, t("becom.ui.settings.dnd"), null, v.doNotDisturb(), manage, on -> send(PhoneActions.DND, on));
        y = pillRow(
            c,
            y,
            t("becom.ui.settings.unlisted"),
            t("becom.ui.settings.unlisted.sub"),
            v.hidden(),
            manage,
            on -> send(PhoneActions.HIDE, on));
        PhoneView.Battery battery = v.battery();
        y = linkRow(
            c,
            y,
            t("becom.ui.settings.battery"),
            t("becom.ui.settings.battery.sub", battery.percent(), duration(battery.standbyMinutes())),
            null,
            () -> ui.open(PhoneUi.Page.BATTERY));
        y = ui.section(c, y, t("becom.ui.settings.calls"));
        y = linkRow(
            c,
            y,
            t("becom.ui.settings.video_window"),
            t("becom.ui.settings.video_window.sub", t("becom.ui.corner." + CORNERS[corner()])),
            null,
            () -> ui.open(PhoneUi.Page.VIDEO_WINDOW));
        y = linkRow(
            c,
            y,
            t("becom.ui.settings.blocked"),
            null,
            String.valueOf(
                v.blocked()
                    .size()),
            () -> ui.open(PhoneUi.Page.BLOCKED));
        y = ui.section(c, y, t("becom.ui.settings.apps"));
        List<String> names = new ArrayList<>();
        for (AppsView.App app : v.apps()
            .installed()) names.add(app.name());
        String sub = names.isEmpty() ? t("becom.ui.settings.apps.none")
            : t("becom.ui.settings.apps.sub", names.size(), String.join(", ", names));
        linkRow(c, y, t("becom.ui.settings.installed"), sub, null, () -> ui.open(PhoneUi.Page.INSTALLED));
        // Who may change these, at the foot: the board's 10-pixel padlock and its line, 6 above the edge.
        float footY = SY + SH - 6 - 13;
        Paint.glyph(c, SX + 10, footY + 2, 10 / 6f, Glyphs.LOCK, Paint.DARK, Paint.SCREEN);
        c.text(
            Paint.fit(c, t("becom.ui.settings.owner_only", v.owner()), 11, SW - 36),
            SX + 10 + 10 + 6,
            footY + 3,
            11,
            Paint.DARK);
    }

    private void send(String action, boolean on) {
        ui.sender.send(action, buf -> buf.writeBoolean(on));
    }

    /** A settings row with an on/off pill on the right; dim and still when this player may not change it. */
    private float pillRow(Canvas c, float y, String title, String sub, boolean on, boolean usable,
        Consumer<Boolean> change) {
        return pill(c, y, ui.row(c, y, title, sub), 0, on, usable, change);
    }

    /** The same with the Battery board's padded row. */
    private float paddedPillRow(Canvas c, float y, String title, String sub, boolean on, boolean usable,
        Consumer<Boolean> change) {
        return pill(c, y, ui.paddedRow(c, y, title, sub), 2, on, usable, change);
    }

    /** The pill of a row from {@code y} to {@code next}, in the middle of what's inside its padding, over its line. */
    private float pill(Canvas c, float y, float next, float padding, boolean on, boolean usable,
        Consumer<Boolean> change) {
        float px = SX + SW - 10 - 36;
        float py = Math.round(y + padding + (next - y - 1 - 2 * padding - 18) / 2);
        Paint.pill(c, px, py, on);
        if (!usable) c.rect(px, py, 36, 18, 0x800d1014);
        if (usable) ui.hit(SX, y, SW, next - y, () -> change.accept(!on));
        return next;
    }

    /** A settings row that opens a page: a chevron on the right, and a count before it when there is one. */
    private float linkRow(Canvas c, float y, String title, String sub, String count, Runnable open) {
        float h = ui.rowHeight(c, sub);
        if (ui.hover(SX, y, SW, h - 1)) c.rect(SX, y, SW, h - 1, 0x141a20);
        float next = ui.row(c, y, title, sub);
        // The board's "›" ends 11 from the right edge, in the middle of the row over its line; the count is 14-pixel
        // text with a space before it, its capitals where a title's are.
        float chevronX = SX + SW - 11 - 6;
        chevron(c, chevronX, y + Math.round((h - 1 - 11) / 2), Paint.GREY);
        if (count != null) Paint.right(c, count, chevronX - 5, y + Math.round((h - 1 - 17) / 2) + 5, 14, Paint.GREY);
        ui.hit(SX, y, SW, next - y, open);
        return next;
    }

    /** The board's "›": 6 wide and 11 tall, a pixel a row. */
    private static void chevron(Canvas c, float x, float y, int color) {
        for (int i = 0; i < 6; i++) {
            c.rect(x + i, y + i, 1, 1, color);
            c.rect(x + i, y + 10 - i, 1, 1, color);
        }
    }

    private static int corner() {
        return Math.max(0, Math.min(3, BeCom.proxy.videoCorner()));
    }

    // endregion

    // region Battery

    /**
     * One row of what drew the charge today, 20 tall in the board's grid: its name in 78, a bar of its share 8 after,
     * the percent right-aligned in the last 34.
     */
    private static float useRow(Canvas c, float x, float y, float w, String name, int share, int colour) {
        c.text(Paint.fit(c, name, 13, 78), x, y + 6, 13, Paint.TEXT);
        float bx = x + 78 + 8;
        float bw = w - 78 - 34 - 16;
        c.rect(bx, y + 7, bw, 6, 0x1c2229);
        c.rect(bx, y + 7, bw * Math.min(100, share) / 100f, 6, colour);
        Paint.right(c, share + "%", x + w, y + 6, 13, Paint.TEXT);
        return y + 20;
    }

    private static final int[] USE_COLOURS = { Paint.GREY, Paint.GREEN, Paint.CYAN, Paint.YELLOW, PhoneUi.LUA_CHIP };

    void paintBattery(Canvas c, PhoneView v) {
        ui.paintStatus(c, v);
        float y = ui.paintBar(c, t("becom.ui.settings.battery_title"), SETTINGS, SETTINGS_SHADOW);
        PhoneView.Battery b = v.battery();
        float x = SX + 10;
        float w = SW - 20;
        // The board's head, 12 under the bar, 6 between its parts: the charge's 34-pixel line with the EU 10 after
        // it on its baseline, the bar, how long it lasts in lines of 16, whether it charges in lines of 14; each text
        // where the board's font puts its capitals in its line.
        y += 12;
        String percent = b.percent() + "%";
        c.text(percent, x, y + 7, 34, Paint.WHITE);
        c.text(
            t("becom.ui.battery.of", eu(b.charge()), eu(b.max())),
            x + c.width(percent, 34) + 10,
            y + 20,
            13,
            Paint.GREY);
        y += 34 + 6;
        c.rect(x, y, w, 10, 0x1c2229);
        c.rect(x, y, w * b.percent() / 100f, 10, b.saving() ? Paint.ORANGE : Paint.GREEN);
        y += 10 + 6;
        String lasts = t("becom.ui.battery.lasts", duration(b.standbyMinutes()), duration(b.callMinutes()));
        for (String line : Paint.wrap(c, lasts, 13, w)) {
            c.text(line, x, y + 4, 13, Paint.LIGHT);
            y += 16;
        }
        y += 6;
        // The board's 12 pixels, drawn at 11: at 12 Minecraft's wider letters take three lines where the board's
        // take two, and push everything under them down a line.
        String charging = t(b.charging() ? "becom.ui.battery.charging" : "becom.ui.battery.not_charging");
        for (String line : Paint.wrap(c, charging, 11, w)) {
            c.text(line, x, y + 3, 11, b.charging() ? Paint.GREEN : Paint.GREY);
            y += 14;
        }
        // The board's sections here keep 3 under their heading, a pixel more than Settings'.
        y = ui.section(c, y + 6, t("becom.ui.battery.used_today")) + 1;
        List<Integer> shares = b.shares();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < shares.size() && i < USE_COLOURS.length; i++) order.add(i);
        order.sort((p, q) -> Integer.compare(shares.get(q), shares.get(p)));
        for (int i : order) {
            int share = shares.get(i);
            // The apps' share shows as each app's own, by name, where the server knows them.
            if (i == BatteryUse.Use.APPS.ordinal() && !b.apps()
                .isEmpty()) {
                for (PhoneView.AppShare app : b.apps()) {
                    y = useRow(c, x, y, w, app.name(), app.share(), USE_COLOURS[i]);
                }
                continue;
            }
            String use = BatteryUse.Use.byOrdinal(i)
                .name()
                .toLowerCase(java.util.Locale.ROOT);
            y = useRow(c, x, y, w, t("becom.ui.battery.use." + use), share, USE_COLOURS[i]);
        }
        y = ui.section(c, y, t("becom.ui.battery.saving")) + 1;
        y = paddedPillRow(
            c,
            y,
            t("becom.ui.battery.saver"),
            t("becom.ui.battery.saver.sub"),
            b.saver(),
            v.mayManage(),
            on -> send(PhoneActions.SAVER, on));
        paddedPillRow(
            c,
            y,
            t("becom.ui.battery.warn"),
            t("becom.ui.battery.warn.sub", BatteryUse.WARNINGS[0], BatteryUse.WARNINGS[1]),
            b.warns(),
            v.mayManage(),
            on -> send(PhoneActions.WARN, on));
    }

    /** Minutes as "2 h 35 min", "39 min", or "for ever" for a drain of nothing. */
    static String duration(long minutes) {
        if (minutes < 0) return t("becom.ui.battery.forever");
        if (minutes < 60) return t("becom.ui.battery.minutes", minutes);
        return t("becom.ui.battery.hours", minutes / 60, minutes % 60);
    }

    /** EU the boards' way: 744k, 1.2M. */
    static String eu(long eu) {
        if (eu >= 1_000_000) return trim(eu / 1_000_000.0) + "M";
        if (eu >= 1_000) return trim(eu / 1_000.0) + "k";
        return String.valueOf(eu);
    }

    private static String trim(double value) {
        return value >= 100 ? String.valueOf(Math.round(value))
            : String.format(java.util.Locale.ROOT, "%.1f", value)
                .replaceAll("\\.0$", "");
    }

    // endregion

    // region Video window

    /** Where the video window goes during a call: a small screen with its four corners to pick from. */
    void paintVideoWindow(Canvas c, PhoneView v) {
        ui.paintStatus(c, v);
        float y = ui.paintBar(c, t("becom.ui.settings.video_window_title"), SETTINGS, SETTINGS_SHADOW);
        y += 14;
        for (String line : Paint.wrap(c, t("becom.ui.video_window.about"), 13, SW - 20)) {
            c.text(line, SX + 10, y, 13, Paint.TEXT);
            y += c.lineHeight(13) + 3;
        }
        y += 14;
        float w = 200;
        float h = 120;
        float x = SX + (SW - w) / 2;
        Paint.box(c, x, y, w, h, 2, 0x06080a, 0x2a3038, 0x2a3038);
        int chosen = corner();
        for (int i = 0; i < 4; i++) {
            float bx = i % 2 == 0 ? x + 8 : x + w - 8 - 56;
            float by = i < 2 ? y + 8 : y + h - 8 - 40;
            boolean on = i == chosen;
            boolean hovered = ui.hover(bx, by, 56, 40);
            Paint.box(
                c,
                bx,
                by,
                56,
                40,
                1,
                on ? 0x2f5f9f : hovered ? 0x2a323c : 0x1c2229,
                on ? 0x8ab4ff : 0x3a434e,
                on ? 0x1a3050 : 0x0a0c0f);
            int corner = i;
            ui.hit(bx, by, 56, 40, () -> BeCom.proxy.setVideoCorner(corner));
        }
        y += h + 10;
        Paint.centered(c, t("becom.ui.corner." + CORNERS[chosen]), SX + SW / 2, y, 14, Paint.WHITE);
    }

    // endregion

    // region Signal

    /** The Signal app: the level big in its colour, what it comes through, and this phone's number. */
    /**
     * The Signal app, the Signal Analyzer's readout for the phone's band: its level in big bars, how many dB that is
     * and
     * what serves it; then, as the analyzer spells it out sneaking, where that is and a bar a term: what the link
     * starts
     * with, each loss it suffers, the biggest first, what's left, and how far off the next level is. An ME Phone in its
     * network's range gets Max whatever the weather or walls, so it has no terms to show.
     */
    void paintSignal(Canvas c, PhoneView v) {
        ui.paintStatus(c, v);
        float y = ui.paintBar(c, t("becom.ui.app.signal"), 0x1f7f8f, 0x0f4048);
        float x = SX + 10;
        float w = SW - 20;
        y += 18;
        SignalLevel level = v.service();
        PhoneView.Phone phone = v.phone();
        boolean viaMe = !phone.meName()
            .isEmpty() && phone.meInRange();
        AnalyzerPacket.Reading r = ui.signal.get();
        boolean terms = !viaMe && r.served();
        // Big bars, three times the status bar's, the level's name in 28, and the dB 10 after it on its baseline, as
        // the Battery page sets its charge.
        int[] heights = { 12, 18, 27, 36 };
        for (int i = 0; i < 4; i++) {
            c.rect(
                x + i * 14,
                y + 36 - heights[i],
                9,
                heights[i],
                i < level.ordinal() ? Paint.level(level) : Paint.UNLIT);
        }
        String name = level == SignalLevel.NONE ? t("becom.ui.phone.no_service") : SignalTerms.levelName(level);
        float nx = x + 4 * 14 + 12;
        c.text(name, nx, y + 36 - c.lineHeight(28), 28, Paint.level(level));
        if (terms) {
            c.text(
                t(
                    "becom.analyzer.snr",
                    Math.round(
                        r.losses()
                            .snr())),
                nx + c.width(name, 28) + 10,
                y + 36 - c.lineHeight(13) - 2,
                13,
                Paint.GREY);
        }
        // What it comes through, in the level's colour as the home screen has it, in a 16-pixel line 10 under; an ME
        // Phone out of its network's range says so under it, as the home screen does.
        y += 36 + 10;
        c.text(ui.serviceLine(v), x, y + 4, 13, Paint.level(level));
        y += 16;
        if (!phone.meName()
            .isEmpty() && !phone.meInRange()) {
            c.text(t("becom.ui.phone.me_out_of_range", phone.meName()), x, y + 3, 12, Paint.DARK);
            y += 16;
        }
        if (!viaMe) {
            // Where it comes from, in grey: the satellites overhead, or the antenna, how far and which way.
            if (terms) {
                String from = r.sky() ? t("becom.analyzer.overhead", r.satellites())
                    : SignalTerms.source(r) + " · " + SignalTerms.away(r);
                c.text(SignalTerms.capitalised(from), x, y + 4, 12, Paint.GREY);
                y += 16;
            }
            y = ui.section(c, y + 4, t("becom.analyzer.phones")) + 1;
            if (!terms) {
                c.text(SignalTerms.capitalised(t("becom.analyzer.no_service")), x, y + 6, 13, Paint.RED);
            } else {
                y = paintTerms(c, r, x, y, w);
            }
        }
        // The hint at the foot, wrapped and centred in 13-pixel lines, the last 8 above the edge as the call's is.
        List<String> hint = Paint.wrap(c, t("becom.ui.signal.hint"), 11, SW - 20);
        float hy = SY + SH - 8 - hint.size() * 13;
        for (String line : hint) {
            Paint.centered(c, line, SX + SW / 2, hy + 3, 11, Paint.DARK);
            hy += 13;
        }
    }

    /**
     * A reading's terms, a row each as the Battery page's uses: what the link starts with, each loss it suffers,
     * biggest first, and what's left at its level; then that nothing's in the way, and how far off the next level is.
     */
    private static float paintTerms(Canvas c, AnalyzerPacket.Reading r, float x, float y, float w) {
        RadioModel.Losses losses = r.losses();
        double budget = losses.budget();
        y = termRow(
            c,
            x,
            y,
            w,
            null,
            t(r.sky() ? "becom.analyzer.sky_label" : "becom.analyzer.budget_label"),
            Paint.TEXT,
            1,
            Paint.DARK,
            Math.round(budget));
        boolean lost = false;
        for (RadioModel.Loss loss : SignalTerms.byBiggest(losses)) {
            long dB = Math.round(losses.of(loss));
            if (dB <= 0) continue;
            lost = true;
            int i = loss.ordinal();
            y = termRow(
                c,
                x,
                y,
                w,
                loss,
                SignalTerms.lossLabel(loss),
                SignalTerms.LOSS_TEXT[i] & 0xFFFFFF,
                losses.of(loss) / budget,
                SignalTerms.LOSS_BAR[i] & 0xFFFFFF,
                -dB);
        }
        int colour = Paint.level(r.level());
        y = termRow(
            c,
            x,
            y,
            w,
            null,
            "= " + SignalTerms.levelName(r.level()),
            colour,
            Math.max(0, losses.snr()) / budget,
            colour,
            Math.round(losses.snr()));
        if (!lost) {
            c.text(t("becom.analyzer.biggest_none"), x, y + 4, 12, Paint.GREY);
            y += 16;
        }
        String shortOf = SignalTerms.shortOf(r);
        if (shortOf != null) {
            c.text(shortOf, x, y + 4, 12, Paint.GREY);
            y += 16;
        }
        return y;
    }

    /**
     * One term, 20 tall as the Battery page's rows: a loss's icon, its name in 78 after the icon's room, a bar of its
     * share of what the link starts with, and its dB right-aligned in the last 34.
     */
    private static float termRow(Canvas c, float x, float y, float w, RadioModel.Loss icon, String name, int colour,
        double share, int barColour, long value) {
        // The analyzer's 8-pixel icon at a pixel and a half to the pixel, in the middle of the row.
        float icons = 16;
        if (icon != null) {
            String[] rows = SignalTerms.LOSS_ICON[icon.ordinal()];
            for (int row = 0; row < rows.length; row++) {
                for (int col = 0; col < rows[row].length(); col++) {
                    if (rows[row].charAt(col) == '#') c.rect(x + col * 1.5f, y + 4 + row * 1.5f, 1.5f, 1.5f, colour);
                }
            }
        }
        c.text(Paint.fit(c, name, 13, 78), x + icons, y + 6, 13, colour);
        float bx = x + icons + 78 + 8;
        float bw = w - icons - 78 - 34 - 16;
        c.rect(bx, y + 7, bw, 6, 0x1c2229);
        c.rect(bx, y + 7, (float) (bw * Math.min(1, share)), 6, barColour);
        Paint.right(c, String.valueOf(value), x + w, y + 6, 13, colour);
        return y + 20;
    }

    // endregion
}
