package com.enn3developer.becom.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ChatComponentTranslation;

import com.enn3developer.becom.logic.call.Call;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.radio.AntennaNetwork;
import com.enn3developer.becom.logic.radio.RadioHops;
import com.enn3developer.becom.logic.radio.RadioModel;
import com.enn3developer.becom.packet.AnalyzerPacket;
import com.enn3developer.becom.selftest.SelfTest;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.RadioService;

/**
 * {@code /becom}: the admin commands (list and hang up calls, set a dimension's satellites, moderate the App Store),
 * the Signal Analyzer's signal breakdown in chat, and in the development environment only, the self-test.
 */
public final class CommandBeCom extends CommandBase {

    private static final int ADMIN_LEVEL = 2;
    private static final int SELFTEST_LEVEL = 4;
    // The most satellites the test command puts over a dimension: far past Max, short of a list that slows a save.
    private static final int MAX_TEST_SATELLITES = 1000;

    @Override
    public String getCommandName() {
        return "becom";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "becom.command.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return ADMIN_LEVEL;
    }

    /** The self-test builds in the world, so it only exists where nobody's world is at stake. */
    public static boolean devEnvironment() {
        Object deobf = Launch.blackboard == null ? null : Launch.blackboard.get("fml.deobfuscatedEnvironment");
        return Boolean.TRUE.equals(deobf);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        BeComServer becom = BeComServer.get();
        if (becom == null || args.length == 0) throw new WrongUsageException("becom.command.usage");
        switch (args[0]) {
            case "calls" -> listCalls(sender, becom);
            case "hangup" -> hangUp(sender, becom, args);
            case "signal" -> signal(sender, becom);
            case "satellites" -> satellites(sender, becom, args);
            case "store" -> store(sender, becom, args);
            case "selftest" -> {
                if (!devEnvironment() || !sender.canCommandSenderUseCommand(SELFTEST_LEVEL, "becom")) {
                    throw new WrongUsageException("becom.command.usage");
                }
                SelfTest.run(becom, sender, args.length > 1 && args[1].equals("apps"));
            }
            default -> throw new WrongUsageException("becom.command.usage");
        }
    }

    private static void listCalls(ICommandSender sender, BeComServer becom) {
        List<Call> calls = new ArrayList<>(
            becom.calls.book()
                .calls());
        if (calls.isEmpty()) {
            sender.addChatMessage(new ChatComponentTranslation("becom.command.calls.none"));
            return;
        }
        for (Call call : calls) {
            StringBuilder who = new StringBuilder();
            for (Call.Leg leg : call.legs()) {
                if (who.length() > 0) who.append(", ");
                who.append(leg.phone());
                UUID player = leg.player();
                EntityPlayerMP mp = becom.calls.onlinePlayer(player);
                if (mp != null) who.append(" (")
                    .append(mp.getCommandSenderName())
                    .append(')');
            }
            sender.addChatMessage(
                new ChatComponentTranslation(
                    "becom.command.calls.entry",
                    call.id(),
                    call.state()
                        .name(),
                    who.toString()));
        }
    }

    private static void hangUp(ICommandSender sender, BeComServer becom, String[] args) {
        if (args.length < 2) throw new WrongUsageException("becom.command.usage");
        long id = parseLong(sender, args[1]);
        Call call = becom.calls.book()
            .call(id);
        if (call == null) {
            sender.addChatMessage(new ChatComponentTranslation("becom.command.hangup.none", id));
            return;
        }
        becom.calls.book()
            .endByAdmin(call);
        sender.addChatMessage(new ChatComponentTranslation("becom.command.hangup.done", id));
    }

    /**
     * Moderates the App Store: lists every app, hidden ones too, and the ones players reported; hides, shows or
     * removes one, or writes out its source.
     */
    private static void store(ICommandSender sender, BeComServer becom, String[] args) {
        com.enn3developer.becom.logic.app.AppStore store = becom.apps.store();
        if (args.length == 1 || args.length == 2 && args[1].equals("list")) {
            if (store.all()
                .isEmpty()) sender.addChatMessage(new ChatComponentTranslation("becom.command.store.none"));
            for (com.enn3developer.becom.logic.app.AppStore.Entry e : store.all()) {
                sender.addChatMessage(
                    new ChatComponentTranslation(
                        e.hidden() ? "becom.command.store.entry_hidden" : "becom.command.store.entry",
                        e.id(),
                        e.name(),
                        e.version(),
                        e.authorName(),
                        e.installs()));
            }
            return;
        }
        if (args.length == 2 && args[1].equals("reports")) {
            if (store.reports()
                .isEmpty()) sender.addChatMessage(new ChatComponentTranslation("becom.command.store.reports_none"));
            for (Map.Entry<String, java.util.Set<java.util.UUID>> report : store.reports()
                .entrySet()) {
                com.enn3developer.becom.logic.app.AppStore.Entry e = store.get(report.getKey());
                if (e == null) continue;
                sender.addChatMessage(
                    new ChatComponentTranslation(
                        "becom.command.store.report",
                        e.id(),
                        e.name(),
                        e.authorName(),
                        report.getValue()
                            .size()));
            }
            return;
        }
        if (args.length != 3) throw new WrongUsageException("becom.command.usage");
        String id = args[2];
        if (args[1].equals("source")) {
            source(sender, store.get(id), id);
            return;
        }
        boolean done = switch (args[1]) {
            case "hide" -> becom.apps.moderate(id, true);
            case "show" -> becom.apps.moderate(id, false);
            case "remove" -> becom.apps.removeFromStore(id);
            default -> throw new WrongUsageException("becom.command.usage");
        };
        sender.addChatMessage(
            new ChatComponentTranslation(done ? "becom.command.store." + args[1] : "becom.command.store.unknown", id));
    }

    /** Writes an app's source to a file in the world's folder, for an operator to read before deciding on it. */
    private static void source(ICommandSender sender, com.enn3developer.becom.logic.app.AppStore.Entry entry,
        String id) {
        if (entry == null || !id.matches("[a-z0-9_]+")) {
            sender.addChatMessage(new ChatComponentTranslation("becom.command.store.unknown", id));
            return;
        }
        java.io.File dir = new java.io.File(
            net.minecraftforge.common.DimensionManager.getCurrentSaveRootDirectory(),
            "becom-store");
        java.io.File file = new java.io.File(dir, id + ".lua");
        try {
            java.nio.file.Files.createDirectories(dir.toPath());
            java.nio.file.Files.write(
                file.toPath(),
                entry.source()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            sender.addChatMessage(new ChatComponentTranslation("becom.command.store.source", id, file.getPath()));
        } catch (java.io.IOException e) {
            sender
                .addChatMessage(new ChatComponentTranslation("becom.command.store.source_failed", id, e.getMessage()));
        }
    }

    /**
     * Sets how many satellites orbit over a dimension, for testing; with no numbers, lists each dimension's.
     */
    private static void satellites(ICommandSender sender, BeComServer becom, String[] args) {
        if (args.length == 1) {
            if (becom.space.counts()
                .isEmpty()) {
                sender.addChatMessage(new ChatComponentTranslation("becom.command.satellites.none"));
            }
            for (Map.Entry<Integer, Integer> entry : becom.space.counts()
                .entrySet()) {
                sender.addChatMessage(
                    new ChatComponentTranslation("becom.command.satellites.entry", entry.getKey(), entry.getValue()));
            }
            return;
        }
        if (args.length != 3) throw new WrongUsageException("becom.command.usage");
        int dimension = parseInt(sender, args[1]);
        int count = parseIntBounded(sender, args[2], 0, MAX_TEST_SATELLITES);
        becom.space.set(dimension, count);
        sender.addChatMessage(new ChatComponentTranslation("becom.command.satellites.set", dimension, count));
    }

    /**
     * The Signal Analyzer's breakdown in chat for radios and for phones, every antenna that reaches the
     * sender's radio, and the satellites' sky hop on both bands where there is one.
     */
    private static void signal(ICommandSender sender, BeComServer becom) {
        if (!(sender instanceof EntityPlayerMP player)) throw new WrongUsageException("becom.command.usage");
        AnalyzerPacket reading = becom.radio.reading(player);
        signal(sender, "becom.analyzer.radios", reading.radio());
        signal(sender, "becom.analyzer.phones", reading.phone());
        RadioHops.End end = RadioService.endOf(player);
        // A player whose position isn't a number has none: nothing reaches them, as the lines above say.
        if (end == null) return;
        AntennaNetwork.Mesh mesh = becom.antennas.mesh(player.dimension);
        double[] access = becom.antennas.access(mesh, player.dimension, end, player.getUniqueID());
        for (int i = 0; i < access.length; i++) {
            if (access[i] == Double.NEGATIVE_INFINITY) continue;
            NodeRef ref = mesh.antennas()
                .get(i)
                .ref();
            sender.addChatMessage(
                new ChatComponentTranslation(
                    "becom.command.signal.antenna",
                    mesh.antennas()
                        .get(i)
                        .tier(),
                    BlockPos.toString(ref.pos()),
                    format(access[i])));
        }
        AnalyzerPacket.Reading radio = reading.radio();
        AnalyzerPacket.Reading phone = reading.phone();
        boolean rains = BeComServer
            .rains(player.worldObj, (int) Math.floor(player.posX), (int) Math.floor(player.posZ));
        double weather = rains ? becom.space.weather(player.dimension) : 0;
        RadioModel.Losses vhf = becom.space.losses(player.dimension, end.depth(), becom.antennas.vhf(), weather);
        RadioModel.Losses uhf = becom.space.losses(player.dimension, end.depth(), becom.antennas.uhf(), weather);
        if (vhf != null && uhf != null && !radio.sky() && !phone.sky()) {
            sender.addChatMessage(
                new ChatComponentTranslation(
                    "becom.command.signal.sky",
                    becom.space.count(player.dimension),
                    format(vhf.snr()),
                    format(uhf.snr())));
        }
    }

    /** One band's breakdown: SNR, level and the serving antenna, the biggest loss, and every term. */
    private static void signal(ICommandSender sender, String bandKey, AnalyzerPacket.Reading reading) {
        ChatComponentTranslation band = new ChatComponentTranslation(bandKey);
        if (!reading.served()) {
            sender.addChatMessage(new ChatComponentTranslation("becom.analyzer.band_none", band));
            return;
        }
        RadioModel.Losses losses = reading.losses();
        ChatComponentTranslation level = new ChatComponentTranslation(
            "becom.level." + reading.level()
                .name()
                .toLowerCase(Locale.ROOT));
        if (reading.sky()) {
            sender.addChatMessage(
                new ChatComponentTranslation(
                    "becom.command.signal.best_sky",
                    band,
                    Math.round(losses.snr()),
                    level,
                    reading.satellites()));
        } else {
            sender.addChatMessage(
                new ChatComponentTranslation(
                    "becom.command.signal.best",
                    band,
                    Math.round(losses.snr()),
                    level,
                    reading.tier(),
                    reading.x(),
                    reading.y(),
                    reading.z()));
        }
        RadioModel.Loss biggest = losses.biggest();
        sender
            .addChatMessage(
                losses.of(biggest) > 0
                    ? new ChatComponentTranslation(
                        "becom.analyzer.biggest",
                        new ChatComponentTranslation(
                            "becom.loss." + biggest.name()
                                .toLowerCase(Locale.ROOT)))
                    : new ChatComponentTranslation("becom.analyzer.biggest_none"));
        if (reading.sky()) {
            sender.addChatMessage(
                new ChatComponentTranslation(
                    "becom.analyzer.sky_terms",
                    format(losses.budget()),
                    format(losses.coverA() + losses.coverB()),
                    format(losses.weather())));
            return;
        }
        sender.addChatMessage(
            new ChatComponentTranslation(
                "becom.analyzer.terms",
                format(losses.budget()),
                format(losses.distance()),
                format(losses.terrain()),
                format(losses.coverA() + losses.coverB()),
                format(losses.weather()),
                format(losses.noise())));
    }

    private static String format(double db) {
        return String.format(Locale.ROOT, "%.1f", db);
    }

    private static long parseLong(ICommandSender sender, String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new WrongUsageException("becom.command.usage");
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 2 && args[0].equals("store")) {
            return getListOfStringsMatchingLastWord(args, "list", "reports", "hide", "show", "remove", "source");
        }
        if (args.length != 1) return null;
        return devEnvironment()
            ? getListOfStringsMatchingLastWord(args, "calls", "hangup", "signal", "satellites", "store", "selftest")
            : getListOfStringsMatchingLastWord(args, "calls", "hangup", "signal", "satellites", "store");
    }
}
