package com.limelight.vr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Simulates a 60 FPS stream shown on a 60 Hz screen: frames leave the decoder with jitter and, at each refresh,
 * the screen shows a new frame or repeats the last one.
 */
public class FramePacerTest {
    private static final long FRAME_NS = 1_000_000_000L / 60;
    private static final long BASE_NS = 1_000_000_000_000L;
    private static final long WARM_UP_NS = 3_000_000_000L;

    private static final class Result implements FramePacer.Output {
        int refreshes, repeats, dropped, maxHeld;
        final List<Integer> shown = new ArrayList<>();
        final Set<Integer> released = new HashSet<>();
        boolean counting;
        int shownThisRefresh;

        @Override
        public void show(int index) {
            assertTrue("released twice: " + index, released.add(index));
            shown.add(index);
            shownThisRefresh++;
        }

        @Override
        public void drop(int index, boolean overflow) {
            assertTrue("released twice: " + index, released.add(index));
            if (counting) {
                dropped++;
            }
        }
    }

    /**
     * @param decodeDelayNs per-frame time from the frame's PC time to leaving the decoder
     * @param pacer null for no pacing: each frame is shown at the first refresh after it's decoded, the newest wins
     * @param refreshPhaseNs where the refreshes fall within a frame interval
     */
    private static Result simulate(long[] decodeDelayNs, FramePacer pacer, long refreshPhaseNs) {
        int frames = decodeDelayNs.length;
        long[] decodedNs = new long[frames];
        for (int i = 0; i < frames; i++) {
            decodedNs[i] = BASE_NS + i * FRAME_NS + decodeDelayNs[i];
            if (i > 0) {
                // The decoder outputs frames in order
                decodedNs[i] = Math.max(decodedNs[i], decodedNs[i - 1] + 500_000);
            }
        }
        Result r = new Result();
        if (pacer != null) {
            pacer.setOutput(r);
        }
        int next = 0;
        long end = decodedNs[frames - 1];
        for (long t = BASE_NS + refreshPhaseNs; t < end; t += FRAME_NS) {
            r.counting = t - BASE_NS >= WARM_UP_NS;
            r.shownThisRefresh = 0;
            int decodedNow = 0;
            while (next < frames && decodedNs[next] <= t) {
                if (pacer != null) {
                    pacer.onFrameDecoded(next, next * FRAME_NS / 1000, decodedNs[next]);
                }
                next++;
                decodedNow++;
            }
            if (pacer != null) {
                pacer.onRefresh(t, FRAME_NS);
                r.maxHeld = Math.max(r.maxHeld, pacer.heldFrames());
            }
            else if (decodedNow > 0) {
                r.shownThisRefresh = 1;
                if (r.counting) {
                    r.dropped += decodedNow - 1;
                }
            }
            if (r.counting) {
                r.refreshes++;
                if (r.shownThisRefresh == 0) {
                    r.repeats++;
                }
            }
        }
        return r;
    }

    /** 20 ms from the PC to the decoder's output, ± {@code jitterNs}, plus optional late frames. */
    private static long[] delays(int frames, long jitterNs, int lateEvery, long lateNs) {
        Random random = new Random(7);
        long[] d = new long[frames];
        for (int i = 0; i < frames; i++) {
            d[i] = 20_000_000L + (long) ((random.nextDouble() * 2 - 1) * jitterNs);
            if (lateEvery > 0 && i % lateEvery == lateEvery / 2) {
                d[i] += lateNs;
            }
        }
        return d;
    }

    // Refreshes right where the frames nominally leave the decoder (20 ms after their PC time): the worst phase
    private static final long WORST_PHASE_NS = 20_000_000L % FRAME_NS;

    @Test
    public void withoutPacingSmallJitterRepeatsAndDropsFrames() {
        Result r = simulate(delays(1200, 4_000_000L, 0, 0), null, WORST_PHASE_NS);
        assertTrue("repeats " + r.repeats + " of " + r.refreshes, r.repeats > r.refreshes / 10);
        assertTrue(r.dropped > 0);
    }

    @Test
    public void oneFrameBufferAbsorbsJitterAtEveryPhase() {
        for (long phase = 0; phase < FRAME_NS; phase += FRAME_NS / 8) {
            Result r = simulate(delays(1200, 4_000_000L, 0, 0), new FramePacer(1, 60), phase);
            assertEquals("repeats at phase " + phase, 0, r.repeats);
            assertEquals("dropped at phase " + phase, 0, r.dropped);
        }
    }

    @Test
    public void framesAreShownInOrderAndEveryBufferIsReturned() {
        Result r = simulate(delays(600, 4_000_000L, 50, 40_000_000L), new FramePacer(1, 60), WORST_PHASE_NS);
        for (int i = 1; i < r.shown.size(); i++) {
            assertTrue(r.shown.get(i) > r.shown.get(i - 1));
        }
        // Everything decoded before the last couple of frames was shown or dropped, never kept
        for (int i = 0; i < 590; i++) {
            assertTrue("frame " + i + " was never released", r.released.contains(i));
        }
    }

    @Test
    public void twoFrameBufferAbsorbsALateFrameThatOneFrameDoesNot() {
        long[] d = delays(1200, 1_000_000L, 120, 25_000_000L);
        Result one = simulate(d, new FramePacer(1, 60), WORST_PHASE_NS);
        Result two = simulate(d, new FramePacer(2, 60), WORST_PHASE_NS);
        assertTrue("1-frame buffer repeats " + one.repeats, one.repeats > 0);
        assertEquals(0, two.repeats);
        assertEquals(0, two.dropped);
    }

    @Test
    public void heldFramesStayBounded() {
        // The PC's timestamps run slow against the phone (the PC clock is 1% behind), so frames are decoded faster
        // than they're due; the pacer must drop instead of holding more and more of the decoder's buffers
        FramePacer pacer = new FramePacer(1, 60);
        Result r = new Result();
        pacer.setOutput(r);
        long t = BASE_NS;
        for (int i = 0; i < 2000; i++) {
            pacer.onFrameDecoded(i, (long) (i * FRAME_NS * 0.99 / 1000), t);
            pacer.onRefresh(t, FRAME_NS);
            assertTrue(pacer.heldFrames() <= 1 + FramePacer.MAX_EXTRA_FRAMES);
            t += FRAME_NS;
        }
    }

    @Test
    public void clearForgetsBuffersWithoutReleasingThem() {
        FramePacer pacer = new FramePacer(2, 60);
        Result r = new Result();
        pacer.setOutput(r);
        pacer.onFrameDecoded(1, 0, BASE_NS);
        pacer.onFrameDecoded(2, 16_667, BASE_NS + FRAME_NS);
        pacer.clear();
        assertEquals(0, pacer.heldFrames());
        assertEquals(0, r.released.size());
        assertTrue(!pacer.onRefresh(BASE_NS + 10 * FRAME_NS, FRAME_NS));
    }

    @Test
    public void bufferSetsTheDelay() {
        assertEquals(FRAME_NS, new FramePacer(1, 60).bufferNanos());
        assertEquals(2 * FRAME_NS, new FramePacer(2, 60).bufferNanos());
    }
}
