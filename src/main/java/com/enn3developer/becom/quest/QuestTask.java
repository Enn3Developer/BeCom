package com.enn3developer.becom.quest;

/**
 * The things BeCom's own BetterQuesting task type waits for (D50, DESIGN.md 15.5). Saved by name in quest files, so
 * constants can be added but never renamed.
 */
public enum QuestTask {
    /** A call connects: everyone in it. */
    FIRST_CALL,
    /** BeCom carries a radio frame from one player to another: the speaker and the listener (DESIGN.md 15.5). */
    RADIO_CONTACT
}
