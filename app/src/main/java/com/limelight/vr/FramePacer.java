package com.limelight.vr;

import java.util.ArrayDeque;

/**
 * Smooth video for the VR view: shows each decoded frame at the pace the PC produced it, a fixed delay after the
 * fastest frame of the last few seconds, instead of each frame the moment it's decoded.
 *
 * <p>Without it, a 60 FPS stream on a 60 Hz screen has no slack: a frame that arrives or decodes a few
 * milliseconds late misses its refresh, the previous frame is shown twice, and the late one then collides with the
 * next one and is dropped. On a Galaxy S22 Ultra in a game, frames arrived ±3 ms around their 16.7 ms spacing and
 * left the decoder ±4 ms, and 9% of the refreshes repeated a frame (5.6 hitches a second) with no frame lost on the
 * network.</p>
 *
 * <p>How it works: every frame carries its time on the PC (from the stream's RTP timestamp). For each frame, the
 * offset between the time it left the decoder and its PC time is measured; the smallest offset over the last
 * {@link #OFFSET_WINDOW_NS} is the fastest path from the PC to the decoder's output. A frame is due at its PC time
 * plus that offset plus the buffer ({@code bufferFrames} frame intervals), so frames keep their PC spacing on the
 * screen as long as none is later than the buffer. The smallest offset follows the slow drift between the PC's and
 * the phone's clocks by itself, which costs an occasional single repeated or skipped frame.</p>
 *
 * <p>The frames wait here as the decoder's output buffers, not in the VR view's {@code SurfaceTexture}: the
 * decoder's surface keeps only its newest frame, so a frame held back there would be replaced by the next one. At
 * each display refresh the VR renderer calls {@link #onRefresh}, which sends at most one frame to the surface (the
 * newest one due; older due frames are dropped), and the renderer shows it from the next refresh on. Head tracking
 * isn't delayed: only the video frame shown in the virtual screen is.</p>
 *
 * <p>Threads: {@link #onFrameDecoded} is called by the decoder's output thread, {@link #onRefresh} by the GL thread,
 * {@link #clear} by the decoder when it restarts. The {@link Output} is called with this object's lock held.</p>
 */
public final class FramePacer {
    /** Where the pacer sends the frames it holds (the decoder's output buffers). */
    public interface Output {
        /** Renders this decoded frame to the VR view's surface now. */
        void show(int index);

        /**
         * Gives this decoded frame back to the decoder without showing it.
         *
         * @param overflow true if more frames waited than the buffer holds; false if a newer frame was due at the
         *                 same refresh
         */
        void drop(int index, boolean overflow);
    }

    /** How far back the fastest frame is looked for. Long enough to see a fast frame between hiccups. */
    static final long OFFSET_WINDOW_NS = 2_000_000_000L;
    /** The largest buffer the Smooth video setting offers, in frames. */
    public static final int MAX_BUFFER_FRAMES = 2;
    /** Frames held beyond the buffer before the oldest are dropped, which bounds the delay and feeds the decoder. */
    static final int MAX_EXTRA_FRAMES = 1;
    /** A jump in PC time larger than this (or backwards) means a new time base: the offsets start over. */
    static final long PC_TIME_JUMP_NS = 1_000_000_000L;

    private final long bufferNs;
    private final int maxHeld;

    /** Decoded frames waiting to be shown, oldest first: {decoder output buffer index, PC time in ns}. */
    private final ArrayDeque<long[]> held = new ArrayDeque<>();
    /** The smallest (decode time - PC time) of the last {@link #OFFSET_WINDOW_NS}: the fastest frame lately. */
    private final WindowMin offsets = new WindowMin(OFFSET_WINDOW_NS);
    private long lastPcNs = Long.MIN_VALUE;
    private Output output;

    /**
     * @param bufferFrames frames of delay that absorb late frames (1 to {@link #MAX_BUFFER_FRAMES})
     * @param fps the stream's frame rate, which sets the length of a frame
     */
    public FramePacer(int bufferFrames, int fps) {
        long frameNs = 1_000_000_000L / Math.max(fps, 1);
        bufferNs = Math.max(bufferFrames, 0) * frameNs;
        maxHeld = Math.max(bufferFrames, 0) + MAX_EXTRA_FRAMES;
    }

    /** The delay the buffer adds, in nanoseconds. */
    public long bufferNanos() {
        return bufferNs;
    }

    /** Sets where frames go; set it before the first frame is decoded. */
    public synchronized void setOutput(Output output) {
        this.output = output;
    }

    /**
     * Decoder output thread: a frame was decoded. The pacer now owns the output buffer until it shows or drops it.
     *
     * @param index the decoder's output buffer index
     * @param pcTimeUs the frame's time on the PC
     * @param nowNs {@code System.nanoTime()} now
     */
    public synchronized void onFrameDecoded(int index, long pcTimeUs, long nowNs) {
        long pcNs = pcTimeUs * 1000;
        if (lastPcNs != Long.MIN_VALUE && (pcNs <= lastPcNs || pcNs - lastPcNs > PC_TIME_JUMP_NS)) {
            offsets.clear();
        }
        lastPcNs = pcNs;

        offsets.add(nowNs, nowNs - pcNs);

        held.addLast(new long[] {index, pcNs});
        while (held.size() > maxHeld) {
            drop(held.pollFirst(), true);
        }
    }

    /**
     * GL thread, once per display refresh: sends the newest frame that's due to the surface (dropping older due
     * ones) so the renderer shows it from the next refresh on.
     *
     * @param nowNs {@code System.nanoTime()} at the start of the refresh's drawing
     * @param refreshNs the length of a display refresh
     * @return whether a frame was sent
     */
    public synchronized boolean onRefresh(long nowNs, long refreshNs) {
        if (held.isEmpty() || offsets.isEmpty()) {
            return false;
        }
        long offset = offsets.min();
        // A frame goes out at the refresh nearest the time it's due
        long deadline = nowNs + refreshNs / 2;
        long[] newestDue = null;
        while (!held.isEmpty() && held.peekFirst()[1] + offset + bufferNs <= deadline) {
            if (newestDue != null) {
                drop(newestDue, false);
            }
            newestDue = held.pollFirst();
        }
        if (newestDue == null) {
            return false;
        }
        if (output != null) {
            output.show((int) newestDue[0]);
        }
        return true;
    }

    /** The decoder was flushed or restarted: its output buffers are gone, so forget them without releasing them. */
    public synchronized void clear() {
        held.clear();
        offsets.clear();
        lastPcNs = Long.MIN_VALUE;
    }

    private void drop(long[] frame, boolean overflow) {
        if (output != null) {
            output.drop((int) frame[0], overflow);
        }
    }

    /** Frames held now (for the frame log and tests). */
    synchronized int heldFrames() {
        return held.size();
    }
}
