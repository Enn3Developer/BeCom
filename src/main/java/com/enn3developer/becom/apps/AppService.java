package com.enn3developer.becom.apps;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.data.AppsSavedData;
import com.enn3developer.becom.item.ItemCarriedPhone;
import com.enn3developer.becom.logic.LevelScale;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.app.AppLimits;
import com.enn3developer.becom.logic.app.AppManifest;
import com.enn3developer.becom.logic.app.AppStore;
import com.enn3developer.becom.logic.app.AppTree;
import com.enn3developer.becom.logic.app.DataLink;
import com.enn3developer.becom.logic.app.PhoneApps;
import com.enn3developer.becom.logic.call.CallBook;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.CallGraph;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.server.CallService;
import com.enn3developer.becom.server.CarriedPhones;
import com.enn3developer.becom.server.MeAccess;
import com.enn3developer.becom.server.MeGrids;
import com.enn3developer.becom.server.NetworkService;
import com.enn3developer.becom.server.Ownership;
import com.enn3developer.becom.server.PhoneHandle;
import com.enn3developer.becom.server.PhoneService;
import com.enn3developer.becom.server.RadioService;

/**
 * Phone apps on the server (how phone apps work): each carried phone's OpenComputers machine, started while its app
 * is on screen or a background app has data waiting, and stopped when it's put away; what the open app shows; the
 * data apps and computers send each other by number, at the route's rate; installs offered from computers; and the
 * App Store.
 */
public final class AppService {

    private static final int TICKS_PER_SECOND = 20;
    private static final long TICKS_PER_DAY = 24_000;
    // A machine that hasn't booted after this long gives up: the phone shows it crashed.
    private static final long BOOT_TICKS = 15 * TICKS_PER_SECOND;
    // How often the apps' daily stats are saved, when nothing else saves them sooner.
    private static final long STATS_SAVE_TICKS = 60 * TICKS_PER_SECOND;
    private static final int MAX_QUEUED = 32;
    private static final int MAX_IN_FLIGHT = 4096;
    // How far behind a sender's queue may fall before it's told the network is busy.
    private static final long MAX_BACKLOG_TICKS = 60 * TICKS_PER_SECOND;

    /** How the open app stands: loading, showing its widgets, unable to run, or crashed. */
    public enum State {
        LOADING,
        RUNNING,
        UNREACHABLE,
        CRASHED
    }

    /** What the app open on a phone shows. */
    public record Screen(String id, State state, List<AppTree.Widget> widgets, String error) {}

    /**
     * An app a computer offers a phone, waiting for its holder: to install, or, with {@code publish}, to put in the App
     * Store under the holder's own name. {@code id} ties the holder's answer to this offer and no later one.
     */
    public record Offer(long id, int computer, String source, AppManifest manifest, String author, String where,
        long until, boolean publish) {}

    /** The last notification on a phone. */
    public record Notice(String app, String title, String text) {}

    /** A sender's stream until {@code until}: {@code rate} KB/s over {@code route}'s links. */
    private record Sending(long until, int rate, CallGraph.DataRoute route) {

        int on(Object link) {
            return route.links()
                .contains(link) ? rate : 0;
        }
    }

    private record Delivery(long due, long order, int to, String app, int from, byte[] data) {}

    /**
     * The best way from a phone or computer to the App Store: the mirror and the route to it, and the KB/s that route
     * has free.
     */
    public record Reach(int mirror, int rate, CallGraph.DataRoute route) {}

    /** A store app on its way to a phone: from {@code mirror}, started at {@code from}, done at {@code until}. */
    private record Download(String id, int mirror, long from, long until, int rate, CallGraph.DataRoute route) {

        int on(Object link) {
            return route.links()
                .contains(link) ? rate : 0;
        }

        double progress(long now) {
            return until <= from ? 1 : Math.max(0, Math.min(1, (now - from) / (double) (until - from)));
        }
    }

    /**
     * A working mirror as it last said so: when, whose it is, whether it serves its owner's team alone, and the rate
     * it serves at, or 0 for its cable's.
     */
    private record Mirror(long heard, UUID owner, boolean locked, int rate) {}

    private final MinecraftServer server;
    private final MeGrids meGrids;
    private final Tunables t;
    private final LevelScale levels;
    private final PhoneService phones;
    private final CarriedPhones carried;
    private final CallService calls;
    private final NetworkService network;
    private AppsSavedData data = new AppsSavedData(AppsSavedData.NAME);

    private final Map<Integer, PhoneMachine> machines = new HashMap<>();
    private final Map<Integer, Long> started = new HashMap<>();
    private final Map<Integer, String> foreground = new HashMap<>();
    private final Map<Integer, Screen> screens = new HashMap<>();
    private final Map<Integer, Long> activeUntil = new HashMap<>();
    private final Map<Integer, Long> lastWake = new HashMap<>();
    private final Map<Integer, Deque<Object[]>> queued = new HashMap<>();
    private final Map<Integer, Deque<Object[]>> waiting = new HashMap<>();
    private final PriorityQueue<Delivery> deliveries = new PriorityQueue<>(
        (a, b) -> a.due != b.due ? Long.compare(a.due, b.due) : Long.compare(a.order, b.order));
    private final Map<Integer, BecomCard> computers = new HashMap<>();
    private final Map<Integer, List<CallGraph.Entry>> computerEntries = new HashMap<>();
    private final Map<NodeRef, Integer> wired = new HashMap<>();
    private final Map<Integer, Offer> offers = new HashMap<>();
    private final Map<Integer, Notice> notices = new HashMap<>();
    // The apps loaded on each running machine, which share what it draws from the battery.
    private final Map<Integer, Set<String>> awake = new HashMap<>();
    // Each sender's stream: its messages go one after another, at the rate its route had free when it last sent.
    private final Map<Integer, Sending> sending = new HashMap<>();
    // The App Store's mirrors heard working, by number; each phone's download, one at a time; and each phone's or
    // computer's reach, worked out once a second.
    private final Map<Integer, Mirror> mirrors = new HashMap<>();
    private final Map<Integer, Download> downloads = new HashMap<>();
    private final Map<Integer, Reach> reaches = new HashMap<>();
    private final Map<Integer, Long> reachedAt = new HashMap<>();
    // Downloads each mirror has served on the day in [0], as [day, count].
    private final Map<Integer, long[]> served = new HashMap<>();
    private static final long MIRROR_LOST_TICKS = 2 * TICKS_PER_SECOND;
    private boolean statsChanged;
    private final Map<Integer, Long> lastChat = new HashMap<>();
    private static final long CHAT_EVERY_TICKS = 5 * TICKS_PER_SECOND;
    private long offerIds;
    private long ticks;
    private long prunedOn = -1;
    private long order;
    private long version;

    public AppService(MinecraftServer server, Tunables t, PhoneService phones, CarriedPhones carried, CallService calls,
        NetworkService network, MeGrids meGrids) {
        this.server = server;
        this.meGrids = meGrids;
        this.t = t;
        this.levels = t.levelScale();
        this.phones = phones;
        this.carried = carried;
        this.calls = calls;
        this.network = network;
        carried.setApps(machines::containsKey, this::billed);
        calls.setComputerEntries(n -> computerEntries.getOrDefault(n, List.of()));
    }

    public void attach(AppsSavedData data) {
        this.data = data;
        data.setLimits(AppLimits.of(t));
    }

    /** Goes up whenever anything a phone screen shows of apps changes. */
    public long version() {
        return version + data.store()
            .version();
    }

    private void changed() {
        version++;
    }

    // Of the world's total time, which /time set doesn't turn back: for forgetting computers.
    private long totalDay() {
        return server.worldServers.length == 0 ? 0 : server.worldServers[0].getTotalWorldTime() / TICKS_PER_DAY;
    }

    private long worldTime() {
        return server.worldServers.length == 0 ? 0 : server.worldServers[0].getTotalWorldTime();
    }

    // When each phone's app last got data, in the world's total time, which clients have too: keyed number:id.
    private final Map<String, Long> lastData = new HashMap<>();

    /** When phone {@code number}'s app {@code id} last got data, in the world's total time; 0 for never. */
    public long lastData(int number, String id) {
        return lastData.getOrDefault(number + ":" + id, 0L);
    }

    // How much of its own storage each app uses, read from disk now and then: keyed number:id, [bytes, tick read].
    private final Map<String, long[]> storageUsed = new HashMap<>();
    private static final long STORAGE_READ_TICKS = 5 * TICKS_PER_SECOND;

    /** The bytes phone {@code number}'s app {@code id} keeps in its own storage, as of a few seconds ago. */
    public long storageUsed(int number, String id) {
        String key = number + ":" + id;
        long[] used = storageUsed.get(key);
        if (used == null || ticks - used[1] > STORAGE_READ_TICKS) {
            used = new long[] { PhoneMachine.filesSize(number, id), ticks };
            storageUsed.put(key, used);
        }
        return used[0];
    }

    /** The bytes each app may keep in its own storage. */
    public int storageLimit() {
        return t.appStorageKB() * 1024;
    }

    /** What the app on screen sees through the phone's camera: the block the holder looks at, or null. */
    Map<String, Object> camera(PhoneMachine machine, String id) {
        if (!id.equals(foreground.get(machine.number))) return null;
        if (permit(machine.number, id, AppManifest.Use.CAMERA) != null) return null;
        EntityPlayerMP holder = holderOf(machine.number);
        return holder == null ? null : PhoneCamera.look(holder);
    }

    private long day() {
        return server.worldServers.length == 0 ? 0 : server.worldServers[0].getWorldTime() / TICKS_PER_DAY;
    }

    // region Queries for the phone screen

    public List<PhoneApps.App> installed(int number) {
        PhoneApps apps = data.appsIfAny(number);
        return apps == null ? List.of() : apps.all();
    }

    public PhoneApps.App app(int number, String id) {
        PhoneApps apps = data.appsIfAny(number);
        return apps == null ? null : apps.get(id);
    }

    public Screen screen(int number) {
        return screens.get(number);
    }

    public Offer offer(int number) {
        Offer offer = offers.get(number);
        return offer == null || offer.until() < ticks ? null : offer;
    }

    public Notice notice(int number) {
        return notices.get(number);
    }

    public AppStore store() {
        return data.store();
    }

    /** Hides an app from the store for everyone, or shows it again; false when there's no such app. */
    public boolean moderate(String id, boolean hidden) {
        boolean done = data.store()
            .setHidden(id, hidden);
        if (done) data.markDirty();
        return done;
    }

    /** Takes an app out of the store for good: its id can't be published again. Phones that have it keep it. */
    public boolean removeFromStore(String id) {
        boolean done = data.store()
            .remove(id);
        if (done) data.markDirty();
        return done;
    }

    /** Which store tab a phone shows: the most installed, the newest, or what it has installed. */
    public enum StoreTab {

        POPULAR,
        NEW,
        INSTALLED;

        private static final StoreTab[] VALUES = values();

        public static StoreTab byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : POPULAR;
        }
    }

    /** What a phone's App Store page shows: what it searches for, the tab, and the page of results. */
    public record StoreQuery(String text, StoreTab tab, int page) {

        public static final StoreQuery NONE = new StoreQuery("", StoreTab.POPULAR, 0);
    }

    private final Map<Integer, StoreQuery> queries = new HashMap<>();

    public StoreQuery query(int number) {
        return queries.getOrDefault(number, StoreQuery.NONE);
    }

    public void setQuery(int number, String text, StoreTab tab, int page) {
        queries.put(number, new StoreQuery(AppTree.cap(text == null ? "" : text, 32), tab, Math.max(0, page)));
        changed();
    }

    /** Whether the owner of phone {@code number} sees {@code entry}: a team's app is for its author's team. */
    public boolean sees(int number, AppStore.Entry entry) {
        if (!entry.team()) return true;
        PhoneRecord record = phones.record(number);
        return record != null && com.enn3developer.becom.server.Ownership.sameTeam(entry.author(), record.owner());
    }

    /** The holder reports a store app to the operators; once each. */
    public void report(EntityPlayerMP player, int number, String id) {
        AppStore.Entry entry = data.store()
            .get(id);
        if (entry == null || !sees(number, entry)) return;
        if (!data.store()
            .report(id, player.getUniqueID())) return;
        data.markDirty();
        BeCom.LOG.info("{} reported the store app {} by {}", player.getCommandSenderName(), id, entry.authorName());
        tell(player, "becom.apps.reported", entry.name());
    }

    // endregion

    // region The phone screen's actions

    /** The holder opened an app: it comes to the foreground, and the machine starts if it isn't running. */
    public void open(EntityPlayerMP player, int number, String id) {
        if (app(number, id) == null) return;
        foreground.put(number, id);
        awake.computeIfAbsent(number, n -> new HashSet<>())
            .add(id);
        screens.put(number, new Screen(id, State.LOADING, List.of(), ""));
        changed();
        PhoneMachine machine = machines.get(number);
        if (machine == null) machine = start(number, player);
        if (machine == null) {
            screens.put(number, new Screen(id, State.UNREACHABLE, List.of(), ""));
            return;
        }
        push(number, "becom_open", id);
    }

    /** The holder left the app, or closed the phone's screen. */
    public void close(int number) {
        if (foreground.remove(number) == null) return;
        screens.remove(number);
        push(number, "becom_close");
        changed();
    }

    /** A toggle, a button or an input in the open app was used. */
    public void event(int number, String id, String widget, String value) {
        if (!id.equals(foreground.get(number))) return;
        push(number, "becom_event", id, AppTree.cap(widget, AppTree.MAX_ID), AppTree.cap(value, AppTree.MAX_TEXT * 4));
    }

    /** Switches what an installed app may use on or off, from its settings page. */
    public void setAllowed(int number, String id, AppManifest.Use use, boolean on) {
        PhoneApps apps = data.appsIfAny(number);
        PhoneApps.App app = apps == null ? null : apps.get(id);
        if (app == null || use == null) return;
        apps.replace(app.withAllowed(use, on));
        data.markDirty();
        changed();
    }

    public void setBackground(int number, String id, boolean on) {
        PhoneApps apps = data.appsIfAny(number);
        PhoneApps.App app = apps == null ? null : apps.get(id);
        if (app == null) return;
        apps.replace(app.withBackground(on));
        data.markDirty();
        changed();
    }

    public void uninstall(int number, String id) {
        PhoneApps apps = data.appsIfAny(number);
        if (apps == null || !apps.uninstall(id)) return;
        if (id.equals(foreground.get(number))) close(number);
        push(number, "becom_unload", id);
        Set<String> loaded = awake.get(number);
        if (loaded != null) loaded.remove(id);
        PhoneMachine.forgetFiles(number, id);
        data.markDirty();
        changed();
    }

    /**
     * Downloads an app from the App Store, through the mirror the phone reaches best, at its route's rate: it installs
     * when it's all there.
     */
    public void installFromStore(EntityPlayerMP player, int number, String id) {
        AppStore.Entry entry = data.store()
            .get(id);
        if (entry == null || entry.hidden() && !player.mcServer.getConfigurationManager()
            .func_152596_g(player.getGameProfile())) return;
        if (!sees(number, entry)) return;
        if (downloads.containsKey(number)) {
            tell(player, "becom.apps.downloading");
            return;
        }
        Reach reach = reachNow(number, ownerOf(number));
        if (reach == null) {
            tell(player, "becom.apps.no_mirror");
            return;
        }
        int ticksTaken = Math.max(1, DataLink.ticks(entry.size(), reach.rate()));
        downloads.put(number, new Download(id, reach.mirror(), ticks, ticks + ticksTaken, reach.rate(), reach.route()));
        long[] count = served.computeIfAbsent(reach.mirror(), m -> new long[2]);
        if (count[0] != day()) {
            count[0] = day();
            count[1] = 0;
        }
        count[1]++;
        changed();
    }

    /** A download is all there: it installs, as it is in the store now. */
    private void finishDownload(int number, String id) {
        EntityPlayerMP player = holderOf(number);
        AppStore.Entry entry = data.store()
            .get(id);
        if (player == null || entry == null || !sees(number, entry)) return;
        // Hidden while it came down: only an operator still gets it, as only they could start it.
        if (entry.hidden() && !player.mcServer.getConfigurationManager()
            .func_152596_g(player.getGameProfile())) return;
        PhoneApps.Installed result = data.apps(number)
            .install(entry.source(), entry.authorName(), PhoneApps.Origin.STORE);
        tell(
            player,
            "becom.apps.installed." + result.name()
                .toLowerCase(Locale.ROOT),
            entry.name(),
            t.appsPerPhone());
        // Only a first install counts for the store's ranking, not installing the same app again.
        if (result == PhoneApps.Installed.NEW) data.store()
            .installed(id);
        if (result == PhoneApps.Installed.NEW || result == PhoneApps.Installed.UPDATED)
            push(number, "becom_unload", id);
        data.markDirty();
        changed();
    }

    /** The holder answered a computer's offer: the one shown as {@code id}, which may have run out since. */
    public void answerOffer(EntityPlayerMP player, int number, long id, boolean accept, boolean team) {
        Offer offer = offers.get(number);
        if (offer == null || offer.id() != id) return;
        offers.remove(number);
        changed();
        BecomCard computer = computers.get(offer.computer());
        String signal = offer.publish() ? "becom_publish" : "becom_install";
        if (!accept || offer.until() < ticks) {
            if (computer != null) computer.signal(signal, number, "declined");
            return;
        }
        if (offer.publish()) {
            // The holder publishes as themselves: a computer can ask, but only the player puts their name on it.
            AppStore.Published published = data.store()
                .publish(
                    player.getUniqueID(),
                    player.getCommandSenderName(),
                    offer.source(),
                    System.currentTimeMillis(),
                    team);
            data.markDirty();
            tell(
                player,
                "becom.apps.published." + published.name()
                    .toLowerCase(Locale.ROOT),
                offer.manifest()
                    .name());
            if (computer != null) computer.signal(
                signal,
                number,
                published.name()
                    .toLowerCase(Locale.ROOT));
            return;
        }
        PhoneApps.Installed result = data.apps(number)
            .install(offer.source(), offer.author(), new PhoneApps.Origin(false, offer.where()));
        data.markDirty();
        push(
            number,
            "becom_unload",
            offer.manifest()
                .id());
        tell(
            player,
            "becom.apps.installed." + result.name()
                .toLowerCase(Locale.ROOT),
            offer.manifest()
                .name(),
            t.appsPerPhone());
        if (computer != null) computer.signal(
            "becom_install",
            number,
            result.name()
                .toLowerCase(Locale.ROOT));
    }

    // endregion

    // region Called by phones' BeCom OS

    void ready(PhoneMachine machine) {
        machine.setReady();
        Deque<Object[]> signals = queued.remove(machine.number);
        if (signals != null) for (Object[] signal : signals) send(machine, signal);
    }

    /** A phone's or a computer's signal, as Lua names it. */
    public String signal(int number) {
        return levelOf(number).name()
            .toLowerCase(Locale.ROOT);
    }

    String source(int number, String id) {
        PhoneApps.App app = app(number, id);
        return app == null ? null : app.source();
    }

    void show(int number, String id, List<AppTree.Widget> widgets) {
        if (!id.equals(foreground.get(number))) return;
        Screen before = screens.get(number);
        Screen now = new Screen(id, State.RUNNING, widgets, "");
        if (!now.equals(before)) {
            screens.put(number, now);
            changed();
        }
    }

    void crashed(int number, String id, String reason) {
        BeCom.LOG.debug("Phone {}'s app {} crashed: {}", number, id, reason);
        if (id.equals(foreground.get(number))) {
            screens.put(number, new Screen(id, State.CRASHED, List.of(), AppTree.cap(reason, 160)));
            changed();
        }
    }

    /** Null when phone {@code number}'s app {@code id} says it uses {@code use}, else why not. */
    String permit(int number, String id, AppManifest.Use use) {
        PhoneApps.App app = app(number, id);
        if (app == null) return "no such app";
        if (!app.manifest()
            .uses(use)) return "not allowed: the app doesn't say --@uses " + use.word();
        return app.allows(use) ? null : "not allowed: switched off in the app's settings";
    }

    void notify(int number, String id, String title, String text) {
        PhoneApps.App app = app(number, id);
        if (app == null || !app.allows(AppManifest.Use.NOTIFY)) return;
        Notice notice = new Notice(
            app.manifest()
                .name(),
            AppTree.cap(title, AppTree.MAX_TEXT),
            AppTree.cap(text, AppTree.MAX_TEXT));
        notices.put(number, notice);
        changed();
        EntityPlayerMP holder = holderOf(number);
        // The notice stays on the phone; chat gets one now and then, so an app can't flood it.
        if (holder != null && ticks - lastChat.getOrDefault(number, Long.MIN_VALUE / 2) >= CHAT_EVERY_TICKS) {
            lastChat.put(number, ticks);
            holder.addChatComponentMessage(
                new ChatComponentTranslation("becom.apps.notice", notice.app(), notice.title(), notice.text()));
        }
    }

    /**
     * The app on screen calls a number. Checked here, not just by the OS: only the app the holder is looking at may
     * dial, and never while the phone is in a call, where a dial would add someone to it unheard.
     */
    boolean dial(PhoneMachine machine, String id, int to) {
        if (!id.equals(foreground.get(machine.number))) return false;
        if (permit(machine.number, id, AppManifest.Use.CALL) != null) return false;
        if (calls.book()
            .callOfPhone(machine.number) != null) return false;
        EntityPlayerMP holder = holderOf(machine.number);
        return holder != null && calls.dialCarried(holder, machine.number, to) == CallBook.DialResult.RINGING;
    }

    /**
     * Sends {@code data} from {@code from}'s app {@code app} to {@code to}: null once it's on its way, else why not.
     * It takes its size at the route's rate, the rate of its slowest part.
     */
    public String send(int from, String app, int to, byte[] data) {
        int bytes = data.length;
        if (bytes > t.appMessageKB() * 1024) return "too big";
        PhoneRecord target = to < 0 ? null : phones.record(to);
        if (target == null) return "no such number";
        if (!target.kind()
            .carried() && target.kind() != PhoneKind.COMPUTER)
            return "it can't take data: only phones and computers do";
        if (deliveries.size() >= MAX_IN_FLIGHT) return "network busy";
        // Data takes what calls leave, less what other senders take; this sender's own stream gives way to its new one.
        Sending last = sending.get(from);
        Sending own = last != null && last.until() > ticks ? last : null;
        CallGraph.DataRoute path = calls.dataRoute(from, to, link -> dataOn(link) - (own == null ? 0 : own.on(link)));
        SignalLevel route = levels.levelOf(path.width());
        if (route == SignalLevel.NONE) return "no signal";
        if (path.free() <= 0) return "network busy";
        int rate = Math.min(Math.min(DataLink.rate(rateOf(from), rateOf(to)), t.radioRate(route)), path.free());
        int ticksTaken = DataLink.ticks(bytes, rate);
        if (ticksTaken < 0) return "no signal";
        // A sender's messages queue behind each other, so the rate holds however fast it sends.
        long due = Math.max(own == null ? ticks : own.until(), ticks) + ticksTaken;
        if (due - ticks > MAX_BACKLOG_TICKS) return "network busy";
        sending.put(from, new Sending(due, rate, path));
        deliveries.add(new Delivery(due, order++, to, AppTree.cap(app, AppTree.MAX_ID), from, data));
        return null;
    }

    /** The KB/s data takes of a link now. */
    public int dataOn(Object link) {
        int kbps = 0;
        for (Sending stream : sending.values()) if (stream.until() > ticks) kbps += stream.on(link);
        for (Download download : downloads.values()) kbps += download.on(link);
        return kbps;
    }

    /** The KB/s data takes through a vertex now, and over how many routes: [KB/s, routes]. */
    public int[] dataAt(Object vertex) {
        int kbps = 0, routes = 0;
        for (Sending stream : sending.values()) {
            if (stream.until() <= ticks || !stream.route()
                .vertices()
                .contains(vertex)) continue;
            kbps += stream.rate();
            routes++;
        }
        for (Download download : downloads.values()) {
            if (!download.route()
                .vertices()
                .contains(vertex)) continue;
            kbps += download.rate();
            routes++;
        }
        return new int[] { kbps, routes };
    }

    /** What an end carries: a radio's by its level, a wired device's by its cable. */
    private int rateOf(int number) {
        PhoneRecord record = phones.record(number);
        if (record == null) return 0;
        if (record.kind()
            .carried()) {
            return t.radioRate(
                carried.serviceOf(number)
                    .level());
        }
        if (record.kind() == PhoneKind.COMPUTER) {
            BecomCard card = computers.get(number);
            if (card == null) return 0;
            if (card.wired()) return network.cableRate(number);
            return t.radioRate(carried.levelOf(computerEntries.getOrDefault(number, List.of())));
        }
        Mirror mirror = mirrors.get(number);
        if (mirror != null && mirror.rate() > 0) return mirror.rate();
        return network.cableRate(number);
    }

    private SignalLevel levelOf(int number) {
        PhoneRecord record = phones.record(number);
        if (record == null) return SignalLevel.NONE;
        if (record.kind()
            .carried()) {
            return carried.serviceOf(number)
                .level();
        }
        if (record.kind() == PhoneKind.COMPUTER) {
            BecomCard card = computers.get(number);
            if (card == null) return SignalLevel.NONE;
            if (card.wired()) return network.cableRate(number) > 0 ? SignalLevel.MAX : SignalLevel.NONE;
            return carried.levelOf(computerEntries.getOrDefault(number, List.of()));
        }
        Mirror mirror = mirrors.get(number);
        if (mirror != null && mirror.rate() > 0) return SignalLevel.MAX;
        return network.cableRate(number) > 0 ? SignalLevel.MAX : SignalLevel.NONE;
    }

    /** A running machine's second of battery, shared by the apps loaded on it. */
    private void billed(int number, long eu) {
        Set<String> loaded = awake.get(number);
        PhoneApps apps = data.appsIfAny(number);
        if (apps == null || loaded == null || loaded.isEmpty()) return;
        long share = eu / loaded.size();
        for (String id : loaded) {
            PhoneApps.App app = apps.get(id);
            if (app != null) apps.replace(app.used(day(), share));
        }
        // Stats only: saved with the next change, or within a minute; the screen shows them now.
        statsChanged = true;
        changed();
    }

    // endregion

    // region The App Store's mirrors

    /** A working mirror on cable says so each tick, with whose it is and whether it serves its owner's team alone. */
    public void mirrorHeard(int number, UUID owner, boolean locked) {
        mirrorHeard(number, owner, locked, 0);
    }

    /** A working mirror says so each tick, as above, and serves at {@code rate} KB/s, or its cable's at 0. */
    public void mirrorHeard(int number, UUID owner, boolean locked, int rate) {
        mirrors.put(number, new Mirror(ticks, owner, locked, rate));
    }

    /** A mirror was broken: what it was serving stops. */
    public void mirrorGone(int number) {
        mirrors.remove(number);
        served.remove(number);
        checkDownloads();
    }

    /** What a mirror serves: [downloads now, downloads today]. */
    public int[] mirrorLoad(int number) {
        int now = 0;
        for (Download download : downloads.values()) if (download.mirror() == number) now++;
        long[] count = served.get(number);
        return new int[] { now, count == null || count[0] != day() ? 0 : (int) count[1] };
    }

    /** How a phone reaches the App Store, as of the last second: null when no working mirror is in reach. */
    public Reach reach(int number) {
        Long at = reachedAt.get(number);
        if (at == null || ticks - at >= TICKS_PER_SECOND) {
            reachedAt.put(number, ticks);
            Reach reach = reachNow(number, ownerOf(number));
            if (reach == null) reaches.remove(number);
            else reaches.put(number, reach);
        }
        return reaches.get(number);
    }

    /** How far a phone's download is, 0 to 1, and which app it is; null when it isn't downloading. */
    public Map.Entry<String, Double> download(int number) {
        Download download = downloads.get(number);
        return download == null ? null : Map.entry(download.id(), download.progress(ticks));
    }

    /**
     * The mirror {@code number} reaches fastest now, serving {@code user}, and its route; null for none. Data already
     * on the way takes its share of each link.
     */
    private Reach reachNow(int number, UUID user) {
        Reach best = null;
        for (Map.Entry<Integer, Mirror> entry : mirrors.entrySet()) {
            Mirror mirror = entry.getValue();
            if (mirror.locked() && !Ownership.sameTeam(mirror.owner(), user)) continue;
            int at = entry.getKey();
            CallGraph.DataRoute path = calls.dataRoute(number, at, this::dataOn);
            if (!path.routed()) continue;
            SignalLevel level = levels.levelOf(path.width());
            if (level == SignalLevel.NONE) continue;
            int rate = Math.min(Math.min(DataLink.rate(rateOf(number), rateOf(at)), t.radioRate(level)), path.free());
            if (rate > 0 && (best == null || rate > best.rate())) best = new Reach(at, rate, path);
        }
        return best;
    }

    private UUID ownerOf(int number) {
        PhoneRecord record = phones.record(number);
        return record == null ? null : record.owner();
    }

    /** Finishes the downloads that are all there, and stops those whose mirror or route to it is gone. */
    private void checkDownloads() {
        for (Iterator<Map.Entry<Integer, Download>> it = downloads.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<Integer, Download> entry = it.next();
            int number = entry.getKey();
            Download download = entry.getValue();
            boolean lost = !mirrors.containsKey(download.mirror())
                || levels.levelOf(calls.width(number, download.mirror())) == SignalLevel.NONE;
            if (lost) {
                it.remove();
                EntityPlayerMP holder = holderOf(number);
                if (holder != null) tell(holder, "becom.apps.download_lost");
                changed();
            } else if (download.until() <= ticks) {
                it.remove();
                finishDownload(number, download.id());
                changed();
            }
        }
    }

    // endregion

    // region Computers

    /**
     * Once a day: a computer that hasn't run for {@code computerForgetDays} gives its number back, and the apps and
     * files of numbers that no longer exist go.
     */
    private void prune(long today) {
        for (PhoneRecord record : List.copyOf(
            phones.registry()
                .all())) {
            if (record.kind() != PhoneKind.COMPUTER || computers.containsKey(record.number())) continue;
            Long seen = data.seenOn(record.number());
            if (seen != null && today - seen > t.computerForgetDays()) {
                phones.unregister(record.number());
                BeCom.LOG.info("Computer {} hasn't run for {} days; its number is free", record.number(), today - seen);
            }
        }
        for (int number : List.copyOf(data.numbers())) {
            if (phones.record(number) != null) continue;
            data.forget(number);
            PhoneMachine.forgetStorage(number);
        }
        for (int number : List.copyOf(data.seenNumbers())) {
            if (phones.record(number) == null) data.forget(number);
        }
    }

    /** A BeCom Card's computer is running: it keeps its number, and a wired card joins the cable it touches. */
    void computerHere(BecomCard card) {
        World world = card.world();
        if (world == null || world.isRemote) return;
        int dimension = world.provider.dimensionId;
        long pos = card.pos();
        PhoneRecord record = card.number() > 0 ? phones.record(card.number()) : null;
        if (record == null || record.kind() != PhoneKind.COMPUTER) {
            record = phones.registerBlockPhone(PhoneKind.COMPUTER, world, pos, null, "");
            card.setNumber(record.number());
        } else if (record.dimension() != dimension || record.pos() != pos) {
            record.setLocation(dimension, pos);
            phones.registry()
                .changed();
        }
        int number = card.number();
        BecomCard other = computers.get(number);
        if (other != null && other != card && other.world() != null) {
            // Two computers with one card's number: a copied card. The newer one gets a number of its own.
            record = phones.registerBlockPhone(PhoneKind.COMPUTER, world, pos, null, "");
            card.setNumber(record.number());
            number = card.number();
        }
        computers.put(number, card);
        data.seen(number, totalDay());
        if (card.wired()) {
            NodeRef at = new NodeRef(dimension, pos);
            if (!Integer.valueOf(number)
                .equals(wired.get(at))) {
                unwire(number);
                wired.put(at, number);
                network.busChanged(dimension, pos);
            }
            computerEntries.put(number, CallGraph.Entry.of(number));
        } else if (card.me()) {
            // The ME card: its ME network, at Max, while the computer stands in that network's wireless range.
            unwire(number);
            appeng.api.networking.IGrid grid = MeAccess.gridInRange(card.meKey(), world, card.x(), card.y(), card.z());
            computerEntries.put(
                number,
                grid == null ? List.of()
                    : List
                        .of(new CallGraph.Entry(new CallGraph.MeVertex(meGrids.idOf(grid)), Double.POSITIVE_INFINITY)));
        } else {
            unwire(number);
            computerEntries.put(
                number,
                carried.entriesAt(
                    world,
                    RadioService.endOf(world, BlockPos.x(pos), BlockPos.y(pos), BlockPos.z(pos)),
                    null));
        }
    }

    void computerGone(BecomCard card) {
        if (computers.get(card.number()) != card) return;
        computers.remove(card.number());
        computerEntries.remove(card.number());
        unwire(card.number());
    }

    /** Forgets where a computer's wired card was, and tells the bus that reached it there. */
    private void unwire(int number) {
        Iterator<Map.Entry<NodeRef, Integer>> it = wired.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<NodeRef, Integer> entry = it.next();
            if (entry.getValue() != number) continue;
            it.remove();
            network.busChanged(
                entry.getKey()
                    .dimension(),
                entry.getKey()
                    .pos());
        }
    }

    /** The number of the computer with a wired BeCom Card at a spot, or -1: a device on the cable that touches it. */
    public int wiredComputerAt(int dimension, long pos) {
        Integer number = wired.get(new NodeRef(dimension, pos));
        return number == null ? -1 : number;
    }

    /** A computer offers an app to a phone; null once its holder is asked, else why not. */
    String offer(BecomCard card, int number, String source) {
        String why = offerable(source);
        if (why != null) return why;
        PhoneRecord record = phones.record(number);
        if (record == null || !record.kind()
            .carried()) return "not a carried phone";
        if (record.doNotDisturb()) return "do not disturb";
        EntityPlayerMP holder = holderOf(number);
        if (holder == null) return "nobody carries it";
        if (levels.levelOf(calls.width(card.number(), number)) == SignalLevel.NONE) return "no signal";
        String author = authorNear(card);
        return ask(card, number, holder, source, author == null ? "?" : author, false);
    }

    /**
     * A computer asks to publish an app to the App Store: the nearest player is asked on their phone, and it goes in
     * under their name only if they accept. Null once they're asked, else why not.
     */
    String publish(BecomCard card, String source) {
        String why = offerable(source);
        if (why != null) return why;
        EntityPlayer author = nearestPlayer(card);
        if (author == null) return "nobody near the computer to publish as";
        EntityPlayerMP holder = author instanceof EntityPlayerMP mp ? mp : null;
        int number = -1;
        for (int n : carried.carriedBy(author.getUniqueID())) {
            if (holder != null && mayAsk(n, holder)) {
                number = n;
                break;
            }
        }
        if (number < 0) return "the player near the computer has no phone on to confirm with";
        if (reachNow(card.number(), author.getUniqueID()) == null) return "no App Store mirror in reach";
        return ask(card, number, holder, source, author.getCommandSenderName(), true);
    }

    private String offerable(String source) {
        if (!AppLimits.of(t)
            .fits(source)) return "too big";
        if (AppManifest.parse(source) == null) return "not an app: it needs a --@name line at the top";
        return null;
    }

    private boolean mayAsk(int number, EntityPlayerMP holder) {
        var stack = new PhoneHandle.CarriedPhone(number).stack(holder);
        return stack != null && ItemCarriedPhone.working(stack);
    }

    /** Puts the question on the phone: one at a time, so a computer can't bury the holder in them. */
    private String ask(BecomCard card, int number, EntityPlayerMP holder, String source, String author,
        boolean publish) {
        Offer pending = offer(number);
        if (pending != null) return "the phone is already deciding on an app";
        AppManifest manifest = AppManifest.parse(source);
        String where = BlockPos.toString(card.pos());
        offers.put(
            number,
            new Offer(
                ++offerIds,
                card.number(),
                source,
                manifest,
                author,
                where,
                ticks + t.appOfferSeconds() * TICKS_PER_SECOND,
                publish));
        changed();
        holder.addChatComponentMessage(
            new ChatComponentTranslation(
                publish ? "becom.apps.publish_asked" : "becom.apps.offered",
                manifest.name(),
                manifest.version(),
                where));
        return null;
    }

    private String authorNear(BecomCard card) {
        EntityPlayer player = nearestPlayer(card);
        return player == null ? null : player.getCommandSenderName();
    }

    private EntityPlayer nearestPlayer(BecomCard card) {
        World world = card.world();
        if (world == null) return null;
        EntityPlayer best = null;
        double bestDistance = (double) t.appAuthorReach() * t.appAuthorReach();
        for (Object o : world.playerEntities) {
            if (!(o instanceof EntityPlayer player) || player instanceof FakePlayer) continue;
            double d = player.getDistanceSq(card.x(), card.y(), card.z());
            if (d <= bestDistance) {
                best = player;
                bestDistance = d;
            }
        }
        return best;
    }

    // endregion

    // region Machines

    private PhoneMachine start(int number, EntityPlayerMP holder) {
        if (!mayRun(number, holder)) return null;
        try {
            PhoneMachine machine = new PhoneMachine(number, holder, this, (long) t.phoneStorageKB() * 1024);
            if (!machine.start()) {
                BeCom.LOG.warn(
                    "Phone {}'s OpenComputers machine didn't start: {} (parts {})",
                    number,
                    machine.lastError(),
                    machine.describe());
                machine.stop();
                return null;
            }
            machines.put(number, machine);
            started.put(number, ticks);
            return machine;
        } catch (RuntimeException | LinkageError e) {
            BeCom.LOG.error("Phone {}'s OpenComputers machine didn't start", number, e);
            return null;
        }
    }

    private void stop(int number) {
        PhoneMachine machine = machines.remove(number);
        started.remove(number);
        queued.remove(number);
        activeUntil.remove(number);
        awake.remove(number);
        if (machine == null) return;
        try {
            machine.stop();
        } catch (RuntimeException e) {
            BeCom.LOG.warn("Phone {}'s machine didn't stop cleanly", number, e);
        }
    }

    /** Whether a phone's apps may run: carried by someone online, switched on and charged, with apps on it. */
    private boolean mayRun(int number, EntityPlayerMP holder) {
        if (holder == null || installed(number).isEmpty()) return false;
        var stack = new PhoneHandle.CarriedPhone(number).stack(holder);
        return stack != null && ItemCarriedPhone.working(stack);
    }

    private EntityPlayerMP holderOf(int number) {
        UUID id = carried.holderOf(number);
        return id != null && calls.onlinePlayer(id) instanceof EntityPlayerMP mp ? mp : null;
    }

    /** Hands the phone's OS a signal now, or once it's up. */
    private void push(int number, Object... signal) {
        PhoneMachine machine = machines.get(number);
        if (machine != null && machine.ready()) {
            send(machine, signal);
            return;
        }
        if (machine == null) return;
        Deque<Object[]> q = queued.computeIfAbsent(number, n -> new ArrayDeque<>());
        if (q.size() < MAX_QUEUED) q.add(signal);
    }

    private static void send(PhoneMachine machine, Object[] signal) {
        Object[] args = new Object[signal.length - 1];
        System.arraycopy(signal, 1, args, 0, args.length);
        machine.signal((String) signal[0], args);
    }

    private void deliver(Delivery d) {
        BecomCard computer = computers.get(d.to());
        if (computer != null) {
            computer.signal("becom_message", d.from(), d.app(), d.data());
            return;
        }
        PhoneApps.App app = app(d.to(), d.app());
        if (app == null || !app.allows(AppManifest.Use.NET)) return;
        Object[] signal = { "becom_message", d.app(), d.from(), d.data() };
        lastData.put(d.to() + ":" + d.app(), worldTime());
        if (d.app()
            .equals(foreground.get(d.to()))) changed();
        PhoneMachine machine = machines.get(d.to());
        if (machine != null) {
            // Delivered to a running machine, without keeping it up longer: a stream of messages mustn't hold a
            // phone's apps awake on its battery. Once it stops, the rest wait for the next wake.
            if (machine.ready() && activeUntil.getOrDefault(d.to(), 0L) > ticks || foreground.containsKey(d.to())) {
                awake.computeIfAbsent(d.to(), n -> new HashSet<>())
                    .add(d.app());
                push(d.to(), signal);
                return;
            }
        }
        // Not running: a background app wakes for it, at most once a minute; it waits until then.
        if (!app.background()) return;
        Deque<Object[]> w = waiting.computeIfAbsent(d.to(), n -> new ArrayDeque<>());
        if (w.size() < MAX_QUEUED) w.add(signal);
    }

    public void tick() {
        ticks++;
        while (!deliveries.isEmpty() && deliveries.peek()
            .due() <= ticks) deliver(deliveries.poll());
        for (Iterator<Map.Entry<Integer, PhoneMachine>> it = machines.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<Integer, PhoneMachine> entry = it.next();
            int number = entry.getKey();
            PhoneMachine machine = entry.getValue();
            EntityPlayerMP holder = holderOf(number);
            if (holder == null) continue;
            try {
                machine.update(holder);
            } catch (RuntimeException e) {
                BeCom.LOG.warn("Phone {}'s machine failed", number, e);
            }
        }
        if (ticks % TICKS_PER_SECOND == 0) everySecond();
    }

    private void everySecond() {
        long today = totalDay();
        if (today != prunedOn) {
            prunedOn = today;
            prune(today);
        }
        if (offers.values()
            .removeIf(offer -> offer.until() < ticks)) changed();
        sending.values()
            .removeIf(stream -> stream.until() <= ticks);
        mirrors.values()
            .removeIf(mirror -> ticks - mirror.heard() > MIRROR_LOST_TICKS);
        checkDownloads();
        if (statsChanged && ticks % STATS_SAVE_TICKS == 0) {
            statsChanged = false;
            data.markDirty();
        }
        Set<Integer> numbers = new HashSet<>(machines.keySet());
        numbers.addAll(waiting.keySet());
        for (int number : numbers) {
            EntityPlayerMP holder = holderOf(number);
            PhoneMachine machine = machines.get(number);
            if (!mayRun(number, holder)) {
                if (machine != null) stop(number);
                if (foreground.containsKey(number)) {
                    String id = foreground.get(number);
                    screens.put(number, new Screen(id, State.UNREACHABLE, List.of(), ""));
                    changed();
                }
                waiting.remove(number);
                continue;
            }
            if (machine != null && !machine.running()) {
                // It stopped: an error in BeCom OS, or no room for it.
                String error = machine.lastError();
                stop(number);
                String id = foreground.get(number);
                if (id != null) {
                    screens.put(
                        number,
                        new Screen(id, State.CRASHED, List.of(), AppTree.cap(error == null ? "stopped" : error, 160)));
                    changed();
                }
                continue;
            }
            if (machine != null && !machine.ready() && ticks - started.getOrDefault(number, ticks) > BOOT_TICKS) {
                stop(number);
                String id = foreground.get(number);
                if (id != null) screens.put(number, new Screen(id, State.CRASHED, List.of(), "didn't boot"));
                changed();
                continue;
            }
            boolean saving = carried.saving(number);
            Deque<Object[]> w = waiting.get(number);
            if (w != null && !w.isEmpty()
                && !saving
                && ticks - lastWake.getOrDefault(number, Long.MIN_VALUE / 2)
                    >= (long) t.appWakeSeconds() * TICKS_PER_SECOND) {
                if (machine == null) machine = start(number, holder);
                if (machine != null) {
                    lastWake.put(number, ticks);
                    activeUntil.put(number, ticks + (long) t.appAwakeSeconds() * TICKS_PER_SECOND);
                    Set<String> woken = new HashSet<>();
                    for (Object[] signal : w) {
                        push(number, signal);
                        woken.add((String) signal[1]);
                    }
                    waiting.remove(number);
                    changed();
                    awake.computeIfAbsent(number, n -> new HashSet<>())
                        .addAll(woken);
                    for (String id : woken) {
                        PhoneApps.App app = app(number, id);
                        if (app != null) data.apps(number)
                            .replace(app.woke(day()));
                    }
                    data.markDirty();
                }
            }
            boolean busy = foreground.containsKey(number) || activeUntil.getOrDefault(number, 0L) > ticks && !saving;
            if (machine != null && !busy) stop(number);
        }
    }

    public void stopAll() {
        for (int number : new ArrayList<>(machines.keySet())) stop(number);
        foreground.clear();
        screens.clear();
        waiting.clear();
        deliveries.clear();
        offers.clear();
    }

    // endregion

    private static void tell(EntityPlayerMP player, String key, Object... args) {
        player.addChatComponentMessage(new ChatComponentTranslation(key, args));
    }
}
