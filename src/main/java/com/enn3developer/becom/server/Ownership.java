package com.enn3developer.becom.server;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.util.FakePlayer;

import com.enn3developer.becom.logic.OwnerRule;

import gregtech.common.misc.spaceprojects.SpaceProjectManager;

/**
 * Who owns what (DESIGN.md 12.1): the player who placed a machine, and their GT team, the one GT uses for wireless EU
 * and space projects. BeCom has no teams of its own.
 */
public final class Ownership {

    // Operators may always undo a link, so a griefed network can be repaired without the owner.
    private static final int OP_LEVEL = 2;

    private Ownership() {}

    /** Whether two players are in the same GT team. Reads GT's teams without creating any. */
    public static boolean sameTeam(UUID a, UUID b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        UUID leaderA = SpaceProjectManager.spaceTeams.getOrDefault(a, a);
        UUID leaderB = SpaceProjectManager.spaceTeams.getOrDefault(b, b);
        return leaderA.equals(leaderB);
    }

    /**
     * Whether {@code player} may act as the owner of something owned by {@code owner} (OwnerRule): the owner, their
     * team or an operator. Nothing with no owner is open to everyone.
     */
    public static boolean mayManage(EntityPlayer player, UUID owner) {
        boolean operator = player instanceof EntityPlayerMP mp && !(player instanceof FakePlayer)
            && mp.canCommandSenderUseCommand(OP_LEVEL, "becom");
        return OwnerRule.mayManage(player.getUniqueID(), owner, operator, Ownership::sameTeam);
    }
}
