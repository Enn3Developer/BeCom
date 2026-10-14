package com.enn3developer.becom.server.teams;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.enn3developer.becom.logic.TeamRule;
import com.mojang.authlib.GameProfile;

import serverutils.lib.EnumTeamStatus;
import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.data.TeamType;
import serverutils.lib.data.Universe;

/**
 * ServerUtilities' teams (D59), the one class that touches ServerUtilities: {@code Ownership} loads it by name, and
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
        // first played alone keeps: ServerUtilities makes every player its moderator, so anyone may join it (X46).
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

    /**
     * Self-test: runs {@code inTeam} with a team that {@code owner} owns, {@code moderator} and {@code member} are in
     * and {@code ally} is an ally of, while {@code stranger} has none. Then runs {@code inSinglePlayerTeam} with
     * {@code owner} and {@code stranger} in a "singleplayer" team, as a world first played alone keeps it and anyone
     * may join it. ServerUtilities never sees the self-test's fake players log in, so it learns them here, and forgets
     * them again before returning: nothing is left for it to save. Neither team is added to its list, so no other
     * player
     * can find them. False when its universe isn't loaded.
     */
    public static boolean withTeamForSelfTest(GameProfile owner, GameProfile moderator, GameProfile member,
        GameProfile ally, GameProfile stranger, Runnable inTeam, Runnable inSinglePlayerTeam) {
        if (!Universe.loaded()) return false;
        Universe universe = Universe.get();
        Map<UUID, ForgePlayer> before = new HashMap<>();
        try {
            Map<UUID, ForgePlayer> made = new HashMap<>();
            for (GameProfile profile : new GameProfile[] { owner, moderator, member, ally, stranger }) {
                if (!before.containsKey(profile.getId())) {
                    before.put(profile.getId(), universe.players.get(profile.getId()));
                }
                ForgePlayer player = new ForgePlayer(universe, profile.getId(), profile.getName());
                universe.players.put(profile.getId(), player);
                made.put(profile.getId(), player);
            }
            ForgeTeam team = new ForgeTeam(
                universe,
                universe.generateTeamUID((short) 0),
                "becomselftest",
                TeamType.PLAYER);
            team.owner = made.get(owner.getId());
            for (GameProfile in : new GameProfile[] { owner, moderator, member }) made.get(in.getId()).team = team;
            // Straight into the team's map: setStatus would mark the universe for saving.
            team.players.put(made.get(moderator.getId()), EnumTeamStatus.MOD);
            team.players.put(made.get(ally.getId()), EnumTeamStatus.ALLY);
            inTeam.run();
            ForgeTeam single = new ForgeTeam(
                universe,
                universe.generateTeamUID((short) 0),
                SINGLE_PLAYER_TEAM,
                TeamType.SERVER);
            made.get(owner.getId()).team = single;
            made.get(stranger.getId()).team = single;
            inSinglePlayerTeam.run();
        } finally {
            for (Map.Entry<UUID, ForgePlayer> entry : before.entrySet()) {
                if (entry.getValue() == null) universe.players.remove(entry.getKey());
                else universe.players.put(entry.getKey(), entry.getValue());
            }
        }
        return true;
    }
}
