package com.enn3developer.becom.gui;

import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.gui.paint.CanvasWidget;
import com.enn3developer.becom.gui.phone.DeskPhoneUi;
import com.enn3developer.becom.gui.phone.PhoneActions;
import com.enn3developer.becom.gui.phone.PhoneUi;
import com.enn3developer.becom.packet.AnalyzerPacket;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.PhoneHandle;
import com.enn3developer.becom.server.PhoneScreenActions;

/**
 * The phone screens, painted from the design boards: a carried phone ({@link PhoneUi}), or a Telephone or Videophone
 * desk phone ({@link DeskPhoneUi}). Built on both sides; the server registers the actions and checks each one, the
 * client only draws and asks.
 */
public final class PhoneScreen {

    private PhoneScreen() {}

    /** @param handle the phone; on the client only its kind matters, the server checks every action against it */
    public static ModularPanel build(PhoneHandle tile, EntityPlayer player, PanelSyncManager sync) {
        PhoneViewSync viewSync = new PhoneViewSync(() -> PhoneScreenActions.view(player, tile));
        sync.syncValue("view", viewSync);
        sync.registerSyncedAction(
            PhoneActions.DIAL,
            false,
            true,
            buf -> PhoneScreenActions.dial(player, tile, buf.readInt()));
        sync.registerSyncedAction(
            PhoneActions.DIAL_VIDEO,
            false,
            true,
            buf -> PhoneScreenActions.dial(player, tile, buf.readInt(), true));
        sync.registerSyncedAction(PhoneActions.PICK_UP, false, true, buf -> PhoneScreenActions.pickUp(player, tile));
        sync.registerSyncedAction(PhoneActions.HANG_UP, false, true, buf -> PhoneScreenActions.hangUp(player, tile));
        sync.registerSyncedAction(
            PhoneActions.POWER,
            false,
            true,
            buf -> PhoneScreenActions.setPower(player, tile, buf.readBoolean()));
        sync.registerSyncedAction(
            PhoneActions.SILENT,
            false,
            true,
            buf -> PhoneScreenActions.setSilent(player, tile, buf.readBoolean()));
        sync.registerSyncedAction(
            PhoneActions.FRONT,
            false,
            true,
            buf -> PhoneScreenActions.setCamera(player, tile, false, buf.readBoolean()));
        sync.registerSyncedAction(
            PhoneActions.REAR,
            false,
            true,
            buf -> PhoneScreenActions.setCamera(player, tile, true, buf.readBoolean()));
        sync.registerSyncedAction(
            PhoneActions.DND,
            false,
            true,
            buf -> PhoneScreenActions.setDoNotDisturb(player, tile, buf.readBoolean()));
        sync.registerSyncedAction(
            PhoneActions.HIDE,
            false,
            true,
            buf -> PhoneScreenActions.setHidden(player, tile, buf.readBoolean()));
        sync.registerSyncedAction(
            PhoneActions.BLOCK_CONTACT,
            false,
            true,
            buf -> PhoneScreenActions.blockContact(player, tile, buf.readInt()));
        sync.registerSyncedAction(
            PhoneActions.BLOCK_RECENT,
            false,
            true,
            buf -> PhoneScreenActions.blockRecent(player, tile, buf.readLong(), buf.readInt()));
        sync.registerSyncedAction(
            PhoneActions.UNBLOCK,
            false,
            true,
            buf -> PhoneScreenActions.unblock(player, tile, new UUID(buf.readLong(), buf.readLong())));

        sync.registerSyncedAction(
            PhoneActions.SAVER,
            false,
            true,
            buf -> PhoneScreenActions.setSaver(player, tile, buf.readBoolean()));
        sync.registerSyncedAction(
            PhoneActions.WARN,
            false,
            true,
            buf -> PhoneScreenActions.setWarns(player, tile, buf.readBoolean()));

        sync.registerSyncedAction(
            PhoneActions.MISSED_SEEN,
            false,
            true,
            buf -> PhoneScreenActions.missedSeen(player, tile));
        sync.registerSyncedAction(
            PhoneActions.SPEED_DIAL,
            false,
            true,
            buf -> PhoneScreenActions.setSpeedDial(player, tile, buf.readUnsignedByte(), buf.readInt()));
        sync.registerSyncedAction(
            PhoneActions.CAMERA_KEY,
            false,
            true,
            buf -> PhoneScreenActions.setVideophoneCamera(player, tile, buf.readBoolean()));

        Supplier<PhoneView> view = viewSync::view;
        AppScreens.register(sync, player, tile);
        int guiHeight = BeCom.proxy.guiHeight();
        CanvasWidget screen;
        if (tile instanceof PhoneHandle.CarriedPhone) {
            // What a Signal Analyzer would read on the phone's band where its holder stands, for the Signal app.
            SignalSync signal = new SignalSync(() -> {
                BeComServer becom = BeComServer.get();
                return becom == null ? AnalyzerPacket.Reading.NONE
                    : becom.radio.reading(player)
                        .phone();
            });
            sync.syncValue("signal", signal);
            screen = new CanvasWidget(
                new PhoneUi(view, signal::reading, sync::callSyncedAction),
                PhoneUi.scaleFor(guiHeight));
        } else {
            boolean videophone = tile instanceof PhoneHandle.Videophone;
            screen = new CanvasWidget(
                new DeskPhoneUi(view, sync::callSyncedAction, videophone),
                DeskPhoneUi.scaleFor(videophone, guiHeight));
        }
        return new ModularPanel("becom_phone").size(screen.width(), screen.height())
            .background(IDrawable.EMPTY)
            .child(screen);
    }
}
