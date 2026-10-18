package com.enn3developer.becom.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;

import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.radio.RadioModel;
import com.enn3developer.becom.packet.AnalyzerPacket;

/**
 * A reading's terms as the Signal Analyzer words and draws them, for its overlay and the phone's Signal app alike:
 * each loss's name, colours and icon, the losses biggest first, what serves the reading and where, and how far off the
 * next level is.
 */
public final class SignalTerms {

    // By RadioModel.Loss ordinal (distance, terrain, cover, weather, noise): a bar's colour and, lighter, its text's.
    public static final int[] LOSS_BAR = { 0xFFB4442E, 0xFF8A6A3A, 0xFF6B6F78, 0xFF3F6FB0, 0xFF7A4FA0 };
    public static final int[] LOSS_TEXT = { 0xFFFF6B55, 0xFFC9A46A, 0xFFA9ADB6, 0xFF7FA8E0, 0xFFB48ADC };
    // Each loss's 8 by 8 icon, a row a string, '#' for a lit pixel.
    public static final String[][] LOSS_ICON = {
        { "        ", "        ", "#      #", "########", "########", "#      #", "        ", "        " },
        { "        ", "   ##   ", "  ####  ", "  ####  ", " ###### ", " ###### ", "########", "        " },
        { "   ##   ", "  ####  ", " ###### ", "########", " #    # ", " #    # ", " ###### ", "        " },
        { "  ###   ", " #####  ", "########", "########", "        ", " #  #  #", "#  #  # ", "        " },
        { "        ", "        ", "#   #   ", " # # # #", "  #   # ", "        ", "        ", "        " } };
    public static final int ICON = 8;
    // The way to the antenna, turned to where the player looks: ahead, then clockwise.
    private static final String[] ARROWS = { "↑", "↗", "→", "↘", "↓", "↙", "←", "↖" };

    private SignalTerms() {}

    /** Every loss, the biggest first. */
    public static List<RadioModel.Loss> byBiggest(RadioModel.Losses losses) {
        List<RadioModel.Loss> all = new ArrayList<>(List.of(RadioModel.Loss.values()));
        all.sort((a, b) -> Double.compare(losses.of(b), losses.of(a)));
        return all;
    }

    /** A loss's name for the middle of a line: "distance". */
    public static String lossName(RadioModel.Loss loss) {
        return StatCollector.translateToLocal(
            "becom.loss." + loss.name()
                .toLowerCase(Locale.ROOT));
    }

    /** A loss's name as a term's label: "Distance". */
    public static String lossLabel(RadioModel.Loss loss) {
        return capitalised(lossName(loss));
    }

    /** Words for the middle of a line made to start one. */
    public static String capitalised(String s) {
        return s.isEmpty() ? s
            : s.substring(0, 1)
                .toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /** What serves the reading: "T2 antenna", or the satellites. */
    public static String source(AnalyzerPacket.Reading r) {
        return r.sky() ? StatCollector.translateToLocal("becom.analyzer.source_sky")
            : StatCollector.translateToLocalFormatted("becom.analyzer.source_antenna", r.tier());
    }

    /** {@link #away(AnalyzerPacket.Reading, EntityPlayer)} from this client's player. */
    public static String away(AnalyzerPacket.Reading r) {
        return away(r, Minecraft.getMinecraft().thePlayer);
    }

    /** How far the serving antenna is from the player, and an arrow its way from where they look: "130 blocks ↗". */
    public static String away(AnalyzerPacket.Reading r, EntityPlayer player) {
        double dx = r.x() + 0.5 - player.posX, dz = r.z() + 0.5 - player.posZ;
        // Minecraft's yaw turns clockwise from south; the player looks along (-sin yaw, cos yaw).
        double toward = Math.toDegrees(Math.atan2(-dx, dz)) - player.rotationYaw;
        String arrow = ARROWS[Math.floorMod((int) Math.round(MathHelper.wrapAngleTo180_double(toward) / 45), 8)];
        return StatCollector
            .translateToLocalFormatted("becom.analyzer.away", Math.round(Math.sqrt(dx * dx + dz * dz)), arrow);
    }

    /** How far off the next level the reading is, "Medium at 12 dB: 2 dB short", or null at Max. */
    public static String shortOf(AnalyzerPacket.Reading r) {
        if (r.level() == SignalLevel.MAX) return null;
        SignalLevel next = r.level()
            .up();
        double threshold = threshold(BeComConfig.get(), next);
        long shortBy = (long) Math.ceil(
            threshold - r.losses()
                .snr());
        if (shortBy <= 0) return null;
        return StatCollector
            .translateToLocalFormatted("becom.analyzer.short", levelName(next), Math.round(threshold), shortBy);
    }

    /** A level's name: "Medium". */
    public static String levelName(SignalLevel level) {
        return StatCollector.translateToLocal(
            "becom.level." + level.name()
                .toLowerCase(Locale.ROOT));
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
}
