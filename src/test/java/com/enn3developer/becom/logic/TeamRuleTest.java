package com.enn3developer.becom.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The owner's team counts, its owner, moderators and members; allies and strangers don't. */
class TeamRuleTest {

    /** A team as ServerUtilities keeps one: an owner, moderators, allies; its members are the players in it. */
    private static final class Team {

        final boolean real;
        UUID owner;
        final Set<UUID> moderators = new HashSet<>();
        final Set<UUID> allies = new HashSet<>();

        Team(boolean real) {
            this.real = real;
        }
    }

    private final UUID owner = UUID.randomUUID();
    private final UUID moderator = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID ally = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final UUID loner = UUID.randomUUID();
    private final UUID otherLoner = UUID.randomUUID();
    private final UUID unknown = UUID.randomUUID();

    // Like ServerUtilities, every player it knows is in a team: the ones with none share one stand-in.
    private final Team noTeam = new Team(false);
    private final Team red = new Team(true);
    private final Team blue = new Team(true);
    private final Map<UUID, Team> known = new HashMap<>();

    private final TeamRule.Teams<Team> teams = new TeamRule.Teams<>() {

        @Override
        public Team teamOf(UUID player) {
            return known.get(player);
        }

        @Override
        public boolean isTeam(Team team) {
            return team.real;
        }

        @Override
        public TeamRule.Standing standing(Team team, UUID player) {
            Team theirs = known.get(player);
            if (theirs == null) return TeamRule.Standing.NONE;
            if (!team.real || theirs != team) {
                return team.allies.contains(player) ? TeamRule.Standing.ALLY : TeamRule.Standing.NONE;
            }
            if (player.equals(team.owner)) return TeamRule.Standing.OWNER;
            return team.moderators.contains(player) ? TeamRule.Standing.MODERATOR : TeamRule.Standing.MEMBER;
        }
    };

    @BeforeEach
    void teams() {
        red.owner = owner;
        known.put(owner, red);
        known.put(moderator, red);
        red.moderators.add(moderator);
        known.put(member, red);
        // The ally runs their own team, which red's owner made an ally of theirs.
        blue.owner = ally;
        known.put(ally, blue);
        red.allies.add(ally);
        known.put(stranger, blue);
        known.put(loner, noTeam);
        known.put(otherLoner, noTeam);
    }

    @Test
    void theOwnerCountsForThemselvesAndForTheirTeam() {
        assertTrue(TeamRule.sameTeam(owner, owner, teams));
        // A machine a member placed: the team's owner is in its owner's team.
        assertTrue(TeamRule.sameTeam(member, owner, teams));
    }

    @Test
    void aMemberCounts() {
        assertTrue(TeamRule.sameTeam(owner, member, teams));
    }

    @Test
    void aModeratorCounts() {
        assertTrue(TeamRule.sameTeam(owner, moderator, teams));
    }

    @Test
    void anAllyDoesNot() {
        assertFalse(TeamRule.sameTeam(owner, ally, teams));
        assertFalse(TeamRule.sameTeam(ally, owner, teams));
    }

    @Test
    void aStrangerDoesNot() {
        assertFalse(TeamRule.sameTeam(owner, stranger, teams));
        assertFalse(TeamRule.sameTeam(stranger, owner, teams));
    }

    @Test
    void twoPlayersWithNoTeamAreNotOne() {
        // Both are in the same stand-in, so comparing their teams would call them one.
        assertFalse(TeamRule.sameTeam(loner, otherLoner, teams));
        assertFalse(TeamRule.sameTeam(owner, loner, teams));
        assertTrue(TeamRule.sameTeam(loner, loner, teams));
    }

    @Test
    void anUnknownPlayerIsOnlyThemselves() {
        assertFalse(TeamRule.sameTeam(owner, unknown, teams));
        assertFalse(TeamRule.sameTeam(unknown, owner, teams));
        assertTrue(TeamRule.sameTeam(unknown, unknown, teams));
        // Nor is anyone in the team of something nobody owns.
        assertFalse(TeamRule.sameTeam(null, member, teams));
    }

    @Test
    void withoutServerUtilitiesOnlyTheOwnerAndOperatorsManage() {
        assertFalse(TeamRule.sameTeam(owner, member, TeamRule.Teams.NONE));
        assertTrue(TeamRule.sameTeam(owner, owner, TeamRule.Teams.NONE));
        assertTrue(OwnerRule.mayManage(owner, owner, false, (o, p) -> TeamRule.sameTeam(o, p, TeamRule.Teams.NONE)));
        assertFalse(OwnerRule.mayManage(member, owner, false, (o, p) -> TeamRule.sameTeam(o, p, TeamRule.Teams.NONE)));
        assertTrue(OwnerRule.mayManage(stranger, owner, true, (o, p) -> TeamRule.sameTeam(o, p, TeamRule.Teams.NONE)));
    }

    @Test
    void ownersTeamsManageThroughTheOwnerRule() {
        assertTrue(OwnerRule.mayManage(member, owner, false, (o, p) -> TeamRule.sameTeam(o, p, teams)));
        assertFalse(OwnerRule.mayManage(ally, owner, false, (o, p) -> TeamRule.sameTeam(o, p, teams)));
    }
}
