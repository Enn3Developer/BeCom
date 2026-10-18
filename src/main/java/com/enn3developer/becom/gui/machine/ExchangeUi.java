package com.enn3developer.becom.gui.machine;

import static com.enn3developer.becom.gui.machine.AntennaUi.t;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.gui.ExchangeView;
import com.enn3developer.becom.gui.paint.Canvas;
import com.enn3developer.becom.gui.paint.CanvasScreen;
import com.enn3developer.becom.gui.paint.Grey;
import com.enn3developer.becom.gui.paint.Paint;
import com.enn3developer.becom.logic.net.CableTier;

/**
 * The Exchange's screen, from the Router board: its name, tier and whether it works; its calls, data and
 * reach; its racks as the hall holds them, a column a slice, lit while calls use them, with a free slot for each it
 * could still take; and a row for each Line Hatch, with the bus at its front and what it carries. Or, while it isn't
 * formed, why.
 */
public final class ExchangeUi extends CanvasScreen {

    private static final float WIDTH = 600;
    private static final float HEIGHT = 520;
    private static final float X0 = 16;
    private static final float INNER = WIDTH - 2 * X0;
    private static final float ROW = 26;
    private static final float CELL = 17;
    private static final float WALL = 64;
    private static final int SLICE_RACKS = 4;

    private final Supplier<ExchangeView> view;
    private int page;

    public ExchangeUi(Supplier<ExchangeView> view) {
        this.view = view;
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

    @Override
    protected void paint(Canvas c) {
        ExchangeView v = view.get();
        Grey.panel(c, WIDTH, HEIGHT);
        float y = 16;
        String title = t("becom.ui.node.exchange");
        c.text(title, X0, y + (20 - c.lineHeight(16)) / 2, 16, Grey.TITLE);
        float x = X0 + c.width(title, 16) + 10;
        if (v.tier() > 0) x += Grey.chip(
            c,
            x,
            y + 1,
            CableTier.ofVoltage(v.tier())
                .voltage(),
            RouterUi.voltageColour(
                CableTier.ofVoltage(v.tier())
                    .ordinal()))
            + 10;
        String state = t(
            !v.formed() ? "becom.ui.machine.unformed"
                : v.online() ? "becom.ui.radio.online" : "becom.ui.radio.offline");
        Grey.chip(c, x, y + 1, state, v.online() ? Grey.GOOD : Grey.BAD);
        y += 20 + 10;
        paintCells(c, v, y);
        y += 66 + 14;
        if (!v.formed()) {
            paintWhy(c, v, y);
            return;
        }
        // Formed, but its energy hatches can't run it.
        if (!v.why()
            .isEmpty()) y = paintWhy(c, v, y) + 8;
        c.text(t("becom.ui.exchange.racks").toUpperCase(Locale.ROOT), X0, y, 11, Grey.NOTE);
        y += c.lineHeight(11) + 4;
        y = paintRacks(c, v, y) + 14;
        paintLines(c, v, y);
    }

    private void paintCells(Canvas c, ExchangeView v, float y) {
        float w = (INNER - 2 * 8) / 3;
        float h = 66;
        // Calls, with a bar of how full its racks are.
        cell(
            c,
            X0,
            y,
            w,
            h,
            t("becom.ui.exchange.calls"),
            String.valueOf(v.calls()),
            t("becom.ui.exchange.of", v.slots()),
            null);
        float bw = w - 24;
        c.rect(X0 + 12, y + h - 16, bw, 5, 0x2a3038);
        float f = v.slots() <= 0 ? 0 : Math.min(1, v.calls() / (float) v.slots());
        c.rect(X0 + 12, y + h - 16, bw * f, 5, f >= 1 ? Paint.ORANGE : Paint.GREEN);
        cell(
            c,
            X0 + w + 8,
            y,
            w,
            h,
            t("becom.ui.exchange.data"),
            String.valueOf(v.data()),
            "KB/s",
            t(v.routes() == 1 ? "becom.ui.exchange.route" : "becom.ui.exchange.routes", v.routes()));
        cell(
            c,
            X0 + 2 * (w + 8),
            y,
            w,
            h,
            t("becom.ui.exchange.reaches"),
            String.valueOf(v.reaches()),
            t("becom.ui.exchange.numbers"),
            t(v.exchanges() == 0 ? "becom.ui.exchange.through_one" : "becom.ui.exchange.through", v.exchanges() + 1));
    }

    /** A display cell: a label, a big number with its unit after it, and a line under. */
    private static void cell(Canvas c, float x, float y, float w, float h, String label, String value, String unit,
        String sub) {
        Grey.display(c, x, y, w, h);
        c.text(label, x + 12, y + 10, 12, Paint.GREY);
        float vy = y + 10 + c.lineHeight(12) + 4;
        c.text(value, x + 12, vy, 20, Paint.WHITE);
        c.text(unit, x + 12 + c.width(value, 20) + 6, vy + c.lineHeight(20) - c.lineHeight(12) - 1, 12, Paint.GREY);
        if (sub != null) c.text(Paint.fit(c, sub, 11, w - 24), x + 12, y + h - 18, 11, Paint.GREY);
    }

    /** Why it isn't formed, or can't run, under the cells; returns where it ends. */
    private static float paintWhy(Canvas c, ExchangeView v, float y) {
        List<String> why = v.why();
        String reason = why.isEmpty() ? t("becom.exchange.why.shape")
            : t(
                why.get(0),
                why.subList(1, why.size())
                    .toArray());
        for (String line : Paint.wrap(c, reason, 14, INNER)) {
            c.text(line, X0, y, 14, Grey.BAD);
            y += 18;
        }
        return y;
    }

    /**
     * Its racks as the hall holds them, seen from the side: a column a slice from the front, the left wall's top and
     * bottom over the right wall's. Racks fill from the front, so the first ones the calls now need are lit.
     */
    private static float paintRacks(Canvas c, ExchangeView v, float y) {
        int slices = v.racks()
            .size() / SLICE_RACKS;
        float gap = 4;
        float w = slices <= 0 ? 0 : Math.min(60, (INNER - WALL - (slices - 1) * gap) / slices);
        for (int s = 0; s < slices; s++)
            Paint.centered(c, String.valueOf(s + 1), X0 + WALL + s * (w + gap) + w / 2, y, 10, Grey.NOTE);
        y += c.lineHeight(10) + 2;
        float[] rows = { y, y + CELL + 2, y + 2 * CELL + 10, y + 3 * CELL + 12 };
        c.text(t("becom.ui.exchange.left"), X0, rows[0] + (2 * CELL + 2 - c.lineHeight(11)) / 2, 11, Grey.NOTE);
        c.text(t("becom.ui.exchange.right"), X0, rows[2] + (2 * CELL + 2 - c.lineHeight(11)) / 2, 11, Grey.NOTE);
        int now = v.calls();
        for (int i = 0; i < slices * SLICE_RACKS; i++) {
            int tier = v.racks()
                .get(i);
            float x = X0 + WALL + (i / SLICE_RACKS) * (w + gap);
            float ry = rows[i % SLICE_RACKS];
            if (tier < 0) {
                free(c, x, ry, w, CELL);
                continue;
            }
            CableTier rack = CableTier.byOrdinal(tier);
            boolean lit = now > 0;
            now -= BeComConfig.get()
                .rackCalls(rack) << 2 * v.overclocks();
            rack(c, rack, lit, x, ry, w, CELL);
        }
        return rows[3] + CELL;
    }

    /** A rack's cell: an LV rack grey, MV orange, HV yellow, as on the Router board; its lamp lit while in use. */
    private static void rack(Canvas c, CableTier tier, boolean lit, float x, float y, float w, float h) {
        int border, fill, voltage;
        switch (tier) {
            case PHONE -> {
                border = 0xc0c0c0;
                fill = 0x2a2a2a;
                voltage = Paint.TEXT;
            }
            case COAXIAL -> {
                border = 0xff8a3a;
                fill = 0x3a2a1a;
                voltage = 0xffc090;
            }
            default -> {
                border = 0xf0d040;
                fill = 0x3a341a;
                voltage = 0xfff0a0;
            }
        }
        c.rect(x, y, w, h, border);
        c.rect(x + 1, y + 1, w - 2, h - 2, fill);
        c.text(tier.voltage(), x + 5, y + (h - c.lineHeight(11)) / 2, 11, voltage);
        c.rect(x + w - 9, y + (h - 5) / 2, 5, 5, lit ? Paint.GREEN : 0x1a3a1a);
    }

    /** A free slot: dashed, with a plus. */
    private static void free(Canvas c, float x, float y, float w, float h) {
        c.rect(x, y, w, h, 0xb8b8b8);
        for (float d = 0; d < w; d += 6) {
            c.rect(x + d, y, Math.min(3, w - d), 1, 0x8a8a8a);
            c.rect(x + d, y + h - 1, Math.min(3, w - d), 1, 0x8a8a8a);
        }
        for (float d = 0; d < h; d += 6) {
            c.rect(x, y + d, 1, Math.min(3, h - d), 0x8a8a8a);
            c.rect(x + w - 1, y + d, 1, Math.min(3, h - d), 0x8a8a8a);
        }
        Paint.centered(c, "+", x + w / 2, y + (h - c.lineHeight(11)) / 2, 11, 0x7a7a7a);
    }

    /** A row a Line Hatch: its tier, then the bus at its front as the Router board writes one; paged. */
    private void paintLines(Canvas c, ExchangeView v, float y) {
        int count = v.lines()
            .size();
        if (count == 0) {
            c.text(t("becom.ui.exchange.nothing"), X0, y + 4, 14, RouterUi.BAD_TEXT);
            return;
        }
        float bottom = HEIGHT - X0;
        int fit = Math.max(1, (int) ((bottom - y) / ROW));
        if (count > fit) {
            bottom -= 24 + 8;
            fit = Math.max(1, (int) ((bottom - y) / ROW));
        }
        int pages = Math.max(1, (count + fit - 1) / fit);
        page = Math.min(page, pages - 1);
        for (int i = page * fit; i < Math.min(count, (page + 1) * fit); i++) {
            ExchangeView.Line line = v.lines()
                .get(i);
            float ty = y + Math.round((ROW - 1 - c.lineHeight(14)) / 2);
            int hatch = line.hatch();
            float x = X0 + Grey.chip(
                c,
                X0,
                y + Math.round((ROW - 1 - 17) / 2),
                CableTier.byOrdinal(hatch)
                    .voltage(),
                RouterUi.voltageColour(hatch)) + 12;
            if (line.bus() == null) c.text(t("becom.ui.exchange.no_cable"), x, ty, 14, Grey.FAINT);
            else RouterUi.busLine(c, line.bus(), x, ty, X0 + INNER - x, true);
            y += ROW;
            c.rect(X0, y - 1, INNER, 1, 0xa8a8a8);
        }
        if (pages > 1) Grey.pager(this, c, X0 + INNER, bottom + 8, page, pages, () -> page--, () -> page++);
    }

    @Override
    public void scroll(int amount) {
        page = Math.max(0, page - Integer.signum(amount));
    }
}
