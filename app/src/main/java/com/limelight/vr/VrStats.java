package com.limelight.vr;

import java.util.Locale;

/**
 * About two seconds of stream statistics for the VR stats panel, with a plain verdict on the network and on
 * processing (the PC's capture and encode, and the phone's decode).
 *
 * <p>Built by the decoder thread from Moonlight's own counters, read on the GL thread. Immutable.</p>
 */
public final class VrStats {
    public enum Level { GOOD, WARN, BAD }

    /** Frames lost by the network at or above this share of frames are visible as stutter. */
    static final float BAD_LOSS_PERCENT = 2f;
    /** Round-trip time variance at or above this means Wi-Fi interference or a busy network. */
    static final int WARN_RTT_VARIANCE_MS = 10;
    /** Round-trip time at or above this is high for a home network. */
    static final int WARN_RTT_MS = 30;

    public final int width, height;
    /** The frame rate the stream was set up for. */
    public final int targetFps;
    public final float incomingFps;
    /** Share of frames the network lost, in percent. */
    public final float lostPercent;
    /** Network round-trip time and its variance; negative when not known yet. */
    public final int rttMs, rttVarianceMs;
    /** Average time the PC took to capture and encode a frame; negative when the PC doesn't report it. */
    public final float hostLatencyMs;
    /** Average time the phone took to decode a frame; NaN when no frame was decoded. */
    public final float decodeMs;

    public VrStats(int width, int height, int targetFps, float incomingFps, float lostPercent,
                   int rttMs, int rttVarianceMs, float hostLatencyMs, float decodeMs) {
        this.width = width;
        this.height = height;
        this.targetFps = targetFps;
        this.incomingFps = incomingFps;
        this.lostPercent = lostPercent;
        this.rttMs = rttMs;
        this.rttVarianceMs = rttVarianceMs;
        this.hostLatencyMs = hostLatencyMs;
        this.decodeMs = decodeMs;
    }

    /**
     * @param connectionPoor Moonlight's own verdict (CONN_STATUS_POOR), which also counts late and lost packets
     *                       that never show up as lost frames
     */
    public Level network(boolean connectionPoor) {
        if (connectionPoor || lostPercent >= BAD_LOSS_PERCENT) {
            return Level.BAD;
        }
        if (lostPercent > 0f || rttVarianceMs >= WARN_RTT_VARIANCE_MS || rttMs >= WARN_RTT_MS) {
            return Level.WARN;
        }
        return Level.GOOD;
    }

    /**
     * Whether the PC or the phone needs most of a frame interval for one frame, which shows up as latency and then
     * as stutter.
     */
    public Level processing() {
        float budgetMs = 1000f / Math.max(targetFps, 1);
        float worst = Math.max(hostLatencyMs, Float.isNaN(decodeMs) ? 0f : decodeMs);
        if (worst >= 0.8f * budgetMs) {
            return Level.BAD;
        }
        if (worst >= 0.5f * budgetMs) {
            return Level.WARN;
        }
        return Level.GOOD;
    }

    public String networkLine(boolean connectionPoor) {
        String rtt = rttMs >= 0 ? String.format(Locale.US, "%d ms ±%d", rttMs, Math.max(rttVarianceMs, 0)) : "- ms";
        switch (network(connectionPoor)) {
            case BAD:
                return lostPercent > 0f
                        ? String.format(Locale.US, "NET POOR  %.1f%% lost  %s", lostPercent, rtt)
                        : "NET POOR  " + rtt;
            case WARN:
                return lostPercent > 0f
                        ? String.format(Locale.US, "NET UNSTABLE  %.2f%% lost  %s", lostPercent, rtt)
                        : "NET UNSTABLE  " + rtt;
            default:
                return "NET OK  " + rtt;
        }
    }

    public String videoLine() {
        return String.format(Locale.US, "VIDEO %dx%d  in %.0f/%d fps", width, height, incomingFps, targetFps);
    }

    /** @param vrFps how often the VR view was redrawn, or a negative value if not measured yet */
    public String processingLine(float vrFps) {
        String pc = hostLatencyMs >= 0f ? String.format(Locale.US, "%.1f ms", hostLatencyMs) : "-";
        String decode = Float.isNaN(decodeMs) ? "-" : String.format(Locale.US, "%.1f ms", decodeMs);
        String vr = vrFps >= 0f ? String.format(Locale.US, "%.0f fps", vrFps) : "-";
        return "PC " + pc + "  DECODE " + decode + "  VR " + vr;
    }
}
