package com.limelight.vr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RefreshRateTest {
    private static final float EPS = 1e-4f;
    // What a Galaxy S22 Ultra offers at WQHD+ with Motion smoothness set to Adaptive
    private static final float[] S22_ADAPTIVE = {120.00001f, 96.00001f, 60.000004f, 48.000004f, 30.000002f, 24.000002f, 10f};
    // What the same phone offers an app with Motion smoothness set to Standard
    private static final float[] S22_STANDARD = {60.000004f};

    @Test
    public void highestPicksTheFastestRate() {
        assertEquals(120.00001f, RefreshRate.choose(S22_ADAPTIVE, RefreshRate.HIGHEST), EPS);
        assertEquals(60.000004f, RefreshRate.choose(S22_STANDARD, RefreshRate.HIGHEST), EPS);
    }

    @Test
    public void specificRatePicksTheClosestOffered() {
        assertEquals(120.00001f, RefreshRate.choose(S22_ADAPTIVE, 120), EPS);
        assertEquals(96.00001f, RefreshRate.choose(S22_ADAPTIVE, 96), EPS);
        assertEquals(60.000004f, RefreshRate.choose(S22_ADAPTIVE, 60), EPS);
    }

    @Test
    public void tieGoesToTheHigherRate() {
        // 90 Hz is 30 away from both 60 and 120
        assertEquals(120f, RefreshRate.choose(new float[]{60f, 120f}, 90), EPS);
    }

    @Test
    public void flagsARateTheSystemDoesNotOffer() {
        float chosen = RefreshRate.choose(S22_STANDARD, 120);
        assertEquals(60.000004f, chosen, EPS);
        assertTrue(RefreshRate.isBelowWanted(chosen, 120));
        assertFalse(RefreshRate.isBelowWanted(60.000004f, 60));
        assertFalse(RefreshRate.isBelowWanted(60.000004f, RefreshRate.HIGHEST));
    }
}
