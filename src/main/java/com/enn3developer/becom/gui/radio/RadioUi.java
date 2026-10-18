package com.enn3developer.becom.gui.radio;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import net.minecraft.util.StatCollector;

import com.enn3developer.becom.gui.paint.Canvas;
import com.enn3developer.becom.gui.paint.CanvasScreen;
import com.enn3developer.becom.gui.paint.Paint;

/**
 * The Walkie-Talkie's and the Transceiver's screens, from the Radios board: a grey panel with the channel on a dark
 * display between two arrows, its TX and RX lights, one switch (Power or Loudspeaker), and under it the walkie's
 * battery or the Transceiver's ranges.
 */
public final class RadioUi extends CanvasScreen {

    private static final float WIDTH = 324;
    private static final float X0 = 16;
    private static final float INNER = 292;
    private static final int INK = 0x404040;
    private static final int LABEL = 0x303030;
    private static final int NOTE = 0x5a5a5a;
    private static final int DISPLAY = 0x101418;
    private static final float SCALE = 0.75f;

    /**
     * What the screen shows: for a walkie its battery and no ranges, for a Transceiver its three ranges (reach, talk
     * within, speaker) and no battery.
     */
    public record State(int channel, boolean on, boolean toggle, boolean tx, boolean rx, int battery,
        List<String> ranges, String note) {}

    private final boolean walkie;
    private final Supplier<State> state;
    private final IntConsumer step;
    private final java.util.function.Consumer<Boolean> toggle;

    /**
     * @param step   asks for the next ({@code 1}) or previous ({@code -1}) channel
     * @param toggle asks for Power or Loudspeaker on or off
     */
    public RadioUi(boolean walkie, Supplier<State> state, IntConsumer step,
        java.util.function.Consumer<Boolean> toggle) {
        this.walkie = walkie;
        this.state = state;
        this.step = step;
        this.toggle = toggle;
    }

    public static float scale(int guiHeight) {
        return guiHeight <= 0 ? SCALE : Math.min(SCALE, (guiHeight - 8) / 290f);
    }

    @Override
    public float width() {
        return WIDTH;
    }

    @Override
    public float height() {
        return walkie ? 288 : 282;
    }

    private static String t(String key, Object... args) {
        return args.length == 0 ? StatCollector.translateToLocal(key)
            : StatCollector.translateToLocalFormatted(key, args);
    }

    @Override
    protected void paint(Canvas c) {
        State s = state.get();
        float h = height();
        c.rect(0, 0, WIDTH, h, Paint.BLACK);
        Paint.box(c, 2, 2, WIDTH - 4, h - 4, 2, 0xc6c6c6, 0xffffff, 0x555555);
        float y = X0;
        // The title, and on the right whether it works: On or Off, Online or Offline.
        c.text(
            t(walkie ? "item.becom.walkie_talkie.name" : "gt.blockmachines.becom.transceiver.name"),
            X0,
            y + (22 - c.lineHeight(18)) / 2,
            18,
            INK);
        String chip = t(
            walkie ? (s.on() ? "becom.ui.radio.on" : "becom.ui.radio.off")
                : (s.on() ? "becom.ui.radio.online" : "becom.ui.radio.offline"));
        float cw = c.width(chip, 13) + 12;
        float cx = X0 + INNER - cw;
        c.rect(cx, y + 3, cw, 17, s.on() ? 0x3d8a3d : walkie ? 0x6a6a6a : 0xa8342a);
        Paint.shadowed(c, chip, cx + 6, y + 3 + (17 - c.lineHeight(13)) / 2, 13, Paint.WHITE, 0x2a2a2a);
        y += 22 + 10;
        paintChannel(c, s, y);
        y += 80 + 10;
        // The switch.
        c.text(
            t(walkie ? "becom.ui.radio.power" : "becom.ui.radio.loudspeaker"),
            X0,
            y + (30 - c.lineHeight(15)) / 2,
            15,
            LABEL);
        float px = X0 + INNER - 44;
        float py = y + 5;
        Paint.box(c, px, py, 44, 20, 2, s.toggle() ? 0x3d8a3d : 0x8b8b8b, 0x3a3a3a, 0xdbdbdb);
        float kx = s.toggle() ? px + 44 - 4 - 14 : px + 4;
        c.rect(kx, py + 4, 14, 12, 0x3a3a3a);
        c.rect(kx + 1, py + 5, 12, 10, 0xdbdbdb);
        boolean now = s.toggle();
        hit(X0, y, INNER, 30, () -> toggle.accept(!now));
        y += 30 + 10;
        if (walkie) {
            float ty = y;
            c.text(t("becom.ui.radio.battery"), X0, ty, 14, INK);
            Paint.right(c, s.battery() + "%", X0 + INNER, ty, 14, INK);
            y += c.lineHeight(14) + 4;
            Paint.box(c, X0, y, INNER, 12, 2, DISPLAY, 0x373737, 0xffffff);
            c.rect(
                X0 + 3,
                y + 3,
                (INNER - 6) * Math.max(0, Math.min(100, s.battery())) / 100f,
                6,
                s.battery() <= 5 ? Paint.RED : Paint.GREEN);
            y += 12 + 10;
        } else {
            String[] names = { t("becom.ui.radio.reach"), t("becom.ui.radio.talk_within"),
                t("becom.ui.radio.speaker") };
            float cell = (INNER - 12) / 3;
            for (int i = 0; i < 3; i++) {
                float x = X0 + i * (cell + 6);
                Paint.box(c, x, y, cell, 42, 2, DISPLAY, 0x373737, 0xffffff);
                c.text(names[i], x + 8, y + 6, 11, Paint.GREY);
                String value = i < s.ranges()
                    .size() ? s.ranges()
                        .get(i) : "";
                c.text(value, x + 8, y + 6 + c.lineHeight(11) + 4, 15, Paint.TEXT);
            }
            y += 42 + 10;
        }
        for (String line : Paint.wrap(c, s.note(), 13, INNER)) {
            c.text(line, X0, y, 13, NOTE);
            y += 17;
        }
    }

    /** The arrows either side of the display, which shows the channel and the TX and RX lights. */
    private void paintChannel(Canvas c, State s, float y) {
        arrow(c, X0, y, "<", -1);
        arrow(c, X0 + INNER - 32, y, ">", 1);
        float dx = X0 + 32 + 8;
        float dw = INNER - 2 * (32 + 8);
        Paint.box(c, dx, y, dw, 80, 2, DISPLAY, 0x373737, 0xffffff);
        c.text(t("becom.ui.radio.channel"), dx + 14, y + 12, 13, Paint.GREY);
        c.text(String.format("%02d", s.channel()), dx + 14, y + 12 + c.lineHeight(13) + 6, 44, Paint.GREEN);
        float lx = dx + dw - 14 - 40;
        light(c, lx, y + 22, "TX", s.tx(), Paint.RED);
        light(c, lx, y + 46, "RX", s.rx(), Paint.GREEN);
    }

    private void arrow(Canvas c, float x, float y, String label, int delta) {
        Paint.box(c, x, y, 32, 80, 2, hover(x, y, 32, 80) ? 0x9b9b9b : 0x8b8b8b, 0xdbdbdb, 0x3a3a3a);
        float ty = y + (80 - c.lineHeight(16)) / 2;
        Paint.centered(c, label, x + 16 + 2, ty + 2, 16, 0x3f3f3f);
        Paint.centered(c, label, x + 16, ty, 16, Paint.WHITE);
        hit(x, y, 32, 80, () -> step.accept(delta));
    }

    private static void light(Canvas c, float x, float y, String name, boolean lit, int color) {
        c.rect(x, y + 3, 8, 8, lit ? color : 0x2a3038);
        c.text(name, x + 14, y, 14, lit ? color : 0x4a4a4a);
    }

    @Override
    public void scroll(int amount) {
        step.accept(amount > 0 ? 1 : -1);
    }
}
