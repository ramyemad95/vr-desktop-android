package com.limelight.vr;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Locale;

/**
 * Follows every video frame from the PC to the phone's screen, and names a cause for every refresh that repeats
 * the previous frame (a visible stutter). Written for finding where FC 27's stutter comes from: the PC (the game,
 * capture or encode), the network, the phone's decoder, or the phone's pacing and drawing.
 *
 * <p>The stages of one frame, each with a timestamp:</p>
 * <ol>
 *   <li>PC capture time ({@code pc}): the stream's RTP timestamp, in the PC's clock. Its spacing is the PC's cadence:
 *       a game or capture that misses a frame shows up as a gap here, with no frame lost.</li>
 *   <li>PC processing ({@code host}): Sunshine's capture-to-encoded time for the frame, sent with it.</li>
 *   <li>First packet received ({@code rx}) and frame complete ({@code cmp}), in moonlight-common's clock.</li>
 *   <li>Submitted to the decoder ({@code sub}), queued in its input ({@code in}), out of the decoder ({@code out}),
 *       sent to the VR view's surface ({@code rel}), latched by the renderer ({@code latch}): the phone's clock.</li>
 * </ol>
 *
 * <p>The clocks differ, so a frame's delay at a stage is measured against the fastest frame of the last
 * {@link #WINDOW_NS} at that stage (the way {@link FramePacer} schedules frames), which cancels the clock offsets.
 * When a frame is latched more than its share of refreshes after the previous one, the next frame was late, and
 * {@link #attribute} picks the stage that made it late.</p>
 *
 * <p>Output goes to a {@link Sink}: one line per frame, per refresh and per event for the file, and a one-line
 * summary a second (plus the stutters) for logcat. The line format is described in docs/FRAME-LOG.md and in the
 * file's own header.</p>
 *
 * <p>Threads: the decoder's input thread ({@link #submitted}, {@link #queued}), its output thread
 * ({@link #decoded}), the GL thread ({@link #released}, {@link #latched}, {@link #drawn}) and others call it;
 * every method takes this object's lock, and the sink is called with it held.</p>
 */
public final class FrameLog {
    /** Where the lines go. Called with the log's lock held, so it must not block. */
    public interface Sink {
        /** A line for the log file. */
        void write(String line);

        /** A line for logcat too (summaries, stutters, events); the sink also writes it to the file. */
        void report(String line);
    }

    /** Why a refresh repeated the previous frame. */
    public enum Cause {
        PC_GAP("the PC sent the next frame late or not at all: the game, capture or encoder on the PC"),
        PC_ENCODE("Sunshine took longer than usual to capture and encode the frame"),
        NETWORK("the frame's first packet arrived late"),
        TRANSFER("the frame took long to arrive in full (a big frame, or packets lost and recovered)"),
        NET_LOSS("the frame was lost on the network"),
        PHONE_QUEUE("the frame waited to get into the phone's decoder"),
        PHONE_DECODE("the phone's decoder took longer than usual"),
        DECODER_RESET("the phone's decoder was flushed or restarted"),
        GL_STALL("the phone's renderer missed a refresh"),
        PACER("the frame was on time, but the pacer showed it a refresh late");

        public final String meaning;

        Cause(String meaning) {
            this.meaning = meaning;
        }
    }

    /** How far back the fastest frame at each stage is looked for: the pacer's own window. */
    static final long WINDOW_NS = FramePacer.OFFSET_WINDOW_NS;
    /** A jump in PC time larger than this (or backwards) means a new time base, as for the pacer. */
    private static final long PC_TIME_JUMP_NS = FramePacer.PC_TIME_JUMP_NS;
    private static final long SUMMARY_NS = 1_000_000_000L;
    /** No frame for this long is a stall (frozen video) rather than a stutter, and is counted apart. */
    static final long STALL_NS = 250_000_000L;
    /** Stutters reported to logcat per summary window; the file gets all of them. */
    private static final int MAX_REPORTED_STUTTERS = 5;
    private static final int RECENT_FRAMES = 128;

    // Frame fates in the file
    static final char SHOWN = 'S', OVERWRITTEN = 'W', SUPERSEDED = 'D', OVERFLOW = 'O', FLUSHED = 'X', UNTRACKED = 'N';

    /** One frame's way through the stages. Times are 0 until the frame gets there. */
    public static final class Frame {
        final int number;
        final boolean idr;
        final int bytes;
        /** Sunshine's processing time in tenths of a millisecond, 0 if not sent. */
        final int hostTenthMs;
        /** PC clock. */
        final long pcNs;
        /** moonlight-common's clock. */
        final long rxNs, cmpNs;
        /** The phone's clock ({@code System.nanoTime()}). */
        final long subNs;
        long inNs, outNs, relNs, latchNs;
        char fate;
        /** Sunshine re-sent its last picture unchanged (no new capture): it sends no processing time for those. */
        boolean resent;

        Frame(int number, boolean idr, int bytes, int hostTenthMs, long pcNs, long rxNs, long cmpNs, long subNs) {
            this.number = number;
            this.idr = idr;
            this.bytes = bytes;
            this.hostTenthMs = hostTenthMs;
            this.pcNs = pcNs;
            this.rxNs = rxNs;
            this.cmpNs = cmpNs;
            this.subNs = subNs;
        }
    }

    /** Counters over one summary window, and over the whole stream. */
    static final class Stats {
        long startNs, endNs;
        int frames, idr, lost, pcLong, pcShort, pcIntervals;
        long bytes, pcMaxGapNs;
        double pcIntervalSum, pcIntervalSqSum;
        int hostN, hostMax;
        long hostSum;
        int xferN;
        long xferSum, xferMax;
        int late;
        long lateMax;
        int decN;
        long decSum, decMax, waitMax;
        int shown, superseded, overflow, overwritten, flushed;
        int refreshes, repeats, glMissed, drawN, latchGuessed;
        int stalls, pcStalls;
        long stallNs;
        int resent, idle;
        long maxDrawGap, drawSum, drawMax;
        final int[] causes = new int[Cause.values().length];

        void add(Stats o) {
            if (startNs == 0) {
                startNs = o.startNs;
            }
            endNs = o.endNs;
            frames += o.frames;
            idr += o.idr;
            lost += o.lost;
            pcLong += o.pcLong;
            pcShort += o.pcShort;
            pcIntervals += o.pcIntervals;
            bytes += o.bytes;
            pcMaxGapNs = Math.max(pcMaxGapNs, o.pcMaxGapNs);
            pcIntervalSum += o.pcIntervalSum;
            pcIntervalSqSum += o.pcIntervalSqSum;
            hostN += o.hostN;
            hostMax = Math.max(hostMax, o.hostMax);
            hostSum += o.hostSum;
            xferN += o.xferN;
            xferSum += o.xferSum;
            xferMax = Math.max(xferMax, o.xferMax);
            late += o.late;
            lateMax = Math.max(lateMax, o.lateMax);
            decN += o.decN;
            decSum += o.decSum;
            decMax = Math.max(decMax, o.decMax);
            waitMax = Math.max(waitMax, o.waitMax);
            shown += o.shown;
            superseded += o.superseded;
            overflow += o.overflow;
            overwritten += o.overwritten;
            flushed += o.flushed;
            refreshes += o.refreshes;
            repeats += o.repeats;
            glMissed += o.glMissed;
            drawN += o.drawN;
            latchGuessed += o.latchGuessed;
            stalls += o.stalls;
            resent += o.resent;
            idle += o.idle;
            pcStalls += o.pcStalls;
            stallNs += o.stallNs;
            maxDrawGap = Math.max(maxDrawGap, o.maxDrawGap);
            drawSum += o.drawSum;
            drawMax = Math.max(drawMax, o.drawMax);
            for (int i = 0; i < causes.length; i++) {
                causes[i] += o.causes[i];
            }
        }

        /** The most common cause of repeats, or null if there were none. */
        Cause topCause() {
            int best = -1;
            for (int i = 0; i < causes.length; i++) {
                if (causes[i] > 0 && (best < 0 || causes[i] > causes[best])) {
                    best = i;
                }
            }
            return best < 0 ? null : Cause.values()[best];
        }

        String causeList() {
            StringBuilder sb = new StringBuilder();
            Cause[] all = Cause.values();
            for (int i = 0; i < causes.length; i++) {
                if (causes[i] > 0) {
                    sb.append(sb.length() == 0 ? "" : ", ").append(all[i].name()).append(' ').append(causes[i]);
                }
            }
            return sb.toString();
        }

        String format(long frameNs, long refreshNs, boolean tracksDisplay) {
            double seconds = Math.max(endNs - startNs, 1) / 1e9;
            double jitter = 0;
            if (pcIntervals > 1) {
                double mean = pcIntervalSum / pcIntervals;
                jitter = Math.sqrt(Math.max(pcIntervalSqSum / pcIntervals - mean * mean, 0));
            }
            StringBuilder sb = new StringBuilder(320);
            sb.append(String.format(Locale.US,
                    "PC %.0f fps (%.1f Mbps%s%s), gaps>%.0fms %d (max %.1f), bunched %d, jitter %.1fms, encode %s",
                    frames / seconds, bytes * 8 / seconds / 1e6, idr > 0 ? ", " + idr + " IDR" : "",
                    resent > 0 ? ", " + resent + " re-sent unchanged" : "",
                    frameNs * 1.5 / 1e6, pcLong, pcMaxGapNs / 1e6, pcShort, jitter / 1e6,
                    hostN > 0 ? String.format(Locale.US, "%.1f/%.1fms", hostSum / 10.0 / hostN, hostMax / 10.0) : "-"));
            sb.append(String.format(Locale.US, " | NET lost %d, transfer %s, late %d%s",
                    lost, xferN > 0 ? String.format(Locale.US, "%.1f/%.1fms", xferSum / 1e6 / xferN, xferMax / 1e6) : "-",
                    late, late > 0 ? String.format(Locale.US, " (max +%.1fms)", lateMax / 1e6) : ""));
            sb.append(String.format(Locale.US, " | DECODE %s, wait max %.1fms",
                    decN > 0 ? String.format(Locale.US, "%.1f/%.1fms", decSum / 1e6 / decN, decMax / 1e6) : "-",
                    waitMax / 1e6));
            if (tracksDisplay) {
                sb.append(String.format(Locale.US,
                        " | SHOW %d, dropped %d+%d, overwritten %d | REPEATS %d of %d refreshes @%.0fHz, GL missed %d (max %.1fms), draw %s",
                        shown, superseded, overflow, overwritten, repeats, refreshes, 1e9 / refreshNs, glMissed,
                        maxDrawGap / 1e6,
                        drawN > 0 ? String.format(Locale.US, "%.1f/%.1fms", drawSum / 1e6 / drawN, drawMax / 1e6) : "-"));
                if (repeats > 0) {
                    sb.append(" | WHY ").append(causeList());
                }
                if (idle > 0) {
                    sb.append(" | IDLE ").append(idle).append(" refreshes (the PC re-sent an unchanged picture)");
                }
            }
            if (stalls > 0) {
                sb.append(String.format(Locale.US, " | STALLS %d (%.1f s), %d with nothing sent by the PC",
                        stalls, stallNs / 1e9, pcStalls));
            }
            if (flushed > 0) {
                sb.append(" | decoder flushed ").append(flushed).append(" frames");
            }
            return sb.toString();
        }
    }

    private final Sink sink;
    private final long t0Ns;
    private final long frameNs;
    private final long bufferNs;
    private final boolean tracksDisplay;

    private long pcBaseNs = Long.MIN_VALUE, coreBaseNs;
    private int lastNumber;
    private long lastPcNs = Long.MIN_VALUE;
    private long lastSubNs, lastRawPcNs = Long.MIN_VALUE;
    /** Sunshine sends its processing time with each new capture; once seen, a frame without it is a re-send. */
    private boolean hostSeen;
    private boolean lastResent;
    private boolean closed;

    // The fastest frame lately at each stage
    private final WindowMin minHost = new WindowMin(WINDOW_NS);
    private final WindowMin minRxPc = new WindowMin(WINDOW_NS);
    private final WindowMin minCmpPc = new WindowMin(WINDOW_NS);
    private final WindowMin minXfer = new WindowMin(WINDOW_NS);
    private final WindowMin minDuQueue = new WindowMin(WINDOW_NS);
    private final WindowMin minInWait = new WindowMin(WINDOW_NS);
    private final WindowMin minDecode = new WindowMin(WINDOW_NS);
    private final WindowMin minOutPc = new WindowMin(WINDOW_NS);

    private final ArrayDeque<Frame> recent = new ArrayDeque<>();
    /** Frame numbers that never arrived: {first, last}, oldest first. */
    private final ArrayDeque<int[]> lostRanges = new ArrayDeque<>();
    /** Frames sent to the VR view's surface and not latched yet, in the order they were sent. */
    private final ArrayDeque<Frame> awaitingLatch = new ArrayDeque<>();
    private Frame lastLatched;
    private Frame latchedThisDraw;
    private long lastDrawNs;
    private long maxDrawGapSinceLatch;
    private long refreshNs;

    private Stats window = new Stats();
    private final Stats total = new Stats();
    private int reportedStutters;

    /**
     * @param fps           the stream's frame rate
     * @param bufferNs      the smooth-video buffer ({@link FramePacer#bufferNanos()}), 0 without it
     * @param tracksDisplay the VR renderer reports what it latches and draws, so stutters can be counted
     * @param t0Ns          {@code System.nanoTime()} at the start; the file's phone times count from it
     */
    public FrameLog(int fps, long bufferNs, boolean tracksDisplay, long t0Ns, Sink sink) {
        this.sink = sink;
        this.t0Ns = t0Ns;
        this.frameNs = 1_000_000_000L / Math.max(fps, 1);
        this.refreshNs = frameNs;
        this.bufferNs = Math.max(bufferNs, 0);
        this.tracksDisplay = tracksDisplay;
        window.startNs = t0Ns;
    }

    /** Decoder input thread: a frame's data is about to go into the decoder. */
    public synchronized Frame submitted(int number, boolean idr, int bytes, int hostTenthMs,
                                        long rxUs, long cmpUs, long pcUs, long subNs) {
        if (closed) {
            return null;
        }
        long pcNs = pcUs * 1000, rxNs = rxUs * 1000, cmpNs = cmpUs * 1000;
        if (pcBaseNs == Long.MIN_VALUE) {
            pcBaseNs = pcNs;
            coreBaseNs = cmpNs;
        }
        // Frozen video: no frame for a while. Whether the PC's clock moved on as much tells whether the PC sent
        // nothing (capture or encoder stopped) or frames were held up between the PC and the phone.
        boolean stalled = false;
        if (lastSubNs != 0 && subNs - lastSubNs > STALL_NS) {
            stalled = true;
            long gap = subNs - lastSubNs;
            long pcGap = lastRawPcNs != Long.MIN_VALUE ? pcNs - lastRawPcNs : -1;
            boolean pcSentNothing = pcGap >= gap / 2;
            window.stalls++;
            window.stallNs += gap;
            if (pcSentNothing) {
                window.pcStalls++;
            }
            event(subNs, String.format(Locale.US,
                    "STALL: no frame for %.2f s before frame %d%s; PC time moved %.2f s: %s", gap / 1e9, number,
                    idr ? " (IDR)" : "", pcGap / 1e9,
                    pcSentNothing ? "the PC sent nothing (capture or encoder stopped on the PC)"
                            : "frames were held up between the PC and the phone"));
        }
        lastSubNs = subNs;
        lastRawPcNs = pcNs;
        if (lastPcNs != Long.MIN_VALUE && (pcNs < lastPcNs || pcNs - lastPcNs > PC_TIME_JUMP_NS)) {
            if (!stalled) {
                event(subNs, String.format(Locale.US, "PC time jumped by %.1f ms at frame %d", (pcNs - lastPcNs) / 1e6, number));
            }
            minRxPc.clear();
            minCmpPc.clear();
            minOutPc.clear();
            lastPcNs = Long.MIN_VALUE;
        }
        Frame f = new Frame(number, idr, bytes, hostTenthMs, pcNs, rxNs, cmpNs, subNs);
        if (hostTenthMs > 0) {
            hostSeen = true;
        }
        f.resent = hostSeen && hostTenthMs == 0 && !idr;
        Stats w = window;
        if (f.resent) {
            w.resent++;
        }

        if (lastNumber != 0 && number > lastNumber + 1) {
            int first = lastNumber + 1, last = number - 1;
            w.lost += last - first + 1;
            lostRanges.addLast(new int[] {first, last});
            while (lostRanges.size() > 32) {
                lostRanges.pollFirst();
            }
            sink.write("L," + us(subNs) + "," + first + "," + last);
        }
        // The PC's cadence, between new captures that followed each other on the PC
        if (lastPcNs != Long.MIN_VALUE && number == lastNumber + 1 && !f.resent && !lastResent) {
            long gap = pcNs - lastPcNs;
            w.pcIntervals++;
            w.pcIntervalSum += gap;
            w.pcIntervalSqSum += (double) gap * gap;
            w.pcMaxGapNs = Math.max(w.pcMaxGapNs, gap);
            if (gap > frameNs * 3 / 2) {
                w.pcLong++;
            }
            else if (gap < frameNs / 2) {
                w.pcShort++;
            }
        }
        w.frames++;
        if (idr) {
            w.idr++;
        }
        w.bytes += bytes;
        if (hostTenthMs > 0) {
            w.hostN++;
            w.hostSum += hostTenthMs;
            w.hostMax = Math.max(w.hostMax, hostTenthMs);
            minHost.add(subNs, hostTenthMs);
        }
        long xfer = cmpNs - rxNs;
        w.xferN++;
        w.xferSum += xfer;
        w.xferMax = Math.max(w.xferMax, xfer);
        minXfer.add(subNs, xfer);
        if (!f.resent) {
            // A re-sent picture keeps a capture time from before Sunshine decided to re-send it
            minRxPc.add(subNs, rxNs - pcNs);
            minCmpPc.add(subNs, cmpNs - pcNs);
        }
        minDuQueue.add(subNs, subNs - cmpNs);
        long arrivalLate = (cmpNs - pcNs) - minCmpPc.min();
        if (!f.resent && arrivalLate > bufferNs + frameNs / 2) {
            w.late++;
            w.lateMax = Math.max(w.lateMax, arrivalLate);
        }

        recent.addLast(f);
        while (recent.size() > RECENT_FRAMES) {
            recent.pollFirst();
        }
        lastNumber = number;
        lastPcNs = pcNs;
        lastResent = f.resent;
        maybeSummarize(subNs);
        return f;
    }

    /** Decoder input thread: the frame is in the decoder's input queue. */
    public synchronized void queued(Frame f, long nowNs) {
        if (f == null || closed) {
            return;
        }
        f.inNs = nowNs;
        long wait = nowNs - f.subNs;
        window.waitMax = Math.max(window.waitMax, wait);
        minInWait.add(nowNs, wait);
    }

    /** Decoder output thread: the frame came out of the decoder. */
    public synchronized void decoded(Frame f, long nowNs) {
        if (f == null || closed) {
            return;
        }
        f.outNs = nowNs;
        long start = f.inNs != 0 ? f.inNs : f.subNs;
        long decode = nowNs - start;
        Stats w = window;
        w.decN++;
        w.decSum += decode;
        w.decMax = Math.max(w.decMax, decode);
        minDecode.add(nowNs, decode);
        if (!f.resent) {
            minOutPc.add(nowNs, nowNs - f.pcNs);
        }
        if (!tracksDisplay) {
            f.fate = UNTRACKED;
            finish(f);
        }
        maybeSummarize(nowNs);
    }

    /** The frame was sent to the VR view's surface with this render timestamp (what the surface reports back). */
    public synchronized void released(Frame f, long releaseNs) {
        if (f == null || closed || f.fate != 0) {
            // Unknown, or already written (without a VR view, frames end at the decoder)
            return;
        }
        f.relNs = releaseNs;
        awaitingLatch.addLast(f);
        while (awaitingLatch.size() > 16) {
            // The renderer stopped latching (paused): don't hold on to frames forever
            Frame old = awaitingLatch.pollFirst();
            old.fate = OVERWRITTEN;
            window.overwritten++;
            finish(old);
        }
    }

    /** The frame went back to the decoder without being shown: {@code overflow} if the pacer held too many. */
    public synchronized void dropped(Frame f, boolean overflow) {
        if (f == null || closed || f.fate != 0) {
            // Unknown, or already written (without a VR view, frames end at the decoder)
            return;
        }
        f.fate = overflow ? OVERFLOW : SUPERSEDED;
        if (overflow) {
            window.overflow++;
        }
        else {
            window.superseded++;
        }
        finish(f);
    }

    /** The decoder was flushed or restarted with this frame inside it (or held by the pacer). */
    public synchronized void flushed(Frame f) {
        if (f == null || closed || f.fate != 0) {
            // Unknown, or already written (without a VR view, frames end at the decoder)
            return;
        }
        f.fate = FLUSHED;
        window.flushed++;
        finish(f);
    }

    /**
     * GL thread, after latching the surface's frames and before the pacer sends the next one.
     *
     * @param count       how many frames the surface announced since the last draw (0 if none was latched)
     * @param timestampNs the latched frame's {@code SurfaceTexture.getTimestamp()}, or 0
     */
    public synchronized void latched(long nowNs, int count, long timestampNs) {
        if (closed) {
            return;
        }
        Stats w = window;
        if (lastDrawNs != 0) {
            long gap = nowNs - lastDrawNs;
            w.refreshes++;
            w.maxDrawGap = Math.max(w.maxDrawGap, gap);
            maxDrawGapSinceLatch = Math.max(maxDrawGapSinceLatch, gap);
            if (gap > refreshNs * 3 / 2) {
                w.glMissed++;
            }
        }
        lastDrawNs = nowNs;
        latchedThisDraw = null;
        if (count <= 0 || awaitingLatch.isEmpty()) {
            return;
        }
        Frame latched = null;
        if (timestampNs != 0) {
            for (Frame f : awaitingLatch) {
                if (f.relNs == timestampNs) {
                    latched = f;
                    break;
                }
            }
        }
        if (latched == null) {
            // The surface keeps only its newest frame
            latched = awaitingLatch.peekLast();
            w.latchGuessed++;
        }
        // Frames sent before it were replaced on the surface before the renderer got to them
        Frame f;
        while ((f = awaitingLatch.pollFirst()) != latched) {
            f.fate = OVERWRITTEN;
            w.overwritten++;
            finish(f);
        }
        latched.latchNs = nowNs;
        latched.fate = SHOWN;
        w.shown++;
        if (lastLatched != null) {
            checkRepeat(lastLatched, latched);
        }
        lastLatched = latched;
        latchedThisDraw = latched;
        maxDrawGapSinceLatch = 0;
        finish(latched);
    }

    /**
     * GL thread, at the end of a draw.
     *
     * @param refreshNs the display refresh the renderer measured
     * @param held      frames the pacer holds, or -1 without one
     */
    public synchronized void drawn(long startNs, long endNs, long refreshNs, int held) {
        if (closed) {
            return;
        }
        if (refreshNs > 0) {
            this.refreshNs = refreshNs;
        }
        Stats w = window;
        long draw = endNs - startNs;
        w.drawN++;
        w.drawSum += draw;
        w.drawMax = Math.max(w.drawMax, draw);
        Frame l = latchedThisDraw;
        sink.write("R," + us(startNs) + "," + draw / 1000 + "," + (l != null ? Integer.toString(l.number) : "") + "," +
                (held >= 0 ? Integer.toString(held) : ""));
        maybeSummarize(endNs);
    }

    /** Something happened that matters for the timeline: a decoder flush, an IDR request, a connection change. */
    public synchronized void event(long nowNs, String text) {
        if (closed) {
            return;
        }
        sink.report("E," + us(nowNs) + "," + text);
    }

    /** A line from somewhere else for the file only (the PC's stats, the phone's temperatures), stamped now. */
    public synchronized void note(char kind, long nowNs, String text) {
        if (closed) {
            return;
        }
        sink.write(kind + "," + us(nowNs) + "," + text);
    }

    /** Ends the log with the stream's totals. Later calls do nothing. */
    public synchronized void close(long nowNs) {
        if (closed) {
            return;
        }
        flushWindow(nowNs);
        Stats t = total;
        double seconds = Math.max(t.endNs - t.startNs, 1) / 1e9;
        sink.report(String.format(Locale.US, "T,%d,TOTAL %.0f s, %d frames: %s", us(nowNs), seconds, t.frames,
                t.format(frameNs, refreshNs, tracksDisplay)));
        sink.report("V," + us(nowNs) + "," + verdict(t));
        closed = true;
    }

    /** A plain-language reading of the totals. */
    String verdict(Stats t) {
        if (!tracksDisplay) {
            return "VERDICT: no VR view, so stutters weren't counted; see the per-stage numbers";
        }
        if (t.refreshes == 0) {
            return "VERDICT: nothing was drawn";
        }
        double repeatPercent = 100.0 * t.repeats / t.refreshes;
        String stalls = t.stalls == 0 ? "no stalls"
                : String.format(Locale.US, "%d stall(s) of frozen video, %.1f s in all, %d of them with nothing sent by the PC",
                t.stalls, t.stallNs / 1e9, t.pcStalls);
        if (t.idle > 0) {
            stalls += String.format(Locale.US, "; %d refreshes showed an unchanged picture the PC re-sent (not counted)",
                    t.idle);
        }
        Cause top = t.topCause();
        if (top == null) {
            return String.format(Locale.US, "VERDICT: no stutter (no repeated refreshes in %d); %s", t.refreshes, stalls);
        }
        int topCount = t.causes[top.ordinal()];
        return String.format(Locale.US,
                "VERDICT: %.2f%% of refreshes repeated a frame (%d); mostly %s (%d%%): %s. All: %s. And %s",
                repeatPercent, t.repeats, top.name(), Math.round(100.0 * topCount / Math.max(t.repeats, 1)),
                top.meaning, t.causeList(), stalls);
    }

    /** Totals so far, for tests. */
    synchronized Stats totals() {
        Stats s = new Stats();
        s.add(total);
        s.add(window);
        return s;
    }

    private void checkRepeat(Frame prev, Frame cur) {
        long r = Math.max(refreshNs, 1);
        int shownRefreshes = (int) Math.max(1, Math.round((double) (cur.latchNs - prev.latchNs) / r));
        int expected = (int) Math.max(1, Math.round((double) frameNs / r));
        if (shownRefreshes <= expected) {
            return;
        }
        int repeats = shownRefreshes - expected;
        boolean stall = cur.latchNs - prev.latchNs > STALL_NS;
        Frame late = cur;
        if (cur.number > prev.number + 1) {
            Frame next = byNumber(prev.number + 1);
            if (next != null) {
                late = next;
            }
        }
        long[] ex = excess(late);
        Cause cause = attribute(prev, cur, late, ex);
        if (late.resent || prev.resent) {
            // An unchanged picture (a static screen, or the game showing nothing new): the repeat isn't visible
            window.idle += repeats;
            return;
        }
        if (!stall) {
            // Frozen video is counted as a stall when the frame arrives (submitted), not as stutter
            window.repeats += repeats;
            window.causes[cause.ordinal()] += repeats;
        }
        String details = String.format(Locale.US,
                "%s%s,%d,%d,%d,%d,pcGap=%.1f,late=%.1f,host=%.1f,net=%.1f,xfer=%.1f,queue=%.1f,decode=%.1f,drawGap=%.1f,fate=%c",
                cause.name(), stall ? "(STALL)" : "", repeats, prev.number, cur.number, late.number,
                late.number == prev.number + 1 ? (late.pcNs - prev.pcNs) / 1e6 : -1.0,
                ex[0] / 1e6, ex[1] / 1e6, ex[2] / 1e6, ex[3] / 1e6, ex[4] / 1e6, ex[5] / 1e6,
                maxDrawGapSinceLatch / 1e6, late.fate == 0 ? '-' : late.fate);
        if (reportedStutters < MAX_REPORTED_STUTTERS) {
            reportedStutters++;
            sink.report("K," + us(cur.latchNs) + "," + details);
        }
        else {
            sink.write("K," + us(cur.latchNs) + "," + details);
        }
    }

    /**
     * How much later than the fastest frame lately {@code f} was at each stage, in ns: {decoded lateness, PC
     * encode, network (first packet), transfer, queue before the decoder, decode}. Unknown stages are 0.
     */
    private long[] excess(Frame f) {
        long[] ex = new long[6];
        if (f.outNs != 0 && !minOutPc.isEmpty()) {
            ex[0] = (f.outNs - f.pcNs) - minOutPc.min();
        }
        if (f.hostTenthMs > 0 && !minHost.isEmpty()) {
            ex[1] = (f.hostTenthMs - minHost.min()) * 100_000L;
        }
        ex[2] = (f.rxNs - f.pcNs) - minRxPc.min() - Math.max(ex[1], 0);
        ex[3] = (f.cmpNs - f.rxNs) - minXfer.min();
        ex[4] = (f.subNs - f.cmpNs) - minDuQueue.min();
        if (f.inNs != 0) {
            ex[4] += (f.inNs - f.subNs) - minInWait.min();
        }
        if (f.outNs != 0) {
            ex[5] = (f.outNs - (f.inNs != 0 ? f.inNs : f.subNs)) - minDecode.min();
        }
        return ex;
    }

    /**
     * Picks the stage that made {@code late} (the frame due right after {@code prev}) miss its refresh.
     *
     * <p>In order: a frame that never arrived; a decoder reset; a frame that left the decoder later than the buffer
     * absorbs (then the stage that added the most delay); the PC's own spacing (the pacer reproduces it, so a
     * frame captured late is shown late); a renderer that missed a refresh; and otherwise the pacer.</p>
     */
    private Cause attribute(Frame prev, Frame cur, Frame late, long[] ex) {
        if (late == cur && cur.number > prev.number + 1 && isLost(prev.number + 1)) {
            return Cause.NET_LOSS;
        }
        if (late.fate == FLUSHED) {
            return Cause.DECODER_RESET;
        }
        if (ex[0] > bufferNs) {
            Cause[] stages = {Cause.PC_ENCODE, Cause.NETWORK, Cause.TRANSFER, Cause.PHONE_QUEUE, Cause.PHONE_DECODE};
            int best = 0;
            for (int i = 1; i < stages.length; i++) {
                if (ex[i + 1] > ex[best + 1]) {
                    best = i;
                }
            }
            return stages[best];
        }
        if (late.number == prev.number + 1 && late.pcNs - prev.pcNs > frameNs + refreshNs / 4) {
            return Cause.PC_GAP;
        }
        if (maxDrawGapSinceLatch > refreshNs * 3 / 2 || late.fate == OVERWRITTEN) {
            return Cause.GL_STALL;
        }
        return Cause.PACER;
    }

    private Frame byNumber(int number) {
        Iterator<Frame> it = recent.descendingIterator();
        while (it.hasNext()) {
            Frame f = it.next();
            if (f.number == number) {
                return f;
            }
            if (f.number < number) {
                return null;
            }
        }
        return null;
    }

    private boolean isLost(int number) {
        for (int[] r : lostRanges) {
            if (number >= r[0] && number <= r[1]) {
                return true;
            }
        }
        return false;
    }

    /** Writes the frame's line once its fate is known. */
    private void finish(Frame f) {
        StringBuilder sb = new StringBuilder(96);
        sb.append("F,").append(f.number).append(',').append(f.idr ? 'I' : 'P').append(',').append(f.bytes).append(',')
                .append((f.pcNs - pcBaseNs) / 1000).append(',').append(f.hostTenthMs).append(',')
                .append((f.rxNs - coreBaseNs) / 1000).append(',').append((f.cmpNs - coreBaseNs) / 1000).append(',')
                .append(us(f.subNs)).append(',');
        appendTime(sb, f.inNs).append(',');
        appendTime(sb, f.outNs).append(',').append(f.fate).append(',');
        appendTime(sb, f.relNs).append(',');
        appendTime(sb, f.latchNs);
        sink.write(sb.toString());
    }

    private StringBuilder appendTime(StringBuilder sb, long ns) {
        if (ns != 0) {
            sb.append(us(ns));
        }
        return sb;
    }

    private long us(long ns) {
        return (ns - t0Ns) / 1000;
    }

    private void maybeSummarize(long nowNs) {
        if (nowNs - window.startNs >= SUMMARY_NS) {
            flushWindow(nowNs);
        }
    }

    private void flushWindow(long nowNs) {
        Stats w = window;
        w.endNs = nowNs;
        if (w.frames > 0 || w.refreshes > 0) {
            sink.report("S," + us(nowNs) + "," + Math.round((nowNs - t0Ns) / 1e9) + "s " +
                    w.format(frameNs, refreshNs, tracksDisplay));
        }
        total.add(w);
        window = new Stats();
        window.startNs = nowNs;
        reportedStutters = 0;
    }
}
