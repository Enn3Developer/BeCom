package com.enn3developer.becom.logic.phone;

import com.enn3developer.becom.logic.SignalLevel;

/** Every kind of phone. Saved by name, so constants can be added but never renamed. */
public enum PhoneKind {

    /** The wired desk phone. */
    TELEPHONE(SignalLevel.LOW, false, false),
    /** The Mobile Phone: carried, with service through antennas on UHF. */
    MOBILE(SignalLevel.HIGH, true, true),
    /** The ME Phone: carried, with service inside its ME network's wireless range. */
    ME_PHONE(SignalLevel.MAX, true, true),
    /** The Videophone: a speakerphone block on a router's bus, like a Telephone. */
    VIDEOPHONE(SignalLevel.HIGH, false, true),
    /** A computer with a BeCom Card: it has a number for apps' data, but takes no calls. */
    COMPUTER(SignalLevel.HIGH, false, false),
    /** The App Store Mirror: a block on a bus that phones download store apps from; it takes no calls or data. */
    MIRROR(SignalLevel.MAX, false, false);

    private final SignalLevel cap;
    private final boolean carried;
    private final boolean video;

    PhoneKind(SignalLevel cap, boolean carried, boolean video) {
        this.cap = cap;
        this.carried = carried;
        this.video = video;
    }

    /** The best level a call through this phone reaches. */
    public SignalLevel cap() {
        return cap;
    }

    /** A phone players carry, not a block on a line: it has service where its holder is. */
    public boolean carried() {
        return carried;
    }

    /** Has a screen and a camera for video calls. */
    public boolean video() {
        return video;
    }
}
