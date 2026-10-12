package com.enn3developer.becom.quest;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.enn3developer.becom.BeCom;

import cpw.mods.fml.common.Loader;

/**
 * Where BeCom reports what its quest tasks wait for (D50). BetterQuesting is optional (DESIGN.md 15.5): without it,
 * every report is dropped. Server thread only.
 */
public final class BeComQuests {

    public static final String BQ_MODID = "betterquesting";

    private static QuestBackend backend = QuestBackend.NONE;
    // What each task was reported for, kept only while the self-test runs: its players are fake, so no quest of
    // theirs is ever unlocked to complete.
    private static Map<QuestTask, Set<UUID>> reported;

    private BeComQuests() {}

    /** Picks the BetterQuesting backend when BQ is installed; called once at startup. */
    public static void init() {
        if (!Loader.isModLoaded(BQ_MODID)) {
            BeCom.LOG.info("BetterQuesting isn't installed: BeCom's quests are off");
            return;
        }
        try {
            backend = (QuestBackend) Class.forName("com.enn3developer.becom.quest.bq.BqBackend")
                .getConstructor()
                .newInstance();
            backend.init();
        } catch (Throwable t) {
            // DESIGN.md 15.5: anything going wrong with the quest book leaves BeCom running without it.
            BeCom.LOG.error("BetterQuesting integration failed to start; BeCom carries on without its quests", t);
            backend = QuestBackend.NONE;
        }
    }

    public static QuestBackend backend() {
        return backend;
    }

    /** {@code task} happened for {@code players}. */
    public static void complete(QuestTask task, Collection<UUID> players) {
        if (reported != null) reported.computeIfAbsent(task, t -> new HashSet<>())
            .addAll(players);
        try {
            backend.complete(task, players);
        } catch (Throwable t) {
            BeCom.LOG.error("Couldn't complete BeCom quest task {}", task, t);
        }
    }

    /** Starts keeping what each task is reported for, for the self-test. */
    public static void recordForSelfTest() {
        reported = new EnumMap<>(QuestTask.class);
    }

    /** Whether {@code task} was reported for {@code player} since the self-test started keeping them. */
    public static boolean reportedForSelfTest(QuestTask task, UUID player) {
        return reported != null && reported.getOrDefault(task, Set.of())
            .contains(player);
    }

    /** The parts of the quest integration that need BetterQuesting, behind an interface so nothing loads BQ classes. */
    public interface QuestBackend {

        QuestBackend NONE = new QuestBackend() {};

        default void init() {}

        default void serverStarted() {}

        default void tick() {}

        default void serverStopped() {}

        default void complete(QuestTask task, Collection<UUID> players) {}

        /** For the self-test: whether BeCom's questline is in the book right now. */
        default boolean questlinePresent() {
            return false;
        }

        /** For the self-test: removes BeCom's questline and quests, as a DefaultQuests reload would. */
        default void removeQuestlineForTest() {}

        /** For the self-test: runs the periodic check now. */
        default void recheckNow() {}
    }
}
