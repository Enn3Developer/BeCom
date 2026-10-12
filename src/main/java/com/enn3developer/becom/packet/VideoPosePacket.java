package com.enn3developer.becom.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.Tunables;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Front-camera poses (DESIGN.md 10.1): how each person this player sees stands right now, about ten times a second.
 * The server already knows all of it, so the person's own client sends nothing. About 22 bytes a person.
 */
public final class VideoPosePacket implements IMessage {

    /**
     * One person's pose.
     *
     * @param seq     counts up with every pose the server takes of them, so a gap shows frames were lost
     * @param headYaw where they look, in degrees from where their body faces
     * @param pitch   how far they look up or down, in degrees
     * @param swing   how far through an arm swing they are, 0 to 1
     * @param speed   how fast they walk, in blocks per tick
     */
    public record Pose(UUID source, int seq, float headYaw, float pitch, float swing, boolean sneaking, float speed) {}

    private static final float DEGREES_PER_STEP = 360f / 256f;
    private static final float SPEED_STEPS = 64f;

    private List<Pose> poses = new ArrayList<>();

    public VideoPosePacket() {}

    public VideoPosePacket(List<Pose> poses) {
        this.poses = poses;
    }

    public List<Pose> poses() {
        return poses;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int count = Math.min(poses.size(), Tunables.MAX_GROUP_CALL);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            Pose pose = poses.get(i);
            Wire.writeUuid(buf, pose.source());
            buf.writeByte(pose.seq());
            buf.writeByte(Math.round(pose.headYaw() / DEGREES_PER_STEP));
            buf.writeByte(Math.round(pose.pitch() / DEGREES_PER_STEP));
            buf.writeByte(Math.round(Math.max(0, Math.min(1, pose.swing())) * 255));
            buf.writeBoolean(pose.sneaking());
            buf.writeByte(Math.round(Math.max(0, Math.min(255, pose.speed() * SPEED_STEPS))));
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = Math.min(buf.readUnsignedByte(), Tunables.MAX_GROUP_CALL);
        poses = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            poses.add(
                new Pose(
                    Wire.readUuid(buf),
                    buf.readUnsignedByte(),
                    buf.readByte() * DEGREES_PER_STEP,
                    buf.readByte() * DEGREES_PER_STEP,
                    buf.readUnsignedByte() / 255f,
                    buf.readBoolean(),
                    buf.readUnsignedByte() / SPEED_STEPS));
        }
    }

    public static final class Handler implements IMessageHandler<VideoPosePacket, IMessage> {

        @Override
        public IMessage onMessage(VideoPosePacket message, MessageContext ctx) {
            BeCom.proxy.onVideoPoses(message);
            return null;
        }
    }
}
