package com.enn3developer.becom.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.Tunables;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** A client gets the server's values whole. */
class ConfigSyncPacketTest {

    @Test
    void carriesEveryValue() {
        Map<String, Object> values = Tunables.defaults()
            .toMap();
        values.put("antennaMastMax", 20);
        values.put("weatherRainLoss", 2.5);
        values.put("questlineEnabled", false);
        Tunables server = Tunables.fromMap(values);
        ByteBuf buf = Unpooled.buffer();
        new ConfigSyncPacket(server).toBytes(buf);
        ConfigSyncPacket read = new ConfigSyncPacket();
        read.fromBytes(buf);
        assertEquals(server, read.tunables());
    }

    @Test
    void refusesValuesNoConfigFileCouldHold() {
        // A modified server sending a mast shorter than its shape has room on would draw a broken hologram.
        Map<String, Object> values = Tunables.defaults()
            .toMap();
        values.put("antennaMastMinT1", 0);
        ByteBuf buf = Unpooled.buffer();
        new ConfigSyncPacket(Tunables.fromMap(values)).toBytes(buf);
        ConfigSyncPacket read = new ConfigSyncPacket();
        read.fromBytes(buf);
        assertNull(read.tunables());
    }

    @Test
    void refusesAnEndlessMast() {
        // A modified server sending an endless mast would make the client's hologram loop.
        Map<String, Object> values = Tunables.defaults()
            .toMap();
        values.put("antennaMastMinT1", Integer.MAX_VALUE);
        values.put("antennaMastMax", Integer.MAX_VALUE);
        ByteBuf buf = Unpooled.buffer();
        new ConfigSyncPacket(Tunables.fromMap(values)).toBytes(buf);
        ConfigSyncPacket read = new ConfigSyncPacket();
        read.fromBytes(buf);
        assertNull(read.tunables());
    }
}
