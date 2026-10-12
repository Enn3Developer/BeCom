package com.enn3developer.becom.server;

import org.jetbrains.annotations.NotNull;

import com.enn3developer.gtnhvoice.api.server.IVoiceSession;
import com.enn3developer.gtnhvoice.api.server.IVoiceSessionListener;
import com.enn3developer.gtnhvoice.api.server.SessionEndReason;

/**
 * Follows GTNH Voice sessions (DESIGN.md 13.2). GTNH Voice wipes memberships on logout and timeout, so a player whose
 * session comes back gets theirs again, and a timed-out session gets the grace period before its call ends.
 * Callbacks run on the server thread.
 */
public final class VoiceSessions implements IVoiceSessionListener {

    @Override
    public void sessionStarted(@NotNull IVoiceSession session) {
        BeComServer becom = BeComServer.get();
        if (becom != null) becom.calls.sessionStarted(session.getPlayerUuid());
    }

    @Override
    public void sessionEnded(@NotNull IVoiceSession session, @NotNull SessionEndReason reason) {
        BeComServer becom = BeComServer.get();
        if (becom == null) return;
        switch (reason) {
            case TIMED_OUT -> becom.calls.sessionTimedOut(session.getPlayerUuid());
            // A logout is handled from the player's own logout event, which also covers players without voice.
            case LOGOUT, REPLACED, SERVER_STOPPED -> {}
        }
    }
}
