package com.enn3developer.becom.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.function.BiPredicate;

import org.junit.jupiter.api.Test;

/** DESIGN.md 12.1, 12.2: owners, their teams and operators manage; nothing without an owner is open to all. */
class OwnerRuleTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    // Alice and Bob share a team.
    private final BiPredicate<UUID, UUID> team = (a, b) -> (a.equals(alice) || a.equals(bob))
        && (b.equals(alice) || b.equals(bob));

    @Test
    void ownerAndTeamManage() {
        assertTrue(OwnerRule.mayManage(alice, alice, false, team));
        assertTrue(OwnerRule.mayManage(bob, alice, false, team));
        assertFalse(OwnerRule.mayManage(carol, alice, false, team));
    }

    @Test
    void nobodyButOperatorsManagesWhatNobodyOwns() {
        assertFalse(OwnerRule.mayManage(carol, null, false, team));
        assertTrue(OwnerRule.mayManage(carol, null, true, team));
    }

    @Test
    void operatorsManageAnything() {
        assertTrue(OwnerRule.mayManage(carol, alice, true, team));
    }
}
