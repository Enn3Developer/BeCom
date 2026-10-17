package com.enn3developer.becom.logic;

import com.enn3developer.becom.logic.radio.Band;
import com.enn3developer.becom.logic.radio.RadioModel;

/**
 * Every server-side tunable from DESIGN.md 17 that the code reads, as loaded from the config at startup (DESIGN.md
 * 14.2). Immutable and free of Minecraft types, so logic and tests can take it as-is.
 */
public record Tunables(
    // Signal (DESIGN.md 4)
    double levelLow, double levelMedium, double levelHigh, double levelMax, double levelHysteresis,
    double dropoutMaxLoss, double dropoutFullLossSnr, double dropoutCleanSnr, double dropoutMeanBurstMillis,
    double dropAfterSeconds,
    // Wired network (DESIGN.md 5)
    int pylonMinHeight, int pylonMaxHeight, double pylonSpanFactor, int pylonClearanceSeconds, double lineBudget,
    double lineLossPerBlock, int phoneCableLength, int terminalPower, int repeaterPower, int routerPower,
    // Calls (DESIGN.md 8.2, 10, 12)
    double directHearingRange, int sessionGraceSeconds, int halfLinkSeconds, int ringSeconds, int callAttemptsPerMinute,
    // Radio simulation (DESIGN.md 6)
    double vhfWavelength, double uhfWavelength, double vhfCoverPerBlock, double uhfCoverPerBlock, double coverLossCap,
    double terrainLossCap, double noSkyNoise, int surveyBearings, int surveyStep, int surveySeconds,
    double surveyBudgetMillis,
    // Antennas (DESIGN.md 7)
    double antennaBudgetT1, double antennaBudgetT2, double antennaBudgetT3, int antennaPowerT1, int antennaPowerT2,
    int antennaPowerT3, int antennaMastMinT1, int antennaMastMinT2, int antennaMastMinT3, int antennaMastMax,
    double wrenchLinkDistance,
    // Radios (DESIGN.md 8, 11.3, 11.4)
    double transceiverBudget, double walkieBudget, int transceiverPower, double loudspeakerRange, double talkRange,
    double walkieSpeakerRange, int captureHoldMillis, double captureMargin, int walkieBattery, int walkieStandbyDrain,
    int walkieReceiveDrain, int walkieTransmitDrain, int analyzerRefreshSeconds,
    // Phones (DESIGN.md 6.1, 8.2, 11.2, D26, D36)
    int groupCallSize, int phoneBattery, int phoneStandbyDrain, int phoneCallDrain, double weatherRainLoss,
    double weatherStormLoss,
    // Video (DESIGN.md 10.1, 10.2, 14)
    int videoPoseRate, int videoHeightMedium, int videoHeightHigh, int videoHeightMax, int videoFpsMedium,
    int videoFpsHigh, int videoFpsMax, int rearWidthHigh, int rearHeightHigh, int rearFpsHigh, int rearWidthMax,
    int rearHeightMax, int rearFpsMax, int rearMaxBytes, int rearMaxFps, int videophonePower, double videophoneRoom,
    double videophoneLoudspeaker, int videophoneEmptySeconds,
    // ME (DESIGN.md 8.3)
    int meBridgePower, int meBridgeChannels,
    // Space (DESIGN.md 9): levels by number, 1 Low to 4 Max
    int satellitesLow, int satellitesMax, double satelliteLifetimeDays, double satelliteLifetimeSpread,
    int launchSeconds, int launchPower, int launchFuel, int dishPower, double dishMaintenanceLoss,
    int receiverUplinkLevel, int relayPower, double relayPortalDistance, int relayHopLevel,
    // Quests (DESIGN.md 15.5)
    boolean questlineEnabled, int questlineRecheckSeconds) {

    // Ceilings no sensible config reaches, which keep a hostile server's values from hanging a client: no frame column
    // is taller than the world, and a terminal's cable scan stays bounded.
    public static final int MAX_COLUMN = 255;
    public static final int MAX_CABLE = 256;
    // Radio work grows with range and bearings: a survey reads bearings x range / step heights, a direct link
    // range / step. Far past DESIGN.md's numbers (256 blocks, 64 bearings), these keep one survey or link bounded.
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

    // The values DESIGN.md 17 starts from, by component name. Doubles are written as doubles, ints as ints: fromMap
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
        d.put("pylonMinHeight", 6);
        d.put("pylonMaxHeight", 16);
        d.put("pylonSpanFactor", 4.0);
        d.put("pylonClearanceSeconds", 60);
        d.put("lineBudget", 30.0);
        d.put("lineLossPerBlock", 0.08);
        d.put("phoneCableLength", 32);
        d.put("terminalPower", 4);
        d.put("repeaterPower", 4);
        d.put("routerPower", 16);
        d.put("directHearingRange", 16.0);
        d.put("sessionGraceSeconds", 10);
        d.put("halfLinkSeconds", 5 * 60);
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
        d.put("antennaPowerT1", 32);
        d.put("antennaPowerT2", 128);
        d.put("antennaPowerT3", 512);
        d.put("antennaMastMinT1", 6);
        d.put("antennaMastMinT2", 8);
        d.put("antennaMastMinT3", 10);
        d.put("antennaMastMax", 32);
        d.put("wrenchLinkDistance", 16.0);
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
        d.put("meBridgePower", 512);
        d.put("meBridgeChannels", 1);
        d.put("satellitesLow", 5);
        d.put("satellitesMax", 100);
        d.put("satelliteLifetimeDays", 100.0);
        d.put("satelliteLifetimeSpread", 0.2);
        d.put("launchSeconds", 30);
        d.put("launchPower", 480);
        d.put("launchFuel", 1000);
        d.put("dishPower", 512);
        d.put("dishMaintenanceLoss", 2.0);
        d.put("receiverUplinkLevel", 3);
        d.put("relayPower", 32);
        d.put("relayPortalDistance", 4.0);
        d.put("relayHopLevel", 3);
        d.put("questlineEnabled", true);
        d.put("questlineRecheckSeconds", 60);
    }

    /** The values DESIGN.md 17 starts from. */
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
        if (pylonMinHeight < 1) throw invalid("pylonMinHeight", pylonMinHeight, "at least 1");
        if (pylonMaxHeight < pylonMinHeight) throw invalid("pylonMaxHeight", pylonMaxHeight, "at least pylonMinHeight");
        if (pylonMaxHeight > MAX_COLUMN) throw invalid("pylonMaxHeight", pylonMaxHeight, "at most " + MAX_COLUMN);
        if (phoneCableLength < 1 || phoneCableLength > MAX_CABLE) {
            throw invalid("phoneCableLength", phoneCableLength, "1 to " + MAX_CABLE);
        }
        if (pylonSpanFactor <= 0) throw invalid("pylonSpanFactor", pylonSpanFactor, "more than 0");
        if (pylonClearanceSeconds < 1) throw invalid("pylonClearanceSeconds", pylonClearanceSeconds, "at least 1");
        if (lineLossPerBlock < 0) throw invalid("lineLossPerBlock", lineLossPerBlock, "0 or more");
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
        if (wrenchLinkDistance < 0) throw invalid("wrenchLinkDistance", wrenchLinkDistance, "0 or more");
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

    /** The level of a Rocket Radio Receiver's uplink to the Overworld's satellites (DESIGN.md 9.3). */
    public SignalLevel receiverUplink() {
        return SignalLevel.byOrdinal(receiverUplinkLevel);
    }

    /** The level of the hop between a linked pair of Portal Relays (DESIGN.md 9.4). */
    public SignalLevel relayHop() {
        return SignalLevel.byOrdinal(relayHopLevel);
    }

    /** Every value by its name, for sending to clients (DESIGN.md 14.2). Doubles, ints and booleans only. */
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
     * The weather's loss on calls and satellite hops (D26, DESIGN.md 6.1), from Minecraft's rain strength and its
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

    public LineModel lineModel() {
        return new LineModel(lineBudget, lineLossPerBlock);
    }

    /** Radio channels' band (DESIGN.md 6.1): longer waves bend over hills better. */
    public Band vhf() {
        return new Band("VHF", vhfWavelength, vhfCoverPerBlock);
    }

    /** Calls' band (DESIGN.md 6.1). */
    public Band uhf() {
        return new Band("UHF", uhfWavelength, uhfCoverPerBlock);
    }

    public RadioModel radioModel() {
        return new RadioModel(terrainLossCap, coverLossCap, noSkyNoise);
    }

    /** An antenna's link budget, by tier 1 to 3 (DESIGN.md 7). */
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

    /** An antenna's power draw in EU/t, by tier 1 to 3 (DESIGN.md 7). */
    public int antennaPower(int tier) {
        return switch (tier) {
            case 1 -> antennaPowerT1;
            case 2 -> antennaPowerT2;
            default -> antennaPowerT3;
        };
    }

    /** How far a radio with {@code budget} reaches Low on open ground, in blocks (DESIGN.md 7, 8.1). */
    public double rangeToLow(double budget) {
        return RadioModel.rangeFor(budget, levelLow);
    }

    /**
     * How far a link already at Low holds on open ground: a level is left only {@code levelHysteresis} below its
     * threshold (DESIGN.md 4.1). Anything that decides whether a link is worth working out reaches this far, or the
     * hysteresis would stop at the edge of range and links there would flicker.
     */
    public double rangeToHold(double budget) {
        return RadioModel.rangeFor(budget, levelLow - levelHysteresis);
    }
}
