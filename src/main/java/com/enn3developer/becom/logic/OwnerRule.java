package com.enn3developer.becom.logic;

import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Who may act as an owner (DESIGN.md 12.1, 12.2, D37): the owner, their team, and operators. Something with no owner,
 * like a machine placed by automation, belongs to nobody, so only operators manage it; a Telephone without one goes
 * to its first user instead (12.1).
 */
public final class OwnerRule {

    private OwnerRule() {}

    public static boolean mayManage(UUID player, UUID owner, boolean operator, BiPredicate<UUID, UUID> sameTeam) {
        if (operator) return true;
        if (player == null || owner == null) return false;
        return player.equals(owner) || sameTeam.test(player, owner);
    }
}
