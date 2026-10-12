package com.enn3developer.becom.data;

import java.io.File;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.Constants;

import com.enn3developer.becom.logic.PhoneNumbers;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.logic.phone.PhoneRegistry;

/**
 * World-wide data kept with the Overworld (DESIGN.md 14): the phone number registry. Lives in the shared map storage,
 * which every dimension of a world sees.
 */
public final class GlobalSavedData extends WorldSavedData {

    public static final String NAME = "becom_global";
    public static final int FORMAT = 1;

    private final PhoneRegistry phones = new PhoneRegistry();
    private long savedVersion;
    private NBTTagCompound tooNewData;
    // What this BeCom found it doesn't understand, which means a newer BeCom saved the file.
    private String notUnderstood;
    private boolean read;

    public GlobalSavedData(String name) {
        super(name);
    }

    /** @throws IllegalStateException when the saved data is newer than this BeCom understands */
    public static GlobalSavedData get(World world) {
        return load(
            world.mapStorage,
            world.getSaveHandler()
                .getMapFileFromName(NAME));
    }

    /** Loads the registry from {@code storage}, whose file is {@code file}; {@link #get} for the Overworld. */
    static GlobalSavedData load(MapStorage storage, File file) {
        GlobalSavedData data = (GlobalSavedData) storage.loadData(GlobalSavedData.class, NAME);
        if (data == null) {
            data = new GlobalSavedData(NAME);
            // Nothing on disk to read: a new world. Counts as read, so a later get() after the first save is fine.
            data.read = true;
            storage.setData(NAME, data);
        } else if (SavedDataFormat.unreadable(file != null && file.isFile(), data.read)) {
            data.tooNewData = new NBTTagCompound();
            throw SavedDataFormat.couldNotRead("the phone registry", file);
        }
        if (data.notUnderstood != null) throw SavedDataFormat.notUnderstood("the phone registry", data.notUnderstood);
        if (data.tooNewData != null) {
            throw SavedDataFormat
                .newerThanUnderstood("the phone registry", SavedDataFormat.versionOf(data.tooNewData), FORMAT);
        }
        return data;
    }

    /** Whether {@link #readFromNBT} ran; false for data vanilla failed to read. */
    public boolean wasRead() {
        return read;
    }

    public PhoneRegistry phones() {
        return phones;
    }

    public boolean isTooNew() {
        return tooNewData != null;
    }

    @Override
    public boolean isDirty() {
        if (tooNewData != null) return false;
        return super.isDirty() || phones.version() != savedVersion;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        if (SavedDataFormat.tooNew(nbt, FORMAT)) {
            tooNewData = (NBTTagCompound) nbt.copy();
            read = true;
            return;
        }
        NBTTagList list = nbt.getTagList("phones", Constants.NBT.TAG_COMPOUND);
        // Phone kinds and history directions are added without a format bump: one this BeCom doesn't know means a newer
        // BeCom saved the file. Dropping those phones would free their numbers for reuse (D12), so keep it untouched.
        java.util.Set<Integer> numbers = new java.util.HashSet<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound phone = list.getCompoundTagAt(i);
            // Numbers are six digits and unique (D12, DESIGN.md 14.2): anything else isn't data this BeCom wrote.
            int number = phone.getInteger("number");
            if (!PhoneNumbers.isValid(number) || !numbers.add(number)) {
                notUnderstood(nbt, "a phone number that is invalid or repeated, " + number);
                return;
            }
            String kind = phone.getString("kind");
            if (kindOf(kind) == null) {
                notUnderstood(nbt, "a phone kind it doesn't know, \"" + kind + "\"");
                return;
            }
            NBTTagList history = phone.getTagList("history", Constants.NBT.TAG_COMPOUND);
            for (int j = 0; j < history.tagCount(); j++) {
                String dir = history.getCompoundTagAt(j)
                    .getString("dir");
                if (directionOf(dir) == null) {
                    notUnderstood(nbt, "a call history entry it doesn't know, \"" + dir + "\"");
                    return;
                }
            }
        }
        for (int i = 0; i < list.tagCount(); i++) phones.restore(readPhone(list.getCompoundTagAt(i)));
        savedVersion = phones.version();
        // Last: anything thrown above leaves the data unread, and so never saved over the file.
        read = true;
    }

    private void notUnderstood(NBTTagCompound nbt, String detail) {
        notUnderstood = detail;
        tooNewData = (NBTTagCompound) nbt.copy();
        read = true;
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
        if (tooNewData != null) {
            for (String key : tooNewData.func_150296_c()) nbt.setTag(key, tooNewData.getTag(key));
            return;
        }
        SavedDataFormat.write(nbt, FORMAT);
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

    /** One phone, already checked by {@link #readFromNBT}: its number, kind and history directions are known. */
    static PhoneRecord readPhone(NBTTagCompound tag) {
        int number = tag.getInteger("number");
        PhoneKind kind = kindOf(tag.getString("kind"));
        PhoneRecord record = new PhoneRecord(number, kind, readUuid(tag, "owner"), tag.getString("ownerName"));
        record.setLocation(tag.getInteger("dim"), tag.getLong("pos"));
        record.setDoNotDisturb(tag.getBoolean("dnd"));
        record.setHidden(tag.getBoolean("hidden"));
        NBTTagList blocked = tag.getTagList("blocked", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < blocked.tagCount(); i++) {
            NBTTagCompound entry = blocked.getCompoundTagAt(i);
            UUID player = readUuid(entry, "player");
            // As saved, even past this BeCom's cap: a newer one may allow more, and the list isn't this BeCom's to cut.
            if (player != null) record.restoreBlock(player, entry.getString("name"));
        }
        NBTTagList history = tag.getTagList("history", Constants.NBT.TAG_COMPOUND);
        // Saved newest first and kept in that order, all of it: a newer BeCom may keep more than this one does, and
        // the next call trims it as usual.
        for (int i = 0; i < history.tagCount(); i++) {
            NBTTagCompound entry = history.getCompoundTagAt(i);
            PhoneRecord.Direction direction = directionOf(entry.getString("dir"));
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
