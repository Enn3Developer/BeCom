package com.enn3developer.becom.packet;

import java.util.LinkedHashMap;
import java.util.Map;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.Tunables;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * The server's tunables, sent to each player as they log in: clients show the server's rules in
 * tooltips and holograms, never their own config file's.
 */
public final class ConfigSyncPacket implements IMessage {

    private static final byte DOUBLE = 0;
    private static final byte INT = 1;
    private static final byte BOOLEAN = 2;
    private static final int MAX_VALUES = 256;

    private Tunables tunables;

    public ConfigSyncPacket() {}

    public ConfigSyncPacket(Tunables tunables) {
        this.tunables = tunables;
    }

    /** The server's values, or null when they failed the config file's checks. */
    public Tunables tunables() {
        return tunables;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        Map<String, Object> values = tunables.toMap();
        buf.writeShort(values.size());
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            ByteBufUtils.writeUTF8String(buf, entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Double d) {
                buf.writeByte(DOUBLE);
                buf.writeDouble(d);
            } else if (value instanceof Integer i) {
                buf.writeByte(INT);
                buf.writeInt(i);
            } else {
                buf.writeByte(BOOLEAN);
                buf.writeBoolean((Boolean) value);
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = Math.min(buf.readUnsignedShort(), MAX_VALUES);
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String key = ByteBufUtils.readUTF8String(buf);
            byte type = buf.readByte();
            values.put(key, switch (type) {
                case DOUBLE -> buf.readDouble();
                case INT -> buf.readInt();
                default -> buf.readBoolean();
            });
        }
        Tunables sent = Tunables.fromMap(values);
        try {
            // Checked like a config file's: a client acts on these too, in tooltips and holograms.
            sent.validate();
            tunables = sent;
        } catch (IllegalArgumentException e) {
            BeCom.LOG.warn("The server sent BeCom values no config could hold, so they're ignored: {}", e.getMessage());
            tunables = null;
        }
    }

    public static final class Handler implements IMessageHandler<ConfigSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(ConfigSyncPacket message, MessageContext ctx) {
            BeCom.proxy.onConfig(message);
            return null;
        }
    }
}
