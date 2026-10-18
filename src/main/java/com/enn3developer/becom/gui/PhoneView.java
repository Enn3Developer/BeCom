package com.enn3developer.becom.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.PacketBuffer;

import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.phone.PhoneRecord;

/**
 * Everything one player's phone screen shows, built on the server for that player and phone, and
 * sent whole whenever it changes. The client never decides anything from it.
 */
public record PhoneView(int number, String owner, boolean mayManage, State state, String otherName, int otherNumber,
    SignalLevel hearing, boolean doNotDisturb, boolean hidden, List<Contact> contacts, List<Recent> recent,
    List<Blocked> blocked, boolean carried, boolean on, boolean silent, SignalLevel service, List<Member> members,
    boolean offline, Video video, Battery battery, Via via, AppsView apps, Phone phone) {

    // Caps on each list, so the view stays well inside one packet: about 11 KB of contacts at most, 5 KB of blocked
    // players, and the whole history.
    public static final int MAX_CONTACTS = 512;
    public static final int MAX_RECENT = PhoneRecord.HISTORY_SIZE;
    public static final int MAX_BLOCKED = PhoneRecord.MAX_BLOCKED;
    public static final int MAX_MEMBERS = Tunables.MAX_GROUP_CALL;

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
        Collections.emptyList(),
        false,
        false,
        false,
        SignalLevel.NONE,
        Collections.emptyList(),
        false,
        Video.NONE,
        Battery.NONE,
        Via.NONE,
        AppsView.NONE,
        Phone.NONE);

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

    /** A contact: its number, its owner's name, and its kind's ordinal (PhoneKind), a Telephone shown as one. */
    public record Contact(int number, String name, int kind) {}

    /**
     * The phone itself: its kind's ordinal (PhoneKind); an ME Phone's network name, empty for none, and whether it's in
     * its wireless range; when its owner last saw its missed calls, by the history's clock; and whether its call is a
     * video call; a desk phone's speed-dial numbers (0 for an empty key), how many players stand in a Videophone's
     * room, and whether a Videophone's camera is on.
     */
    public record Phone(int kind, String meName, boolean meInRange, long missedSeen, boolean videoCall,
        List<Integer> speedDial, int here, boolean camera) {

        public static final Phone NONE = new Phone(0, "", false, 0, false, List.of(), 0, true);
    }

    /** A recent call; {@code time} names the row when the owner blocks from it. */
    public record Recent(long time, int number, String name, PhoneRecord.Direction direction) {}

    public record Blocked(UUID player, String name) {}

    /** Another phone in the call: who is on it, the level this phone hears it at, or still ringing. */
    public record Member(int number, String name, SignalLevel level, boolean ringing) {}

    /**
     * Video on this phone: whether it has a camera, this player's switches, whether a rear
     * camera means anything in this call, and whether they see anyone right now.
     */
    public record Video(boolean capable, boolean front, boolean rear, boolean rearPossible, boolean active) {

        public static final Video NONE = new Video(false, true, false, false, false);
    }

    /**
     * A carried phone's battery (the Battery settings page): its charge, how long that lasts on standby and in calls
     * (-1 for ever), whether it's charging, each use's share of today's drain by {@link
     * com.enn3developer.becom.logic.phone.BatteryUse.Use} ordinal, the saver and warning switches, and each app's
     * share of today's drain, most first, which the apps' share stands for on the page.
     */
    public record Battery(long charge, long max, long standbyMinutes, long callMinutes, boolean charging,
        List<Integer> shares, boolean saver, boolean saving, boolean warns, List<AppShare> apps) {

        public static final Battery NONE = new Battery(0, 0, -1, -1, false, List.of(), false, false, true, List.of());

        public int percent() {
            return max <= 0 ? 0 : (int) Math.min(100, charge * 100 / max);
        }
    }

    /** An app's share of a phone's drain today, in whole percent. */
    public record AppShare(String name, int share) {}

    /** What a carried phone's service comes through: the best of its ways in, and an antenna's tier. */
    public record Via(Kind kind, int tier) {

        public static final Via NONE = new Via(Kind.NONE, 0);

        /** Ordinals go on the wire, so constants are only ever appended. */
        public enum Kind {

            NONE,
            ANTENNA,
            SATELLITES,
            ME_NETWORK;

            private static final Kind[] VALUES = values();

            static Kind byOrdinal(int ordinal) {
                return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NONE;
            }
        }
    }

    // A battery's use shares go on the wire as bytes, at most this many.
    private static final int MAX_SHARES = 16;

    private static List<AppShare> readAppShares(PacketBuffer buf) {
        int count = Math.min(buf.readUnsignedByte(), MAX_SHARES);
        List<AppShare> apps = new ArrayList<>(count);
        for (int i = 0; i < count; i++) apps.add(new AppShare(readString(buf), buf.readUnsignedByte()));
        return apps;
    }

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
            buf.writeByte(contact.kind());
        }
        List<Recent> r = cap(recent, MAX_RECENT);
        buf.writeVarIntToBuffer(r.size());
        for (Recent entry : r) {
            buf.writeLong(entry.time());
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
        buf.writeBoolean(carried);
        buf.writeBoolean(on);
        buf.writeBoolean(silent);
        buf.writeByte(service.ordinal());
        List<Member> m = cap(members, MAX_MEMBERS);
        buf.writeVarIntToBuffer(m.size());
        for (Member member : m) {
            buf.writeInt(member.number());
            writeString(buf, member.name());
            buf.writeByte(
                member.level()
                    .ordinal());
            buf.writeBoolean(member.ringing());
        }
        buf.writeBoolean(offline);
        buf.writeBoolean(video.capable());
        buf.writeBoolean(video.front());
        buf.writeBoolean(video.rear());
        buf.writeBoolean(video.rearPossible());
        buf.writeBoolean(video.active());
        buf.writeLong(battery.charge());
        buf.writeLong(battery.max());
        buf.writeLong(battery.standbyMinutes());
        buf.writeLong(battery.callMinutes());
        buf.writeBoolean(battery.charging());
        List<Integer> shares = cap(battery.shares(), MAX_SHARES);
        buf.writeByte(shares.size());
        for (int share : shares) buf.writeByte(share);
        buf.writeBoolean(battery.saver());
        buf.writeBoolean(battery.saving());
        buf.writeBoolean(battery.warns());
        List<AppShare> appShares = cap(battery.apps(), MAX_SHARES);
        buf.writeByte(appShares.size());
        for (AppShare app : appShares) {
            writeString(buf, app.name());
            buf.writeByte(app.share());
        }
        buf.writeByte(
            via.kind()
                .ordinal());
        buf.writeByte(via.tier());
        apps.write(buf);
        buf.writeByte(phone.kind());
        writeString(buf, phone.meName());
        buf.writeBoolean(phone.meInRange());
        buf.writeLong(phone.missedSeen());
        buf.writeBoolean(phone.videoCall());
        List<Integer> keys = phone.speedDial()
            .size() > PhoneRecord.SPEED_DIAL_KEYS ? phone.speedDial()
                .subList(0, PhoneRecord.SPEED_DIAL_KEYS) : phone.speedDial();
        buf.writeByte(keys.size());
        for (int key : keys) buf.writeInt(key);
        buf.writeByte(Math.min(phone.here(), 255));
        buf.writeBoolean(phone.camera());
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
        for (int i = 0; i < count; i++) contacts.add(new Contact(buf.readInt(), readString(buf), buf.readByte()));
        count = Math.min(buf.readVarIntFromBuffer(), MAX_RECENT);
        List<Recent> recent = new ArrayList<>(count);
        PhoneRecord.Direction[] directions = PhoneRecord.Direction.values();
        for (int i = 0; i < count; i++) {
            long time = buf.readLong();
            int n = buf.readInt();
            String name = readString(buf);
            int dir = buf.readByte();
            recent.add(new Recent(time, n, name, directions[Math.floorMod(dir, directions.length)]));
        }
        count = Math.min(buf.readVarIntFromBuffer(), MAX_BLOCKED);
        List<Blocked> blocked = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            blocked.add(new Blocked(new UUID(buf.readLong(), buf.readLong()), readString(buf)));
        }
        boolean carried = buf.readBoolean();
        boolean on = buf.readBoolean();
        boolean silent = buf.readBoolean();
        SignalLevel service = SignalLevel.byOrdinal(buf.readByte());
        count = Math.min(buf.readVarIntFromBuffer(), MAX_MEMBERS);
        List<Member> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            members.add(
                new Member(buf.readInt(), readString(buf), SignalLevel.byOrdinal(buf.readByte()), buf.readBoolean()));
        }
        boolean offline = buf.readBoolean();
        Video video = new Video(
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean());
        long charge = buf.readLong();
        long max = buf.readLong();
        long standby = buf.readLong();
        long inCalls = buf.readLong();
        boolean charging = buf.readBoolean();
        count = Math.min(buf.readUnsignedByte(), MAX_SHARES);
        List<Integer> shares = new ArrayList<>(count);
        for (int i = 0; i < count; i++) shares.add((int) buf.readUnsignedByte());
        Battery battery = new Battery(
            charge,
            max,
            standby,
            inCalls,
            charging,
            shares,
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            readAppShares(buf));
        Via via = new Via(Via.Kind.byOrdinal(buf.readByte()), buf.readByte());
        AppsView apps = AppsView.read(buf);
        int kind = buf.readByte();
        String meName = readString(buf);
        boolean meInRange = buf.readBoolean();
        long missedSeen = buf.readLong();
        boolean videoCall = buf.readBoolean();
        int keyCount = Math.min(buf.readUnsignedByte(), PhoneRecord.SPEED_DIAL_KEYS);
        List<Integer> speedDial = new ArrayList<>(keyCount);
        for (int i = 0; i < keyCount; i++) speedDial.add(buf.readInt());
        Phone phone = new Phone(
            kind,
            meName,
            meInRange,
            missedSeen,
            videoCall,
            speedDial,
            buf.readUnsignedByte(),
            buf.readBoolean());
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
            blocked,
            carried,
            on,
            silent,
            service,
            members,
            offline,
            video,
            battery,
            via,
            apps,
            phone);
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
