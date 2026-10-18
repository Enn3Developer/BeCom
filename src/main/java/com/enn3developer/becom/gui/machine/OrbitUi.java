package com.enn3developer.becom.gui.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.util.StatCollector;

import com.enn3developer.becom.gui.OrbitView;
import com.enn3developer.becom.gui.paint.Canvas;
import com.enn3developer.becom.gui.paint.CanvasScreen;
import com.enn3developer.becom.gui.paint.Grey;
import com.enn3developer.becom.gui.paint.Paint;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.space.Forecast;

/**
 * The Satellite Launch Pad's Orbit page, from the Orbit board: its Launches and Orbit tabs, a table of the skies with
 * their satellites, level, calls and who's using them, then the pad's own sky over the next days, if nothing more goes
 * up, and when its level drops.
 */
public final class OrbitUi extends CanvasScreen {

    private static final float WIDTH = 720;
    private static final float HEIGHT = 460;
    private static final float X0 = 16;
    private static final float INNER = WIDTH - 2 * X0;
    private static final float ROW = 31;
    private static final int ROWS = 3;
    private static final float[] COLUMNS = { 0, 118, 180, 268, 396 };
    private static final int LINE = 0x55ffff;
    private static final int RULE = 0x9a9a9a;
    private static final int NONE = 0xa02818;

    private final Supplier<OrbitView> view;
    private final Runnable launches;
    private int page;

    /** @param launches goes back to the Launches page */
    public OrbitUi(Supplier<OrbitView> view, Runnable launches) {
        this.view = view;
        this.launches = launches;
    }

    public static float scale(int guiHeight) {
        return guiHeight <= 0 ? 0.75f : Math.min(0.75f, (guiHeight - 8) / HEIGHT);
    }

    @Override
    public float width() {
        return WIDTH;
    }

    @Override
    public float height() {
        return HEIGHT;
    }

    private static String t(String key, Object... args) {
        return args.length == 0 ? StatCollector.translateToLocal(key)
            : StatCollector.translateToLocalFormatted(key, args);
    }

    /** A level's word on the grey panel, in the Orbit board's colours. */
    private static int levelColour(SignalLevel level) {
        return switch (level) {
            case NONE -> NONE;
            case MEDIUM -> 0x8a7a00;
            default -> Grey.text(level);
        };
    }

    private static String levelName(SignalLevel level) {
        return t(
            "becom.level." + level.name()
                .toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    protected void paint(Canvas c) {
        OrbitView v = view.get();
        Grey.panel(c, WIDTH, HEIGHT);
        float y = 14;
        c.text(t("gt.blockmachines.becom.launch_pad.name"), X0, y + (26 - c.lineHeight(15)) / 2, 15, Grey.TITLE);
        float tab = 110;
        Grey.button(this, c, X0 + INNER - 2 * tab, y, tab, 26, t("becom.ui.orbit.launches"), 13, launches);
        Grey.pressed(c, X0 + INNER - tab, y, tab, 26, t("becom.ui.orbit.orbit"), 13);
        y += 26 + 18;
        y = paintTable(c, v, y);
        paintForecast(c, v, X0, 176, INNER, 160);
        String note = t("becom.ui.orbit.note", v.satelliteCalls(), v.satelliteCalls(), v.lifetimeDays());
        List<String> lines = Paint.wrap(c, note, 11, INNER);
        float ny = HEIGHT - 16 - lines.size() * 16;
        for (String line : lines) {
            c.text(line, X0, ny, 11, Grey.NOTE);
            ny += 16;
        }
    }

    private float paintTable(Canvas c, OrbitView v, float y) {
        String[] heads = { t("becom.ui.orbit.over"), t("becom.ui.orbit.up"), t("becom.ui.orbit.level"),
            t("becom.ui.orbit.calls"), t("becom.ui.orbit.who") };
        for (int i = 0; i < heads.length; i++) c.text(heads[i], X0 + COLUMNS[i], y, 12, Grey.NOTE);
        int pages = Math.max(
            1,
            (v.skies()
                .size() + ROWS
                - 1) / ROWS);
        page = Math.min(page, pages - 1);
        if (pages > 1) Grey.pager(this, c, X0 + INNER, y - 6, page, pages, () -> page--, () -> page++);
        y += c.lineHeight(12) + 4;
        c.rect(X0, y, INNER, 2, RULE);
        y += 2;
        int from = page * ROWS;
        for (int i = from; i < Math.min(
            v.skies()
                .size(),
            from + ROWS); i++) {
            paintSky(
                c,
                v.skies()
                    .get(i),
                y);
            y += ROW;
            // A rule between rows, none under the last.
            if (i < Math.min(
                v.skies()
                    .size(),
                from + ROWS) - 1) c.rect(X0, y - 1, INNER, 1, 0xb0b0b0);
        }
        return y;
    }

    private void paintSky(Canvas c, OrbitView.Sky sky, float y) {
        float ty = y + (ROW - c.lineHeight(13)) / 2;
        c.text(Paint.fit(c, sky.name(), 13, COLUMNS[1] - 8), X0, ty, 13, Grey.TEXT);
        c.text(String.valueOf(sky.up()), X0 + COLUMNS[1], ty, 13, Grey.TEXT);
        c.text(levelName(sky.level()), X0 + COLUMNS[2], ty, 13, levelColour(sky.level()));
        float cx = X0 + COLUMNS[3];
        if (sky.slots() > 0) {
            float bw = 120;
            Paint.box(c, cx, y + 4, bw, 10, 1, 0x2a3038, 0x6a6a6a, 0x6a6a6a);
            c.rect(cx + 1, y + 5, (bw - 2) * Math.min(1, sky.calls() / (float) sky.slots()), 8, 0x40b040);
            c.text(t("becom.ui.orbit.calls_of", sky.calls(), sky.slots()), cx, y + 16, 11, Grey.TEXT);
        } else if (sky.up() > 0) {
            c.text(t("becom.ui.orbit.needs", sky.needs()), cx, ty, 12, 0x8a8a8a);
        }
        // A sky whose receivers uplink elsewhere says so dimmer than one in use here, as on the board.
        c.text(
            Paint.fit(c, who(sky), 13, INNER - COLUMNS[4]),
            X0 + COLUMNS[4],
            ty,
            13,
            sky.uplinksTo()
                .isEmpty() ? Grey.TEXT : Grey.FAINT);
    }

    /** Who's using a sky, as the board words it: "2 dishes, 1 Moon uplink, 9 under open sky". */
    static String who(OrbitView.Sky sky) {
        if (!sky.uplinksTo()
            .isEmpty()) return t("becom.ui.orbit.uplinks_to", sky.uplinksTo());
        List<String> parts = new ArrayList<>();
        if (sky.dishes() > 0) {
            parts.add(sky.dishes() == 1 ? t("becom.ui.orbit.dish") : t("becom.ui.orbit.dishes", sky.dishes()));
        }
        for (OrbitView.Uplink uplink : sky.uplinks()) {
            parts.add(
                uplink.receivers() == 1 ? t("becom.ui.orbit.uplink", uplink.name())
                    : t("becom.ui.orbit.uplinks", uplink.receivers(), uplink.name()));
        }
        if (sky.underSky() > 0) parts.add(t("becom.ui.orbit.under_sky", sky.underSky()));
        return parts.isEmpty() ? t("becom.ui.orbit.nobody") : String.join(", ", parts);
    }

    /** The pad's sky over the next days: a step down each time satellites come down, and when its level drops. */
    private void paintForecast(Canvas c, OrbitView v, float x, float y, float w, float h) {
        Forecast f = v.forecast();
        Grey.display(c, x, y, w, h);
        c.text(t("becom.ui.orbit.next_days", v.here(), Forecast.DAYS), x + 12, y + 12, 12, Paint.GREY);
        Paint.right(c, t("becom.ui.orbit.if_nothing"), x + w - 12, y + 12, 12, Paint.GREY);
        float px = x + 12;
        float pw = w - 24;
        float py = y + 34;
        float ph = 70;
        c.rect(px, py, pw, ph, 0x161b21);
        int now = v.skies()
            .isEmpty() ? 0
                : v.skies()
                    .get(0)
                    .up();
        int last = now - f.fallDays()
            .size();
        int low = Math.max(0, Math.min(last, f.threshold() > 0 ? f.threshold() : last) - 2);
        int high = Math.max(now, low + 1);
        float perDay = pw / Forecast.DAYS;
        java.util.function.IntFunction<Float> yOf = n -> py + 6 + (ph - 12) * (high - n) / (float) (high - low);
        if (f.threshold() > 0) {
            float ly = yOf.apply(f.threshold()) + (yOf.apply(f.threshold() - 1) - yOf.apply(f.threshold())) / 2;
            // Dashed in the label's yellow, at 60% over the chart.
            for (float dx = 0; dx < pw; dx += 6) c.rect(px + dx, ly, Math.min(3, pw - dx), 1, 0xa2a440);
            Paint.right(
                c,
                t("becom.ui.orbit.below", f.threshold(), levelName(f.below())),
                px + pw - 4,
                ly - c.lineHeight(11) - 3,
                11,
                Paint.YELLOW);
        }
        if (now > 0) {
            // The step line: flat until a day satellites come down, then down by as many.
            int count = now;
            float lx = px;
            float ly = yOf.apply(count);
            List<Integer> days = f.fallDays();
            for (int i = 0; i < days.size();) {
                int day = days.get(i);
                int same = 0;
                while (i < days.size() && days.get(i) == day) {
                    same++;
                    i++;
                }
                float sx = px + day * perDay;
                c.rect(lx, ly - 1, Math.max(2, sx - lx), 2, LINE);
                float ny = yOf.apply(count - same);
                c.rect(sx - 1, Math.min(ly, ny) - 1, 2, Math.abs(ny - ly) + 2, LINE);
                if (day == f.dropDay()) c.rect(sx - 3, (ly + ny) / 2 - 5, 6, 10, 0xff4444);
                count -= same;
                lx = sx;
                ly = ny;
            }
            c.rect(lx, ly - 1, px + pw - lx, 2, LINE);
        }
        float ay = py + ph + 6;
        String[] marks = { t("becom.ui.orbit.now"), "15", "30", "45", t("becom.ui.orbit.days", Forecast.DAYS) };
        for (int i = 0; i < marks.length; i++) {
            float mx = px + pw * i / (marks.length - 1);
            if (i == 0) c.text(marks[i], mx, ay, 11, Paint.DARK);
            else if (i == marks.length - 1) Paint.right(c, marks[i], mx, ay, 11, Paint.DARK);
            else Paint.centered(c, marks[i], mx, ay, 11, Paint.DARK);
        }
        String verdict;
        int colour;
        if (now == 0) {
            verdict = t(
                "becom.ui.orbit.empty",
                v.here(),
                v.skies()
                    .isEmpty() ? 0
                        : v.skies()
                            .get(0)
                            .needs());
            colour = Paint.ORANGE;
        } else if (f.dropDay() < 0) {
            verdict = f.threshold() == 0 ? t("becom.ui.orbit.too_few", now)
                : t("becom.ui.orbit.holds", levelName(levelOf(v)), Forecast.DAYS);
            colour = f.threshold() == 0 ? Paint.ORANGE : Paint.GREEN;
        } else {
            verdict = f.together() == 1
                ? t("becom.ui.orbit.drops_one", levelName(f.below()), f.dropDay(), f.callsThen(), f.callsNow())
                : t(
                    "becom.ui.orbit.drops",
                    levelName(f.below()),
                    f.dropDay(),
                    f.together(),
                    f.callsThen(),
                    f.callsNow());
            colour = 0xff8a8a;
        }
        float vy = ay + c.lineHeight(11) + 10;
        for (String line : Paint.wrap(c, verdict, 13, w - 24)) {
            c.text(line, x + 12, vy, 13, colour);
            vy += 17;
        }
    }

    private static SignalLevel levelOf(OrbitView v) {
        return v.skies()
            .isEmpty() ? SignalLevel.NONE
                : v.skies()
                    .get(0)
                    .level();
    }
}
