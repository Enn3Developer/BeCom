package com.enn3developer.becom.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import com.enn3developer.becom.logic.radio.RadioModel;
import com.enn3developer.becom.packet.AnalyzerPacket;
import com.enn3developer.becom.selftest.SelfTest;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.RadioService;

/**
 * {@code /becom}: the admin commands from DESIGN.md 12.3 (list and hang up calls), the signal breakdown of DESIGN.md
 * 6.6, and in the development environment only, the self-test (DESIGN.md 16.3).
 */
public final class CommandBeCom extends CommandBase {

    private static final int ADMIN_LEVEL = 2;
    private static final int SELFTEST_LEVEL = 4;

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
            case "selftest" -> {
                if (!devEnvironment() || !sender.canCommandSenderUseCommand(SELFTEST_LEVEL, "becom")) {
                    throw new WrongUsageException("becom.command.usage");
                }
                SelfTest.run(becom, sender);
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

    /** DESIGN.md 6.6: the Signal Analyzer's breakdown in chat, and every antenna that reaches the sender. */
    private static void signal(ICommandSender sender, BeComServer becom) {
        if (!(sender instanceof EntityPlayerMP player)) throw new WrongUsageException("becom.command.usage");
        AnalyzerPacket reading = becom.radio.reading(player);
        if (!reading.served()) {
            sender.addChatMessage(new ChatComponentTranslation("becom.analyzer.none"));
            return;
        }
        RadioModel.Losses losses = reading.losses();
        sender.addChatMessage(
            new ChatComponentTranslation(
                "becom.command.signal.best",
                Math.round(losses.snr()),
                new ChatComponentTranslation(
                    "becom.level." + reading.level()
                        .name()
                        .toLowerCase(Locale.ROOT)),
                reading.tier(),
                reading.x(),
                reading.y(),
                reading.z()));
        sender.addChatMessage(
            new ChatComponentTranslation(
                "becom.analyzer.biggest",
                new ChatComponentTranslation(
                    "becom.loss." + losses.biggest()
                        .name()
                        .toLowerCase(Locale.ROOT))));
        sender.addChatMessage(
            new ChatComponentTranslation(
                "becom.analyzer.terms",
                format(losses.budget()),
                format(losses.distance()),
                format(losses.terrain()),
                format(losses.coverA() + losses.coverB()),
                format(losses.weather()),
                format(losses.noise())));
        AntennaNetwork.Mesh mesh = becom.antennas.mesh(player.dimension);
        double[] access = becom.antennas
            .access(mesh, player.dimension, RadioService.endOf(player), player.getUniqueID());
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
        if (args.length != 1) return null;
        return devEnvironment() ? getListOfStringsMatchingLastWord(args, "calls", "hangup", "signal", "selftest")
            : getListOfStringsMatchingLastWord(args, "calls", "hangup", "signal");
    }
}
