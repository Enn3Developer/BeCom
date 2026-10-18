package com.enn3developer.becom.gui.machine;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.gui.AntennaView;
import com.enn3developer.becom.gui.paint.Canvas;
import com.enn3developer.becom.gui.paint.CanvasScreen;
import com.enn3developer.becom.gui.paint.Grey;
import com.enn3developer.becom.gui.paint.Paint;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.net.CableTier;

/**
 * The Antenna's screen, from the Antenna board: its mast, reach and calls; the team lock; what it's linked to, other
 * antennas on each band and its backhaul; and its coverage on either band.
 */
public final class AntennaUi extends CanvasScreen {

    private static final float WIDTH = 724;
    private static final float HEIGHT = 424;
    private static final float X0 = 16;
    private static final float LEFT = 444;
    private static final float MAP = 232;
    private static final float ROW = 26;
    private static final int ROWS = 6;
    private static final int[] LEGEND = { 0xb03020, 0xd0a020, 0x40b040, 0x30c0d0 };

    private final Supplier<AntennaView> view;
    private final Consumer<Boolean> lock;
    private boolean calls;
    private int page;
    private IDrawable radioMap;
    private IDrawable callMap;

    public AntennaUi(Supplier<AntennaView> view, Consumer<Boolean> lock) {
        this.view = view;
        this.lock = lock;
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

    static String t(String key, Object... args) {
        return args.length == 0 ? StatCollector.translateToLocal(key)
            : StatCollector.translateToLocalFormatted(key, args);
    }

    @Override
    protected void paint(Canvas c) {
        AntennaView v = view.get();
        Grey.panel(c, WIDTH, HEIGHT);
        float y = X0;
        // Antenna T2, whether it works, its survey, and whose.
        String title = t("becom.ui.antenna.title", v.tier());
        c.text(title, X0, y + (22 - c.lineHeight(18)) / 2, 18, Grey.TITLE);
        float x = X0 + c.width(title, 18) + 10;
        String state = !v.formed() ? t("becom.ui.machine.unformed")
            : t(v.working() ? "becom.ui.radio.online" : "becom.ui.radio.offline");
        x += Grey.chip(c, x, y + 3, state, v.formed() && v.working() ? Grey.GOOD : Grey.BAD) + 10;
        c.text(
            t(v.surveyed() ? "becom.ui.antenna.surveyed" : "becom.ui.antenna.surveying"),
            x,
            y + (22 - c.lineHeight(13)) / 2,
            13,
            Grey.NOTE);
        Paint.right(
            c,
            t("becom.ui.machine.whose", v.owner()),
            X0 + 692,
            y + (22 - c.lineHeight(15)) / 2,
            15,
            Grey.FAINT);
        y += 22 + 10;
        paintLeft(c, v, y);
        paintMap(c, v, X0 + LEFT + 16, y);
    }

    private void paintLeft(Canvas c, AntennaView v, float top) {
        float y = top;
        float cell = (LEFT - 12) / 3;
        AntennaView.Bridge backhaul = backhaul(v);
        String fed = backhaul == null || backhaul.cable() < 0 ? t("becom.ui.antenna.calls_radio")
            : t("becom.ui.antenna.calls_fed", cable(backhaul.cable()));
        Grey.cell(
            c,
            X0,
            y,
            cell,
            46,
            t("becom.ui.antenna.mast"),
            12,
            t("becom.ui.machine.blocks", v.mast()),
            18,
            Paint.TEXT);
        Grey.cell(
            c,
            X0 + cell + 6,
            y,
            cell,
            46,
            t("becom.ui.antenna.reaches"),
            12,
            t("becom.ui.machine.about_blocks", v.range()),
            18,
            Paint.TEXT);
        Grey.cell(
            c,
            X0 + 2 * (cell + 6),
            y,
            cell,
            46,
            fed,
            12,
            t("becom.ui.machine.of", v.calls(), v.callSlots()),
            18,
            Paint.TEXT);
        y += 46 + 10;
        c.text(t("becom.ui.antenna.lock", v.owner()), X0, y + (20 - c.lineHeight(15)) / 2, 15, Grey.TEXT);
        Grey.toggle(this, c, X0 + LEFT - 44, y, v.locked(), v.mayManage() ? lock : null);
        y += 20 + 10;
        // The table: its heading, then a row a link.
        float[] col = columns();
        c.text(t("becom.ui.antenna.linked_to"), col[1], y + 2, 12, Grey.NOTE);
        c.text(t("becom.ui.antenna.blocks"), col[2], y + 2, 12, Grey.NOTE);
        c.text(t("becom.ui.antenna.radio"), col[3], y + 2, 12, Grey.NOTE);
        c.text(t("becom.ui.antenna.calls"), col[4], y + 2, 12, Grey.NOTE);
        c.rect(X0, y + 16, LEFT, 2, 0x9a9a9a);
        y += 18;
        int size = v.links()
            .size()
            + v.bridges()
                .size();
        int pages = Math.max(1, (size + ROWS - 1) / ROWS);
        page = Math.min(page, pages - 1);
        if (size == 0) c.text(t("becom.ui.antenna.no_links"), col[1], y + (ROW - c.lineHeight(14)) / 2, 14, Grey.NOTE);
        for (int i = page * ROWS; i < Math.min(size, (page + 1) * ROWS); i++) {
            float ty = y + (ROW - c.lineHeight(14)) / 2;
            if (i < v.links()
                .size()) {
                paintLink(
                    c,
                    v.links()
                        .get(i),
                    col,
                    y,
                    ty);
            } else {
                paintBridge(
                    c,
                    v.bridges()
                        .get(
                            i - v.links()
                                .size()),
                    col,
                    y,
                    ty);
            }
            y += ROW;
        }
        // The foot: what it serves without backhaul, and the pages.
        float footY = HEIGHT - X0 - 34;
        SignalLevel radio = SignalLevel.values()[v.radioLevel()];
        String without = radio == SignalLevel.NONE ? t("becom.ui.antenna.without_any")
            : t(
                "becom.ui.antenna.without_backhaul",
                v.radioRate(),
                t(
                    "becom.level." + radio.name()
                        .toLowerCase(Locale.ROOT)));
        List<String> lines = Paint.wrap(c, without, 13, LEFT - 110);
        float ly = footY;
        for (String line : lines) {
            c.text(line, X0, ly, 13, Grey.NOTE);
            ly += 17;
        }
        Grey.pager(this, c, X0 + LEFT, footY + 4, page, pages, () -> page--, () -> page++);
    }

    private static float[] columns() {
        // Icon 20, name, blocks 52, radio 92, calls 92, six apart.
        float calls = X0 + LEFT - 92;
        float radio = calls - 6 - 92;
        float blocks = radio - 6 - 52;
        return new float[] { X0, X0 + 26, blocks, radio, calls };
    }

    private static void paintLink(Canvas c, AntennaView.Link link, float[] col, float y, float ty) {
        icon(c, col[0] + 3, y + 6, false);
        c.text(t("becom.ui.antenna.antenna_at", link.x(), link.y(), link.z()), col[1], ty, 14, Grey.TEXT);
        c.text(String.valueOf(link.distance()), col[2], ty, 14, Grey.TEXT);
        band(c, col[3], y, ty, link.vhf(), SignalLevel.MEDIUM);
        band(c, col[4], y, ty, link.uhf(), SignalLevel.MAX);
    }

    private static void paintBridge(Canvas c, AntennaView.Bridge bridge, float[] col, float y, float ty) {
        icon(c, col[0] + 3, y + 6, true);
        String name = RouterUi.kindLabel(bridge.kind());
        c.text(
            Paint.fit(c, t("becom.ui.antenna.at", name, bridge.x(), bridge.y(), bridge.z()), 14, col[2] - col[1] - 6),
            col[1],
            ty,
            14,
            Grey.TEXT);
        c.text(String.valueOf(bridge.distance()), col[2], ty, 14, Grey.TEXT);
        String carried = bridge.rate() <= 0 || bridge.cable() < 0 ? t("becom.ui.antenna.backhaul_none")
            : t("becom.ui.antenna.backhaul", cable(bridge.cable()), bridge.used(), bridge.rate());
        // It spans Radio and Calls, wrapping onto a second line as on the board.
        List<String> lines = Paint.wrap(c, carried, 12, X0 + LEFT - col[3]);
        float ly = lines.size() > 1 ? y + (ROW - 2 * c.lineHeight(12) - 2) / 2 : ty + 1;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            c.text(lines.get(i), col[3], ly, 12, bridge.rate() > 0 ? 0x2f6f2f : 0xa02818);
            ly += c.lineHeight(12) + 2;
        }
    }

    /** An antenna, a mast with two crossbars; or a backhaul, a run between two posts. */
    private static void icon(Canvas c, float x, float y, boolean backhaul) {
        int ink = Grey.TITLE;
        if (backhaul) {
            c.rect(x, y + 6, 14, 2, ink);
            c.rect(x + 2, y + 2, 2, 10, ink);
            c.rect(x + 10, y + 2, 2, 10, ink);
        } else {
            c.rect(x + 6, y, 2, 14, ink);
            c.rect(x + 2, y + 2, 10, 2, ink);
            c.rect(x + 4, y + 6, 6, 2, ink);
        }
    }

    /** A band's bars and SNR, or "out of reach" below Low. */
    private static void band(Canvas c, float x, float y, float ty, double snr, SignalLevel cap) {
        SignalLevel level = SignalLevel.min(
            BeComConfig.get()
                .levelScale()
                .levelOf(snr),
            cap);
        if (!level.atLeast(SignalLevel.LOW)) {
            c.text(t("becom.ui.antenna.out_of_reach"), x, ty, 13, 0x8a8a8a);
            return;
        }
        Grey.bars(c, x, y + 7, level);
        c.text(Math.round(snr) + " dB", x + 24, ty, 13, Grey.text(level));
    }

    private static AntennaView.Bridge backhaul(AntennaView v) {
        AntennaView.Bridge best = null;
        for (AntennaView.Bridge b : v.bridges()) if (best == null || b.rate() > best.rate()) best = b;
        return best;
    }

    static String cable(int tier) {
        return t(
            "becom.ui.cable." + CableTier.values()[tier].name()
                .toLowerCase(Locale.ROOT));
    }

    /** The coverage map with its two bands, its key and its scale. */
    private void paintMap(Canvas c, AntennaView v, float x, float y) {
        float half = MAP / 2;
        if (calls) {
            Grey.button(this, c, x, y, half, 28, t("becom.ui.antenna.radio"), 14, () -> calls = false);
            Grey.pressed(c, x + half, y, half, 28, t("becom.ui.antenna.calls"), 14);
        } else {
            Grey.pressed(c, x, y, half, 28, t("becom.ui.antenna.radio"), 14);
            Grey.button(this, c, x + half, y, half, 28, t("becom.ui.antenna.calls"), 14, () -> calls = true);
        }
        y += 28 + 8;
        Grey.display(c, x, y, MAP, MAP, 0x202020);
        if (radioMap == null) {
            radioMap = BeCom.proxy.coverageMap(
                () -> view.get()
                    .vhfMap(),
                () -> SignalLevel.MEDIUM);
            callMap = BeCom.proxy.coverageMap(
                () -> view.get()
                    .uhfMap(),
                () -> SignalLevel.MAX);
        }
        c.drawable(calls ? callMap : radioMap, x + 2, y + 2, MAP - 4, MAP - 4);
        c.text("N", x + 6, y + 4, 11, Paint.GREY);
        y += MAP + 8;
        int levels = calls ? 4 : 2;
        float lx = x;
        for (int i = 0; i <= levels; i++) {
            boolean none = i == levels;
            String name = none ? t("becom.level.none")
                : t(
                    "becom.level." + SignalLevel.values()[i + 1].name()
                        .toLowerCase(Locale.ROOT));
            c.rect(lx, y + 2, 10, 10, none ? 0x303030 : LEGEND[i]);
            c.text(name, lx + 14, y, 13, Grey.TITLE);
            lx += 14 + c.width(name, 13) + 10;
        }
        y += 22;
        String note = t(calls ? "becom.ui.antenna.map_calls" : "becom.ui.antenna.map_radio", v.mapRange());
        for (String line : Paint.wrap(c, note, 13, MAP)) {
            c.text(line, x, y, 13, Grey.NOTE);
            y += 17;
        }
    }

    @Override
    public void scroll(int amount) {
        page = Math.max(0, page - Integer.signum(amount));
    }
}
