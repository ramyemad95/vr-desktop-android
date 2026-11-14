package com.limelight.vr;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The PC display's current mode, as served by the VR Desktop Host app (docs/PROTOCOL.md, v1:
 * {@code GET} {@link #PATH} on TCP {@link #PORT}). With "Resolution: match the PC", the stream uses this resolution, so
 * a game in exclusive fullscreen at 1080p is streamed at 1080p rather than upscaled to the desktop's 4K.
 */
public final class PcDisplayMode {
    public static final int PORT = 48100;
    public static final String PATH = "/vr/v1/mode";
    /** The host app's one-second stats for the frame log (docs/PROTOCOL.md, v2). */
    public static final String STATS_PATH = "/vr/v2/stats";

    /** Largest stream the phone's HEVC decoder is set up for; anything bigger falls back to the user's setting. */
    static final int MAX_WIDTH = 4096, MAX_HEIGHT = 2304;
    static final int MIN_WIDTH = 640, MIN_HEIGHT = 360;
    /** Kept below what saturated the router-to-phone Wi-Fi in testing (4K at 150 Mbps plus FEC). */
    static final int MAX_BITRATE_KBPS = 150_000;

    public final int width, height, refreshHz;
    public final boolean hdr;
    /** Goes up by 1 on every change; the long poll asks for anything newer than the last one seen. */
    public final long seq;

    public PcDisplayMode(int width, int height, int refreshHz, boolean hdr, long seq) {
        this.width = width;
        this.height = height;
        this.refreshHz = refreshHz;
        this.hdr = hdr;
        this.seq = seq;
    }

    /** Whether the phone can stream this resolution: within the decoder's range, even sizes, landscape or square. */
    public boolean isStreamable() {
        return width >= MIN_WIDTH && height >= MIN_HEIGHT && width <= MAX_WIDTH && height <= MAX_HEIGHT &&
                width % 2 == 0 && height % 2 == 0 && width >= height;
    }

    public boolean sameResolution(int w, int h) {
        return width == w && height == h;
    }

    /**
     * The bitrate for a stream of this size: about 50 Mbps for 1080p, 80 for 1440p and 100 for 4K at 60 fps, enough
     * that more isn't visible through the lenses, and scaled with the frame rate.
     */
    public static int bitrateKbps(int width, int height, int fps) {
        long pixels = (long) width * height;
        int base;
        if (pixels <= 1920L * 1080) {
            base = 50_000;
        }
        else if (pixels <= 2560L * 1440) {
            base = 80_000;
        }
        else {
            base = 100_000;
        }
        long kbps = (long) base * Math.max(fps, 30) / 60;
        return (int) Math.min(kbps, MAX_BITRATE_KBPS);
    }

    private static final Pattern INT_FIELD = Pattern.compile("\"(width|height|refreshHz|seq)\"\\s*:\\s*(-?\\d+)");
    private static final Pattern HDR_FIELD = Pattern.compile("\"hdr\"\\s*:\\s*(true|false)");

    /**
     * Parses the endpoint's JSON body. Field order and whitespace don't matter and unknown fields are ignored.
     *
     * @return null if width or height is missing
     */
    public static PcDisplayMode parseJson(String body) {
        if (body == null) {
            return null;
        }
        long width = -1, height = -1, refresh = 0, seq = 0;
        Matcher m = INT_FIELD.matcher(body);
        while (m.find()) {
            long v;
            try {
                v = Long.parseLong(m.group(2));
            } catch (NumberFormatException e) {
                return null;
            }
            switch (m.group(1)) {
                case "width": width = v; break;
                case "height": height = v; break;
                case "refreshHz": refresh = v; break;
                default: seq = v; break;
            }
        }
        if (width <= 0 || height <= 0 || width > Integer.MAX_VALUE || height > Integer.MAX_VALUE) {
            return null;
        }
        Matcher h = HDR_FIELD.matcher(body);
        boolean hdr = h.find() && "true".equals(h.group(1));
        return new PcDisplayMode((int) width, (int) height, (int) Math.max(0, Math.min(refresh, 1000)), hdr, seq);
    }

    /**
     * Parses a whole HTTP/1.1 response as read from the socket.
     *
     * @return null unless the status is 200 and the body has a valid mode
     */
    public static PcDisplayMode parseHttpResponse(String raw) {
        PcModeClient.Response response = PcModeClient.Response.parse(raw);
        return response != null && response.status == 200 ? parseJson(response.body) : null;
    }

    /** The mode endpoint's path, optionally as a long poll for anything after {@code afterSeq}. */
    static String modePath(long afterSeq) {
        return afterSeq > 0 ? PATH + "?after=" + afterSeq : PATH;
    }

    /** The request line and headers for the mode endpoint, optionally as a long poll after {@code afterSeq}. */
    public static String httpRequest(String host, long afterSeq) {
        return httpGet(host, modePath(afterSeq));
    }

    /** The request line and headers for {@code GET path} on the host app's port. */
    static String httpGet(String host, String path) {
        return "GET " + path + " HTTP/1.1\r\nHost: " + host + ":" + PORT + "\r\nConnection: close\r\n" +
                "Accept: application/json\r\n\r\n";
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%dx%d@%d%s (seq %d)", width, height, refreshHz, hdr ? " HDR" : "", seq);
    }
}
