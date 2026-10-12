package com.enn3developer.becom.quest.bq;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.quest.QuestTask;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.storage.DBEntry;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.handlers.NetChapterSync;
import betterquesting.network.handlers.NetQuestSync;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestInstance;
import betterquesting.questing.QuestLine;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.questing.QuestLineEntry;

/**
 * BeCom and BetterQuesting (DESIGN.md 15.5): registers BeCom's task type, puts BeCom's questline into the book once
 * the server has started, puts it back whenever something removed it, and completes BeCom's tasks.
 * <p>
 * Only ever touches BeCom's own questline and quests, found by their fixed UUIDs. BQ keeps progress inside each quest,
 * so whoever removes them (a DefaultQuests reload) takes the progress too; BeCom keeps a copy of its quests' progress
 * and gives it back when it re-adds them, and reads BQ's own progress files for anything it hasn't copied yet.
 */
public final class BqBackend implements BeComQuests.QuestBackend {

    private static final String ROOT = "/assets/becom/questline/";
    private static final String INDEX = ROOT + "index.txt";
    private static final int TICKS_PER_SECOND = 20;

    private Questline questline;
    private final Map<UUID, NBTTagCompound> progressCopy = new HashMap<>();
    private long ticks;

    /** BeCom's questline as its files describe it. */
    private record Questline(UUID lineId, NBTTagCompound line, Map<UUID, NBTTagCompound> entries,
        Map<UUID, NBTTagCompound> quests) {}

    @Override
    public void init() {
        QuestingAPI.getAPI(ApiReference.TASK_REG)
            .register(FactoryTaskBeCom.INSTANCE);
        BeCom.LOG.info("Registered BeCom's BetterQuesting task type {}", FactoryTaskBeCom.ID);
    }

    private boolean enabled() {
        return BeComConfig.get()
            .questlineEnabled();
    }

    @Override
    public void serverStarted() {
        ticks = 0;
        progressCopy.clear();
        if (!enabled()) {
            BeCom.LOG.info("BeCom's questline is switched off in the config: the quest book is left alone");
            return;
        }
        try {
            questline = load();
            ensure(true);
        } catch (Throwable t) {
            BeCom.LOG.error("Couldn't add BeCom's questline; carrying on without it", t);
            questline = null;
        }
    }

    @Override
    public void tick() {
        if (questline == null || !enabled()) return;
        ticks++;
        // A reload takes BeCom's quests with their progress, and the copy is all that brings it back: keep it fresh,
        // so at most a second of progress is lost (writing a handful of quests' progress is cheap).
        if (ticks % TICKS_PER_SECOND == 0) {
            try {
                copyProgress();
            } catch (Throwable t) {
                BeCom.LOG.error("Couldn't copy the progress of BeCom's quests", t);
            }
        }
        int every = BeComConfig.get()
            .questlineRecheckSeconds() * TICKS_PER_SECOND;
        if (every <= 0 || ticks % every != 0) return;
        recheckNow();
    }

    @Override
    public void recheckNow() {
        if (questline == null) return;
        try {
            ensure(false);
        } catch (Throwable t) {
            BeCom.LOG.error("Couldn't check BeCom's questline", t);
        }
    }

    @Override
    public void serverStopped() {
        questline = null;
        progressCopy.clear();
    }

    // region Adding the questline

    /** Adds whatever of the questline is missing, and copies the progress of what's there. */
    private void ensure(boolean startup) {
        Questline q = questline;
        List<UUID> added = new ArrayList<>();
        for (Map.Entry<UUID, NBTTagCompound> entry : q.quests()
            .entrySet()) {
            if (QuestDatabase.INSTANCE.containsKey(entry.getKey())) continue;
            IQuest quest = new QuestInstance();
            quest.readFromNBT(
                withInstalledRewards(
                    (NBTTagCompound) entry.getValue()
                        .copy()));
            QuestDatabase.INSTANCE.put(entry.getKey(), quest);
            added.add(entry.getKey());
        }
        boolean lineAdded = false;
        IQuestLine line = QuestLineDatabase.INSTANCE.get(q.lineId());
        if (line == null) {
            line = new QuestLine();
            line.readFromNBT(q.line());
            List<Map.Entry<UUID, IQuestLine>> ordered = new ArrayList<>(QuestLineDatabase.INSTANCE.getOrderedEntries());
            ordered.add(Maps.immutableEntry(q.lineId(), line));
            QuestLineDatabase.INSTANCE.setOrderedEntries(ordered);
            lineAdded = true;
        }
        for (Map.Entry<UUID, NBTTagCompound> entry : q.entries()
            .entrySet()) {
            if (!line.containsKey(entry.getKey())) {
                line.put(entry.getKey(), new QuestLineEntry(entry.getValue()));
                lineAdded = true;
            }
        }
        if (!added.isEmpty()) restoreProgress(added);
        copyProgress();
        if (added.isEmpty() && !lineAdded) return;
        BeCom.LOG.info(
            startup ? "Added BeCom's questline to the quest book ({} quests new)"
                : "BeCom's questline was missing from the quest book; added it back ({} quests)",
            added.size());
        NetQuestSync.quickSync(null, true, true);
        NetChapterSync.sendSync(null, null);
        SaveLoadHandler.INSTANCE.markDirty();
    }

    /** DESIGN.md 15.5: a reward whose item isn't installed is left out. */
    private static NBTTagCompound withInstalledRewards(NBTTagCompound quest) {
        NBTTagList rewards = quest.getTagList("rewards", Constants.NBT.TAG_COMPOUND);
        NBTTagList kept = new NBTTagList();
        for (int i = 0; i < rewards.tagCount(); i++) {
            NBTTagCompound reward = rewards.getCompoundTagAt(i);
            NBTTagList items = reward.getTagList("rewards", Constants.NBT.TAG_COMPOUND);
            if (items.tagCount() == 0) {
                kept.appendTag(reward);
                continue;
            }
            NBTTagList installed = new NBTTagList();
            for (int j = 0; j < items.tagCount(); j++) {
                NBTTagCompound item = items.getCompoundTagAt(j);
                if (Item.itemRegistry.getObject(item.getString("id")) != null) installed.appendTag(item);
            }
            if (installed.tagCount() == 0) continue;
            reward.setTag("rewards", installed);
            reward.setInteger("index", kept.tagCount());
            kept.appendTag(reward);
        }
        quest.setTag("rewards", kept);
        return quest;
    }

    // endregion

    // region Progress

    private void copyProgress() {
        for (UUID id : questline.quests()
            .keySet()) {
            IQuest quest = QuestDatabase.INSTANCE.get(id);
            if (quest != null) progressCopy.put(id, quest.writeProgressToNBT(new NBTTagCompound(), null));
        }
    }

    /** Gives re-added quests their progress back: from BeCom's copy, or from BQ's progress files on disk. */
    private void restoreProgress(List<UUID> quests) {
        List<UUID> fromDisk = new ArrayList<>();
        for (UUID id : quests) {
            NBTTagCompound copy = progressCopy.get(id);
            IQuest quest = QuestDatabase.INSTANCE.get(id);
            if (copy != null && quest != null) quest.readProgressFromNBT(copy, false);
            else fromDisk.add(id);
        }
        if (!fromDisk.isEmpty()) restoreFromDisk(fromDisk);
    }

    private void restoreFromDisk(Collection<UUID> quests) {
        File dir = BQ_Settings.curWorldDir == null ? null : new File(BQ_Settings.curWorldDir, "QuestProgress");
        File[] files = dir == null ? null : dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;
        for (File file : files) {
            try (Reader reader = new InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8)) {
                JsonObject json = new JsonParser().parse(reader)
                    .getAsJsonObject();
                NBTTagCompound nbt = NBTConverter.JSONtoNBT_Object(json, new NBTTagCompound(), true);
                NBTTagList all = nbt.getTagList("questProgress", Constants.NBT.TAG_COMPOUND);
                NBTTagList ours = new NBTTagList();
                for (int i = 0; i < all.tagCount(); i++) {
                    NBTTagCompound entry = all.getCompoundTagAt(i);
                    NBTConverter.UuidValueType.QUEST.tryReadId(entry)
                        .filter(quests::contains)
                        .ifPresent(id -> ours.appendTag(entry));
                }
                if (ours.tagCount() > 0) QuestDatabase.INSTANCE.readProgressFromNBT(ours, true);
            } catch (Exception e) {
                BeCom.LOG.warn("Couldn't read quest progress from {}", file, e);
            }
        }
    }

    // endregion

    // region Completing tasks

    @Override
    public void complete(QuestTask kind, Collection<UUID> players) {
        if (players.isEmpty()) return;
        // Every quest with a BeCom task, BeCom's own or a pack's that reused the task type.
        for (Map.Entry<UUID, IQuest> entry : QuestDatabase.INSTANCE.entrySet()) {
            IQuest quest = entry.getValue();
            for (DBEntry<ITask> task : quest.getTasks()
                .getEntries()) {
                if (!(task.getValue() instanceof TaskBeCom becom) || becom.kind() != kind) continue;
                for (UUID player : players) {
                    if (becom.isComplete(player) || !quest.isUnlocked(player)) continue;
                    becom.setComplete(player);
                    EntityPlayerMP online = QuestingAPI.getPlayer(player);
                    if (online == null) continue;
                    QuestCache cache = (QuestCache) online.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());
                    if (cache != null) cache.markQuestDirty(entry.getKey());
                }
            }
        }
    }

    // endregion

    // region Loading the files

    private static Questline load() throws Exception {
        List<String> paths = new ArrayList<>();
        try (BufferedReader index = open(INDEX)) {
            String line;
            while ((line = index.readLine()) != null) {
                if (!line.isBlank()) paths.add(line.trim());
            }
        }
        UUID lineId = null;
        NBTTagCompound lineTag = null;
        Map<UUID, NBTTagCompound> entries = new LinkedHashMap<>();
        Map<UUID, NBTTagCompound> quests = new LinkedHashMap<>();
        for (String path : paths) {
            NBTTagCompound tag;
            try (BufferedReader reader = open(ROOT + path)) {
                JsonObject json = new JsonParser().parse(reader)
                    .getAsJsonObject();
                tag = NBTConverter.JSONtoNBT_Object(json, new NBTTagCompound(), true);
            }
            if (path.startsWith("DefaultQuests/QuestLines/") && path.endsWith("/QuestLine.json")) {
                lineId = NBTConverter.UuidValueType.QUEST_LINE.readId(tag);
                lineTag = tag;
            } else if (path.startsWith("DefaultQuests/QuestLines/")) {
                entries.put(NBTConverter.UuidValueType.QUEST.readId(tag), tag);
            } else if (path.startsWith("DefaultQuests/Quests/")) {
                quests.put(NBTConverter.UuidValueType.QUEST.readId(tag), tag);
            }
        }
        if (lineId == null) throw new IllegalStateException("BeCom's questline files have no QuestLine.json");
        return new Questline(lineId, lineTag, entries, quests);
    }

    private static BufferedReader open(String resource) {
        InputStream in = BqBackend.class.getResourceAsStream(resource);
        if (in == null) throw new IllegalStateException("Missing BeCom quest resource " + resource);
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    // endregion

    // region Self-test hooks

    @Override
    public boolean questlinePresent() {
        Questline q = questline;
        if (q == null || !QuestLineDatabase.INSTANCE.containsKey(q.lineId())) return false;
        for (UUID id : q.quests()
            .keySet()) {
            if (!QuestDatabase.INSTANCE.containsKey(id)) return false;
        }
        return true;
    }

    @Override
    public void removeQuestlineForTest() {
        Questline q = questline;
        if (q == null) return;
        copyProgress();
        QuestLineDatabase.INSTANCE.remove(q.lineId());
        for (UUID id : q.quests()
            .keySet()) {
            QuestLineDatabase.INSTANCE.removeQuest(id);
            QuestDatabase.INSTANCE.remove(id);
        }
    }

    /** The self-test's handle on a BeCom quest's task, to check progress survives removal. */
    public TaskBeCom firstTask(QuestTask kind) {
        Questline q = questline;
        if (q == null) return null;
        for (UUID id : q.quests()
            .keySet()) {
            IQuest quest = QuestDatabase.INSTANCE.get(id);
            if (quest == null) continue;
            for (DBEntry<ITask> task : quest.getTasks()
                .getEntries()) {
                if (task.getValue() instanceof TaskBeCom becom && becom.kind() == kind) return becom;
            }
        }
        return null;
    }

    // endregion
}
