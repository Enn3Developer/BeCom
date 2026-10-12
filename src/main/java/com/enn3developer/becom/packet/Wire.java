package com.enn3developer.becom.packet;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import io.netty.buffer.ByteBuf;

/**
 * Small pieces BeCom's packets share. Everything read is bounded: a client or a server that sends more than a field
 * can hold gets it cut, never an allocation of its choosing.
 */
final class Wire {

    private Wire() {}

    static void writeUuid(ByteBuf buf, UUID id) {
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
    }

    static UUID readUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    /** A string of at most {@code maxBytes} bytes of UTF-8; a longer one is cut. */
    static void writeString(ByteBuf buf, String s, int maxBytes) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, maxBytes);
        buf.writeShort(length);
        buf.writeBytes(bytes, 0, length);
    }

    static String readString(ByteBuf buf, int maxBytes) {
        int length = buf.readUnsignedShort();
        byte[] bytes = new byte[Math.min(length, maxBytes)];
        buf.readBytes(bytes);
        buf.skipBytes(length - bytes.length);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static void writeBytes(ByteBuf buf, byte[] bytes) {
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    /** At most {@code maxBytes}; longer reads as empty, so a cut picture is never shown. */
    static byte[] readBytes(ByteBuf buf, int maxBytes) {
        int length = buf.readInt();
        if (length < 0 || length > maxBytes || length > buf.readableBytes()) {
            buf.skipBytes(buf.readableBytes());
            return new byte[0];
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return bytes;
    }
}
