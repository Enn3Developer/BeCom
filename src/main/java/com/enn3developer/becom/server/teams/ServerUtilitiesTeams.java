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

    /**
     * Self-test: runs {@code check} with a team that {@code owner} owns, {@code moderator} and {@code member} are in
     * and {@code ally} is an ally of, while {@code stranger} has none. ServerUtilities never sees the self-test's fake
     * players log in, so it learns them here, and forgets them again before returning: nothing is left for it to save.
     * The team is never added to its list, so no other player can find it. False when its universe isn't loaded.
     */
    public static boolean withTeamForSelfTest(GameProfile owner, GameProfile moderator, GameProfile member,
        GameProfile ally, GameProfile stranger, Runnable check) {
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
            check.run();
        } finally {
            for (Map.Entry<UUID, ForgePlayer> entry : before.entrySet()) {
                if (entry.getValue() == null) universe.players.remove(entry.getKey());
                else universe.players.put(entry.getKey(), entry.getValue());
            }
        }
        return true;
    }
}
