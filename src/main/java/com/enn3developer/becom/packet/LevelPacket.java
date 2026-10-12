package com.enn3developer.becom.packet;

import java.util.UUID;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.SignalLevel;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * The level a player hears one source at through one BeCom group changed (DESIGN.md 13.5). {@link SignalLevel#NONE}
 * means forget the source. Sent only on changes, never per frame.
 */
public final class LevelPacket implements IMessage {

    private UUID source;
    private String group;
    private SignalLevel level;

    public LevelPacket() {}

    public LevelPacket(UUID source, String group, SignalLevel level) {
        this.source = source;
        this.group = group;
        this.level = level;
    }

    public UUID source() {
        return source;
    }

    public String group() {
        return group;
    }

    public SignalLevel level() {
        return level;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        source = new UUID(buf.readLong(), buf.readLong());
        group = ByteBufUtils.readUTF8String(buf);
        level = SignalLevel.byOrdinal(buf.readByte());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(source.getMostSignificantBits());
        buf.writeLong(source.getLeastSignificantBits());
        ByteBufUtils.writeUTF8String(buf, group);
        buf.writeByte(level.ordinal());
    }

    public static final class Handler implements IMessageHandler<LevelPacket, IMessage> {

        @Override
        public IMessage onMessage(LevelPacket message, MessageContext ctx) {
            BeCom.proxy.onLevel(message);
            return null;
        }
    }
}
