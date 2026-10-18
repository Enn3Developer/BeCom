package com.enn3developer.becom.gui;

import java.nio.charset.StandardCharsets;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.enn3developer.becom.gui.phone.PhoneActions;
import com.enn3developer.becom.server.PhoneHandle;
import com.enn3developer.becom.server.PhoneScreenActions;

/**
 * The server's side of a carried phone's app pages (PhoneAppPages paints them): opening and closing an app, its
 * events, its settings, the App Store and an install or publish a computer offers.
 */
final class AppScreens {

    // The longest text an app's event or a store search carries.
    private static final int MAX_ARG = 256;

    private AppScreens() {}

    static String read(PacketBuffer buf) {
        int length = Math.min(buf.readVarIntFromBuffer(), MAX_ARG * 4);
        byte[] bytes = new byte[Math.max(0, length)];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static void register(PanelSyncManager sync, EntityPlayer player, PhoneHandle tile) {
        sync.registerSyncedAction(
            PhoneActions.APP_OPEN,
            false,
            true,
            buf -> PhoneScreenActions.appOpen(player, tile, read(buf)));
        sync.registerSyncedAction(
            PhoneActions.APP_CLOSE,
            false,
            true,
            buf -> PhoneScreenActions.appClose(player, tile));
        sync.registerSyncedAction(
            PhoneActions.APP_EVENT,
            false,
            true,
            buf -> PhoneScreenActions.appEvent(player, tile, read(buf), read(buf), read(buf)));
        sync.registerSyncedAction(
            PhoneActions.APP_BACKGROUND,
            false,
            true,
            buf -> PhoneScreenActions.appBackground(player, tile, read(buf), buf.readBoolean()));
        sync.registerSyncedAction(
            PhoneActions.APP_ALLOWED,
            false,
            true,
            buf -> PhoneScreenActions.appAllowed(player, tile, read(buf), buf.readByte(), buf.readBoolean()));
        sync.registerSyncedAction(
            PhoneActions.APP_UNINSTALL,
            false,
            true,
            buf -> PhoneScreenActions.appUninstall(player, tile, read(buf)));
        sync.registerSyncedAction(
            PhoneActions.STORE_QUERY,
            false,
            true,
            buf -> PhoneScreenActions.storeQuery(player, tile, read(buf), buf.readUnsignedByte(), buf.readShort()));
        sync.registerSyncedAction(
            PhoneActions.STORE_REPORT,
            false,
            true,
            buf -> PhoneScreenActions.storeReport(player, tile, read(buf)));
        sync.registerSyncedAction(
            PhoneActions.STORE_INSTALL,
            false,
            true,
            buf -> PhoneScreenActions.storeInstall(player, tile, read(buf)));
        sync.registerSyncedAction(
            PhoneActions.OFFER_ANSWER,
            false,
            true,
            buf -> PhoneScreenActions.answerOffer(player, tile, buf.readLong(), buf.readBoolean(), buf.readBoolean()));
    }
}
