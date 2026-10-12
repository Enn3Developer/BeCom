package com.enn3developer.becom.gui;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.block.TileTelephone;
import com.enn3developer.becom.logic.PhoneNumbers;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.server.PhoneScreenActions;

/**
 * The phone screen (DESIGN.md 11.2): keypad, contacts, recent and missed calls, and settings, with the call's state on
 * top. Built on both sides; the server registers the actions and checks each one, the client only draws and asks.
 */
public final class PhoneScreen {

    private static final int WIDTH = 184;
    private static final int HEIGHT = 206;
    private static final int ROWS = 7;
    private static final int ROW_HEIGHT = 13;
    // The settings page has two toggles and a heading above its block list.
    private static final int BLOCKED_ROWS = ROWS - 4;
    private static final int KEY_SIZE = 22;
    private static final int TAB_COUNT = 4;
    private static final int BARS_WIDTH = 14;

    private static final String DIAL = "dial";
    private static final String PICK_UP = "pickup";
    private static final String HANG_UP = "hangup";
    private static final String DND = "dnd";
    private static final String HIDE = "hide";
    private static final String BLOCK_CONTACT = "block_contact";
    private static final String BLOCK_RECENT = "block_recent";
    private static final String UNBLOCK = "unblock";

    private PhoneScreen() {}

    public static ModularPanel build(TileTelephone tile, PosGuiData data, PanelSyncManager sync) {
        EntityPlayer player = data.getPlayer();
        PhoneViewSync viewSync = new PhoneViewSync(() -> PhoneScreenActions.view(player, tile));
        sync.syncValue("view", viewSync);
        sync.registerSyncedAction(DIAL, false, true, buf -> PhoneScreenActions.dial(player, tile, buf.readInt()));
        sync.registerSyncedAction(PICK_UP, false, true, buf -> PhoneScreenActions.pickUp(player, tile));
        sync.registerSyncedAction(HANG_UP, false, true, buf -> PhoneScreenActions.hangUp(player, tile));
        sync.registerSyncedAction(
            DND,
            false,
            true,
            buf -> PhoneScreenActions.setDoNotDisturb(player, tile, buf.readBoolean()));
        sync.registerSyncedAction(
            HIDE,
            false,
            true,
            buf -> PhoneScreenActions.setHidden(player, tile, buf.readBoolean()));
        sync.registerSyncedAction(
            BLOCK_CONTACT,
            false,
            true,
            buf -> PhoneScreenActions.blockContact(player, tile, buf.readInt()));
        sync.registerSyncedAction(
            BLOCK_RECENT,
            false,
            true,
            buf -> PhoneScreenActions.blockRecent(player, tile, buf.readLong(), buf.readInt()));
        sync.registerSyncedAction(
            UNBLOCK,
            false,
            true,
            buf -> PhoneScreenActions.unblock(player, tile, new UUID(buf.readLong(), buf.readLong())));

        Supplier<PhoneView> view = viewSync::view;
        StringBuilder typed = new StringBuilder();
        PagedWidget.Controller tabs = new PagedWidget.Controller();

        ModularPanel panel = new ModularPanel("becom_phone").size(WIDTH, HEIGHT);
        Flow column = Flow.column()
            .sizeRel(1f)
            .padding(6, 6, 6, 6)
            .childPadding(3);
        column.child(
            IKey.dynamic(
                () -> view.get()
                    .number() < 0 ? ""
                        : StatCollector.translateToLocalFormatted(
                            "becom.phone.title",
                            view.get()
                                .number(),
                            view.get()
                                .owner()))
                .asWidget()
                .widthRel(1f)
                .height(10));
        Flow statusRow = Flow.row()
            .widthRel(1f)
            .height(10);
        statusRow.child(
            IKey.dynamic(() -> status(view.get()))
                .asWidget()
                .width(WIDTH - 12 - BARS_WIDTH)
                .height(10));
        // The call's signal bars (DESIGN.md 11.2), drawn only on the client.
        statusRow.child(
            BeCom.proxy.signalBars(
                () -> view.get()
                    .state() == PhoneView.State.CONNECTED ? view.get()
                        .hearing() : SignalLevel.NONE)
                .asWidget()
                .size(BARS_WIDTH, 10));
        column.child(statusRow);
        Flow tabRow = Flow.row()
            .widthRel(1f)
            .height(14)
            .childPadding(2);
        String[] tabNames = { "keypad", "contacts", "recent", "settings" };
        for (int i = 0; i < TAB_COUNT; i++) {
            tabRow.child(
                new PageButton(i, tabs).size((WIDTH - 12 - 2 * (TAB_COUNT - 1)) / TAB_COUNT, 14)
                    .overlay(IKey.lang("becom.phone.tab." + tabNames[i])));
        }
        column.child(tabRow);
        column.child(
            new PagedWidget<>().controller(tabs)
                .widthRel(1f)
                .height(HEIGHT - 12 - 10 - 10 - 14 - 9)
                .addPage(keypad(sync, view, typed))
                .addPage(
                    rows(
                        sync,
                        () -> view.get()
                            .contacts()
                            .size(),
                        i -> {
                            PhoneView.Contact c = view.get()
                                .contacts()
                                .get(i);
                            return c.name() + "  " + c.number();
                        },
                        i -> view.get()
                            .contacts()
                            .get(i)
                            .number(),
                        (i, n) -> sync.callSyncedAction(BLOCK_CONTACT, buf -> buf.writeInt(n)),
                        view))
                .addPage(
                    rows(
                        sync,
                        () -> view.get()
                            .recent()
                            .size(),
                        i -> {
                            PhoneView.Recent r = view.get()
                                .recent()
                                .get(i);
                            return StatCollector.translateToLocal(
                                "becom.phone.recent." + r.direction()
                                    .name()
                                    .toLowerCase(java.util.Locale.ROOT))
                                + " "
                                + r.name()
                                + "  "
                                + r.number();
                        },
                        i -> view.get()
                            .recent()
                            .get(i)
                            .number(),
                        (i, n) -> {
                            long time = view.get()
                                .recent()
                                .get(i)
                                .time();
                            sync.callSyncedAction(BLOCK_RECENT, buf -> {
                                buf.writeLong(time);
                                buf.writeInt(n);
                            });
                        },
                        view))
                .addPage(settings(sync, view)));
        panel.child(column);
        return panel;
    }

    private static String status(PhoneView view) {
        String other = view.otherName()
            .isEmpty() ? String.valueOf(view.otherNumber()) : view.otherName();
        return switch (view.state()) {
            case IDLE -> StatCollector.translateToLocal("becom.phone.state.idle");
            case OFF_HOOK -> StatCollector.translateToLocal("becom.phone.state.off_hook");
            case RINGING_OUT -> StatCollector.translateToLocalFormatted("becom.phone.state.ringing_out", other);
            case RINGING_IN -> StatCollector.translateToLocalFormatted("becom.phone.state.ringing_in", other);
            case CONNECTED -> StatCollector.translateToLocalFormatted("becom.phone.state.connected", other);
            case IN_USE -> StatCollector.translateToLocal("becom.phone.state.in_use");
        };
    }

    private static ParentWidget<?> keypad(PanelSyncManager sync, Supplier<PhoneView> view, StringBuilder typed) {
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(2);
        column.child(
            IKey.dynamic(
                () -> typed.length() == 0 ? StatCollector.translateToLocal("becom.phone.type_number")
                    : typed.toString())
                .asWidget()
                .widthRel(1f)
                .height(10));
        String[][] keys = { { "1", "2", "3" }, { "4", "5", "6" }, { "7", "8", "9" }, { "C", "0", "<" } };
        for (String[] rowKeys : keys) {
            Flow row = Flow.row()
                .coverChildren()
                .childPadding(2);
            for (String key : rowKeys) {
                row.child(
                    new ButtonWidget<>().size(KEY_SIZE, KEY_SIZE - 6)
                        .overlay(IKey.str(key))
                        .onMousePressed(button -> {
                            switch (key) {
                                case "C" -> typed.setLength(0);
                                case "<" -> {
                                    if (typed.length() > 0) typed.setLength(typed.length() - 1);
                                }
                                default -> {
                                    if (typed.length() < PhoneNumbers.DIGITS) typed.append(key);
                                }
                            }
                            return true;
                        }));
            }
            column.child(row);
        }
        Flow actions = Flow.row()
            .coverChildren()
            .childPadding(2);
        actions.child(
            new ButtonWidget<>().size(54, 14)
                .overlay(IKey.lang("becom.phone.call"))
                .onMousePressed(button -> {
                    int number = PhoneNumbers.parse(typed.toString());
                    if (number >= 0) sync.callSyncedAction(DIAL, buf -> buf.writeInt(number));
                    return true;
                }));
        actions.child(
            new ButtonWidget<>().size(54, 14)
                .overlay(IKey.lang("becom.phone.answer"))
                .setEnabledIf(
                    w -> view.get()
                        .state() == PhoneView.State.RINGING_IN)
                .onMousePressed(button -> {
                    sync.callSyncedAction(PICK_UP, buf -> {});
                    return true;
                }));
        actions.child(
            new ButtonWidget<>().size(54, 14)
                .overlay(IKey.lang("becom.phone.hang_up"))
                .onMousePressed(button -> {
                    sync.callSyncedAction(HANG_UP, buf -> {});
                    return true;
                }));
        column.child(actions);
        return column;
    }

    /**
     * A paged list: each row dials its number; for the phone's owner, a second button blocks whoever the row stands for
     * (X9). The server decides who that is from the row, so the client only names the row.
     */
    private static ParentWidget<?> rows(PanelSyncManager sync, IntSupplier size, IntFunction<String> label,
        IntFunction<Integer> number, BiConsumer<Integer, Integer> block, Supplier<PhoneView> view) {
        int[] page = { 0 };
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(1);
        for (int r = 0; r < ROWS; r++) {
            int row = r;
            Flow line = Flow.row()
                .widthRel(1f)
                .height(ROW_HEIGHT)
                .childPadding(2);
            line.child(
                new ButtonWidget<>().width(WIDTH - 12 - 34)
                    .height(ROW_HEIGHT)
                    .overlay(IKey.dynamic(() -> {
                        int i = page[0] * ROWS + row;
                        return i < size.getAsInt() ? label.apply(i) : "";
                    }))
                    .setEnabledIf(w -> page[0] * ROWS + row < size.getAsInt())
                    .onMousePressed(button -> {
                        int i = page[0] * ROWS + row;
                        if (i < size.getAsInt()) {
                            int n = number.apply(i);
                            sync.callSyncedAction(DIAL, buf -> buf.writeInt(n));
                        }
                        return true;
                    }));
            line.child(
                new ButtonWidget<>().width(32)
                    .height(ROW_HEIGHT)
                    .overlay(IKey.lang("becom.phone.block"))
                    .setEnabledIf(
                        w -> view.get()
                            .mayManage() && page[0] * ROWS + row < size.getAsInt())
                    .onMousePressed(button -> {
                        int i = page[0] * ROWS + row;
                        if (i < size.getAsInt()) block.accept(i, number.apply(i));
                        return true;
                    }));
            column.child(line);
        }
        column.child(pager(page, size, ROWS));
        return column;
    }

    private static Flow pager(int[] page, IntSupplier size, int rows) {
        Flow pager = Flow.row()
            .coverChildren()
            .childPadding(4);
        pager.child(
            new ButtonWidget<>().size(20, 12)
                .overlay(IKey.str("<"))
                .onMousePressed(button -> {
                    if (page[0] > 0) page[0]--;
                    return true;
                }));
        pager.child(IKey.dynamic(() -> {
            // A list can shrink under an open page, after an unblock or a new view.
            int pages = Math.max(1, (size.getAsInt() + rows - 1) / rows);
            if (page[0] >= pages) page[0] = pages - 1;
            return (page[0] + 1) + "/" + pages;
        })
            .asWidget()
            .size(30, 12));
        pager.child(
            new ButtonWidget<>().size(20, 12)
                .overlay(IKey.str(">"))
                .onMousePressed(button -> {
                    if ((page[0] + 1) * rows < size.getAsInt()) page[0]++;
                    return true;
                }));
        return pager;
    }

    private static ParentWidget<?> settings(PanelSyncManager sync, Supplier<PhoneView> view) {
        Flow column = Flow.column()
            .sizeRel(1f)
            .childPadding(2);
        column.child(
            toggle(
                sync,
                DND,
                "becom.phone.dnd",
                () -> view.get()
                    .doNotDisturb(),
                view));
        column.child(
            toggle(
                sync,
                HIDE,
                "becom.phone.hidden",
                () -> view.get()
                    .hidden(),
                view));
        column.child(
            IKey.lang("becom.phone.blocked")
                .asWidget()
                .widthRel(1f)
                .height(10));
        int[] page = { 0 };
        IntSupplier size = () -> view.get()
            .blocked()
            .size();
        for (int r = 0; r < BLOCKED_ROWS; r++) {
            int row = r;
            Flow line = Flow.row()
                .widthRel(1f)
                .height(ROW_HEIGHT)
                .childPadding(2);
            line.child(IKey.dynamic(() -> {
                int i = page[0] * BLOCKED_ROWS + row;
                List<PhoneView.Blocked> blocked = view.get()
                    .blocked();
                return i < blocked.size() ? blocked.get(i)
                    .name() : "";
            })
                .asWidget()
                .width(WIDTH - 12 - 50)
                .height(ROW_HEIGHT));
            line.child(
                new ButtonWidget<>().width(48)
                    .height(ROW_HEIGHT)
                    .overlay(IKey.lang("becom.phone.unblock"))
                    .setEnabledIf(w -> page[0] * BLOCKED_ROWS + row < size.getAsInt())
                    .onMousePressed(button -> {
                        int i = page[0] * BLOCKED_ROWS + row;
                        List<PhoneView.Blocked> blocked = view.get()
                            .blocked();
                        if (i < blocked.size()) {
                            UUID id = blocked.get(i)
                                .player();
                            sync.callSyncedAction(UNBLOCK, buf -> {
                                buf.writeLong(id.getMostSignificantBits());
                                buf.writeLong(id.getLeastSignificantBits());
                            });
                        }
                        return true;
                    }));
            column.child(line);
        }
        column.child(pager(page, size, BLOCKED_ROWS));
        column.child(
            IKey.dynamic(
                () -> view.get()
                    .mayManage() ? "" : StatCollector.translateToLocal("becom.phone.not_owner"))
                .asWidget()
                .widthRel(1f)
                .height(10));
        return column;
    }

    private static ToggleButton toggle(PanelSyncManager sync, String action, String langKey,
        java.util.function.BooleanSupplier current, Supplier<PhoneView> view) {
        return new ToggleButton().size(WIDTH - 12, 14)
            .overlay(IKey.lang(langKey))
            .value(new BoolValue.Dynamic(current, on -> sync.callSyncedAction(action, buf -> buf.writeBoolean(on))))
            .setEnabledIf(
                w -> view.get()
                    .mayManage());
    }
}
