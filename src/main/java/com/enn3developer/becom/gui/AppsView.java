package com.enn3developer.becom.gui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.PacketBuffer;

import com.enn3developer.becom.logic.app.AppTree;

/**
 * The apps part of a carried phone's screen: its installed Lua apps and each one's settings, what the open app shows,
 * an install a computer offers, the last notification, and one page of the App Store. Built on the server, drawn on
 * the client, which decides nothing from it.
 *
 * @param tab  the store tab, an AppService.StoreTab ordinal
 * @param page which page of {@code total} results {@code store} is
 */
public record AppsView(List<App> installed, Screen screen, Offer offer, Notice notice, List<StoreApp> store,
    String query, int tab, int page, int total) {

    public static final int MAX_INSTALLED = com.enn3developer.becom.logic.app.AppLimits.MAX_APPS_PER_PHONE;
    /** Store results a page. */
    public static final int STORE_ROWS = 5;

    public static final AppsView NONE = new AppsView(
        List.of(),
        Screen.NONE,
        Offer.NONE,
        Notice.NONE,
        List.of(),
        "",
        0,
        0,
        0);

    /**
     * An installed app and its settings: its icon (64 hex digits or empty) and what it uses (AppManifest.Use bits),
     * where it came from, background, its wakes and battery today, and its storage against what it may keep.
     */
    public record App(String id, String name, String version, String author, String about, int accent, String icon,
        int uses, boolean store, String where, boolean background, int woke, long euToday, int size, long stored,
        long storage) {}

    /** Ordinals go on the wire, so constants are only ever appended. */
    public enum State {

        NONE,
        LOADING,
        RUNNING,
        UNREACHABLE,
        CRASHED;

        private static final State[] VALUES = values();

        static State byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NONE;
        }
    }

    /**
     * The open app: its state, why it crashed, its widgets, and when it last got data, in the world's total time (0 for
     * never).
     */
    public record Screen(String id, State state, String error, List<AppTree.Widget> widgets, long data) {

        public static final Screen NONE = new Screen("", State.NONE, "", List.of(), 0);
    }

    /** An app a computer offers this phone, to install or to publish as its holder, for them to accept or decline. */
    public record Offer(long id, boolean publish, String name, String version, String author, String where, int size,
        int uses) {

        public static final Offer NONE = new Offer(0, false, "", "", "", "", 0, 0);

        public boolean any() {
            return !name.isEmpty();
        }
    }

    public record Notice(String app, String title, String text) {

        public static final Notice NONE = new Notice("", "", "");
    }

    /**
     * An app in the store: {@code installed} is the version on this phone, or empty; {@code days} since it was last
     * published; {@code team} when only its author's team sees it; {@code reported} when this phone's owner reported
     * it.
     */
    public record StoreApp(String id, String name, String version, String author, String about, int installs, int size,
        String installed, String icon, int uses, int days, boolean team, boolean reported) {}

    public void write(PacketBuffer buf) {
        List<App> apps = cap(installed, MAX_INSTALLED);
        buf.writeByte(apps.size());
        for (App a : apps) {
            str(buf, a.id());
            str(buf, a.name());
            str(buf, a.version());
            str(buf, a.author());
            str(buf, a.about());
            buf.writeInt(a.accent());
            str(buf, a.icon());
            buf.writeByte(a.uses());
            buf.writeBoolean(a.store());
            str(buf, a.where());
            buf.writeBoolean(a.background());
            buf.writeInt(a.woke());
            buf.writeLong(a.euToday());
            buf.writeInt(a.size());
            buf.writeLong(a.stored());
            buf.writeLong(a.storage());
        }
        str(buf, screen.id());
        buf.writeByte(
            screen.state()
                .ordinal());
        str(buf, screen.error());
        List<AppTree.Widget> widgets = cap(screen.widgets(), AppTree.MAX_WIDGETS);
        buf.writeByte(widgets.size());
        for (AppTree.Widget w : widgets) {
            buf.writeByte(
                w.kind()
                    .ordinal());
            str(buf, w.id());
            str(buf, w.label());
            str(buf, w.value());
            buf.writeFloat((float) w.fraction());
            buf.writeByte(
                w.level()
                    .ordinal());
            buf.writeByte(
                w.style()
                    .ordinal());
            str(buf, w.extra());
        }
        buf.writeLong(screen.data());
        buf.writeLong(offer.id());
        buf.writeBoolean(offer.publish());
        str(buf, offer.name());
        str(buf, offer.version());
        str(buf, offer.author());
        str(buf, offer.where());
        buf.writeInt(offer.size());
        buf.writeByte(offer.uses());
        str(buf, notice.app());
        str(buf, notice.title());
        str(buf, notice.text());
        List<StoreApp> s = cap(store, STORE_ROWS);
        buf.writeByte(s.size());
        for (StoreApp a : s) {
            str(buf, a.id());
            str(buf, a.name());
            str(buf, a.version());
            str(buf, a.author());
            str(buf, a.about());
            buf.writeInt(a.installs());
            buf.writeInt(a.size());
            str(buf, a.installed());
            str(buf, a.icon());
            buf.writeByte(a.uses());
            buf.writeShort(Math.min(a.days(), Short.MAX_VALUE));
            buf.writeBoolean(a.team());
            buf.writeBoolean(a.reported());
        }
        str(buf, query);
        buf.writeByte(tab);
        buf.writeShort(page);
        buf.writeShort(total);
    }

    public static AppsView read(PacketBuffer buf) {
        int count = Math.min(buf.readUnsignedByte(), MAX_INSTALLED);
        List<App> installed = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            installed.add(
                new App(
                    str(buf),
                    str(buf),
                    str(buf),
                    str(buf),
                    str(buf),
                    buf.readInt(),
                    str(buf),
                    buf.readUnsignedByte(),
                    buf.readBoolean(),
                    str(buf),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readLong(),
                    buf.readInt(),
                    buf.readLong(),
                    buf.readLong()));
        }
        String id = str(buf);
        State state = State.byOrdinal(buf.readByte());
        String error = str(buf);
        count = Math.min(buf.readUnsignedByte(), AppTree.MAX_WIDGETS);
        List<AppTree.Widget> widgets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            widgets.add(
                new AppTree.Widget(
                    AppTree.Kind.byOrdinal(buf.readByte()),
                    str(buf),
                    str(buf),
                    str(buf),
                    buf.readFloat(),
                    AppTree.Level.byOrdinal(buf.readByte()),
                    AppTree.Style.byOrdinal(buf.readByte()),
                    str(buf)));
        }
        long data = buf.readLong();
        Offer offer = new Offer(
            buf.readLong(),
            buf.readBoolean(),
            str(buf),
            str(buf),
            str(buf),
            str(buf),
            buf.readInt(),
            buf.readUnsignedByte());
        Notice notice = new Notice(str(buf), str(buf), str(buf));
        count = Math.min(buf.readUnsignedByte(), STORE_ROWS);
        List<StoreApp> store = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            store.add(
                new StoreApp(
                    str(buf),
                    str(buf),
                    str(buf),
                    str(buf),
                    str(buf),
                    buf.readInt(),
                    buf.readInt(),
                    str(buf),
                    str(buf),
                    buf.readUnsignedByte(),
                    buf.readShort(),
                    buf.readBoolean(),
                    buf.readBoolean()));
        }
        String query = str(buf);
        return new AppsView(
            installed,
            new Screen(id, state, error, widgets, data),
            offer,
            notice,
            store,
            query,
            buf.readUnsignedByte(),
            buf.readShort(),
            buf.readShort());
    }

    private static <T> List<T> cap(List<T> list, int max) {
        return list.size() > max ? list.subList(0, max) : list;
    }

    // An app's text is capped where it's made; anything longer here is a bug, cut rather than trusted.
    private static final int MAX_STRING = 256;

    private static void str(PacketBuffer buf, String s) {
        byte[] bytes = (s.length() > MAX_STRING ? s.substring(0, MAX_STRING) : s).getBytes(StandardCharsets.UTF_8);
        buf.writeVarIntToBuffer(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String str(PacketBuffer buf) {
        int length = Math.min(buf.readVarIntFromBuffer(), MAX_STRING * 4);
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
