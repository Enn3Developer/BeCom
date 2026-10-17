package com.enn3developer.becom.logic.radio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NodeRef;

/** Surveys within budget, links on both bands, access and the team lock. */
class AntennaNetworkTest {

    private static final int DIM = 0;
    private static final double GROUND = 64;
    private final Tunables t = Tunables.defaults();
    private final UUID owner = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final UUID teammate = UUID.randomUUID();
    private long clock;
    // The owner's team counts the teammate. Asked the wrong way round, the teammate's own team wouldn't count the
    // owner.
    private final BiPredicate<UUID, UUID> sameTeam = (o, p) -> o != null
        && (o.equals(p) || o.equals(owner) && p.equals(teammate));
    private final AntennaNetwork network = new AntennaNetwork(t, new AntennaNetwork.Dimensions() {

        @Override
        public Terrain terrain(int dimension) {
            return (x, z) -> GROUND;
        }

        @Override
        public boolean noSky(int dimension) {
            return false;
        }
    }, sameTeam);

    private static NodeRef at(int x, int z) {
        return new NodeRef(DIM, BlockPos.pack(x, 84, z));
    }

    /** An antenna head 20 blocks up its mast, its casings on the ground. */
    private static RadioHops.End head(NodeRef ref) {
        return new RadioHops.End(
            BlockPos.x(ref.pos()) + 0.5,
            BlockPos.y(ref.pos()) + 1,
            BlockPos.z(ref.pos()) + 0.5,
            0,
            GROUND);
    }

    private static RadioHops.End player(double x, double z) {
        return new RadioHops.End(x, GROUND + 1.62, z, -1.62, GROUND);
    }

    private void see(NodeRef ref, int tier, boolean locked) {
        network.seen(ref, tier, head(ref), locked, owner);
    }

    /** Ticks with every antenna in {@code working} reporting, and an unlimited survey budget. */
    private void run(int ticks, List<NodeRef> working, int tier) {
        for (int i = 0; i < ticks; i++) {
            for (NodeRef ref : working) see(ref, tier, false);
            network.tick(Long.MAX_VALUE, () -> clock);
        }
    }

    @Test
    void aNewAntennaSurveysAndLinksToAnotherInRangeOnBothBands() {
        NodeRef a = at(0, 0);
        NodeRef b = at(100, 0);
        run(2, List.of(a, b), 2);
        assertNotNull(
            network.antenna(a)
                .survey());
        AntennaNetwork.Link link = network.links(DIM)
            .get(AntennaNetwork.LinkKey.of(a.pos(), b.pos()));
        assertNotNull(link);
        assertTrue(
            link.vhf()
                .snr() >= t.levelLow());
        List<double[]> uhf = new ArrayList<>();
        network.forEachUhfLink(DIM, (x, y, snr) -> uhf.add(new double[] { snr }));
        assertEquals(1, uhf.size());
        AntennaNetwork.Mesh mesh = network.mesh(DIM);
        assertEquals(
            2,
            mesh.antennas()
                .size());
        assertTrue(
            mesh.vhf()
                .linkSnr(0, 1) >= t.levelLow());
    }

    @Test
    void antennasOutOfRangeDontLink() {
        NodeRef a = at(0, 0);
        NodeRef b = at(200, 0);
        run(2, List.of(a, b), 2);
        assertTrue(
            network.links(DIM)
                .isEmpty());
    }

    @Test
    void aDeviceInRangeGetsAnAccessLinkAndOneOutsideDoesnt() {
        NodeRef a = at(0, 0);
        run(2, List.of(a), 1);
        AntennaNetwork.Mesh mesh = network.mesh(DIM);
        double near = network.access(mesh, DIM, player(40.5, 0.5), stranger)[0];
        double far = network.access(mesh, DIM, player(80.5, 0.5), stranger)[0];
        assertTrue(near >= t.levelLow(), "near " + near);
        assertEquals(Double.NEGATIVE_INFINITY, far);
        AntennaNetwork.Best best = network.best(DIM, player(40.5, 0.5), stranger, network.vhf());
        assertNotNull(best);
        assertEquals(
            RadioModel.Loss.DISTANCE,
            best.losses()
                .biggest());
    }

    @Test
    void aDeviceAtAPositionThatIsntANumberReachesNoAntenna() {
        // Its distance is NaN, which the range check used to let through to a link with a NaN SNR.
        NodeRef a = at(0, 0);
        run(2, List.of(a), 1);
        RadioHops.End lost = new RadioHops.End(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        assertEquals(Double.NEGATIVE_INFINITY, network.access(network.mesh(DIM), DIM, lost, owner)[0]);
        assertTrue(
            network.reach(DIM, lost, owner, network.uhf(), false)
                .isEmpty());
        assertNull(network.best(DIM, lost, owner, network.vhf()));
    }

    @Test
    void aLockedAntennaServesItsOwnersTeamRefusesStrangersAndStillRelays() {
        NodeRef a = at(0, 0);
        NodeRef b = at(100, 0);
        for (int i = 0; i < 2; i++) {
            see(a, 2, true);
            see(b, 2, false);
            network.tick(Long.MAX_VALUE, () -> clock);
        }
        AntennaNetwork.Mesh mesh = network.mesh(DIM);
        int ia = mesh.antennas()
            .indexOf(network.antenna(a));
        assertEquals(Double.NEGATIVE_INFINITY, network.access(mesh, DIM, player(10.5, 0.5), stranger)[ia]);
        assertTrue(network.access(mesh, DIM, player(10.5, 0.5), owner)[ia] >= t.levelLow());
        assertTrue(network.access(mesh, DIM, player(10.5, 0.5), teammate)[ia] >= t.levelLow());
        // A locked antenna still relays calls along its links.
        assertTrue(
            mesh.vhf()
                .linkSnr(0, 1) >= t.levelLow());
    }

    @Test
    void anAntennaNotHeardFromForTwoSecondsIsOffline() {
        NodeRef a = at(0, 0);
        NodeRef b = at(100, 0);
        run(2, List.of(a, b), 2);
        long before = network.version();
        run(60, List.of(a), 2);
        assertTrue(network.version() > before);
        assertEquals(
            1,
            network.mesh(DIM)
                .antennas()
                .size());
        List<double[]> uhf = new ArrayList<>();
        network.forEachUhfLink(DIM, (x, y, snr) -> uhf.add(new double[] { snr }));
        assertTrue(uhf.isEmpty());
    }

    @Test
    void aChangedAntennaSurveysAgain() {
        NodeRef a = at(0, 0);
        run(2, List.of(a), 1);
        Survey first = network.antenna(a)
            .survey();
        // Upgraded in place: same spot, new tier, a survey reaching further.
        run(2, List.of(a), 3);
        Survey second = network.antenna(a)
            .survey();
        assertNotNull(second);
        assertTrue(second != first);
        assertTrue(second.steps() > first.steps());
    }

    @Test
    void surveysStayWithinTheBudgetAndStillFinish() {
        NodeRef a = at(0, 0);
        see(a, 3, false);
        // Each step costs a millisecond on this clock; the budget is two.
        long budget = 2_000_000;
        network.tick(budget, () -> clock += 1_000_000);
        assertNull(
            network.antenna(a)
                .survey());
        assertEquals(1, network.surveysQueued());
        for (int i = 0; i < 100 && network.surveysQueued() > 0; i++) {
            see(a, 3, false);
            network.tick(budget, () -> clock += 1_000_000);
        }
        assertNotNull(
            network.antenna(a)
                .survey());
    }

    @Test
    void surveysRefreshEveryPeriod() {
        NodeRef a = at(0, 0);
        run(2, List.of(a), 1);
        Survey first = network.antenna(a)
            .survey();
        run(t.surveySeconds() * 20 + 40, List.of(a), 1);
        assertTrue(
            network.antenna(a)
                .survey() != first);
    }

    @Test
    void aBlockOnTheHeadChangesItsCoverButKeepsItsSurveyAndLinks() {
        NodeRef a = at(0, 0);
        NodeRef b = at(100, 0);
        run(2, List.of(a, b), 2);
        Survey before = network.antenna(a)
            .survey();
        // Someone puts a block on the head: one block of cover, same spot.
        RadioHops.End covered = head(a);
        covered = new RadioHops.End(covered.x(), covered.y(), covered.z(), 1, covered.ground());
        network.seen(a, 2, covered, false, owner);
        assertTrue(
            network.antenna(a)
                .survey() == before,
            "the survey stays until a new one is done");
        assertNotNull(
            network.links(DIM)
                .get(AntennaNetwork.LinkKey.of(a.pos(), b.pos())));
        // The cover counts in device links straight away, and a new survey brings the links up to date.
        network.tick(Long.MAX_VALUE, () -> clock);
        assertTrue(
            network.antenna(a)
                .survey() != before);
        assertNotNull(
            network.links(DIM)
                .get(AntennaNetwork.LinkKey.of(a.pos(), b.pos())));
        assertEquals(
            1,
            network.antenna(a)
                .end()
                .depth(),
            1e-9);
    }
}
