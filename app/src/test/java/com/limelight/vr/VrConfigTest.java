package com.limelight.vr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class VrConfigTest {
    private static final float EPS = 1e-9f;

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
