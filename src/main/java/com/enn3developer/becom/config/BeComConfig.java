package com.enn3developer.becom.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.Tunables;

/**
 * BeCom's Forge config file (DESIGN.md 14.2): every tunable from DESIGN.md 17 except the three kept fixed, read once at
 * startup. Clients never act on their own copy; whatever they need comes from the server.
 */
public final class BeComConfig {

    /** Bumped when a key is renamed or its meaning changes, so an older file can be upgraded as it loads. */
    public static final int FORMAT_VERSION = 1;

    private static final String GENERAL = "general";
    private static final String SIGNAL = "signal";
    private static final String WIRED = "wired";
    private static final String CALLS = "calls";
    private static final String QUESTS = "quests";

    private static volatile Tunables tunables = Tunables.defaults();

    private BeComConfig() {}

    public static Tunables get() {
        return tunables;
    }

    /** For tests and the self-test: replaces the loaded values. */
    public static void set(Tunables values) {
        tunables = values;
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
            throw new IllegalStateException(
                "BeCom's config " + file
                    + " has format "
                    + version
                    + ", newer than this BeCom understands ("
                    + FORMAT_VERSION
                    + "). Update BeCom, or remove the file to start from the defaults.");
        }
        config.get(GENERAL, "formatVersion", FORMAT_VERSION)
            .set(FORMAT_VERSION);

        Tunables d = Tunables.defaults();
        tunables = new Tunables(
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
            integer(config, WIRED, "pylonMaxHeight", d.pylonMaxHeight(), "Tallest pylon, in frame boxes."),
            dbl(config, WIRED, "pylonSpanFactor", d.pylonSpanFactor(), "Span limit, times the shorter pylon's height."),
            integer(config, WIRED, "pylonClearanceSeconds", d.pylonClearanceSeconds(), "Seconds between line checks."),
            dbl(config, WIRED, "lineBudget", d.lineBudget(), "dB every powered node drives the line at."),
            dbl(config, WIRED, "lineLossPerBlock", d.lineLossPerBlock(), "dB the line loses per block of span."),
            integer(
                config,
                WIRED,
                "phoneCableLength",
                d.phoneCableLength(),
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
        validate(tunables);
        if (config.hasChanged()) config.save();
        BeCom.LOG.info("Loaded config {}", file.getName());
    }

    private static void validate(Tunables t) {
        try {
            t.validate();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("BeCom config: " + e.getMessage(), e);
        }
    }

    private static double dbl(Configuration config, String category, String key, double def, String comment) {
        return config.get(category, key, def, comment, -1000, 1000)
            .getDouble(def);
    }

    private static int integer(Configuration config, String category, String key, int def, String comment) {
        return config.getInt(key, category, def, 0, Integer.MAX_VALUE, comment);
    }
}
