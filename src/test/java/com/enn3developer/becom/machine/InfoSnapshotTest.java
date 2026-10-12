package com.enn3developer.becom.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

/** WAILA asks on the network thread; the lines are only ever worked out on the server thread's tick. */
class InfoSnapshotTest {

    private static List<String> asked(InfoSnapshot snapshot) {
        NBTTagCompound tag = new NBTTagCompound();
        snapshot.write(tag);
        List<String> tip = new ArrayList<>();
        InfoLines.read(tag, tip);
        return tip;
    }

    @Test
    void askingQueuesTheWorkForTheNextTickOnce() {
        AtomicInteger made = new AtomicInteger();
        InfoSnapshot snapshot = new InfoSnapshot(() -> {
            made.incrementAndGet();
            return new InfoLines().add("becom.test.first");
        });
        assertEquals(List.of(), asked(snapshot));
        asked(snapshot);
        assertEquals(0, made.get());
        InfoSnapshot.refreshWanted();
        assertEquals(1, made.get());
        assertEquals(List.of("becom.test.first"), asked(snapshot));
        InfoSnapshot.refreshWanted();
        assertEquals(2, made.get());
        InfoSnapshot.refreshWanted();
        assertEquals(2, made.get(), "nobody asked since the last tick");
    }

    @Test
    void aMachineThatFailsKeepsItsLastLinesAndTheOthersGoOn() {
        AtomicInteger calls = new AtomicInteger();
        InfoSnapshot failing = new InfoSnapshot(() -> {
            if (calls.incrementAndGet() > 1) throw new IllegalStateException("broken since");
            return new InfoLines().add("becom.test.before");
        });
        InfoSnapshot fine = new InfoSnapshot(() -> new InfoLines().add("becom.test.fine"));
        asked(failing);
        InfoSnapshot.refreshWanted();
        asked(failing);
        asked(fine);
        InfoSnapshot.refreshWanted();
        assertEquals(List.of("becom.test.before"), asked(failing));
        assertEquals(List.of("becom.test.fine"), asked(fine));
        InfoSnapshot.refreshWanted();
    }

    @Test
    void aRequestMadeWhileATickWorksWaitsForTheNext() {
        AtomicInteger made = new AtomicInteger();
        AtomicBoolean flooding = new AtomicBoolean(true);
        AtomicReference<InfoSnapshot> self = new AtomicReference<>();
        self.set(new InfoSnapshot(() -> {
            // WAILA asking again on the network thread meanwhile, as a flood of requests would.
            if (made.incrementAndGet() < 1000 && flooding.get()) self.get()
                .write(new NBTTagCompound());
            return new InfoLines();
        }));
        self.get()
            .write(new NBTTagCompound());
        InfoSnapshot.refreshWanted();
        assertEquals(1, made.get(), "a tick works out what was asked before it");
        InfoSnapshot.refreshWanted();
        assertEquals(2, made.get());
        flooding.set(false);
        InfoSnapshot.refreshWanted();
    }

    @Test
    void requestsLeftWhenTheServerStopsAreForgotten() {
        AtomicInteger made = new AtomicInteger();
        InfoSnapshot snapshot = new InfoSnapshot(() -> {
            made.incrementAndGet();
            return new InfoLines();
        });
        snapshot.write(new NBTTagCompound());
        InfoSnapshot.forgetWanted();
        InfoSnapshot.refreshWanted();
        assertEquals(0, made.get(), "the stopped server's request never runs");
        snapshot.write(new NBTTagCompound());
        InfoSnapshot.refreshWanted();
        assertEquals(1, made.get(), "and the machine can be asked about again");
    }

    @Test
    void onlyAPlayerCloseByInTheSameDimensionIsLooking() {
        assertTrue(InfoSnapshot.near(0, 12.5, 64, 10.5, 0, 10, 64, 10), "two blocks off");
        assertTrue(InfoSnapshot.near(0, 10.5 + 7.9, 64.5, 10.5, 0, 10, 64, 10), "just within reach");
        assertFalse(InfoSnapshot.near(0, 10.5 + 8.1, 64.5, 10.5, 0, 10, 64, 10), "just out of reach");
        assertFalse(InfoSnapshot.near(0, 10.5, 64.5 + 9, 10.5, 0, 10, 64, 10), "straight above, out of reach");
        assertFalse(InfoSnapshot.near(-1, 12.5, 64, 10.5, 0, 10, 64, 10), "the same spot in another dimension");
    }
}
