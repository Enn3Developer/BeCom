package com.enn3developer.becom.gui;

import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.enn3developer.becom.item.ItemWalkieTalkie;

/**
 * The walkie-talkie's screen: its channel, 1 to 16, and its power. The server changes the stack the
 * screen was opened from, and only while it's still a walkie.
 */
public final class WalkieScreen {

    private static final int WIDTH = 150;
    private static final int HEIGHT = 84;
    private static final String CHANNEL = "channel";
    private static final String POWER = "power";

    /** What the screen shows. */
    public record View(int channel, boolean on, long charge, long max) {

        static final View EMPTY = new View(1, false, 0, 0);

        void write(PacketBuffer buf) {
            buf.writeByte(channel);
            buf.writeBoolean(on);
            buf.writeLong(charge);
            buf.writeLong(max);
        }

        static View read(PacketBuffer buf) {
            return new View(buf.readByte(), buf.readBoolean(), buf.readLong(), buf.readLong());
        }
    }

    private WalkieScreen() {}

    public static ModularPanel build(PlayerInventoryGuiData data, PanelSyncManager sync) {
        ViewSync<View> viewSync = new ViewSync<>(() -> view(data), View::write, View::read, View.EMPTY);
        sync.syncValue("view", viewSync);
        sync.registerSyncedAction(CHANNEL, false, true, buf -> step(data, buf.readByte()));
        sync.registerSyncedAction(POWER, false, true, buf -> setOn(data, buf.readBoolean()));
        Supplier<View> view = viewSync::view;

        ModularPanel panel = new ModularPanel("becom_walkie").size(WIDTH, HEIGHT);
        Flow column = Flow.column()
            .sizeRel(1f)
            .padding(6, 6, 6, 6)
            .childPadding(3);
        column.child(
            IKey.lang("item.becom.walkie_talkie.name")
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
                .overlay(IKey.lang("becom.walkie.power"))
                .value(
                    new BoolValue.Dynamic(
                        () -> view.get()
                            .on(),
                        on -> sync.callSyncedAction(POWER, buf -> buf.writeBoolean(on)))));
        column.child(
            IKey.dynamic(
                () -> StatCollector.translateToLocalFormatted(
                    "becom.walkie.charge",
                    view.get()
                        .charge(),
                    view.get()
                        .max()))
                .asWidget()
                .widthRel(1f)
                .height(10));
        panel.child(column);
        return panel;
    }

    private static View view(PlayerInventoryGuiData data) {
        ItemStack stack = data.getUsedItemStack();
        if (!ItemWalkieTalkie.is(stack)) return View.EMPTY;
        ItemWalkieTalkie item = (ItemWalkieTalkie) stack.getItem();
        return new View(
            ItemWalkieTalkie.channel(stack),
            ItemWalkieTalkie.on(stack),
            (long) ItemWalkieTalkie.charge(stack),
            (long) item.getMaxCharge(stack));
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
