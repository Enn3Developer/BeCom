package com.enn3developer.becom.server;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** BeCom's services between a server's stopping and stopped events. */
class BeComServerTest {

    @AfterEach
    void forget() {
        BeComServer.stop();
        BeComServer.stopped();
    }

    @Test
    void aStoppedServerIsForgottenAfterItsStoppedEventNotBefore() {
        BeComServer one = BeComServer.create(null);
        BeComServer.stop();
        // Its height cache still follows the final world save.
        assertSame(one, BeComServer.get());
        BeComServer.stopped();
        assertNull(BeComServer.get());
    }

    @Test
    void theLastWorldsStoppedEventDoesntForgetTheNextWorldsServer() {
        // Single-player: the next world starts while the last one's stopped handlers still run.
        BeComServer.create(null);
        BeComServer.stop();
        BeComServer next = BeComServer.create(null);
        BeComServer.stopped();
        assertSame(next, BeComServer.get());
    }
}
