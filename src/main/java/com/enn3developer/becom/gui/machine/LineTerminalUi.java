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
 * then a row a port: the device on it, its number, cable and run, and what it's doing; free ports; and the devices
 * on its cable it can't serve, and why.
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
        float h = 80;
        Grey.display(c, X0, y, INNER, h);
        float x = X0 + 12;
        float w = INNER - 24;
        float ly = y + 10;
        c.text(t("becom.ui.terminal.uplink"), x, ly, 14, Paint.GREY);
        float vx = x + c.width(t("becom.ui.terminal.uplink"), 14) + 8;
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
        int callCount = 0;
        for (LineTerminalView.Device d : v.devices()) if (d.now() == LineTerminalView.Now.CALL) callCount++;
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

    static float key(Canvas c, float x, float y, int colour, String label) {
        c.rect(x, y + 4, 14, 5, colour);
        if (colour == FREE) Paint.border(c, x, y + 4, 14, 5, 1, 0x4a5560);
        c.text(label, x + 20, y, 12, Paint.GREY);
        return x + 20 + c.width(label, 12) + 14;
    }

    /** The table: a row a port, free ports, then the devices it can't serve. */
    private void paintTable(Canvas c, LineTerminalView v, float y, float bottom) {
        float[] col = { X0, X0 + 32, X0 + 150, X0 + 228, X0 + INNER - 112 - 8 - 54, X0 + INNER - 112 };
        String[] heads = { "", t("becom.ui.terminal.device"), t("becom.ui.terminal.number"),
            t("becom.ui.terminal.cable"), t("becom.ui.terminal.run"), t("becom.ui.terminal.now") };
        for (int i = 1; i < heads.length; i++) c.text(heads[i], col[i], y + 4, 12, Grey.NOTE);
        c.rect(X0, y + 18, INNER, 2, 0x9a9a9a);
        y += 20;
        List<LineTerminalView.Device> served = new ArrayList<>();
        List<LineTerminalView.Device> unserved = new ArrayList<>();
        for (LineTerminalView.Device d : v.devices()) (d.now()
            .served() ? served : unserved).add(d);
        int ports = Math.max(v.ports(), served.size());
        int fit = Math.max(1, (int) ((bottom - y) / ROW));
        // The rows: each served device, the first two free ports, the rest of them as one "7–8 free" row as on the
        // board, then each device it can't serve.
        List<Object> rows = new ArrayList<>();
        for (int i = 0; i < served.size(); i++) rows.add(i);
        int free = Math.min(ports - served.size(), 3);
        for (int i = 0; i < free; i++) rows.add(new int[] { served.size() + i, served.size() + i });
        if (free == 3) rows.set(rows.size() - 1, new int[] { served.size() + 2, ports - 1 });
        rows.addAll(unserved);
        // Free ports after what fits on a page merge into one "7-8 free" row.
        while (rows.size() > fit && free > 1) {
            int at = served.size() + free - 1;
            int[] removed = (int[]) rows.remove(at);
            int[] prev = (int[]) rows.get(at - 1);
            rows.set(at - 1, new int[] { prev[0], removed[1] });
            free--;
        }
        int pages = Math.max(1, (rows.size() + fit - 1) / fit);
        page = Math.min(page, pages - 1);
        for (int i = page * fit; i < Math.min(rows.size(), (page + 1) * fit); i++) {
            Object row = rows.get(i);
            float ty = y + (ROW - c.lineHeight(14)) / 2;
            if (row instanceof Integer index) {
                paintDevice(c, served.get(index), String.valueOf(index + 1), col, ty);
            } else if (row instanceof int[]range) {
                String label = range[0] == range[1] ? String.valueOf(range[0] + 1)
                    : (range[0] + 1) + "\u2013" + (range[1] + 1);
                c.text(label, col[0], ty, 14, 0x8a8a8a);
                c.text(t("becom.ui.terminal.free"), col[1], ty, 14, 0x8a8a8a);
            } else {
                paintDevice(c, (LineTerminalView.Device) row, "-", col, ty);
            }
            // A rule between rows, none under the last.
            if (i < Math.min(rows.size(), (page + 1) * fit) - 1) c.rect(X0, y + ROW - 1, INNER, 1, 0xb0b0b0);
            y += ROW;
        }
        if (pages > 1) Grey.pager(this, c, X0 + INNER, bottom - 26, page, pages, () -> page--, () -> page++);
    }

    private static void paintDevice(Canvas c, LineTerminalView.Device d, String index, float[] col, float ty) {
        c.text(index, col[0], ty, 14, Grey.NOTE);
        String kind = d.kind() < 0 ? "?"
            : t(
                "becom.phone.kind." + PhoneKind.values()[d.kind()].name()
                    .toLowerCase(Locale.ROOT));
        c.text(kind, col[1], ty, 14, Grey.TEXT);
        c.text(d.number() < 0 ? "-" : String.valueOf(d.number()), col[2], ty, 14, Grey.TEXT);
        c.rect(col[3], ty + 4, 14, 5, cableColour(d.cable()));
        c.text(
            t(
                "becom.ui.cable." + CableTier.byOrdinal(d.cable())
                    .name()
                    .toLowerCase(Locale.ROOT)),
            col[3] + 20,
            ty,
            14,
            Grey.TEXT);
        c.text(d.run() + "/" + blocks(d.reach()), col[4], ty, 14, d.run() > d.reach() ? 0xa02818 : Grey.TEXT);
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
            case NO_PORT -> {
                now = t("becom.ui.terminal.now_no_port");
                colour = 0xa02818;
            }
            case TOO_FAR -> {
                now = t("becom.ui.terminal.now_too_far");
                colour = 0xa02818;
            }
            case NEARER -> {
                now = t("becom.ui.terminal.now_nearer");
                colour = 0x8a8a8a;
            }
            default -> {
                now = d.data() > 0 ? t("becom.ui.terminal.now_data", d.data()) : t("becom.ui.terminal.now_idle");
                colour = d.data() > 0 ? 0x1f6f5f : Grey.FAINT;
            }
        }
        c.text(Paint.fit(c, now, 14, 112 + 20), col[5], ty, 14, colour);
    }

    @Override
    public void scroll(int amount) {
        page = Math.max(0, page - Integer.signum(amount));
    }
}
