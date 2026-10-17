package com.enn3developer.becom.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.item.ItemSignalAnalyzer;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.radio.RadioModel;
import com.enn3developer.becom.packet.AnalyzerPacket;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * The Signal Analyzer's overlay: while the player holds one, the server's latest reading in
 * the top-left corner. At a glance, a line a band: signal bars, the SNR and the biggest loss. Sneaking, every term of
 * each band, a bar each: what the link starts with, what each loss takes off it, and what's left.
 */
public final class AnalyzerOverlay {

    private static final int MARGIN = 4;
    private static final int PAD = 3;
    private static final int LINE = 10;
    private static final int GAP = 4;
    private static final int BAND_GAP = 5;
    private static final int TERM_BAR = 64;
    private static final int BACKGROUND = 0xB0000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int LABEL = 0xFFC8C8C8;
    private static final int GREY = 0xFF9A9A9A;
    private static final int UNLIT = 0xFF4A4A4A;
    private static final int BUDGET = 0xFF5A5A5A;
    // By SignalLevel ordinal: None, Low, Medium, High, Max.
    private static final int[] LEVEL = { 0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF55FF55, 0xFF55FFFF };
    // By RadioModel.Loss ordinal (distance, terrain, cover, weather, noise): a bar's colour and, lighter, its text's.
    private static final int[] LOSS_BAR = { 0xFFB4442E, 0xFF8A6A3A, 0xFF6B6F78, 0xFF3F6FB0, 0xFF7A4FA0 };
    private static final int[] LOSS_TEXT = { 0xFFFF6B55, 0xFFC9A46A, 0xFFA9ADB6, 0xFF7FA8E0, 0xFFB48ADC };
    // Each loss's 8 by 8 icon, drawn a pixel at a time.
    private static final String[][] LOSS_ICON = {
        { "        ", "        ", "#      #", "########", "########", "#      #", "        ", "        " },
        { "        ", "   ##   ", "  ####  ", "  ####  ", " ###### ", " ###### ", "########", "        " },
        { "   ##   ", "  ####  ", " ###### ", "########", " #    # ", " #    # ", " ###### ", "        " },
        { "  ###   ", " #####  ", "########", "########", "        ", " #  #  #", "#  #  # ", "        " },
        { "        ", "        ", "#   #   ", " # # # #", "  #   # ", "        ", "        ", "        " } };
    private static final int ICON = 8;
    // The way to the antenna, turned to where the player looks: ahead, then clockwise.
    private static final String[] ARROWS = { "↑", "↗", "→", "↘", "↓", "↙", "←", "↖" };
    // A reading older than a few refreshes is stale: the server stopped sending, or the player just picked it up.
    private static final int STALE_REFRESHES = 3;

    private static volatile AnalyzerPacket reading;
    private static volatile long readAt;

    public static void set(AnalyzerPacket packet) {
        reading = packet;
        readAt = System.currentTimeMillis();
    }

    public static void clear() {
        reading = null;
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings.showDebugInfo || !ItemSignalAnalyzer.is(mc.thePlayer.getHeldItem()))
            return;
        FontRenderer font = mc.fontRenderer;
        List<Line> lines = new ArrayList<>();
        AnalyzerPacket r = reading;
        long staleAfter = (long) BeComConfig.get()
            .analyzerRefreshSeconds() * 1000 * STALE_REFRESHES;
        if (r == null || System.currentTimeMillis() - readAt > staleAfter) {
            lines.add(new Text(StatCollector.translateToLocal("becom.analyzer.reading"), GREY));
        } else if (mc.thePlayer.isSneaking()) {
            int[] labels = new int[1];
            detail(lines, labels, font, "becom.analyzer.radios_name", "VHF", r.radio(), mc.thePlayer);
            lines.add(new Space(BAND_GAP));
            detail(lines, labels, font, "becom.analyzer.phones_name", "UHF", r.phone(), mc.thePlayer);
        } else {
            int[] columns = new int[3];
            lines.add(summary(columns, font, "becom.analyzer.radios_name", r.radio()));
            lines.add(summary(columns, font, "becom.analyzer.phones_name", r.phone()));
        }
        int width = 0, height = 0;
        for (Line line : lines) {
            width = Math.max(width, line.width(font));
            height += line.height();
        }
        Gui.drawRect(MARGIN - PAD, MARGIN - PAD, MARGIN + width + PAD, MARGIN + height + PAD - 2, BACKGROUND);
        int y = MARGIN;
        for (Line line : lines) {
            line.draw(font, MARGIN, y, width);
            y += line.height();
        }
    }

    // ---- At a glance: one line a band ----

    /** Bars, the band's name, its SNR, and its biggest loss, or what serves it when nothing is lost. */
    private static Line summary(int[] columns, FontRenderer font, String nameKey, AnalyzerPacket.Reading r) {
        String name = StatCollector.translateToLocal(nameKey);
        if (!r.served()) {
            return new Summary(
                columns,
                font,
                0,
                name,
                StatCollector.translateToLocal("becom.analyzer.no_service_short"),
                LEVEL[0],
                null,
                "",
                0);
        }
        RadioModel.Losses losses = r.losses();
        int level = r.level()
            .ordinal();
        String snr = StatCollector.translateToLocalFormatted("becom.analyzer.snr", Math.round(losses.snr()));
        RadioModel.Loss biggest = losses.biggest();
        long lost = Math.round(losses.of(biggest));
        if (lost > 0) {
            return new Summary(
                columns,
                font,
                level,
                name,
                snr,
                LEVEL[level],
                biggest,
                lossName(biggest) + " -" + lost,
                LOSS_TEXT[biggest.ordinal()]);
        }
        return new Summary(columns, font, level, name, snr, LEVEL[level], null, source(r), GREY);
    }

    private static String source(AnalyzerPacket.Reading r) {
        return r.sky() ? StatCollector.translateToLocal("becom.analyzer.source_sky")
            : StatCollector.translateToLocalFormatted("becom.analyzer.source_antenna", r.tier());
    }

    private static final class Summary implements Line {

        private final int[] columns;
        private final int lit;
        private final String name, snr, extra;
        private final int snrColour, extraColour;
        private final RadioModel.Loss icon;

        Summary(int[] columns, FontRenderer font, int lit, String name, String snr, int snrColour, RadioModel.Loss icon,
            String extra, int extraColour) {
            this.columns = columns;
            this.lit = lit;
            this.name = name;
            this.snr = snr;
            this.snrColour = snrColour;
            this.icon = icon;
            this.extra = extra;
            this.extraColour = extraColour;
            columns[0] = Math.max(columns[0], font.getStringWidth(name));
            columns[1] = Math.max(columns[1], font.getStringWidth(snr));
            columns[2] = Math.max(columns[2], (icon == null ? 0 : ICON + 2) + font.getStringWidth(extra));
        }

        @Override
        public int width(FontRenderer font) {
            return Bars.WIDTH + GAP + columns[0] + GAP + columns[1] + GAP + columns[2];
        }

        @Override
        public void draw(FontRenderer font, int x, int y, int width) {
            Bars.draw(x, y, lit, snrColour);
            x += Bars.WIDTH + GAP;
            font.drawStringWithShadow(name, x, y, WHITE);
            x += columns[0] + GAP;
            font.drawStringWithShadow(snr, x, y, snrColour);
            x += columns[1] + GAP;
            if (icon != null) {
                icon(icon, x, y, extraColour);
                x += ICON + 2;
            }
            font.drawStringWithShadow(extra, x, y, extraColour);
        }
    }

    // ---- Sneaking: every term ----

    /**
     * A band's heading with its bars and SNR, what serves it and how far away, then a bar a term: what the link starts
     * with, each loss it suffers, and what's left at the level it makes; last, how far off the next level is.
     */
    private static void detail(List<Line> lines, int[] labels, FontRenderer font, String nameKey, String band,
        AnalyzerPacket.Reading r, EntityPlayer player) {
        String name = StatCollector.translateToLocal(nameKey);
        if (!r.served()) {
            lines.add(new Heading(name, band, 0, LEVEL[0], ""));
            lines.add(new Text(StatCollector.translateToLocal("becom.analyzer.no_service"), LEVEL[0]));
            return;
        }
        RadioModel.Losses losses = r.losses();
        SignalLevel level = r.level();
        int colour = LEVEL[level.ordinal()];
        lines.add(
            new Heading(
                name,
                band,
                level.ordinal(),
                colour,
                StatCollector.translateToLocalFormatted("becom.analyzer.snr", Math.round(losses.snr()))));
        if (r.sky()) {
            lines.add(
                new Text(StatCollector.translateToLocalFormatted("becom.analyzer.overhead", r.satellites()), GREY));
        } else {
            double dx = r.x() + 0.5 - player.posX, dz = r.z() + 0.5 - player.posZ;
            // Minecraft's yaw turns clockwise from south; the player looks along (-sin yaw, cos yaw).
            double toward = Math.toDegrees(Math.atan2(-dx, dz)) - player.rotationYaw;
            String arrow = ARROWS[Math.floorMod((int) Math.round(MathHelper.wrapAngleTo180_double(toward) / 45), 8)];
            lines.add(
                new Text(
                    source(r) + " · "
                        + StatCollector.translateToLocalFormatted(
                            "becom.analyzer.away",
                            Math.round(Math.sqrt(dx * dx + dz * dz)),
                            arrow),
                    GREY));
        }
        double budget = losses.budget();
        lines.add(
            new Term(
                labels,
                font,
                null,
                StatCollector.translateToLocal(r.sky() ? "becom.analyzer.sky_label" : "becom.analyzer.budget_label"),
                LABEL,
                1,
                BUDGET,
                Math.round(budget)));
        boolean lost = false;
        for (RadioModel.Loss loss : byBiggest(losses)) {
            long dB = Math.round(losses.of(loss));
            if (dB <= 0) continue;
            lost = true;
            int i = loss.ordinal();
            lines.add(
                new Term(
                    labels,
                    font,
                    loss,
                    capitalised(lossName(loss)),
                    LOSS_TEXT[i],
                    losses.of(loss) / budget,
                    LOSS_BAR[i],
                    -dB));
        }
        String levelName = StatCollector.translateToLocal(
            "becom.level." + level.name()
                .toLowerCase(Locale.ROOT));
        lines.add(
            new Term(
                labels,
                font,
                null,
                "= " + levelName,
                colour,
                Math.max(0, losses.snr()) / budget,
                colour,
                Math.round(losses.snr())));
        if (!lost) lines.add(new Text(StatCollector.translateToLocal("becom.analyzer.biggest_none"), GREY));
        if (level != SignalLevel.MAX) {
            SignalLevel next = level.up();
            double threshold = threshold(BeComConfig.get(), next);
            long shortBy = (long) Math.ceil(threshold - losses.snr());
            if (shortBy > 0) {
                lines.add(
                    new Text(
                        StatCollector.translateToLocalFormatted(
                            "becom.analyzer.short",
                            StatCollector.translateToLocal(
                                "becom.level." + next.name()
                                    .toLowerCase(Locale.ROOT)),
                            Math.round(threshold),
                            shortBy),
                        GREY));
            }
        }
    }

    private static List<RadioModel.Loss> byBiggest(RadioModel.Losses losses) {
        List<RadioModel.Loss> all = new ArrayList<>(List.of(RadioModel.Loss.values()));
        all.sort((a, b) -> Double.compare(losses.of(b), losses.of(a)));
        return all;
    }

    private static double threshold(Tunables t, SignalLevel level) {
        return switch (level) {
            case NONE -> 0;
            case LOW -> t.levelLow();
            case MEDIUM -> t.levelMedium();
            case HIGH -> t.levelHigh();
            case MAX -> t.levelMax();
        };
    }

    private static String lossName(RadioModel.Loss loss) {
        return StatCollector.translateToLocal(
            "becom.loss." + loss.name()
                .toLowerCase(Locale.ROOT));
    }

    private static String capitalised(String s) {
        return s.isEmpty() ? s
            : s.substring(0, 1)
                .toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /** A band's name and tag, then its bars and SNR. */
    private static final class Heading implements Line {

        private final String name, band, snr;
        private final int lit, colour;

        Heading(String name, String band, int lit, int colour, String snr) {
            this.name = name;
            this.band = band;
            this.lit = lit;
            this.colour = colour;
            this.snr = snr;
        }

        @Override
        public int width(FontRenderer font) {
            return font.getStringWidth(name + " " + band) + GAP + Bars.WIDTH + GAP + font.getStringWidth(snr);
        }

        @Override
        public void draw(FontRenderer font, int x, int y, int width) {
            font.drawStringWithShadow(name, x, y, WHITE);
            x += font.getStringWidth(name + " ");
            font.drawStringWithShadow(band, x, y, GREY);
            x += font.getStringWidth(band) + GAP;
            Bars.draw(x, y, lit, colour);
            font.drawStringWithShadow(snr, x + Bars.WIDTH + GAP, y, colour);
        }
    }

    /** One term: its icon, its name, a bar for its share of what the link starts with, and its dB. */
    private static final class Term implements Line {

        private static final int VALUE = 20;
        private final int[] labels;
        private final RadioModel.Loss icon;
        private final String label;
        private final int labelColour, barColour;
        private final double share;
        private final String value;

        Term(int[] labels, FontRenderer font, RadioModel.Loss icon, String label, int labelColour, double share,
            int barColour, long value) {
            this.labels = labels;
            this.icon = icon;
            this.label = label;
            this.labelColour = labelColour;
            this.share = Math.min(1, share);
            this.barColour = barColour;
            this.value = String.valueOf(value);
            labels[0] = Math.max(labels[0], font.getStringWidth(label));
        }

        @Override
        public int width(FontRenderer font) {
            return ICON + GAP + labels[0] + GAP + TERM_BAR + GAP + VALUE;
        }

        @Override
        public void draw(FontRenderer font, int x, int y, int width) {
            if (icon != null) icon(icon, x, y, labelColour);
            x += ICON + GAP;
            font.drawStringWithShadow(label, x, y, labelColour);
            x += labels[0] + GAP;
            int filled = (int) Math.round(TERM_BAR * share);
            Gui.drawRect(x, y + 2, x + filled, y + 6, barColour);
            x += TERM_BAR + GAP;
            font.drawStringWithShadow(value, x + VALUE - font.getStringWidth(value), y, labelColour);
        }
    }

    // ---- Shared pieces ----

    private interface Line {

        int width(FontRenderer font);

        default int height() {
            return LINE;
        }

        void draw(FontRenderer font, int x, int y, int width);
    }

    private record Text(String text, int colour) implements Line {

        @Override
        public int width(FontRenderer font) {
            return font.getStringWidth(text);
        }

        @Override
        public void draw(FontRenderer font, int x, int y, int width) {
            font.drawStringWithShadow(text, x, y, colour);
        }
    }

    private record Space(int height) implements Line {

        @Override
        public int width(FontRenderer font) {
            return 0;
        }

        @Override
        public void draw(FontRenderer font, int x, int y, int width) {}
    }

    /** Four rising bars, as many lit as the level is above None. */
    private static final class Bars {

        static final int WIDTH = 4 * 2 + 3;

        static void draw(int x, int y, int lit, int colour) {
            for (int i = 0; i < 4; i++) {
                int height = 2 + 2 * i;
                Gui.drawRect(x + 3 * i, y + 8 - height, x + 3 * i + 2, y + 8, i < lit ? colour : UNLIT);
            }
        }
    }

    private static void icon(RadioModel.Loss loss, int x, int y, int colour) {
        String[] rows = LOSS_ICON[loss.ordinal()];
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length(); col++) {
                if (rows[row].charAt(col) == '#') Gui.drawRect(x + col, y + row, x + col + 1, y + row + 1, colour);
            }
        }
    }
}
