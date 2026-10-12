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
    ME_BRIDGE_CALL,
    /** The second Portal Relay of a pair is linked: whoever links it (DESIGN.md 15.5). */
    PORTAL_RELAY_PAIR,
    /** A launch from their pad puts a satellite into orbit: the Launch Pad's owner (DESIGN.md 15.5). */
    FIRST_SATELLITE,
    /** A launch from their pad leaves its dimension with at least 5 satellites: the pad's owner (DESIGN.md 15.5). */
    FIVE_SATELLITES,
    /** A call connects with people in different dimensions: everyone in it (DESIGN.md 15.5). */
    CALL_BETWEEN_DIMENSIONS
}
