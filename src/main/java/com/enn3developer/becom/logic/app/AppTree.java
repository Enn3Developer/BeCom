package com.enn3developer.becom.logic.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What an app shows (the phone app kit): never pixels, a list of widgets the phone draws in BeCom's style. Lua apps
 * send it as a table, read here from what OpenComputers hands Java: a table of widget tables, each with a {@code type}.
 * A {@code list} widget's rows join the list in its place; the phone pages a long list itself. Anything malformed is
 * left out rather than failing the app, and every string and the widget count are capped, since a phone draws what
 * any app sends.
 */
public final class AppTree {

    public static final int MAX_WIDGETS = 128;
    public static final int MAX_TEXT = 64;
    public static final int MAX_ID = 32;

    private AppTree() {}

    /** How a value or a bar is coloured: plain, or a signal level's colour. */
    public enum Level {

        PLAIN,
        LOW,
        MEDIUM,
        HIGH,
        MAX;

        private static final Level[] VALUES = values();

        public static Level byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : PLAIN;
        }

        static Level of(Object o) {
            if (!(o instanceof String s)) return PLAIN;
            for (Level level : VALUES) if (level.name()
                .equalsIgnoreCase(s)) return level;
            return PLAIN;
        }
    }

    /** A button's look. */
    public enum Style {

        PLAIN,
        GO,
        DANGER;

        private static final Style[] VALUES = values();

        public static Style byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : PLAIN;
        }

        static Style of(Object o) {
            if (!(o instanceof String s)) return PLAIN;
            for (Style style : VALUES) if (style.name()
                .equalsIgnoreCase(s)) return style;
            return PLAIN;
        }
    }

    /** Ordinals go on the wire, so constants are only ever appended. */
    public enum Kind {

        HEADING,
        TEXT,
        VALUE,
        PROGRESS,
        TOGGLE,
        BUTTON,
        ITEM,
        INPUT;

        private static final Kind[] VALUES = values();

        public static Kind byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : TEXT;
        }
    }

    /**
     * One widget, flat: {@code id} names what it sends back (toggles, buttons and inputs), {@code label} is its text,
     * {@code value} a value's text, a toggle's "true" or "false", an item's name or an input's hint, {@code fraction}
     * a progress bar's fill, and {@code extra} an item's count or value.
     */
    public record Widget(Kind kind, String id, String label, String value, double fraction, Level level, Style style,
        String extra) {

        public static Widget text(Kind kind, String label) {
            return new Widget(kind, "", label, "", 0, Level.PLAIN, Style.PLAIN, "");
        }
    }

    /** The widgets in a Lua table of widget tables, in order, with lists flattened. */
    public static List<Widget> parse(Object tree) {
        List<Widget> widgets = new ArrayList<>();
        if (tree instanceof Map<?, ?>map) add(widgets, map, 0, 0);
        return Collections.unmodifiableList(widgets);
    }

    // How deep sequences of sequences go before the rest is dropped: a table holding itself would never end.
    private static final int MAX_NESTING = 4;

    private static void add(List<Widget> widgets, Map<?, ?> table, int depth, int nesting) {
        if (nesting > MAX_NESTING) return;
        // A widget table has a type; anything else is a sequence of them, keyed 1, 2, 3... as Lua sends arrays.
        if (table.get("type") instanceof String) {
            addWidget(widgets, table, depth);
            return;
        }
        for (int i = 1; i <= Math.min(table.size(), MAX_WIDGETS * 4) && widgets.size() < MAX_WIDGETS; i++) {
            Object child = at(table, i);
            if (child instanceof Map<?, ?>map) add(widgets, map, depth, nesting + 1);
        }
    }

    private static void addWidget(List<Widget> widgets, Map<?, ?> t, int depth) {
        if (widgets.size() >= MAX_WIDGETS) return;
        String type = ((String) t.get("type")).toLowerCase(Locale.ROOT);
        switch (type) {
            case "heading" -> widgets.add(Widget.text(Kind.HEADING, str(t.get("text"))));
            case "text" -> widgets.add(Widget.text(Kind.TEXT, str(t.get("text"))));
            case "value" -> widgets.add(
                new Widget(
                    Kind.VALUE,
                    "",
                    str(t.get("label")),
                    str(t.get("value")),
                    0,
                    Level.of(t.get("level")),
                    Style.PLAIN,
                    ""));
            case "progress" -> widgets.add(
                new Widget(
                    Kind.PROGRESS,
                    "",
                    str(t.get("label")),
                    "",
                    clamp(num(t.get("value"))),
                    Level.of(t.get("level")),
                    Style.PLAIN,
                    ""));
            case "toggle" -> widgets.add(
                new Widget(
                    Kind.TOGGLE,
                    id(t.get("id")),
                    str(t.get("label")),
                    String.valueOf(Boolean.TRUE.equals(t.get("on"))),
                    0,
                    Level.PLAIN,
                    Style.PLAIN,
                    ""));
            case "button" -> widgets.add(
                new Widget(
                    Kind.BUTTON,
                    id(t.get("id")),
                    str(t.get("label")),
                    "",
                    0,
                    Level.PLAIN,
                    Style.of(t.get("style")),
                    ""));
            case "item" -> widgets.add(
                new Widget(
                    Kind.ITEM,
                    "",
                    str(t.get("label")),
                    str(t.get("item")),
                    0,
                    Level.PLAIN,
                    Style.PLAIN,
                    str(t.get("value"))));
            case "input" -> widgets
                .add(new Widget(Kind.INPUT, id(t.get("id")), "", str(t.get("hint")), 0, Level.PLAIN, Style.PLAIN, ""));
            case "list" -> {
                // Lists don't nest: a list's rows are widgets, not more lists.
                if (depth == 0 && t.get("rows") instanceof Map<?, ?>rows) add(widgets, rows, depth + 1, 0);
            }
            default -> {}
        }
    }

    /** Lua arrays come through keyed by doubles or integers, whichever the architecture uses. */
    private static Object at(Map<?, ?> table, int i) {
        Object v = table.get(i);
        if (v == null) v = table.get((double) i);
        if (v == null) v = table.get((long) i);
        return v;
    }

    static String str(Object o) {
        if (o == null) return "";
        String s;
        if (o instanceof byte[]bytes) s = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        else if (o instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15) s = String.valueOf(d.longValue());
        else s = String.valueOf(o);
        return cap(s, MAX_TEXT);
    }

    private static String id(Object o) {
        return cap(str(o), MAX_ID);
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static double clamp(double d) {
        return Double.isNaN(d) ? 0 : Math.max(0, Math.min(1, d));
    }

    public static String cap(String s, int max) {
        // No control characters: Minecraft's font draws section signs as formatting.
        StringBuilder out = new StringBuilder(Math.min(s.length(), max));
        for (int i = 0; i < s.length() && out.length() < max; i++) {
            char c = s.charAt(i);
            if (c >= ' ' && c != '§') out.append(c);
        }
        return out.toString();
    }
}
