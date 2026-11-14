package com.limelight.vr;

/**
 * Decides when a change in the PC's display mode should restart the stream at the new resolution: only once the new
 * mode has held for {@link #HOLD_MILLIS}, so a game flickering through modes while it loads, or a quick
 * match → menu → match, doesn't reconnect the stream more than once. Not thread-safe; use it from one thread.
 */
public final class ModeFollower {
    public static final long HOLD_MILLIS = 3000;

    private final int streamWidth, streamHeight;
    private int candidateWidth, candidateHeight;
    private long candidateSince = -1;

    /** @param streamWidth the resolution the stream is running at now */
    public ModeFollower(int streamWidth, int streamHeight) {
        this.streamWidth = streamWidth;
        this.streamHeight = streamHeight;
    }

    /**
     * A mode report from the PC. Only streamable modes that differ from the stream's become a candidate; a report of
     * the stream's own resolution (the game went back) cancels any candidate.
     */
    public void onMode(PcDisplayMode mode, long nowMillis) {
        if (mode == null || !mode.isStreamable() || mode.sameResolution(streamWidth, streamHeight)) {
            candidateSince = -1;
            return;
        }
        if (candidateSince < 0 || !mode.sameResolution(candidateWidth, candidateHeight)) {
            candidateWidth = mode.width;
            candidateHeight = mode.height;
            candidateSince = nowMillis;
        }
    }

    /** Whether the stream should restart now at {@link #candidateWidth()} x {@link #candidateHeight()}. */
    public boolean shouldRestart(long nowMillis) {
        return candidateSince >= 0 && nowMillis - candidateSince >= HOLD_MILLIS;
    }

    /** When to check again, or -1 if no change is pending. */
    public long nextCheckAt() {
        return candidateSince >= 0 ? candidateSince + HOLD_MILLIS : -1;
    }

    public int candidateWidth() {
        return candidateWidth;
    }

    public int candidateHeight() {
        return candidateHeight;
    }
}
