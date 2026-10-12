package com.enn3developer.becom.logic.radio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** DESIGN.md 7 and 8.1: a signal goes up to an antenna, along antenna links, and down; its SNR is the weakest hop. */
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
}
