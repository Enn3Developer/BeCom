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
        values.put("pylonMaxHeight", 20);
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
        // A modified server sending an endless pylon would make the client's hologram loop.
        Map<String, Object> values = Tunables.defaults()
            .toMap();
        values.put("pylonMinHeight", 0);
        ByteBuf buf = Unpooled.buffer();
        new ConfigSyncPacket(Tunables.fromMap(values)).toBytes(buf);
        ConfigSyncPacket read = new ConfigSyncPacket();
        read.fromBytes(buf);
        assertNull(read.tunables());
    }

    @Test
    void refusesAnEndlessPylon() {
        Map<String, Object> values = Tunables.defaults()
            .toMap();
        values.put("pylonMinHeight", Integer.MAX_VALUE);
        values.put("pylonMaxHeight", Integer.MAX_VALUE);
        ByteBuf buf = Unpooled.buffer();
        new ConfigSyncPacket(Tunables.fromMap(values)).toBytes(buf);
        ConfigSyncPacket read = new ConfigSyncPacket();
        read.fromBytes(buf);
        assertNull(read.tunables());
    }
}
