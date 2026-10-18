package com.enn3developer.becom.gui;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.gui.paint.CanvasWidget;
import com.enn3developer.becom.gui.radio.RadioUi;
import com.enn3developer.becom.item.ItemWalkieTalkie;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.server.BeComServer;

/**
 * The walkie-talkie's screen (the Radios board): its channel, 1 to 16, TX and RX, its power and its battery. The server
 * changes the stack the
 * screen was opened from, and only while it's still a walkie.
 */
public final class WalkieScreen {

    private static final String CHANNEL = "channel";
    private static final String POWER = "power";

    /** What the screen shows: its reach in blocks, and whether it transmits and receives now. */
    public record View(int channel, boolean on, long charge, long max, int reach, boolean tx, boolean rx) {

        static final View EMPTY = new View(1, false, 0, 0, 0, false, false);

        void write(PacketBuffer buf) {
            buf.writeByte(channel);
            buf.writeBoolean(on);
            buf.writeLong(charge);
            buf.writeLong(max);
            buf.writeShort(reach);
            buf.writeBoolean(tx);
            buf.writeBoolean(rx);
        }

        static View read(PacketBuffer buf) {
            return new View(
                buf.readByte(),
                buf.readBoolean(),
                buf.readLong(),
                buf.readLong(),
                buf.readShort(),
                buf.readBoolean(),
                buf.readBoolean());
        }

        int percent() {
            return max <= 0 ? 0 : (int) Math.min(100, charge * 100 / max);
        }
    }

    private WalkieScreen() {}

    public static ModularPanel build(PlayerInventoryGuiData data, PanelSyncManager sync) {
        ViewSync<View> viewSync = new ViewSync<>(() -> view(data), View::write, View::read, View.EMPTY);
        sync.syncValue("view", viewSync);
        sync.registerSyncedAction(CHANNEL, false, true, buf -> step(data, buf.readByte()));
        sync.registerSyncedAction(POWER, false, true, buf -> setOn(data, buf.readBoolean()));
        RadioUi ui = new RadioUi(true, () -> {
            View v = viewSync.view();
            return new RadioUi.State(
                v.channel(),
                v.on(),
                v.on(),
                v.tx(),
                v.rx(),
                v.percent(),
                List.of(),
                StatCollector.translateToLocalFormatted("becom.ui.radio.walkie_note", v.reach()));
        },
            delta -> sync.callSyncedAction(CHANNEL, buf -> buf.writeByte(delta)),
            on -> sync.callSyncedAction(POWER, buf -> buf.writeBoolean(on)));
        CanvasWidget screen = new CanvasWidget(ui, RadioUi.scale(BeCom.proxy.guiHeight()));
        return new ModularPanel("becom_walkie").size(screen.width(), screen.height())
            .background(IDrawable.EMPTY)
            .child(screen);
    }

    private static View view(PlayerInventoryGuiData data) {
        ItemStack stack = data.getUsedItemStack();
        if (!ItemWalkieTalkie.is(stack)) return View.EMPTY;
        ItemWalkieTalkie item = (ItemWalkieTalkie) stack.getItem();
        Tunables t = BeComConfig.get();
        BeComServer becom = BeComServer.get();
        java.util.UUID id = data.getPlayer()
            .getUniqueID();
        return new View(
            ItemWalkieTalkie.channel(stack),
            ItemWalkieTalkie.on(stack),
            (long) ItemWalkieTalkie.charge(stack),
            (long) item.getMaxCharge(stack),
            (int) Math.round(t.rangeToLow(t.walkieBudget())),
            becom != null && becom.radio.walkieTransmits(id),
            becom != null && becom.radio.walkieReceives(id));
    }

    /** Server side: one channel up or down, wrapping round. */
    static void step(PlayerInventoryGuiData data, int delta) {
        ItemStack stack = data.getUsedItemStack();
        if (!ItemWalkieTalkie.is(stack) || Math.abs(delta) != 1) return;
        ItemWalkieTalkie.setChannel(stack, ItemWalkieTalkie.channel(stack) + delta);
        markChanged(data.getPlayer());
    }

    /** Server side: switches it on or off. A flat walkie doesn't switch on. */
    static void setOn(PlayerInventoryGuiData data, boolean on) {
        ItemStack stack = data.getUsedItemStack();
        if (!ItemWalkieTalkie.is(stack)) return;
        if (on && ItemWalkieTalkie.charge(stack) <= 0) return;
        ItemWalkieTalkie.setOn(stack, on);
        markChanged(data.getPlayer());
    }

    private static void markChanged(EntityPlayer player) {
        player.inventory.markDirty();
    }
}
