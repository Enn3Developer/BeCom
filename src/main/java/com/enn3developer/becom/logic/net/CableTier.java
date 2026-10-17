package com.enn3developer.becom.logic.net;

/**
 * The three cables: Phone Cable, Coaxial and Fibre, one per voltage tier from LV. Wire is digital: a
 * cable carries its data rate the whole of its reach and nothing past it, whatever the weather. The ordinal is the
 * cable block's metadata and the saved and wire format, so constants can be added but never reordered.
 */
public enum CableTier {

    PHONE("Phone", "LV", 1),
    COAXIAL("Coaxial", "MV", 2),
    FIBRE("Fibre", "HV", 3);

    private static final CableTier[] VALUES = values();

    private final String configName;
    private final String voltage;
    private final int voltageTier;

    CableTier(String configName, String voltage, int voltageTier) {
        this.configName = configName;
        this.voltage = voltage;
        this.voltageTier = voltageTier;
    }

    /** How config keys name it: cableRatePhone, cableReachFibre. */
    public String configName() {
        return configName;
    }

    /** Its GT voltage's short name, which config keys of tiered machines use: terminalPortsMV. */
    public String voltage() {
        return voltage;
    }

    /** Its GT voltage tier: 1 for LV. */
    public int voltageTier() {
        return voltageTier;
    }

    /** The tier for a saved ordinal or block metadata, or null for one this BeCom doesn't know. */
    public static CableTier byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
    }

    /** The tier for a GT voltage tier, the highest for anything above Fibre's and the lowest below Phone's. */
    public static CableTier ofVoltage(int voltageTier) {
        for (int i = VALUES.length - 1; i >= 0; i--) if (VALUES[i].voltageTier <= voltageTier) return VALUES[i];
        return VALUES[0];
    }

    public static CableTier min(CableTier a, CableTier b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
