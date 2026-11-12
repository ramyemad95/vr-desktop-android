package com.limelight.vr;

import static org.junit.Assert.assertEquals;
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
        assertEquals(1f, new VrConfig(ViewerProfile.NO_LENS, 90f, 3840, 2160, 5e-5f, 2f, true, true, null, false, null).sharpness, EPS);
    }

    @Test
    public void stereoModeDefaultsToOff() {
        assertEquals(StereoMode.OFF, new VrConfig(ViewerProfile.NO_LENS, 90f, 3840, 2160, 5e-5f, 0.3f, true, false, null, false, null).stereoMode);
        assertEquals(StereoMode.TOP_BOTTOM, new VrConfig(ViewerProfile.NO_LENS, 90f, 3840, 2160, 5e-5f, 0.3f, true, false, null, false,
                StereoMode.TOP_BOTTOM).stereoMode);
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
}
