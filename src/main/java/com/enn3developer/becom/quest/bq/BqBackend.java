package com.enn3developer.becom.quest.bq;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.config.BeComConfig;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.quest.QuestRefresh;
import com.enn3developer.becom.quest.QuestRewards;
import com.enn3developer.becom.quest.QuestTask;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineEntry;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.DirtyPlayerMarker;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.handlers.NetChapterSync;
import betterquesting.network.handlers.NetQuestSync;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestInstance;
import betterquesting.questing.QuestLine;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.questing.QuestLineEntry;

/**
 * BeCom and BetterQuesting: registers BeCom's task type, puts BeCom's questline into the book once
 * the server has started, brings the quests a world already holds up to the ones BeCom ships, puts it back
 * whenever something removed it, and completes BeCom's tasks.
 * <p>
 * Only ever touches BeCom's own questline and quests, found by their fixed UUIDs. BQ keeps progress inside each quest,
 * so whoever removes them (a DefaultQuests reload) takes the progress too; BeCom keeps a copy of its quests' progress
 * and gives it back when it re-adds them, and reads BQ's own progress files for anything it hasn't copied yet, under
 * the definitions the world last saved, which are the ones that progress was earned under.
 */
public final class BqBackend implements BeComQuests.QuestBackend {

    private static final String ROOT = "/assets/becom/questline/";
    private static final String INDEX = ROOT + "index.txt";
    private static final int TICKS_PER_SECOND = 20;

    private Questline questline;
    private final Map<UUID, NBTTagCompound> progressCopy = new HashMap<>();
    // What the world's own files held, read once when something needs them and forgotten after: BeCom's quests as it
    // last saved them, and their entries in its players' progress files.
    private Map<UUID, NBTTagCompound> savedQuests;
    private Map<UUID, List<NBTTagCompound>> savedProgress;
    // Whether this start changed the book, and so whether to save it on the first tick, before anyone joins: saved
    // from FMLServerStartedEvent itself, BQ would take BeCom for the mod saving and write BeCom's version as its own.
    private boolean changed;
    private boolean saveOnFirstTick;
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
        questline = null;
        progressCopy.clear();
        forgetSaved();
        changed = false;
        saveOnFirstTick = false;
        if (!enabled()) {
            BeCom.LOG.info("BeCom's questline is switched off in the config: the quest book is left alone");
            return;
        }
        try {
            questline = load();
            try {
                refresh();
            } catch (Throwable t) {
                BeCom.LOG.error("Couldn't bring all of BeCom's quests up to date; the rest keep what they had", t);
            }
            ensure(true);
        } catch (Throwable t) {
            BeCom.LOG.error("Couldn't add BeCom's questline; carrying on without it", t);
            questline = null;
        } finally {
            forgetSaved();
        }
        // Writing every marked player's progress file takes the server thread a while: at the first tick, before
        // anyone can join, rather than at the first autosave with players on.
        saveOnFirstTick = changed;
    }

    @Override
    public void tick() {
        if (saveOnFirstTick) {
            saveOnFirstTick = false;
            try {
                SaveLoadHandler.INSTANCE.saveDatabases();
            } catch (Throwable t) {
                BeCom.LOG.error("Couldn't save the quest book BeCom brought up to date; the next save will", t);
            }
        }
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
        recheck();
    }

    private void recheck() {
        if (questline == null) return;
        try {
            ensure(false);
        } catch (Throwable t) {
            BeCom.LOG.error("Couldn't check BeCom's questline", t);
        } finally {
            forgetSaved();
        }
    }

    @Override
    public void serverStopped() {
        questline = null;
        progressCopy.clear();
        forgetSaved();
        saveOnFirstTick = false;
    }

    // region Adding the questline

    /** Adds whatever of the questline is missing, and copies the progress of what's there. */
    private void ensure(boolean startup) {
        Questline q = questline;
        List<UUID> added = new ArrayList<>();
        for (Map.Entry<UUID, NBTTagCompound> entry : q.quests()
            .entrySet()) {
            if (QuestDatabase.INSTANCE.containsKey(entry.getKey())) continue;
            QuestDatabase.INSTANCE.put(entry.getKey(), shipped(entry.getValue()));
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
        changed = true;
        BeCom.LOG.info(
            startup ? "Added BeCom's questline to the quest book ({} quests new)"
                : "BeCom's questline was missing from the quest book; added it back ({} quests)",
            added.size());
        NetQuestSync.quickSync(null, true, true);
        NetChapterSync.sendSync(null, null);
        SaveLoadHandler.INSTANCE.markDirty();
    }

    // endregion

    // region Bringing quests up to date

    /**
     * Brings the quests and questline entries of BeCom's that the book already holds up to the ones BeCom ships, and
     * keeps their progress. Runs at server start, once BQ has loaded the book and, after a pack update, reloaded
     * it. Each quest is compared as BQ writes it, the shipped one after the installed-rewards filter, so a reward left
     * out for a mod that isn't installed is no change. Only BeCom's own UUIDs are touched.
     *
     * @return how many quests changed
     */
    private int refresh() {
        Questline q = questline;
        int quests = 0;
        int entries = 0;
        Set<UUID> players = new HashSet<>();
        try {
            for (Map.Entry<UUID, NBTTagCompound> entry : q.quests()
                .entrySet()) {
                IQuest stored = QuestDatabase.INSTANCE.get(entry.getKey());
                // A quest the book is missing is ensure()'s to add.
                if (stored == null) continue;
                IQuest fresh = shipped(entry.getValue());
                NBTTagCompound book = stored.writeToNBT(new NBTTagCompound());
                NBTTagCompound now = fresh.writeToNBT(new NBTTagCompound());
                if (!QuestRefresh.changed(book, now)) continue;
                // Its players' progress was earned under the definition the world last saved: the book's, unless
                // BetterQuesting's own reload at this start put another copy in the book.
                NBTTagCompound was = savedQuests().getOrDefault(entry.getKey(), book);
                NBTTagCompound progress = was.equals(book) ? stored.writeProgressToNBT(new NBTTagCompound(), null)
                    : earned(entry.getKey(), was, stored);
                fresh.readProgressFromNBT(QuestRefresh.progressFor(was, now, progress), false);
                QuestDatabase.INSTANCE.put(entry.getKey(), fresh);
                players.addAll(QuestRefresh.toMark(was, now, progress));
                quests++;
            }
            IQuestLine line = QuestLineDatabase.INSTANCE.get(q.lineId());
            if (line != null) {
                for (Map.Entry<UUID, NBTTagCompound> entry : q.entries()
                    .entrySet()) {
                    IQuestLineEntry stored = line.get(entry.getKey());
                    if (stored == null) continue;
                    NBTTagCompound now = new QuestLineEntry(entry.getValue()).writeToNBT(new NBTTagCompound());
                    if (now.equals(stored.writeToNBT(new NBTTagCompound()))) continue;
                    stored.readFromNBT(now);
                    entries++;
                }
            }
        } finally {
            // Saved with the book's next save, even when a later quest failed: what changed stays changed. BQ writes a
            // player's progress file only once they're marked, and reads every file back at the next start, so the
            // players of a quest whose tasks changed are marked, or a task gets old progress back from their file.
            if (quests > 0 || entries > 0) {
                SaveLoadHandler.INSTANCE.markDirty();
                changed = true;
            }
            if (!players.isEmpty()) DirtyPlayerMarker.markDirty(players);
        }
        if (quests == 0 && entries == 0) return 0;
        BeCom.LOG.info(
            "Brought {} of BeCom's quests and {} of its questline's places up to date, keeping players' progress",
            quests,
            entries);
        NetQuestSync.quickSync(null, true, true);
        NetChapterSync.sendSync(null, null);
        return quests;
    }

    /** A quest as BeCom ships it, without the rewards whose items aren't installed. */
    private static IQuest shipped(NBTTagCompound file) {
        IQuest quest = new QuestInstance();
        quest.readFromNBT(withInstalledRewards((NBTTagCompound) file.copy()));
        return quest;
    }

    /** A reward whose item isn't installed is left out. */
    private static NBTTagCompound withInstalledRewards(NBTTagCompound quest) {
        return QuestRewards.withInstalled(quest, id -> Item.itemRegistry.getObject(id) != null);
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

    /**
     * Gives re-added quests their progress back: from BeCom's copy, or from BQ's progress files, read under the
     * definition the world last saved and brought to the one BeCom ships as the refresh brings a quest the book still
     * holds. A quest the world never saved takes the files' progress as they read.
     */
    private void restoreProgress(List<UUID> quests) {
        Set<UUID> players = new HashSet<>();
        try {
            for (UUID id : quests) {
                IQuest quest = QuestDatabase.INSTANCE.get(id);
                if (quest == null) continue;
                NBTTagCompound copy = progressCopy.get(id);
                if (copy != null) {
                    quest.readProgressFromNBT(copy, false);
                    continue;
                }
                NBTTagCompound now = quest.writeToNBT(new NBTTagCompound());
                NBTTagCompound was = savedQuests().getOrDefault(id, now);
                NBTTagCompound progress = earned(id, was, null);
                quest.readProgressFromNBT(QuestRefresh.progressFor(was, now, progress), false);
                players.addAll(QuestRefresh.toMark(was, now, progress));
            }
        } finally {
            if (!players.isEmpty()) DirtyPlayerMarker.markDirty(players);
        }
    }

    /**
     * What the players earned in one of BeCom's quests under {@code definition}, the one the world last saved: their
     * progress files read as BQ reads them under it, with what the book holds for the quest now, if anything. After
     * BQ's own reload at this start put another copy in the book, the book's progress, carried there by index and kind,
     * misses every task that copy has no match for, and the files still hold it.
     */
    private NBTTagCompound earned(UUID id, NBTTagCompound definition, IQuest book) {
        IQuest quest = new QuestInstance();
        quest.readFromNBT(definition);
        if (book != null) quest.readProgressFromNBT(book.writeProgressToNBT(new NBTTagCompound(), null), true);
        for (NBTTagCompound entry : savedProgress().getOrDefault(id, List.of())) quest.readProgressFromNBT(entry, true);
        return quest.writeProgressToNBT(new NBTTagCompound(), null);
    }

    /**
     * BeCom's quests as the world last saved them, the definitions its players' progress files were last written under.
     * At server start, BQ's own reload of a pack's quests has changed the book but written nothing. Read through BQ's
     * code, as BQ reads the file and like the book's and the shipped ones.
     */
    private Map<UUID, NBTTagCompound> savedQuests() {
        if (savedQuests != null) return savedQuests;
        Map<UUID, NBTTagCompound> saved = new HashMap<>();
        if (BQ_Settings.curWorldDir != null) {
            NBTTagCompound file = NBTConverter.JSONtoNBT_Object(
                JsonHelper.ReadFromFile(new File(BQ_Settings.curWorldDir, "QuestDatabase.json")),
                new NBTTagCompound(),
                true);
            NBTTagList quests = file.getTagList("questDatabase", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < quests.tagCount(); i++) {
                NBTTagCompound entry = quests.getCompoundTagAt(i);
                NBTConverter.UuidValueType.QUEST.tryReadId(entry)
                    .filter(
                        questline.quests()
                            .keySet()::contains)
                    .ifPresent(id -> {
                        IQuest quest = new QuestInstance();
                        quest.readFromNBT(entry);
                        saved.put(id, quest.writeToNBT(new NBTTagCompound()));
                    });
            }
        }
        savedQuests = saved;
        return saved;
    }

    /**
     * Each of BeCom's quests' entries in the players' progress files, read as BQ reads them at a start: a world's old
     * single file first, if it still has one, then each player's, through BQ's own reader, which leaves out the copies
     * BQ keeps of files it couldn't read.
     */
    private Map<UUID, List<NBTTagCompound>> savedProgress() {
        if (savedProgress != null) return savedProgress;
        Map<UUID, List<NBTTagCompound>> saved = new HashMap<>();
        if (BQ_Settings.curWorldDir != null) {
            List<File> files = new ArrayList<>();
            files.add(new File(BQ_Settings.curWorldDir, "QuestProgress.json"));
            File[] players = new File(BQ_Settings.curWorldDir, "QuestProgress")
                .listFiles((d, name) -> name.endsWith(".json"));
            if (players != null) files.addAll(Arrays.asList(players));
            for (File file : files) {
                NBTTagList all = NBTConverter
                    .JSONtoNBT_Object(JsonHelper.ReadFromFile(file), new NBTTagCompound(), true)
                    .getTagList("questProgress", Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < all.tagCount(); i++) {
                    NBTTagCompound entry = all.getCompoundTagAt(i);
                    NBTConverter.UuidValueType.QUEST.tryReadId(entry)
                        .filter(
                            questline.quests()
                                .keySet()::contains)
                        .ifPresent(
                            id -> saved.computeIfAbsent(id, k -> new ArrayList<>())
                                .add(entry));
                }
            }
        }
        savedProgress = saved;
        return saved;
    }

    /** The world's files change with every save: what was read of them is read again when next needed. */
    private void forgetSaved() {
        savedQuests = null;
        savedProgress = null;
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

}
