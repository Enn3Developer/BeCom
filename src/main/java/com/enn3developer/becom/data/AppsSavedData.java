package com.enn3developer.becom.data;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.Constants;

import com.enn3developer.becom.logic.app.AppLimits;
import com.enn3developer.becom.logic.app.AppManifest;
import com.enn3developer.becom.logic.app.AppStore;
import com.enn3developer.becom.logic.app.PhoneApps;

/**
 * Phone apps, kept with the Overworld: every phone's installed Lua apps and their settings, and the server-wide App
 * Store. An app's source is kept here, not on the phone's own storage, so a phone's OpenComputers machine can stop
 * whenever it's put away and lose nothing.
 */
public final class AppsSavedData extends WorldSavedData {

    public static final String NAME = "becom_apps";
    public static final int FORMAT = 1;

    private final Map<Integer, PhoneApps> phones = new HashMap<>();
    private final AppStore store = new AppStore();
    // The day, of the world's total time, each BeCom Card computer last ran: one gone long enough loses its number.
    private final Map<Integer, Long> seen = new HashMap<>();
    private NBTTagCompound tooNewData;
    private boolean read;

    public AppsSavedData(String name) {
        super(name);
    }

    /** @throws IllegalStateException when the saved data is newer than this BeCom understands */
    public static AppsSavedData get(World world) {
        return load(
            world.mapStorage,
            world.getSaveHandler()
                .getMapFileFromName(NAME));
    }

    static AppsSavedData load(MapStorage storage, File file) {
        AppsSavedData data = (AppsSavedData) storage.loadData(AppsSavedData.class, NAME);
        if (data == null) {
            data = new AppsSavedData(NAME);
            data.read = true;
            storage.setData(NAME, data);
        } else if (SavedDataFormat.unreadable(file != null && file.isFile(), data.read)) {
            data.tooNewData = new NBTTagCompound();
            throw SavedDataFormat.couldNotRead("phone apps", file);
        }
        if (data.tooNewData != null) {
            throw SavedDataFormat.newerThanUnderstood("phone apps", SavedDataFormat.versionOf(data.tooNewData), FORMAT);
        }
        return data;
    }

    private AppLimits limits = AppLimits.DEFAULTS;

    /** The server's limits, for the store and every phone. */
    public void setLimits(AppLimits limits) {
        this.limits = limits;
        store.setLimits(limits);
        for (PhoneApps apps : phones.values()) apps.setLimits(limits);
    }

    /** The apps on {@code number}, made on first use. */
    public PhoneApps apps(int number) {
        return phones.computeIfAbsent(number, n -> {
            PhoneApps apps = new PhoneApps();
            apps.setLimits(limits);
            return apps;
        });
    }

    /** The apps on {@code number}, or null when it has none. */
    public PhoneApps appsIfAny(int number) {
        return phones.get(number);
    }

    public AppStore store() {
        return store;
    }

    /** Forgets a phone's apps: its number is gone. */
    public void forget(int number) {
        if (phones.remove(number) != null | seen.remove(number) != null) markDirty();
    }

    /** The numbers that have apps. */
    public java.util.Set<Integer> numbers() {
        return java.util.Collections.unmodifiableSet(phones.keySet());
    }

    /** The numbers of the computers whose last run is kept. */
    public java.util.Set<Integer> seenNumbers() {
        return java.util.Collections.unmodifiableSet(seen.keySet());
    }

    /** A computer with {@code number} ran on {@code day}. */
    public void seen(int number, long day) {
        Long before = seen.put(number, day);
        if (before == null || before != day) markDirty();
    }

    /** The day a computer with {@code number} last ran, or null when it never did since this was kept. */
    public Long seenOn(int number) {
        return seen.get(number);
    }

    @Override
    public boolean isDirty() {
        return tooNewData == null && super.isDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        read = true;
        if (SavedDataFormat.tooNew(nbt, FORMAT)) {
            tooNewData = (NBTTagCompound) nbt.copy();
            return;
        }
        NBTTagList list = nbt.getTagList("phones", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound phone = list.getCompoundTagAt(i);
            PhoneApps apps = apps(phone.getInteger("number"));
            NBTTagList installed = phone.getTagList("apps", Constants.NBT.TAG_COMPOUND);
            for (int j = 0; j < installed.tagCount(); j++) {
                NBTTagCompound tag = installed.getCompoundTagAt(j);
                AppManifest manifest = AppManifest.parse(source(tag));
                if (manifest == null) continue;
                apps.restore(
                    new PhoneApps.App(
                        manifest,
                        source(tag),
                        tag.getString("author"),
                        new PhoneApps.Origin(tag.getBoolean("store"), tag.getString("where")),
                        tag.getBoolean("background"),
                        tag.getLong("day"),
                        tag.getInteger("woke"),
                        tag.getLong("eu")));
            }
        }
        NBTTagList entries = nbt.getTagList("store", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound tag = entries.getCompoundTagAt(i);
            AppManifest manifest = AppManifest.parse(source(tag));
            if (manifest == null) continue;
            store.restore(
                new AppStore.Entry(
                    manifest.id(),
                    manifest.name(),
                    manifest.version(),
                    manifest.accent(),
                    manifest.icon(),
                    manifest.about(),
                    new UUID(tag.getLong("authorMost"), tag.getLong("authorLeast")),
                    tag.getString("authorName"),
                    source(tag),
                    tag.getLong("published"),
                    tag.getInteger("installs"),
                    tag.getBoolean("hidden"),
                    tag.getBoolean("team")));
        }
        NBTTagList removed = nbt.getTagList("removed", Constants.NBT.TAG_STRING);
        for (int i = 0; i < removed.tagCount(); i++) store.restoreRemoved(removed.getStringTagAt(i));
        NBTTagList reports = nbt.getTagList("reports", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < reports.tagCount(); i++) {
            NBTTagCompound tag = reports.getCompoundTagAt(i);
            store.restoreReport(tag.getString("id"), new UUID(tag.getLong("most"), tag.getLong("least")));
        }
        NBTTagList computers = nbt.getTagList("seen", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < computers.tagCount(); i++) {
            NBTTagCompound tag = computers.getCompoundTagAt(i);
            seen.put(tag.getInteger("number"), tag.getLong("day"));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        if (tooNewData != null) {
            for (Object key : tooNewData.func_150296_c()) {
                nbt.setTag((String) key, tooNewData.getTag((String) key));
            }
            return;
        }
        SavedDataFormat.write(nbt, FORMAT);
        NBTTagList list = new NBTTagList();
        for (Map.Entry<Integer, PhoneApps> entry : phones.entrySet()) {
            if (entry.getValue()
                .isEmpty()) continue;
            NBTTagCompound phone = new NBTTagCompound();
            phone.setInteger("number", entry.getKey());
            NBTTagList installed = new NBTTagList();
            for (PhoneApps.App app : entry.getValue()
                .all()) {
                NBTTagCompound tag = new NBTTagCompound();
                source(tag, app.source());
                tag.setString("author", app.author());
                tag.setBoolean(
                    "store",
                    app.origin()
                        .store());
                tag.setString(
                    "where",
                    app.origin()
                        .where());
                tag.setBoolean("background", app.background());
                tag.setLong("day", app.day());
                tag.setInteger("woke", app.woke());
                tag.setLong("eu", app.euToday());
                installed.appendTag(tag);
            }
            phone.setTag("apps", installed);
            list.appendTag(phone);
        }
        nbt.setTag("phones", list);
        NBTTagList entries = new NBTTagList();
        for (AppStore.Entry e : store.all()) {
            NBTTagCompound tag = new NBTTagCompound();
            source(tag, e.source());
            tag.setLong(
                "authorMost",
                e.author()
                    .getMostSignificantBits());
            tag.setLong(
                "authorLeast",
                e.author()
                    .getLeastSignificantBits());
            tag.setString("authorName", e.authorName());
            tag.setLong("published", e.published());
            tag.setInteger("installs", e.installs());
            tag.setBoolean("hidden", e.hidden());
            tag.setBoolean("team", e.team());
            entries.appendTag(tag);
        }
        nbt.setTag("store", entries);
        NBTTagList removed = new NBTTagList();
        for (String id : store.removed()) removed.appendTag(new net.minecraft.nbt.NBTTagString(id));
        nbt.setTag("removed", removed);
        NBTTagList reports = new NBTTagList();
        for (Map.Entry<String, java.util.Set<UUID>> entry : store.reports()
            .entrySet()) {
            for (UUID player : entry.getValue()) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("id", entry.getKey());
                tag.setLong("most", player.getMostSignificantBits());
                tag.setLong("least", player.getLeastSignificantBits());
                reports.appendTag(tag);
            }
        }
        nbt.setTag("reports", reports);
        NBTTagList computers = new NBTTagList();
        for (Map.Entry<Integer, Long> entry : seen.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("number", entry.getKey());
            tag.setLong("day", entry.getValue());
            computers.appendTag(tag);
        }
        nbt.setTag("seen", computers);
    }

    // A source is up to 64 KB of UTF-8: too big for an NBT string, which writeUTF caps at 65,535 bytes, failing the
    // whole
    // save. As bytes it has no such limit.
    private static void source(NBTTagCompound tag, String source) {
        tag.setByteArray("source", source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String source(NBTTagCompound tag) {
        return new String(tag.getByteArray("source"), java.nio.charset.StandardCharsets.UTF_8);
    }
}
