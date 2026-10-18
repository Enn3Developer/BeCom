package com.enn3developer.becom.gui.phone;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.gui.PhoneView;
import com.enn3developer.becom.gui.paint.Canvas;
import com.enn3developer.becom.gui.paint.CanvasScreen;
import com.enn3developer.becom.gui.paint.Paint;
import com.enn3developer.becom.logic.PhoneNumbers;
import com.enn3developer.becom.logic.phone.PhoneRecord;

/**
 * The desk phones, from the Telephone, Telephone display and Videophone boards: a grey panel with a two-line display,
 * a keypad, speed-dial keys, and Redial, Calls and Menu, which step through recent calls and the settings on the
 * display. The Videophone adds its screen on top, a Camera key, and Answer and Hang up, as it has no handset.
 */
public final class DeskPhoneUi extends CanvasScreen {

    // The panel's colours, Minecraft's own GUI grey.
    private static final int PANEL = 0xc6c6c6;
    private static final int INK = 0x404040;
    private static final int NOTE = 0x5a5a5a;
    private static final int LCD = 0x1e2a10;
    private static final int LCD_TEXT = 0xb8f040;
    private static final int KEY = 0x8b8b8b;
    private static final int FUNCTION = 0xb4b4b4;
    private static final int LED_OFF = 0x3a3a3a;
    private static final float WIDTH = 484;
    private static final float X0 = 18;
    private static final float INNER = 448;
    private static final float SCALE = 0.75f;

    /** What the display shows: the call or the number being typed, recent calls, or the menu. */
    private enum Mode {
        NORMAL,
        CALLS,
        MENU
    }

    private final Supplier<PhoneView> view;
    private final PhoneUi.Sender sender;
    private final boolean videophone;
    private final StringBuilder typed = new StringBuilder();
    private Mode mode = Mode.NORMAL;
    private int index;
    // C was pressed once on a call: the second press blocks its caller.
    private boolean confirmBlock;
    private IDrawable screen;

    public DeskPhoneUi(Supplier<PhoneView> view, PhoneUi.Sender sender, boolean videophone) {
        this.view = view;
        this.sender = sender;
        this.videophone = videophone;
    }

    /** The scale a desk phone is drawn at on a screen {@code guiHeight} GUI pixels tall. */
    public static float scaleFor(boolean videophone, int guiHeight) {
        float height = videophone ? 624 : 504;
        if (guiHeight <= 0) return SCALE;
        return Math.min(SCALE, (guiHeight - 8) / height);
    }

    @Override
    public float width() {
        return WIDTH;
    }

    @Override
    public float height() {
        return videophone ? 624 : 504;
    }

    private static String t(String key, Object... args) {
        return args.length == 0 ? StatCollector.translateToLocal(key)
            : StatCollector.translateToLocalFormatted(key, args);
    }

    private int keys() {
        return videophone ? 3 : PhoneRecord.SPEED_DIAL_KEYS;
    }

    // region Painting

    @Override
    protected void paint(Canvas c) {
        PhoneView v = view.get();
        float h = height();
        c.rect(0, 0, WIDTH, h, Paint.BLACK);
        Paint.box(c, 2, 2, WIDTH - 4, h - 4, 2, PANEL, 0xffffff, 0x555555);
        float y = X0;
        paintTitle(c, v, y);
        y += 22 + (videophone ? 10 : 12);
        if (videophone) {
            Paint.box(c, X0, y, INNER, 200, 2, 0x101418, 0x373737, 0xffffff);
            if (screen == null) screen = BeCom.proxy.videoView(() -> true);
            c.drawable(screen, X0 + 6, y + 6, INNER - 12, 188);
            y += 200 + 10;
        }
        float lcdHeight = videophone ? 60 : 72;
        paintDisplay(c, v, y, lcdHeight);
        y += lcdHeight + (videophone ? 10 : 12);
        // The Videophone's board explains its speakerphone where the Telephone's explains its speed dial.
        String hint = videophone ? t(
            "becom.ui.desk.hint_video",
            (int) BeComConfig.get()
                .videophoneRoom())
            : t("becom.ui.desk.hint");
        List<String> hints = Paint.wrap(c, hint, 12, INNER);
        float footY = h - X0 - c.lineHeight(12) - (hints.size() - 1) * 16;
        for (int i = 0; i < hints.size(); i++) c.text(hints.get(i), X0, footY + i * 16, 12, NOTE);
        float bottom = footY - (videophone ? 10 : 12);
        paintKeypad(c, v, y);
        paintRight(c, v, y, bottom);
    }

    /** "Telephone 482913" or "Videophone 300417", and on the right the MV chip and the line's light. */
    private void paintTitle(Canvas c, PhoneView v, float y) {
        String title = t(
            videophone ? "becom.ui.desk.videophone" : "becom.ui.desk.telephone",
            v.number() < 0 ? "" : PhoneNumbers.format(v.number()));
        c.text(title, X0, y + (22 - c.lineHeight(18)) / 2, 18, INK);
        float right = X0 + INNER;
        String line = t("becom.ui.desk.line");
        float ty = y + (22 - c.lineHeight(13)) / 2;
        right -= c.width(line, 13);
        c.text(line, right, ty, 13, INK);
        right -= 6 + 10;
        led(c, right, y + 6, v.offline() ? Paint.RED : Paint.GREEN);
        if (videophone) {
            float w = c.width("MV", 13) + 10;
            right -= 10 + w;
            c.rect(right, y + 3, w, 16, 0x4a6a9a);
            Paint.centered(c, "MV", right + w / 2, y + 3 + (16 - c.lineHeight(13)) / 2, 13, Paint.WHITE);
        }
    }

    private static void led(Canvas c, float x, float y, int color) {
        c.rect(x, y, 10, 10, 0x303030);
        c.rect(x + 1, y + 1, 8, 8, color);
    }

    /** The display: an inset green LCD with two lines, each with something on its right. */
    private void paintDisplay(Canvas c, PhoneView v, float y, float h) {
        Paint.box(c, X0, y, INNER, h, 2, LCD, 0x373737, 0xffffff);
        String[] lines = lines(v);
        float size = videophone ? 24 : 26;
        float lineHeight = videophone ? 26 : 28;
        float top = y + (h - 2 * lineHeight) / 2 + (lineHeight - c.lineHeight(size)) / 2;
        for (int i = 0; i < 2; i++) {
            float ly = top + i * lineHeight;
            String left = lines[i * 2];
            String right = lines[i * 2 + 1];
            float rightWidth = right.isEmpty() ? 0 : c.width(chip(right), size);
            c.text(Paint.fit(c, left, size, INNER - 28 - rightWidth - 12), X0 + 14, ly, size, LCD_TEXT);
            if (right.isEmpty()) continue;
            if (right.startsWith("#")) {
                // An inverted chip: a setting's state.
                String word = chip(right);
                float w = c.width(word, size) + 6;
                float x = X0 + INNER - 14 - w;
                c.rect(x, ly - 3, w, lineHeight - 2, LCD_TEXT);
                c.text(word, x + 3, ly, size, LCD);
            } else {
                Paint.right(c, right, X0 + INNER - 14, ly, size, LCD_TEXT);
            }
        }
    }

    private static String chip(String right) {
        return right.startsWith("#") ? right.substring(1) : right;
    }

    /** The display's two lines, each as its left and right text; a right text starting with # is a chip. */
    private String[] lines(PhoneView v) {
        if (mode != Mode.NORMAL && !v.mayManage()) {
            return new String[] { up("becom.ui.desk.owner_only"), "", up("becom.ui.desk.team", v.owner()), "" };
        }
        if (mode == Mode.CALLS) return callLines(v);
        if (mode == Mode.MENU) return menuLines(v);
        String here = videophone && v.phone()
            .here() > 0 ? up(
                "becom.ui.desk.here",
                v.phone()
                    .here())
                : "";
        String number = typed + "_";
        if (v.offline()) return new String[] { up("becom.ui.desk.no_line"), here, typed.toString(), "" };
        String other = (v.otherName()
            .isEmpty() ? "" : v.otherName() + " ") + (v.otherNumber() > 0 ? PhoneNumbers.format(v.otherNumber()) : "");
        return switch (v.state()) {
            case IDLE -> new String[] { up(videophone ? "becom.ui.desk.ready" : "becom.ui.desk.on_hook"), here, number,
                "" };
            case OFF_HOOK -> new String[] { up("becom.ui.desk.dial_tone"), here, number, "" };
            case RINGING_OUT -> new String[] { up("becom.ui.desk.calling"), here, other.toUpperCase(Locale.ROOT), "" };
            case RINGING_IN -> new String[] { up("becom.ui.desk.ringing"), here, other.toUpperCase(Locale.ROOT), "" };
            case CONNECTED -> new String[] { up("becom.ui.desk.in_call"), here,
                typed.length() > 0 ? number : members(v), "" };
            case IN_USE -> new String[] { up("becom.ui.desk.in_use"), here, "", "" };
        };
    }

    private static String members(PhoneView v) {
        List<String> names = new ArrayList<>();
        for (PhoneView.Member m : v.members()) names.add(m.name());
        if (names.isEmpty()) names.add(v.otherName());
        return String.join(", ", names)
            .toUpperCase(Locale.ROOT);
    }

    private String[] callLines(PhoneView v) {
        List<PhoneView.Recent> recent = v.recent();
        if (recent.isEmpty())
            return new String[] { up("becom.ui.desk.calls"), "0/0", up("becom.ui.desk.no_calls"), "" };
        index = Math.min(index, recent.size() - 1);
        PhoneView.Recent call = recent.get(index);
        String count = (index + 1) + "/" + recent.size();
        if (confirmBlock)
            return new String[] { up("becom.ui.desk.calls"), count, up("becom.ui.desk.block_confirm"), "" };
        String who = (call.name()
            .isEmpty() ? "" : call.name() + " ") + PhoneNumbers.format(call.number());
        String direction = up(
            "becom.ui.desk.dir." + call.direction()
                .name()
                .toLowerCase(Locale.ROOT));
        return new String[] { up("becom.ui.desk.calls"), count, who.toUpperCase(Locale.ROOT), direction };
    }

    private String[] menuLines(PhoneView v) {
        if (index == 0) {
            return new String[] { up("becom.ui.desk.menu"), "1/3", up("becom.ui.desk.dnd"),
                "#" + up(v.doNotDisturb() ? "becom.ui.desk.on" : "becom.ui.desk.off") };
        }
        if (index == 1) {
            return new String[] { up("becom.ui.desk.menu"), "2/3", up("becom.ui.desk.unlisted"),
                "#" + up(v.hidden() ? "becom.ui.desk.on" : "becom.ui.desk.off") };
        }
        List<PhoneView.Blocked> blocked = v.blocked();
        if (blocked.isEmpty())
            return new String[] { up("becom.ui.desk.blocked"), "0/0", up("becom.ui.desk.nobody"), "" };
        int i = Math.min(index - 2, blocked.size() - 1);
        return new String[] { up("becom.ui.desk.blocked"), (i + 1) + "/" + blocked.size(), blocked.get(i)
            .name()
            .toUpperCase(Locale.ROOT), up("becom.ui.desk.free") };
    }

    private static String up(String key, Object... args) {
        return t(key, args).toUpperCase(Locale.ROOT);
    }

    /** The keypad: digits, C, delete, and the wide key under them. */
    private void paintKeypad(Canvas c, PhoneView v, float y) {
        float width = videophone ? 180 : 200;
        float row = videophone ? 32 : 38;
        float gap = videophone ? 5 : 6;
        float keyWidth = (width - 2 * gap) / 3;
        String[] keys = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "<" };
        for (int i = 0; i < keys.length; i++) {
            float kx = X0 + (i % 3) * (keyWidth + gap);
            float ky = y + (i / 3) * (row + gap);
            String key = keys[i];
            boolean clear = key.equals("C");
            key(
                c,
                kx,
                ky,
                keyWidth,
                row,
                key,
                clear ? (videophone ? 15 : 16) : 20,
                clear ? 0xffd0c8 : Paint.WHITE,
                KEY,
                0xdbdbdb,
                0x3a3a3a);
            hit(kx, ky, keyWidth, row, () -> press(key.charAt(0)));
        }
        float wy = y + 4 * (row + gap);
        Paint.Style style;
        String label;
        Runnable action;
        PhoneView.State state = v.state();
        if (videophone && state == PhoneView.State.RINGING_IN) {
            style = Paint.Style.GO;
            label = t("becom.ui.desk.answer");
            action = () -> sender.send(PhoneActions.PICK_UP, buf -> {});
        } else if (videophone && (state == PhoneView.State.CONNECTED || state == PhoneView.State.RINGING_OUT)
            && typed.length() == 0) {
                style = Paint.Style.DANGER;
                label = t("becom.ui.desk.hang_up");
                action = () -> sender.send(PhoneActions.HANG_UP, buf -> {});
            } else {
                style = Paint.Style.GO;
                label = t("becom.ui.desk.dial");
                action = this::dialKey;
            }
        key(c, X0, wy, width, row, label, videophone ? 16 : 17, Paint.WHITE, style.face, style.light, style.dark);
        hit(X0, wy, width, row, action);
    }

    /** A key of the boards: a bevelled face and a white label with a dark shadow two pixels down. */
    private void key(Canvas c, float x, float y, float w, float h, String label, float size, int color, int face,
        int light, int dark) {
        Paint.box(c, x, y, w, h, 2, hover(x, y, w, h) ? Paint.brighter(face) : face, light, dark);
        float ty = y + (h - c.lineHeight(size)) / 2;
        Paint.centered(c, label, x + w / 2 + 2, ty + 2, size, 0x3f3f3f);
        Paint.centered(c, label, x + w / 2, ty, size, color);
    }

    /** Speed dial on the right, then Redial, Calls and Menu (and the Videophone's Camera) at the foot. */
    private void paintRight(Canvas c, PhoneView v, float y, float bottom) {
        float x = X0 + (videophone ? 180 + 14 : 200 + 16);
        float w = X0 + INNER - x;
        float gap = videophone ? 5 : 6;
        c.text(t("becom.ui.desk.speed_dial").toUpperCase(Locale.ROOT), x, y, 12, NOTE);
        float ky = y + c.lineHeight(12) + gap + 2;
        List<Integer> stored = v.phone()
            .speedDial();
        for (int k = 0; k < keys(); k++) {
            int number = k < stored.size() ? stored.get(k) : 0;
            String name = number == 0 ? t("becom.ui.desk.empty") : nameOf(v, number);
            function(c, x, ky, w, name, "M" + (k + 1), number == 0 ? 0x8a8a8a : 0x303030);
            int key = k;
            hit(x, ky, w, 30, () -> { if (number != 0) dial(number); }, () -> storeSpeedDial(key));
            ky += 30 + gap;
        }
        boolean missed = PhoneUi.newMissed(v) > 0;
        if (videophone) {
            float half = (w - gap) / 2;
            float row1 = bottom - 30 - gap - 30;
            boolean camera = v.phone()
                .camera();
            ledButton(c, x, row1, half, camera ? Paint.GREEN : LED_OFF, t("becom.ui.desk.camera"), () -> {
                sender.send(PhoneActions.CAMERA_KEY, buf -> buf.writeBoolean(!camera));
                if (v.state() == PhoneView.State.CONNECTED)
                    sender.send(PhoneActions.FRONT, buf -> buf.writeBoolean(!camera));
            });
            ledButton(c, x + half + gap, row1, half, -1, t("becom.ui.desk.redial"), () -> redial(v));
            ledButton(
                c,
                x,
                bottom - 30,
                half,
                missed ? Paint.ORANGE : LED_OFF,
                t("becom.ui.desk.calls_key"),
                this::calls);
            ledButton(c, x + half + gap, bottom - 30, half, -1, t("becom.ui.desk.menu_key"), this::menu);
        } else {
            float third = (w - 2 * gap) / 3;
            ledButton(c, x, bottom - 30, third, -1, t("becom.ui.desk.redial"), () -> redial(v));
            ledButton(
                c,
                x + third + gap,
                bottom - 30,
                third,
                missed ? Paint.ORANGE : LED_OFF,
                t("becom.ui.desk.calls_key"),
                this::calls);
            ledButton(c, x + 2 * (third + gap), bottom - 30, third, -1, t("becom.ui.desk.menu_key"), this::menu);
        }
    }

    /** A grey function key with a name on the left and a small label on the right. */
    private void function(Canvas c, float x, float y, float w, String left, String right, int color) {
        Paint.box(c, x, y, w, 30, 2, hover(x, y, w, 30) ? 0xc4c4c4 : FUNCTION, 0xe8e8e8, 0x6a6a6a);
        float ty = y + (30 - c.lineHeight(14)) / 2;
        c.text(Paint.fit(c, left, 14, w - 48), x + 10, ty, 14, color);
        Paint.right(c, right, x + w - 10, y + (30 - c.lineHeight(12)) / 2, 12, color == 0x8a8a8a ? color : 0x6a6a6a);
    }

    /** A grey key with its label centred, and a light before it when {@code led} isn't -1. */
    private void ledButton(Canvas c, float x, float y, float w, int led, String label, Runnable press) {
        Paint.box(c, x, y, w, 30, 2, hover(x, y, w, 30) ? 0xc4c4c4 : FUNCTION, 0xe8e8e8, 0x6a6a6a);
        float lw = c.width(label, 14);
        float total = led == -1 ? lw : lw + 16;
        float lx = x + (w - total) / 2;
        float ty = y + (30 - c.lineHeight(14)) / 2;
        if (led != -1) {
            led(c, lx, y + 10, led);
            lx += 16;
        }
        c.text(label, lx, ty, 14, 0x303030);
        hit(x, y, w, 30, press);
    }

    private static String nameOf(PhoneView v, int number) {
        for (PhoneView.Contact contact : v.contacts()) if (contact.number() == number) return contact.name();
        return PhoneNumbers.format(number);
    }

    // endregion

    // region Keys

    private void press(char key) {
        PhoneView v = view.get();
        if (key == 'C') {
            clearKey(v);
            return;
        }
        if (key == '<') {
            if (mode != Mode.NORMAL) {
                normal();
            } else if (typed.length() > 0) {
                typed.setLength(typed.length() - 1);
            }
            return;
        }
        normal();
        if (typed.length() < PhoneNumbers.DIGITS) typed.append(key);
    }

    /**
     * C: clears what's typed; on a recent call it blocks the caller on the second press; on a blocked player, frees
     * them.
     */
    private void clearKey(PhoneView v) {
        if (mode == Mode.CALLS && v.mayManage()
            && !v.recent()
                .isEmpty()) {
            if (!confirmBlock) {
                confirmBlock = true;
                return;
            }
            PhoneView.Recent call = v.recent()
                .get(
                    Math.min(
                        index,
                        v.recent()
                            .size() - 1));
            sender.send(PhoneActions.BLOCK_RECENT, buf -> {
                buf.writeLong(call.time());
                buf.writeInt(call.number());
            });
            confirmBlock = false;
            return;
        }
        if (mode == Mode.MENU && index >= 2
            && v.mayManage()
            && !v.blocked()
                .isEmpty()) {
            PhoneView.Blocked one = v.blocked()
                .get(
                    Math.min(
                        index - 2,
                        v.blocked()
                            .size() - 1));
            sender.send(PhoneActions.UNBLOCK, buf -> {
                buf.writeLong(
                    one.player()
                        .getMostSignificantBits());
                buf.writeLong(
                    one.player()
                        .getLeastSignificantBits());
            });
            return;
        }
        normal();
        typed.setLength(0);
    }

    /** Dial: calls what's typed; on a recent call rings it back; in the menu flips the setting shown. */
    private void dialKey() {
        PhoneView v = view.get();
        if (mode == Mode.CALLS) {
            if (!v.recent()
                .isEmpty())
                dial(
                    v.recent()
                        .get(
                            Math.min(
                                index,
                                v.recent()
                                    .size() - 1))
                        .number());
            return;
        }
        if (mode == Mode.MENU) {
            if (!v.mayManage()) return;
            if (index == 0) sender.send(PhoneActions.DND, buf -> buf.writeBoolean(!v.doNotDisturb()));
            if (index == 1) sender.send(PhoneActions.HIDE, buf -> buf.writeBoolean(!v.hidden()));
            return;
        }
        if (typed.length() == 0) return;
        int number = PhoneNumbers.parse(typed.toString());
        if (number < 0) return;
        dial(number);
        typed.setLength(0);
    }

    private void dial(int number) {
        sender.send(PhoneActions.DIAL, buf -> buf.writeInt(number));
        normal();
    }

    private void redial(PhoneView v) {
        for (PhoneView.Recent call : v.recent()) {
            if (call.direction() == PhoneRecord.Direction.OUTGOING) {
                dial(call.number());
                return;
            }
        }
    }

    /** Right-click on a speed-dial key: stores the number on the display, or empties the key when there's none. */
    private void storeSpeedDial(int key) {
        int number = typed.length() == 0 ? 0 : PhoneNumbers.parse(typed.toString());
        if (number < 0) return;
        sender.send(PhoneActions.SPEED_DIAL, buf -> {
            buf.writeByte(key);
            buf.writeInt(number);
        });
        typed.setLength(0);
    }

    /** Calls: the first press shows the latest call, each next one an older one, and after the last the display. */
    private void calls() {
        confirmBlock = false;
        if (mode != Mode.CALLS) {
            mode = Mode.CALLS;
            index = 0;
            sender.send(PhoneActions.MISSED_SEEN, buf -> {});
            return;
        }
        index++;
        if (index >= view.get()
            .recent()
            .size()) normal();
    }

    /** Menu: Do not disturb, Unlisted, then each blocked player, then back to the display. */
    private void menu() {
        confirmBlock = false;
        if (mode != Mode.MENU) {
            mode = Mode.MENU;
            index = 0;
            return;
        }
        index++;
        if (index >= 2 + Math.max(
            1,
            view.get()
                .blocked()
                .size()))
            normal();
    }

    private void normal() {
        mode = Mode.NORMAL;
        index = 0;
        confirmBlock = false;
    }

    @Override
    public boolean key(char typedChar, int code) {
        if (typedChar >= '0' && typedChar <= '9') {
            press(typedChar);
            return true;
        }
        if (code == Keyboard.KEY_BACK) {
            press('<');
            return true;
        }
        if (code == Keyboard.KEY_DELETE) {
            press('C');
            return true;
        }
        if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
            dialKey();
            return true;
        }
        return false;
    }

    // endregion
}
