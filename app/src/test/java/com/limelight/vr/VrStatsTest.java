package com.limelight.vr;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VrStatsTest {
    private static VrStats stats(float lostPercent, int rttMs, int rttVarianceMs, float hostMs, float decodeMs) {
        return new VrStats(2560, 1440, 60, 59.6f, lostPercent, rttMs, rttVarianceMs, hostMs, decodeMs);
    }

    @Test
    public void cleanHomeWifiIsGood() {
        VrStats s = stats(0f, 5, 1, 4.2f, 6.1f);
        assertEquals(VrStats.Level.GOOD, s.network(false));
        assertEquals("NET OK  5 ms ±1", s.networkLine(false));
    }

    @Test
    public void jitterOrHighLatencyIsUnstable() {
        assertEquals(VrStats.Level.WARN, stats(0f, 6, 12, 4f, 6f).network(false));
        assertEquals(VrStats.Level.WARN, stats(0f, 35, 2, 4f, 6f).network(false));
        assertEquals(VrStats.Level.WARN, stats(0.3f, 5, 1, 4f, 6f).network(false));
        assertEquals("NET UNSTABLE  0.30% lost  5 ms ±1", stats(0.3f, 5, 1, 4f, 6f).networkLine(false));
    }

    @Test
    public void heavyLossOrMoonlightsPoorVerdictIsPoor() {
        assertEquals(VrStats.Level.BAD, stats(2.5f, 5, 1, 4f, 6f).network(false));
        assertEquals(VrStats.Level.BAD, stats(0f, 5, 1, 4f, 6f).network(true));
        assertEquals("NET POOR  2.5% lost  5 ms ±1", stats(2.5f, 5, 1, 4f, 6f).networkLine(false));
        assertEquals("NET POOR  5 ms ±1", stats(0f, 5, 1, 4f, 6f).networkLine(true));
    }

    @Test
    public void processingIsJudgedAgainstTheFrameInterval() {
        // 60 fps leaves 16.7 ms per frame
        assertEquals(VrStats.Level.GOOD, stats(0f, 5, 1, 4f, 6f).processing());
        assertEquals(VrStats.Level.WARN, stats(0f, 5, 1, 9f, 6f).processing());
        assertEquals(VrStats.Level.BAD, stats(0f, 5, 1, 4f, 14f).processing());
    }

    @Test
    public void unknownValuesShowAsDashes() {
        VrStats s = stats(0f, -1, -1, -1f, Float.NaN);
        assertEquals("NET OK  - ms", s.networkLine(false));
        assertEquals("PC -  DECODE -  VR -", s.processingLine(-1f));
        assertEquals(VrStats.Level.GOOD, s.processing());
    }

    @Test
    public void linesShowTheMeasuredValues() {
        VrStats s = stats(0f, 5, 1, 4.25f, 6.04f);
        assertEquals("VIDEO 2560x1440  in 60/60 fps", s.videoLine(false, StereoMode.OFF));
        assertEquals("VIDEO 2560x1440 HDR  in 60/60 fps", s.videoLine(true, StereoMode.OFF));
        assertEquals("VIDEO 2560x1440 HDR 3D SBS  in 60/60 fps", s.videoLine(true, StereoMode.SIDE_BY_SIDE));
        assertEquals("VIDEO 2560x1440 3D TB  in 60/60 fps", s.videoLine(false, StereoMode.TOP_BOTTOM));
        assertEquals("VIDEO 2560x1440  in 60/60 fps", s.videoLine(false, null));
        assertEquals("PC 4.3 ms  DECODE 6.0 ms  VR 120 fps", s.processingLine(119.8f));
    }
}
