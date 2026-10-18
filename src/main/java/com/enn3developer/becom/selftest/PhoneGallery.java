package com.enn3developer.becom.selftest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.factory.ClientGUI;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.enn3developer.becom.gui.AppsView;
import com.enn3developer.becom.gui.PhoneView;
import com.enn3developer.becom.gui.machine.ReadoutUi;
import com.enn3developer.becom.gui.paint.CanvasWidget;
import com.enn3developer.becom.gui.phone.PhoneUi;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.logic.app.AppManifest;
import com.enn3developer.becom.logic.app.AppTree;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.machine.InfoLines;
import com.enn3developer.becom.packet.AnalyzerPacket;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The carried phone's pages, each on made-up data like the design boards', for the client self-test to screenshot
 * beside the boards: the phone's screen opened client-side, with nothing sent anywhere.
 */
@SideOnly(Side.CLIENT)
final class PhoneGallery {

    /** One picture: its name, and what the phone shows for it. */
    record Shot(String name, Runnable setup) {}

    private PhoneView view;
    private AnalyzerPacket.Reading signal = AnalyzerPacket.Reading.NONE;
    private final PhoneUi ui = new PhoneUi(() -> view, () -> signal, (action, write) -> {});
    private final com.enn3developer.becom.gui.phone.DeskPhoneUi telephone = new com.enn3developer.becom.gui.phone.DeskPhoneUi(
        () -> view,
        (action, write) -> {},
        false);
    private final com.enn3developer.becom.gui.phone.DeskPhoneUi videophone = new com.enn3developer.becom.gui.phone.DeskPhoneUi(
        () -> view,
        (action, write) -> {},
        true);

    void open() {
        open(stillMouse(ui), PhoneUi.scaleFor(com.enn3developer.becom.BeCom.proxy.guiHeight()));
    }

    /**
     * {@code screen} painted with the mouse off it, as the boards draw it: the test's cursor rests in the middle of the
     * window, where it would light a key or a row. The shots click the screen itself.
     */
    private static com.enn3developer.becom.gui.paint.CanvasScreen stillMouse(
        com.enn3developer.becom.gui.paint.CanvasScreen screen) {
        return new com.enn3developer.becom.gui.paint.CanvasScreen() {

            @Override
            public float width() {
                return screen.width();
            }

            @Override
            public float height() {
                return screen.height();
            }

            @Override
            protected void paint(com.enn3developer.becom.gui.paint.Canvas canvas) {
                screen.paint(canvas, -1, -1);
            }
        };
    }

    private void open(com.enn3developer.becom.gui.paint.CanvasScreen screen, float scale) {
        if (view == null) view = view(PhoneView.State.IDLE, base());
        CanvasWidget phone = new CanvasWidget(screen, scale);
        ModularPanel panel = new ModularPanel("becom_phone_gallery").size(phone.width(), phone.height())
            .background(IDrawable.EMPTY)
            .child(phone);
        ClientGUI.open(new ModularScreen("becom", panel));
    }

    List<Shot> shots() {
        List<Shot> shots = new ArrayList<>();
        shots.add(new Shot("phone-main", () -> show(view(PhoneView.State.IDLE, base()), "HOME", "")));
        shots.add(new Shot("phone-dialer", () -> {
            show(view(PhoneView.State.IDLE, base()), "DIALER", "");
            ui.typeForTest("7740");
        }));
        shots.add(new Shot("phone-incoming", () -> show(view(PhoneView.State.RINGING_IN, base()), "HOME", "")));
        shots.add(new Shot("phone-call", () -> show(view(PhoneView.State.CONNECTED, base()), "CALL", "")));
        shots.add(new Shot("phone-call-video", () -> {
            PhoneView v = view(PhoneView.State.CONNECTED, base());
            show(
                withPhone(v, new PhoneView.Phone(PhoneKind.MOBILE.ordinal(), "", false, 0, true, List.of(), 0, true)),
                "CALL",
                "");
        }));
        shots.add(new Shot("phone-contacts", () -> show(view(PhoneView.State.IDLE, base()), "CONTACTS", "")));
        // A right click on Bob's row, as on the board, on the Contacts page the last shot painted.
        shots.add(new Shot("phone-contacts-menu", () -> ui.click(180, 34 + 22 + 32 + 40 + 20, 1)));
        shots.add(new Shot("phone-recent", () -> show(view(PhoneView.State.IDLE, base()), "RECENT", "")));
        shots.add(new Shot("phone-settings", () -> show(view(PhoneView.State.IDLE, base()), "SETTINGS", "")));
        shots.add(new Shot("phone-battery", () -> show(view(PhoneView.State.IDLE, base()), "BATTERY", "")));
        shots.add(new Shot("phone-signal", () -> {
            signal = antennaReading();
            show(view(PhoneView.State.IDLE, base()), "SIGNAL", "");
        }));
        shots.add(new Shot("phone-video-window", () -> show(view(PhoneView.State.IDLE, base()), "VIDEO_WINDOW", "")));
        shots.add(new Shot("phone-store", () -> show(view(PhoneView.State.IDLE, base()), "STORE", "")));
        shots.add(
            new Shot(
                "phone-store-app",
                () -> show(view(PhoneView.State.IDLE, base(TANK_ABOUT_PAGE)), "STORE_APP", "tank")));
        shots.add(new Shot("phone-app", () -> show(view(PhoneView.State.IDLE, running()), "APP", "reactor")));
        shots.add(
            new Shot(
                "phone-app-unreachable",
                () -> show(view(PhoneView.State.IDLE, state(AppsView.State.UNREACHABLE, "")), "APP", "reactor")));
        shots.add(
            new Shot(
                "phone-app-crashed",
                () -> show(
                    view(PhoneView.State.IDLE, state(AppsView.State.CRASHED, "reactor.lua:42: attempt to index nil")),
                    "APP",
                    "reactor")));
        shots.add(
            new Shot("phone-app-settings", () -> show(view(PhoneView.State.IDLE, base()), "APP_SETTINGS", "reactor")));
        shots.add(new Shot("phone-installed", () -> show(view(PhoneView.State.IDLE, base()), "INSTALLED", "")));
        shots.add(new Shot("phone-install-offer", () -> show(view(PhoneView.State.IDLE, offer(false)), "HOME", "")));
        shots.add(new Shot("phone-publish-offer", () -> show(view(PhoneView.State.IDLE, offer(true)), "HOME", "")));
        shots.add(new Shot("phone-me", () -> {
            PhoneView v = view(PhoneView.State.IDLE, AppsView.NONE);
            show(
                withPhone(
                    v,
                    new PhoneView.Phone(
                        PhoneKind.ME_PHONE.ordinal(),
                        "Base",
                        true,
                        Long.MAX_VALUE,
                        false,
                        List.of(),
                        0,
                        true)),
                "HOME",
                "");
        }));
        // The board's other ME Phone, out of its network's range: on an antenna at Medium, "Base" out of range.
        shots.add(new Shot("phone-me-out", () -> {
            PhoneView v = view(PhoneView.State.IDLE, AppsView.NONE);
            show(
                withPhone(
                    v,
                    new PhoneView.Phone(
                        PhoneKind.ME_PHONE.ordinal(),
                        "Base",
                        false,
                        Long.MAX_VALUE,
                        false,
                        List.of(),
                        0,
                        true),
                    SignalLevel.MEDIUM,
                    new PhoneView.Via(PhoneView.Via.Kind.ANTENNA, 2)),
                "HOME",
                "");
        }));
        int h = com.enn3developer.becom.BeCom.proxy.guiHeight();
        shots.add(new Shot("telephone", () -> {
            view = desk(PhoneView.State.OFF_HOOK, false);
            open(telephone, com.enn3developer.becom.gui.phone.DeskPhoneUi.scaleFor(false, h));
            telephone.showForTest("NORMAL", 0, "4829");
        }));
        shots.add(new Shot("telephone-calls", () -> telephone.showForTest("CALLS", 0, "")));
        shots.add(new Shot("telephone-menu", () -> telephone.showForTest("MENU", 0, "")));
        shots.add(new Shot("telephone-blocked", () -> telephone.showForTest("MENU", 2, "")));
        shots.add(new Shot("telephone-owner-only", () -> {
            view = withManage(desk(PhoneView.State.OFF_HOOK, false), false);
            telephone.showForTest("MENU", 0, "");
        }));
        shots.add(new Shot("videophone", () -> {
            view = desk(PhoneView.State.CONNECTED, true);
            open(videophone, com.enn3developer.becom.gui.phone.DeskPhoneUi.scaleFor(true, h));
            videophone.showForTest("NORMAL", 0, "");
        }));
        shots.add(
            new Shot(
                "walkie",
                () -> open(
                    new com.enn3developer.becom.gui.radio.RadioUi(
                        true,
                        () -> new com.enn3developer.becom.gui.radio.RadioUi.State(
                            4,
                            true,
                            true,
                            true,
                            false,
                            61,
                            List.of(),
                            net.minecraft.util.StatCollector
                                .translateToLocalFormatted("becom.ui.radio.walkie_note", 80)),
                        d -> {},
                        on -> {}),
                    com.enn3developer.becom.gui.radio.RadioUi.scale(h))));
        shots.add(
            new Shot(
                "transceiver",
                () -> open(
                    new com.enn3developer.becom.gui.radio.RadioUi(
                        false,
                        () -> new com.enn3developer.becom.gui.radio.RadioUi.State(
                            3,
                            true,
                            true,
                            false,
                            true,
                            0,
                            List.of("~128", "2", "16"),
                            net.minecraft.util.StatCollector
                                .translateToLocalFormatted("becom.ui.radio.transceiver_note", 8)),
                        d -> {},
                        on -> {}),
                    com.enn3developer.becom.gui.radio.RadioUi.scale(h))));
        shots.add(
            new Shot(
                "antenna",
                () -> open(
                    new com.enn3developer.becom.gui.machine.AntennaUi(PhoneGallery::antenna, on -> {}),
                    com.enn3developer.becom.gui.machine.AntennaUi.scale(h))));
        shots.add(
            new Shot(
                "router",
                () -> open(
                    new com.enn3developer.becom.gui.machine.RouterUi(PhoneGallery::router),
                    com.enn3developer.becom.gui.machine.RouterUi.scale(h))));
        shots.add(
            new Shot("me-bridge", () -> readout(PhoneGallery::bridge, "gt.blockmachines.becom.me_bridge.name", null)));
        shots.add(
            new Shot("relay", () -> readout(PhoneGallery::relay, "gt.blockmachines.becom.portal_relay.name", "HV")));
        shots.add(
            new Shot("dish", () -> readout(PhoneGallery::dish, "gt.blockmachines.becom.satellite_dish.name", null)));
        shots.add(
            new Shot(
                "tooltips",
                () -> net.minecraft.client.Minecraft.getMinecraft()
                    .displayGuiScreen(new TooltipGallery(false))));
        shots.add(
            new Shot(
                "scanner",
                () -> net.minecraft.client.Minecraft.getMinecraft()
                    .displayGuiScreen(new TooltipGallery(true))));
        shots.add(
            new Shot(
                "exchange",
                () -> open(
                    new com.enn3developer.becom.gui.machine.ExchangeUi(PhoneGallery::exchange),
                    com.enn3developer.becom.gui.machine.ExchangeUi.scale(h))));
        shots.add(
            new Shot(
                "orbit",
                () -> open(
                    new com.enn3developer.becom.gui.machine.OrbitUi(PhoneGallery::orbit, () -> {}),
                    com.enn3developer.becom.gui.machine.OrbitUi.scale(h))));
        shots.add(
            new Shot("launch-pad", () -> readout(PhoneGallery::pad, "gt.blockmachines.becom.launch_pad.name", null)));
        return shots;
    }

    /** A machine's readout on a panel, as its screen shows it. */
    private void readout(java.util.function.Supplier<InfoLines> info, String title, String chip) {
        String lines = String.join(
            "\n",
            info.get()
                .encode());
        open(
            ReadoutUi.panel(() -> lines, net.minecraft.util.StatCollector.translateToLocal(title), chip),
            ReadoutUi.panelScale(com.enn3developer.becom.BeCom.proxy.guiHeight()));
    }

    private static InfoLines.Key level(SignalLevel level) {
        return new InfoLines.Key(
            "becom.level." + level.name()
                .toLowerCase(java.util.Locale.ROOT));
    }

    private static void backhaul(InfoLines info, String label, String cable, int used, int rate) {
        info.add("becom.info.backhaul_value", new InfoLines.Key("becom.ui.cable." + cable), used, rate)
            .row(label);
    }

    /**
     * An ME Bridge as on the Gateways board: online, six ME Phones linked, two in calls; under the line, what it says
     * when something's wrong, as the board lists it.
     */
    private static InfoLines bridge() {
        InfoLines info = new InfoLines();
        info.add("becom.info.online")
            .status();
        info.add("becom.info.eu_t", 512)
            .right();
        info.add("becom.bridge.channel_held")
            .row("becom.info.row.me_network");
        info.add("becom.bridge.linked", 6)
            .row("becom.info.row.me_phones");
        info.add("becom.bridge.in_calls", 2)
            .sub();
        info.add("becom.info.of_slots", 2, 8)
            .bar("becom.info.row.calls", 0.25);
        backhaul(info, "becom.info.row.backhaul", "coaxial", 4, 64);
        info.add("becom.info.to_router", "100, 70, 200")
            .sub();
        info.problem("becom.bridge.no_channel");
        info.problem("becom.info.no_backhaul");
        info.warn("becom.bridge.calls_full_th", 9);
        return info;
    }

    /**
     * A Portal Relay as on the Gateways board: paired through a Nether portal, both backhauls; under the line, what it
     * says when something's wrong, as the board lists it.
     */
    private static InfoLines relay() {
        InfoLines info = new InfoLines();
        info.add("becom.relay.status_paired")
            .status();
        info.add("becom.info.eu_t", 32)
            .right();
        info.add("becom.relay.portal_kind", new InfoLines.Key("becom.portal_name.nether"), 3)
            .row("becom.info.row.portal");
        info.add("becom.relay.far_side", "MV", "12, 64, 25", "Nether")
            .row("becom.info.row.far_side");
        info.add("becom.relay.hop_fixed", level(SignalLevel.HIGH))
            .level("becom.info.row.hop", SignalLevel.HIGH);
        info.add("becom.info.of_slots", 5, 16)
            .bar("becom.info.row.calls", 5 / 16.0);
        backhaul(info, "becom.info.row.backhaul", "fibre", 10, 256);
        backhaul(info, "becom.info.row.far_backhaul", "coaxial", 10, 64);
        info.problem("becom.relay.no_portal_within", 4);
        info.problem("becom.relay.unpaired");
        info.warn("becom.relay.lower_tier", "MV", "MV", 16, 48);
        return info;
    }

    /**
     * A Satellite Dish as on the Machines board: a Low hop, two maintenance problems, its backhaul; under the line,
     * what it says when something's wrong, as the board lists it.
     */
    private static InfoLines dish() {
        InfoLines info = new InfoLines();
        info.add("becom.dish.hop_status", "10", level(SignalLevel.LOW))
            .status(SignalLevel.LOW);
        info.add("becom.dish.calls_right", 9, 32)
            .right();
        info.add("becom.dish.overhead_calls", 18, 21, 36)
            .row("becom.info.row.satellites");
        info.add("becom.dish.sky_clear")
            .good("becom.info.row.sky");
        info.problem("becom.dish.problems_short", 2, "4")
            .row("becom.info.row.maintenance");
        info.add("becom.info.backhaul_cable", new InfoLines.Key("becom.ui.cable.fibre"))
            .row("becom.info.row.backhaul");
        info.problem("becom.dish.too_few_short", 3, 5);
        info.problem("becom.info.no_backhaul");
        return info;
    }

    /** A Launch Pad as on the Machines board: launching, two rockets waiting, the Overworld's sky and the Moon's. */
    private static InfoLines pad() {
        InfoLines info = new InfoLines();
        info.add("becom.launch_pad.status_launching")
            .status();
        info.add("becom.launch_pad.seconds", 18, 30)
            .right();
        info.add("")
            .bar("", 0.6);
        info.add("becom.launch_pad.rockets", 2)
            .row("becom.info.row.waiting");
        info.add("becom.launch_pad.litres", 3400)
            .row("becom.info.row.fuel");
        info.add("becom.launch_pad.launches", 3)
            .sub();
        info.add("becom.launch_pad.in_orbit")
            .section();
        info.add("becom.launch_pad.count", 18)
            .value("Overworld", SignalLevel.MEDIUM);
        info.add("becom.launch_pad.orbit_calls", 21, 36, "12")
            .sub();
        info.add("becom.launch_pad.count", 3)
            .value("Moon", SignalLevel.NONE);
        info.add("becom.launch_pad.dish_needs", 5)
            .sub();
        return info;
    }

    /** The Overworld's sky as on the Orbit board, with the Moon's and Mars's under it. */
    private static com.enn3developer.becom.gui.OrbitView orbit() {
        List<Integer> falls = new ArrayList<>();
        for (int day : new int[] { 12, 21, 29, 29, 29, 44, 52 }) falls.add(day);
        return new com.enn3developer.becom.gui.OrbitView(
            List.of(
                new com.enn3developer.becom.gui.OrbitView.Sky(
                    "Overworld",
                    18,
                    SignalLevel.MEDIUM,
                    5,
                    21,
                    36,
                    2,
                    List.of(new com.enn3developer.becom.gui.OrbitView.Uplink("Moon", 1)),
                    9,
                    ""),
                new com.enn3developer.becom.gui.OrbitView.Sky(
                    "Moon",
                    3,
                    SignalLevel.NONE,
                    5,
                    0,
                    0,
                    0,
                    List.of(),
                    0,
                    "Overworld"),
                new com.enn3developer.becom.gui.OrbitView.Sky(
                    "Mars",
                    0,
                    SignalLevel.NONE,
                    5,
                    0,
                    0,
                    0,
                    List.of(),
                    0,
                    "Overworld")),
            "Overworld",
            new com.enn3developer.becom.logic.space.Forecast(falls, 14, SignalLevel.LOW, 29, 3, 36, 26),
            2,
            100);
    }

    /** An Exchange as on the Router board: two MV racks and an LV, five things joined, one too far. */
    private static com.enn3developer.becom.gui.ExchangeView exchange() {
        int router = com.enn3developer.becom.logic.net.NodeKind.ROUTER.ordinal();
        return new com.enn3developer.becom.gui.ExchangeView(
            true,
            true,
            "Steve",
            100,
            64,
            -100,
            9,
            72,
            240,
            5,
            47,
            1,
            List.of(1, 1, 0),
            List.of(
                new com.enn3developer.becom.gui.ExchangeView.Joined(
                    com.enn3developer.becom.logic.net.NodeKind.ANTENNA_T3.ordinal(),
                    -1,
                    -1,
                    160,
                    90,
                    -100,
                    List.of(0, 0, 1),
                    300,
                    256,
                    96),
                new com.enn3developer.becom.gui.ExchangeView.Joined(
                    com.enn3developer.becom.logic.net.NodeKind.EXCHANGE.ordinal(),
                    -1,
                    -1,
                    900,
                    64,
                    -120,
                    List.of(0, 0, 2),
                    800,
                    512,
                    120),
                new com.enn3developer.becom.gui.ExchangeView.Joined(
                    router,
                    -1,
                    -1,
                    20,
                    64,
                    -100,
                    List.of(2, 0, 0),
                    90,
                    32,
                    12),
                new com.enn3developer.becom.gui.ExchangeView.Joined(
                    router,
                    -1,
                    -1,
                    100,
                    64,
                    -410,
                    List.of(1, 0, 0),
                    310,
                    0,
                    0),
                new com.enn3developer.becom.gui.ExchangeView.Joined(
                    com.enn3developer.becom.logic.net.NodeKind.SATELLITE_DISH.ordinal(),
                    -1,
                    -1,
                    110,
                    70,
                    -95,
                    List.of(0, 0, 1),
                    12,
                    256,
                    24)));
    }

    /**
     * An MV Router as on its board: a bus of Phone Cable with two Telephones, one in a call, and a bus of
     * Coaxial with a Videophone, a computer, an Exchange and an ME Bridge.
     */
    private static com.enn3developer.becom.gui.RouterView router() {
        com.enn3developer.becom.gui.RouterView.Now idle = com.enn3developer.becom.gui.RouterView.Now.IDLE;
        com.enn3developer.becom.gui.RouterView.Now joined = com.enn3developer.becom.gui.RouterView.Now.JOINED;
        return new com.enn3developer.becom.gui.RouterView(
            1,
            true,
            "Steve",
            1,
            32,
            List.of(
                new com.enn3developer.becom.gui.RouterView.Bus(0, 14, 128, 16, 8, 0, 2, 0, 0),
                new com.enn3developer.becom.gui.RouterView.Bus(1, 40, 512, 64, 0, 10, 2, 1, 1)),
            List.of(
                new com.enn3developer.becom.gui.RouterView.Device(4, -1, 300210, 1, idle, ""),
                new com.enn3developer.becom.gui.RouterView.Device(3, -1, 300417, 1, idle, ""),
                new com.enn3developer.becom.gui.RouterView.Device(
                    0,
                    -1,
                    482913,
                    0,
                    com.enn3developer.becom.gui.RouterView.Now.CALL,
                    "Alex"),
                new com.enn3developer.becom.gui.RouterView.Device(0, -1, 482914, 0, idle, ""),
                new com.enn3developer.becom.gui.RouterView.Device(
                    -1,
                    com.enn3developer.becom.logic.net.NodeKind.EXCHANGE.ordinal(),
                    -1,
                    1,
                    joined,
                    ""),
                new com.enn3developer.becom.gui.RouterView.Device(
                    -1,
                    com.enn3developer.becom.logic.net.NodeKind.ME_BRIDGE.ordinal(),
                    -1,
                    1,
                    joined,
                    "")));
    }

    /** An Antenna T2 as on its board: two antennas in reach, one out of reach for radios, and a Fibre backhaul. */
    private static com.enn3developer.becom.gui.AntennaView antenna() {
        int cells = com.enn3developer.becom.logic.radio.Coverage.CELLS;
        byte[] vhf = new byte[cells * cells];
        byte[] uhf = new byte[cells * cells];
        for (int row = 0; row < cells; row++) {
            for (int col = 0; col < cells; col++) {
                double d = Math.hypot(row - cells / 2.0, col - cells / 2.0);
                boolean hill = row > cells * 0.6 && col > cells * 0.65;
                vhf[row * cells + col] = hill || d > 15 ? com.enn3developer.becom.logic.radio.Coverage.NONE
                    : (byte) (28 - d * 1.6);
                uhf[row * cells + col] = hill || d > 15 ? com.enn3developer.becom.logic.radio.Coverage.NONE
                    : (byte) (36 - d * 2);
            }
        }
        return new com.enn3developer.becom.gui.AntennaView(
            2,
            true,
            true,
            24,
            128,
            true,
            false,
            true,
            "Steve",
            9,
            12,
            List.of(
                new com.enn3developer.becom.gui.AntennaView.Link(120, 70, -340, 96, 14, 19),
                new com.enn3developer.becom.gui.AntennaView.Link(-60, 88, 15, 141, -20, 7)),
            List.of(
                new com.enn3developer.becom.gui.AntennaView.Bus(2, 210, 0, 1, 0, 256, 96),
                new com.enn3developer.becom.gui.AntennaView.Bus(0, 12, 3, 0, 0, 16, 4)),
            80,
            vhf,
            uhf,
            16,
            com.enn3developer.becom.logic.SignalLevel.MEDIUM.ordinal());
    }

    /** A desk phone's view: its speed dial, three standing at a Videophone, in a call with Alex and Bob. */
    private static PhoneView desk(PhoneView.State state, boolean video) {
        PhoneView v = view(state, AppsView.NONE);
        List<PhoneView.Member> members = state == PhoneView.State.CONNECTED ? List.of(
            new PhoneView.Member(482917, "Alex", SignalLevel.HIGH, false),
            new PhoneView.Member(300417, "Bob", SignalLevel.MEDIUM, false)) : List.of();
        return new PhoneView(
            video ? 300417 : 482913,
            v.owner(),
            v.mayManage(),
            state,
            v.otherName(),
            v.otherNumber(),
            v.hearing(),
            v.doNotDisturb(),
            true,
            v.contacts(),
            v.recent(),
            v.blocked(),
            false,
            true,
            false,
            SignalLevel.NONE,
            members,
            false,
            new PhoneView.Video(video, true, false, false, video),
            PhoneView.Battery.NONE,
            PhoneView.Via.NONE,
            AppsView.NONE,
            new PhoneView.Phone(
                (video ? PhoneKind.VIDEOPHONE : PhoneKind.TELEPHONE).ordinal(),
                "",
                false,
                0,
                video,
                List.of(482917, 300417, 606212, 0),
                video ? 3 : 0,
                true));
    }

    private static PhoneView withManage(PhoneView v, boolean manage) {
        return new PhoneView(
            v.number(),
            v.owner(),
            manage,
            v.state(),
            v.otherName(),
            v.otherNumber(),
            v.hearing(),
            v.doNotDisturb(),
            v.hidden(),
            v.contacts(),
            v.recent(),
            v.blocked(),
            v.carried(),
            v.on(),
            v.silent(),
            v.service(),
            v.members(),
            v.offline(),
            v.video(),
            v.battery(),
            v.via(),
            v.apps(),
            v.phone());
    }

    private void show(PhoneView v, String page, String app) {
        view = v;
        ui.showForTest(page, app);
    }

    // region Made-up data, as on the boards

    /** A T2 antenna 90 blocks east and 90 north of the player, at High: 48 dB down to 20, 4 dB short of Max. */
    private static AnalyzerPacket.Reading antennaReading() {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        return new AnalyzerPacket.Reading(
            true,
            0,
            (int) player.posX + 90,
            70,
            (int) player.posZ - 90,
            2,
            48,
            20,
            5,
            3,
            0,
            0,
            SignalLevel.HIGH);
    }

    private static final String REACTOR_ICON = "00000000" + "0aaaaaa0"
        + "0a0000a0"
        + "0a0aa0a0"
        + "0a0aa0a0"
        + "0a0000a0"
        + "0aaaaaa0"
        + "00000000";
    private static final String LIGHTS_ICON = "000ee000" + "00eeee00"
        + "00eeee00"
        + "00eeee00"
        + "000ee000"
        + "00077000"
        + "00077000"
        + "00000000";
    private static final String NOTES_ICON = "00000000" + "06666660"
        + "00000000"
        + "06666660"
        + "00000000"
        + "06666000"
        + "00000000"
        + "00000000";
    // Tank Levels has no icon of its own: the store's default is the boards' tank, light blue on its accent.
    private static final String TANK_ICON = "";
    private static final int TANK_ACCENT = 0x2f4f8f;
    // The store board and Tank Levels' page board describe it differently; each shot shows its board's.
    private static final String TANK_ABOUT = "Every tank in your base, in litres";
    private static final String TANK_ABOUT_PAGE = "Every tank in your base, in litres, with an alarm when one runs low. "
        + "Needs Alex's tank computer on your team's network.";

    private static AppsView.App reactor() {
        return new AppsView.App(
            "reactor",
            "Reactor",
            "1.2",
            "Steve",
            "Watch and SCRAM IC2 reactors",
            0x24504a,
            REACTOR_ICON,
            AppManifest.Use.NET.bit() | AppManifest.Use.NOTIFY.bit(),
            true,
            "",
            true,
            14,
            36_000,
            3 * 1024,
            12 * 1024,
            64 * 1024,
            0);
    }

    private static AppsView.App lights() {
        return new AppsView.App(
            "lights",
            "Base Lights",
            "0.3",
            "Carol",
            "Switch lamps by redstone",
            0x6a5a1a,
            LIGHTS_ICON,
            AppManifest.Use.NET.bit(),
            true,
            "",
            false,
            0,
            0,
            1024,
            0,
            64 * 1024,
            0);
    }

    private static AppsView base() {
        return base(TANK_ABOUT);
    }

    /** The phone's apps, and the store with Tank Levels described as {@code tankAbout}. */
    private static AppsView base(String tankAbout) {
        List<AppsView.StoreApp> store = List.of(
            new AppsView.StoreApp(
                "reactor",
                "Reactor",
                "1.2",
                "Steve",
                "Watch and SCRAM IC2 reactors",
                41,
                3 * 1024,
                "1.2",
                REACTOR_ICON,
                AppManifest.Use.NET.bit() | AppManifest.Use.NOTIFY.bit(),
                5,
                false,
                false,
                0x24504a),
            new AppsView.StoreApp(
                "tank",
                "Tank Levels",
                "1.4",
                "Alex",
                tankAbout,
                28,
                4 * 1024,
                "",
                TANK_ICON,
                AppManifest.Use.NET.bit() | AppManifest.Use.NOTIFY.bit(),
                2,
                false,
                false,
                TANK_ACCENT),
            new AppsView.StoreApp(
                "lights",
                "Base Lights",
                "0.4",
                "Carol",
                "Switch lamps by redstone",
                17,
                1024,
                "0.3",
                LIGHTS_ICON,
                AppManifest.Use.NET.bit(),
                9,
                true,
                false,
                0x6a5a1a),
            new AppsView.StoreApp(
                "notes",
                "Notes",
                "2.0",
                "Dana",
                "Shared notes for your team",
                9,
                2 * 1024,
                "",
                NOTES_ICON,
                AppManifest.Use.NET.bit() | AppManifest.Use.STORAGE.bit(),
                30,
                false,
                false,
                0x5a3a2a));
        return new AppsView(
            List.of(reactor(), lights()),
            AppsView.Screen.NONE,
            AppsView.Offer.NONE,
            new AppsView.Notice("reactor", "Hull heat 80%", ""),
            store,
            "",
            0,
            0,
            store.size());
    }

    private static AppsView with(AppsView.Screen screen) {
        AppsView b = base();
        return new AppsView(b.installed(), screen, b.offer(), b.notice(), b.store(), "", 0, 0, b.total());
    }

    private static AppsView running() {
        List<AppTree.Widget> w = new ArrayList<>();
        w.add(AppTree.Widget.text(AppTree.Kind.HEADING, "Main reactor"));
        w.add(
            new AppTree.Widget(
                AppTree.Kind.TOGGLE,
                "run",
                "Running",
                "true",
                0,
                AppTree.Level.PLAIN,
                AppTree.Style.PLAIN,
                ""));
        w.add(
            new AppTree.Widget(
                AppTree.Kind.VALUE,
                "",
                "Output",
                "8 192 EU/t",
                0,
                AppTree.Level.PLAIN,
                AppTree.Style.PLAIN,
                ""));
        w.add(
            new AppTree.Widget(
                AppTree.Kind.PROGRESS,
                "",
                "Hull heat",
                "",
                0.34,
                AppTree.Level.PLAIN,
                AppTree.Style.PLAIN,
                ""));
        // The fuel rods as the board lists them: items without an icon, each with what's left, the low one red.
        w.add(AppTree.Widget.text(AppTree.Kind.HEADING, "Fuel rods"));
        w.add(rod("Quad Uranium", "71%", AppTree.Level.PLAIN));
        w.add(rod("Quad Uranium", "71%", AppTree.Level.PLAIN));
        w.add(rod("Quad Thorium", "6%", AppTree.Level.LOW));
        w.add(
            new AppTree.Widget(
                AppTree.Kind.BUTTON,
                "refuel",
                "Refuel",
                "",
                0,
                AppTree.Level.PLAIN,
                AppTree.Style.PLAIN,
                ""));
        w.add(
            new AppTree.Widget(
                AppTree.Kind.BUTTON,
                "scram",
                "SCRAM",
                "",
                0,
                AppTree.Level.PLAIN,
                AppTree.Style.DANGER,
                ""));
        w.add(
            new AppTree.Widget(
                AppTree.Kind.ITEM,
                "",
                "Iron Ingot",
                "minecraft:iron_ingot",
                0,
                AppTree.Level.PLAIN,
                AppTree.Style.PLAIN,
                "12 480"));
        w.add(
            new AppTree.Widget(
                AppTree.Kind.INPUT,
                "search",
                "",
                "Search items",
                0,
                AppTree.Level.PLAIN,
                AppTree.Style.PLAIN,
                ""));
        // Its last data came 2 seconds ago, in the world's time as the server stamps it.
        long data = Math.max(1, com.enn3developer.becom.BeCom.proxy.worldTime() - 2 * 20);
        return with(new AppsView.Screen("reactor", AppsView.State.RUNNING, "", w, data));
    }

    private static AppTree.Widget rod(String name, String left, AppTree.Level level) {
        return new AppTree.Widget(AppTree.Kind.ITEM, "", name, "", 0, level, AppTree.Style.PLAIN, left);
    }

    private static AppsView state(AppsView.State state, String error) {
        return with(new AppsView.Screen("reactor", state, error, List.of(), 0));
    }

    private static AppsView offer(boolean publish) {
        AppsView b = base();
        AppsView.Offer offer = publish
            ? new AppsView.Offer(
                1,
                true,
                "Tank Levels",
                "1.4",
                "Alex",
                "computer 300210",
                4 * 1024,
                AppManifest.Use.NET.bit() | AppManifest.Use.NOTIFY.bit(),
                TANK_ACCENT,
                TANK_ICON)
            : new AppsView.Offer(
                1,
                false,
                "Reactor",
                "1.2",
                "Steve",
                "a computer at 120, 64, -30",
                3 * 1024,
                AppManifest.Use.NET.bit(),
                0x24504a,
                REACTOR_ICON);
        return new AppsView(b.installed(), b.screen(), offer, b.notice(), b.store(), "", 0, 0, b.total());
    }

    private static PhoneView view(PhoneView.State state, AppsView apps) {
        long now = System.currentTimeMillis();
        List<PhoneView.Contact> contacts = List.of(
            new PhoneView.Contact(482917, "Alex", PhoneKind.MOBILE.ordinal()),
            new PhoneView.Contact(300417, "Bob", PhoneKind.MOBILE.ordinal()),
            new PhoneView.Contact(774001, "Carol", PhoneKind.MOBILE.ordinal()),
            new PhoneView.Contact(606212, "Steve's base", PhoneKind.TELEPHONE.ordinal()),
            new PhoneView.Contact(210988, "Dana", PhoneKind.MOBILE.ordinal()));
        List<PhoneView.Recent> recent = List.of(
            new PhoneView.Recent(now - 4 * 60_000, 774001, "Carol", PhoneRecord.Direction.MISSED),
            new PhoneView.Recent(now - 50 * 60_000, 482917, "Alex", PhoneRecord.Direction.OUTGOING),
            new PhoneView.Recent(now - 3 * 3600_000, 300417, "Bob", PhoneRecord.Direction.INCOMING));
        List<PhoneView.Blocked> blocked = List.of(
            new PhoneView.Blocked(UUID.nameUUIDFromBytes("x".getBytes()), "Griefer"),
            new PhoneView.Blocked(UUID.nameUUIDFromBytes("y".getBytes()), "Spammer"));
        List<PhoneView.Member> members = state == PhoneView.State.CONNECTED ? List.of(
            new PhoneView.Member(774001, "Carol", SignalLevel.HIGH, false),
            new PhoneView.Member(300417, "Bob", SignalLevel.MEDIUM, true)) : List.of();
        PhoneView.Battery battery = new PhoneView.Battery(
            744_000,
            1_200_000,
            155,
            39,
            false,
            List.of(42, 24, 21, 9, 4),
            false,
            false,
            true,
            List.of(new PhoneView.AppShare("Reactor", 4)));
        return new PhoneView(
            482913,
            "Steve",
            true,
            state,
            "Carol",
            774001,
            SignalLevel.HIGH,
            false,
            false,
            contacts,
            recent,
            blocked,
            true,
            true,
            true,
            SignalLevel.HIGH,
            members,
            false,
            new PhoneView.Video(true, true, false, false, false),
            battery,
            new PhoneView.Via(PhoneView.Via.Kind.ANTENNA, 2),
            apps,
            new PhoneView.Phone(
                PhoneKind.MOBILE.ordinal(),
                "",
                false,
                0,
                false,
                List.of(300417, 482917, 0, 0),
                2,
                true));
    }

    private static PhoneView withPhone(PhoneView v, PhoneView.Phone phone) {
        return withPhone(v, phone, SignalLevel.MAX, new PhoneView.Via(PhoneView.Via.Kind.ME_NETWORK, 0));
    }

    /** {@code v} on ME Phone 119200 with {@code phone}, its signal at {@code service} through {@code via}. */
    private static PhoneView withPhone(PhoneView v, PhoneView.Phone phone, SignalLevel service, PhoneView.Via via) {
        return new PhoneView(
            119200,
            v.owner(),
            v.mayManage(),
            v.state(),
            v.otherName(),
            v.otherNumber(),
            v.hearing(),
            v.doNotDisturb(),
            v.hidden(),
            v.contacts(),
            v.recent(),
            v.blocked(),
            v.carried(),
            v.on(),
            v.silent(),
            service,
            v.members(),
            v.offline(),
            v.video(),
            v.battery(),
            via,
            v.apps(),
            phone);
    }

    // endregion
}
