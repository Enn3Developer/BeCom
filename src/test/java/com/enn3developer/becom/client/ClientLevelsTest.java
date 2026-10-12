package com.enn3developer.becom.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.SignalLevel;

/** DESIGN.md 13.5: the level a radio frame is filtered at, before and after the server tells it. */
class ClientLevelsTest {

    @AfterEach
    void clear() {
        ClientLevels.clear();
    }

    @Test
    void aRadioFrameWhoseLevelHasntArrivedPlaysAtMediumNotClean() {
        UUID speaker = UUID.randomUUID();
        assertEquals(SignalLevel.MEDIUM, ClientLevels.radio(speaker, "becom:radio.3"));
        ClientLevels.set(speaker, "becom:radio.3", SignalLevel.LOW);
        assertEquals(SignalLevel.LOW, ClientLevels.radio(speaker, "becom:radio.3"));
        ClientLevels.set(speaker, "becom:radio.3", SignalLevel.NONE);
        assertEquals(SignalLevel.MEDIUM, ClientLevels.radio(speaker, "becom:radio.3"));
    }

    @Test
    void aCallFrameWhoseLevelHasntArrivedPlaysAtLowNotClean() {
        UUID speaker = UUID.randomUUID();
        assertEquals(SignalLevel.LOW, ClientLevels.call(speaker, "becom:call"));
        ClientLevels.set(speaker, "becom:call", SignalLevel.MAX);
        assertEquals(SignalLevel.MAX, ClientLevels.call(speaker, "becom:call"));
    }
}
