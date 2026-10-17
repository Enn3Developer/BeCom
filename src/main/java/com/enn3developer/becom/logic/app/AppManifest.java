package com.enn3developer.becom.logic.app;

import java.util.Locale;

/**
 * What a Lua app says about itself, in comment lines at the top of its one file:
 *
 * <pre>
 * --@name Reactor
 * --@version 1.2
 * --@accent 2a8a4a
 * --@icon 0000000000111100...   (64 hex digits: 8 by 8, a palette colour each, 0 for none)
 * --@about Keeps the hull under 50%.
 * --@uses net, notify, storage
 * </pre>
 *
 * Only the leading comment block counts. An app without a name isn't one. {@code uses} is what the app may do beyond
 * drawing its screen, a bit per {@link Use}: one that doesn't say gets none of it.
 */
public record AppManifest(String id, String name, String version, int accent, String icon, String about, int uses) {

    /** What an app may ask for. Ordinals are saved bits and go on the wire, so constants are only ever appended. */
    public enum Use {

        /** Send data by number, and receive it. */
        NET,
        /** Show a notification. */
        NOTIFY,
        /** Keep files in its own folder on the phone. */
        STORAGE,
        /** Place a call while it's on screen. */
        CALL,
        /** See what the holder looks at while it's on screen. */
        CAMERA;

        public static final Use[] VALUES = values();

        public int bit() {
            return 1 << ordinal();
        }

        /** The word in a {@code --@uses} line. */
        public String word() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final int MAX_SOURCE = 64 * 1024;
    public static final int ICON_CELLS = 64;
    private static final int DEFAULT_ACCENT = 0x5a5d62;

    /** The manifest of {@code source}, or null when it names no app. */
    public static AppManifest parse(String source) {
        if (source == null) return null;
        String name = "";
        String version = "1.0";
        int accent = DEFAULT_ACCENT;
        String icon = "";
        String about = "";
        int uses = 0;
        for (String raw : source.split("\n", 64)) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            if (!line.startsWith("--")) break;
            if (!line.startsWith("--@")) continue;
            int space = line.indexOf(' ');
            String key = (space < 0 ? line.substring(3) : line.substring(3, space)).toLowerCase(Locale.ROOT);
            String value = space < 0 ? ""
                : line.substring(space + 1)
                    .strip();
            switch (key) {
                case "name" -> name = AppTree.cap(value, 24);
                case "version" -> version = AppTree.cap(value, 12);
                case "accent" -> accent = colour(value, accent);
                case "icon" -> icon = icon(value);
                case "about" -> about = AppTree.cap(value, AppTree.MAX_TEXT);
                case "uses" -> uses |= uses(value);
                default -> {}
            }
        }
        if (name.isEmpty()) return null;
        return new AppManifest(idOf(name), name, version, accent, icon, about, uses);
    }

    public boolean uses(Use use) {
        return (uses & use.bit()) != 0;
    }

    /** The bits of the words in a {@code --@uses} line, commas or spaces between; unknown words are left out. */
    static int uses(String line) {
        int bits = 0;
        for (String word : line.toLowerCase(Locale.ROOT)
            .split("[,\\s]+")) {
            for (Use use : Use.VALUES) if (use.word()
                .equals(word)) bits |= use.bit();
        }
        return bits;
    }

    /** The id an app is installed under: its name, lower case, letters and digits only. */
    public static String idOf(String name) {
        StringBuilder id = new StringBuilder();
        for (char c : name.toLowerCase(Locale.ROOT)
            .toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) id.append(c);
            else if (id.length() > 0 && id.charAt(id.length() - 1) != '_') id.append('_');
        }
        while (id.length() > 0 && id.charAt(id.length() - 1) == '_') id.setLength(id.length() - 1);
        return id.length() == 0 ? "app" : id.toString();
    }

    private static int colour(String hex, int fallback) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() != 6) return fallback;
        try {
            return Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String icon(String hex) {
        String h = hex.toLowerCase(Locale.ROOT);
        if (h.length() != ICON_CELLS) return "";
        for (char c : h.toCharArray()) if (Character.digit(c, 16) < 0) return "";
        return h;
    }
}
