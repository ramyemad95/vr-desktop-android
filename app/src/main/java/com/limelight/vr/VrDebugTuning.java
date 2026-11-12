package com.limelight.vr;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Debug builds only: picture settings that can be switched while streaming, over adb, to compare variants from
 * screenshots (scripts/vr-ab.sh):
 *
 * <pre>adb shell setprop debug.vrdesktop.hq 0</pre>
 *
 * <p>An unset (or empty, or invalid) property leaves the real setting alone. {@link #parse} and the merge methods
 * are plain Java; {@link Poller} reads the properties on the GL thread.</p>
 */
final class VrDebugTuning {
    static final String PREFIX = "debug.vrdesktop.";
    /** Property names after the prefix. "tag" comes first: see {@link Poller}. */
    static final String[] KEYS = {"tag", "frame_half", "hq", "asd", "sharp", "hq_sharp", "lod_bias", "hq_lod",
            "hq_spread", "aniso", "freeze_pose", "split"};

    /** No property set: the real settings. */
    static final VrDebugTuning NONE = parse(new LinkedHashMap<String, String>());

    /** Free text that is only echoed in the log line, so a script can tell which set of properties was applied. */
    final String tag;
    /** 0 = frame texture at the stream's size, 1 = halved, -1 = as {@link VrConfig#frameTargetSize} decides. */
    final int frameHalf;
    /** High-quality scaling and anti screen-door: 0 = off, 1 = on, -1 = the setting. */
    final int hq, asd;
    /** Sharpness 0..1, or NaN for the setting. */
    final float sharp;
    /** Replacements for {@link VrShaders#HQ_SHARPENING} (0..1), the frame's bias, HQ_LOD and HQ_SPREAD, or NaN. */
    final float hqSharp, lodBias, hqLod, hqSpread;
    /** Maximum anisotropy 1..16, or 0 for the renderer's own. */
    final int aniso;
    /** Ignore head tracking and look straight at the screen's centre. */
    final boolean freezePose;
    /** The real settings on the screen's left half, these on its right half (shader settings only). */
    final boolean split;

    private final String text;

    private VrDebugTuning(Map<String, String> p) {
        StringBuilder sb = new StringBuilder("{");
        tag = cleanTag(p.get("tag"));
        if (!tag.isEmpty()) {
            sb.append("tag=").append(tag).append(", ");
        }
        frameHalf = flag(p, "frame_half", sb);
        hq = flag(p, "hq", sb);
        asd = flag(p, "asd", sb);
        float percent = number(p, "sharp", 0f, 100f, sb);
        sharp = percent / 100f;
        percent = number(p, "hq_sharp", 0f, 100f, sb);
        hqSharp = percent / 100f;
        lodBias = number(p, "lod_bias", -4f, 4f, sb);
        hqLod = number(p, "hq_lod", -4f, 4f, sb);
        hqSpread = number(p, "hq_spread", 0f, 4f, sb);
        float a = number(p, "aniso", 1f, 16f, sb);
        aniso = Float.isNaN(a) ? 0 : Math.round(a);
        freezePose = flag(p, "freeze_pose", sb) == 1;
        split = flag(p, "split", sb) == 1;
        if (sb.length() > 1) {
            sb.setLength(sb.length() - 2);
        }
        text = sb.append('}').toString();
    }

    /**
     * Builds the tuning from property values, keyed by the names in {@link #KEYS} (without the prefix). Missing,
     * empty and unreadable values count as unset; numbers are clamped to their ranges.
     */
    static VrDebugTuning parse(Map<String, String> properties) {
        return new VrDebugTuning(properties);
    }

    private static String cleanTag(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < 64; i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 0 or 1, or -1 for anything else. */
    private static int flag(Map<String, String> p, String key, StringBuilder sb) {
        String s = p.get(key);
        s = s != null ? s.trim() : "";
        int v = s.equals("0") ? 0 : s.equals("1") ? 1 : -1;
        if (v >= 0) {
            sb.append(key).append('=').append(v).append(", ");
        }
        return v;
    }

    /** The number, clamped to [min, max], or NaN. */
    private static float number(Map<String, String> p, String key, float min, float max, StringBuilder sb) {
        String s = p.get(key);
        if (s == null || s.trim().isEmpty()) {
            return Float.NaN;
        }
        float v;
        try {
            v = Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
        if (Float.isNaN(v) || Float.isInfinite(v)) {
            return Float.NaN;
        }
        v = Math.max(min, Math.min(max, v));
        sb.append(key).append('=').append(v).append(", ");
        return v;
    }

    boolean hq(boolean setting) {
        return hq < 0 ? setting : hq == 1;
    }

    boolean asd(boolean setting) {
        return asd < 0 ? setting : asd == 1;
    }

    float sharpness(float setting) {
        return Float.isNaN(sharp) ? setting : sharp;
    }

    /**
     * The value for the shader's uSharpness. In the split view that stays the real setting (the left half reads
     * it), and the right half's is written into the shader.
     */
    float sharpnessUniform(float setting) {
        return split ? setting : sharpness(setting);
    }

    float lodBias(float setting) {
        return Float.isNaN(lodBias) ? setting : lodBias;
    }

    float hqLod() {
        return Float.isNaN(hqLod) ? VrShaders.HQ_LOD : hqLod;
    }

    float hqSharpening() {
        return Float.isNaN(hqSharp) ? VrShaders.HQ_SHARPENING : hqSharp;
    }

    float hqSpread() {
        return Float.isNaN(hqSpread) ? VrShaders.HQ_SPREAD : hqSpread;
    }

    float anisotropy(float rendererMaximum) {
        return aniso > 0 ? aniso : rendererMaximum;
    }

    /**
     * The frame texture's size: {@code decided} (from {@link VrConfig#frameTargetSize}) unless frame_half forces the
     * stream's size or half of it (a size that can't be halved exactly stays whole).
     */
    int[] frameSize(int[] decided, int videoWidth, int videoHeight) {
        if (frameHalf == 0) {
            return new int[] {videoWidth, videoHeight};
        }
        if (frameHalf == 1 && videoWidth % 2 == 0 && videoHeight % 2 == 0 && videoWidth >= 4 && videoHeight >= 4) {
            return new int[] {videoWidth / 2, videoHeight / 2};
        }
        return decided;
    }

    /** The properties that are set, as they were understood: {@code {tag=3, hq=0, lod_bias=0.0}}. */
    @Override
    public String toString() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VrDebugTuning && ((VrDebugTuning) o).text.equals(text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /** Parses getprop's output ({@code [debug.vrdesktop.hq]: [0]} lines) into the map {@link #parse} takes. */
    static Map<String, String> parseGetprop(String output) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : output.split("\n")) {
            line = line.trim();
            int sep = line.indexOf("]: [");
            if (line.startsWith("[" + PREFIX) && line.endsWith("]") && sep > 0) {
                map.put(line.substring(1 + PREFIX.length(), sep), line.substring(sep + 4, line.length() - 1));
            }
        }
        return map;
    }

    /**
     * Reads the properties, at most every {@link #INTERVAL_NANOS}, for the GL thread. A property read through
     * android.os.SystemProperties takes microseconds; where that class can't be reached, {@code getprop} runs on a
     * thread of its own and the GL thread only picks up its last result.
     */
    static final class Poller {
        static final long INTERVAL_NANOS = 500_000_000L;

        private long lastPollNanos;
        private boolean polled;
        private Map<String, String> last;
        private Method get;
        private boolean useGetprop;
        private volatile Map<String, String> fromGetprop;
        private volatile boolean getpropRunning;

        /** Makes the next poll report the tuning again, even if nothing changed (after a new GL context). */
        void reset() {
            last = null;
        }

        /**
         * @return the tuning when the properties changed since the last call (and on the first call), else null
         */
        VrDebugTuning poll(long nowNanos) {
            if (polled && nowNanos - lastPollNanos < INTERVAL_NANOS) {
                return null;
            }
            polled = true;
            lastPollNanos = nowNanos;
            Map<String, String> now = read();
            if (now == null || now.equals(last)) {
                return null;
            }
            last = now;
            return parse(now);
        }

        private Map<String, String> read() {
            if (!useGetprop) {
                try {
                    if (get == null) {
                        get = Class.forName("android.os.SystemProperties").getMethod("get", String.class);
                    }
                    // "tag" is read first and scripts set it last, so a new tag always comes with its new values
                    Map<String, String> map = new LinkedHashMap<>();
                    for (String key : KEYS) {
                        Object value = get.invoke(null, PREFIX + key);
                        if (value != null && !value.toString().isEmpty()) {
                            map.put(key, value.toString());
                        }
                    }
                    return map;
                } catch (Exception | LinkageError e) {
                    useGetprop = true;
                }
            }
            if (!getpropRunning) {
                getpropRunning = true;
                Thread thread = new Thread(() -> {
                    try {
                        Process process = new ProcessBuilder("getprop").redirectErrorStream(true).start();
                        StringBuilder sb = new StringBuilder();
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                            for (String line; (line = reader.readLine()) != null; ) {
                                if (line.startsWith("[" + PREFIX)) {
                                    sb.append(line).append('\n');
                                }
                            }
                        }
                        process.waitFor();
                        Map<String, String> map = parseGetprop(sb.toString());
                        // Empty values are unset, as with SystemProperties
                        map.values().removeIf(String::isEmpty);
                        fromGetprop = map;
                    } catch (Exception e) {
                        // Leave the last result
                    } finally {
                        getpropRunning = false;
                    }
                }, "VR debug tuning");
                thread.setDaemon(true);
                thread.start();
            }
            return fromGetprop;
        }
    }
}
