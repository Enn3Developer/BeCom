package com.enn3developer.becom.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

import com.enn3developer.becom.logic.DropoutModel;
import com.enn3developer.becom.logic.Positions;
import com.enn3developer.becom.logic.RepeatReports;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.call.Call;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.video.RearRelay;
import com.enn3developer.becom.logic.video.VideoPlan;
import com.enn3developer.becom.packet.BeComPackets;
import com.enn3developer.becom.packet.RearFramePacket;
import com.enn3developer.becom.packet.VideoEquipmentPacket;
import com.enn3developer.becom.packet.VideoPosePacket;
import com.enn3developer.becom.packet.VideoProfilePacket;
import com.enn3developer.becom.packet.VideoStatePacket;
import com.enn3developer.becom.quest.BeComQuests;
import com.enn3developer.becom.quest.QuestTask;
import com.mojang.authlib.properties.Property;

/**
 * Video calls on the server (DESIGN.md 10.1, 14): who sees whom and at which level, front-camera poses taken from the
 * players about ten times a second and sent to those who see them, and rear-camera pictures relayed as their client
 * sent them. Both are dropped per viewer by level and by the dropout model, the same burst chain voice uses; the
 * server never decodes a picture. Server thread only.
 */
public final class VideoService {

    private static final int TICKS_PER_SECOND = 20;
    private static final long MILLIS_PER_SECOND = 1000L;
    // A viewer of live video is reported for the Video call task again after this long, as radio contacts are, so
    // seeing video while the quest is still locked isn't used up for good.
    private static final long REPORT_AGAIN_MILLIS = 60 * MILLIS_PER_SECOND;
    // Armor slots and the held item, in EntityLivingBase's order: held item, boots, leggings, chestplate, helmet.
    private static final int EQUIPMENT_SLOTS = VideoEquipmentPacket.SLOTS;

    private final CallService calls;
    private final DropoutModel dropouts;
    private final Random random = new Random();
    private final int poseTicks;
    private final int framesPerPose;
    private final RearRelay rear;

    private final Map<UUID, VideoPlan.Cameras> cameras = new HashMap<>();
    private final Map<UUID, Viewer> viewers = new HashMap<>();
    private final Map<UUID, Source> sources = new HashMap<>();
    private final Map<Pair, Link> links = new HashMap<>();
    private final RepeatReports<UUID> videoReports = new RepeatReports<>(REPORT_AGAIN_MILLIS);
    private VideoPlan plan = VideoPlan.EMPTY;
    private long ticks;

    /** What one viewer was last told: the state, and whose profiles they have. */
    private static final class Viewer {

        VideoStatePacket.State sent = VideoStatePacket.State.NONE;
        final Set<UUID> profiles = new HashSet<>();
    }

    /** One person seen in a video call: their pose count, where they were, and what they held and wore. */
    private static final class Source {

        int seq;
        double lastX = Double.NaN;
        double lastZ = Double.NaN;
        ItemStack[] equipment = new ItemStack[EQUIPMENT_SLOTS];
        int equipmentVersion;
    }

    /** One person and one viewer: their poses' loss chain, and what has reached the viewer. */
    private static final class Link {

        final DropoutModel.Chain poses = new DropoutModel.Chain();
        int equipmentSent = -1;
        int posesDelivered;
    }

    // How many times each person's profile went to each viewer, kept only while the self-test asks.
    private Map<Pair, Integer> profilesSent;

    private record Pair(UUID source, UUID viewer) {}

    public VideoService(CallService calls, Tunables tunables) {
        this.calls = calls;
        this.dropouts = tunables.dropoutModel(Tunables.FRAME_MILLIS);
        this.poseTicks = Math.max(1, Math.round((float) TICKS_PER_SECOND / tunables.videoPoseRate()));
        this.framesPerPose = Math
            .max(1, Math.round((float) MILLIS_PER_SECOND * poseTicks / TICKS_PER_SECOND / Tunables.FRAME_MILLIS));
        this.rear = new RearRelay(tunables, random::nextDouble);
    }

    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    // region Cameras

    /** {@code player}'s camera switches: the front starts on, the rear off, at every call (DESIGN.md 10.1). */
    public VideoPlan.Cameras camerasOf(UUID player) {
        return cameras.getOrDefault(player, VideoPlan.Cameras.START);
    }

    /** Switches {@code player}'s front camera, during a call. */
    public void setFront(UUID player, boolean on) {
        if (calls.book()
            .callOf(player) == null) return;
        cameras.put(player, new VideoPlan.Cameras(on, camerasOf(player).rear()));
        update();
    }

    /** Switches {@code player}'s rear camera, during a call. */
    public void setRear(UUID player, boolean on) {
        if (calls.book()
            .callOf(player) == null) return;
        cameras.put(player, new VideoPlan.Cameras(camerasOf(player).front(), on));
        update();
    }

    // endregion

    // region Plan and state

    /** Who sees whom right now. */
    public VideoPlan plan() {
        return plan;
    }

    public void tick() {
        ticks++;
        if (ticks % poseTicks != 0) return;
        update();
        sendPoses();
        reportVideo();
    }

    /** Works out who sees whom from every call, and tells each player whose view changed. */
    private void update() {
        List<VideoPlan> plans = new ArrayList<>();
        for (Call call : calls.book()
            .calls()) plans.add(VideoPlan.of(call, calls::kindOf, this::camerasOf));
        plan = VideoPlan.merge(plans);
        // Out of a call, the switches go back to how the next call starts.
        cameras.keySet()
            .removeIf(
                player -> calls.book()
                    .callOf(player) == null);
        Set<UUID> current = new HashSet<>();
        for (UUID viewer : plan.viewers()) {
            current.add(viewer);
            sync(viewer);
        }
        for (UUID gone : new ArrayList<>(viewers.keySet())) {
            if (current.contains(gone)) continue;
            viewers.remove(gone);
            send(gone, new VideoStatePacket(VideoStatePacket.State.NONE));
        }
        links.keySet()
            .removeIf(pair -> !seen(pair.source(), pair.viewer()));
        rear.keep(plan);
        sources.keySet()
            .removeIf(
                source -> !links.keySet()
                    .stream()
                    .anyMatch(
                        pair -> pair.source()
                            .equals(source)));
    }

    private boolean seen(UUID source, UUID viewer) {
        for (VideoPlan.Tile tile : plan.tilesOf(viewer)) if (tile.source()
            .equals(source)) return true;
        return false;
    }

    /** Sends {@code viewer} their state if it changed, and the profiles of anyone new they see. */
    private void sync(UUID viewer) {
        Viewer known = viewers.computeIfAbsent(viewer, v -> new Viewer());
        // Their client lets go of a profile as soon as its person is out of sight, so one coming back needs it again.
        Set<UUID> seen = new HashSet<>();
        for (VideoPlan.Tile tile : plan.tilesOf(viewer)) seen.add(tile.source());
        known.profiles.retainAll(seen);
        List<VideoStatePacket.Tile> tiles = new ArrayList<>();
        for (VideoPlan.Tile tile : plan.tilesOf(viewer)) {
            tiles.add(
                new VideoStatePacket.Tile(
                    tile.source(),
                    calls.nameOf(tile.source(), ""),
                    tile.level(),
                    tile.front(),
                    tile.rear()));
            links.computeIfAbsent(new Pair(tile.source(), viewer), p -> new Link());
            if (known.profiles.add(tile.source())) sendProfile(viewer, tile.source());
        }
        VideoPlan.Cameras own = camerasOf(viewer);
        SignalLevel rearSend = plan.rearSendOf(viewer);
        VideoStatePacket.State state = new VideoStatePacket.State(
            own.front(),
            own.rear(),
            rearSend == null ? SignalLevel.NONE : rearSend,
            tiles,
            screenOf(viewer));
        if (state.equals(known.sent)) return;
        known.sent = state;
        send(viewer, new VideoStatePacket(state));
    }

    /** The Videophone {@code viewer} is in the room of, so their client draws the call on its screen. */
    private VideoStatePacket.Screen screenOf(UUID viewer) {
        Call call = calls.book()
            .callOf(viewer);
        Call.Leg leg = call == null ? null : call.legOf(viewer);
        if (leg == null || calls.kindOf(leg.phone()) != PhoneKind.VIDEOPHONE) return null;
        CallService.Videophone phone = calls.videophone(leg.phone());
        return phone == null ? null
            : new VideoStatePacket.Screen(phone.dimension(), phone.x(), phone.y(), phone.z(), phone.facing());
    }

    private void sendProfile(UUID viewer, UUID source) {
        if (profilesSent != null) profilesSent.merge(new Pair(source, viewer), 1, Integer::sum);
        EntityPlayer player = calls.playerOrPresent(source);
        String name = calls.nameOf(source, "");
        String textures = "";
        String signature = "";
        if (player != null) {
            for (Property property : player.getGameProfile()
                .getProperties()
                .get("textures")) {
                textures = property.getValue();
                signature = property.getSignature();
                break;
            }
        }
        send(viewer, new VideoProfilePacket(source, name, textures, signature));
    }

    // endregion

    // region Front camera

    /**
     * One round of front-camera poses: each person seen live gets their pose taken once, and each viewer gets those
     * that survive their dropout chain, after the person's equipment if it changed since they last got it.
     */
    private void sendPoses() {
        Map<UUID, VideoPosePacket.Pose> taken = new HashMap<>();
        for (UUID viewer : plan.viewers()) {
            List<VideoPosePacket.Pose> out = new ArrayList<>();
            for (VideoPlan.Tile tile : plan.tilesOf(viewer)) {
                if (!tile.live()) continue;
                UUID source = tile.source();
                VideoPosePacket.Pose pose = taken.computeIfAbsent(source, this::takePose);
                if (pose == null) continue;
                Link link = links.computeIfAbsent(new Pair(source, viewer), p -> new Link());
                double loss = dropouts.lossRate(snr(source, viewer));
                if (link.poses.dropAfter(
                    framesPerPose,
                    dropouts.enterBadChance(loss),
                    dropouts.leaveBadChance(),
                    random::nextDouble)) continue;
                Source state = sources.get(source);
                if (state != null && link.equipmentSent != state.equipmentVersion) {
                    link.equipmentSent = state.equipmentVersion;
                    send(viewer, new VideoEquipmentPacket(source, state.equipment.clone()));
                }
                out.add(pose);
                link.posesDelivered++;
            }
            if (out.isEmpty()) continue;
            send(viewer, new VideoPosePacket(out));
        }
    }

    /** Those who get live video from someone in their call complete the Video call task (DESIGN.md 15.5). */
    private void reportVideo() {
        long now = now();
        List<UUID> seeing = new ArrayList<>();
        for (UUID viewer : plan.viewers()) {
            if (plan.seesLive(viewer) && videoReports.due(viewer, now)) seeing.add(viewer);
        }
        if (!seeing.isEmpty()) BeComQuests.complete(QuestTask.VIDEO_CALL, seeing);
        videoReports.sweep(now);
    }

    /** The SNR the viewer gets the source at, for the dropout model; their call's weakest hop. */
    private double snr(UUID source, UUID viewer) {
        Call call = calls.book()
            .callOf(viewer);
        return call == null ? Double.NEGATIVE_INFINITY : call.snr(source, viewer);
    }

    /** {@code id}'s pose now, and whether their equipment changed; null when they aren't anywhere to be seen. */
    private VideoPosePacket.Pose takePose(UUID id) {
        EntityPlayer player = calls.playerOrPresent(id);
        if (player == null) return null;
        Source source = sources.computeIfAbsent(id, s -> new Source());
        source.seq++;
        // A player whose position isn't a number has none: they stand still, and the next pose starts afresh.
        boolean placed = Positions.finite(player.posX, player.posY, player.posZ);
        double speed = !placed || Double.isNaN(source.lastX) ? 0
            : Math.hypot(player.posX - source.lastX, player.posZ - source.lastZ) / poseTicks;
        source.lastX = placed ? player.posX : Double.NaN;
        source.lastZ = placed ? player.posZ : Double.NaN;
        boolean changed = false;
        for (int slot = 0; slot < EQUIPMENT_SLOTS; slot++) {
            ItemStack now = player.getEquipmentInSlot(slot);
            if (!ItemStack.areItemStacksEqual(now, source.equipment[slot])) {
                source.equipment[slot] = now == null ? null : now.copy();
                changed = true;
            }
        }
        if (changed) source.equipmentVersion++;
        return new VideoPosePacket.Pose(
            id,
            source.seq,
            MathHelper.wrapAngleTo180_float(player.rotationYawHead - player.renderYawOffset),
            player.rotationPitch,
            player.swingProgress,
            player.isSneaking(),
            (float) speed);
    }

    // endregion

    // region Rear camera

    /**
     * A rear-camera picture from {@code sender}'s client: kept within the per-client size and rate caps (DESIGN.md 14:
     * client input), then sent on to whoever their rear camera reaches, as often as that viewer's level allows and
     * when the dropout chain lets it through.
     */
    public void rearFrame(EntityPlayerMP sender, byte[] jpeg) {
        UUID id = sender.getUniqueID();
        for (UUID viewer : rear.relay(id, jpeg.length, now(), plan, this::snr)) {
            send(viewer, new RearFramePacket(id, jpeg));
        }
    }

    // endregion

    // region Lifecycle

    public void stop() {
        cameras.clear();
        viewers.clear();
        sources.clear();
        links.clear();
        profilesSent = null;
        rear.clear();
        videoReports.clear();
        plan = VideoPlan.EMPTY;
    }

    /** How many of {@code source}'s poses have gone to {@code viewer}; for the self-test. */
    public int posesDelivered(UUID source, UUID viewer) {
        Link link = links.get(new Pair(source, viewer));
        return link == null ? 0 : link.posesDelivered;
    }

    /** From now on, counts each profile sent, for the self-test. */
    public void logForSelfTest() {
        if (profilesSent == null) profilesSent = new HashMap<>();
    }

    /** How many times {@code source}'s profile has gone to {@code viewer} since {@link #logForSelfTest}. */
    public int profilesSent(UUID source, UUID viewer) {
        return profilesSent == null ? 0 : profilesSent.getOrDefault(new Pair(source, viewer), 0);
    }

    /** How many of {@code source}'s rear pictures have gone to {@code viewer}; for the self-test. */
    public int rearDelivered(UUID source, UUID viewer) {
        return rear.delivered(source, viewer);
    }

    // endregion

    private void send(UUID player, cpw.mods.fml.common.network.simpleimpl.IMessage message) {
        EntityPlayerMP mp = calls.onlinePlayer(player);
        if (mp != null) BeComPackets.send(message, mp);
    }
}
