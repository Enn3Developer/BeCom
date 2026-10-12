package com.enn3developer.becom.quest;

/**
 * The things BeCom's own BetterQuesting task type waits for (D50, DESIGN.md 15.5). Saved by name in quest files, so
 * constants can be added but never renamed.
 */
public enum QuestTask {
    /** A call connects: everyone in it. */
    FIRST_CALL,
    /** BeCom carries a radio frame from one player to another: the speaker and the listener (DESIGN.md 15.5). */
    RADIO_CONTACT,
    /** Live video from someone in their call reaches a player: the viewer (DESIGN.md 15.5). */
    VIDEO_CALL,
    /** A call connects: everyone in it on a Mobile Phone (DESIGN.md 15.5). */
    MOBILE_CALL,
    /** A call connects whose path goes through an ME Bridge: the ME Phone's user (DESIGN.md 15.5). */
    ME_BRIDGE_CALL
}
