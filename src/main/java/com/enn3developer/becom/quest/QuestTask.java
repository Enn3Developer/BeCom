package com.enn3developer.becom.quest;

/**
 * The things BeCom's own BetterQuesting task type waits for. Saved by name in quest files, so
 * constants can be added but never renamed.
 */
public enum QuestTask {
    /** A call connects: everyone in it. */
    FIRST_CALL,
    /** BeCom carries a radio frame from one player to another: the speaker and the listener. */
    RADIO_CONTACT,
    /** Live video from someone in their call reaches a player: the viewer. */
    VIDEO_CALL,
    /** A call connects: everyone in it on a Mobile Phone. */
    MOBILE_CALL,
    /** A call connects whose path goes through an ME Bridge: the ME Phone's user. */
    ME_BRIDGE_CALL,
    /** The second Portal Relay of a pair is linked: whoever links it. */
    PORTAL_RELAY_PAIR,
    /** A launch from their pad puts a satellite into orbit: the Launch Pad's owner. */
    FIRST_SATELLITE,
    /** A launch from their pad leaves its dimension with at least 5 satellites: the pad's owner. */
    FIVE_SATELLITES,
    /** A call connects with people in different dimensions: everyone in it. */
    CALL_BETWEEN_DIMENSIONS
}
