package com.enn3developer.becom.server.teams;

import java.util.UUID;

import com.enn3developer.becom.logic.TeamRule;

import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.data.Universe;

/**
 * ServerUtilities' teams (D59), the one class that touches ServerUtilities: {@code Ownership} loads it by name, and
 * only when ServerUtilities is installed. Server thread only: ServerUtilities' universe is the server thread's.
 */
public final class ServerUtilitiesTeams implements TeamRule.Teams<ForgeTeam> {

    @Override
    public ForgeTeam teamOf(UUID player) {
        ForgePlayer known = known(player);
        return known == null ? null : known.team;
    }

    @Override
    public boolean isTeam(ForgeTeam team) {
        // False for the "no team" every player without one is in.
        return team.isValid();
    }

    @Override
    public TeamRule.Standing standing(ForgeTeam team, UUID player) {
        ForgePlayer known = known(player);
        if (known == null) return TeamRule.Standing.NONE;
        if (!team.isMember(known)) return team.isAlly(known) ? TeamRule.Standing.ALLY : TeamRule.Standing.NONE;
        if (team.isOwner(known)) return TeamRule.Standing.OWNER;
        return team.isModerator(known) ? TeamRule.Standing.MODERATOR : TeamRule.Standing.MEMBER;
    }

    /** The player ServerUtilities knows by {@code id}, or null: it doesn't, or its universe isn't loaded. */
    private static ForgePlayer known(UUID id) {
        return Universe.loaded() ? Universe.get()
            .getPlayer(id) : null;
    }
}
