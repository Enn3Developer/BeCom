package com.enn3developer.becom.server.teams;

import java.util.UUID;

import com.enn3developer.becom.logic.TeamRule;

import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.data.TeamType;
import serverutils.lib.data.Universe;

/**
 * ServerUtilities' teams, the one class that touches ServerUtilities: {@code Ownership} loads it by name, and
 * only when ServerUtilities is installed. Server thread only: ServerUtilities' universe is the server thread's.
 */
public final class ServerUtilitiesTeams implements TeamRule.Teams<ForgeTeam> {

    // The server team ServerUtilities puts single-player and LAN players in, by the id its own ForgeTeam checks.
    private static final String SINGLE_PLAYER_TEAM = "singleplayer";

    @Override
    public ForgeTeam teamOf(UUID player) {
        ForgePlayer known = known(player);
        return known == null ? null : known.team;
    }

    @Override
    public boolean isTeam(ForgeTeam team) {
        // Not the "no team" every player without one is in. Nor, on a dedicated server, the "singleplayer" team a world
        // first played alone keeps: ServerUtilities makes every player its moderator, so anyone may join it.
        return team.isValid() && !(team.type == TeamType.SERVER && SINGLE_PLAYER_TEAM.equals(team.getId())
            && !team.universe.server.isSinglePlayer());
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
