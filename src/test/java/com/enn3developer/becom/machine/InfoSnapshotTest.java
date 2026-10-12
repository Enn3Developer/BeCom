package com.enn3developer.becom.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
}
