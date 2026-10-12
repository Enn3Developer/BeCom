package com.enn3developer.becom.packet;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.SignalLevel;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * The service of the phone a player carries, for the signal bars by the hotbar while they hold it (DESIGN.md 11.2).
 * Sent when it changes.
 */
public final class PhoneStatusPacket implements IMessage {

    private SignalLevel service = SignalLevel.NONE;

    public PhoneStatusPacket() {}

    public PhoneStatusPacket(SignalLevel service) {
        this.service = service;
    }

    public SignalLevel service() {
        return service;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        service = SignalLevel.byOrdinal(buf.readByte());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(service.ordinal());
    }

    public static final class Handler implements IMessageHandler<PhoneStatusPacket, IMessage> {

        @Override
        public IMessage onMessage(PhoneStatusPacket message, MessageContext ctx) {
            BeCom.proxy.onPhoneStatus(message);
            return null;
        }
    }
}
