package com.enn3developer.becom.logic.phone;

import com.enn3developer.becom.logic.SignalLevel;

/** Every kind of phone (DESIGN.md 8.2). Saved by name, so constants can be added but never renamed. */
public enum PhoneKind {

    /** The wired desk phone (DESIGN.md 5.6). */
    TELEPHONE(SignalLevel.LOW);

    private final SignalLevel cap;

    PhoneKind(SignalLevel cap) {
        this.cap = cap;
    }

    /** The best level a call through this phone reaches (D10). */
    public SignalLevel cap() {
        return cap;
    }
}
