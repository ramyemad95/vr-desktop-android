package com.limelight.vr;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StereoModeTest {
    private static final float EPS = 1e-6f;

    private static float[] region(StereoMode mode, int eye) {
        float[] out = new float[4];
        mode.eyeRegion(eye, out);
        return out;
    }

    @Test
    public void preferenceValues() {
        assertEquals(StereoMode.OFF, StereoMode.fromPreference("off"));
        assertEquals(StereoMode.SIDE_BY_SIDE, StereoMode.fromPreference("sbs"));
        assertEquals(StereoMode.SIDE_BY_SIDE_SWAPPED, StereoMode.fromPreference("sbs_rl"));
        assertEquals(StereoMode.TOP_BOTTOM, StereoMode.fromPreference("tb"));
        assertEquals(StereoMode.OFF, StereoMode.fromPreference(null));
        assertEquals(StereoMode.OFF, StereoMode.fromPreference("SBS"));
        assertEquals(StereoMode.OFF, StereoMode.fromPreference(""));
        assertFalse(StereoMode.OFF.isStereo());
        assertTrue(StereoMode.TOP_BOTTOM.isStereo());
        assertEquals("", StereoMode.OFF.label());
    }

    @Test
    public void offShowsTheWholeFrameToBothEyes() {
        float[] whole = {0f, 0f, 1f, 1f};
        assertArrayEquals(whole, region(StereoMode.OFF, LensDistortion.LEFT_EYE), EPS);
        assertArrayEquals(whole, region(StereoMode.OFF, LensDistortion.RIGHT_EYE), EPS);
    }

    @Test
    public void sideBySideGivesEachEyeItsHalf() {
        assertArrayEquals(new float[] {0f, 0f, 0.5f, 1f}, region(StereoMode.SIDE_BY_SIDE, LensDistortion.LEFT_EYE), EPS);
        assertArrayEquals(new float[] {0.5f, 0f, 0.5f, 1f}, region(StereoMode.SIDE_BY_SIDE, LensDistortion.RIGHT_EYE), EPS);
        // Swapped is the mirror image
        assertArrayEquals(region(StereoMode.SIDE_BY_SIDE, LensDistortion.RIGHT_EYE),
                region(StereoMode.SIDE_BY_SIDE_SWAPPED, LensDistortion.LEFT_EYE), EPS);
        assertArrayEquals(region(StereoMode.SIDE_BY_SIDE, LensDistortion.LEFT_EYE),
                region(StereoMode.SIDE_BY_SIDE_SWAPPED, LensDistortion.RIGHT_EYE), EPS);
    }

    @Test
    public void topAndBottomPutsTheLeftEyeOnTop() {
        // The frame texture has v = 1 at the picture's top
        assertArrayEquals(new float[] {0f, 0.5f, 1f, 0.5f}, region(StereoMode.TOP_BOTTOM, LensDistortion.LEFT_EYE), EPS);
        assertArrayEquals(new float[] {0f, 0f, 1f, 0.5f}, region(StereoMode.TOP_BOTTOM, LensDistortion.RIGHT_EYE), EPS);
    }

    @Test
    public void eachEyeCoversTheScreenWithinItsPicture() {
        for (StereoMode mode : StereoMode.values()) {
            for (int eye = LensDistortion.LEFT_EYE; eye <= LensDistortion.RIGHT_EYE; eye++) {
                float[] r = region(mode, eye);
                // The screen's corners (uv 0 and 1) land inside the frame
                assertTrue(r[0] >= 0f && r[0] + r[2] <= 1f + EPS);
                assertTrue(r[1] >= 0f && r[1] + r[3] <= 1f + EPS);
            }
        }
    }

    @Test
    public void insetIsOnlyAlongTheSplit() {
        float[] inset = new float[2];
        StereoMode.SIDE_BY_SIDE.regionInset(3840, 2160, inset);
        assertEquals(4f / 1920f, inset[0], EPS);
        assertEquals(0f, inset[1], EPS);
        StereoMode.TOP_BOTTOM.regionInset(3840, 2160, inset);
        assertEquals(0f, inset[0], EPS);
        assertEquals(4f / 1080f, inset[1], EPS);
        StereoMode.OFF.regionInset(3840, 2160, inset);
        assertArrayEquals(new float[] {0f, 0f}, inset, EPS);
    }
}
