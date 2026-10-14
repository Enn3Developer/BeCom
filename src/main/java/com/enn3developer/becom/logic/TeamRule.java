package com.enn3developer.becom.logic;

import java.util.UUID;

/**
 * Who is in an owner's team (DESIGN.md 12.1, D59): the members of the team the owner is in, its owner and moderators
 * included. Its allies aren't, and two players with no team aren't one. A player the team system doesn't know, or no
 * team system at all, leaves only the owner.
 */
public final class TeamRule {

    /** A player's standing in a team, as the team system has it. */
    public enum Standing {
        NONE,
        ALLY,
        MEMBER,
        MODERATOR,
        OWNER
    }

    /**
     * What the rule reads from a team system: ServerUtilities' in the game.
     *
     * @param <T> the system's team
     */
    public interface Teams<T> {

        /** No team system: nobody is known, so nobody has a team. */
        Teams<Object> NONE = new Teams<>() {

            @Override
            public Object teamOf(UUID player) {
                return null;
            }

            @Override
            public boolean isTeam(Object team) {
                return false;
            }

            @Override
            public Standing standing(Object team, UUID player) {
                return Standing.NONE;
            }
        };

        /**
         * The team {@code player} is in, or null when the system doesn't know them. Players with no team can all be in
         * the same stand-in for none, as ServerUtilities' are.
         */
        T teamOf(UUID player);

        /** Whether {@code team} is a real team, not the stand-in for none. */
        boolean isTeam(T team);

        /** {@code player}'s standing in {@code team}: NONE for a player the system doesn't know. */
        Standing standing(T team, UUID player);
    }

    private TeamRule() {}

    /**
     * Whether {@code player} is the owner, or in {@code owner}'s team. The owner's team is asked about the player, and
     * never compared with the player's own: everyone with no team is in the same stand-in, and that makes nobody a
     * team.
     */
    public static <T> boolean sameTeam(UUID owner, UUID player, Teams<T> teams) {
        if (owner == null || player == null) return false;
        if (owner.equals(player)) return true;
        T team = teams.teamOf(owner);
        if (team == null || !teams.isTeam(team)) return false;
        return switch (teams.standing(team, player)) {
            case OWNER, MODERATOR, MEMBER -> true;
            case ALLY, NONE -> false;
        };
    }
}
