package com.enn3developer.becom.gui.phone;

import static com.enn3developer.becom.gui.phone.PhoneUi.SH;
import static com.enn3developer.becom.gui.phone.PhoneUi.SW;
import static com.enn3developer.becom.gui.phone.PhoneUi.SX;
import static com.enn3developer.becom.gui.phone.PhoneUi.SY;
import static com.enn3developer.becom.gui.phone.PhoneUi.t;

import java.util.ArrayList;
import java.util.List;

import com.enn3developer.becom.gui.PhoneView;
import com.enn3developer.becom.gui.paint.Canvas;
import com.enn3developer.becom.gui.paint.Glyphs;
import com.enn3developer.becom.gui.paint.Paint;
import com.enn3developer.becom.logic.PhoneNumbers;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;

/**
 * The phone's lists, from the Contacts board: Contacts, Recent and Settings › Blocked. Rows 40 tall with a face, a name
 * and a number, a green call button on the right, five to a page with the page count at the foot, and a menu on a
 * right click.
 */
final class PhoneLists {

    private static final float ROW = 40;
    // Five a page: the board's twelve contacts make three pages.
    private static final int ROWS = 5;
    private static final float FOOT = 26;
    // The menu: 144 wide with its 2-pixel edge, an item every 30.
    private static final float MENU_WIDTH = 144;
    private static final float ITEM = 30;

    /** One item of the right-click menu. */
    private record Item(String label, int color, Runnable press) {}

    private final PhoneUi ui;
    private int page;
    private List<Item> menu = List.of();
    private float menuX;
    private float menuY;
    // The row the menu is about, lit while it's open.
    private float menuRow;

    PhoneLists(PhoneUi ui) {
        this.ui = ui;
    }

    void reset() {
        page = 0;
        menu = List.of();
    }

    boolean menuOpen() {
        return !menu.isEmpty();
    }

    void closeMenu() {
        menu = List.of();
    }

    void scroll(int amount) {
        if (menuOpen()) return;
        page = Math.max(0, page - Integer.signum(amount));
    }

    /** The page the list is on, kept inside the list as it shrinks. */
    private int page(int size) {
        int pages = Math.max(1, (size + ROWS - 1) / ROWS);
        page = Math.min(page, pages - 1);
        return pages;
    }

    /**
     * Opens the menu for the row at {@code rowY}: at the mouse across, 16 under the row as on the board, so the row
     * stays in sight; over it when there's no room under.
     */
    private void openMenu(List<Item> items, float rowY) {
        menu = items;
        menuRow = rowY;
        float h = menuHeight();
        menuX = Math.min(ui.mouseX(), SX + SW - MENU_WIDTH - 8);
        menuY = rowY + ROW + 16;
        if (menuY + h > SY + SH - FOOT) menuY = rowY - 16 - h;
    }

    private float menuHeight() {
        return 2 + menu.size() * ITEM - 1 + 2;
    }

    // region Contacts

    void paintContacts(Canvas c, PhoneView v) {
        ui.paintStatus(c, v);
        float y = ui.paintBar(c, t(ui.adding ? "becom.ui.phone.add_who" : "becom.ui.app.contacts"), 0x2f5f9f, 0x1a3050);
        List<PhoneView.Contact> contacts = v.contacts();
        if (contacts.isEmpty()) {
            Paint.centered(c, t("becom.ui.contacts.none"), SX + SW / 2, y + 30, 13, Paint.GREY);
        }
        int pages = page(contacts.size());
        int first = page * ROWS;
        for (int i = first; i < Math.min(contacts.size(), first + ROWS); i++) {
            PhoneView.Contact contact = contacts.get(i);
            boolean telephone = contact.kind() == PhoneKind.TELEPHONE.ordinal();
            String sub = PhoneNumbers.format(contact.number())
                + (telephone ? ", " + t("becom.ui.contacts.telephone") : "");
            float rowY = y;
            paintRow(
                c,
                y,
                contact.name(),
                null,
                sub,
                Paint.GREY,
                telephone,
                () -> ui.dial(contact.number(), false),
                () -> {
                    List<Item> items = new ArrayList<>();
                    items.add(
                        new Item(
                            t("becom.ui.contacts.call", contact.name()),
                            Paint.WHITE,
                            () -> ui.dial(contact.number(), false)));
                    if (!telephone && v.video()
                        .capable()) {
                        items.add(
                            new Item(
                                t("becom.ui.contacts.video_call"),
                                Paint.WHITE,
                                () -> ui.dial(contact.number(), true)));
                    }
                    if (v.mayManage()) {
                        items.add(
                            new Item(
                                t("becom.ui.contacts.block"),
                                0xff6b55,
                                () -> ui.sender
                                    .send(PhoneActions.BLOCK_CONTACT, buf -> buf.writeInt(contact.number()))));
                    }
                    openMenu(items, rowY);
                });
            y += ROW;
        }
        paintFoot(c, pages);
        paintMenu(c);
    }

    // endregion

    // region Recent

    void paintRecent(Canvas c, PhoneView v) {
        ui.paintStatus(c, v);
        float y = ui.paintBar(c, t("becom.ui.app.recent"), 0xb8761c, 0x5a3a0a);
        List<PhoneView.Recent> recent = v.recent();
        if (recent.isEmpty()) {
            Paint.centered(c, t("becom.ui.recent.none"), SX + SW / 2, y + 30, 13, Paint.GREY);
        }
        int pages = page(recent.size());
        int first = page * ROWS;
        long now = System.currentTimeMillis();
        for (int i = first; i < Math.min(recent.size(), first + ROWS); i++) {
            PhoneView.Recent call = recent.get(i);
            boolean missed = call.direction() == PhoneRecord.Direction.MISSED;
            String sub = t(
                "becom.ui.recent." + call.direction()
                    .name()
                    .toLowerCase(java.util.Locale.ROOT),
                ago(now - call.time()));
            boolean named = !call.name()
                .isEmpty();
            String name = named ? call.name() : PhoneNumbers.format(call.number());
            float rowY = y;
            // A missed call in the boards' red, as the home screen's card has it.
            paintRow(
                c,
                y,
                name,
                named ? PhoneNumbers.format(call.number()) : null,
                sub,
                missed ? Paint.RED : Paint.GREY,
                false,
                () -> ui.dial(call.number(), false),
                () -> {
                    List<Item> items = new ArrayList<>();
                    items.add(
                        new Item(t("becom.ui.contacts.call", name), Paint.WHITE, () -> ui.dial(call.number(), false)));
                    if (v.video()
                        .capable()) {
                        items.add(
                            new Item(
                                t("becom.ui.contacts.video_call"),
                                Paint.WHITE,
                                () -> ui.dial(call.number(), true)));
                    }
                    if (v.mayManage()) {
                        items.add(
                            new Item(
                                t("becom.ui.contacts.block"),
                                0xff6b55,
                                () -> ui.sender.send(PhoneActions.BLOCK_RECENT, buf -> {
                                    buf.writeLong(call.time());
                                    buf.writeInt(call.number());
                                })));
                    }
                    openMenu(items, rowY);
                });
            y += ROW;
        }
        paintFoot(c, pages);
        paintMenu(c);
    }

    /** How long ago a call was: "now", "5 min ago", "2 h ago", "3 days ago". */
    private static String ago(long millis) {
        long minutes = Math.max(0, millis / 60_000);
        if (minutes < 1) return t("becom.ui.ago.now");
        if (minutes < 60) return t("becom.ui.ago.minutes", minutes);
        if (minutes < 48 * 60) return t("becom.ui.ago.hours", minutes / 60);
        return t("becom.ui.ago.days", minutes / (24 * 60));
    }

    // endregion

    // region Blocked

    void paintBlocked(Canvas c, PhoneView v) {
        ui.paintStatus(c, v);
        float y = ui.paintBar(c, t("becom.ui.settings.blocked_title"), 0x5a5d62, 0x2a2c2f);
        List<PhoneView.Blocked> blocked = v.blocked();
        if (blocked.isEmpty()) {
            Paint.centered(c, t("becom.ui.blocked.none"), SX + SW / 2, y + 30, 13, Paint.GREY);
        }
        int pages = page(blocked.size());
        int first = page * ROWS;
        for (int i = first; i < Math.min(blocked.size(), first + ROWS); i++) {
            PhoneView.Blocked one = blocked.get(i);
            c.face(one.name(), SX + 10, y + 8, 24);
            // The name alone in the middle of the row.
            c.text(one.name(), SX + 44, y + Math.round((ROW - 1 - c.lineHeight(15)) / 2), 15, Paint.WHITE);
            if (v.mayManage()) {
                String label = t("becom.ui.blocked.unblock");
                float w = c.width(label, 13) + 20;
                float bx = SX + SW - 10 - w;
                float by = y + (ROW - 24) / 2;
                Paint.button(c, bx, by, w, 24, Paint.Style.PLAIN, label, 13, ui.hover(bx, by, w, 24));
                ui.hit(bx, by, w, 24, () -> ui.sender.send(PhoneActions.UNBLOCK, buf -> {
                    buf.writeLong(
                        one.player()
                            .getMostSignificantBits());
                    buf.writeLong(
                        one.player()
                            .getLeastSignificantBits());
                }));
            }
            c.rect(SX, y + ROW - 1, SW, 1, Paint.LINE);
            y += ROW;
        }
        paintFoot(c, pages);
    }

    // endregion

    /**
     * One row: the player's face (a TEL chip for a Telephone), the name and a grey line under it, and the green call
     * button; a left click on the button calls, a right click anywhere opens the menu, and the row stays lit while
     * its menu is open.
     */
    private void paintRow(Canvas c, float y, String name, String number, String sub, int subColor, boolean telephone,
        Runnable call, Runnable menu) {
        if (menuOpen() ? menuRow == y : ui.hover(SX, y, SW, ROW)) {
            c.rect(SX, y, SW, ROW - 1, menuOpen() ? 0x161b21 : 0x141a20);
        }
        if (telephone) {
            c.rect(SX + 10, y + 8, 24, 24, 0x3a3a3a);
            Paint.centered(c, "TEL", SX + 22, y + 8 + (24 - c.lineHeight(11)) / 2, 11, Paint.LIGHT);
        } else {
            c.face(name, SX + 10, y + 8, 24);
        }
        // The name in 15, its number after it in 12 when there is one, the line under in 12; then the 30-pixel
        // button, 10 from the edge.
        float textX = SX + 44;
        float max = SW - 44 - 10 - 30 - 10;
        float numberW = number == null ? 0 : c.width(number, 12) + 8;
        String shown = Paint.fit(c, name, 15, max - numberW);
        float top = y + Math.round((ROW - 1 - c.lineHeight(15) - 3 - c.lineHeight(12)) / 2);
        c.text(shown, textX, top, 15, Paint.WHITE);
        if (number != null)
            c.text(number, textX + c.width(shown, 15) + 8, top + c.lineHeight(15) - c.lineHeight(12), 12, Paint.GREY);
        c.text(Paint.fit(c, sub, 12, max), textX, top + c.lineHeight(15) + 3, 12, subColor);
        float bx = SX + SW - 10 - 30;
        float by = y + 5;
        boolean button = ui.hover(bx, by, 30, 30);
        c.rect(bx, by, 30, 30, 0x2f8a3a);
        c.rect(bx + 1, by + 1, 28, 28, button ? 0x2a5a32 : 0x1f3a24);
        Paint.glyph(c, bx + 8, by + 8, 14 / 8f, Glyphs.PHONE, Paint.GREEN, 0x1f3a24);
        c.rect(SX, y + ROW - 1, SW, 1, Paint.LINE);
        ui.hit(SX, y, SW, ROW, null, menu);
        ui.hit(bx, by, 30, 30, call, menu);
    }

    /**
     * The foot of a list: the page count on the right, in the board's 12-pixel line 6 above the edge, while there's
     * more than one page; the wheel turns the page.
     */
    private void paintFoot(Canvas c, int pages) {
        if (pages <= 1) return;
        float y = SY + SH - FOOT + 6 + 3;
        String label = (page + 1) + " / " + pages;
        float w = c.width(label, 12);
        Paint.right(c, label, SX + SW - 10, y, 12, Paint.LIGHT);
        // A click on the page count turns to the next page, and back to the first after the last.
        ui.hit(SX + SW - 10 - w - 6, y - 6, w + 12, c.lineHeight(12) + 12, () -> page = (page + 1) % pages);
    }

    /**
     * The right-click menu, over everything else on the screen: the board's box with a 2-pixel edge, its items 29
     * tall with a line between, their 14-pixel text 10 in.
     */
    private void paintMenu(Canvas c) {
        if (menu.isEmpty()) return;
        Paint.box(c, menuX, menuY, MENU_WIDTH, menuHeight(), 2, Paint.LINE, 0x4a5560, Paint.STATUS);
        // Clicks anywhere else close it: a box under its items catches them.
        ui.hit(SX, SY, SW, SH, this::closeMenu, this::closeMenu);
        float x = menuX + 2;
        float w = MENU_WIDTH - 4;
        float y = menuY + 2;
        for (int i = 0; i < menu.size(); i++) {
            Item item = menu.get(i);
            if (ui.hover(x, y, w, ITEM - 1)) c.rect(x, y, w, ITEM - 1, 0x2a323c);
            c.text(item.label(), x + 10, y + 6 + 5, 14, item.color());
            if (i < menu.size() - 1) c.rect(x, y + ITEM - 1, w, 1, 0x2a3038);
            ui.hit(x, y, w, ITEM, () -> {
                closeMenu();
                item.press()
                    .run();
            });
            y += ITEM;
        }
    }
}
