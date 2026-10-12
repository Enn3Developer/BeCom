package com.enn3developer.becom.logic;

/**
 * The five levels every BeCom link ends up at (DESIGN.md 4). Declared weakest first, so {@link #ordinal()} orders
 * them and the wire format is the ordinal.
 */
public enum SignalLevel {

    NONE,
    LOW,
    MEDIUM,
    HIGH,
    MAX;

    private static final SignalLevel[] VALUES = values();

    public boolean atLeast(SignalLevel other) {
        return ordinal() >= other.ordinal();
    }

    public static SignalLevel min(SignalLevel a, SignalLevel b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }

    public static SignalLevel max(SignalLevel a, SignalLevel b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    /** The level for a wire ordinal; anything out of range reads as {@link #NONE}. */
    public static SignalLevel byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NONE;
    }

    /** The level one step up, or this one at {@link #MAX}. */
    public SignalLevel up() {
        return this == MAX ? MAX : VALUES[ordinal() + 1];
    }
}
