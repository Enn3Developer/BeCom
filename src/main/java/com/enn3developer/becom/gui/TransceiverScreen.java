package com.enn3developer.becom.gui;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.gui.paint.CanvasWidget;
import com.enn3developer.becom.gui.radio.RadioUi;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.machine.MTETransceiver;
import com.enn3developer.becom.server.BeComServer;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** The Transceiver's screen (the Radios board): its channel, TX and RX, its loudspeaker, and its ranges. */
public final class TransceiverScreen {

    private static final String CHANNEL = "channel";
    private static final String LOUDSPEAKER = "loudspeaker";
    // Further than this from the block, a screen left open stops changing it.
    private static final int REACH = 8;

    /** What the screen shows; the ranges are in blocks. */
    public record View(int channel, boolean loudspeaker, boolean online, boolean tx, boolean rx, int reach, int talk,
        int speaker) {

        static final View EMPTY = new View(1, true, false, false, false, 0, 0, 0);

        void write(PacketBuffer buf) {
            buf.writeByte(channel);
            buf.writeBoolean(loudspeaker);
            buf.writeBoolean(online);
            buf.writeBoolean(tx);
            buf.writeBoolean(rx);
            buf.writeShort(reach);
            buf.writeShort(talk);
            buf.writeShort(speaker);
        }

        static View read(PacketBuffer buf) {
            return new View(
                buf.readByte(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readShort(),
                buf.readShort(),
                buf.readShort());
        }
    }

    private TransceiverScreen() {}

    public static ModularPanel build(MTETransceiver transceiver, PosGuiData data, PanelSyncManager sync) {
        EntityPlayer player = data.getPlayer();
        ViewSync<View> viewSync = new ViewSync<>(() -> view(transceiver), View::write, View::read, View.EMPTY);
        sync.syncValue("view", viewSync);
        sync.registerSyncedAction(CHANNEL, false, true, buf -> {
            int delta = buf.readByte();
            if (Math.abs(delta) == 1 && near(transceiver, player)) {
                transceiver.setChannel(transceiver.channel() + delta);
            }
        });
        sync.registerSyncedAction(LOUDSPEAKER, false, true, buf -> {
            boolean on = buf.readBoolean();
            if (near(transceiver, player)) transceiver.setLoudspeaker(on);
        });
        RadioUi ui = new RadioUi(false, () -> {
            View v = viewSync.view();
            return new RadioUi.State(
                v.channel(),
                v.online(),
                v.loudspeaker(),
                v.tx(),
                v.rx(),
                0,
                List.of("~" + v.reach(), String.valueOf(v.talk()), String.valueOf(v.speaker())));
        },
            delta -> sync.callSyncedAction(CHANNEL, buf -> buf.writeByte(delta)),
            on -> sync.callSyncedAction(LOUDSPEAKER, buf -> buf.writeBoolean(on)));
        CanvasWidget screen = new CanvasWidget(ui, RadioUi.scale(BeCom.proxy.guiHeight()));
        return new ModularPanel("becom_transceiver").size(screen.width(), screen.height())
            .background(IDrawable.EMPTY)
            .child(screen);
    }

    private static View view(MTETransceiver transceiver) {
        Tunables t = BeComConfig.get();
        BeComServer becom = BeComServer.get();
        boolean tx = becom != null && becom.radio.transceiverTransmits(transceiver.ref());
        boolean rx = becom != null && becom.radio.transceiverReceives(transceiver.ref());
        return new View(
            transceiver.channel(),
            transceiver.loudspeaker(),
            transceiver.isWorking(),
            tx,
            rx,
            (int) Math.round(t.rangeToLow(t.transceiverBudget())),
            (int) t.talkRange(),
            (int) t.loudspeakerRange());
    }

    private static boolean near(MTETransceiver transceiver, EntityPlayer player) {
        IGregTechTileEntity tile = transceiver.getBaseMetaTileEntity();
        return tile != null && !tile.isDead()
            && player.getDistanceSq(tile.getXCoord() + 0.5, tile.getYCoord() + 0.5, tile.getZCoord() + 0.5)
                <= REACH * REACH;
    }
}
