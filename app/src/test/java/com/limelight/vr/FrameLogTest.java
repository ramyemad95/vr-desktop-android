package com.limelight.vr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Runs a 60 FPS stream on a 60 Hz screen through the real {@link FramePacer} and {@link FrameLog}, with one thing
 * going wrong at a time (the PC skipping a frame, a slow decode, a lost frame...), and checks that the log blames
 * the stutter on that stage.
 */
public class FrameLogTest {
    private static final long MS = 1_000_000L;
    private static final long FRAME_NS = 1_000_000_000L / 60;
    private static final long BASE_NS = 1_000_000_000_000L;
    private static final int FRAMES = 600;
    private static final int K = 300;

    /** Per-frame timing of one simulated stream, all in one clock (the clock offsets don't matter to the log). */
    private static final class Stream {
        final long[] pcNs = new long[FRAMES];
        final long[] hostNs = new long[FRAMES];
        final long[] netNs = new long[FRAMES];
        final long[] xferNs = new long[FRAMES];
        final long[] decodeNs = new long[FRAMES];
        final boolean[] lost = new boolean[FRAMES];
        /** Refreshes that the GL thread misses. */
        final List<Long> skippedRefreshes = new ArrayList<>();

        Stream() {
            Random random = new Random(7);
            for (int i = 0; i < FRAMES; i++) {
                pcNs[i] = BASE_NS + i * FRAME_NS;
                hostNs[i] = 4 * MS;
                netNs[i] = 3 * MS + (long) (random.nextDouble() * MS);
                xferNs[i] = MS;
                decodeNs[i] = 7 * MS + (long) (random.nextDouble() * MS);
            }
        }
    }

    private static final class Run implements FrameLog.Sink {
        final List<String> lines = new ArrayList<>();
        FrameLog log;

        @Override
        public void write(String line) {
            lines.add(line);
        }

        @Override
        public void report(String line) {
            lines.add(line);
        }

        List<String> starting(String prefix) {
            List<String> out = new ArrayList<>();
            for (String l : lines) {
                if (l.startsWith(prefix)) {
                    out.add(l);
                }
            }
            return out;
        }
    }

    private static Run simulate(Stream s) {
        Run run = new Run();
        FramePacer pacer = new FramePacer(1, 60);
        FrameLog log = new FrameLog(60, pacer.bufferNanos(), true, BASE_NS, run);
        run.log = log;
        Map<Integer, FrameLog.Frame> held = new HashMap<>();
        List<Long> releasedThisRefresh = new ArrayList<>();
        final long[] now = new long[1];
        pacer.setOutput(new FramePacer.Output() {
            @Override
            public void show(int index) {
                log.released(held.remove(index), now[0]);
                releasedThisRefresh.add(now[0]);
            }

            @Override
            public void drop(int index, boolean overflow) {
                log.dropped(held.remove(index), overflow);
            }
        });

        // Frames leave the decoder in order
        long[] outNs = new long[FRAMES];
        long prevOut = 0;
        for (int i = 0; i < FRAMES; i++) {
            long cmp = s.pcNs[i] + s.hostNs[i] + s.netNs[i] + s.xferNs[i];
            outNs[i] = Math.max(cmp + 300_000 + s.decodeNs[i], prevOut + 500_000);
            prevOut = outNs[i];
        }

        int next = 0;
        long pendingTimestamp = 0;
        int pendingCount = 0;
        long end = outNs[FRAMES - 1] + 10 * FRAME_NS;
        for (long t = BASE_NS + 5 * MS; t < end; t += FRAME_NS) {
            while (next < FRAMES && outNs[next] <= t) {
                int i = next++;
                if (s.lost[i]) {
                    continue;
                }
                long rx = s.pcNs[i] + s.hostNs[i] + s.netNs[i];
                long cmp = rx + s.xferNs[i];
                long sub = cmp + 200_000;
                FrameLog.Frame f = log.submitted(i + 1, i == 0, 50_000, (int) (s.hostNs[i] / 100_000),
                        rx / 1000, cmp / 1000, s.pcNs[i] / 1000, sub);
                log.queued(f, sub + 100_000);
                log.decoded(f, outNs[i]);
                held.put(i, f);
                pacer.onFrameDecoded(i, s.pcNs[i] / 1000, outNs[i]);
            }
            if (s.skippedRefreshes.contains(t)) {
                continue;
            }
            now[0] = t;
            log.latched(t, pendingCount, pendingTimestamp);
            pendingCount = 0;
            releasedThisRefresh.clear();
            pacer.onRefresh(t, FRAME_NS);
            if (!releasedThisRefresh.isEmpty()) {
                pendingCount = releasedThisRefresh.size();
                pendingTimestamp = releasedThisRefresh.get(releasedThisRefresh.size() - 1);
            }
            log.drawn(t, t + 2 * MS, FRAME_NS, pacer.heldFrames());
        }
        log.close(end);
        return run;
    }

    private static FrameLog.Stats totals(Run run) {
        return run.log.totals();
    }

    private static void assertOnlyCause(Run run, FrameLog.Cause cause) {
        FrameLog.Stats t = totals(run);
        assertTrue("expected a stutter: " + run.starting("V,"), t.repeats >= 1);
        assertEquals("all repeats from " + cause + ": " + run.starting("K,"), t.repeats, t.causes[cause.ordinal()]);
        assertTrue(run.starting("K,").get(0).contains("," + cause.name() + ","));
    }

    @Test
    public void steadyStreamHasNoStutter() {
        Run run = simulate(new Stream());
        FrameLog.Stats t = totals(run);
        assertEquals(0, t.repeats);
        assertEquals(0, t.lost);
        assertEquals(0, t.stalls);
        assertTrue(t.shown >= FRAMES - 5);
        assertEquals(0, t.latchGuessed);
        assertTrue(run.starting("V,").get(0), run.starting("V,").get(0).contains("no stutter"));
        // One line per frame, one per refresh, and a summary a second
        assertTrue(run.starting("F,").size() >= FRAMES - 1);
        assertTrue(run.starting("R,").size() >= FRAMES);
        assertTrue(run.starting("S,").size() >= 9);
    }

    @Test
    public void framesThePcDidNotSendAreBlamedOnThePc() {
        Stream s = new Stream();
        for (int i = K; i < FRAMES; i++) {
            // The PC captured nothing for one frame interval: the frame numbers go on, the PC's clock jumps
            s.pcNs[i] += FRAME_NS;
        }
        Run run = simulate(s);
        assertOnlyCause(run, FrameLog.Cause.PC_GAP);
        assertEquals(1, totals(run).pcLong);
        assertEquals(0, totals(run).lost);
    }

    @Test
    public void aSlowDecodeIsBlamedOnTheDecoder() {
        Stream s = new Stream();
        s.decodeNs[K] += 30 * MS;
        assertOnlyCause(simulate(s), FrameLog.Cause.PHONE_DECODE);
    }

    @Test
    public void aSlowTransferIsBlamedOnTheTransfer() {
        Stream s = new Stream();
        s.xferNs[K] += 30 * MS;
        assertOnlyCause(simulate(s), FrameLog.Cause.TRANSFER);
    }

    @Test
    public void aSlowEncodeIsBlamedOnThePcEncoder() {
        Stream s = new Stream();
        s.hostNs[K] += 30 * MS;
        assertOnlyCause(simulate(s), FrameLog.Cause.PC_ENCODE);
    }

    @Test
    public void aLatePacketIsBlamedOnTheNetwork() {
        Stream s = new Stream();
        s.netNs[K] += 30 * MS;
        assertOnlyCause(simulate(s), FrameLog.Cause.NETWORK);
    }

    @Test
    public void aLostFrameIsBlamedOnTheNetwork() {
        Stream s = new Stream();
        s.lost[K] = true;
        Run run = simulate(s);
        assertOnlyCause(run, FrameLog.Cause.NET_LOSS);
        assertEquals(1, totals(run).lost);
        assertEquals(1, run.starting("L,").size());
    }

    @Test
    public void aMissedRefreshIsBlamedOnTheRenderer() {
        Stream s = new Stream();
        s.skippedRefreshes.add(BASE_NS + 5 * MS + K * FRAME_NS);
        Run run = simulate(s);
        assertOnlyCause(run, FrameLog.Cause.GL_STALL);
        assertTrue(totals(run).glMissed >= 1);
    }

    @Test
    public void frozenVideoIsAStallNotStutter() {
        Stream s = new Stream();
        for (int i = K; i < FRAMES; i++) {
            // Sunshine restarted capture: 3 s with nothing sent
            s.pcNs[i] += 3_000_000_000L;
        }
        Run run = simulate(s);
        FrameLog.Stats t = totals(run);
        assertEquals(1, t.stalls);
        assertEquals(1, t.pcStalls);
        assertEquals(0, t.repeats);
        boolean reported = false;
        for (String e : run.starting("E,")) {
            reported |= e.contains("STALL") && e.contains("the PC sent nothing");
        }
        assertTrue(run.starting("E,").toString(), reported);
        assertTrue(run.starting("V,").get(0).contains("1 stall(s)"));
    }

    @Test
    public void framesHeldUpOnTheWayAreAStallOnTheNetwork() {
        Stream s = new Stream();
        for (int i = K; i < K + 30; i++) {
            // 30 frames sent on time but delivered together half a second later
            s.netNs[i] += 500 * MS - (i - K) * FRAME_NS;
        }
        Run run = simulate(s);
        FrameLog.Stats t = totals(run);
        assertEquals(1, t.stalls);
        assertEquals(0, t.pcStalls);
    }

    @Test
    public void anUnchangedPictureTheSunshineResendsIsIdleNotStutter() {
        Stream s = new Stream();
        int resends = 40;
        for (int i = K; i < FRAMES; i++) {
            // A static screen: Sunshine captures nothing new and re-sends the last picture 4 frames apart, with no
            // processing time and a capture time from before it decided to re-send
            s.pcNs[i] += (Math.min(i, K + resends) - K) * 3 * FRAME_NS;
            if (i < K + resends) {
                s.hostNs[i] = 0;
                s.netNs[i] += 40 * MS;
            }
        }
        Run run = simulate(s);
        FrameLog.Stats t = totals(run);
        assertTrue("idle " + t.idle, t.idle >= 3 * (resends - 2));
        assertEquals(resends, t.resent);
        assertEquals("no stutter blamed: " + run.starting("K,"), 0, t.causes[FrameLog.Cause.NETWORK.ordinal()]);
        assertTrue("at most the step back into motion: " + t.repeats, t.repeats <= 3);
        assertEquals("re-sends aren't PC gaps", 0, t.pcLong);
    }

    @Test
    public void framesReplacedBeforeTheLatchAreOverwritten() {
        Run run = new Run();
        FrameLog log = new FrameLog(60, 0, true, BASE_NS, run);
        FrameLog.Frame a = log.submitted(1, true, 1000, 40, 0, 1000, 0, BASE_NS);
        FrameLog.Frame b = log.submitted(2, false, 1000, 40, 16_667, 17_667, 16_667, BASE_NS + FRAME_NS);
        log.decoded(a, BASE_NS + 10 * MS);
        log.decoded(b, BASE_NS + FRAME_NS + 10 * MS);
        log.released(a, BASE_NS + 20 * MS);
        log.released(b, BASE_NS + 21 * MS);
        log.latched(BASE_NS + 30 * MS, 2, BASE_NS + 21 * MS);
        FrameLog.Stats t = log.totals();
        assertEquals(1, t.overwritten);
        assertEquals(1, t.shown);
        assertEquals(0, t.latchGuessed);
        List<String> f = run.starting("F,");
        assertEquals(2, f.size());
        assertTrue(f.get(0), f.get(0).startsWith("F,1,I,") && f.get(0).contains(",W,"));
        assertTrue(f.get(1), f.get(1).startsWith("F,2,P,") && f.get(1).contains(",S,"));
    }

    @Test
    public void unknownTimestampsFallBackToTheNewestFrame() {
        Run run = new Run();
        FrameLog log = new FrameLog(60, 0, true, BASE_NS, run);
        FrameLog.Frame a = log.submitted(1, true, 1000, 40, 0, 1000, 0, BASE_NS);
        log.decoded(a, BASE_NS + 10 * MS);
        log.released(a, 0);
        log.latched(BASE_NS + 30 * MS, 1, 12345);
        FrameLog.Stats t = log.totals();
        assertEquals(1, t.shown);
        assertEquals(1, t.latchGuessed);
    }

    @Test
    public void withoutAVrViewFramesEndAtTheDecoder() {
        Run run = new Run();
        FrameLog log = new FrameLog(60, 0, false, BASE_NS, run);
        FrameLog.Frame a = log.submitted(1, true, 1000, 40, 0, 1000, 0, BASE_NS);
        log.decoded(a, BASE_NS + 10 * MS);
        // Released later by Moonlight's own path: ignored, the frame is already written
        log.released(a, BASE_NS + 11 * MS);
        log.close(BASE_NS + 2_000_000_000L);
        assertEquals(1, run.starting("F,").size());
        assertTrue(run.starting("F,").get(0).contains(",N,"));
        assertNotNull(run.starting("V,").get(0));
    }

    @Test
    public void windowMinFollowsTheWindow() {
        WindowMin m = new WindowMin(10);
        m.add(0, 5);
        m.add(1, 7);
        assertEquals(5, m.min());
        m.add(2, 3);
        assertEquals(3, m.min());
        m.add(13, 9);
        // 3 (at time 2) is out of the window [3, 13]
        assertEquals(9, m.min());
    }
}
