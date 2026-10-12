package com.enn3developer.becom.logic.phone;

import com.enn3developer.becom.logic.SignalLevel;

/** Every kind of phone (DESIGN.md 8.2). Saved by name, so constants can be added but never renamed. */
public enum PhoneKind {

    /** The wired desk phone (DESIGN.md 5.6). */
    TELEPHONE(SignalLevel.LOW, false, false),
    /** The Mobile Phone (DESIGN.md 8): carried, with service through antennas on UHF. */
    MOBILE(SignalLevel.HIGH, true, true),
    /** The ME Phone (DESIGN.md 8.3): carried, with service inside its ME network's wireless range. */
    ME_PHONE(SignalLevel.MAX, true, true),
    /** The Videophone (DESIGN.md 10.2): a speakerphone block on a Line Terminal, like a Telephone. */
    VIDEOPHONE(SignalLevel.HIGH, false, true);

    private final SignalLevel cap;
    private final boolean carried;
    private final boolean video;

    PhoneKind(SignalLevel cap, boolean carried, boolean video) {
        this.cap = cap;
        this.carried = carried;
        this.video = video;
    }

    /** The best level a call through this phone reaches (D10). */
    public SignalLevel cap() {
        return cap;
    }

    /** A phone players carry, not a block on a line: it has service where its holder is. */
    public boolean carried() {
        return carried;
    }

    /** Has a screen and a camera for video calls (D42). */
    public boolean video() {
        return video;
    }
}
