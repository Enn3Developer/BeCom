package com.enn3developer.becom.gui.machine;

import static com.enn3developer.becom.gui.machine.AntennaUi.t;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.enn3developer.becom.gui.RouterView;
import com.enn3developer.becom.gui.paint.Canvas;
import com.enn3developer.becom.gui.paint.CanvasScreen;
import com.enn3developer.becom.gui.paint.Grey;
import com.enn3developer.becom.gui.paint.Paint;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.phone.PhoneKind;

/**
 * The Router's screen, from the Router board: the calls it carries, then each bus of cable joining it, with its cable
 * and what it carries, and under it what's on it: each phone, its number and what it's doing, and the routers and
 * gateways it joins.
 */
public final class RouterUi extends CanvasScreen {

    private static final float WIDTH = 604;
    private static final float HEIGHT = 484;
    private static final float X0 = 16;
    private static final float INNER = 572;
    private static final float ROW = 24;
    static final int CALLS = 0x55ff55;
    static final int FREE = 0x2a3038;
    // A bus's strip, a shade darker than the panel; text in colours that read on grey.
    static final int STRIP = 0xb4b4b4;
    static final int GOOD_TEXT = 0x2f7f2f;
    static final int BUSY_TEXT = 0x8a6a10;
    static final int BAD_TEXT = 0xa02818;

    private final Supplier<RouterView> view;
    private int page;

    public RouterUi(Supplier<RouterView> view) {
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

    /** A cable's colour, as its swatch: Phone orange, Coaxial grey, Fibre blue. */
    static int cableColour(int tier) {
        return switch (CableTier.byOrdinal(tier)) {
            case PHONE -> 0xe08a3a;
            case COAXIAL -> 0x9a9a9a;
            case FIBRE -> 0x5aa0d0;
        };
    }

    /** A voltage's chip colour, as Gregtech writes its tiers: LV grey, MV orange, HV yellow. */
    static int voltageColour(int tier) {
        return switch (CableTier.byOrdinal(tier)) {
            case PHONE -> 0x7a7a7a;
            case COAXIAL -> 0xc0601a;
            case FIBRE -> 0xb09a10;
        };
    }

    /**
     * A node kind's name on its own, as a label starts with it: "Antenna T3", where a sentence says "the" before it.
     */
    static String kindLabel(int kind) {
        if (kind < 0 || kind >= NodeKind.values().length) return "?";
        return t(
            "becom.ui.node." + NodeKind.values()[kind].name()
                .toLowerCase(Locale.ROOT));
    }

    static String blocks(int blocks) {
        return blocks >= 1000 && blocks % 1024 == 0 ? (blocks / 1024) + "k" : String.valueOf(blocks);
    }

    @Override
    protected void paint(Canvas c) {
        RouterView v = view.get();
        Grey.panel(c, WIDTH, HEIGHT);
        float y = X0;
        String title = t("becom.ui.router.title");
        c.text(title, X0, y + (22 - c.lineHeight(18)) / 2, 18, Grey.TITLE);
        float x = X0 + c.width(title, 18) + 10;
        x += Grey.chip(
            c,
            x,
            y + 3,
            CableTier.byOrdinal(v.tier())
                .voltage(),
            voltageColour(v.tier())) + 10;
        Grey.chip(
            c,
            x,
            y + 3,
            t(v.working() ? "becom.ui.radio.online" : "becom.ui.radio.offline"),
            v.working() ? Grey.GOOD : Grey.BAD);
        y += 22 + 10;
        y = paintCalls(c, v, y) + 10;
        paintBuses(c, v, y, HEIGHT - X0);
    }

    /** Its display: the calls it carries now, of the most it carries at once; or why it carries none. */
    private static float paintCalls(Canvas c, RouterView v, float y) {
        float h = 40;
        Grey.display(c, X0, y, INNER, h);
        float x = X0 + 12;
        float w = INNER - 24;
        float ly = y + (h - c.lineHeight(14)) / 2;
        c.text(t("becom.ui.router.calls"), x, ly, 14, Paint.GREY);
        float bx = x + c.width(t("becom.ui.router.calls"), 14) + 8;
        if (!v.working()) {
            c.text(t("becom.ui.router.offline"), bx, ly, 14, Paint.RED);
            return y + h;
        }
        String of = t("becom.ui.router.calls_of", v.calls(), v.slots());
        float bw = w - (bx - x) - c.width(of, 14) - 10;
        c.rect(bx, ly + 1, bw, 12, FREE);
        if (v.slots() > 0) c.rect(bx, ly + 1, bw * Math.min(1, v.calls() / (float) v.slots()), 12, CALLS);
        Paint.right(c, of, x + w, ly, 14, v.slots() > 0 && v.calls() >= v.slots() ? Paint.RED : Paint.TEXT);
        return y + h;
    }

    /**
     * What's on its buses, a bus at a time: a strip with its cable and what it carries, then each phone, router and
     * gateway on it, a phone with its number and what it's doing. Paged when it doesn't fit.
     */
    private void paintBuses(Canvas c, RouterView v, float y, float bottom) {
        if (v.buses()
            .isEmpty()) {
            c.text(t("becom.ui.router.no_bus"), X0, y + 4, 14, BAD_TEXT);
            return;
        }
        List<Object> rows = new ArrayList<>();
        for (int i = 0; i < v.buses()
            .size(); i++) {
            rows.add(
                v.buses()
                    .get(i));
            int before = rows.size();
            for (RouterView.Device d : v.devices()) if (d.bus() == i) rows.add(d);
            if (rows.size() == before) rows.add(t("becom.ui.router.nothing_on"));
        }
        int fit = Math.max(1, (int) ((bottom - y) / ROW));
        if (rows.size() > fit) {
            bottom -= 24 + 8;
            fit = Math.max(1, (int) ((bottom - y) / ROW));
        }
        int pages = Math.max(1, (rows.size() + fit - 1) / fit);
        page = Math.min(page, pages - 1);
        for (int i = page * fit; i < Math.min(rows.size(), (page + 1) * fit); i++) {
            Object row = rows.get(i);
            float ty = y + Math.round((ROW - 2 - c.lineHeight(14)) / 2);
            if (row instanceof RouterView.Bus bus) {
                c.rect(X0, y, INNER, ROW - 2, STRIP);
                busLine(c, bus, X0 + 8, ty, INNER - 16, false);
            } else if (row instanceof RouterView.Device d) {
                paintDevice(c, d, X0 + 28, ty, INNER - 36);
            } else {
                c.text((String) row, X0 + 28, ty, 14, Grey.FAINT);
            }
            y += ROW;
        }
        if (pages > 1) Grey.pager(this, c, X0 + INNER, bottom + 8, page, pages, () -> page--, () -> page++);
    }

    /**
     * A bus on one line from {@code x}, {@code w} wide: its cable, with what it joins after when {@code joins}, and on
     * the right what it carries, or why it carries nothing.
     */
    static void busLine(Canvas c, RouterView.Bus bus, float x, float ty, float w, boolean joins) {
        String carries;
        int colour;
        if (bus.tooLong()) {
            carries = t("becom.ui.router.too_long", bus.cables(), blocks(bus.limit()));
            colour = BAD_TEXT;
        } else if (bus.rate() <= 0) {
            carries = t("becom.ui.router.bus_down");
            colour = BAD_TEXT;
        } else {
            carries = t("becom.ui.router.of", bus.used() + bus.data(), bus.rate());
            colour = bus.used() + bus.data() >= bus.rate() ? BAD_TEXT : Grey.TEXT;
        }
        float right = x + w - c.width(carries, 14);
        c.text(carries, right, ty, 14, colour);
        float after = cable(c, bus.cable(), x, ty);
        if (!joins) return;
        List<String> joined = new ArrayList<>();
        if (bus.phones() > 0)
            joined.add(t(bus.phones() == 1 ? "becom.ui.router.phone" : "becom.ui.router.phones", bus.phones()));
        if (bus.routers() > 0)
            joined.add(t(bus.routers() == 1 ? "becom.ui.router.router" : "becom.ui.router.routers", bus.routers()));
        if (bus.gateways() > 0)
            joined.add(t(bus.gateways() == 1 ? "becom.ui.router.gateway" : "becom.ui.router.gateways", bus.gateways()));
        if (!joined.isEmpty())
            c.text(Paint.fit(c, String.join(", ", joined), 14, right - after - 20), after + 12, ty, 14, Grey.NOTE);
    }

    /** A cable's swatch and its name, "Phone cable"; returns where it ends. */
    static float cable(Canvas c, int tier, float x, float ty) {
        c.rect(x, ty + Math.round((c.lineHeight(14) - 5) / 2), 14, 5, cableColour(tier));
        String name = t(
            "becom.ui.router.cable_named",
            t(
                "becom.ui.cable." + CableTier.byOrdinal(tier)
                    .name()
                    .toLowerCase(Locale.ROOT)));
        c.text(name, x + 20, ty, 14, Grey.TITLE);
        return x + 20 + c.width(name, 14);
    }

    /** A thing on a bus: a phone's kind and number, and what it's doing; a router's or gateway's kind alone. */
    private static void paintDevice(Canvas c, RouterView.Device d, float x, float ty, float w) {
        String kind = d.node() >= 0 ? kindLabel(d.node())
            : d.kind() < 0 ? "?"
                : t(
                    "becom.phone.kind." + PhoneKind.values()[d.kind()].name()
                        .toLowerCase(Locale.ROOT));
        c.text(kind, x, ty, 14, Grey.TEXT);
        if (d.node() >= 0) return;
        if (d.number() >= 0) c.text(String.valueOf(d.number()), x + c.width(kind, 14) + 10, ty, 14, Grey.NOTE);
        String now;
        int colour;
        switch (d.now()) {
            case CALL -> {
                now = t("becom.ui.router.now_call", d.with());
                colour = GOOD_TEXT;
            }
            case RINGING -> {
                now = t("becom.ui.router.now_ringing");
                colour = BUSY_TEXT;
            }
            case TOO_LONG -> {
                now = t("becom.ui.router.now_too_long");
                colour = BAD_TEXT;
            }
            default -> {
                now = t("becom.ui.router.now_idle");
                colour = Grey.FAINT;
            }
        }
        Paint.right(c, Paint.fit(c, now, 14, w / 2), x + w, ty, 14, colour);
    }

    @Override
    public void scroll(int amount) {
        page = Math.max(0, page - Integer.signum(amount));
    }
}
