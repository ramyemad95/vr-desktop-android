package com.limelight.vr;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class VrConfigTest {
    private static final float EPS = 1e-9f;

    @Test
    public void lensDistanceSettingReplacesOnlyTheLensDistance() {
        assertSame(ViewerProfile.CARDOO_VR_GO, ViewerProfile.fromPreferences("cardoo_vr_go", 0));
        ViewerProfile narrow = ViewerProfile.fromPreferences("none", 60);
        assertEquals(0.060f, narrow.interLensDistance, EPS);
        assertEquals(ViewerProfile.NO_LENS.screenToLensDistance, narrow.screenToLensDistance, EPS);
        assertEquals(ViewerProfile.NO_LENS.k1, narrow.k1, EPS);
        assertEquals(ViewerProfile.NO_LENS.k2, narrow.k2, EPS);
        assertEquals(ViewerProfile.NO_LENS.halfFovDegrees, narrow.halfFovDegrees, EPS);
    }

    @Test
    public void screenDistanceSettingReplacesOnlyTheScreenDistance() {
        ViewerProfile far = ViewerProfile.fromPreferences("cardoo_vr_go", 60, 45f);
        assertEquals(0.045f, far.screenToLensDistance, EPS);
        assertEquals(0.060f, far.interLensDistance, EPS);
        assertEquals(ViewerProfile.CARDOO_VR_GO.k1, far.k1, EPS);
        assertEquals(ViewerProfile.CARDOO_VR_GO.halfFovDegrees, far.halfFovDegrees, EPS);
        assertSame(ViewerProfile.CARDOO_VR_GO, ViewerProfile.fromPreferences("cardoo_vr_go", 0, 0f));
        // Half-millimeter steps, e.g. the cardoO's focus dial with the lenses closest to the phone
        assertEquals(0.0375f, ViewerProfile.fromPreferences("none", 60, 37.5f).screenToLensDistance, 1e-7f);
    }

    @Test
    public void sharpnessSettingIsClampedToTheMaskRange() {
        assertEquals(0f, VrConfig.sharpnessFromPercent(0), EPS);
        assertEquals(0.4f, VrConfig.sharpnessFromPercent(40), EPS);
        assertEquals(1f, VrConfig.sharpnessFromPercent(150), EPS);
        assertEquals(0f, VrConfig.sharpnessFromPercent(-10), EPS);
        assertEquals(1f, config(2f, null, 0f, false).sharpness, EPS);
    }

    @Test
    public void stereoModeDefaultsToOff() {
        assertEquals(StereoMode.OFF, config(0.3f, null, 0f, false).stereoMode);
        assertEquals(StereoMode.TOP_BOTTOM, config(0.3f, StereoMode.TOP_BOTTOM, 0f, false).stereoMode);
    }

    @Test
    public void usesMeasuredDensityWhenPlausible() {
        assertEquals(0.0254f / 500f, VrConfig.metersPerPixel(499f, 501f, 450), EPS);
    }

    @Test
    public void fallsBackToBucketDensityWhenMeasuredIsBogus() {
        // Some devices report 160 dpi for a 450 dpi screen.
        assertEquals(0.0254f / 450f, VrConfig.metersPerPixel(160f, 160f, 450), EPS);
        assertEquals(0.0254f / 450f, VrConfig.metersPerPixel(0f, 0f, 450), EPS);
    }

    @Test
    public void galaxyS22UltraUsesPanelWidthAtAnyResolution() {
        // WQHD+ and FHD+ settings: same physical width, different pixel counts, whatever the reported dpi.
        assertEquals(0.1564f / 3088, VrConfig.metersPerPixel("SM-S908B", 3088, 500f, 500f, 600), EPS);
        assertEquals(0.1564f / 2316, VrConfig.metersPerPixel("SM-S908U1", 2316, 500f, 500f, 450), EPS);
        // Other phones keep using the densities.
        assertEquals(0.0254f / 420f, VrConfig.metersPerPixel("Pixel 8", 2400, 420f, 420f, 420), EPS);
    }

    @Test
    public void viewerPreferenceValues() {
        assertSame(ViewerProfile.CARDBOARD_V2, ViewerProfile.fromPreference("cardboard_v2"));
        assertSame(ViewerProfile.CARDBOARD_V1, ViewerProfile.fromPreference("cardboard_v1"));
        assertSame(ViewerProfile.NO_LENS, ViewerProfile.fromPreference("none"));
        assertSame(ViewerProfile.CARDOO_VR_GO, ViewerProfile.fromPreference("cardoo_vr_go"));
        assertSame(ViewerProfile.CARDBOARD_V2, ViewerProfile.fromPreference(null));
    }

    @Test
    public void largeStreamsArePrefilteredAtHalfSize() {
        // Galaxy S22 Ultra at WQHD+: each eye is 1544 pixels wide
        assertArrayEquals(new int[] {1920, 1080}, VrConfig.frameTargetSize(3840, 2160, 1544, false));
        // Already close to what the eye can show: kept
        assertArrayEquals(new int[] {2560, 1440}, VrConfig.frameTargetSize(2560, 1440, 1544, false));
        assertArrayEquals(new int[] {1920, 1080}, VrConfig.frameTargetSize(1920, 1080, 1544, false));
        // 3D modes give each eye half the frame, so it keeps every pixel
        assertArrayEquals(new int[] {3840, 2160}, VrConfig.frameTargetSize(3840, 2160, 1544, true));
        // Only one halving, and never for odd sizes or an unknown screen
        assertArrayEquals(new int[] {3840, 2160}, VrConfig.frameTargetSize(7680, 4320, 1544, false));
        assertArrayEquals(new int[] {3841, 2160}, VrConfig.frameTargetSize(3841, 2160, 1544, false));
        assertArrayEquals(new int[] {3840, 2160}, VrConfig.frameTargetSize(3840, 2160, 0, false));
        // A phone whose eye is wider than half the stream keeps the stream's size
        assertArrayEquals(new int[] {3840, 2160}, VrConfig.frameTargetSize(3840, 2160, 1920, false));
    }

    @Test
    public void softnessIsClamped() {
        assertEquals(0.8f, VrConfig.softnessFromPercent(80), 0f);
        assertEquals(1f, VrConfig.softnessFromPercent(250), 0f);
        assertEquals(0f, VrConfig.softnessFromPercent(-5), 0f);
        assertEquals(0.8f, config(0.3f, StereoMode.OFF, 0.8f, true).softness, 0f);
        assertEquals(1f, config(0.3f, StereoMode.OFF, 3f, true).softness, 0f);
        assertTrue(config(0.3f, StereoMode.OFF, 0.8f, true).halveLargeFrames);
        VrConfig soft = config(0.3f, StereoMode.OFF, Float.NaN, false);
        assertEquals(0f, soft.softness, 0f);
        assertFalse(soft.halveLargeFrames);
    }

    /** A config with the values these tests don't look at filled in. */
    private static VrConfig config(float sharpness, StereoMode stereoMode, float softness, boolean halveLargeFrames) {
        return new VrConfig(ViewerProfile.NO_LENS, 90f, 3840, 2160, 5e-5f, sharpness, true, false, null, false,
                stereoMode, false, softness, halveLargeFrames);
    }
}
