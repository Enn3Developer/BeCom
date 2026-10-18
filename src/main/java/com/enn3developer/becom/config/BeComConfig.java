package com.enn3developer.becom.config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import net.minecraftforge.common.config.Configuration;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.Tunables;

/**
 * BeCom's Forge config file: every tunable except the three kept fixed, read once at
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
    private static final String SPACE = "space";
    private static final String QUESTS = "quests";
    private static final String APPS = "apps";

    // What this side acts on: the file's values while a server runs here, the server's on a client connected to one,
    // and the defaults on a client that isn't.
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
        integer(config, v, WIRED, "cableRatePhone", "KB/s a Phone Cable carries.");
        integer(config, v, WIRED, "cableRateCoaxial", "KB/s a Coaxial cable carries.");
        integer(config, v, WIRED, "cableRateFibre", "KB/s a Fibre cable carries.");
        integer(
            config,
            v,
            WIRED,
            "cableReachPhone",
            1,
            Tunables.MAX_CABLE,
            "Most Phone Cable blocks a bus carries over.");
        integer(
            config,
            v,
            WIRED,
            "cableReachCoaxial",
            1,
            Tunables.MAX_CABLE,
            "Most Coaxial blocks a bus carries over.");
        integer(config, v, WIRED, "cableReachFibre", 1, Tunables.MAX_CABLE, "Most Fibre blocks a bus carries over.");
        integer(config, v, WIRED, "bundleSize", 1, Tunables.MAX_BUNDLE, "Most cables a pylon span holds.");
        integer(config, v, WIRED, "cableBlocksPerItem", "Blocks of span one cable item strings.");
        integer(config, v, WIRED, "routerPower", "EU/t a Router draws.");
        integer(config, v, WIRED, "routerCallsLV", "Calls an LV Router carries at once.");
        integer(config, v, WIRED, "routerCallsMV", "Calls an MV Router carries at once.");
        integer(config, v, WIRED, "routerCallsHV", "Calls an HV Router carries at once.");
        integer(config, v, WIRED, "exchangePower", "EU/t an Exchange draws.");
        integer(config, v, WIRED, "rackCallsLV", "Calls an LV Switching Rack switches at once.");
        integer(config, v, WIRED, "rackCallsMV", "Calls an MV Switching Rack switches at once.");
        integer(config, v, WIRED, "rackCallsHV", "Calls an HV Switching Rack switches at once.");
        integer(config, v, WIRED, "voiceRate", "KB/s a voice call takes on every hop.");
        integer(config, v, WIRED, "videoRate", "KB/s a video call takes on every hop.");
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
        integer(config, v, ANTENNAS, "antennaCallsT1", "Calls an Antenna T1 carries at once.");
        integer(config, v, ANTENNAS, "antennaCallsT2", "Calls an Antenna T2 carries at once.");
        integer(config, v, ANTENNAS, "antennaCallsT3", "Calls an Antenna T3 carries at once.");
        integer(config, v, RADIO, "radioRateLow", "KB/s a radio hop carries at Low.");
        integer(config, v, RADIO, "radioRateMedium", "KB/s a radio hop carries at Medium.");
        integer(config, v, RADIO, "radioRateHigh", "KB/s a radio hop carries at High.");
        integer(config, v, RADIO, "radioRateMax", "KB/s a radio hop carries at Max.");
        integer(config, v, ANTENNAS, "antennaMastMinT1", "Shortest Antenna T1 mast, in frame boxes.");
        integer(config, v, ANTENNAS, "antennaMastMinT2", "Shortest Antenna T2 mast, in frame boxes.");
        integer(config, v, ANTENNAS, "antennaMastMinT3", "Shortest Antenna T3 mast, in frame boxes.");
        integer(config, v, ANTENNAS, "antennaMastMax", 1, Tunables.MAX_COLUMN, "Tallest antenna mast, in frame boxes.");
        dbl(config, v, ANTENNAS, "wrenchLinkDistance", "Blocks an antenna reaches to bridge a Pylon Head or Router.");
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
        integer(config, v, PHONES, "phoneVideoDrain", "EU/t more a phone uses while it shows video.");
        integer(config, v, PHONES, "phoneScreenDrain", "EU/t more a phone uses while its screen is open.");
        integer(config, v, PHONES, "phoneAppDrain", "EU/t more a phone uses while its apps run.");
        integer(config, v, PHONES, "phoneStorageKB", 1, 16384, "KB of storage a phone's apps share.");
        integer(config, v, APPS, "appsPerPhone", 1, 64, "Most apps one phone holds.");
        integer(config, v, APPS, "appSourceKB", 1, 64, "KB an app's one Lua file may take.");
        integer(
            config,
            v,
            APPS,
            "appStorageKB",
            1,
            16384,
            "KB of the phone's storage each app may fill with its files.");
        integer(config, v, APPS, "appMessageKB", 1, 64, "KB one message between apps and computers may take.");
        integer(config, v, APPS, "appWakeSeconds", 10, 3600, "Seconds at least between a phone's background wakes.");
        integer(
            config,
            v,
            APPS,
            "appAwakeSeconds",
            2,
            120,
            "Seconds a background wake keeps the phone's apps running.");
        integer(
            config,
            v,
            APPS,
            "appOfferSeconds",
            10,
            600,
            "Seconds a phone's holder has to answer a computer's install or publish.");
        integer(
            config,
            v,
            APPS,
            "appAuthorReach",
            2,
            64,
            "Blocks from a computer within which a player is asked to publish its app.");
        integer(config, v, APPS, "storeApps", 1, 8192, "Most apps the App Store holds.");
        integer(config, v, APPS, "storeAppsPerAuthor", 1, 1024, "Most apps one player may have in the App Store.");
        integer(
            config,
            v,
            APPS,
            "computerForgetDays",
            1,
            3650,
            "Days after which the number of a BeCom Card nobody has run is given up.");
        integer(
            config,
            v,
            PHONES,
            "phoneSaverPercent",
            0,
            100,
            "Charge left, in percent, at which a phone's battery saver comes on by itself.");
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
        integer(config, v, ME, "meBridgeCalls", "Calls an ME Bridge carries at once.");
        integer(config, v, SPACE, "satellitesLow", "Satellites over a dimension for its sky hops to reach Low.");
        integer(config, v, SPACE, "satellitesMax", "Satellites over a dimension for its sky hops to reach Max.");
        dbl(config, v, SPACE, "satelliteLifetimeDays", "Minecraft days a satellite stays in orbit.");
        dbl(
            config,
            v,
            SPACE,
            "satelliteLifetimeSpread",
            "How far each satellite's lifetime varies either way, as a share of it, so they don't all come down at once.");
        integer(config, v, SPACE, "launchSeconds", "Seconds a Satellite Launch Pad takes to launch one satellite.");
        integer(config, v, SPACE, "launchPower", "EU/t a Satellite Launch Pad draws while it launches.");
        integer(config, v, SPACE, "launchFuel", "Litres of rocket fuel one launch takes.");
        integer(config, v, SPACE, "dishPower", "EU/t a Satellite Dish draws.");
        dbl(config, v, SPACE, "dishMaintenanceLoss", "dB each maintenance problem costs a Satellite Dish's sky hops.");
        integer(
            config,
            v,
            SPACE,
            "receiverUplinkLevel",
            1,
            4,
            "Level of a Rocket Radio Receiver's uplink to the Overworld's satellites: 1 Low, 2 Medium, 3 High, 4 Max.");
        integer(config, v, SPACE, "relayPower", "EU/t a Portal Relay draws.");
        dbl(config, v, SPACE, "relayPortalDistance", "Blocks from a portal within which a Portal Relay works.");
        integer(
            config,
            v,
            SPACE,
            "relayHopLevel",
            1,
            4,
            "Level of the hop between a linked pair of Portal Relays: 1 Low, 2 Medium, 3 High, 4 Max.");
        integer(
            config,
            v,
            SPACE,
            "satelliteCalls",
            "Calls each satellite carries, shared by everything under its sky.");
        integer(config, v, SPACE, "dishCalls", "Calls a Satellite Dish carries at once.");
        integer(config, v, SPACE, "receiverCalls", "Calls a Rocket Radio Receiver carries at once.");
        integer(config, v, SPACE, "relayCallsMV", "Calls an MV Portal Relay carries at once.");
        integer(config, v, SPACE, "relayCallsHV", "Calls an HV Portal Relay carries at once.");
        integer(config, v, SPACE, "relayCallsEV", "Calls an EV Portal Relay carries at once.");
        integer(config, v, SPACE, "relayRateMV", "KB/s a pair of Portal Relays carries at MV.");
        integer(config, v, SPACE, "relayRateHV", "KB/s a pair of Portal Relays carries at HV.");
        integer(config, v, SPACE, "relayRateEV", "KB/s a pair of Portal Relays carries at EV.");
        v.put(
            "questlineEnabled",
            config.getBoolean(
                "questlineEnabled",
                QUESTS,
                (Boolean) Tunables.defaultOf("questlineEnabled"),
                "Add BeCom's questline to the BetterQuesting book, and bring BeCom's quests up to date at each start,"
                    + " keeping progress: hand edits to BeCom's own quests are lost at the next start. Turn off for"
                    + " servers with a curated book, and BeCom leaves the book alone."));
        integer(config, v, QUESTS, "questlineRecheckSeconds", "Seconds between checks that the tab is there.");
        Tunables loaded = Tunables.fromMap(v);
        // Checked when a server is about to use them, not here: a client never acts on its own file,
        // so a bad value in it mustn't stop the client from starting.
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
        Map.entry("antennaMastMinT1", Tunables.ANTENNA_SHORTEST_MAST[1]),
        Map.entry("antennaMastMinT2", Tunables.ANTENNA_SHORTEST_MAST[2]),
        Map.entry("antennaMastMinT3", Tunables.ANTENNA_SHORTEST_MAST[3]),
        Map.entry("walkieBattery", 1),
        Map.entry("analyzerRefreshSeconds", 1),
        Map.entry("phoneBattery", 1),
        Map.entry("rearMaxFps", 1),
        Map.entry("videophoneEmptySeconds", 1),
        Map.entry("satellitesLow", 1),
        Map.entry("launchSeconds", 1));

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
