package com.enn3developer.becom.gui.machine;

import static com.enn3developer.becom.gui.machine.AntennaUi.t;

import java.util.Locale;
import java.util.function.Supplier;

import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.gui.ExchangeView;
import com.enn3developer.becom.gui.paint.Canvas;
import com.enn3developer.becom.gui.paint.CanvasScreen;
import com.enn3developer.becom.gui.paint.Grey;
import com.enn3developer.becom.gui.paint.Paint;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.phone.PhoneKind;

/**
 * The Exchange's screen, from the Router board: its name, whether it works and whose it is; its calls, data and
 * reach; its switching racks, with a free slot for each it could still take; and what's joined to it, with the
 * cables between and what they carry, or why they carry nothing.
 */
public final class ExchangeUi extends CanvasScreen {

    private static final float WIDTH = 600;
    private static final float HEIGHT = 520;
    private static final float X0 = 16;
    private static final float INNER = WIDTH - 2 * X0;
    private static final float ROW = 29;
    private static final int ROWS = 7;
    private static final int SLOTS = 8;
    private static final float[] COLUMNS = { 0, 178, 306 };

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
        String state = t(
            !v.formed() ? "becom.ui.machine.unformed"
                : v.online() ? "becom.ui.radio.online" : "becom.ui.radio.offline");
        Grey.chip(c, X0 + c.width(title, 16) + 10, y + 1, state, v.online() ? Grey.GOOD : Grey.BAD);
        if (!v.owner()
            .isEmpty()) {
            Paint.right(
                c,
                t("becom.ui.machine.whose", v.owner()),
                X0 + INNER,
                y + (20 - c.lineHeight(13)) / 2,
                13,
                Grey.NOTE);
        }
        y += 20 + 10;
        paintCells(c, v, y);
        y += 66 + 14;
        c.text(t("becom.ui.exchange.racks").toUpperCase(Locale.ROOT), X0, y, 11, Grey.NOTE);
        y += c.lineHeight(11) + 4;
        paintRacks(c, v, y);
        y += 46 + 16;
        paintJoined(c, v, y);
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

    /** Its racks, best first, then a dashed free slot for each it could still take. */
    private static void paintRacks(Canvas c, ExchangeView v, float y) {
        float w = (INNER - (SLOTS - 1) * 6) / SLOTS;
        float h = 46;
        for (int i = 0; i < SLOTS; i++) {
            float x = X0 + i * (w + 6);
            if (i < v.racks()
                .size()) {
                int tier = v.racks()
                    .get(i);
                // As on the Router board: an MV rack orange on brown, an LV one grey; HV likewise in its yellow.
                int border, fill, voltage;
                switch (CableTier.byOrdinal(tier)) {
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
                c.rect(x + 2, y + 2, w - 4, h - 4, fill);
                Paint.centered(
                    c,
                    CableTier.byOrdinal(tier)
                        .voltage(),
                    x + w / 2,
                    y + 8,
                    12,
                    voltage);
                Paint.centered(
                    c,
                    String.valueOf(
                        BeComConfig.get()
                            .rackCalls(CableTier.byOrdinal(tier))),
                    x + w / 2,
                    y + 8 + c.lineHeight(12) + 3,
                    12,
                    Paint.LIGHT);
            } else {
                c.rect(x, y, w, h, 0xb8b8b8);
                for (float d = 0; d < w; d += 6) {
                    c.rect(x + d, y, Math.min(3, w - d), 1, 0x8a8a8a);
                    c.rect(x + d, y + h - 1, Math.min(3, w - d), 1, 0x8a8a8a);
                }
                for (float d = 0; d < h; d += 6) {
                    c.rect(x, y + d, 1, Math.min(3, h - d), 0x8a8a8a);
                    c.rect(x + w - 1, y + d, 1, Math.min(3, h - d), 0x8a8a8a);
                }
                Paint.centered(c, "+", x + w / 2, y + (h - c.lineHeight(12)) / 2, 12, 0x7a7a7a);
            }
        }
    }

    private void paintJoined(Canvas c, ExchangeView v, float y) {
        String[] heads = { t("becom.ui.exchange.joined"), t("becom.ui.exchange.bundle"),
            t("becom.ui.exchange.carrying") };
        for (int i = 0; i < heads.length; i++) c.text(heads[i], X0 + COLUMNS[i], y, 12, Grey.NOTE);
        y += c.lineHeight(12) + 4;
        c.rect(X0, y, INNER, 2, 0x8b8b8b);
        y += 2;
        int count = v.joined()
            .size();
        int pages = Math.max(1, (count + ROWS - 1) / ROWS);
        page = Math.min(page, pages - 1);
        if (count == 0) {
            c.text(t("becom.ui.exchange.nothing"), X0, y + 8, 13, Grey.NOTE);
        }
        for (int i = page * ROWS; i < Math.min(count, (page + 1) * ROWS); i++) {
            paintRow(
                c,
                v,
                v.joined()
                    .get(i),
                y);
            y += ROW;
            c.rect(X0, y - 1, INNER, 1, 0xa8a8a8);
        }
        float bottom = HEIGHT - 16;
        String note = t("becom.ui.exchange.note");
        java.util.List<String> lines = Paint.wrap(c, note, 12, pages > 1 ? INNER - 110 : INNER);
        float ny = bottom - lines.size() * 16;
        for (String line : lines) {
            c.text(line, X0, ny, 12, Grey.NOTE);
            ny += 16;
        }
        if (pages > 1) Grey.pager(this, c, X0 + INNER, bottom - 28, page, pages, () -> page--, () -> page++);
    }

    private static void paintRow(Canvas c, ExchangeView v, ExchangeView.Joined j, float y) {
        float ty = y + (ROW - c.lineHeight(13)) / 2;
        c.text(Paint.fit(c, name(v, j), 13, COLUMNS[1] - 8), X0, ty, 13, Grey.TEXT);
        // The bundle: a swatch a cable, then "2× Fibre".
        float bx = X0 + COLUMNS[1];
        int best = -1;
        int total = 0;
        StringBuilder bundle = new StringBuilder();
        for (int tier = 0; tier < j.cables()
            .size(); tier++) {
            int n = j.cables()
                .get(tier);
            if (n <= 0) continue;
            for (int k = 0; k < n; k++) {
                c.rect(bx, ty + 2, 5, 9, RouterUi.cableColour(tier));
                bx += 7;
            }
            if (bundle.length() > 0) bundle.append(", ");
            String cable = t(
                "becom.ui.cable." + CableTier.byOrdinal(tier)
                    .name()
                    .toLowerCase(Locale.ROOT));
            bundle.append(n > 1 ? n + "× " + cable : cable);
            best = tier;
            total += n;
        }
        c.text(
            Paint.fit(c, total == 0 ? "-" : bundle.toString(), 13, X0 + COLUMNS[2] - bx - 10),
            bx + 3,
            ty,
            13,
            total == 0 ? Grey.NOTE : Grey.TEXT);
        float cx = X0 + COLUMNS[2];
        float cw = INNER - COLUMNS[2];
        if (j.rate() <= 0) {
            CableTier tier = best < 0 ? null : CableTier.byOrdinal(best);
            String why = tier == null ? t("becom.ui.exchange.bare")
                : j.length() <= BeComConfig.get()
                    .cableReach(tier)
                        ? t("becom.ui.exchange.dead")
                        : t(
                            "becom.ui.exchange.too_long",
                            j.length(),
                            t(
                                "tile.becom."
                                    + com.enn3developer.becom.block.BlockPhoneCable.nameOf(CableTier.byOrdinal(best))
                                    + ".name"),
                            BeComConfig.get()
                                .cableReach(CableTier.byOrdinal(best)));
            c.text(Paint.fit(c, why, 11, cw), cx, ty + 2, 11, Grey.BAD);
            return;
        }
        String amount = t("becom.ui.exchange.used", j.used(), j.rate());
        float aw = c.width(amount, 12);
        float bw = cw - aw - 14;
        c.rect(cx, ty + 3, bw, 8, 0x9a9a9a);
        float f = Math.min(1, j.used() / (float) j.rate());
        c.rect(cx, ty + 3, bw * f, 8, f >= 1 ? 0xc0601a : 0x3d8a3d);
        Paint.right(c, amount, X0 + INNER, ty + 1, 12, Grey.TEXT);
    }

    /**
     * What's joined, as the board names it: "Exchange 900, 64, -120", "Antenna T3, backhaul", "Router, west".
     */
    static String name(ExchangeView v, ExchangeView.Joined j) {
        if (j.node() < 0) {
            String kind = j.phoneKind() < 0 ? t("becom.ui.exchange.device")
                : t(
                    "becom.phone.kind." + PhoneKind.values()[j.phoneKind()].name()
                        .toLowerCase(Locale.ROOT));
            return j.number() < 0 ? kind : kind + " " + j.number();
        }
        NodeKind kind = NodeKind.values()[j.node()];
        String name = RouterUi.kindLabel(j.node());
        return switch (kind) {
            case EXCHANGE -> t("becom.ui.exchange.at", name, j.x(), j.y(), j.z());
            case ANTENNA_T1, ANTENNA_T2, ANTENNA_T3 -> t("becom.ui.exchange.backhaul", name);
            case ROUTER -> t(
                "becom.ui.exchange.toward",
                name,
                t("becom.ui.dir." + com.enn3developer.becom.machine.InfoLines.direction(j.x() - v.x(), j.z() - v.z())));
            default -> name;
        };
    }

}
