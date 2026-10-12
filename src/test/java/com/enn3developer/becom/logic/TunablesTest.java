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
}
