package com.enn3developer.becom.gui.machine;

import java.util.ArrayList;
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
 * antennas on each band and the buses its Line Hatches join; and its coverage on either band.
 */
public final class AntennaUi extends CanvasScreen {

    private static final float WIDTH = 724;
    private static final float HEIGHT = 424;
    private static final float X0 = 16;
    private static final float LEFT = 444;
    private static final float MAP = 232;
    private static final float ROW = 26;
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
        // Antenna T2, whether it works, and its survey.
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
        y += 22 + 10;
        paintLeft(c, v, y);
        paintMap(c, v, X0 + LEFT + 16, y);
    }

    private void paintLeft(Canvas c, AntennaView v, float top) {
        float y = top;
        float cell = (LEFT - 12) / 3;
        AntennaView.Bus backhaul = backhaul(v);
        String fed = backhaul == null ? t("becom.ui.antenna.calls_radio")
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
        // The table, in a display: its heading, then a row a link, paged when it doesn't fit.
        float[] col = columns();
        String[] heads = { "", t("becom.ui.antenna.linked_to"), t("becom.ui.antenna.blocks"),
            t("becom.ui.antenna.radio"), t("becom.ui.antenna.calls") };
        int size = v.links()
            .size()
            + v.buses()
                .size();
        float bottom = HEIGHT - X0;
        int fit = fit(bottom - y);
        if (size > fit) {
            bottom -= 24 + 8;
            fit = fit(bottom - y);
        }
        int pages = Math.max(1, (size + fit - 1) / fit);
        page = Math.min(page, pages - 1);
        Grey.display(c, X0, y, LEFT, bottom - y);
        RouterUi.heads(c, heads, col, X0, y, LEFT);
        y += RouterUi.HEAD;
        if (size == 0) c.text(t("becom.ui.antenna.no_links"), col[1], y + (ROW - c.lineHeight(14)) / 2, 14, Paint.GREY);
        for (int i = page * fit; i < Math.min(size, (page + 1) * fit); i++) {
            float ty = y + (ROW - c.lineHeight(14)) / 2;
            RouterUi.stripe(c, i, X0, y, LEFT);
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
                paintBus(
                    c,
                    v.buses()
                        .get(
                            i - v.links()
                                .size()),
                    col,
                    y,
                    ty);
            }
            y += ROW;
        }
        if (pages > 1) Grey.pager(this, c, X0 + LEFT, bottom + 8, page, pages, () -> page--, () -> page++);
    }

    /** How many rows a display {@code h} tall holds under its heading. */
    private static int fit(float h) {
        return Math.max(1, (int) ((h - RouterUi.HEAD - 4) / ROW));
    }

    private static float[] columns() {
        // Icon 20, name, blocks 52, radio 92, calls 92, six apart, inside the display.
        float calls = X0 + LEFT - 8 - 92;
        float radio = calls - 6 - 92;
        float blocks = radio - 6 - 52;
        return new float[] { X0 + 8, X0 + 34, blocks, radio, calls };
    }

    private static void paintLink(Canvas c, AntennaView.Link link, float[] col, float y, float ty) {
        icon(c, col[0] + 3, y + 6, false);
        c.text(t("becom.ui.antenna.link"), col[1], ty, 14, Paint.TEXT);
        c.text(String.valueOf(link.distance()), col[2], ty, 14, Paint.TEXT);
        band(c, col[3], y, ty, link.vhf(), SignalLevel.MEDIUM);
        band(c, col[4], y, ty, link.uhf(), SignalLevel.MAX);
    }

    /** A bus its Line Hatches join: what it reaches, its blocks, and its cable and what calls and data take of it. */
    private static void paintBus(Canvas c, AntennaView.Bus bus, float[] col, float y, float ty) {
        icon(c, col[0] + 3, y + 6, true);
        List<String> joins = new ArrayList<>();
        if (bus.phones() > 0)
            joins.add(t(bus.phones() == 1 ? "becom.ui.router.phone" : "becom.ui.router.phones", bus.phones()));
        if (bus.routers() > 0)
            joins.add(t(bus.routers() == 1 ? "becom.ui.router.router" : "becom.ui.router.routers", bus.routers()));
        if (bus.gateways() > 0)
            joins.add(t(bus.gateways() == 1 ? "becom.ui.router.gateway" : "becom.ui.router.gateways", bus.gateways()));
        String joined = joins.isEmpty() ? t("becom.ui.router.joins_nothing") : String.join(", ", joins);
        c.text(Paint.fit(c, joined, 14, col[2] - col[1] - 6), col[1], ty, 14, Paint.TEXT);
        c.text(String.valueOf(bus.cables()), col[2], ty, 14, Paint.TEXT);
        String carried = bus.rate() > 0 ? t("becom.ui.antenna.bus", cable(bus.cable()), bus.used(), bus.rate())
            : bus.cables() > BeComConfig.get()
                .cableReach(CableTier.values()[bus.cable()]) ? t("becom.ui.router.bus_too_long")
                    : t("becom.ui.router.bus_down");
        // It spans Radio and Calls, wrapping onto a second line as on the board.
        List<String> lines = Paint.wrap(c, carried, 12, X0 + LEFT - 8 - col[3]);
        float ly = lines.size() > 1 ? y + (ROW - 2 * c.lineHeight(12) - 2) / 2 : ty + 1;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            c.text(lines.get(i), col[3], ly, 12, bus.rate() > 0 ? Paint.GREEN : Paint.RED);
            ly += c.lineHeight(12) + 2;
        }
    }

    /** An antenna, a mast with two crossbars; or a bus, a run of cable between two posts. */
    private static void icon(Canvas c, float x, float y, boolean backhaul) {
        int ink = Paint.LIGHT;
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
            c.text(t("becom.ui.antenna.out_of_reach"), x, ty, 13, Paint.GREY);
            return;
        }
        Paint.bars(c, x, y + 7, level);
        c.text(Math.round(snr) + " dB", x + 24, ty, 13, Paint.level(level));
    }

    /** Its best working backhaul: the bus to another router that carries the most, or null. */
    private static AntennaView.Bus backhaul(AntennaView v) {
        AntennaView.Bus best = null;
        for (AntennaView.Bus b : v.buses()) {
            if (b.backhaul() && b.rate() > 0 && (best == null || b.rate() > best.rate())) best = b;
        }
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
    }

    @Override
    public void scroll(int amount) {
        page = Math.max(0, page - Integer.signum(amount));
    }
}
