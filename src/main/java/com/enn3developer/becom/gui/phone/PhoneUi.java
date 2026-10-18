package com.enn3developer.becom.gui.phone;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.gui.AppsView;
import com.enn3developer.becom.gui.PhoneView;
import com.enn3developer.becom.gui.paint.Canvas;
import com.enn3developer.becom.gui.paint.CanvasScreen;
import com.enn3developer.becom.gui.paint.Glyphs;
import com.enn3developer.becom.gui.paint.Paint;
import com.enn3developer.becom.logic.PhoneNumbers;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.packet.AnalyzerPacket;

/**
 * A carried phone, painted from the design boards (Main, Phone app, Incoming call, In a call, Contacts, Settings,
 * Battery, the app and store boards, ME Phone): its body, a screen with a status bar, and the Back and Home buttons
 * under it. The screen shows one app at a time; an incoming call, a call and an offer from a computer take it over.
 * It asks the server for everything through {@code send}; the server checks each ask.
 */
public final class PhoneUi extends CanvasScreen {

    // The board's phone body, and its screen inside the bezel: the body's 2-pixel edge and its padding of 14 on top
    // and 12 on the sides, the speaker 6 tall and 10 over the screen, and the screen's own 2-pixel black frame.
    static final float WIDTH = 304;
    static final float HEIGHT = 540;
    static final float SX = 16;
    static final float SY = 34;
    static final float SW = 272;
    static final float SH = 460;
    static final float STATUS = 22;
    static final float BAR = 32;
    static final int LUA_CHIP = 0x55ffaa;

    /** The pages the screen shows; the store, app and settings pages carry which app in {@link #app}. */
    enum Page {
        HOME,
        DIALER,
        CONTACTS,
        RECENT,
        SIGNAL,
        SETTINGS,
        BLOCKED,
        BATTERY,
        VIDEO_WINDOW,
        INSTALLED,
        APP_SETTINGS,
        STORE,
        STORE_APP,
        APP,
        CALL
    }

    /** One way the screen asks the server for something: an action's name and what it writes. */
    public interface Sender {

        void send(String action, Consumer<PacketBuffer> write);
    }

    final Supplier<PhoneView> view;
    // What a Signal Analyzer would read on the phone's band where it is, for the Signal app.
    final Supplier<AnalyzerPacket.Reading> signal;
    final Sender sender;
    private final Deque<Page> back = new ArrayDeque<>();
    Page page = Page.HOME;
    // Which app the app, settings and store pages are about.
    String app = "";
    // The number typed on the keypad.
    final StringBuilder typed = new StringBuilder();
    // The Contacts page is choosing someone to add to the call.
    boolean adding;
    final PhoneLists lists = new PhoneLists(this);
    final PhoneSettingsPages settings = new PhoneSettingsPages(this);
    final PhoneAppPages apps = new PhoneAppPages(this);
    // The video call's tiles, made once.
    private com.cleanroommc.modularui.api.drawable.IDrawable videoTiles;
    // The screen opened on the call while one was going; leaving it once keeps it closed.
    private boolean callShown;

    // A board pixel is three quarters of a GUI pixel, so the board's 13-pixel text is Minecraft's font at full size.
    private static final float SCALE = 0.75f;

    /** The scale the phone is drawn at on a screen {@code guiHeight} GUI pixels tall: smaller when it wouldn't fit. */
    public static float scaleFor(int guiHeight) {
        if (guiHeight <= 0) return SCALE;
        return Math.min(SCALE, (guiHeight - 8) / HEIGHT);
    }

    public PhoneUi(Supplier<PhoneView> view, Supplier<AnalyzerPacket.Reading> signal, Sender sender) {
        this.view = view;
        this.signal = signal;
        this.sender = sender;
    }

    @Override
    public float width() {
        return WIDTH;
    }

    @Override
    public float height() {
        return HEIGHT;
    }

    static String t(String key, Object... args) {
        return args.length == 0 ? StatCollector.translateToLocal(key)
            : StatCollector.translateToLocalFormatted(key, args);
    }

    // region Navigation

    void open(Page next) {
        if (next == page) return;
        back.push(page);
        page = next;
        lists.reset();
        if (next == Page.RECENT) sender.send(PhoneActions.MISSED_SEEN, buf -> {});
    }

    void goBack() {
        if (page == Page.APP) sender.send(PhoneActions.APP_CLOSE, buf -> {});
        if (lists.menuOpen()) {
            lists.closeMenu();
            return;
        }
        page = back.isEmpty() ? Page.HOME : back.pop();
        if (page == Page.CONTACTS && adding && !inCall()) adding = false;
        lists.reset();
    }

    void goHome() {
        if (page == Page.APP) sender.send(PhoneActions.APP_CLOSE, buf -> {});
        back.clear();
        page = Page.HOME;
        adding = false;
        lists.reset();
    }

    /** For the client self-test's gallery: the screen on a page by its name, as if opened from home. */
    public void showForTest(String name, String app) {
        back.clear();
        page = Page.valueOf(name);
        this.app = app;
        lists.reset();
        callShown = inCall();
    }

    /** For the gallery: the dialer with a number half typed. */
    public void typeForTest(String digits) {
        typed.setLength(0);
        typed.append(digits);
    }

    boolean inCall() {
        PhoneView.State state = view.get()
            .state();
        return state == PhoneView.State.CONNECTED || state == PhoneView.State.RINGING_OUT;
    }

    // endregion

    // region Painting

    @Override
    protected void paint(Canvas c) {
        PhoneView v = view.get();
        // A call opens the call screen once; Home leaves it for the corner window.
        if (inCall() && !callShown) {
            callShown = true;
            if (page != Page.CALL) open(Page.CALL);
        } else if (!inCall()) {
            callShown = false;
            if (page == Page.CALL) goHome();
            adding = false;
        }
        paintBody(c);
        c.clip(SX, SY, SW, SH);
        if (!v.on()) {
            paintOff(c);
        } else if (v.state() == PhoneView.State.RINGING_IN) {
            paintIncoming(c, v);
        } else if (v.apps()
            .offer()
            .any()) {
                apps.paintOffer(c, v);
            } else {
                switch (page) {
                    case HOME -> paintHome(c, v);
                    case DIALER -> paintDialer(c, v);
                    case CALL -> paintCall(c, v);
                    case CONTACTS -> lists.paintContacts(c, v);
                    case RECENT -> lists.paintRecent(c, v);
                    case BLOCKED -> lists.paintBlocked(c, v);
                    case SIGNAL -> settings.paintSignal(c, v);
                    case SETTINGS -> settings.paintSettings(c, v);
                    case BATTERY -> settings.paintBattery(c, v);
                    case VIDEO_WINDOW -> settings.paintVideoWindow(c, v);
                    case INSTALLED -> apps.paintInstalled(c, v);
                    case APP_SETTINGS -> apps.paintAppSettings(c, v);
                    case STORE -> apps.paintStore(c, v);
                    case STORE_APP -> apps.paintStoreApp(c, v);
                    case APP -> apps.paintApp(c, v);
                }
            }
        c.unclip();
        paintButtons(c);
    }

    /** The body: a dark slab with a black edge and a lighter ring inside it, the speaker, and the screen's frame. */
    private void paintBody(Canvas c) {
        c.rect(0, 0, WIDTH, HEIGHT, Paint.BLACK);
        c.rect(2, 2, WIDTH - 4, HEIGHT - 4, 0x3a3d42);
        c.rect(4, 4, WIDTH - 8, HEIGHT - 8, Paint.BODY);
        c.rect(122, 16, 60, 6, 0x0a0b0c);
        c.rect(SX - 2, SY - 2, SW + 4, SH + 4, Paint.BLACK);
        c.rect(SX, SY, SW, SH, Paint.SCREEN);
    }

    /** The Back and Home buttons under the screen. */
    private void paintButtons(Canvas c) {
        float y = SY + SH + 2 + 10;
        float backX = 92;
        float homeX = 172;
        Paint.box(c, backX, y, 40, 20, 2, hover(backX, y, 40, 20) ? 0x3a3d42 : 0x2a2d31, 0x4a4d52, 0x0a0b0c);
        Paint.glyph(c, backX + 14, y + 4, 2, Glyphs.BACK, 0x9a9a9a, 0x2a2d31);
        hit(backX, y, 40, 20, this::goBack);
        Paint.box(c, homeX, y, 40, 20, 2, hover(homeX, y, 40, 20) ? 0x3a3d42 : 0x2a2d31, 0x4a4d52, 0x0a0b0c);
        Paint.border(c, homeX + 13, y + 3, 14, 14, 2, 0x9a9a9a);
        hit(homeX, y, 40, 20, this::goHome);
    }

    /** A switched-off phone: a dark screen, and the way to switch it on. */
    private void paintOff(Canvas c) {
        c.rect(SX, SY, SW, SH, 0x050607);
        Paint.centered(c, t("becom.ui.phone.off"), SX + SW / 2, SY + SH / 2 - 40, 14, Paint.DARK);
        float w = 120;
        float x = SX + (SW - w) / 2;
        float y = SY + SH / 2 - 10;
        Paint.button(c, x, y, w, 30, Paint.Style.PLAIN, t("becom.ui.phone.switch_on"), 14, hover(x, y, w, 30));
        hit(x, y, w, 30, () -> sender.send(PhoneActions.POWER, buf -> buf.writeBoolean(true)));
    }

    /**
     * The status bar: the time, or "In a call" in green during one; on the right an ME chip in an ME network's range,
     * the signal bars and the battery.
     */
    void paintStatus(Canvas c, PhoneView v) {
        c.rect(SX, SY, SW, STATUS, Paint.STATUS);
        // The board's 13-pixel text in a 16-pixel line in the middle of the bar: its capitals start 7 down.
        float ty = SY + 7;
        if (inCall() && v.state() == PhoneView.State.CONNECTED) {
            c.text(t("becom.ui.phone.in_call"), SX + 8, ty, 13, Paint.GREEN);
        } else {
            c.text(clock(), SX + 8, ty, 13, Paint.LIGHT);
        }
        float right = SX + SW - 8;
        float batteryX = right - 21;
        Paint.battery(
            c,
            batteryX,
            SY + 6.5f,
            v.battery()
                .percent(),
            Paint.LIGHT);
        float barsX = batteryX - 6 - 18;
        Paint.bars(c, barsX, SY + 5, v.service());
        if (v.phone()
            .meInRange()) {
            // The board's chip, 20 by 13 around "ME".
            float x = barsX - 6 - 20;
            c.rect(x, SY + 5, 20, 13, Paint.CYAN);
            Paint.centered(c, "ME", x + 10, SY + 5 + 3, 11, Paint.SCREEN);
        }
    }

    /** A coloured app bar under the status bar, its title with a dark shadow; returns where the page starts. */
    float paintBar(Canvas c, String title, int color, int shadow) {
        float y = SY + STATUS;
        c.rect(SX, y, SW, BAR, color);
        // The board's 16-pixel title has its capitals 12 under the bar's top.
        Paint.shadowed(c, title, SX + 8, y + 12, 16, Paint.WHITE, shadow);
        return y + BAR;
    }

    /** Minecraft's time of day as the phone shows it, 24-hour. */
    static String clock() {
        long time = BeCom.proxy.dayTime();
        long ofDay = Math.floorMod(time + 6000, 24000);
        return String.format("%d:%02d", ofDay / 1000, ofDay % 1000 * 60 / 1000);
    }

    static long day() {
        return BeCom.proxy.dayTime() / 24000 + 1;
    }

    // endregion

    // region Home

    /** A home screen app: what it's called, its tile's colour, its icon, and what opening it does. */
    private record Tile(String name, int color, int[][] glyph, String luaIcon, String badge, boolean redBadge,
        Runnable open, Runnable settings) {}

    private void paintHome(Canvas c, PhoneView v) {
        paintStatus(c, v);
        // The board's block, 18 under the status bar, its lines 2 apart: the clock's 40-pixel line, the day's 17, the
        // signal's 16, and 14 for an ME network out of range. Each text goes where the board's font puts its capitals
        // in its line, as Minecraft's start at the top of theirs.
        float x = SX + 14;
        float line = SY + STATUS + 18;
        c.text(clock(), x, line + 8, 40, Paint.WHITE);
        line += 40 + 2;
        boolean me = v.phone()
            .kind() == PhoneKind.ME_PHONE.ordinal();
        c.text(
            t(me ? "becom.ui.phone.day_me" : "becom.ui.phone.day", day(), PhoneNumbers.format(v.number())),
            x,
            line + 5,
            14,
            Paint.GREY);
        line += 17 + 2;
        c.text(serviceLine(v), x, line + 4, 13, Paint.level(v.service()));
        line += 16;
        String name = v.phone()
            .meName();
        if (me && !name.isEmpty()
            && !v.phone()
                .meInRange()) {
            line += 2;
            c.text(t("becom.ui.phone.me_out_of_range", name), x, line + 3, 12, Paint.DARK);
            line += 14;
        }
        // The grid: the block's 10 of padding and its own 14 under the block, four columns, a row as tall as its
        // longest name and 16 between rows.
        float rowY = line + 10 + 14;
        List<Tile> tiles = tiles(v);
        float column = (SW - 20) / 4;
        for (int first = 0; first < tiles.size(); first += 4) {
            int lines = 1;
            for (int i = first; i < Math.min(tiles.size(), first + 4); i++) {
                // In the middle of its column, on a whole pixel as the board's are.
                float tx = Math.round(SX + 10 + (i % 4) * column + (column - 52) / 2);
                lines = Math.max(lines, paintTile(c, tiles.get(i), tx, rowY, column));
            }
            rowY += 52 + 4 + lines * 16 + 16;
        }
        paintNotices(c, v);
    }

    /** One app: its tile, its badge, and its name in lines of 16 under it. Returns how many lines the name took. */
    private int paintTile(Canvas c, Tile tile, float x, float y, float column) {
        boolean hovered = hover(x, y, 52, 52);
        c.rect(x, y, 52, 52, hovered ? Paint.brighter(tile.color()) : tile.color());
        // The tile's edge: white at 35% on top and left, black at 50% on the bottom and right.
        Paint.bevel(c, x, y, 52, 52, 2, 0x59ffffff, 0x80000000);
        if (tile.luaIcon() == null || !Paint.appIcon(c, x + 14, y + 14, 3, tile.luaIcon())) {
            Paint.glyph(c, x + 14, y + 14, 3, tile.glyph(), Paint.WHITE, tile.color());
        }
        if (tile.badge() != null) {
            // The board's badge hangs 4 past the tile's right and bottom edges.
            float bw = c.width(tile.badge(), 10) + 8;
            float bx = x + 52 + 4 - bw;
            float by = y + 52 + 4 - 14;
            c.rect(bx, by, bw, 14, tile.redBadge() ? 0xff6b55 : 0x6a6a6a);
            c.rect(bx + 1, by + 1, bw - 2, 12, tile.redBadge() ? 0xa8342a : Paint.BLACK);
            Paint.centered(c, tile.badge(), bx + bw / 2, by + 3, 10, tile.redBadge() ? Paint.WHITE : Paint.LIGHT);
        }
        // The name 4 under the tile, in 16-pixel lines with their capitals 4 down.
        List<String> lines = Paint.wrap(c, tile.name(), 13, column);
        float ty = y + 52 + 4 + 4;
        for (String line : lines) {
            Paint.centered(c, line, x + 26, ty, 13, 0xd0d0d0);
            ty += 16;
        }
        hit(x - (column - 52) / 2, y, column, 52 + 4 + lines.size() * 16, tile.open(), tile.settings());
        return lines.size();
    }

    private List<Tile> tiles(PhoneView v) {
        int missed = newMissed(v);
        List<Tile> tiles = new java.util.ArrayList<>();
        tiles.add(
            new Tile(
                t("becom.ui.app.phone"),
                0x2f8a3a,
                Glyphs.PHONE,
                null,
                null,
                false,
                () -> open(Page.DIALER),
                null));
        tiles.add(
            new Tile(
                t("becom.ui.app.contacts"),
                0x2f5f9f,
                Glyphs.CONTACTS,
                null,
                null,
                false,
                () -> open(Page.CONTACTS),
                null));
        tiles.add(
            new Tile(
                t("becom.ui.app.recent"),
                0xb8761c,
                Glyphs.RECENT,
                null,
                missed > 0 ? String.valueOf(missed) : null,
                true,
                () -> open(Page.RECENT),
                null));
        tiles.add(
            new Tile(
                t("becom.ui.app.signal"),
                0x1f7f8f,
                Glyphs.SIGNAL,
                null,
                null,
                false,
                () -> open(Page.SIGNAL),
                null));
        tiles.add(
            new Tile(
                t("becom.ui.app.settings"),
                0x5a5d62,
                Glyphs.SETTINGS,
                null,
                null,
                false,
                () -> open(Page.SETTINGS),
                null));
        if (v.mayManage()) {
            tiles.add(
                new Tile(
                    t("becom.ui.app.store"),
                    0x9f2f5a,
                    Glyphs.STORE,
                    null,
                    null,
                    false,
                    () -> apps.openStore(),
                    null));
        }
        for (AppsView.App a : v.apps()
            .installed()) {
            tiles.add(
                new Tile(
                    a.name(),
                    a.accent() & 0xffffff,
                    Glyphs.APP,
                    a.icon(),
                    "Lua",
                    false,
                    () -> apps.openApp(a.id()),
                    () -> apps.openSettings(a.id())));
        }
        return tiles;
    }

    /** The notification cards at the foot of the home screen: the last app notice, and new missed calls. */
    private void paintNotices(Canvas c, PhoneView v) {
        float bottom = SY + SH - 10;
        PhoneView.Recent missed = latestMissed(v);
        if (missed != null) {
            bottom = paintNotice(
                c,
                bottom,
                0xff5555,
                t("becom.ui.phone.missed_call"),
                missed.name() + ", " + PhoneNumbers.format(missed.number()),
                Paint.GREY,
                () -> open(Page.RECENT)) - 6;
        }
        AppsView.Notice notice = v.apps()
            .notice();
        if (!notice.app()
            .isEmpty()) {
            AppsView.App app = installed(v, notice.app());
            String title = app == null ? notice.title() : app.name();
            String text = app == null ? notice.text() : notice.title();
            paintNotice(
                c,
                bottom,
                app == null ? LUA_CHIP : lighter(app.accent()),
                title,
                text,
                Paint.ORANGE,
                () -> { if (app != null) apps.openApp(app.id()); });
        }
    }

    /** One notification card ending at {@code bottom}; returns its top. */
    private float paintNotice(Canvas c, float bottom, int dot, String title, String text, int textColor,
        Runnable open) {
        float h = 30;
        float x = SX + 10;
        float w = SW - 20;
        float y = bottom - h;
        c.rect(x, y, w, h, 0x2a3038);
        c.rect(x + 1, y + 1, w - 2, h - 2, hover(x, y, w, h) ? 0x1c2229 : 0x161b21);
        c.rect(x + 9, y + (h - 8) / 2, 8, 8, dot);
        // The board's 13-pixel line inside the card's edge and 6 of padding, its capitals 4 down.
        float ty = y + 1 + 6 + 4;
        c.text(title, x + 25, ty, 13, Paint.WHITE);
        float tx = x + 25 + c.width(title, 13) + 8;
        c.text(Paint.fit(c, text, 13, x + w - 8 - tx), tx, ty, 13, textColor);
        hit(x, y, w, h, open);
        return y;
    }

    /** "High, via Antenna T2", "Max, via the ME network "Base"", or "No service". */
    String serviceLine(PhoneView v) {
        if (v.service() == SignalLevel.NONE) return t("becom.ui.phone.no_service");
        String level = t(
            "becom.level." + v.service()
                .name()
                .toLowerCase(java.util.Locale.ROOT));
        return switch (v.via()
            .kind()) {
            case ANTENNA -> t(
                "becom.ui.phone.via_antenna",
                level,
                v.via()
                    .tier());
            case SATELLITES -> t("becom.ui.phone.via_satellites", level);
            case ME_NETWORK -> v.phone()
                .meName()
                .isEmpty() ? t("becom.ui.phone.via_me", level)
                    : t(
                        "becom.ui.phone.via_me_named",
                        level,
                        v.phone()
                            .meName());
            default -> level;
        };
    }

    // endregion

    // region Phone app

    private void paintDialer(Canvas c, PhoneView v) {
        paintStatus(c, v);
        float y = paintBar(c, t("becom.ui.app.phone"), 0x2f8a3a, 0x1a4a20);
        // The board's number, 18 under the bar in a 41-pixel line with its digits 10 down and 4 apart, the cursor a
        // green bar 11 wide on their baseline; the contact it matches 4 under, in an 18-pixel button.
        float line = y + 18;
        String number = typed.toString();
        float spacing = 4;
        float cursor = 11;
        float numberWidth = cursor + spacing;
        for (int i = 0; i < number.length(); i++) numberWidth += c.width(number.substring(i, i + 1), 34) + spacing;
        float nx = SX + (SW - numberWidth) / 2;
        for (int i = 0; i < number.length(); i++) {
            String digit = number.substring(i, i + 1);
            c.text(digit, nx, line + 10, 34, Paint.WHITE);
            nx += c.width(digit, 34) + spacing;
        }
        c.rect(nx, line + 28, cursor, 3, Paint.GREEN);
        PhoneView.Contact match = matching(v);
        if (match != null) {
            String label = match.name() + ", " + PhoneNumbers.format(match.number());
            float w = c.width(label, 13);
            float lx = SX + (SW - w) / 2;
            float ly = line + 41 + 4;
            c.text(label, lx, ly + 1 + 4, 13, hover(lx, ly, w, 18) ? 0xb0ccff : 0x8ab4ff);
            hit(lx, ly, w, 18, () -> {
                typed.setLength(0);
                typed.append(match.number());
            });
        }
        // The keypad, the block's 6 of padding and its own 8 under the button: keys 44 tall and 8 apart, their labels
        // where the board's font puts them, a digit's top 15 under the key's and the smaller DEL's 18.
        float keysY = line + 41 + 4 + 18 + 6 + 8;
        float keyW = (SW - 36 - 16) / 3f;
        Paint.Style plain = Paint.Style.PLAIN;
        String[] keys = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "DEL" };
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].isEmpty()) continue;
            float kx = SX + 18 + (i % 3) * (keyW + 8);
            float ky = keysY + (i / 3) * (44 + 8);
            String key = keys[i];
            boolean del = key.equals("DEL");
            int face = hover(kx, ky, keyW, 44) ? Paint.brighter(plain.face) : plain.face;
            Paint.box(c, kx, ky, keyW, 44, 2, face, plain.light, plain.dark);
            Paint.centered(c, key, kx + keyW / 2, ky + (del ? 18 : 15), del ? 16 : 20, del ? 0xff9a8a : Paint.WHITE);
            hit(kx, ky, keyW, 44, del ? this::deleteDigit : () -> typeDigit(key.charAt(0)));
        }
        float bx = SX + (SW - 120) / 2;
        float by = SY + SH - 14 - 40;
        goButton(c, bx, by, 120, 40, t(adding ? "becom.ui.phone.add" : "becom.ui.phone.call"));
        hit(bx, by, 120, 40, () -> dialTyped(false));
    }

    /**
     * The board's green Call button: a Go button whose label has its shadow in dark green, not black, its 17-pixel
     * capitals 15 under the top.
     */
    private void goButton(Canvas c, float x, float y, float w, float h, String label) {
        Paint.Style go = Paint.Style.GO;
        Paint.box(c, x, y, w, h, 2, hover(x, y, w, h) ? Paint.brighter(go.face) : go.face, go.light, go.dark);
        Paint.shadowed(c, label, x + (w - c.width(label, 17)) / 2, y + 15, 17, Paint.WHITE, 0x1a4a20);
    }

    private PhoneView.Contact matching(PhoneView v) {
        if (typed.length() == 0) return null;
        String prefix = typed.toString();
        for (PhoneView.Contact contact : v.contacts()) {
            if (String.valueOf(contact.number())
                .startsWith(prefix)) return contact;
        }
        return null;
    }

    private void typeDigit(char digit) {
        if (typed.length() < PhoneNumbers.DIGITS) typed.append(digit);
    }

    private void deleteDigit() {
        if (typed.length() > 0) typed.setLength(typed.length() - 1);
    }

    private void dialTyped(boolean video) {
        if (typed.length() == 0) return;
        int number;
        try {
            number = Integer.parseInt(typed.toString());
        } catch (NumberFormatException e) {
            return;
        }
        dial(number, video);
        typed.setLength(0);
    }

    /** Calls {@code number}, or adds it to the call. */
    void dial(int number, boolean video) {
        sender.send(video ? PhoneActions.DIAL_VIDEO : PhoneActions.DIAL, buf -> buf.writeInt(number));
        adding = false;
        open(Page.CALL);
    }

    @Override
    public boolean key(char typed, int code) {
        if (page == Page.DIALER && view.get()
            .on()) {
            if (typed >= '0' && typed <= '9') {
                typeDigit(typed);
                return true;
            }
            if (code == Keyboard.KEY_BACK) {
                deleteDigit();
                return true;
            }
            if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
                dialTyped(false);
                return true;
            }
        }
        return apps.key(typed, code);
    }

    @Override
    public void scroll(int amount) {
        lists.scroll(amount);
        apps.scroll(amount);
    }

    @Override
    protected boolean clickedNothing(int button) {
        if (lists.menuOpen()) {
            lists.closeMenu();
            return true;
        }
        apps.unfocus();
        return false;
    }

    // endregion

    // region Calls

    /** The incoming call, full screen, green: who calls, and Decline and Answer with their keys. */
    private void paintIncoming(Canvas c, PhoneView v) {
        c.rect(SX, SY, SW, SH, 0x0c1a10);
        paintStatus(c, v);
        float cx = SX + SW / 2;
        // The board's column, 34 under the status bar, 8 apart: the 17-pixel line, the face 80 square in a 2-pixel
        // frame with 10 above and below, the name's 34-pixel line and the number's; each text where the board's font
        // puts its capitals in its line.
        float y = SY + STATUS + 34;
        Paint.centered(c, t("becom.ui.phone.incoming"), cx, y + 5, 14, Paint.GREEN);
        y += 17 + 8 + 10;
        Paint.border(c, cx - 42, y, 84, 84, 2, 0x2f8a3a);
        c.face(v.otherName(), cx - 40, y + 2, 80);
        y += 84 + 10 + 8;
        Paint.centered(c, v.otherName(), cx, y + 8, 28, Paint.WHITE);
        y += 34 + 8;
        Paint.centered(c, PhoneNumbers.format(v.otherNumber()), cx, y + 4, 15, Paint.GREY);
        // Decline and Answer 14 above the screen's foot: each square, its name's 17-pixel line and its 16-pixel keys,
        // 6 apart, and a pixel of the button's own padding.
        float by = SY + SH - 14 - 1 - 16 - 6 - 17 - 6 - 64;
        float left = SX + 20 + (SW - 40) / 4 - 32;
        float right = SX + 20 + (SW - 40) * 3 / 4 - 32;
        paintRound(c, left, by, Paint.Style.DANGER, Glyphs.CROSS, 4, 7, t("becom.ui.phone.decline"), "Shift", "N");
        hit(left, by, 64, 64, () -> sender.send(PhoneActions.HANG_UP, buf -> {}));
        paintRound(c, right, by, Paint.Style.GO, Glyphs.PHONE, 3.5f, 8, t("becom.ui.phone.answer"), null, "N");
        hit(right, by, 64, 64, () -> sender.send(PhoneActions.PICK_UP, buf -> {}));
    }

    /** One of the incoming screen's big square buttons, its name under it, and the keys that do the same. */
    private void paintRound(Canvas c, float x, float y, Paint.Style style, int[][] glyph, float cell, int cells,
        String label, String modifier, String key) {
        Paint.box(
            c,
            x,
            y,
            64,
            64,
            2,
            hover(x, y, 64, 64) ? Paint.brighter(style.face) : style.face,
            style.light,
            style.dark);
        float size = cell * cells;
        Paint.glyph(c, x + (64 - size) / 2, y + (64 - size) / 2, cell, glyph, Paint.WHITE, style.face);
        // The name's 17-pixel line 6 under the square, its capitals 5 down; the keys 6 under the line.
        float ly = y + 64 + 6;
        Paint.centered(c, label, x + 32, ly + 5, 14, Paint.LIGHT);
        ly += 17 + 6;
        float capsWidth = (modifier == null ? 0 : c.width(modifier, 11) + 6 + 2) + Math.max(16, c.width(key, 11) + 6);
        float kx = x + 32 - capsWidth / 2;
        if (modifier != null) kx += Paint.keyCap(c, kx, ly, modifier, 11) + 2;
        Paint.keyCap(c, kx, ly, key, 11);
    }

    /**
     * The call: in a video call, the people as tiles; everyone in it with their bars; then Camera, Rear, Add and Hang
     * up, and that Home keeps it going.
     */
    private void paintCall(Canvas c, PhoneView v) {
        paintStatus(c, v);
        float y = SY + STATUS;
        boolean video = v.phone()
            .videoCall()
            && v.video()
                .capable();
        if (video) {
            if (videoTiles == null) videoTiles = BeCom.proxy.videoView(() -> true);
            c.drawable(videoTiles, SX + 4, y + 4, SW - 8, 150);
            y += 158;
        }
        // Everyone in it, in rows 22 tall and 2 apart: their bars, and their name in the middle of the row as the
        // board's font sets it. One still ringing has no bars lit yet, a grey name and "ringing" after it.
        y += 8;
        for (PhoneView.Member member : v.members()) {
            Paint.bars(c, SX + 12, y + 5, member.ringing() ? SignalLevel.NONE : member.level());
            c.text(member.name(), SX + 12 + 18 + 8, y + 8, 14, member.ringing() ? Paint.GREY : Paint.WHITE);
            if (member.ringing()) {
                c.text(
                    t("becom.ui.phone.ringing"),
                    SX + 12 + 18 + 8 + c.width(member.name(), 14) + 8,
                    y + 7,
                    12,
                    Paint.ORANGE);
            }
            y += 22 + 2;
        }
        // At the foot, 8 above its edge, the hint's 13-pixel line; 10 above that the controls, each square with its
        // name's line 4 under it.
        float hintY = SY + SH - 8 - 13;
        Paint.centered(c, t("becom.ui.phone.home_keeps"), SX + SW / 2, hintY + 3, 11, Paint.DARK);
        float controlsY = hintY - 10 - 13 - 4 - 44;
        float column = (SW - 20) / 4f;
        PhoneView.Video cameras = v.video();
        int slot = 0;
        if (video) {
            paintControl(
                c,
                slot++,
                column,
                controlsY,
                Glyphs.CAMERA,
                8,
                cameras.front(),
                true,
                t("becom.ui.phone.camera"),
                () -> sender.send(PhoneActions.FRONT, buf -> buf.writeBoolean(!cameras.front())));
            // Rear is a switch like Camera, as the board draws it: the server takes it in any call, and the camera
            // streams once the call is two people at High or better.
            paintControl(
                c,
                slot++,
                column,
                controlsY,
                Glyphs.REAR,
                8,
                cameras.rear(),
                true,
                t("becom.ui.phone.rear"),
                () -> sender.send(PhoneActions.REAR, buf -> buf.writeBoolean(!cameras.rear())));
        } else {
            slot = 1;
        }
        paintControl(
            c,
            slot++,
            column,
            controlsY,
            Glyphs.ADD,
            8,
            false,
            v.state() == PhoneView.State.CONNECTED,
            t("becom.ui.phone.add"),
            () -> {
                adding = true;
                open(Page.CONTACTS);
            });
        float hx = SX + 10 + slot * column + (column - 44) / 2;
        Paint.Style danger = Paint.Style.DANGER;
        Paint.box(
            c,
            hx,
            controlsY,
            44,
            44,
            2,
            hover(hx, controlsY, 44, 44) ? Paint.brighter(danger.face) : danger.face,
            danger.light,
            danger.dark);
        // The board's cross is 20 square, as the other controls' icons are.
        Paint.glyph(c, hx + 12, controlsY + 12, 20 / 7f, Glyphs.CROSS, Paint.WHITE, danger.face);
        Paint.centered(c, t("becom.ui.phone.hang_up"), hx + 22, controlsY + 44 + 4 + 3, 11, Paint.LIGHT);
        hit(hx, controlsY, 44, 44, () -> sender.send(PhoneActions.HANG_UP, buf -> {}));
    }

    /** One of the call's square controls: light when it's on, dim when it can't be used now. */
    private void paintControl(Canvas c, int slot, float column, float y, int[][] glyph, int cells, boolean on,
        boolean usable, String label, Runnable press) {
        float x = SX + 10 + slot * column + (column - 44) / 2;
        Paint.Style style = on ? Paint.Style.ON : Paint.Style.PLAIN;
        int face = usable && hover(x, y, 44, 44) ? Paint.brighter(style.face) : style.face;
        Paint.box(c, x, y, 44, 44, 2, face, style.light, style.dark);
        float cell = 20f / cells;
        int ink = on ? 0x1c2229 : usable ? Paint.LIGHT : Paint.DARK;
        Paint.glyph(c, x + 12, y + 12, cell, glyph, ink, face);
        // The name's 13-pixel line 4 under the square, its capitals 3 down.
        Paint.centered(c, label, x + 22, y + 44 + 4 + 3, 11, usable ? Paint.LIGHT : Paint.DARK);
        if (usable) hit(x, y, 44, 44, press);
    }

    // endregion

    // region What several pages share

    static int newMissed(PhoneView v) {
        int count = 0;
        for (PhoneView.Recent recent : v.recent()) {
            if (recent.direction() == PhoneRecord.Direction.MISSED && recent.time() > v.phone()
                .missedSeen()) count++;
        }
        return count;
    }

    static PhoneView.Recent latestMissed(PhoneView v) {
        for (PhoneView.Recent recent : v.recent()) {
            if (recent.direction() == PhoneRecord.Direction.MISSED && recent.time() > v.phone()
                .missedSeen()) return recent;
        }
        return null;
    }

    static AppsView.App installed(PhoneView v, String id) {
        for (AppsView.App a : v.apps()
            .installed())
            if (a.id()
                .equals(id)) return a;
        return null;
    }

    /** An app's accent made light enough to read on the dark screen, for its dot and its bar in Battery. */
    static int lighter(int accent) {
        int r = (accent >> 16) & 0xff;
        int g = (accent >> 8) & 0xff;
        int b = accent & 0xff;
        int max = Math.max(r, Math.max(g, b));
        if (max == 0) return 0x55ffaa;
        float k = 255f / max;
        return ((int) (r * k) << 16) | ((int) (g * k) << 8) | (int) (b * k);
    }

    /**
     * A section's heading: small, blue-grey and in capitals, in the boards' 13-pixel line with 8 above and 2 under.
     * Returns where what's under it starts.
     */
    float section(Canvas c, float y, String title) {
        c.text(title.toUpperCase(), SX + 10, y + 8 + 3, 11, Paint.SECTION);
        return y + 8 + 13 + 2;
    }

    /**
     * A row of the Settings board: 30 tall over its line, the title in the middle, or at the top with a grey line under
     * it when {@code sub} isn't null. A grey line that wraps makes the row a line taller. Returns the row's height.
     */
    float rowHeight(Canvas c, String sub) {
        if (sub == null) return 31;
        return 31 + (subLines(c, sub).size() - 1) * 13;
    }

    // A row's grey line wraps once, leaving room for a pill or a chevron on the right as the boards' do.
    private static List<String> subLines(Canvas c, String sub) {
        List<String> lines = Paint.wrap(c, sub, 11, SW - 64);
        if (lines.size() <= 2) return lines;
        return List.of(lines.get(0), Paint.fit(c, String.join(" ", lines.subList(1, lines.size())), 11, SW - 64));
    }

    /** Paints a {@link #rowHeight} row; returns where the next row starts. */
    float row(Canvas c, float y, String title, String sub) {
        float h = rowHeight(c, sub);
        if (sub == null) {
            c.text(title, SX + 10, y + 12, 14, Paint.WHITE);
        } else {
            // The title's 17-pixel line at the top, its capitals 5 down; the grey lines' 13 under it, theirs 3 down.
            c.text(title, SX + 10, y + 5, 14, Paint.WHITE);
            float ty = y + 17 + 3;
            for (String line : subLines(c, sub)) {
                c.text(line, SX + 10, ty, 11, Paint.GREY);
                ty += 13;
            }
        }
        c.rect(SX, y + h - 1, SW, 1, Paint.LINE);
        return y + h;
    }

    /**
     * A row of the Battery and app settings boards: 2 of padding around a title's 17-pixel line and the grey lines'
     * 14 each, 30 at least, over its line. Returns the row's height.
     */
    float paddedRowHeight(Canvas c, String sub) {
        float lines = sub == null ? 17 : 17 + subLines(c, sub).size() * 14;
        return 2 + Math.max(30, lines) + 2 + 1;
    }

    /** Paints a {@link #paddedRowHeight} row; returns where the next row starts. */
    float paddedRow(Canvas c, float y, String title, String sub) {
        float h = paddedRowHeight(c, sub);
        if (sub == null) {
            c.text(title, SX + 10, y + 14, 14, Paint.WHITE);
        } else {
            c.text(title, SX + 10, y + 2 + 5, 14, Paint.WHITE);
            float ty = y + 2 + 17 + 4;
            for (String line : subLines(c, sub)) {
                c.text(line, SX + 10, ty, 11, Paint.GREY);
                ty += 14;
            }
        }
        c.rect(SX, y + h - 1, SW, 1, Paint.LINE);
        return y + h;
    }

    // endregion
}
