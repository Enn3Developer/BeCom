package com.enn3developer.becom.data;

import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.Constants;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.logic.PhoneNumbers;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.logic.phone.PhoneRegistry;

/**
 * World-wide data kept with the Overworld: the phone number registry. Lives in the shared map storage,
 * which every dimension of a world sees.
 */
public final class GlobalSavedData extends WorldSavedData {

    public static final String NAME = "becom_global";

    private final PhoneRegistry phones = new PhoneRegistry();
    private long savedVersion;

    public GlobalSavedData(String name) {
        super(name);
    }

    public static GlobalSavedData get(World world) {
        MapStorage storage = world.mapStorage;
        GlobalSavedData data = (GlobalSavedData) storage.loadData(GlobalSavedData.class, NAME);
        if (data == null) {
            data = new GlobalSavedData(NAME);
            storage.setData(NAME, data);
        }
        return data;
    }

    public PhoneRegistry phones() {
        return phones;
    }

    @Override
    public boolean isDirty() {
        return super.isDirty() || phones.version() != savedVersion;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        NBTTagList list = nbt.getTagList("phones", Constants.NBT.TAG_COMPOUND);
        java.util.Set<Integer> numbers = new java.util.HashSet<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound phone = list.getCompoundTagAt(i);
            // A phone whose number isn't valid, is taken already, or whose kind this BeCom doesn't have is dropped.
            int number = phone.getInteger("number");
            String kind = phone.getString("kind");
            if (!PhoneNumbers.isValid(number) || !numbers.add(number) || kindOf(kind) == null) {
                BeCom.LOG.warn("Dropping a saved phone, number {} of kind \"{}\"", number, kind);
                continue;
            }
            phones.restore(readPhone(phone));
        }
        savedVersion = phones.version();
    }

    private static PhoneRecord.Direction directionOf(String name) {
        try {
            return PhoneRecord.Direction.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (PhoneRecord record : phones.all()) list.appendTag(writePhone(record));
        nbt.setTag("phones", list);
        savedVersion = phones.version();
    }

    private static PhoneKind kindOf(String name) {
        try {
            return PhoneKind.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** One phone, already checked by {@link #readFromNBT}: its number and kind are known. */
    static PhoneRecord readPhone(NBTTagCompound tag) {
        int number = tag.getInteger("number");
        PhoneKind kind = kindOf(tag.getString("kind"));
        PhoneRecord record = new PhoneRecord(number, kind, readUuid(tag, "owner"), tag.getString("ownerName"));
        record.setLocation(tag.getInteger("dim"), tag.getLong("pos"));
        record.setDoNotDisturb(tag.getBoolean("dnd"));
        record.setHidden(tag.getBoolean("hidden"));
        record.setSilent(tag.getBoolean("silent"));
        int[] speedDial = tag.getIntArray("speedDial");
        for (int i = 0; i < Math.min(speedDial.length, PhoneRecord.SPEED_DIAL_KEYS); i++) {
            record.setSpeedDial(i, speedDial[i]);
        }
        record.setMissedSeen(tag.getLong("missedSeen"));
        record.setCamera(!tag.getBoolean("cameraOff"));
        NBTTagList blocked = tag.getTagList("blocked", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < blocked.tagCount(); i++) {
            NBTTagCompound entry = blocked.getCompoundTagAt(i);
            UUID player = readUuid(entry, "player");
            if (player != null) record.restoreBlock(player, entry.getString("name"));
        }
        NBTTagList history = tag.getTagList("history", Constants.NBT.TAG_COMPOUND);
        // Saved newest first and kept in that order.
        for (int i = 0; i < history.tagCount(); i++) {
            NBTTagCompound entry = history.getCompoundTagAt(i);
            PhoneRecord.Direction direction = directionOf(entry.getString("dir"));
            // An entry of a direction this BeCom doesn't have is dropped.
            if (direction == null) continue;
            record.restoreHistory(
                new PhoneRecord.HistoryEntry(
                    entry.getLong("time"),
                    entry.getInteger("number"),
                    entry.getString("name"),
                    direction,
                    readUuid(entry, "player")));
        }
        return record;
    }

    static NBTTagCompound writePhone(PhoneRecord record) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("number", record.number());
        tag.setString(
            "kind",
            record.kind()
                .name());
        writeUuid(tag, "owner", record.owner());
        tag.setString("ownerName", record.ownerName());
        tag.setInteger("dim", record.dimension());
        tag.setLong("pos", record.pos());
        tag.setBoolean("dnd", record.doNotDisturb());
        tag.setBoolean("hidden", record.hidden());
        tag.setBoolean("silent", record.silent());
        int[] speedDial = new int[PhoneRecord.SPEED_DIAL_KEYS];
        for (int i = 0; i < speedDial.length; i++) speedDial[i] = record.speedDial(i);
        tag.setIntArray("speedDial", speedDial);
        tag.setLong("missedSeen", record.missedSeen());
        tag.setBoolean("cameraOff", !record.camera());
        NBTTagList blocked = new NBTTagList();
        for (Map.Entry<UUID, String> entry : record.blocked()
            .entrySet()) {
            NBTTagCompound item = new NBTTagCompound();
            writeUuid(item, "player", entry.getKey());
            item.setString("name", entry.getValue());
            blocked.appendTag(item);
        }
        tag.setTag("blocked", blocked);
        NBTTagList history = new NBTTagList();
        for (PhoneRecord.HistoryEntry entry : record.history()) {
            NBTTagCompound item = new NBTTagCompound();
            item.setLong("time", entry.time());
            item.setInteger("number", entry.otherNumber());
            item.setString("name", entry.otherName());
            writeUuid(item, "player", entry.otherPlayer());
            item.setString(
                "dir",
                entry.direction()
                    .name());
            history.appendTag(item);
        }
        tag.setTag("history", history);
        return tag;
    }

    static UUID readUuid(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key + "Most")) return null;
        return new UUID(tag.getLong(key + "Most"), tag.getLong(key + "Least"));
    }

    static void writeUuid(NBTTagCompound tag, String key, UUID uuid) {
        if (uuid == null) return;
        tag.setLong(key + "Most", uuid.getMostSignificantBits());
        tag.setLong(key + "Least", uuid.getLeastSignificantBits());
    }
}
