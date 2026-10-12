package com.enn3developer.becom.packet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.gui.AntennaView;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.radio.Coverage;
import com.enn3developer.becom.logic.radio.RadioModel;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Each of 0.3's packets reads back what was written, and a reader takes no more than its caps allow. */
class PacketRoundTripTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private static <T extends IMessage> T roundTrip(IMessage written, Supplier<T> fresh) {
        ByteBuf buf = Unpooled.buffer();
        written.toBytes(buf);
        T read = fresh.get();
        read.fromBytes(buf);
        assertEquals(0, buf.readableBytes(), "everything written is read");
        return read;
    }

    @Test
    void videoState() {
        VideoStatePacket.State state = new VideoStatePacket.State(
            false,
            true,
            SignalLevel.HIGH,
            List.of(
                new VideoStatePacket.Tile(ALICE, "Alice", SignalLevel.MEDIUM, true, false),
                new VideoStatePacket.Tile(BOB, "Bob", SignalLevel.HIGH, false, true)),
            new VideoStatePacket.Screen(-1, 100, 64, -200, 3));
        assertEquals(state, roundTrip(new VideoStatePacket(state), VideoStatePacket::new).state());
        assertEquals(
            VideoStatePacket.State.NONE,
            roundTrip(new VideoStatePacket(VideoStatePacket.State.NONE), VideoStatePacket::new).state());
    }

    @Test
    void videoPoses() {
        List<VideoPosePacket.Pose> poses = List.of(
            new VideoPosePacket.Pose(ALICE, 255, 40.5f, -12.25f, 0.5f, true, 0.2f),
            new VideoPosePacket.Pose(BOB, 0, -75f, 90f, 0f, false, 0f));
        List<VideoPosePacket.Pose> read = roundTrip(new VideoPosePacket(poses), VideoPosePacket::new).poses();
        assertEquals(poses.size(), read.size());
        for (int i = 0; i < poses.size(); i++) {
            VideoPosePacket.Pose a = poses.get(i), b = read.get(i);
            assertEquals(a.source(), b.source());
            assertEquals(a.seq(), b.seq());
            assertEquals(a.sneaking(), b.sneaking());
            assertEquals(a.headYaw(), b.headYaw(), 1, "angles may be packed");
            assertEquals(a.pitch(), b.pitch(), 1);
            assertEquals(a.swing(), b.swing(), 0.01);
            assertEquals(a.speed(), b.speed(), 0.01);
        }
    }

    @Test
    void videoEquipmentWithEmptySlots() {
        VideoEquipmentPacket read = roundTrip(
            new VideoEquipmentPacket(ALICE, new ItemStack[VideoEquipmentPacket.SLOTS]),
            VideoEquipmentPacket::new);
        assertEquals(ALICE, read.source());
        assertEquals(VideoEquipmentPacket.SLOTS, read.stacks().length);
        for (ItemStack stack : read.stacks()) assertNull(stack);
    }

    @Test
    void videoProfile() {
        VideoProfilePacket read = roundTrip(
            new VideoProfilePacket(ALICE, "Alice", "dGV4dHVyZXM=", "c2lnbmF0dXJl"),
            VideoProfilePacket::new);
        assertEquals(ALICE, read.source());
        assertEquals("Alice", read.name());
        assertEquals("dGV4dHVyZXM=", read.textures());
        assertEquals("c2lnbmF0dXJl", read.signature());
    }

    @Test
    void ringingAndItsStop() {
        RingPacket ringing = roundTrip(RingPacket.ringing("Alice", 123_456), RingPacket::new);
        assertTrue(ringing.isRinging());
        assertEquals("Alice", ringing.caller());
        assertEquals(123_456, ringing.number());
        assertFalse(roundTrip(RingPacket.stop(), RingPacket::new).isRinging());
    }

    @Test
    void phoneStatusAndCallBars() {
        assertEquals(
            SignalLevel.HIGH,
            roundTrip(new PhoneStatusPacket(SignalLevel.HIGH), PhoneStatusPacket::new).service());
        CallBarsPacket bars = roundTrip(new CallBarsPacket(true, SignalLevel.LOW), CallBarsPacket::new);
        assertTrue(bars.inCall());
        assertEquals(SignalLevel.LOW, bars.level());
        assertFalse(roundTrip(new CallBarsPacket(false, SignalLevel.NONE), CallBarsPacket::new).inCall());
    }

    @Test
    void rearPicturesUpToTheCeiling() {
        byte[] jpeg = new byte[] { (byte) 0xFF, (byte) 0xD8, 1, 2, 3, (byte) 0xFF, (byte) 0xD9 };
        RearFramePacket frame = roundTrip(new RearFramePacket(BOB, jpeg), RearFramePacket::new);
        assertEquals(BOB, frame.source());
        assertArrayEquals(jpeg, frame.jpeg());
        assertArrayEquals(jpeg, roundTrip(new RearUploadPacket(jpeg), RearUploadPacket::new).jpeg());
        byte[] huge = new byte[Tunables.MAX_REAR_BYTES + 1];
        assertEquals(0, roundTrip(new RearUploadPacket(huge), RearUploadPacket::new).jpeg().length);
    }

    @Test
    void analyzerReadingsOnBothBands() {
        AnalyzerPacket.Reading radio = AnalyzerPacket.Reading
            .of(10, 64, -20, 2, new RadioModel.Losses(48, 30, 2, 1, 1, 0, 0), SignalLevel.MEDIUM);
        AnalyzerPacket read = roundTrip(new AnalyzerPacket(radio, AnalyzerPacket.Reading.NONE), AnalyzerPacket::new);
        assertEquals(radio, read.radio());
        assertEquals(AnalyzerPacket.Reading.NONE, read.phone());
        // DESIGN.md 9.1: the satellites serving the phone band, 37 of them.
        AnalyzerPacket.Reading sky = AnalyzerPacket.Reading
            .sky(37, new RadioModel.Losses(18, 0, 0, 5, 0, 3, 0), SignalLevel.MEDIUM);
        AnalyzerPacket both = roundTrip(new AnalyzerPacket(radio, sky), AnalyzerPacket::new);
        assertEquals(sky, both.phone());
        assertEquals(
            37,
            both.phone()
                .satellites());
    }

    @Test
    void antennaViewWithItsMaps() {
        int cells = Coverage.CELLS * Coverage.CELLS;
        byte[] vhf = new byte[cells];
        byte[] uhf = new byte[cells];
        for (int i = 0; i < cells; i++) {
            vhf[i] = (byte) (i % 5);
            uhf[i] = (byte) (4 - i % 5);
        }
        AntennaView view = new AntennaView(
            2,
            true,
            true,
            12,
            128,
            true,
            false,
            true,
            "Alice",
            List.of(new AntennaView.Link(1, 2, 3, 100, 12.5, Double.NEGATIVE_INFINITY)),
            List.of(new AntennaView.Bridge(4, 5, 6, 10)),
            256,
            vhf,
            uhf);
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        view.write(buf);
        AntennaView read = AntennaView.read(buf);
        assertEquals(view.links(), read.links());
        assertEquals(view.bridges(), read.bridges());
        assertEquals(view.mapRange(), read.mapRange());
        assertArrayEquals(vhf, read.vhfMap());
        assertArrayEquals(uhf, read.uhfMap());
        assertEquals("Alice", read.owner());
    }
}
