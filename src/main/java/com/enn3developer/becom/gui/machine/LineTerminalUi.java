package com.enn3developer.becom.gui.machine;

import static com.enn3developer.becom.gui.machine.AntennaUi.t;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.enn3developer.becom.gui.LineTerminalView;
import com.enn3developer.becom.gui.paint.Canvas;
import com.enn3developer.becom.gui.paint.CanvasScreen;
import com.enn3developer.becom.gui.paint.Grey;
import com.enn3developer.becom.gui.paint.Paint;
import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.phone.PhoneKind;

/**
 * The Line Terminal's screen, from the Line Terminal board: its uplink and how much of the line calls and data take,
 * the calls it carries, then a row a bus of cable joining it: its cable, length and what it carries; and what's on
 * those buses: each phone, its number and what it's doing, then the routers and gateways they join.
 */
public final class LineTerminalUi extends CanvasScreen {

    private static final float WIDTH = 604;
    private static final float HEIGHT = 484;
    private static final float X0 = 16;
    private static final float INNER = 572;
    private static final float ROW = 26;
    static final int CALLS = 0x55ff55;
    static final int DATA = 0x55ffaa;
    static final int FREE = 0x2a3038;

    private final Supplier<LineTerminalView> view;
    private int page;

    public LineTerminalUi(Supplier<LineTerminalView> view) {
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

    static String kindName(int kind) {
        if (kind < 0) return t("becom.node.pylon");
        return t(
            "becom.node." + NodeKind.values()[kind].name()
                .toLowerCase(Locale.ROOT));
    }

    /**
     * A node kind's name on its own, as a label starts with it: "Antenna T3", where a sentence says "the" before it.
     */
    static String kindLabel(int kind) {
        if (kind < 0) return t("becom.ui.node.pylon");
        return t(
            "becom.ui.node." + NodeKind.values()[kind].name()
                .toLowerCase(Locale.ROOT));
    }

    /** "2× Phone Cable, Coaxial Cable", or a dash for a bare span. */
    static String cables(int[] counts) {
        List<String> parts = new ArrayList<>();
        for (int tier = 0; tier < counts.length; tier++) {
            if (counts[tier] <= 0) continue;
            String name = t(
                "tile.becom." + com.enn3developer.becom.block.BlockPhoneCable.nameOf(CableTier.byOrdinal(tier))
                    + ".name");
            parts.add(counts[tier] > 1 ? counts[tier] + "× " + name : name);
        }
        return parts.isEmpty() ? "-" : String.join(", ", parts);
    }

    static String blocks(int blocks) {
        return blocks >= 1000 && blocks % 1024 == 0 ? (blocks / 1024) + "k" : String.valueOf(blocks);
    }

    @Override
    protected void paint(Canvas c) {
        LineTerminalView v = view.get();
        Grey.panel(c, WIDTH, HEIGHT);
        float y = X0;
        String title = t("becom.ui.terminal.title");
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
        if (!v.owner()
            .isEmpty())
            Paint.right(
                c,
                t("becom.ui.machine.whose", v.owner()),
                X0 + INNER,
                y + (22 - c.lineHeight(15)) / 2,
                15,
                Grey.FAINT);
        y += 22 + 10;
        y = paintUplink(c, v, y) + 10;
        paintTable(c, v, y, HEIGHT - X0 - 40);
        float footY = HEIGHT - X0 - 34;
        for (String line : Paint.wrap(c, t("becom.ui.terminal.note"), 13, INNER)) {
            c.text(line, X0, footY, 13, Grey.NOTE);
            footY += 17;
        }
    }

    /** The uplink's display: where the line goes, the bar of what calls and data take of it, and its key. */
    private float paintUplink(Canvas c, LineTerminalView v, float y) {
        float h = 104;
        Grey.display(c, X0, y, INNER, h);
        float x = X0 + 12;
        float w = INNER - 24;
        float ly = y + 10;
        c.text(t("becom.ui.terminal.uplink"), x, ly, 14, Paint.GREY);
        float vx = x + c.width(t("becom.ui.terminal.uplink"), 14) + 8;
        paintCalls(c, v, x, vx, w, y + h - 24);
        if (v.uplinks()
            .isEmpty()) {
            c.text(t("becom.ui.terminal.no_uplink"), vx, ly, 14, Paint.RED);
            return y + h;
        }
        LineTerminalView.Uplink u = v.uplinks()
            .get(0);
        String to = t("becom.ui.terminal.to", cables(u.cables()), kindName(u.kind()), u.x(), u.y(), u.z());
        String blocks = t("becom.ui.machine.blocks", u.distance());
        c.text(Paint.fit(c, to, 14, w - (vx - x) - c.width(blocks, 14) - 12), vx, ly, 14, Paint.TEXT);
        Paint.right(c, blocks, x + w, ly, 14, Paint.GREY);
        ly += 24;
        c.text(t("becom.ui.terminal.line"), x, ly, 14, Paint.GREY);
        float bx = x + c.width(t("becom.ui.terminal.uplink"), 14) + 8;
        String problem = !v.working() ? t("becom.ui.terminal.offline")
            : u.rate() < 0 ? t("becom.ui.terminal.uplink_down")
                : u.rate() == 0 ? t("becom.ui.terminal.too_long") : null;
        if (problem != null) {
            c.text(problem, bx, ly, 14, Paint.RED);
            return y + h;
        }
        String of = t("becom.ui.terminal.of", u.used() + u.data(), u.rate());
        float bw = w - (bx - x) - c.width(of, 14) - 10;
        c.rect(bx, ly + 1, bw, 12, FREE);
        float calls = bw * Math.min(1, u.used() / (float) u.rate());
        float data = Math.min(bw - calls, bw * u.data() / (float) u.rate());
        c.rect(bx, ly + 1, calls, 12, CALLS);
        c.rect(bx + calls, ly + 1, data, 12, DATA);
        Paint.right(c, of, x + w, ly, 14, u.used() + u.data() >= u.rate() ? Paint.RED : Paint.TEXT);
        ly += 22;
        int callCount = v.calls();
        float kx = x;
        kx = key(
            c,
            kx,
            ly,
            CALLS,
            t(callCount == 1 ? "becom.ui.terminal.key_call" : "becom.ui.terminal.key_calls", callCount, u.used()));
        kx = key(c, kx, ly, DATA, t("becom.ui.terminal.key_data", u.data()));
        key(c, kx, ly, FREE, t("becom.ui.terminal.key_free"));
        return y + h;
    }

    /** The calls it carries now, of the most it carries at once. */
    private static void paintCalls(Canvas c, LineTerminalView v, float x, float bx, float w, float ly) {
        c.text(t("becom.ui.terminal.calls"), x, ly, 14, Paint.GREY);
        String of = t("becom.ui.terminal.calls_of", v.calls(), v.slots());
        float bw = w - (bx - x) - c.width(of, 14) - 10;
        c.rect(bx, ly + 1, bw, 12, FREE);
        if (v.slots() > 0) c.rect(bx, ly + 1, bw * Math.min(1, v.calls() / (float) v.slots()), 12, CALLS);
        Paint.right(c, of, x + w, ly, 14, v.slots() > 0 && v.calls() >= v.slots() ? Paint.RED : Paint.TEXT);
    }

    static float key(Canvas c, float x, float y, int colour, String label) {
        c.rect(x, y + 4, 14, 5, colour);
        if (colour == FREE) Paint.border(c, x, y + 4, 14, 5, 1, 0x4a5560);
        c.text(label, x + 20, y, 12, Paint.GREY);
        return x + 20 + c.width(label, 12) + 14;
    }

    /** The table: a row a bus, then what's on the buses, paged when it doesn't fit. */
    private void paintTable(Canvas c, LineTerminalView v, float y, float bottom) {
        float[] col = { X0, X0 + 32, X0 + 170, X0 + 262, X0 + INNER - 150 };
        if (v.buses()
            .isEmpty()) {
            c.text(t("becom.ui.terminal.no_bus"), X0, y + 4, 14, 0xa02818);
            return;
        }
        // Each bus by index, then the devices under their own heading.
        List<Object> rows = new ArrayList<>();
        for (int i = 0; i < v.buses()
            .size(); i++) rows.add(i);
        if (!v.devices()
            .isEmpty()) rows.add(Boolean.TRUE);
        rows.addAll(v.devices());
        int fit = Math.max(1, (int) ((bottom - y - 20) / ROW));
        int pages = Math.max(1, (rows.size() + fit - 1) / fit);
        page = Math.min(page, pages - 1);
        String[] heads = { "", t("becom.ui.terminal.bus"), t("becom.ui.terminal.length"), t("becom.ui.terminal.cable"),
            t("becom.ui.terminal.carries") };
        for (int i = 1; i < heads.length; i++) c.text(heads[i], col[i], y + 4, 12, Grey.NOTE);
        c.rect(X0, y + 18, INNER, 2, 0x9a9a9a);
        y += 20;
        int last = Math.min(rows.size(), (page + 1) * fit);
        for (int i = page * fit; i < last; i++) {
            Object row = rows.get(i);
            float ty = y + (ROW - c.lineHeight(14)) / 2;
            if (row instanceof Integer index) {
                paintBus(
                    c,
                    v.buses()
                        .get(index),
                    index,
                    col,
                    ty);
            } else if (row instanceof LineTerminalView.Device d) {
                paintDevice(c, d, col, ty);
            } else {
                // The devices' own heading, under the buses.
                String[] under = { "", t("becom.ui.terminal.device"), t("becom.ui.terminal.number"),
                    t("becom.ui.terminal.cable"), t("becom.ui.terminal.now") };
                for (int h = 1; h < under.length; h++) c.text(under[h], col[h], ty + 2, 12, Grey.NOTE);
            }
            if (i < last - 1) c.rect(X0, y + ROW - 1, INNER, 1, 0xb0b0b0);
            y += ROW;
        }
        if (pages > 1) Grey.pager(this, c, X0 + INNER, bottom - 26, page, pages, () -> page--, () -> page++);
    }

    private static void paintBus(Canvas c, LineTerminalView.Bus bus, int index, float[] col, float ty) {
        c.text(String.valueOf(index + 1), col[0], ty, 14, Grey.NOTE);
        List<String> joins = new ArrayList<>();
        if (bus.phones() > 0)
            joins.add(t(bus.phones() == 1 ? "becom.ui.terminal.phone" : "becom.ui.terminal.phones", bus.phones()));
        if (bus.routers() > 0)
            joins.add(t(bus.routers() == 1 ? "becom.ui.terminal.router" : "becom.ui.terminal.routers", bus.routers()));
        if (bus.gateways() > 0) joins
            .add(t(bus.gateways() == 1 ? "becom.ui.terminal.gateway" : "becom.ui.terminal.gateways", bus.gateways()));
        String joined = joins.isEmpty() ? t("becom.ui.terminal.joins_nothing") : String.join(", ", joins);
        c.text(Paint.fit(c, joined, 14, col[2] - col[1] - 8), col[1], ty, 14, Grey.TEXT);
        c.text(
            (bus.tooLong() ? ">" + blocks(bus.limit()) : String.valueOf(bus.cables())) + "/" + blocks(bus.limit()),
            col[2],
            ty,
            14,
            bus.tooLong() ? 0xa02818 : Grey.TEXT);
        cable(c, bus.cable(), col[3], ty);
        String carries;
        int colour;
        if (bus.tooLong()) {
            carries = t("becom.ui.terminal.bus_too_long");
            colour = 0xa02818;
        } else if (bus.rate() <= 0) {
            carries = t("becom.ui.terminal.bus_down");
            colour = 0xa02818;
        } else {
            carries = t("becom.ui.terminal.of", bus.used() + bus.data(), bus.rate());
            colour = bus.used() + bus.data() >= bus.rate() ? 0xa02818 : Grey.TEXT;
        }
        c.text(Paint.fit(c, carries, 14, 150), col[4], ty, 14, colour);
    }

    /** A cable's swatch and short name. */
    private static void cable(Canvas c, int tier, float x, float ty) {
        c.rect(x, ty + 4, 14, 5, cableColour(tier));
        c.text(
            t(
                "becom.ui.cable." + CableTier.byOrdinal(tier)
                    .name()
                    .toLowerCase(Locale.ROOT)),
            x + 20,
            ty,
            14,
            Grey.TEXT);
    }

    private void paintDevice(Canvas c, LineTerminalView.Device d, float[] col, float ty) {
        LineTerminalView v = view.get();
        c.text(String.valueOf(d.bus() + 1), col[0], ty, 14, Grey.NOTE);
        String kind = d.node() >= 0 ? kindLabel(d.node())
            : d.kind() < 0 ? "?"
                : t(
                    "becom.phone.kind." + PhoneKind.values()[d.kind()].name()
                        .toLowerCase(Locale.ROOT));
        c.text(Paint.fit(c, kind, 14, col[2] - col[1] - 8), col[1], ty, 14, Grey.TEXT);
        c.text(d.number() < 0 ? "-" : String.valueOf(d.number()), col[2], ty, 14, Grey.TEXT);
        if (d.bus() >= 0 && d.bus() < v.buses()
            .size()) cable(
                c,
                v.buses()
                    .get(d.bus())
                    .cable(),
                col[3],
                ty);
        String now;
        int colour;
        switch (d.now()) {
            case CALL -> {
                now = t("becom.ui.terminal.now_call", d.with());
                colour = 0x2f7f2f;
            }
            case RINGING -> {
                now = t("becom.ui.terminal.now_ringing");
                colour = 0x8a6a10;
            }
            case TOO_LONG -> {
                now = t("becom.ui.terminal.now_too_long");
                colour = 0xa02818;
            }
            case JOINED -> {
                now = t("becom.ui.terminal.now_joined");
                colour = Grey.FAINT;
            }
            default -> {
                now = t("becom.ui.terminal.now_idle");
                colour = Grey.FAINT;
            }
        }
        c.text(Paint.fit(c, now, 14, 150), col[4], ty, 14, colour);
    }

    @Override
    public void scroll(int amount) {
        page = Math.max(0, page - Integer.signum(amount));
    }
}
