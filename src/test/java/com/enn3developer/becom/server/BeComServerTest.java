package com.enn3developer.becom.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** BeCom's services between a server's stopping and stopped events. */
class BeComServerTest {

    private final AtomicBoolean forgotten = new AtomicBoolean();

    @AfterEach
    void forget() {
        BeComServer.stop();
        BeComServer.stopped(() -> {});
    }

    @Test
    void aStoppedServerIsForgottenAfterItsStoppedEventNotBefore() {
        BeComServer one = BeComServer.create(null);
        BeComServer.stop();
        // Its height cache still follows the final world save.
        assertSame(one, BeComServer.get());
        BeComServer.stopped(() -> forgotten.set(true));
        assertNull(BeComServer.get());
        assertTrue(forgotten.get());
    }

    @Test
    void theLastWorldsStoppedEventDoesntForgetTheNextWorldsServer() {
        // Single-player: the next world starts while the last one's stopped handlers still run.
        BeComServer.create(null);
        BeComServer.stop();
        BeComServer next = BeComServer.create(null);
        BeComServer.stopped(() -> forgotten.set(true));
        assertSame(next, BeComServer.get());
        // The caller's shared state, the quest backend's, is left alone too.
        assertFalse(forgotten.get());
    }

    @Test
    void aRefusedWorldsStoppedEventDoesntForgetTheNextWorldsServer() throws InterruptedException {
        // A world refused at start gets a stopped event but no stopping one, on its own server thread, and the next
        // world's server thread can create its services first.
        CountDownLatch created = new CountDownLatch(1);
        CountDownLatch nextCreated = new CountDownLatch(1);
        Thread refused = new Thread(() -> {
            BeComServer.create(null);
            created.countDown();
            try {
                nextCreated.await();
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
            }
            // A late stopping event for it does nothing either.
            BeComServer.stop();
            BeComServer.stopped(() -> forgotten.set(true));
        });
        refused.start();
        created.await();
        BeComServer next = BeComServer.create(null);
        nextCreated.countDown();
        refused.join();
        assertSame(next, BeComServer.get());
        assertFalse(forgotten.get());
    }
}
