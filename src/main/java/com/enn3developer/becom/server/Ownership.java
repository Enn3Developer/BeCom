package com.enn3developer.becom.server;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.util.FakePlayer;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.OwnerRule;
import com.enn3developer.becom.logic.TeamRule;

import cpw.mods.fml.common.Loader;

/**
 * Who owns what: the player who placed a machine, and their team, which is ServerUtilities'.
 * BeCom has no teams of its own. Server thread only: ServerUtilities' universe is the server thread's.
 */
public final class Ownership {

    public static final String SERVER_UTILITIES_MODID = "serverutilities";
    // Operators may always undo a link, so a griefed network can be repaired without the owner.
    private static final int OP_LEVEL = 2;

    private static TeamRule.Teams<?> teams = TeamRule.Teams.NONE;

    private Ownership() {}

    /**
     * Reads teams from ServerUtilities when it's installed; called once at startup. Without it, nobody has one.
     */
    public static void init() {
        if (!Loader.isModLoaded(SERVER_UTILITIES_MODID)) {
            BeCom.LOG.info("ServerUtilities isn't installed: only owners and operators manage BeCom's machines");
            return;
        }
        try {
            // By name, so a server without ServerUtilities never loads a class that refers to it.
            teams = (TeamRule.Teams<?>) Class.forName("com.enn3developer.becom.server.teams.ServerUtilitiesTeams")
                .getConstructor()
                .newInstance();
        } catch (Throwable t) {
            BeCom.LOG
                .error("Couldn't read ServerUtilities' teams: only owners and operators manage BeCom's machines", t);
        }
    }

    /** Whether {@code player} is {@code owner}, or in their ServerUtilities team (TeamRule). */
    public static boolean sameTeam(UUID owner, UUID player) {
        try {
            return TeamRule.sameTeam(owner, player, teams);
        } catch (LinkageError e) {
            // A ServerUtilities without what BeCom was built against: carry on as if it weren't installed.
            BeCom.LOG.error("ServerUtilities' teams don't match the version BeCom was built against; teams are off", e);
            teams = TeamRule.Teams.NONE;
            return TeamRule.sameTeam(owner, player, teams);
        }
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
