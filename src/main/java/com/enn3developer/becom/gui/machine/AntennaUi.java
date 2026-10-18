package com.enn3developer.becom.gui.machine;

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
            t("becom.ui.antenna.calls"),
            12,
            t("becom.ui.machine.of", v.calls(), v.callSlots()),
            18,
            Paint.TEXT);
        y += 46 + 10;
        float ly = y + Math.round((20 - c.lineHeight(15)) / 2);
        c.text(t("becom.ui.antenna.lock", v.owner()), X0, ly, 15, Grey.TEXT);
        Grey.toggle(this, c, X0 + LEFT - 44, y, v.locked(), v.mayManage() ? lock : null);
        y += 20 + 8;
        // Its backhaul: the cable and what it carries, on the right as the lock's switch.
        ly = y + Math.round((20 - c.lineHeight(15)) / 2);
        c.text(t("becom.ui.antenna.backhaul"), X0, ly, 15, Grey.TEXT);
        AntennaView.Bus backhaul = backhaul(v);
        if (backhaul == null) Paint.right(c, t("becom.ui.antenna.no_backhaul"), X0 + LEFT, ly, 15, Grey.FAINT);
        else {
            String carries = t("becom.ui.router.of", backhaul.used(), backhaul.rate());
            float cx = X0 + LEFT - c.width(carries, 15);
            c.text(carries, cx, ly, 15, Grey.TEXT);
            String name = t("becom.ui.router.cable_named", cable(backhaul.cable()));
            float nx = cx - 16 - c.width(name, 15);
            c.text(name, nx, ly, 15, Grey.TITLE);
            c.rect(nx - 20, ly + Math.round((c.lineHeight(15) - 5) / 2), 14, 5, RouterUi.cableColour(backhaul.cable()));
        }
        y += 20 + 12;
        // The antennas it's linked to: a heading, then a row a link, paged when they don't fit.
        float[] col = columns();
        c.text(t("becom.ui.antenna.linked_to"), col[0], y, 12, Grey.NOTE);
        c.text(t("becom.ui.antenna.blocks"), col[1], y, 12, Grey.NOTE);
        c.text(t("becom.ui.antenna.radio"), col[2], y, 12, Grey.NOTE);
        c.text(t("becom.ui.antenna.calls"), col[3], y, 12, Grey.NOTE);
        y += c.lineHeight(12) + 4;
        c.rect(X0, y, LEFT, 2, 0x9a9a9a);
        y += 2;
        int size = v.links()
            .size();
        float bottom = HEIGHT - X0;
        int fit = Math.max(1, (int) ((bottom - y) / ROW));
        if (size > fit) {
            bottom -= 24 + 8;
            fit = Math.max(1, (int) ((bottom - y) / ROW));
        }
        int pages = Math.max(1, (size + fit - 1) / fit);
        page = Math.min(page, pages - 1);
        if (size == 0) c
            .text(t("becom.ui.antenna.no_links"), col[0], y + Math.round((ROW - c.lineHeight(14)) / 2), 14, Grey.FAINT);
        for (int i = page * fit; i < Math.min(size, (page + 1) * fit); i++) {
            paintLink(
                c,
                v.links()
                    .get(i),
                col,
                y,
                y + Math.round((ROW - 1 - c.lineHeight(14)) / 2));
            y += ROW;
            c.rect(X0, y - 1, LEFT, 1, 0xa8a8a8);
        }
        if (pages > 1) Grey.pager(this, c, X0 + LEFT, bottom + 8, page, pages, () -> page--, () -> page++);
    }

    private static float[] columns() {
        // The antenna, blocks 52, radio 92, calls 92, six apart.
        float calls = X0 + LEFT - 92;
        float radio = calls - 6 - 92;
        float blocks = radio - 6 - 52;
        return new float[] { X0, blocks, radio, calls };
    }

    private static void paintLink(Canvas c, AntennaView.Link link, float[] col, float y, float ty) {
        c.text(
            link.tier() > 0 ? t("becom.ui.antenna.title", link.tier()) : t("becom.ui.antenna.link"),
            col[0],
            ty,
            14,
            Grey.TEXT);
        c.text(String.valueOf(link.distance()), col[1], ty, 14, Grey.TEXT);
        band(c, col[2], y, ty, link.vhf(), SignalLevel.MEDIUM);
        band(c, col[3], y, ty, link.uhf(), SignalLevel.MAX);
    }

    /** A band's bars and SNR, or "out of reach" below Low. */
    private static void band(Canvas c, float x, float y, float ty, double snr, SignalLevel cap) {
        SignalLevel level = SignalLevel.min(
            BeComConfig.get()
                .levelScale()
                .levelOf(snr),
            cap);
        if (!level.atLeast(SignalLevel.LOW)) {
            c.text(t("becom.ui.antenna.out_of_reach"), x, ty, 13, Grey.FAINT);
            return;
        }
        Grey.bars(c, x, ty + Math.round((c.lineHeight(13) - 12) / 2), level);
        c.text(Math.round(snr) + " dB", x + 24, ty, 13, Grey.text(level));
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
