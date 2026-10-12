package com.enn3developer.becom.config;

import java.io.File;
import java.util.HashMap;
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
    private static final String RADIO = "radio";
    private static final String ANTENNAS = "antennas";
    private static final String RADIOS = "radios";
    private static final String PHONES = "phones";
    private static final String VIDEO = "video";
    private static final String ME = "me";
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

        Map<String, Object> v = new HashMap<>();
        dbl(config, v, SIGNAL, "levelLow", "SNR in dB a link needs for Low.");
        dbl(config, v, SIGNAL, "levelMedium", "SNR in dB a link needs for Medium.");
        dbl(config, v, SIGNAL, "levelHigh", "SNR in dB a link needs for High.");
        dbl(config, v, SIGNAL, "levelMax", "SNR in dB a link needs for Max.");
        dbl(config, v, SIGNAL, "levelHysteresis", "dB below a threshold before a link leaves it.");
        dbl(config, v, SIGNAL, "dropoutMaxLoss", "Share of voice frames lost at the worst.");
        dbl(config, v, SIGNAL, "dropoutFullLossSnr", "SNR in dB where losses peak.");
        dbl(config, v, SIGNAL, "dropoutCleanSnr", "SNR in dB from which nothing is lost.");
        dbl(config, v, SIGNAL, "dropoutMeanBurstMillis", "Mean burst of losses, in ms.");
        dbl(config, v, SIGNAL, "dropAfterSeconds", "Seconds below Low before a link drops.");
        integer(config, v, WIRED, "pylonMinHeight", "Shortest pylon, in frame boxes.");
        integer(config, v, WIRED, "pylonMaxHeight", 1, Tunables.MAX_COLUMN, "Tallest pylon, in frame boxes.");
        dbl(config, v, WIRED, "pylonSpanFactor", "Span limit, times the shorter pylon's height.");
        integer(config, v, WIRED, "pylonClearanceSeconds", "Seconds between line checks.");
        dbl(config, v, WIRED, "lineBudget", "dB every powered node drives the line at.");
        dbl(config, v, WIRED, "lineLossPerBlock", "dB the line loses per block of span.");
        integer(
            config,
            v,
            WIRED,
            "phoneCableLength",
            1,
            Tunables.MAX_CABLE,
            "Most Phone Cable from phone to terminal.");
        integer(config, v, WIRED, "terminalPower", "EU/t a Line Terminal draws.");
        integer(config, v, WIRED, "repeaterPower", "EU/t a Repeater Head draws.");
        integer(config, v, WIRED, "routerPower", "EU/t a Router Head draws.");
        dbl(
            config,
            v,
            CALLS,
            "directHearingRange",
            "Blocks within which listeners hear a speaker directly, not through BeCom.");
        integer(config, v, CALLS, "sessionGraceSeconds", "Seconds a timed-out voice keeps a call.");
        integer(config, v, CALLS, "halfLinkSeconds", "Seconds a half-made wrench link is kept.");
        integer(config, v, CALLS, "ringSeconds", "Seconds a phone rings before \"no answer\".");
        integer(config, v, CALLS, "callAttemptsPerMinute", "Call attempts per caller a minute.");
        dbl(config, v, RADIO, "vhfWavelength", "Radio channels' wavelength in blocks. Longer bends over hills better.");
        dbl(config, v, RADIO, "uhfWavelength", "Calls' wavelength in blocks.");
        dbl(config, v, RADIO, "vhfCoverPerBlock", "dB a radio loses per block below the surface.");
        dbl(config, v, RADIO, "uhfCoverPerBlock", "dB a phone loses per block below the surface.");
        dbl(config, v, RADIO, "coverLossCap", "Most dB cover costs at one end.");
        dbl(config, v, RADIO, "terrainLossCap", "Most dB terrain costs a hop.");
        dbl(config, v, RADIO, "noSkyNoise", "Noise floor in dB in dimensions without a sky.");
        integer(config, v, RADIO, "surveyBearings", 1, Tunables.MAX_BEARINGS, "Bearings an antenna surveys.");
        integer(config, v, RADIO, "surveyStep", "Blocks between survey samples.");
        integer(config, v, RADIO, "surveySeconds", "Seconds between an antenna's surveys.");
        dbl(config, v, RADIO, "surveyBudgetMillis", "ms per tick surveys and line checks may take together.");
        dbl(config, v, ANTENNAS, "antennaBudgetT1", "Antenna T1 link budget in dB.");
        dbl(config, v, ANTENNAS, "antennaBudgetT2", "Antenna T2 link budget in dB.");
        dbl(config, v, ANTENNAS, "antennaBudgetT3", "Antenna T3 link budget in dB.");
        integer(config, v, ANTENNAS, "antennaPowerT1", "EU/t an Antenna T1 draws.");
        integer(config, v, ANTENNAS, "antennaPowerT2", "EU/t an Antenna T2 draws.");
        integer(config, v, ANTENNAS, "antennaPowerT3", "EU/t an Antenna T3 draws.");
        integer(config, v, ANTENNAS, "antennaMastMin", "Shortest antenna mast, in frame boxes.");
        integer(config, v, ANTENNAS, "antennaMastMax", 1, Tunables.MAX_COLUMN, "Tallest antenna mast, in frame boxes.");
        dbl(
            config,
            v,
            ANTENNAS,
            "wrenchLinkDistance",
            "Blocks an antenna reaches to bridge a Pylon Head or Line Terminal.");
        dbl(config, v, RADIOS, "transceiverBudget", "Transceiver link budget in dB, for direct links.");
        dbl(config, v, RADIOS, "walkieBudget", "Walkie-Talkie link budget in dB, for direct links.");
        integer(config, v, RADIOS, "transceiverPower", "EU/t a Transceiver draws.");
        dbl(config, v, RADIOS, "loudspeakerRange", "Blocks a Transceiver's loudspeaker carries.");
        dbl(config, v, RADIOS, "talkRange", "Blocks from a Transceiver within which it transmits your voice.");
        dbl(config, v, RADIOS, "walkieSpeakerRange", "Blocks around a receiving walkie within which others hear it.");
        integer(config, v, RADIOS, "captureHoldMillis", "ms a listener keeps hearing someone after their last frame.");
        dbl(config, v, RADIOS, "captureMargin", "dB stronger another transmitter must be to take over.");
        integer(config, v, RADIOS, "walkieBattery", "EU a Walkie-Talkie holds.");
        integer(config, v, RADIOS, "walkieStandbyDrain", "EU/t a Walkie-Talkie uses switched on.");
        integer(config, v, RADIOS, "walkieReceiveDrain", "EU/t a Walkie-Talkie uses while receiving.");
        integer(config, v, RADIOS, "walkieTransmitDrain", "EU/t a Walkie-Talkie uses while transmitting.");
        integer(config, v, RADIOS, "analyzerRefreshSeconds", "Seconds between Signal Analyzer readings.");
        integer(config, v, PHONES, "groupCallSize", 2, Tunables.MAX_GROUP_CALL, "Most phones in one call.");
        integer(config, v, PHONES, "phoneBattery", "EU a Mobile Phone holds.");
        integer(config, v, PHONES, "phoneStandbyDrain", "EU/t a phone uses switched on.");
        integer(config, v, PHONES, "phoneCallDrain", "EU/t a phone uses in a call.");
        dbl(config, v, PHONES, "weatherRainLoss", "dB rain costs calls and satellite hops.");
        dbl(config, v, PHONES, "weatherStormLoss", "dB a thunderstorm costs them, rain included.");
        integer(config, v, VIDEO, "videoPoseRate", 1, Tunables.MAX_POSE_RATE, "Front-camera updates a second.");
        integer(
            config,
            v,
            VIDEO,
            "videoHeightMedium",
            1,
            Tunables.MAX_VIDEO_HEIGHT,
            "Front camera's height at Medium.");
        integer(config, v, VIDEO, "videoHeightHigh", 1, Tunables.MAX_VIDEO_HEIGHT, "Front camera's height at High.");
        integer(config, v, VIDEO, "videoHeightMax", 1, Tunables.MAX_VIDEO_HEIGHT, "Front camera's height at Max.");
        integer(config, v, VIDEO, "videoFpsMedium", "Front camera's frames a second at Medium; 0 for every frame.");
        integer(config, v, VIDEO, "videoFpsHigh", "Front camera's frames a second at High; 0 for every frame.");
        integer(config, v, VIDEO, "videoFpsMax", "Front camera's frames a second at Max; 0 for every frame.");
        integer(config, v, VIDEO, "rearWidthHigh", 1, Tunables.MAX_REAR_WIDTH, "Rear camera's width at High.");
        integer(config, v, VIDEO, "rearHeightHigh", 1, Tunables.MAX_REAR_HEIGHT, "Rear camera's height at High.");
        integer(config, v, VIDEO, "rearFpsHigh", "Rear camera's frames a second at High.");
        integer(config, v, VIDEO, "rearWidthMax", 1, Tunables.MAX_REAR_WIDTH, "Rear camera's width at Max.");
        integer(config, v, VIDEO, "rearHeightMax", 1, Tunables.MAX_REAR_HEIGHT, "Rear camera's height at Max.");
        integer(config, v, VIDEO, "rearFpsMax", "Rear camera's frames a second at Max.");
        integer(
            config,
            v,
            VIDEO,
            "rearMaxBytes",
            1,
            Tunables.MAX_REAR_BYTES,
            "Largest rear-camera frame a client sends.");
        integer(config, v, VIDEO, "rearMaxFps", "Most rear-camera frames a client sends a second.");
        integer(config, v, VIDEO, "videophonePower", "EU/t a Videophone draws.");
        dbl(config, v, VIDEO, "videophoneRoom", "Blocks around a Videophone within which players are in its call.");
        dbl(config, v, VIDEO, "videophoneLoudspeaker", "Blocks a Videophone's loudspeaker carries.");
        integer(config, v, VIDEO, "videophoneEmptySeconds", "Seconds with nobody in the room before it hangs up.");
        integer(config, v, ME, "meBridgePower", "EU/t an ME Bridge draws.");
        integer(config, v, ME, "meBridgeChannels", 0, 1, "ME channels an ME Bridge uses.");
        v.put(
            "questlineEnabled",
            config.getBoolean(
                "questlineEnabled",
                QUESTS,
                (Boolean) Tunables.defaultOf("questlineEnabled"),
                "Add BeCom's questline to the BetterQuesting book. Turn off for servers with a curated book."));
        integer(config, v, QUESTS, "questlineRecheckSeconds", "Seconds between checks that the tab is there.");
        Tunables loaded = Tunables.fromMap(v);
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

    private static void dbl(Configuration config, Map<String, Object> values, String category, String key,
        String comment) {
        double def = (Double) Tunables.defaultOf(key);
        values.put(
            key,
            config.get(category, key, def, comment, -1000, 1000)
                .getDouble(def));
    }

    // The smallest value each whole-number option makes sense with, as Tunables.validate checks; the file shows it.
    private static final Map<String, Integer> MINIMUM = Map.ofEntries(
        Map.entry("pylonMinHeight", 1),
        Map.entry("pylonClearanceSeconds", 1),
        Map.entry("ringSeconds", 1),
        Map.entry("callAttemptsPerMinute", 1),
        Map.entry("questlineRecheckSeconds", 1),
        Map.entry("surveyStep", 1),
        Map.entry("surveySeconds", 1),
        Map.entry("antennaMastMin", 1),
        Map.entry("walkieBattery", 1),
        Map.entry("analyzerRefreshSeconds", 1),
        Map.entry("phoneBattery", 1),
        Map.entry("rearMaxFps", 1),
        Map.entry("videophoneEmptySeconds", 1));

    private static void integer(Configuration config, Map<String, Object> values, String category, String key,
        String comment) {
        integer(config, values, category, key, MINIMUM.getOrDefault(key, 0), Integer.MAX_VALUE, comment);
    }

    /** A whole number in [min, max]; a value outside is used at the nearest end, with a warning naming what was set. */
    private static void integer(Configuration config, Map<String, Object> values, String category, String key, int min,
        int max, String comment) {
        int def = (Integer) Tunables.defaultOf(key);
        // What the file says, read first: Configuration.get without a comment clears the comment getInt writes.
        int written = config.get(category, key, def)
            .getInt(def);
        int value = config.getInt(key, category, def, min, max, comment);
        if (written != value) {
            BeCom.LOG.warn("BeCom config: {} is {}, outside {} to {}; using {}", key, written, min, max, value);
        }
        values.put(key, value);
    }
}
