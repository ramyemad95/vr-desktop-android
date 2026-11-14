package com.limelight.vr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModeFollowerTest {
    private static PcDisplayMode mode(int w, int h) {
        return new PcDisplayMode(w, h, 60, false, 1);
    }

    @Test
    public void restartsOnceANewModeHasHeldThreeSeconds() {
        ModeFollower f = new ModeFollower(3840, 2160);
        f.onMode(mode(1920, 1080), 10_000);
        assertFalse(f.shouldRestart(12_999));
        assertEquals(13_000, f.nextCheckAt());
        assertTrue(f.shouldRestart(13_000));
        assertEquals(1920, f.candidateWidth());
        assertEquals(1080, f.candidateHeight());
    }

    @Test
    public void goingBackToTheStreamModeCancels() {
        // Match -> menu -> match within 3 s: no restart
        ModeFollower f = new ModeFollower(1920, 1080);
        f.onMode(mode(3840, 2160), 0);
        f.onMode(mode(1920, 1080), 1_500);
        assertFalse(f.shouldRestart(10_000));
        assertEquals(-1, f.nextCheckAt());
    }

    @Test
    public void aDifferentNewModeRestartsTheWait() {
        ModeFollower f = new ModeFollower(3840, 2160);
        f.onMode(mode(1920, 1080), 0);
        f.onMode(mode(2560, 1440), 2_000);
        assertFalse(f.shouldRestart(4_000));
        assertTrue(f.shouldRestart(5_000));
        assertEquals(2560, f.candidateWidth());
    }

    @Test
    public void repeatedReportsOfTheSameModeDontRestartTheWait() {
        // A long poll timing out reports the unchanged mode again
        ModeFollower f = new ModeFollower(3840, 2160);
        f.onMode(mode(1920, 1080), 0);
        f.onMode(mode(1920, 1080), 2_500);
        assertTrue(f.shouldRestart(3_000));
    }

    @Test
    public void unstreamableModesAreIgnored() {
        ModeFollower f = new ModeFollower(1920, 1080);
        f.onMode(mode(5120, 1440), 0);
        f.onMode(null, 100);
        assertFalse(f.shouldRestart(60_000));
    }
}
