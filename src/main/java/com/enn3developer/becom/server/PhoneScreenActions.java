package com.enn3developer.becom.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.enn3developer.becom.apps.AppService;
import com.enn3developer.becom.gui.AppsView;
import com.enn3developer.becom.gui.PhoneView;
import com.enn3developer.becom.item.ItemCarriedPhone;
import com.enn3developer.becom.item.ItemMePhone;
import com.enn3developer.becom.logic.PhoneNumbers;
import com.enn3developer.becom.logic.RateLimiter;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.app.AppManifest;
import com.enn3developer.becom.logic.app.AppStore;
import com.enn3developer.becom.logic.app.PhoneApps;
import com.enn3developer.becom.logic.call.Call;
import com.enn3developer.becom.logic.net.CallGraph;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.logic.phone.BatteryUse;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.logic.phone.PhoneRegistry;
import com.enn3developer.becom.logic.video.VideoPlan;

/**
 * What the phone screen may do, checked on the server. Every action re-checks that the
 * player can use the phone, at a Telephone or a Videophone or carrying the phone, and is allowed to do it, whatever
 * their client sent.
 */
public final class PhoneScreenActions {

    private PhoneScreenActions() {}

    public static PhoneView view(EntityPlayer player, PhoneHandle handle) {
        BeComServer becom = BeComServer.get();
        int number = handle.number();
        // A phone the player can't use shows nothing: one renumbered in their hands as a copy was another's, and its
        // calls are private.
        if (becom == null || number < 0 || !handle.usableBy(player)) return PhoneView.EMPTY;
        PhoneRecord record = becom.phones.record(number);
        if (record == null) return PhoneView.EMPTY;
        boolean manage = Ownership.mayManage(player, record.owner());
        Call call = becom.calls.book()
            .callOfPhone(number);
        Call.Leg own = call == null ? null : call.legOf(number);
        // Calls are private to the people in them: anyone else at a Telephone or a Videophone in a
        // call sees only that it's in use.
        boolean outsider = own != null && own.state() == Call.Leg.State.ACTIVE
            && !call.participants()
                .contains(player.getUniqueID());
        PhoneView.State state;
        List<PhoneView.Member> members = new ArrayList<>();
        if (outsider) {
            state = PhoneView.State.IN_USE;
        } else if (own != null && own.state() == Call.Leg.State.RINGING) {
            // A ringing phone shows who calls, as its notice does, and none of the rest of the call.
            state = PhoneView.State.RINGING_IN;
            Call.Leg ringer = call.legOf(own.invitedBy());
            if (ringer == null && !call.activeLegs()
                .isEmpty())
                ringer = call.activeLegs()
                    .get(0);
            if (ringer != null) {
                members.add(new PhoneView.Member(ringer.phone(), nameOn(becom, ringer), SignalLevel.NONE, false));
            }
        } else if (own != null) {
            state = call.state() == Call.State.RINGING ? PhoneView.State.RINGING_OUT : PhoneView.State.CONNECTED;
            for (Call.Leg leg : call.legs()) {
                if (leg == own || leg.state() == Call.Leg.State.LEFT) continue;
                boolean talking = own.state() == Call.Leg.State.ACTIVE && leg.state() == Call.Leg.State.ACTIVE;
                SignalLevel level = talking ? call.level(leg, own) : SignalLevel.NONE;
                if (leg.speakerphone() && !leg.players()
                    .isEmpty()) {
                    // Everyone in a Videophone's room is in the call, each by name.
                    String owner = becom.phones.name(leg.phone());
                    for (UUID inRoom : leg.players()) {
                        members.add(new PhoneView.Member(leg.phone(), becom.calls.nameOf(inRoom, owner), level, false));
                    }
                    continue;
                }
                members.add(
                    new PhoneView.Member(
                        leg.phone(),
                        nameOn(becom, leg),
                        level,
                        leg.state() == Call.Leg.State.RINGING));
            }
        } else if (handle instanceof PhoneHandle.Telephone) {
            UUID holder = becom.calls.holderOf(number);
            state = holder == null ? PhoneView.State.IDLE
                : holder.equals(player.getUniqueID()) ? PhoneView.State.OFF_HOOK : PhoneView.State.IN_USE;
        } else {
            state = PhoneView.State.IDLE;
        }
        PhoneView.Member first = members.isEmpty() ? null : members.get(0);
        List<PhoneView.Contact> contacts = new ArrayList<>();
        for (PhoneRegistry.Contact contact : becom.phones.registry()
            .contacts(player.getUniqueID(), number, becom.calls.reachableFrom(number))) {
            PhoneRecord other = becom.phones.record(contact.number());
            contacts.add(
                new PhoneView.Contact(
                    contact.number(),
                    contact.ownerName(),
                    other == null ? 0
                        : other.kind()
                            .ordinal()));
        }
        // Calls are private to the people in them: a phone's history is for its owner and their team.
        List<PhoneView.Recent> recent = new ArrayList<>();
        if (manage) {
            for (PhoneRecord.HistoryEntry entry : record.history()) {
                recent
                    .add(new PhoneView.Recent(entry.time(), entry.otherNumber(), entry.otherName(), entry.direction()));
            }
        }
        List<PhoneView.Blocked> blocked = new ArrayList<>();
        if (manage) {
            for (Map.Entry<UUID, String> entry : record.blocked()
                .entrySet()) blocked.add(new PhoneView.Blocked(entry.getKey(), entry.getValue()));
        }
        boolean carried = handle instanceof PhoneHandle.CarriedPhone;
        ItemStack stack = handle instanceof PhoneHandle.CarriedPhone phone ? phone.stack(player) : null;
        boolean offline = handle instanceof PhoneHandle.Videophone && !becom.calls.videophoneWorking(number);
        return new PhoneView(
            number,
            record.ownerName(),
            manage,
            state,
            first == null ? "" : first.name(),
            first == null ? -1 : first.number(),
            first == null ? SignalLevel.NONE : first.level(),
            record.doNotDisturb(),
            record.hidden(),
            contacts,
            recent,
            blocked,
            carried,
            stack != null && ItemCarriedPhone.on(stack),
            record.silent(),
            carried ? becom.carried.serviceOf(number)
                .level() : SignalLevel.NONE,
            members,
            offline,
            videoOf(becom, player, record, call, own),
            stack == null ? PhoneView.Battery.NONE : batteryOf(becom, number, stack),
            carried ? viaOf(becom, number) : PhoneView.Via.NONE,
            carried && manage ? appsOf(becom, number) : AppsView.NONE,
            new PhoneView.Phone(
                record.kind()
                    .ordinal(),
                stack != null && stack.getItem() instanceof ItemMePhone me ? me.networkName(stack) : "",
                stack != null && stack.getItem() instanceof ItemMePhone && inMeRange(becom, number),
                manage ? record.missedSeen() : 0,
                call != null && call.video(),
                speedDialOf(record),
                handle instanceof PhoneHandle.Videophone ? becom.calls.hereAt(number) : 0,
                record.camera()));
    }

    private static List<Integer> speedDialOf(PhoneRecord record) {
        List<Integer> keys = new ArrayList<>(PhoneRecord.SPEED_DIAL_KEYS);
        for (int i = 0; i < PhoneRecord.SPEED_DIAL_KEYS; i++) keys.add(record.speedDial(i));
        return keys;
    }

    /** Stores {@code target} on a desk phone's speed-dial key, or clears the key for 0; its owner and team only. */
    public static void setSpeedDial(EntityPlayer player, PhoneHandle handle, int key, int target) {
        if (handle instanceof PhoneHandle.CarriedPhone) return;
        PhoneRecord record = managed(player, handle);
        if (record == null || key < 0 || key >= PhoneRecord.SPEED_DIAL_KEYS) return;
        if (target != 0 && !PhoneNumbers.isValid(target)) return;
        record.setSpeedDial(key, target);
        BeComServer.get().phones.registry()
            .changed();
    }

    /** A Videophone's Camera key: whether its calls start as video calls; its owner and team only. */
    public static void setVideophoneCamera(EntityPlayer player, PhoneHandle handle, boolean on) {
        if (!(handle instanceof PhoneHandle.Videophone)) return;
        PhoneRecord record = managed(player, handle);
        if (record == null) return;
        record.setCamera(on);
        BeComServer.get().phones.registry()
            .changed();
    }

    /** {@code player} looked at the phone's missed calls: they aren't new any more. */
    public static void missedSeen(EntityPlayer player, PhoneHandle handle) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !handle.usableBy(player)) return;
        PhoneRecord record = becom.phones.record(handle.number());
        if (record == null || !Ownership.mayManage(player, record.owner())) return;
        long latest = 0;
        for (PhoneRecord.HistoryEntry entry : record.history()) latest = Math.max(latest, entry.time());
        if (latest <= record.missedSeen()) return;
        record.setMissedSeen(latest);
        becom.phones.registry()
            .changed();
    }

    /** Whether a carried phone's service comes in through its ME network: an ME Phone in its wireless range. */
    private static boolean inMeRange(BeComServer becom, int number) {
        for (CallGraph.Entry entry : becom.carried.serviceOf(number)
            .entries()) if (entry.vertex() instanceof CallGraph.MeVertex) return true;
        return false;
    }

    private record CachedApps(long version, AppsView view) {}

    // Each open phone's apps view, built again only when the apps' version moves: the view is asked for every tick,
    // and building it measures and searches every app's source.
    private static final Map<Integer, CachedApps> APPS_VIEWS = new java.util.LinkedHashMap<>(16, 0.75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, CachedApps> eldest) {
            return size() > 64;
        }
    };

    /** A carried phone's apps, for its owner: installed, the open one, an offer, the last notice and the store. */
    private static AppsView appsOf(BeComServer becom, int number) {
        long version = becom.apps.version();
        CachedApps cached = APPS_VIEWS.get(number);
        if (cached != null && cached.version() == version) return cached.view();
        AppsView view = buildApps(becom, number);
        APPS_VIEWS.put(number, new CachedApps(version, view));
        return view;
    }

    private static AppsView buildApps(BeComServer becom, int number) {
        AppService apps = becom.apps;
        List<AppsView.App> installed = new ArrayList<>();
        Map<String, String> versions = new java.util.HashMap<>();
        for (PhoneApps.App app : apps.installed(number)) {
            AppManifest m = app.manifest();
            versions.put(m.id(), m.version());
            installed.add(
                new AppsView.App(
                    m.id(),
                    m.name(),
                    m.version(),
                    app.author(),
                    m.about(),
                    m.accent(),
                    m.icon(),
                    m.uses(),
                    app.origin()
                        .store(),
                    app.origin()
                        .where(),
                    app.background(),
                    app.woke(),
                    app.euToday(),
                    app.size(),
                    apps.storageUsed(number, m.id()),
                    apps.storageLimit(),
                    app.denied()));
        }
        AppService.Screen screen = apps.screen(number);
        AppsView.Screen shown = screen == null ? AppsView.Screen.NONE
            : new AppsView.Screen(
                screen.id(),
                AppsView.State.valueOf(
                    screen.state()
                        .name()),
                screen.error(),
                screen.widgets(),
                apps.lastData(number, screen.id()));
        AppService.Offer offer = apps.offer(number);
        AppsView.Offer offered = offer == null ? AppsView.Offer.NONE
            : new AppsView.Offer(
                offer.id(),
                offer.publish(),
                offer.manifest()
                    .name(),
                offer.manifest()
                    .version(),
                offer.author(),
                offer.where(),
                offer.source()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                offer.manifest()
                    .uses(),
                offer.manifest()
                    .accent(),
                offer.manifest()
                    .icon());
        AppService.Notice notice = apps.notice(number);
        AppService.StoreQuery query = apps.query(number);
        List<AppStore.Entry> found;
        if (query.tab() == AppService.StoreTab.INSTALLED) {
            // What this phone has from the store, still in it, matching the search.
            found = new ArrayList<>();
            for (AppStore.Entry e : apps.store()
                .search(query.text(), AppStore.Order.POPULAR, e -> apps.sees(number, e)))
                if (versions.containsKey(e.id())) found.add(e);
        } else {
            found = apps.store()
                .search(
                    query.text(),
                    query.tab() == AppService.StoreTab.NEW ? AppStore.Order.NEW : AppStore.Order.POPULAR,
                    e -> apps.sees(number, e));
        }
        int pages = Math.max(1, (found.size() + AppsView.STORE_ROWS - 1) / AppsView.STORE_ROWS);
        int page = Math.min(query.page(), pages - 1);
        PhoneRecord record = becom.phones.record(number);
        java.util.UUID owner = record == null ? null : record.owner();
        long now = System.currentTimeMillis();
        List<AppsView.StoreApp> store = new ArrayList<>();
        for (AppStore.Entry e : found
            .subList(page * AppsView.STORE_ROWS, Math.min(found.size(), (page + 1) * AppsView.STORE_ROWS))) {
            AppManifest m = e.manifest();
            store.add(
                new AppsView.StoreApp(
                    e.id(),
                    e.name(),
                    e.version(),
                    e.authorName(),
                    e.about(),
                    e.installs(),
                    e.size(),
                    versions.getOrDefault(e.id(), ""),
                    e.icon(),
                    m == null ? 0 : m.uses(),
                    (int) Math.max(0, (now - e.published()) / 86_400_000L),
                    e.team(),
                    owner != null && apps.store()
                        .reported(e.id(), owner),
                    m == null ? 0 : m.accent()));
        }
        return new AppsView(
            installed,
            shown,
            offered,
            notice == null ? AppsView.Notice.NONE : new AppsView.Notice(notice.app(), notice.title(), notice.text()),
            store,
            query.text(),
            query.tab()
                .ordinal(),
            page,
            found.size());
    }

    /** A carried phone's battery page: its charge, how long it lasts, where it went today and its switches. */
    private static PhoneView.Battery batteryOf(BeComServer becom, int number, ItemStack stack) {
        ItemCarriedPhone item = (ItemCarriedPhone) stack.getItem();
        Tunables t = becom.tunables;
        double charge = item.charge(stack);
        return new PhoneView.Battery(
            (long) charge,
            (long) item.maxCharge(stack),
            BatteryUse.minutesLeft(charge, t.phoneStandbyDrain()),
            BatteryUse.minutesLeft(charge, Math.max(t.phoneCallDrain(), t.phoneStandbyDrain())),
            becom.carried.charging(number),
            java.util.Arrays.stream(
                ItemCarriedPhone.use(stack)
                    .shares())
                .boxed()
                .toList(),
            ItemCarriedPhone.saver(stack),
            becom.carried.saving(number),
            ItemCarriedPhone.warns(stack),
            appShares(becom, number, ItemCarriedPhone.use(stack)));
    }

    /** Each app's share of today's drain, most first: what it drew while awake, of all the phone drew today. */
    private static List<PhoneView.AppShare> appShares(BeComServer becom, int number, BatteryUse use) {
        long total = 0;
        for (long eu : use.eu()) total += eu;
        List<PhoneView.AppShare> shares = new java.util.ArrayList<>();
        if (total <= 0) return shares;
        for (com.enn3developer.becom.logic.app.PhoneApps.App app : becom.apps.installed(number)) {
            if (app.day() != use.day() || app.euToday() <= 0) continue;
            shares.add(
                new PhoneView.AppShare(
                    app.manifest()
                        .name(),
                    (int) Math.round(100.0 * app.euToday() / total)));
        }
        shares.sort((a, b) -> Integer.compare(b.share(), a.share()));
        return shares;
    }

    /** What a carried phone's service comes through: the way in with the best SNR. */
    private static PhoneView.Via viaOf(BeComServer becom, int number) {
        CallGraph.Entry best = null;
        for (CallGraph.Entry entry : becom.carried.serviceOf(number)
            .entries()) if (best == null || entry.snr() > best.snr()) best = entry;
        if (best == null) return PhoneView.Via.NONE;
        if (best.vertex() instanceof CallGraph.SkyVertex) return new PhoneView.Via(PhoneView.Via.Kind.SATELLITES, 0);
        if (best.vertex() instanceof CallGraph.MeVertex) return new PhoneView.Via(PhoneView.Via.Kind.ME_NETWORK, 0);
        if (best.vertex() instanceof CallGraph.NodeVertex node) {
            NetworkModel model = becom.network.model(node.dimension());
            NetNode antenna = model == null ? null : model.node(node.pos());
            return new PhoneView.Via(
                PhoneView.Via.Kind.ANTENNA,
                antenna == null ? 0
                    : antenna.kind()
                        .antennaTier());
        }
        return PhoneView.Via.NONE;
    }

    /** The video part of the screen: the player's own switches, on a phone with a camera. */
    private static PhoneView.Video videoOf(BeComServer becom, EntityPlayer player, PhoneRecord record, Call call,
        Call.Leg own) {
        if (!record.kind()
            .video()) return PhoneView.Video.NONE;
        UUID id = player.getUniqueID();
        VideoPlan.Cameras cameras = becom.video.camerasOf(id);
        // The player's own leg, when they're in the call on this phone; at a Videophone, when they're in its room.
        boolean onIt = own != null && own.state() == Call.Leg.State.ACTIVE
            && own.players()
                .contains(id);
        boolean rearPossible = onIt && record.kind()
            .carried()
            && call.state() == Call.State.CONNECTED
            && call.activeLegs()
                .size() == 2
            && call.participants()
                .size() == 2;
        boolean active = onIt && !becom.video.plan()
            .tilesOf(id)
            .isEmpty();
        return new PhoneView.Video(true, cameras.front(), cameras.rear(), rearPossible, active);
    }

    /**
     * Who the screen names on a phone of the call: the player on it once there is one (the caller, or whoever
     * answered), as the call history does; the phone's owner while it still rings.
     */
    private static String nameOn(BeComServer becom, Call.Leg leg) {
        String owner = becom.phones.name(leg.phone());
        return leg.player() != null ? becom.calls.nameOf(leg.player(), owner) : owner;
    }

    /**
     * Dials {@code target}: from a Telephone, taking its handset if needed; from a carried phone or a Videophone, or
     * adds it to the call.
     */
    public static void dial(EntityPlayer player, PhoneHandle handle, int target) {
        dial(player, handle, target, false);
    }

    /**
     * Dials {@code target} as {@link #dial(EntityPlayer, PhoneHandle, int)} does; from a carried phone, a video call
     * when {@code video}.
     */
    public static void dial(EntityPlayer player, PhoneHandle handle, int target, boolean video) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !(player instanceof EntityPlayerMP mp) || !handle.usableBy(player)) return;
        if (!PhoneNumbers.isValid(target)) return;
        if (handle instanceof PhoneHandle.Videophone) {
            becom.calls.dialVideophone(mp, handle.number(), target);
            return;
        }
        if (handle instanceof PhoneHandle.Telephone telephone) {
            Integer held = becom.calls.handsetOf(player.getUniqueID());
            if (held == null) {
                if (becom.calls.takeHandset(mp, telephone.tile()) != CallService.Pickup.TAKEN) return;
            } else if (held != handle.number()) {
                return;
            }
            becom.calls.dial(mp, handle.number(), target);
            return;
        }
        becom.calls.dialCarried(mp, handle.number(), target, video);
    }

    /** Picks up: answers a ringing phone, or takes a Telephone's handset to dial. */
    public static void pickUp(EntityPlayer player, PhoneHandle handle) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !(player instanceof EntityPlayerMP mp) || !handle.usableBy(player)) return;
        if (handle instanceof PhoneHandle.Videophone) {
            becom.calls.answerVideophone(mp, handle.number());
            return;
        }
        if (handle instanceof PhoneHandle.Telephone telephone) {
            becom.calls.takeHandset(mp, telephone.tile());
            return;
        }
        becom.calls.answerCarried(mp, handle.number());
    }

    /**
     * Hangs up: puts a Telephone's handset back, ends the carried phone's or the Videophone's call, or turns down a
     * ringing one.
     */
    public static void hangUp(EntityPlayer player, PhoneHandle handle) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !(player instanceof EntityPlayerMP mp) || !handle.usableBy(player)) return;
        if (handle instanceof PhoneHandle.Videophone) {
            becom.calls.hangUpVideophone(mp, handle.number());
            return;
        }
        if (handle instanceof PhoneHandle.Telephone) {
            Integer held = becom.calls.handsetOf(player.getUniqueID());
            if (held != null && held == handle.number()) becom.calls.hangUpHandset(player.getUniqueID());
            return;
        }
        if (becom.calls.isRinging(handle.number())) {
            becom.calls.rejectCarried(player, handle.number());
            return;
        }
        Call call = becom.calls.book()
            .callOf(player.getUniqueID());
        Call.Leg own = call == null ? null : call.legOf(player.getUniqueID());
        if (own != null && own.phone() == handle.number()) becom.calls.hangUp(mp);
    }

    /**
     * Switches a carried phone on or off: whoever carries it may, as with a walkie. It looks at the
     * player's phones again at once, so it shares the settings' rate limit.
     */
    public static void setPower(EntityPlayer player, PhoneHandle handle, boolean on) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !(player instanceof EntityPlayerMP mp)
            || !(handle instanceof PhoneHandle.CarriedPhone phone)) return;
        ItemStack stack = phone.stack(player);
        if (stack == null || !mayChange(player)) return;
        ItemCarriedPhone.setOn(stack, on);
        player.inventory.markDirty();
        // Now, not at the next second: a phone switched off can't ring or stay in its call.
        becom.carried.refresh(mp);
    }

    // region Apps

    /**
     * The phone whose apps {@code player} may use from this screen: a carried phone they carry and own, or whose
     * owner's team they're on; -1 otherwise.
     */
    private static int appPhone(EntityPlayer player, PhoneHandle handle) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !(player instanceof EntityPlayerMP) || carriedStack(player, handle) == null) return -1;
        PhoneRecord record = becom.phones.record(handle.number());
        return record != null && Ownership.mayManage(player, record.owner()) ? handle.number() : -1;
    }

    public static void appOpen(EntityPlayer player, PhoneHandle handle, String id) {
        int number = appPhone(player, handle);
        if (number >= 0) BeComServer.get().apps.open((EntityPlayerMP) player, number, id);
    }

    public static void appClose(EntityPlayer player, PhoneHandle handle) {
        int number = appPhone(player, handle);
        if (number >= 0) BeComServer.get().apps.close(number);
    }

    public static void appEvent(EntityPlayer player, PhoneHandle handle, String id, String widget, String value) {
        int number = appPhone(player, handle);
        if (number >= 0) BeComServer.get().apps.event(number, id, widget, value);
    }

    public static void appBackground(EntityPlayer player, PhoneHandle handle, String id, boolean on) {
        int number = appPhone(player, handle);
        if (number >= 0 && mayChange(player)) BeComServer.get().apps.setBackground(number, id, on);
    }

    public static void appAllowed(EntityPlayer player, PhoneHandle handle, String id, int use, boolean on) {
        int number = appPhone(player, handle);
        if (number < 0 || !mayChange(player) || use < 0 || use >= AppManifest.Use.VALUES.length) return;
        BeComServer.get().apps.setAllowed(number, id, AppManifest.Use.VALUES[use], on);
    }

    public static void appUninstall(EntityPlayer player, PhoneHandle handle, String id) {
        int number = appPhone(player, handle);
        if (number >= 0 && mayChange(player)) BeComServer.get().apps.uninstall(number, id);
    }

    public static void storeQuery(EntityPlayer player, PhoneHandle handle, String query, int tab, int page) {
        int number = appPhone(player, handle);
        if (number >= 0 && mayChange(player))
            BeComServer.get().apps.setQuery(number, query, AppService.StoreTab.byOrdinal(tab), page);
    }

    public static void storeReport(EntityPlayer player, PhoneHandle handle, String id) {
        int number = appPhone(player, handle);
        if (number >= 0 && mayChange(player)) BeComServer.get().apps.report((EntityPlayerMP) player, number, id);
    }

    public static void storeInstall(EntityPlayer player, PhoneHandle handle, String id) {
        int number = appPhone(player, handle);
        if (number >= 0 && mayChange(player))
            BeComServer.get().apps.installFromStore((EntityPlayerMP) player, number, id);
    }

    public static void answerOffer(EntityPlayer player, PhoneHandle handle, long id, boolean accept, boolean team) {
        int number = appPhone(player, handle);
        if (number >= 0) BeComServer.get().apps.answerOffer((EntityPlayerMP) player, number, id, accept, team);
    }

    // endregion

    /** Switches a carried phone's battery saver by hand (the Battery settings page). */
    public static void setSaver(EntityPlayer player, PhoneHandle handle, boolean on) {
        ItemStack stack = carriedStack(player, handle);
        if (stack == null || !mayChange(player)) return;
        ItemCarriedPhone.setSaver(stack, on);
        player.inventory.markDirty();
    }

    /** Switches a carried phone's low-battery warnings. */
    public static void setWarns(EntityPlayer player, PhoneHandle handle, boolean on) {
        ItemStack stack = carriedStack(player, handle);
        if (stack == null || !mayChange(player)) return;
        ItemCarriedPhone.setWarns(stack, on);
        player.inventory.markDirty();
    }

    private static ItemStack carriedStack(EntityPlayer player, PhoneHandle handle) {
        if (BeComServer.get() == null || !(handle instanceof PhoneHandle.CarriedPhone phone)) return null;
        return phone.stack(player);
    }

    // Settings and block-list changes are cheap one by one, but each recomputes contacts or the view: a client that
    // floods them only wastes the server's time. A person never needs more than this.
    private static final int SETTINGS_CHANGES_PER_MINUTE = 60;
    private static final RateLimiter<UUID> SETTINGS_CHANGES = new RateLimiter<>(SETTINGS_CHANGES_PER_MINUTE, 60_000);
    // Each phone screen opened finds every phone in reach for its contacts; nobody opens one faster than this.
    private static final int SCREEN_OPENS_PER_SECOND = 4;
    private static final RateLimiter<UUID> SCREEN_OPENS = new RateLimiter<>(SCREEN_OPENS_PER_SECOND, 1000);

    private static boolean mayChange(EntityPlayer player) {
        long now = System.nanoTime() / 1_000_000L;
        SETTINGS_CHANGES.sweep(now);
        return SETTINGS_CHANGES.tryAcquire(player.getUniqueID(), now);
    }

    /** Whether {@code player} may open a carried phone's screen now, from the item or the Phone key. */
    public static boolean mayOpenScreen(EntityPlayer player) {
        long now = System.nanoTime() / 1_000_000L;
        SCREEN_OPENS.sweep(now);
        return SCREEN_OPENS.tryAcquire(player.getUniqueID(), now);
    }

    /**
     * Switches one of {@code player}'s own cameras during their call on this phone, on a phone with
     * video. Each switch re-plans every video call, so it shares the settings' rate limit.
     */
    public static void setCamera(EntityPlayer player, PhoneHandle handle, boolean rear, boolean on) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !handle.usableBy(player)) return;
        PhoneKind kind = becom.calls.kindOf(handle.number());
        if (kind == null || !kind.video()) return;
        UUID id = player.getUniqueID();
        Call call = becom.calls.book()
            .callOf(id);
        Call.Leg own = call == null ? null : call.legOf(id);
        if (own == null || own.phone() != handle.number() || !mayChange(player)) return;
        if (rear) becom.video.setRear(id, on);
        else becom.video.setFront(id, on);
    }

    private static PhoneRecord managed(EntityPlayer player, PhoneHandle handle) {
        BeComServer becom = BeComServer.get();
        if (becom == null || !handle.usableBy(player) || !mayChange(player)) return null;
        PhoneRecord record = becom.phones.record(handle.number());
        if (record == null || !Ownership.mayManage(player, record.owner())) return null;
        return record;
    }

    public static void setDoNotDisturb(EntityPlayer player, PhoneHandle handle, boolean on) {
        PhoneRecord record = managed(player, handle);
        if (record == null) return;
        record.setDoNotDisturb(on);
        BeComServer.get().phones.registry()
            .changed();
    }

    public static void setHidden(EntityPlayer player, PhoneHandle handle, boolean on) {
        PhoneRecord record = managed(player, handle);
        if (record == null) return;
        record.setHidden(on);
        BeComServer.get().phones.registry()
            .changed();
    }

    /** Silent mode, for a carried phone: it buzzes for its holder instead of ringing out loud. */
    public static void setSilent(EntityPlayer player, PhoneHandle handle, boolean on) {
        if (!(handle instanceof PhoneHandle.CarriedPhone)) return;
        PhoneRecord record = managed(player, handle);
        if (record == null) return;
        record.setSilent(on);
        BeComServer.get().phones.registry()
            .changed();
    }

    /** Blocks the owner of a phone in this player's contacts. */
    public static void blockContact(EntityPlayer player, PhoneHandle handle, int number) {
        PhoneRecord record = managed(player, handle);
        if (record == null) return;
        BeComServer becom = BeComServer.get();
        PhoneRegistry.Blockable target = becom.phones.registry()
            .contactTarget(player.getUniqueID(), record.number(), becom.calls.reachableFrom(record.number()), number);
        block(player, record, target);
    }

    /** Blocks whoever the recent call at {@code time} with {@code number} recorded on the other end. */
    public static void blockRecent(EntityPlayer player, PhoneHandle handle, long time, int number) {
        PhoneRecord record = managed(player, handle);
        if (record == null) return;
        block(
            player,
            record,
            BeComServer.get().phones.registry()
                .recentTarget(record, time, number));
    }

    private static void block(EntityPlayer player, PhoneRecord record, PhoneRegistry.Blockable target) {
        if (target == null) return;
        if (!record.block(target.player(), target.name())) {
            player.addChatMessage(new ChatComponentTranslation("becom.phone.block_full", PhoneRecord.MAX_BLOCKED));
            return;
        }
        BeComServer.get().phones.registry()
            .changed();
    }

    public static void unblock(EntityPlayer player, PhoneHandle handle, UUID blocked) {
        PhoneRecord record = managed(player, handle);
        if (record == null) return;
        if (record.unblock(blocked)) BeComServer.get().phones.registry()
            .changed();
    }
}
