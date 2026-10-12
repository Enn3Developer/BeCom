package com.enn3developer.becom.logic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;

import org.junit.jupiter.api.Test;

/** A config value that would crash or overload the server is refused at load, naming the key (DESIGN.md 14.2). */
class TunablesTest {

    /** The defaults with one value changed, by its record component's name. */
    private static Tunables with(String name, Object value) throws Exception {
        Tunables d = Tunables.defaults();
        RecordComponent[] components = Tunables.class.getRecordComponents();
        Object[] args = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            args[i] = components[i].getName()
                .equals(name) ? value
                    : components[i].getAccessor()
                        .invoke(d);
        }
        Constructor<Tunables> constructor = Tunables.class.getDeclaredConstructor(types);
        return constructor.newInstance(args);
    }

    private static void refused(String name, Object value) throws Exception {
        Tunables t = with(name, value);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, t::validate);
        assertTrue(
            e.getMessage()
                .contains(name),
            e.getMessage());
    }

    @Test
    void defaultsAreValid() {
        assertDoesNotThrow(Tunables.defaults()::validate);
    }

    @Test
    void everyDecimalValueMustBeFinite() throws Exception {
        // A config file takes NaN and Infinity; a drop delay of either would hold the self-test's edge step forever.
        for (RecordComponent c : Tunables.class.getRecordComponents()) {
            if (c.getType() != double.class) continue;
            refused(c.getName(), Double.NaN);
            refused(c.getName(), Double.POSITIVE_INFINITY);
        }
    }

    @Test
    void phoneAndVideoValuesMustMakeSense() throws Exception {
        refused("groupCallSize", 1);
        refused("groupCallSize", Tunables.MAX_GROUP_CALL + 1);
        refused("phoneBattery", 0);
        // A storm is rain and thunder: it can't cost less than the rain alone.
        refused("weatherStormLoss", 2.0);
        refused("videoPoseRate", 0);
        refused("videoPoseRate", Tunables.MAX_POSE_RATE + 1);
        refused("videoHeightMax", Tunables.MAX_VIDEO_HEIGHT + 1);
        refused("rearWidthMax", 0);
        refused("rearMaxBytes", Tunables.MAX_REAR_BYTES + 1);
        refused("rearMaxFps", 0);
        refused("videophoneEmptySeconds", 0);
        refused("meBridgeChannels", 2);
        assertDoesNotThrow(with("videoFpsMax", 0)::validate);
    }

    @Test
    void noCallAttemptsAtAllIsRefused() throws Exception {
        refused("callAttemptsPerMinute", 0);
    }

    @Test
    void aBurstShorterThanOneVoiceFrameIsRefused() throws Exception {
        refused("dropoutMeanBurstMillis", 10.0);
        assertDoesNotThrow(with("dropoutMeanBurstMillis", (double) Tunables.FRAME_MILLIS)::validate);
    }

    @Test
    void checksEveryTickAreRefused() throws Exception {
        refused("pylonClearanceSeconds", 0);
        refused("questlineRecheckSeconds", 0);
    }

    @Test
    void pylonHeightsMustMakeSense() throws Exception {
        refused("pylonMinHeight", 0);
        refused("pylonMaxHeight", 5);
        // No column of frames is taller than the world.
        refused("pylonMaxHeight", 2_000_000_000);
    }

    @Test
    void theCeilingsThemselvesAreAllowed() throws Exception {
        assertDoesNotThrow(with("pylonMaxHeight", 255)::validate);
        assertDoesNotThrow(with("phoneCableLength", 256)::validate);
    }

    @Test
    void cableLengthHasACeiling() throws Exception {
        // Past it, a scan would walk hundreds of thousands of blocks, and the cable cap would overflow.
        refused("phoneCableLength", 1 << 30);
        refused("phoneCableLength", 0);
    }

    @Test
    void levelThresholdsMustRise() throws Exception {
        refused("levelMedium", 30.0);
    }

    @Test
    void roundTripsThroughAMapByName() throws Exception {
        Tunables changed = with("pylonSpanFactor", 5.0);
        assertEquals(changed, Tunables.fromMap(changed.toMap()));
    }

    @Test
    void missingOrMistypedValuesKeepTheirDefaults() {
        java.util.Map<String, Object> values = new java.util.HashMap<>(
            Tunables.defaults()
                .toMap());
        values.remove("ringSeconds");
        values.put("lineBudget", "loud");
        values.put("notAKey", 3);
        assertEquals(Tunables.defaults(), Tunables.fromMap(values));
    }

    @Test
    void radioWorkStaysBounded() throws Exception {
        // A budget reaching past MAX_RADIO_RANGE would make one survey or direct link read thousands of heights.
        refused("antennaBudgetT3", 200.0);
        refused("walkieBudget", 200.0);
        refused("surveyBearings", 0);
        refused("surveyBearings", Tunables.MAX_BEARINGS + 1);
        refused("surveyStep", 0);
        refused("surveyBudgetMillis", 0.0);
    }

    @Test
    void antennaMastsMustMakeSense() throws Exception {
        refused("antennaMastMin", 0);
        refused("antennaMastMax", 3);
        refused("antennaMastMax", Tunables.MAX_COLUMN + 1);
    }

    @Test
    void designRangesComeFromTheDefaults() {
        Tunables t = Tunables.defaults();
        // DESIGN.md 7: roughly 64, 128 and 256 blocks to the edge of Low; 8.1: a walkie about 80, a Transceiver 125.
        assertEquals(64, t.rangeToLow(t.antennaBudget(1)), 2);
        assertEquals(128, t.rangeToLow(t.antennaBudget(2)), 3);
        assertEquals(256, t.rangeToLow(t.antennaBudget(3)), 6);
        assertEquals(80, t.rangeToLow(t.walkieBudget()), 2);
        assertEquals(125, t.rangeToLow(t.transceiverBudget()), 2);
    }

    @Test
    void aLinkAtLowHoldsTwoDecibelsFurther() {
        Tunables t = Tunables.defaults();
        // DESIGN.md 4.1: Low is left at 4 dB. A walkie at Low holds to 100 blocks, a T3 to about 316.
        assertEquals(100, t.rangeToHold(t.walkieBudget()), 1e-9);
        assertEquals(316, t.rangeToHold(t.antennaBudget(3)), 1);
        assertTrue(t.rangeToHold(t.walkieBudget()) > t.rangeToLow(t.walkieBudget()));
    }

    @Test
    void aHugeHysteresisCantStretchRadioWorkPastTheCeiling() throws Exception {
        // Links are worked out as far as a link at Low holds (4.1), so the bound covers the hysteresis too: 100 dB of
        // it would make one T3 survey allocate hundreds of megabytes.
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            with("levelHysteresis", 100.0)::validate);
        assertTrue(
            e.getMessage()
                .contains("Budget"),
            e.getMessage());
    }
}
