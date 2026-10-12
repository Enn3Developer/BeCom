package com.enn3developer.becom.gui;

import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.enn3developer.becom.machine.MTETransceiver;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** The Transceiver's screen (DESIGN.md 11.3): its channel and its loudspeaker. */
public final class TransceiverScreen {

    private static final int WIDTH = 150;
    private static final int HEIGHT = 72;
    private static final String CHANNEL = "channel";
    private static final String LOUDSPEAKER = "loudspeaker";
    // Further than this from the block, a screen left open stops changing it.
    private static final double REACH_SQ = 64;

    /** What the screen shows. */
    public record View(int channel, boolean loudspeaker) {

        static final View EMPTY = new View(1, true);

        void write(PacketBuffer buf) {
            buf.writeByte(channel);
            buf.writeBoolean(loudspeaker);
        }

        static View read(PacketBuffer buf) {
            return new View(buf.readByte(), buf.readBoolean());
        }
    }

    private TransceiverScreen() {}

    public static ModularPanel build(MTETransceiver transceiver, PosGuiData data, PanelSyncManager sync) {
        EntityPlayer player = data.getPlayer();
        ViewSync<View> viewSync = new ViewSync<>(
            () -> new View(transceiver.channel(), transceiver.loudspeaker()),
            View::write,
            View::read,
            View.EMPTY);
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
        Supplier<View> view = viewSync::view;

        ModularPanel panel = new ModularPanel("becom_transceiver").size(WIDTH, HEIGHT);
        Flow column = Flow.column()
            .sizeRel(1f)
            .padding(6, 6, 6, 6)
            .childPadding(3);
        column.child(
            IKey.lang("gt.blockmachines.becom.transceiver.name")
                .asWidget()
                .widthRel(1f)
                .height(10));
        Flow channel = Flow.row()
            .widthRel(1f)
            .height(14)
            .childPadding(4);
        channel.child(
            new ButtonWidget<>().size(20, 14)
                .overlay(IKey.str("<"))
                .onMousePressed(button -> {
                    sync.callSyncedAction(CHANNEL, buf -> buf.writeByte(-1));
                    return true;
                }));
        channel.child(
            IKey.dynamic(
                () -> StatCollector.translateToLocalFormatted(
                    "becom.walkie.channel",
                    view.get()
                        .channel()))
                .asWidget()
                .width(WIDTH - 12 - 48)
                .height(14));
        channel.child(
            new ButtonWidget<>().size(20, 14)
                .overlay(IKey.str(">"))
                .onMousePressed(button -> {
                    sync.callSyncedAction(CHANNEL, buf -> buf.writeByte(1));
                    return true;
                }));
        column.child(channel);
        column.child(
            new ToggleButton().size(WIDTH - 12, 14)
                .overlay(IKey.lang("becom.transceiver.loudspeaker"))
                .value(
                    new BoolValue.Dynamic(
                        () -> view.get()
                            .loudspeaker(),
                        on -> sync.callSyncedAction(LOUDSPEAKER, buf -> buf.writeBoolean(on)))));
        panel.child(column);
        return panel;
    }

    private static boolean near(MTETransceiver transceiver, EntityPlayer player) {
        IGregTechTileEntity tile = transceiver.getBaseMetaTileEntity();
        return tile != null && !tile.isDead()
            && player.getDistanceSq(tile.getXCoord() + 0.5, tile.getYCoord() + 0.5, tile.getZCoord() + 0.5) <= REACH_SQ;
    }
}
