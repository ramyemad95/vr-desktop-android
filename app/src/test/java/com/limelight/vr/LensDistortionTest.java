package com.limelight.vr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LensDistortionTest {
    private static final float EPS = 1e-4f;

    @Test
    public void undistortInvertsDistort() {
        for (ViewerProfile v : new ViewerProfile[] {ViewerProfile.CARDBOARD_V1, ViewerProfile.CARDBOARD_V2}) {
            for (float r = 0f; r <= 1.8f; r += 0.05f) {
                float screen = LensDistortion.distort(r, v.k1, v.k2);
                assertEquals("r=" + r, r, LensDistortion.undistort(screen, v.k1, v.k2), EPS);
            }
        }
    }

    @Test
    public void lensMagnifiesMoreTowardItsEdge() {
        ViewerProfile v = ViewerProfile.CARDBOARD_V2;
        assertTrue(LensDistortion.distort(0.5f, v.k1, v.k2) > 0.5f);
        assertTrue(LensDistortion.distort(1.0f, v.k1, v.k2) / 1.0f > LensDistortion.distort(0.5f, v.k1, v.k2) / 0.5f);
    }

    @Test
    public void noLensIsIdentity() {
        assertEquals(0.7f, LensDistortion.undistort(0.7f, 0f, 0f), EPS);
    }

    // A 6.1" 2400x1080 phone: about 0.062 mm per pixel.
    private static final int W = 2400, H = 1080;
    private static final float MPP = 0.0000625f;

    @Test
    public void meshHasExpectedSize() {
        LensDistortion.EyeMesh m = LensDistortion.buildEyeMesh(LensDistortion.LEFT_EYE, W, H, MPP, ViewerProfile.CARDBOARD_V2, 40);
        assertEquals(40 * 40 * LensDistortion.EyeMesh.FLOATS_PER_VERTEX, m.vertices.length);
        assertEquals(39 * 39 * 6, m.indices.length);
        // Corners of the viewport in normalized device coordinates.
        assertEquals(-1f, m.vertices[0], EPS);
        assertEquals(-1f, m.vertices[1], EPS);
        int last = m.vertices.length - LensDistortion.EyeMesh.FLOATS_PER_VERTEX;
        assertEquals(1f, m.vertices[last], EPS);
        assertEquals(1f, m.vertices[last + 1], EPS);
    }

    @Test
    public void eyesAreMirrorImages() {
        int n = 21;
        LensDistortion.EyeMesh left = LensDistortion.buildEyeMesh(LensDistortion.LEFT_EYE, W, H, MPP, ViewerProfile.CARDBOARD_V2, n);
        LensDistortion.EyeMesh right = LensDistortion.buildEyeMesh(LensDistortion.RIGHT_EYE, W, H, MPP, ViewerProfile.CARDBOARD_V2, n);
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                int l = (row * n + col) * 4;
                int r = (row * n + (n - 1 - col)) * 4;
                assertEquals(-left.vertices[l + 2], right.vertices[r + 2], EPS);
                assertEquals(left.vertices[l + 3], right.vertices[r + 3], EPS);
            }
        }
    }

    @Test
    public void lensCenterLooksStraightAhead() {
        // With an odd grid on a screen where the lens centre falls exactly on a grid column, that vertex has tan 0.
        ViewerProfile v = ViewerProfile.CARDBOARD_V2;
        int n = 3;
        // Pick a width so the left lens centre (W/2 - IPD/2) sits at the middle of the left half (W/4).
        float mpp = 0.0001f;
        int width = Math.round(2 * v.interLensDistance / mpp);
        LensDistortion.EyeMesh m = LensDistortion.buildEyeMesh(LensDistortion.LEFT_EYE, width, 1000, mpp, v, n);
        int center = (1 * n + 1) * 4;
        assertEquals(0f, m.vertices[center + 2], EPS);
        assertEquals(0f, m.vertices[center + 3], EPS);
    }

    @Test
    public void meshMatchesTheLensPolynomial() {
        // Every vertex's view angle is the lens polynomial applied to its angle on the screen, in the same direction.
        ViewerProfile v = ViewerProfile.CARDBOARD_V2;
        LensDistortion.EyeMesh lens = LensDistortion.buildEyeMesh(LensDistortion.LEFT_EYE, W, H, MPP, v, 11);
        LensDistortion.EyeMesh flat = LensDistortion.buildEyeMesh(LensDistortion.LEFT_EYE, W, H, MPP, ViewerProfile.NO_LENS, 11);
        for (int i = 0; i < lens.vertices.length; i += 4) {
            float rs = (float) Math.hypot(flat.vertices[i + 2], flat.vertices[i + 3]);
            float re = (float) Math.hypot(lens.vertices[i + 2], lens.vertices[i + 3]);
            assertEquals(LensDistortion.distort(rs, v.k1, v.k2), re, 1e-3f * Math.max(1f, re));
            assertTrue(re >= rs - EPS);
            assertEquals(flat.vertices[i + 2] * lens.vertices[i + 3], flat.vertices[i + 3] * lens.vertices[i + 2], 1e-3f);
        }
    }
}
