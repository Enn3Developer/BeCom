package com.enn3developer.becom.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.StatCollector;

/**
 * Tooltip lines as on the Tooltips board: short, a grey label before a white value, on and off in green and red, a
 * battery's percent in green, yellow or red, numbers that matter highlighted, and how-to text in dim grey.
 */
public final class Tips {

    private static final String LABEL = "§7";
    private static final String VALUE = "§f";
    private static final String HOW_TO = "§8";
    private static final String NUMBER = "§b";
    private static final String POWER = "§e";

    /** The letters a line holds before it wraps, as the Tooltips board's boxes wrap them: an item's. */
    public static final int ITEM = 36;
    /** And a machine's, in its wider box. */
    public static final int MACHINE = 44;

    private Tips() {}

    private static String t(String key, Object... args) {
        return args.length == 0 ? StatCollector.translateToLocal(key)
            : StatCollector.translateToLocalFormatted(key, args);
    }

    /** "Number 482913": a grey label, a white value. */
    public static String row(String labelKey, Object value) {
        return LABEL + t(labelKey) + " " + VALUE + value;
    }

    /** "On" in green or "Off" in red, then grey words after it, such as " · Channel 4". */
    public static String state(boolean on, String after) {
        return (on ? "§a" + t("becom.tip.on") : "§c" + t("becom.tip.off")) + LABEL + " · " + after;
    }

    /** "Battery 61%", the percent green above half, yellow above a fifth, else red, and "charge it" when empty. */
    public static String battery(double charge, double max) {
        int percent = max <= 0 ? 0 : (int) Math.round(100 * Math.max(0, Math.min(1, charge / max)));
        String colour = percent > 50 ? "§a" : percent > 20 ? "§e" : "§c";
        String line = LABEL + t("becom.tip.battery") + " " + colour + percent + "%";
        return percent == 0 ? line + LABEL + t("becom.tip.charge_it") : line;
    }

    /** How to use it, in dim grey. */
    public static String howTo(String key, Object... args) {
        return HOW_TO + t(key, args);
    }

    /** A value inside a grey line, white, and back to grey after: the "4" of "On · Channel 4". */
    public static String value(Object value) {
        return VALUE + value + LABEL;
    }

    /** A number that matters, highlighted, and back to the grey of GT's info lines after. */
    public static String number(Object value) {
        return NUMBER + value + LABEL;
    }

    /**
     * The lines wrapped at spaces to at most {@code letters} letters each, as the Tooltips board's boxes wrap them, so
     * a
     * long one never runs off the screen. A wrapped line goes on in the colour it broke in.
     */
    public static String[] wrap(int letters, String... lines) {
        List<String> wrapped = new ArrayList<>();
        for (String text : lines) {
            StringBuilder line = new StringBuilder();
            int length = 0;
            for (String word : text.split(" ")) {
                int visible = word.replaceAll("§.", "")
                    .length();
                if (length > 0 && length + 1 + visible > letters) {
                    String done = line.toString();
                    wrapped.add(done);
                    line = new StringBuilder(formatAtEnd(done));
                    length = 0;
                }
                if (length > 0) {
                    line.append(' ');
                    length++;
                }
                line.append(word);
                length += visible;
            }
            wrapped.add(line.toString());
        }
        return wrapped.toArray(new String[0]);
    }

    /** The formatting codes in force at the end of {@code text}: its last colour and the styles after it. */
    private static String formatAtEnd(String text) {
        StringBuilder format = new StringBuilder();
        for (int i = text.indexOf('§'); i >= 0 && i + 1 < text.length(); i = text.indexOf('§', i + 2)) {
            char code = Character.toLowerCase(text.charAt(i + 1));
            if (code == 'r') format.setLength(0);
            else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) format.setLength(0);
            if (code != 'r') format.append('§')
                .append(code);
        }
        return format.toString();
    }

    /** "Uses 128 EU/t at MV while working", the power and voltage highlighted. */
    public static String power(long eut, String voltage) {
        return t("becom.machine.power", POWER + eut + " EU/t" + LABEL, POWER + voltage + LABEL);
    }
}
