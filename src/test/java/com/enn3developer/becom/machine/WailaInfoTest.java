package com.enn3developer.becom.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

/** WAILA asks on the server thread, so a machine's lines are worked out as it asks, for a player close by. */
class WailaInfoTest {

    private static List<String> asked(WailaInfo info) {
        NBTTagCompound tag = new NBTTagCompound();
        info.write(tag);
        List<String> tip = new ArrayList<>();
        InfoLines.read(tag, tip);
        return tip;
    }

    @Test
    void theLinesAreWorkedOutEachTimeWailaAsks() {
        AtomicInteger made = new AtomicInteger();
        WailaInfo info = new WailaInfo(() -> new InfoLines().add("becom.test.n" + made.incrementAndGet()));
        assertEquals(List.of("becom.test.n1"), asked(info), "the first look gets them");
        assertEquals(List.of("becom.test.n2"), asked(info), "and the next, as the machine stands then");
    }

    @Test
    void aMachineThatThrowsGivesNoLinesAndTheOthersGoOn() {
        WailaInfo failing = new WailaInfo(() -> { throw new IllegalStateException("broken since"); });
        WailaInfo fine = new WailaInfo(() -> new InfoLines().add("becom.test.fine"));
        assertEquals(List.of(), asked(failing));
        assertEquals(List.of("becom.test.fine"), asked(fine));
    }

    @Test
    void onlyAPlayerCloseByInTheSameDimensionIsLooking() {
        assertTrue(WailaInfo.near(5, 0, 12.5, 64, 10.5, 0, 10, 64, 10), "two blocks off");
        assertTrue(WailaInfo.near(5, 0, 10.5 + 7.9, 64, 10.5, 0, 10, 65, 10), "just within reach");
        assertFalse(WailaInfo.near(5, 0, 10.5 + 8.1, 64, 10.5, 0, 10, 65, 10), "just out of reach");
        assertFalse(WailaInfo.near(5, 0, 10.5, 64 + 9, 10.5, 0, 10, 64, 10), "straight above, out of reach");
        assertFalse(WailaInfo.near(5, -1, 12.5, 64, 10.5, 0, 10, 64, 10), "the same spot in another dimension");
    }

    @Test
    void aFarReachingPlayerIsLookingFromFurther() {
        // Two of Botania's Rings of Far Reach: 12 blocks, here to the face, 12.5 to the middle.
        assertTrue(WailaInfo.near(12, 0, 23.0, 65.5 - 1.62, 10.5, 0, 10, 65, 10), "eyes 12.5 blocks from the middle");
        assertFalse(WailaInfo.near(5, 0, 23.0, 65.5 - 1.62, 10.5, 0, 10, 65, 10), "out of reach without the rings");
    }
}
