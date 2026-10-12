package com.enn3developer.becom.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.radio.AntennaNetwork;
import com.enn3developer.becom.logic.radio.RadioHops;
import com.enn3developer.becom.logic.radio.Terrain;
import com.enn3developer.gtnhvoice.api.server.ActivationIds;
import com.enn3developer.gtnhvoice.api.server.SourceState;
import com.enn3developer.gtnhvoice.api.server.group.IGroup;
import com.enn3developer.gtnhvoice.api.server.group.RoutingContext;

/**
 * DESIGN.md 6.5's budgets at a busy server's scale, timed on this machine: 64 T3 antennas over hilly ground, 200
 * players each holding a walkie and a phone, 30 radios keyed at once, and the voice routing on GTNH Voice's thread.
 * Not part of the build, since timings depend on the machine: {@code ./gradlew loadTest} runs it and prints the report.
 */
@Tag("load")
class BudgetLoadTest {

    private static final int SIDE = 8;
    private static final int SPACING = 240;
    private static final int PLAYERS = 200;
    private static final int TRANSMITTERS = 30;
    private static final int LISTENERS = 20;
    private static final int TICKS_PER_SECOND = 20;
    private static final int FRAMES_PER_SECOND = 50;
    /**
     * Timed survey runs: 140 ticks or so. A tick the machine stalls, a pause or another process taking the core, runs
     * over by as much as the stall, which BeCom can't help: on this test's VM, up to a few in a run. So the bound holds
     * p95, which a step grown too long would push over as it does nearly every tick, and p99 and the slowest are only
     * reported.
     */
    private static final int SURVEY_RUNS = 10;
    /**
     * How far a tick may run past the survey budget: the budget is checked between steps, and a step, one bearing or
     * one link, takes a few microseconds, so half a millisecond leaves room for the clock and nothing more.
     */
    private static final long STEP_SLACK_NANOS = 500_000;
    private static final double EYES = 1.62;
    private static final double MAST = 24;

    /** Rolling hills from about 34 to 94: a survey's cost doesn't depend on the heights, only on reading them. */
    private static final Terrain HILLS = (x, z) -> 64 + 20 * Math.sin(x / 97.0) * Math.cos(z / 131.0)
        + 10 * Math.sin((x + z) / 53.0);

    private static final AntennaNetwork.Dimensions OVERWORLD = new AntennaNetwork.Dimensions() {

        @Override
        public Terrain terrain(int dimension) {
            return HILLS;
        }

        @Override
        public boolean noSky(int dimension) {
            return false;
        }
    };

    private final Tunables t = Tunables.defaults();
    private final Random random = new Random(6_5);
    private final StringBuilder report = new StringBuilder();

    private static RadioHops.End antennaAt(int x, int z) {
        double ground = HILLS.heightAt(x, z);
        return new RadioHops.End(x + 0.5, ground + MAST, z + 0.5, 0, ground + 1);
    }

    private RadioHops.End playerSomewhere() {
        int x = random.nextInt(SIDE * SPACING), z = random.nextInt(SIDE * SPACING);
        double ground = HILLS.heightAt(x, z);
        return new RadioHops.End(x + 0.5, ground + EYES, z + 0.5, 0, ground);
    }

    private static String ms(double nanos) {
        return String.format(Locale.ROOT, "%.3f ms", nanos / 1e6);
    }

    private static String us(double nanos) {
        return String.format(Locale.ROOT, "%.1f µs", nanos / 1e3);
    }

    private void line(String format, Object... args) {
        report.append(String.format(Locale.ROOT, format, args))
            .append('\n');
    }

    /** Every antenna seen, then ticks until their surveys are done; each tick's time, in nanoseconds. */
    private long[] survey(AntennaNetwork network, List<NodeRef> refs, List<RadioHops.End> ends, long budget) {
        UUID owner = new UUID(0, 1);
        List<Long> ticks = new ArrayList<>();
        do {
            for (int i = 0; i < refs.size(); i++) network.seen(refs.get(i), 3, ends.get(i), false, owner);
            long start = System.nanoTime();
            network.tick(budget, System::nanoTime);
            ticks.add(System.nanoTime() - start);
        } while (network.surveysQueued() > 0);
        return ticks.stream()
            .mapToLong(Long::longValue)
            .toArray();
    }

    private AntennaNetwork surveyed(List<NodeRef> refs, List<RadioHops.End> ends, long budget, long[][] times) {
        AntennaNetwork network = new AntennaNetwork(t, OVERWORLD, (a, b) -> false);
        times[0] = survey(network, refs, ends, budget);
        return network;
    }

    @Test
    void budgetsHoldAtABusyServersScale() {
        long budget = (long) (t.surveyBudgetMillis() * 1_000_000);
        List<NodeRef> refs = new ArrayList<>();
        List<RadioHops.End> ends = new ArrayList<>();
        for (int i = 0; i < SIDE; i++) {
            for (int j = 0; j < SIDE; j++) {
                int x = i * SPACING + SPACING / 2, z = j * SPACING + SPACING / 2;
                RadioHops.End end = antennaAt(x, z);
                refs.add(new NodeRef(0, BlockPos.pack(x, (int) end.y(), z)));
                ends.add(end);
            }
        }
        line(
            "BeCom load test (DESIGN.md 6.5), Java %s, %d cores",
            System.getProperty("java.version"),
            Runtime.getRuntime()
                .availableProcessors());

        // Surveys: once as a warm-up for the JIT, then timed over several runs.
        long[][] times = new long[1][];
        surveyed(refs, ends, budget, times);
        AntennaNetwork network = null;
        List<Long> timed = new ArrayList<>();
        for (int run = 0; run < SURVEY_RUNS; run++) {
            network = surveyed(refs, ends, budget, times);
            for (long tick : times[0]) timed.add(tick);
        }
        long[] ticks = timed.stream()
            .mapToLong(Long::longValue)
            .sorted()
            .toArray();
        long total = Arrays.stream(ticks)
            .sum();
        double perSurvey = (double) total / (refs.size() * SURVEY_RUNS);
        long p95 = ticks[(int) Math.min(ticks.length - 1, Math.ceil(ticks.length * 0.95) - 1)];
        long p99 = ticks[(int) Math.min(ticks.length - 1, Math.ceil(ticks.length * 0.99) - 1)];
        long max = ticks[ticks.length - 1];
        double sustained = t.surveySeconds() * TICKS_PER_SECOND * (double) budget / perSurvey;
        line(
            "Surveys: %d T3 antennas %d blocks apart, %s a tick: all done in %d ticks (%.1f s)",
            refs.size(),
            SPACING,
            ms(budget),
            times[0].length,
            times[0].length / (double) TICKS_PER_SECOND);
        line(
            "  per tick: p95 %s, p99 %s, max %s; one T3 survey with its links: %s",
            ms(p95),
            ms(p99),
            ms(max),
            ms(perSurvey));
        line(
            "  the budget sustains about %.0f T3 antennas, each surveyed again every %d s",
            sustained,
            t.surveySeconds());
        int links = network.links(0)
            .size();
        line("  links found between antennas: %d", links);

        // Player access: each player holding a walkie and a phone, looked at once a second, spread over 20 ticks.
        AntennaNetwork.Mesh mesh = network.mesh(0);
        List<RadioHops.End> players = new ArrayList<>();
        for (int i = 0; i < PLAYERS; i++) players.add(playerSomewhere());
        UUID user = new UUID(0, 2);
        for (RadioHops.End p : players) {
            network.access(mesh, 0, p, user);
            network.reach(0, p, user, network.uhf(), false);
        }
        int rounds = 10;
        long start = System.nanoTime();
        for (int r = 0; r < rounds; r++) for (RadioHops.End p : players) network.access(mesh, 0, p, user);
        double vhf = (System.nanoTime() - start) / (double) (rounds * PLAYERS);
        start = System.nanoTime();
        for (int r = 0; r < rounds; r++)
            for (RadioHops.End p : players) network.reach(0, p, user, network.uhf(), false);
        double uhf = (System.nanoTime() - start) / (double) (rounds * PLAYERS);
        double accessPerTick = PLAYERS / (double) TICKS_PER_SECOND * (vhf + uhf);
        line(
            "Access: %d players among %d antennas: a walkie (VHF) %s, a phone (UHF) %s each",
            PLAYERS,
            mesh.antennas()
                .size(),
            us(vhf),
            us(uhf));
        line("  once a second, spread over 20 ticks: %s a tick", ms(accessPerTick));

        // Direct links: only for keyed transmitters, each listener in range looked at once a second.
        RadioHops hops = new RadioHops(t.radioModel(), t.surveyStep());
        double range = t.rangeToHold(t.walkieBudget());
        List<RadioHops.End[]> pairs = new ArrayList<>();
        for (int i = 0; i < TRANSMITTERS; i++) {
            RadioHops.End tx = playerSomewhere();
            for (int j = 0; j < LISTENERS; j++) {
                double angle = random.nextDouble() * 2 * Math.PI, d = random.nextDouble() * range;
                int x = (int) (tx.x() + d * Math.cos(angle)), z = (int) (tx.z() + d * Math.sin(angle));
                double ground = HILLS.heightAt(x, z);
                pairs.add(new RadioHops.End[] { tx, new RadioHops.End(x + 0.5, ground + EYES, z + 0.5, 0, ground) });
            }
        }
        for (RadioHops.End[] pair : pairs) hops.direct(HILLS, pair[0], pair[1], t.walkieBudget(), t.vhf(), 0, false);
        start = System.nanoTime();
        for (int r = 0; r < rounds; r++) {
            for (RadioHops.End[] pair : pairs)
                hops.direct(HILLS, pair[0], pair[1], t.walkieBudget(), t.vhf(), 0, false);
        }
        double direct = (System.nanoTime() - start) / (double) (rounds * pairs.size());
        double directPerTick = pairs.size() / (double) TICKS_PER_SECOND * direct;
        line(
            "Direct links: %d keyed radios, %d listeners each within %.0f blocks: %s a link, %s a tick",
            TRANSMITTERS,
            LISTENERS,
            range,
            us(direct),
            ms(directPerTick));

        // Routing, on GTNH Voice's network thread: every frame of every speaker.
        RoutingFixture fixture = new RoutingFixture();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < PLAYERS; i++) {
            RadioHops.End p = players.get(i);
            ids.add(fixture.player("p" + i, p.x(), p.y(), p.z(), 0));
        }
        RadioRouting radio = new RadioRouting(16, 6, 16, 100_000_000L, 3, () -> 0.99, System::nanoTime);
        RadioGroup channel = new RadioGroup(1, "Radio 1", radio);
        UUID speaker = ids.get(0);
        RadioSnapshot.Builder snapshot = RadioSnapshot.builder()
            .transmitter(speaker, 1);
        for (UUID listener : ids.subList(1, ids.size()))
            snapshot.link(speaker, new RadioSnapshot.Link(listener, null, 15, 0, 1));
        radio.publish(snapshot.build());
        int frames = 2_000;
        for (int i = 0; i < frames; i++) {
            fixture.route(channel, speaker, RadioGroup.ACTIVATION);
            fixture.sent.clear();
        }
        start = System.nanoTime();
        for (int i = 0; i < frames; i++) {
            fixture.route(channel, speaker, RadioGroup.ACTIVATION);
            fixture.sent.clear();
        }
        double radioFrame = (System.nanoTime() - start) / (double) frames;
        // The same frame sent flat to the same listeners by a group with no logic of its own: what GTNH Voice and this
        // fixture cost, which BeCom's routing can't do without.
        IGroup bare = new IGroup() {

            @Override
            public String getName() {
                return "bare";
            }

            @Override
            public String getDisplayName() {
                return "Bare";
            }

            @Override
            public void route(RoutingContext context) {
                for (UUID listener : ids.subList(1, ids.size())) {
                    context.sendTo(
                        context.getSessionsByPlayerUuid()
                            .get(listener),
                        SourceState.FLAT,
                        0,
                        0,
                        0);
                }
            }
        };
        for (int i = 0; i < frames; i++) {
            fixture.route(bare, speaker, RadioGroup.ACTIVATION);
            fixture.sent.clear();
        }
        start = System.nanoTime();
        for (int i = 0; i < frames; i++) {
            fixture.route(bare, speaker, RadioGroup.ACTIVATION);
            fixture.sent.clear();
        }
        double bareFrame = (System.nanoTime() - start) / (double) frames;
        fixture.route(channel, speaker, RadioGroup.ACTIVATION);
        int heard = fixture.sent.size();
        fixture.sent.clear();
        CallGroup call = new CallGroup("Call", 16, () -> 0.99);
        CallSnapshot.Builder calls = CallSnapshot.builder();
        List<UUID> members = ids.subList(100, 108);
        for (UUID a : members) for (UUID b : members) if (a != b) calls.route(a, new CallSnapshot.Route(b, 0, 1.0 / 3));
        call.publish(calls.build());
        for (int i = 0; i < frames; i++) {
            fixture.route(call, members.get(0), ActivationIds.DEFAULT);
            fixture.sent.clear();
        }
        start = System.nanoTime();
        for (int i = 0; i < frames; i++) {
            fixture.route(call, members.get(0), ActivationIds.DEFAULT);
            fixture.sent.clear();
        }
        double callFrame = (System.nanoTime() - start) / (double) frames;
        line(
            "Routing on GTNH Voice's thread: a radio frame to %d listeners %s; %d speakers at %d frames a second: %s of"
                + " each second",
            heard,
            us(radioFrame),
            TRANSMITTERS,
            FRAMES_PER_SECOND,
            ms(radioFrame * TRANSMITTERS * FRAMES_PER_SECOND));
        line(
            "  of which sending: the same frame sent flat to the same listeners with no routing logic takes %s",
            us(bareFrame));
        line("  a call frame among 8 people: %s", us(callFrame));
        System.out.print(report);

        // What DESIGN.md 6.5 promises: surveys keep to their budget, give or take one step, and the rest is small
        // beside a tick's 50 ms.
        assertEquals(PLAYERS - 1, heard, "every listener on the channel gets the frame");
        assertTrue(links > 0, "the antennas link up");
        assertTrue(p95 <= budget + STEP_SLACK_NANOS, "surveys keep to their budget: p95 " + ms(p95));
        assertTrue(accessPerTick + directPerTick < 5_000_000, "access and direct links stay small beside a tick");
    }
}
