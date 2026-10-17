package com.enn3developer.becom.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * A player's video call as the server sees it: whom they see and at which level, their own
 * camera switches, at which level their rear camera goes out, and the Videophone whose screen they stand at. Sent
 * whole whenever it changes; the client draws from it and decides nothing.
 */
public final class VideoStatePacket implements IMessage {

    // A name is at most 16 characters; anything longer is cut.
    private static final int MAX_NAME_BYTES = 64;

    /** One person this player sees: who, at which level, and which of their cameras reach them. */
    public record Tile(UUID source, String name, SignalLevel level, boolean front, boolean rear) {}

    /**
     * A Videophone whose screen shows the call to its room: where it stands and which way it faces.
     */
    public record Screen(int dimension, int x, int y, int z, int facing) {}

    /**
     * @param rearSend the level this player's rear camera goes out at, {@link SignalLevel#NONE} when it goes nowhere
     * @param screen   the Videophone this player is in the room of, or null
     */
    public record State(boolean front, boolean rear, SignalLevel rearSend, List<Tile> tiles, Screen screen) {

        public static final State NONE = new State(true, false, SignalLevel.NONE, Collections.emptyList(), null);

        /** Whether this player sees anyone at all. */
        public boolean active() {
            return !tiles.isEmpty();
        }
    }

    private State state = State.NONE;

    public VideoStatePacket() {}

    public VideoStatePacket(State state) {
        this.state = state;
    }

    public State state() {
        return state;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(state.front());
        buf.writeBoolean(state.rear());
        buf.writeByte(
            state.rearSend()
                .ordinal());
        List<Tile> tiles = state.tiles();
        int count = Math.min(tiles.size(), Tunables.MAX_GROUP_CALL);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            Tile tile = tiles.get(i);
            Wire.writeUuid(buf, tile.source());
            Wire.writeString(buf, tile.name(), MAX_NAME_BYTES);
            buf.writeByte(
                tile.level()
                    .ordinal());
            buf.writeBoolean(tile.front());
            buf.writeBoolean(tile.rear());
        }
        Screen screen = state.screen();
        buf.writeBoolean(screen != null);
        if (screen != null) {
            buf.writeInt(screen.dimension());
            buf.writeInt(screen.x());
            buf.writeInt(screen.y());
            buf.writeInt(screen.z());
            buf.writeByte(screen.facing());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        boolean front = buf.readBoolean();
        boolean rear = buf.readBoolean();
        SignalLevel rearSend = SignalLevel.byOrdinal(buf.readByte());
        int count = Math.min(buf.readUnsignedByte(), Tunables.MAX_GROUP_CALL);
        List<Tile> tiles = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tiles.add(
                new Tile(
                    Wire.readUuid(buf),
                    Wire.readString(buf, MAX_NAME_BYTES),
                    SignalLevel.byOrdinal(buf.readByte()),
                    buf.readBoolean(),
                    buf.readBoolean()));
        }
        Screen screen = buf.readBoolean()
            ? new Screen(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readUnsignedByte())
            : null;
        state = new State(front, rear, rearSend, tiles, screen);
    }

    public static final class Handler implements IMessageHandler<VideoStatePacket, IMessage> {

        @Override
        public IMessage onMessage(VideoStatePacket message, MessageContext ctx) {
            BeCom.proxy.onVideoState(message);
            return null;
        }
    }
}
