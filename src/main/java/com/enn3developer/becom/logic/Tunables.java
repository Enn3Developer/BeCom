package com.enn3developer.becom.logic;

import com.enn3developer.becom.logic.net.CableTier;
import com.enn3developer.becom.logic.radio.Band;
import com.enn3developer.becom.logic.radio.RadioModel;

/**
 * Every server-side tunable the code reads, as loaded from the config at startup.
 * Immutable and free of Minecraft types, so logic and tests can take it as-is.
 */
public record Tunables(
    // Signal
    double levelLow, double levelMedium, double levelHigh, double levelMax, double levelHysteresis,
    double dropoutMaxLoss, double dropoutFullLossSnr, double dropoutCleanSnr, double dropoutMeanBurstMillis,
    double dropAfterSeconds,
    // Wired network
    int cableRatePhone, int cableRateCoaxial, int cableRateFibre, int cableReachPhone, int cableReachCoaxial,
    int cableReachFibre, int routerPower, int routerCallsLV, int routerCallsMV, int routerCallsHV, int exchangePower,
    int rackPower, int rackCallsLV, int rackCallsMV, int rackCallsHV, int voiceRate, int videoRate,
    // Calls
    double directHearingRange, int sessionGraceSeconds, int ringSeconds, int callAttemptsPerMinute,
    // Radio simulation
    double vhfWavelength, double uhfWavelength, double vhfCoverPerBlock, double uhfCoverPerBlock, double coverLossCap,
    double terrainLossCap, double noSkyNoise, int surveyBearings, int surveyStep, int surveySeconds,
    double surveyBudgetMillis,
    // Antennas
    double antennaBudgetT1, double antennaBudgetT2, double antennaBudgetT3, int antennaPowerT1, int antennaPowerT2,
    int antennaPowerT3, int antennaMastMinT1, int antennaMastMinT2, int antennaMastMinT3, int antennaMastMax,
    int antennaCallsT1, int antennaCallsT2, int antennaCallsT3, int radioRateLow, int radioRateMedium,
    int radioRateHigh, int radioRateMax,
    // Radios
    double transceiverBudget, double walkieBudget, int transceiverPower, double loudspeakerRange, double talkRange,
    double walkieSpeakerRange, int captureHoldMillis, double captureMargin, int walkieBattery, int walkieStandbyDrain,
    int walkieReceiveDrain, int walkieTransmitDrain, int analyzerRefreshSeconds,
    // Phones
    int groupCallSize, int phoneBattery, int phoneStandbyDrain, int phoneCallDrain, int phoneVideoDrain,
    int phoneScreenDrain, int phoneSaverPercent, int phoneAppDrain, int phoneStorageKB, double weatherRainLoss,
    double weatherStormLoss,
    // Apps
    int appsPerPhone, int appSourceKB, int appStorageKB, int appMessageKB, int appWakeSeconds, int appAwakeSeconds,
    int appOfferSeconds, int appAuthorReach, int storeApps, int storeAppsPerAuthor, int computerForgetDays,
    int mirrorPower,
    // Video
    int videoPoseRate, int videoHeightMedium, int videoHeightHigh, int videoHeightMax, int videoFpsMedium,
    int videoFpsHigh, int videoFpsMax, int rearWidthHigh, int rearHeightHigh, int rearFpsHigh, int rearWidthMax,
    int rearHeightMax, int rearFpsMax, int rearMaxBytes, int rearMaxFps, int videophonePower, double videophoneRoom,
    double videophoneLoudspeaker, int videophoneEmptySeconds,
    // ME
    int meBridgePower, int meBridgeCallPower, int meBridgeChannels, int meBridgeCalls,
    // Space: levels by number, 1 Low to 4 Max
    int satellitesLow, int satellitesMax, double satelliteLifetimeDays, double satelliteLifetimeSpread,
    int launchSeconds, int launchPower, int launchFuel, int dishPower, double dishMaintenanceLoss,
    int receiverUplinkLevel, int relayPower, double relayPortalDistance, int relayHopLevel, int satelliteCalls,
    int dishCalls, int receiverCalls, int relayCallsMV, int relayCallsHV, int relayCallsEV, int relayRateMV,
    int relayRateHV, int relayRateEV,
    // Quests
    boolean questlineEnabled, int questlineRecheckSeconds) {

    // Ceilings no sensible config reaches, which keep a hostile server's values from hanging a client: no frame column
    // is taller than the world, and no bus is counted far past what it carries over.
    public static final int MAX_COLUMN = 255;
    public static final int MAX_CABLE = 16_384;
    // Portal Relays come in MV, HV and EV.
    public static final int RELAY_MIN_TIER = 2;
    public static final int RELAY_MAX_TIER = 4;
    private static final String[] RELAY_VOLTAGES = { "MV", "HV", "EV" };
    // Radio work grows with range and bearings: a survey reads bearings x range / step heights, a direct link
    // range / step. Far past the defaults (256 blocks, 64 bearings), these keep one survey or link bounded.
    public static final int MAX_RADIO_RANGE = 4096;
    public static final int MAX_BEARINGS = 1024;
    // A call's every listener gets every speaker, and a group call's grid one tile each: past this, both stop being
    // bounded. Video sizes stay within what a client draws and a packet carries.
    public static final int MAX_GROUP_CALL = 16;
    public static final int MAX_VIDEO_HEIGHT = 512;
    public static final int MAX_REAR_WIDTH = 640;
    public static final int MAX_REAR_HEIGHT = 360;
    public static final int MAX_REAR_BYTES = 32_768;
    // Poses go out at most once a server tick.
    public static final int MAX_POSE_RATE = 20;
    // A Portal Relay looks for its portal in a cube this many blocks out each way.
    public static final int MAX_PORTAL_DISTANCE = 8;

    /**
     * The shortest mast each antenna tier's shape has room on, by tier (index 0 unused): below it, the guys or the
     * lattice at its foot run into the antennas at its top.
     */
    public static final int[] ANTENNA_SHORTEST_MAST = { 0, 4, 6, 7 };

    /** GTNH Voice's frame length, in ms: the dropout model's step. Fixed by the voice codec, not a tunable. */
    public static final int FRAME_MILLIS = 20;

    // The defaults, by component name. Doubles are written as doubles, ints as ints: fromMap
    // takes a value only when its type matches.
    private static final java.util.Map<String, Object> DEFAULTS = new java.util.LinkedHashMap<>();

    static {
        java.util.Map<String, Object> d = DEFAULTS;
        d.put("levelLow", 6.0);
        d.put("levelMedium", 12.0);
        d.put("levelHigh", 18.0);
        d.put("levelMax", 24.0);
        d.put("levelHysteresis", 2.0);
        d.put("dropoutMaxLoss", 0.30);
        d.put("dropoutFullLossSnr", 6.0);
        d.put("dropoutCleanSnr", 12.0);
        d.put("dropoutMeanBurstMillis", 60.0);
        d.put("dropAfterSeconds", 3.0);
        d.put("cableRatePhone", 16);
        d.put("cableRateCoaxial", 64);
        d.put("cableRateFibre", 256);
        d.put("cableReachPhone", 128);
        d.put("cableReachCoaxial", 512);
        d.put("cableReachFibre", 4096);
        d.put("routerPower", 8);
        d.put("routerCallsLV", 8);
        d.put("routerCallsMV", 16);
        d.put("routerCallsHV", 32);
        d.put("exchangePower", 24);
        d.put("rackPower", 4);
        d.put("rackCallsLV", 8);
        d.put("rackCallsMV", 16);
        d.put("rackCallsHV", 32);
        d.put("voiceRate", 2);
        d.put("videoRate", 8);
        d.put("directHearingRange", 16.0);
        d.put("sessionGraceSeconds", 10);
        d.put("ringSeconds", 30);
        d.put("callAttemptsPerMinute", 5);
        d.put("vhfWavelength", 3.0);
        d.put("uhfWavelength", 1.0);
        d.put("vhfCoverPerBlock", 1.5);
        d.put("uhfCoverPerBlock", 2.5);
        d.put("coverLossCap", 40.0);
        d.put("terrainLossCap", 40.0);
        d.put("noSkyNoise", 10.0);
        d.put("surveyBearings", 64);
        d.put("surveyStep", 4);
        d.put("surveySeconds", 5 * 60);
        d.put("surveyBudgetMillis", 2.0);
        d.put("antennaBudgetT1", 42.0);
        d.put("antennaBudgetT2", 48.0);
        d.put("antennaBudgetT3", 54.0);
        d.put("antennaPowerT1", 24);
        d.put("antennaPowerT2", 96);
        d.put("antennaPowerT3", 384);
        d.put("antennaMastMinT1", 6);
        d.put("antennaMastMinT2", 8);
        d.put("antennaMastMinT3", 10);
        d.put("antennaMastMax", 32);
        d.put("antennaCallsT1", 4);
        d.put("antennaCallsT2", 8);
        d.put("antennaCallsT3", 16);
        d.put("radioRateLow", 4);
        d.put("radioRateMedium", 16);
        d.put("radioRateHigh", 64);
        d.put("radioRateMax", 256);
        d.put("transceiverBudget", 48.0);
        d.put("walkieBudget", 44.0);
        d.put("transceiverPower", 32);
        d.put("loudspeakerRange", 16.0);
        d.put("talkRange", 2.0);
        d.put("walkieSpeakerRange", 6.0);
        d.put("captureHoldMillis", 100);
        d.put("captureMargin", 3.0);
        d.put("walkieBattery", 300_000);
        d.put("walkieStandbyDrain", 1);
        d.put("walkieReceiveDrain", 2);
        d.put("walkieTransmitDrain", 4);
        d.put("analyzerRefreshSeconds", 1);
        d.put("groupCallSize", 8);
        d.put("phoneBattery", 1_200_000);
        d.put("phoneStandbyDrain", 4);
        d.put("phoneCallDrain", 16);
        d.put("phoneVideoDrain", 16);
        d.put("phoneScreenDrain", 2);
        d.put("phoneSaverPercent", 15);
        d.put("phoneAppDrain", 8);
        d.put("phoneStorageKB", 256);
        d.put("appsPerPhone", 16);
        d.put("appSourceKB", 64);
        d.put("appStorageKB", 64);
        d.put("appMessageKB", 8);
        d.put("appWakeSeconds", 60);
        d.put("appAwakeSeconds", 10);
        d.put("appOfferSeconds", 60);
        d.put("appAuthorReach", 16);
        d.put("storeApps", 1024);
        d.put("storeAppsPerAuthor", 32);
        d.put("mirrorPower", 64);
        d.put("computerForgetDays", 30);
        d.put("weatherRainLoss", 3.0);
        d.put("weatherStormLoss", 6.0);
        d.put("videoPoseRate", 10);
        d.put("videoHeightMedium", 48);
        d.put("videoHeightHigh", 96);
        d.put("videoHeightMax", 192);
        d.put("videoFpsMedium", 5);
        d.put("videoFpsHigh", 12);
        // 0: every frame the client draws.
        d.put("videoFpsMax", 0);
        d.put("rearWidthHigh", 128);
        d.put("rearHeightHigh", 72);
        d.put("rearFpsHigh", 2);
        d.put("rearWidthMax", 192);
        d.put("rearHeightMax", 108);
        d.put("rearFpsMax", 5);
        d.put("rearMaxBytes", 8 * 1024);
        d.put("rearMaxFps", 6);
        d.put("videophonePower", 32);
        d.put("videophoneRoom", 4.0);
        d.put("videophoneLoudspeaker", 16.0);
        d.put("videophoneEmptySeconds", 30);
        d.put("meBridgePower", 64);
        d.put("meBridgeCallPower", 16);
        d.put("meBridgeChannels", 1);
        d.put("meBridgeCalls", 8);
        d.put("satellitesLow", 5);
        d.put("satellitesMax", 100);
        d.put("satelliteLifetimeDays", 100.0);
        d.put("satelliteLifetimeSpread", 0.2);
        d.put("launchSeconds", 30);
        d.put("launchPower", 480);
        d.put("launchFuel", 1000);
        d.put("dishPower", 384);
        d.put("dishMaintenanceLoss", 2.0);
        d.put("receiverUplinkLevel", 3);
        d.put("relayPower", 32);
        d.put("relayPortalDistance", 4.0);
        d.put("relayHopLevel", 3);
        d.put("satelliteCalls", 2);
        d.put("dishCalls", 32);
        d.put("receiverCalls", 4);
        d.put("relayCallsMV", 16);
        d.put("relayCallsHV", 32);
        d.put("relayCallsEV", 64);
        d.put("relayRateMV", 64);
        d.put("relayRateHV", 128);
        d.put("relayRateEV", 256);
        d.put("questlineEnabled", true);
        d.put("questlineRecheckSeconds", 60);
    }

    /** The defaults. */
    public static Tunables defaults() {
        return fromMap(java.util.Map.of());
    }

    /** The default of one value, by its name. */
    public static Object defaultOf(String name) {
        Object value = DEFAULTS.get(name);
        if (value == null) throw new IllegalArgumentException("No tunable named " + name);
        return value;
    }

    /**
     * Refuses values that would crash the server at start or make it do something every tick, naming the config key.
     *
     * @throws IllegalArgumentException for the first such value
     */
    public void validate() {
        // A config file reads NaN and Infinity as numbers, and most checks below let NaN through.
        try {
            for (java.lang.reflect.RecordComponent c : Tunables.class.getRecordComponents()) {
                if (c.getType() != double.class) continue;
                double value = (double) c.getAccessor()
                    .invoke(this);
                if (!Double.isFinite(value)) throw invalid(c.getName(), value, "a finite number");
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        try {
            levelScale();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("levelLow, levelMedium, levelHigh, levelMax: " + e.getMessage(), e);
        }
        try {
            dropoutModel(FRAME_MILLIS);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "dropoutMaxLoss, dropoutFullLossSnr, dropoutCleanSnr, dropoutMeanBurstMillis (at least one "
                    + FRAME_MILLIS
                    + " ms voice frame): "
                    + e.getMessage(),
                e);
        }
        for (CableTier tier : CableTier.values()) {
            String name = tier.configName();
            if (cableRate(tier) < 1) throw invalid("cableRate" + name, cableRate(tier), "at least 1");
            if (cableReach(tier) < 1 || cableReach(tier) > MAX_CABLE) {
                throw invalid("cableReach" + name, cableReach(tier), "1 to " + MAX_CABLE);
            }
            if (routerCalls(tier) < 1) throw invalid("routerCalls" + tier.voltage(), routerCalls(tier), "at least 1");
            if (rackCalls(tier) < 1) throw invalid("rackCalls" + tier.voltage(), rackCalls(tier), "at least 1");
        }
        if (routerPower < 0) throw invalid("routerPower", routerPower, "0 or more");
        if (exchangePower < 0) throw invalid("exchangePower", exchangePower, "0 or more");
        if (rackPower < 0) throw invalid("rackPower", rackPower, "0 or more");
        if (voiceRate < 1) throw invalid("voiceRate", voiceRate, "at least 1");
        if (videoRate < voiceRate) throw invalid("videoRate", videoRate, "at least voiceRate");
        for (int tier = 1; tier <= 3; tier++) {
            if (antennaCalls(tier) < 1) throw invalid("antennaCallsT" + tier, antennaCalls(tier), "at least 1");
        }
        if (radioRateLow < 1) throw invalid("radioRateLow", radioRateLow, "at least 1");
        if (radioRateMedium < radioRateLow) throw invalid("radioRateMedium", radioRateMedium, "at least radioRateLow");
        if (radioRateHigh < radioRateMedium) throw invalid("radioRateHigh", radioRateHigh, "at least radioRateMedium");
        if (radioRateMax < radioRateHigh) throw invalid("radioRateMax", radioRateMax, "at least radioRateHigh");
        if (meBridgeCalls < 1) throw invalid("meBridgeCalls", meBridgeCalls, "at least 1");
        if (satelliteCalls < 1) throw invalid("satelliteCalls", satelliteCalls, "at least 1");
        if (dishCalls < 1) throw invalid("dishCalls", dishCalls, "at least 1");
        if (receiverCalls < 1) throw invalid("receiverCalls", receiverCalls, "at least 1");
        for (int tier = RELAY_MIN_TIER; tier <= RELAY_MAX_TIER; tier++) {
            String voltage = RELAY_VOLTAGES[tier - RELAY_MIN_TIER];
            if (relayCalls(tier) < 1) throw invalid("relayCalls" + voltage, relayCalls(tier), "at least 1");
            if (relayRate(tier) < 1) throw invalid("relayRate" + voltage, relayRate(tier), "at least 1");
        }
        if (dropAfterSeconds < 0) throw invalid("dropAfterSeconds", dropAfterSeconds, "0 or more");
        if (directHearingRange < 0) throw invalid("directHearingRange", directHearingRange, "0 or more");
        if (ringSeconds < 1) throw invalid("ringSeconds", ringSeconds, "at least 1");
        if (callAttemptsPerMinute < 1) throw invalid("callAttemptsPerMinute", callAttemptsPerMinute, "at least 1");
        if (questlineRecheckSeconds < 1)
            throw invalid("questlineRecheckSeconds", questlineRecheckSeconds, "at least 1");
        if (!(vhfWavelength > 0)) throw invalid("vhfWavelength", vhfWavelength, "more than 0");
        if (!(uhfWavelength > 0)) throw invalid("uhfWavelength", uhfWavelength, "more than 0");
        if (vhfCoverPerBlock < 0) throw invalid("vhfCoverPerBlock", vhfCoverPerBlock, "0 or more");
        if (uhfCoverPerBlock < 0) throw invalid("uhfCoverPerBlock", uhfCoverPerBlock, "0 or more");
        if (coverLossCap < 0) throw invalid("coverLossCap", coverLossCap, "0 or more");
        if (terrainLossCap < 0) throw invalid("terrainLossCap", terrainLossCap, "0 or more");
        if (surveyBearings < 1 || surveyBearings > MAX_BEARINGS) {
            throw invalid("surveyBearings", surveyBearings, "1 to " + MAX_BEARINGS);
        }
        if (surveyStep < 1) throw invalid("surveyStep", surveyStep, "at least 1");
        if (surveySeconds < 1) throw invalid("surveySeconds", surveySeconds, "at least 1");
        if (!(surveyBudgetMillis > 0)) throw invalid("surveyBudgetMillis", surveyBudgetMillis, "more than 0");
        checkRange("antennaBudgetT1", antennaBudgetT1);
        checkRange("antennaBudgetT2", antennaBudgetT2);
        checkRange("antennaBudgetT3", antennaBudgetT3);
        checkRange("transceiverBudget", transceiverBudget);
        checkRange("walkieBudget", walkieBudget);
        for (int tier = 1; tier <= 3; tier++) {
            String key = "antennaMastMinT" + tier;
            int shortest = ANTENNA_SHORTEST_MAST[tier];
            if (antennaMastMin(tier) < shortest) throw invalid(key, antennaMastMin(tier), "at least " + shortest);
            if (antennaMastMax < antennaMastMin(tier))
                throw invalid("antennaMastMax", antennaMastMax, "at least " + key);
        }
        if (antennaMastMax > MAX_COLUMN) throw invalid("antennaMastMax", antennaMastMax, "at most " + MAX_COLUMN);
        if (loudspeakerRange < 0) throw invalid("loudspeakerRange", loudspeakerRange, "0 or more");
        if (talkRange < 0) throw invalid("talkRange", talkRange, "0 or more");
        if (walkieSpeakerRange < 0) throw invalid("walkieSpeakerRange", walkieSpeakerRange, "0 or more");
        if (captureHoldMillis < 0) throw invalid("captureHoldMillis", captureHoldMillis, "0 or more");
        if (captureMargin < 0) throw invalid("captureMargin", captureMargin, "0 or more");
        if (walkieBattery < 1) throw invalid("walkieBattery", walkieBattery, "at least 1");
        if (analyzerRefreshSeconds < 1) throw invalid("analyzerRefreshSeconds", analyzerRefreshSeconds, "at least 1");
        if (groupCallSize < 2 || groupCallSize > MAX_GROUP_CALL)
            throw invalid("groupCallSize", groupCallSize, "2 to " + MAX_GROUP_CALL);
        if (phoneBattery < 1) throw invalid("phoneBattery", phoneBattery, "at least 1");
        if (phoneStandbyDrain < 0) throw invalid("phoneStandbyDrain", phoneStandbyDrain, "0 or more");
        if (phoneCallDrain < 0) throw invalid("phoneCallDrain", phoneCallDrain, "0 or more");
        if (phoneVideoDrain < 0) throw invalid("phoneVideoDrain", phoneVideoDrain, "0 or more");
        if (phoneScreenDrain < 0) throw invalid("phoneScreenDrain", phoneScreenDrain, "0 or more");
        if (phoneAppDrain < 0) throw invalid("phoneAppDrain", phoneAppDrain, "0 or more");
        if (phoneStorageKB < 1 || phoneStorageKB > 16384) throw invalid("phoneStorageKB", phoneStorageKB, "1 to 16384");
        if (phoneSaverPercent < 0 || phoneSaverPercent > 100) {
            throw invalid("phoneSaverPercent", phoneSaverPercent, "0 to 100");
        }
        if (appsPerPhone < 1 || appsPerPhone > 64) throw invalid("appsPerPhone", appsPerPhone, "1 to 64");
        if (appSourceKB < 1 || appSourceKB > 64) throw invalid("appSourceKB", appSourceKB, "1 to 64");
        if (appStorageKB < 1 || appStorageKB > 16384) throw invalid("appStorageKB", appStorageKB, "1 to 16384");
        if (appMessageKB < 1 || appMessageKB > 64) throw invalid("appMessageKB", appMessageKB, "1 to 64");
        if (appWakeSeconds < 10 || appWakeSeconds > 3600) throw invalid("appWakeSeconds", appWakeSeconds, "10 to 3600");
        if (appAwakeSeconds < 2 || appAwakeSeconds > 120) throw invalid("appAwakeSeconds", appAwakeSeconds, "2 to 120");
        if (appOfferSeconds < 10 || appOfferSeconds > 600)
            throw invalid("appOfferSeconds", appOfferSeconds, "10 to 600");
        if (appAuthorReach < 2 || appAuthorReach > 64) throw invalid("appAuthorReach", appAuthorReach, "2 to 64");
        if (storeApps < 1 || storeApps > 8192) throw invalid("storeApps", storeApps, "1 to 8192");
        if (mirrorPower < 0) throw invalid("mirrorPower", mirrorPower, "0 or more");
        if (storeAppsPerAuthor < 1 || storeAppsPerAuthor > 1024)
            throw invalid("storeAppsPerAuthor", storeAppsPerAuthor, "1 to 1024");
        if (computerForgetDays < 1 || computerForgetDays > 3650)
            throw invalid("computerForgetDays", computerForgetDays, "1 to 3650");
        if (weatherRainLoss < 0) throw invalid("weatherRainLoss", weatherRainLoss, "0 or more");
        if (weatherStormLoss < weatherRainLoss)
            throw invalid("weatherStormLoss", weatherStormLoss, "at least weatherRainLoss");
        if (videoPoseRate < 1 || videoPoseRate > MAX_POSE_RATE)
            throw invalid("videoPoseRate", videoPoseRate, "1 to " + MAX_POSE_RATE);
        checkSize("videoHeightMedium", videoHeightMedium, MAX_VIDEO_HEIGHT);
        checkSize("videoHeightHigh", videoHeightHigh, MAX_VIDEO_HEIGHT);
        checkSize("videoHeightMax", videoHeightMax, MAX_VIDEO_HEIGHT);
        if (videoFpsMedium < 0) throw invalid("videoFpsMedium", videoFpsMedium, "0 or more");
        if (videoFpsHigh < 0) throw invalid("videoFpsHigh", videoFpsHigh, "0 or more");
        if (videoFpsMax < 0) throw invalid("videoFpsMax", videoFpsMax, "0 or more");
        checkSize("rearWidthHigh", rearWidthHigh, MAX_REAR_WIDTH);
        checkSize("rearHeightHigh", rearHeightHigh, MAX_REAR_HEIGHT);
        checkSize("rearWidthMax", rearWidthMax, MAX_REAR_WIDTH);
        checkSize("rearHeightMax", rearHeightMax, MAX_REAR_HEIGHT);
        if (rearFpsHigh < 0) throw invalid("rearFpsHigh", rearFpsHigh, "0 or more");
        if (rearFpsMax < 0) throw invalid("rearFpsMax", rearFpsMax, "0 or more");
        checkSize("rearMaxBytes", rearMaxBytes, MAX_REAR_BYTES);
        if (rearMaxFps < 1) throw invalid("rearMaxFps", rearMaxFps, "at least 1");
        if (videophonePower < 0) throw invalid("videophonePower", videophonePower, "0 or more");
        if (videophoneRoom < 0) throw invalid("videophoneRoom", videophoneRoom, "0 or more");
        if (videophoneLoudspeaker < 0) throw invalid("videophoneLoudspeaker", videophoneLoudspeaker, "0 or more");
        if (videophoneEmptySeconds < 1) throw invalid("videophoneEmptySeconds", videophoneEmptySeconds, "at least 1");
        if (meBridgePower < 0) throw invalid("meBridgePower", meBridgePower, "0 or more");
        if (meBridgeCallPower < 0) throw invalid("meBridgeCallPower", meBridgeCallPower, "0 or more");
        if (meBridgeChannels < 0 || meBridgeChannels > 1) throw invalid("meBridgeChannels", meBridgeChannels, "0 or 1");
        if (satellitesLow < 1) throw invalid("satellitesLow", satellitesLow, "at least 1");
        if (satellitesMax <= satellitesLow) throw invalid("satellitesMax", satellitesMax, "more than satellitesLow");
        if (!(satelliteLifetimeDays > 0)) throw invalid("satelliteLifetimeDays", satelliteLifetimeDays, "more than 0");
        if (satelliteLifetimeSpread < 0 || satelliteLifetimeSpread >= 1) {
            throw invalid("satelliteLifetimeSpread", satelliteLifetimeSpread, "0 or more, and less than 1");
        }
        if (launchSeconds < 1) throw invalid("launchSeconds", launchSeconds, "at least 1");
        if (launchPower < 0) throw invalid("launchPower", launchPower, "0 or more");
        if (launchFuel < 0) throw invalid("launchFuel", launchFuel, "0 or more");
        if (dishPower < 0) throw invalid("dishPower", dishPower, "0 or more");
        if (dishMaintenanceLoss < 0) throw invalid("dishMaintenanceLoss", dishMaintenanceLoss, "0 or more");
        checkLevel("receiverUplinkLevel", receiverUplinkLevel);
        if (relayPower < 0) throw invalid("relayPower", relayPower, "0 or more");
        if (relayPortalDistance < 0 || relayPortalDistance > MAX_PORTAL_DISTANCE) {
            throw invalid("relayPortalDistance", relayPortalDistance, "0 to " + MAX_PORTAL_DISTANCE);
        }
        checkLevel("relayHopLevel", relayHopLevel);
    }

    private static void checkLevel(String key, int level) {
        if (level < SignalLevel.LOW.ordinal() || level > SignalLevel.MAX.ordinal()) {
            throw invalid(key, level, SignalLevel.LOW.ordinal() + " (Low) to " + SignalLevel.MAX.ordinal() + " (Max)");
        }
    }

    /**
     * A budget whose open-ground range, out to where a link at Low still holds, stays within
     * {@link #MAX_RADIO_RANGE}: surveys and direct links reach that far.
     */
    private void checkRange(String key, double budget) {
        double floor = levelLow - levelHysteresis;
        double range = Math.pow(10, (budget - floor) / 20);
        if (!(range <= MAX_RADIO_RANGE)) {
            throw invalid(
                key,
                budget,
                "at most " + (floor + 20 * Math.log10(MAX_RADIO_RANGE))
                    + " with this levelLow and levelHysteresis (a range of "
                    + MAX_RADIO_RANGE
                    + " blocks)");
        }
    }

    private static void checkSize(String key, int value, int max) {
        if (value < 1 || value > max) throw invalid(key, value, "1 to " + max);
    }

    private static IllegalArgumentException invalid(String key, Object value, String rule) {
        return new IllegalArgumentException(key + " is " + value + ", but must be " + rule);
    }

    /** The level of a Rocket Radio Receiver's uplink to the Overworld's satellites. */
    public SignalLevel receiverUplink() {
        return SignalLevel.byOrdinal(receiverUplinkLevel);
    }

    /** How many calls a Portal Relay of a GT voltage tier carries at once, MV to EV. */
    public int relayCalls(int voltageTier) {
        return switch (Math.max(RELAY_MIN_TIER, Math.min(RELAY_MAX_TIER, voltageTier))) {
            case 2 -> relayCallsMV;
            case 3 -> relayCallsHV;
            default -> relayCallsEV;
        };
    }

    /** A Portal Relay pair's data rate in KB/s, by the GT voltage tier it works at, MV to EV. */
    public int relayRate(int voltageTier) {
        return switch (Math.max(RELAY_MIN_TIER, Math.min(RELAY_MAX_TIER, voltageTier))) {
            case 2 -> relayRateMV;
            case 3 -> relayRateHV;
            default -> relayRateEV;
        };
    }

    /** The level of the hop between a linked pair of Portal Relays. */
    public SignalLevel relayHop() {
        return SignalLevel.byOrdinal(relayHopLevel);
    }

    /** Every value by its name, for sending to clients. Doubles, ints and booleans only. */
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        try {
            for (java.lang.reflect.RecordComponent c : Tunables.class.getRecordComponents()) {
                values.put(
                    c.getName(),
                    c.getAccessor()
                        .invoke(this));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return values;
    }

    /**
     * The values in {@code values}, by name; any missing or of the wrong type keep their default. Not checked: call
     * {@link #validate} on the result.
     */
    public static Tunables fromMap(java.util.Map<String, Object> values) {
        java.lang.reflect.RecordComponent[] components = Tunables.class.getRecordComponents();
        Object[] args = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        try {
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                Object value = values.get(components[i].getName());
                Object fallback = defaultOf(components[i].getName());
                args[i] = value != null && fallback.getClass()
                    .isInstance(value) ? value : fallback;
            }
            return Tunables.class.getDeclaredConstructor(types)
                .newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The weather's loss on calls and satellite hops, from Minecraft's rain strength and its
     * thunder strength (already weighed by the rain), each 0 to 1: the rain's loss in rain, the storm's in a storm.
     */
    public double weatherLoss(double rain, double thunder) {
        return weatherRainLoss * rain + (weatherStormLoss - weatherRainLoss) * thunder;
    }

    public LevelScale levelScale() {
        return new LevelScale(levelLow, levelMedium, levelHigh, levelMax, levelHysteresis);
    }

    public DropoutModel dropoutModel(double frameMillis) {
        return new DropoutModel(
            dropoutMaxLoss,
            dropoutFullLossSnr,
            dropoutCleanSnr,
            dropoutMeanBurstMillis,
            frameMillis);
    }

    /** A cable's data rate in KB/s. */
    public int cableRate(CableTier tier) {
        return switch (tier) {
            case PHONE -> cableRatePhone;
            case COAXIAL -> cableRateCoaxial;
            case FIBRE -> cableRateFibre;
        };
    }

    /** The most cable blocks a bus of a tier carries over; one longer carries nothing. */
    public int cableReach(CableTier tier) {
        return switch (tier) {
            case PHONE -> cableReachPhone;
            case COAXIAL -> cableReachCoaxial;
            case FIBRE -> cableReachFibre;
        };
    }

    /** How many calls a router of a cable tier's voltage carries at once. */
    public int routerCalls(CableTier tier) {
        return switch (tier) {
            case PHONE -> routerCallsLV;
            case COAXIAL -> routerCallsMV;
            case FIBRE -> routerCallsHV;
        };
    }

    /** What a router of a cable tier's voltage draws in EU/t: routerPower at LV, four times as much each tier up. */
    public long routerPower(CableTier tier) {
        return (long) routerPower << 2 * (tier.voltageTier() - 1);
    }

    /**
     * What an Exchange's hall draws in EU/t before its racks, by its GT voltage tier, 1 LV to 3 HV: exchangePower at
     * LV, four times as much each tier up.
     */
    public long exchangePower(int voltageTier) {
        return (long) exchangePower << 2 * (Math.max(1, voltageTier) - 1);
    }

    /** What a Switching Rack of a cable tier's voltage draws in EU/t: rackPower at LV, four times as much each up. */
    public long rackPower(CableTier tier) {
        return (long) rackPower << 2 * (tier.voltageTier() - 1);
    }

    /** How many calls a Switching Rack of a cable tier's voltage switches at once. */
    public int rackCalls(CableTier tier) {
        return switch (tier) {
            case PHONE -> rackCallsLV;
            case COAXIAL -> rackCallsMV;
            case FIBRE -> rackCallsHV;
        };
    }

    /** How many calls an antenna carries at once, by tier 1 to 3. */
    public int antennaCalls(int tier) {
        return switch (tier) {
            case 1 -> antennaCallsT1;
            case 2 -> antennaCallsT2;
            default -> antennaCallsT3;
        };
    }

    /** A radio hop's data rate in KB/s at a level; nothing below Low. */
    public int radioRate(SignalLevel level) {
        return switch (level) {
            case NONE -> 0;
            case LOW -> radioRateLow;
            case MEDIUM -> radioRateMedium;
            case HIGH -> radioRateHigh;
            case MAX -> radioRateMax;
        };
    }

    /** Radio channels' band: longer waves bend over hills better. */
    public Band vhf() {
        return new Band("VHF", vhfWavelength, vhfCoverPerBlock);
    }

    /** Calls' band. */
    public Band uhf() {
        return new Band("UHF", uhfWavelength, uhfCoverPerBlock);
    }

    public RadioModel radioModel() {
        return new RadioModel(terrainLossCap, coverLossCap, noSkyNoise);
    }

    /** An antenna's link budget, by tier 1 to 3. */
    public double antennaBudget(int tier) {
        return switch (tier) {
            case 1 -> antennaBudgetT1;
            case 2 -> antennaBudgetT2;
            default -> antennaBudgetT3;
        };
    }

    /** An antenna's shortest mast in frame boxes, by tier 1 to 3. */
    public int antennaMastMin(int tier) {
        return switch (tier) {
            case 1 -> antennaMastMinT1;
            case 2 -> antennaMastMinT2;
            default -> antennaMastMinT3;
        };
    }

    /** An antenna's power draw in EU/t, by tier 1 to 3. */
    public int antennaPower(int tier) {
        return switch (tier) {
            case 1 -> antennaPowerT1;
            case 2 -> antennaPowerT2;
            default -> antennaPowerT3;
        };
    }

    /** How far a radio with {@code budget} reaches Low on open ground, in blocks. */
    public double rangeToLow(double budget) {
        return RadioModel.rangeFor(budget, levelLow);
    }

    /**
     * How far a link already at Low holds on open ground: a level is left only {@code levelHysteresis} below its
     * threshold. Anything that decides whether a link is worth working out reaches this far, or the
     * hysteresis would stop at the edge of range and links there would flicker.
     */
    public double rangeToHold(double budget) {
        return RadioModel.rangeFor(budget, levelLow - levelHysteresis);
    }
}
