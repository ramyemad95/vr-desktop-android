package com.limelight.vr;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HeadPoseTest {
    private static final float EPS = 1e-5f;

    /**
     * Device-to-world rotation for a user facing {@code headingDeg} (0 = north, 90 = east), head pitched up by
     * {@code pitchDeg}, with the phone held upright in landscape as in a viewer.
     */
    private static float[] deviceToWorld(double headingDeg, double pitchDeg) {
        double h = Math.toRadians(headingDeg), p = Math.toRadians(pitchDeg);
        // Forward (device -z), right (device +x) and up (device +y), in world coordinates (x east, y north, z up).
        double[] fwd = {Math.sin(h) * Math.cos(p), Math.cos(h) * Math.cos(p), Math.sin(p)};
        double[] right = {Math.cos(h), -Math.sin(h), 0};
        double[] up = {-Math.sin(h) * Math.sin(p), -Math.cos(h) * Math.sin(p), Math.cos(p)};
        // Columns are the device axes in world coordinates: x = right, y = up, z = -forward.
        return new float[] {
                (float) right[0], (float) up[0], (float) -fwd[0],
                (float) right[1], (float) up[1], (float) -fwd[1],
                (float) right[2], (float) up[2], (float) -fwd[2],
        };
    }

    private static float[] forwardInScene(float[] worldToScene, float[] deviceToWorld) {
        float[] headToScene = HeadPose.multiply(worldToScene, deviceToWorld);
        float[] out = new float[3];
        HeadPose.transform(headToScene, 0, 0, -1, out);
        return out;
    }

    @Test
    public void recenteredPoseIsIdentity() {
        for (double heading : new double[] {0, 37, 90, 180, 271}) {
            float[] r = deviceToWorld(heading, 0);
            float[] headToScene = HeadPose.multiply(HeadPose.recenterFrame(r), r);
            assertArrayEquals("heading " + heading, HeadPose.identity(), headToScene, EPS);
        }
    }

    @Test
    public void turningLeftAfterRecenterLooksLeftInScene() {
        float[] world = HeadPose.recenterFrame(deviceToWorld(90, 0));
        // Facing east, turn 90 degrees left: now facing north.
        assertArrayEquals(new float[] {-1, 0, 0}, forwardInScene(world, deviceToWorld(0, 0)), EPS);
    }

    @Test
    public void recenterIgnoresPitch() {
        // Re-centering while looking down 30 degrees keeps the screen level: the head still looks down at it.
        float[] r = deviceToWorld(200, -30);
        float[] fwd = forwardInScene(HeadPose.recenterFrame(r), r);
        assertEquals(0f, fwd[0], EPS);
        assertEquals((float) Math.sin(Math.toRadians(-30)), fwd[1], EPS);
        assertEquals((float) -Math.cos(Math.toRadians(-30)), fwd[2], EPS);
    }

    @Test
    public void recenterWhileLookingStraightDownUsesScreenTop() {
        float[] r = deviceToWorld(90, -90);
        float[] fwd = forwardInScene(HeadPose.recenterFrame(r), r);
        assertArrayEquals(new float[] {0, -1, 0}, fwd, EPS);
        // Raising the head back to level from there faces the screen.
        assertArrayEquals(new float[] {0, 0, -1}, forwardInScene(HeadPose.recenterFrame(r), deviceToWorld(90, 0)), EPS);
    }

    @Test
    public void recenterWithPitchIsIdentity() {
        for (double heading : new double[] {0, 37, 90, 180, 271}) {
            for (double pitch : new double[] {-60, -30, 0, 25, 70}) {
                float[] r = deviceToWorld(heading, pitch);
                float[] headToScene = HeadPose.multiply(HeadPose.recenterFrame(r, true), r);
                assertArrayEquals("heading " + heading + " pitch " + pitch, HeadPose.identity(), headToScene, EPS);
            }
        }
    }

    @Test
    public void recenterWithPitchKeepsTheScreenUpright() {
        // The scene's x axis (the screen's horizontal edge) stays level in the world.
        float[] worldToScene = HeadPose.recenterFrame(deviceToWorld(123, -40), true);
        assertEquals(0f, worldToScene[2], EPS);
    }

    @Test
    public void raisingTheHeadAfterRecenteringLowLooksAboveTheScreen() {
        float[] world = HeadPose.recenterFrame(deviceToWorld(45, -30), true);
        float[] fwd = forwardInScene(world, deviceToWorld(45, 0));
        assertEquals(0f, fwd[0], EPS);
        assertEquals((float) Math.sin(Math.toRadians(30)), fwd[1], EPS);
        assertEquals((float) -Math.cos(Math.toRadians(30)), fwd[2], EPS);
    }

    @Test
    public void recenterWithPitchWhileLookingStraightDownFacesTheScreen() {
        float[] r = deviceToWorld(90, -90);
        assertArrayEquals(new float[] {0, 0, -1}, forwardInScene(HeadPose.recenterFrame(r, true), r), EPS);
    }

    @Test
    public void columnMajorIsTranspose() {
        float[] m = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        float[] out = new float[9];
        HeadPose.toColumnMajor(m, out);
        assertArrayEquals(new float[] {1, 4, 7, 2, 5, 8, 3, 6, 9}, out, 0f);
    }
}
