package com.enn3developer.becom.data;

import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import com.enn3developer.becom.BeCom;
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

    public GlobalSavedData(String name) {
        super(name);
    }

    /** @throws IllegalStateException when the saved data is newer than this BeCom understands */
    public static GlobalSavedData get(World world) {
        GlobalSavedData data = (GlobalSavedData) world.mapStorage.loadData(GlobalSavedData.class, NAME);
        if (data == null) {
            data = new GlobalSavedData(NAME);
            world.mapStorage.setData(NAME, data);
        }
        if (data.tooNewData != null) {
            throw SavedDataFormat
                .newerThanUnderstood("the phone registry", SavedDataFormat.versionOf(data.tooNewData), FORMAT);
        }
        return data;
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
            return;
        }
        NBTTagList list = nbt.getTagList("phones", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            PhoneRecord record = readPhone(list.getCompoundTagAt(i));
            if (record != null && phones.get(record.number()) == null) phones.restore(record);
        }
        savedVersion = phones.version();
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

    static PhoneRecord readPhone(NBTTagCompound tag) {
        int number = tag.getInteger("number");
        if (!PhoneNumbers.isValid(number)) {
            BeCom.LOG.warn("Dropping a saved phone with invalid number {}", number);
            return null;
        }
        PhoneKind kind;
        try {
            kind = PhoneKind.valueOf(tag.getString("kind"));
        } catch (IllegalArgumentException e) {
            BeCom.LOG.warn("Dropping saved phone {} of unknown kind {}", number, tag.getString("kind"));
            return null;
        }
        PhoneRecord record = new PhoneRecord(number, kind, readUuid(tag, "owner"), tag.getString("ownerName"));
        record.setLocation(tag.getInteger("dim"), tag.getLong("pos"));
        record.setDoNotDisturb(tag.getBoolean("dnd"));
        record.setHidden(tag.getBoolean("hidden"));
        NBTTagList blocked = tag.getTagList("blocked", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < blocked.tagCount(); i++) {
            NBTTagCompound entry = blocked.getCompoundTagAt(i);
            UUID player = readUuid(entry, "player");
            if (player != null) record.block(player, entry.getString("name"));
        }
        NBTTagList history = tag.getTagList("history", Constants.NBT.TAG_COMPOUND);
        // Saved newest first; added oldest first so the newest ends up on top again.
        for (int i = history.tagCount() - 1; i >= 0; i--) {
            NBTTagCompound entry = history.getCompoundTagAt(i);
            PhoneRecord.Direction direction;
            try {
                direction = PhoneRecord.Direction.valueOf(entry.getString("dir"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            record.addHistory(
                new PhoneRecord.HistoryEntry(
                    entry.getLong("time"),
                    entry.getInteger("number"),
                    entry.getString("name"),
                    direction));
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
