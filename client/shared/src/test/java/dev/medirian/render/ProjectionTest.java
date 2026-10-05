package dev.medirian.render;

import dev.medirian.platform.CameraView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectionTest {

    private static CameraView camera(final float yaw, final float pitch, final float fov) {
        return new CameraView() {
            public double x() { return 0; }
            public double y() { return 64; }
            public double z() { return 0; }
            public float yaw() { return yaw; }
            public float pitch() { return pitch; }
            public float fov() { return fov; }
        };
    }

    private final float[] out = new float[3];

    @Test
    void pointStraightAheadIsInTheCentre() {
        // yaw 0 looks south (+Z)
        assertTrue(Projection.project(camera(0, 0, 70), 0, 64, 10, 400, 200, out));
        assertEquals(200, out[0], 0.01);
        assertEquals(100, out[1], 0.01);
        assertEquals(10, out[2], 1e-4);
    }

    @Test
    void axesMatchMinecraft() {
        CameraView south = camera(0, 0, 70);
        // facing south, west (-X) is on the right and up is up
        Projection.project(south, -2, 64, 10, 400, 200, out);
        assertTrue(out[0] > 200);
        Projection.project(south, 0, 66, 10, 400, 200, out);
        assertTrue(out[1] < 100);
        // yaw 90 looks west, yaw -90 east
        assertTrue(Projection.project(camera(90, 0, 70), -10, 64, 0, 400, 200, out));
        assertEquals(200, out[0], 0.01);
        assertTrue(Projection.project(camera(-90, 0, 70), 10, 64, 0, 400, 200, out));
        assertEquals(200, out[0], 0.01);
    }

    @Test
    void pitchLooksDown() {
        // looking 45° down, a point 10 ahead and 10 below is in the centre
        assertTrue(Projection.project(camera(0, 45, 70), 0, 54, 10, 400, 200, out));
        assertEquals(200, out[0], 0.01);
        assertEquals(100, out[1], 0.01);
    }

    @Test
    void fovMapsToTheScreenEdge() {
        // a point at half the vertical FOV above the view axis lands on the top edge
        double h = Math.tan(Math.toRadians(35)) * 10;
        Projection.project(camera(0, 0, 70), 0, 64 + h, 10, 400, 200, out);
        assertEquals(0, out[1], 0.01);
    }

    @Test
    void offScreenPointsGetAnEdgePosition() {
        CameraView south = camera(0, 0, 70);
        // far to the right (west), slightly up: right edge
        Projection.edge(south, -100, 65, 10, 400, 200, 20, out);
        assertEquals(380, out[0], 0.01);
        assertTrue(out[1] < 100);
        // behind and to the left (east): left edge
        Projection.edge(south, 10, 64, -5, 400, 200, 20, out);
        assertEquals(20, out[0], 0.01);
        // straight behind: bottom edge
        Projection.edge(south, 0, 64, -10, 400, 200, 20, out);
        assertEquals(180, out[1], 0.01);
    }

    @Test
    void pointsBehindTheCameraAreRejected() {
        assertFalse(Projection.project(camera(0, 0, 70), 0, 64, -5, 400, 200, out));
    }
}
