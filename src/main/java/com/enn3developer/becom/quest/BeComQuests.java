package com.enn3developer.becom.quest;

import java.util.Collection;
import java.util.UUID;

import com.enn3developer.becom.BeCom;

import cpw.mods.fml.common.Loader;

/**
 * Where BeCom reports what its quest tasks wait for. BetterQuesting is optional: without it,
 * every report is dropped. Server thread only.
 */
public final class BeComQuests {

    public static final String BQ_MODID = "betterquesting";

    private static QuestBackend backend = QuestBackend.NONE;

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
            // Anything going wrong with the quest book leaves BeCom running without it.
            BeCom.LOG.error("BetterQuesting integration failed to start; BeCom carries on without its quests", t);
            backend = QuestBackend.NONE;
        }
    }

    public static QuestBackend backend() {
        return backend;
    }

    /** {@code task} happened for {@code players}. */
    public static void complete(QuestTask task, Collection<UUID> players) {
        try {
            backend.complete(task, players);
        } catch (Throwable t) {
            BeCom.LOG.error("Couldn't complete BeCom quest task {}", task, t);
        }
    }

    /** The parts of the quest integration that need BetterQuesting, behind an interface so nothing loads BQ classes. */
    public interface QuestBackend {

        QuestBackend NONE = new QuestBackend() {};

        default void init() {}

        default void serverStarted() {}

        default void tick() {}

        default void serverStopped() {}

        default void complete(QuestTask task, Collection<UUID> players) {}
    }
}
