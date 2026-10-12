package com.enn3developer.becom.packet;

import java.util.UUID;

import com.enn3developer.becom.BeCom;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * A person's profile, sent once to each player who sees them in a video call (DESIGN.md 10.1), so that player's client
 * can load their skin: their name, and the textures property Mojang signed, empty on a server that has none.
 */
public final class VideoProfilePacket implements IMessage {

    private static final int MAX_NAME_BYTES = 64;
    // Mojang's textures property and its signature are a few hundred bytes each in base64.
    private static final int MAX_PROPERTY_BYTES = 8192;

    private UUID source;
    private String name = "";
    private String textures = "";
    private String signature = "";

    public VideoProfilePacket() {}

    public VideoProfilePacket(UUID source, String name, String textures, String signature) {
        this.source = source;
        this.name = name;
        this.textures = textures == null ? "" : textures;
        this.signature = signature == null ? "" : signature;
    }

    public UUID source() {
        return source;
    }

    public String name() {
        return name;
    }

    public String textures() {
        return textures;
    }

    public String signature() {
        return signature;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        Wire.writeUuid(buf, source);
        Wire.writeString(buf, name, MAX_NAME_BYTES);
        Wire.writeString(buf, textures, MAX_PROPERTY_BYTES);
        Wire.writeString(buf, signature, MAX_PROPERTY_BYTES);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        source = Wire.readUuid(buf);
        name = Wire.readString(buf, MAX_NAME_BYTES);
        textures = Wire.readString(buf, MAX_PROPERTY_BYTES);
        signature = Wire.readString(buf, MAX_PROPERTY_BYTES);
    }

    public static final class Handler implements IMessageHandler<VideoProfilePacket, IMessage> {

        @Override
        public IMessage onMessage(VideoProfilePacket message, MessageContext ctx) {
            BeCom.proxy.onVideoProfile(message);
            return null;
        }
    }
}
