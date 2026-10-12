package com.enn3developer.becom.config;

import java.io.File;
import java.util.Map;

import net.minecraftforge.common.config.Configuration;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.Tunables;

/**
 * BeCom's Forge config file (DESIGN.md 14.2): every tunable from DESIGN.md 17 except the three kept fixed, read once at
 * startup. Only a server acts on it; clients get the server's values when they log in.
 */
public final class BeComConfig {

    /** Bumped when a key is renamed or its meaning changes, so an older file can be upgraded as it loads. */
    public static final int FORMAT_VERSION = 1;

    private static final String GENERAL = "general";
    private static final String SIGNAL = "signal";
    private static final String WIRED = "wired";
    private static final String CALLS = "calls";
    private static final String QUESTS = "quests";

    // What this side acts on: the file's values while a server runs here, the server's on a client connected to one,
    // and the defaults on a client that isn't (DESIGN.md 14.2: clients never use their own file).
    private static volatile Tunables tunables = Tunables.defaults();
    private static volatile Tunables fileValues = Tunables.defaults();
    private static volatile String fileProblem;

    private BeComConfig() {}

    public static Tunables get() {
        return tunables;
    }

    /** Replaces the values this side acts on: what a server sent this client, or a test's. */
    public static void set(Tunables values) {
        tunables = values;
    }

    /** What's wrong with the config file, for a server about to start with it, or null. */
    public static String fileProblem() {
        return fileProblem;
    }

    /**
     * A server starting here, dedicated or integrated, uses the config file's values.
     *
     * @throws IllegalStateException when a value in the file would crash or overload the server
     */
    public static void useFileValues() {
        if (fileProblem != null) throw new IllegalStateException("BeCom config: " + fileProblem);
        tunables = fileValues;
    }

    /** A client back from a server forgets the server's values. */
    public static void useDefaults() {
        tunables = Tunables.defaults();
    }

    public static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();
        int version = config.getInt(
            "formatVersion",
            GENERAL,
            FORMAT_VERSION,
            0,
            Integer.MAX_VALUE,
            "Format of this file. Don't change it by hand.");
        if (version > FORMAT_VERSION) {
            // Refused when a server starts with it, like any other problem with the file; left untouched meanwhile.
            fileProblem = "the file has format " + version
                + ", newer than this BeCom understands ("
                + FORMAT_VERSION
                + "). Update BeCom, or remove the file to start from the defaults";
            fileValues = Tunables.defaults();
            return;
        }
        config.get(GENERAL, "formatVersion", FORMAT_VERSION)
            .set(FORMAT_VERSION);

        Tunables d = Tunables.defaults();
        Tunables loaded = new Tunables(
            dbl(config, SIGNAL, "levelLow", d.levelLow(), "SNR in dB a link needs for Low."),
            dbl(config, SIGNAL, "levelMedium", d.levelMedium(), "SNR in dB a link needs for Medium."),
            dbl(config, SIGNAL, "levelHigh", d.levelHigh(), "SNR in dB a link needs for High."),
            dbl(config, SIGNAL, "levelMax", d.levelMax(), "SNR in dB a link needs for Max."),
            dbl(
                config,
                SIGNAL,
                "levelHysteresis",
                d.levelHysteresis(),
                "dB below a threshold before a link leaves it."),
            dbl(config, SIGNAL, "dropoutMaxLoss", d.dropoutMaxLoss(), "Share of voice frames lost at the worst."),
            dbl(config, SIGNAL, "dropoutFullLossSnr", d.dropoutFullLossSnr(), "SNR in dB where losses peak."),
            dbl(config, SIGNAL, "dropoutCleanSnr", d.dropoutCleanSnr(), "SNR in dB from which nothing is lost."),
            dbl(config, SIGNAL, "dropoutMeanBurstMillis", d.dropoutMeanBurstMillis(), "Mean burst of losses, in ms."),
            dbl(config, SIGNAL, "dropAfterSeconds", d.dropAfterSeconds(), "Seconds below Low before a link drops."),
            integer(config, WIRED, "pylonMinHeight", d.pylonMinHeight(), "Shortest pylon, in frame boxes."),
            integer(
                config,
                WIRED,
                "pylonMaxHeight",
                d.pylonMaxHeight(),
                1,
                Tunables.MAX_COLUMN,
                "Tallest pylon, in frame boxes."),
            dbl(config, WIRED, "pylonSpanFactor", d.pylonSpanFactor(), "Span limit, times the shorter pylon's height."),
            integer(config, WIRED, "pylonClearanceSeconds", d.pylonClearanceSeconds(), "Seconds between line checks."),
            dbl(config, WIRED, "lineBudget", d.lineBudget(), "dB every powered node drives the line at."),
            dbl(config, WIRED, "lineLossPerBlock", d.lineLossPerBlock(), "dB the line loses per block of span."),
            integer(
                config,
                WIRED,
                "phoneCableLength",
                d.phoneCableLength(),
                1,
                Tunables.MAX_CABLE,
                "Most Phone Cable from phone to terminal."),
            integer(config, WIRED, "terminalPower", d.terminalPower(), "EU/t a Line Terminal draws."),
            integer(config, WIRED, "repeaterPower", d.repeaterPower(), "EU/t a Repeater Head draws."),
            integer(config, WIRED, "routerPower", d.routerPower(), "EU/t a Router Head draws."),
            dbl(
                config,
                CALLS,
                "directHearingRange",
                d.directHearingRange(),
                "Blocks within which listeners hear a speaker directly, not through BeCom."),
            integer(
                config,
                CALLS,
                "sessionGraceSeconds",
                d.sessionGraceSeconds(),
                "Seconds a timed-out voice keeps a call."),
            integer(config, CALLS, "halfLinkSeconds", d.halfLinkSeconds(), "Seconds a half-made wrench link is kept."),
            integer(config, CALLS, "ringSeconds", d.ringSeconds(), "Seconds a phone rings before \"no answer\"."),
            integer(
                config,
                CALLS,
                "callAttemptsPerMinute",
                d.callAttemptsPerMinute(),
                "Call attempts per caller a minute."),
            config.getBoolean(
                "questlineEnabled",
                QUESTS,
                d.questlineEnabled(),
                "Add BeCom's questline to the BetterQuesting book. Turn off for servers with a curated book."),
            integer(
                config,
                QUESTS,
                "questlineRecheckSeconds",
                d.questlineRecheckSeconds(),
                "Seconds between checks that the tab is there."));
        // Checked when a server is about to use them, not here: a client never acts on its own file (DESIGN.md
        // 14.2), so a bad value in it mustn't stop the client from starting.
        fileValues = loaded;
        try {
            loaded.validate();
            fileProblem = null;
        } catch (IllegalArgumentException e) {
            fileProblem = e.getMessage() + " (in " + file.getName() + ")";
            BeCom.LOG.warn("BeCom config: {}. A server won't start with it.", fileProblem);
        }
        if (config.hasChanged()) config.save();
        BeCom.LOG.info("Loaded config {}", file.getName());
    }

    private static double dbl(Configuration config, String category, String key, double def, String comment) {
        return config.get(category, key, def, comment, -1000, 1000)
            .getDouble(def);
    }

    // The smallest value each whole-number option makes sense with, as Tunables.validate checks; the file shows it.
    private static final Map<String, Integer> MINIMUM = Map.of(
        "pylonMinHeight",
        1,
        "pylonClearanceSeconds",
        1,
        "ringSeconds",
        1,
        "callAttemptsPerMinute",
        1,
        "questlineRecheckSeconds",
        1);

    private static int integer(Configuration config, String category, String key, int def, String comment) {
        return integer(config, category, key, def, MINIMUM.getOrDefault(key, 0), Integer.MAX_VALUE, comment);
    }

    /** A whole number in [min, max]; a value outside is used at the nearest end, with a warning naming what was set. */
    private static int integer(Configuration config, String category, String key, int def, int min, int max,
        String comment) {
        // What the file says, read first: Configuration.get without a comment clears the comment getInt writes.
        int written = config.get(category, key, def)
            .getInt(def);
        int value = config.getInt(key, category, def, min, max, comment);
        if (written != value) {
            BeCom.LOG.warn("BeCom config: {} is {}, outside {} to {}; using {}", key, written, min, max, value);
        }
        return value;
    }
}
