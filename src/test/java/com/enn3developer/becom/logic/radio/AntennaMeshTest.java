package com.enn3developer.becom.logic.radio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.WidestPath;

/** A signal goes up to an antenna, along antenna links, and down; its SNR is the weakest hop. */
class AntennaMeshTest {

    private static final double NONE = Double.NEGATIVE_INFINITY;

    @Test
    void singleAntennaPathIsTheWeakerAccessLink() {
        AntennaMesh mesh = new AntennaMesh(1);
        double[] reach = mesh.reach(new double[] { 20 });
        assertEquals(20, reach[0], 1e-9);
        assertEquals(9, AntennaMesh.through(reach, new double[] { 9 }), 1e-9);
    }

    @Test
    void goesAlongAntennaLinks() {
        AntennaMesh mesh = new AntennaMesh(3);
        mesh.link(0, 1, 15);
        mesh.link(1, 2, 11);
        double[] reach = mesh.reach(new double[] { 25, NONE, NONE });
        assertEquals(15, reach[1], 1e-9);
        assertEquals(11, reach[2], 1e-9);
        assertEquals(11, AntennaMesh.through(reach, new double[] { NONE, NONE, 30 }), 1e-9);
    }

    @Test
    void picksTheWidestOfSeveralPaths() {
        AntennaMesh mesh = new AntennaMesh(4);
        mesh.link(0, 1, 8);
        mesh.link(1, 3, 30);
        mesh.link(0, 2, 20);
        mesh.link(2, 3, 14);
        double[] reach = mesh.reach(new double[] { 40, NONE, NONE, NONE });
        assertEquals(14, reach[3], 1e-9);
    }

    @Test
    void comesDownFromTheBestAntenna() {
        AntennaMesh mesh = new AntennaMesh(2);
        mesh.link(0, 1, 18);
        double[] reach = mesh.reach(new double[] { 10, 22 });
        // Up via antenna 1 directly at 22 beats up via 0 at 10.
        assertEquals(18, reach[0], 1e-9);
        assertEquals(22, reach[1], 1e-9);
        assertEquals(16, AntennaMesh.through(reach, new double[] { 16, 7 }), 1e-9);
    }

    @Test
    void nothingWithoutAccess() {
        AntennaMesh mesh = new AntennaMesh(2);
        mesh.link(0, 1, 18);
        double[] reach = mesh.reach(new double[] { NONE, NONE });
        assertEquals(NONE, AntennaMesh.through(reach, new double[] { 30, 30 }));
    }

    /** The same widest paths, from a graph built afresh with the device as one more vertex. */
    private static double[] reachThroughAGraph(AntennaMesh mesh, double[] access) {
        int n = mesh.antennas();
        WidestPath paths = new WidestPath(n + 1);
        for (int a = 0; a < n; a++) {
            if (access[a] > NONE) paths.addEdge(n, a, access[a], null);
            for (int b = 0; b < n; b++)
                if (a != b && mesh.linkSnr(a, b) > NONE) paths.addEdge(a, b, mesh.linkSnr(a, b), null);
        }
        WidestPath.Result result = paths.solve(n);
        double[] reach = new double[n];
        for (int a = 0; a < n; a++) reach[a] = result.width(a);
        return reach;
    }

    @Test
    void reachIsTheWidestPathFromTheDevice() {
        Random random = new Random(7);
        for (int round = 0; round < 300; round++) {
            int n = 1 + random.nextInt(40);
            AntennaMesh mesh = new AntennaMesh(n);
            double density = random.nextDouble() * 0.3;
            for (int a = 0; a < n; a++) {
                for (int b = a + 1; b < n; b++) if (random.nextDouble() < density) mesh.link(a, b, random.nextInt(40));
            }
            double[] access = new double[n];
            for (int a = 0; a < n; a++) {
                double roll = random.nextDouble();
                access[a] = roll < 0.65 ? NONE : roll < 0.7 ? Double.NaN : random.nextInt(40);
            }
            assertArrayEquals(reachThroughAGraph(mesh, access), mesh.reach(access), 0, "round " + round);
        }
    }

    @Test
    void aLinkThatIsntANumberCountsAsNone() {
        AntennaMesh mesh = new AntennaMesh(2);
        mesh.link(0, 1, 20);
        assertArrayEquals(new double[] { 20, 30 }, mesh.reach(new double[] { Double.NaN, 30 }), 0);
        assertArrayEquals(new double[] { 30, 20 }, mesh.reach(new double[] { 30, Double.NaN }), 0);
        AntennaMesh broken = new AntennaMesh(2);
        broken.link(0, 1, Double.NaN);
        assertArrayEquals(new double[] { 30, NONE }, broken.reach(new double[] { 30, NONE }), 0);
    }
}
