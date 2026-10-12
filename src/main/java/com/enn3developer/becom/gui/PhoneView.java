package com.enn3developer.becom.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.PacketBuffer;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.phone.PhoneRecord;

/**
 * Everything one player's phone screen shows (DESIGN.md 11.2), built on the server for that player and phone, and
 * sent whole whenever it changes. The client never decides anything from it.
 */
public record PhoneView(int number, String owner, boolean mayManage, State state, String otherName, int otherNumber,
    SignalLevel hearing, boolean doNotDisturb, boolean hidden, List<Contact> contacts, List<Recent> recent,
    List<Blocked> blocked) {

    // Caps on each list, so the view stays well inside one packet: about 11 KB of contacts at most, 5 KB of blocked
    // players, and the whole history.
    public static final int MAX_CONTACTS = 512;
    public static final int MAX_RECENT = PhoneRecord.HISTORY_SIZE;
    public static final int MAX_BLOCKED = PhoneRecord.MAX_BLOCKED;

    public static final PhoneView EMPTY = new PhoneView(
        -1,
        "",
        false,
        State.IDLE,
        "",
        -1,
        SignalLevel.NONE,
        false,
        false,
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList());

    /** Ordinals go on the wire, so constants are only ever appended. */
    public enum State {

        IDLE,
        OFF_HOOK,
        RINGING_OUT,
        RINGING_IN,
        CONNECTED,
        IN_USE;

        private static final State[] VALUES = values();

        static State byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : IDLE;
        }
    }

    public record Contact(int number, String name) {}

    public record Recent(int number, String name, PhoneRecord.Direction direction) {}

    public record Blocked(UUID player, String name) {}

    public void write(PacketBuffer buf) {
        buf.writeInt(number);
        writeString(buf, owner);
        buf.writeBoolean(mayManage);
        buf.writeByte(state.ordinal());
        writeString(buf, otherName);
        buf.writeInt(otherNumber);
        buf.writeByte(hearing.ordinal());
        buf.writeBoolean(doNotDisturb);
        buf.writeBoolean(hidden);
        List<Contact> c = cap(contacts, MAX_CONTACTS);
        buf.writeVarIntToBuffer(c.size());
        for (Contact contact : c) {
            buf.writeInt(contact.number());
            writeString(buf, contact.name());
        }
        List<Recent> r = cap(recent, MAX_RECENT);
        buf.writeVarIntToBuffer(r.size());
        for (Recent entry : r) {
            buf.writeInt(entry.number());
            writeString(buf, entry.name());
            buf.writeByte(
                entry.direction()
                    .ordinal());
        }
        List<Blocked> b = cap(blocked, MAX_BLOCKED);
        buf.writeVarIntToBuffer(b.size());
        for (Blocked entry : b) {
            buf.writeLong(
                entry.player()
                    .getMostSignificantBits());
            buf.writeLong(
                entry.player()
                    .getLeastSignificantBits());
            writeString(buf, entry.name());
        }
    }

    public static PhoneView read(PacketBuffer buf) {
        int number = buf.readInt();
        String owner = readString(buf);
        boolean mayManage = buf.readBoolean();
        State state = State.byOrdinal(buf.readByte());
        String otherName = readString(buf);
        int otherNumber = buf.readInt();
        SignalLevel hearing = SignalLevel.byOrdinal(buf.readByte());
        boolean dnd = buf.readBoolean();
        boolean hidden = buf.readBoolean();
        int count = Math.min(buf.readVarIntFromBuffer(), MAX_CONTACTS);
        List<Contact> contacts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) contacts.add(new Contact(buf.readInt(), readString(buf)));
        count = Math.min(buf.readVarIntFromBuffer(), MAX_RECENT);
        List<Recent> recent = new ArrayList<>(count);
        PhoneRecord.Direction[] directions = PhoneRecord.Direction.values();
        for (int i = 0; i < count; i++) {
            int n = buf.readInt();
            String name = readString(buf);
            int dir = buf.readByte();
            recent.add(new Recent(n, name, directions[Math.floorMod(dir, directions.length)]));
        }
        count = Math.min(buf.readVarIntFromBuffer(), MAX_BLOCKED);
        List<Blocked> blocked = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            blocked.add(new Blocked(new UUID(buf.readLong(), buf.readLong()), readString(buf)));
        }
        return new PhoneView(
            number,
            owner,
            mayManage,
            state,
            otherName,
            otherNumber,
            hearing,
            dnd,
            hidden,
            contacts,
            recent,
            blocked);
    }

    private static <T> List<T> cap(List<T> list, int max) {
        return list.size() > max ? list.subList(0, max) : list;
    }

    // Player names are at most 16 characters; anything longer in a view is a bug, cut rather than trusted.
    private static final int MAX_STRING = 64;

    private static void writeString(PacketBuffer buf, String s) {
        byte[] bytes = (s.length() > MAX_STRING ? s.substring(0, MAX_STRING) : s)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeVarIntToBuffer(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(PacketBuffer buf) {
        int length = Math.min(buf.readVarIntFromBuffer(), MAX_STRING * 4);
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
