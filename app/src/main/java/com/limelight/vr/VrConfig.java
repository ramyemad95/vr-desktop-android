package com.limelight.vr;

/** Everything the VR renderer needs to know up front. */
public final class VrConfig {
    public final ViewerProfile viewer;
    /** How much of the user's horizontal view the virtual screen covers. */
    public final float screenWidthDegrees;
    /** Stream resolution; sets the virtual screen's aspect ratio. */
    public final int videoWidth, videoHeight;
    /** Physical size of one phone screen pixel. */
    public final float metersPerPixel;
    /** Strength of the unsharp mask on the video, 0 (off) to 1. */
    public final float sharpness;
    /** A re-center also follows the head's pitch, so the screen goes where the user looks, up or down. */
    public final boolean recenterVertically;

    public VrConfig(ViewerProfile viewer, float screenWidthDegrees, int videoWidth, int videoHeight, float metersPerPixel,
                    float sharpness, boolean recenterVertically) {
        this.viewer = viewer;
        this.screenWidthDegrees = screenWidthDegrees;
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.metersPerPixel = metersPerPixel;
        this.sharpness = Math.max(0f, Math.min(1f, sharpness));
        this.recenterVertically = recenterVertically;
    }

    /** The "Sharpness" setting, in percent, as an unsharp-mask strength. */
    public static float sharpnessFromPercent(int percent) {
        return Math.max(0, Math.min(100, percent)) / 100f;
    }

    private static final float METERS_PER_INCH = 0.0254f;

    /**
     * Width of the screen's long side in meters, for phones where it is known, or 0.
     *
     * <p>This is more reliable than the reported densities, which don't always follow Samsung's screen resolution
     * setting (FHD+ or WQHD+). A wrong pixel size puts the lens centers in the wrong place.</p>
     */
    public static float knownScreenWidthMeters(String model) {
        if (model == null) {
            return 0f;
        }
        // Galaxy S22 Ultra (SM-S908B/E/N/U/U1/W, SM-S9080): 6.8" 3088x1440 panel, 172.6 mm diagonal.
        if (model.startsWith("SM-S908")) {
            return 0.1564f;
        }
        return 0f;
    }

    /**
     * Physical pixel size: from the known screen width for supported phones, otherwise from the reported densities.
     *
     * @param screenLongSidePx the screen's long side in pixels, at the current resolution setting
     */
    public static float metersPerPixel(String model, int screenLongSidePx, float xdpi, float ydpi, int densityDpi) {
        float width = knownScreenWidthMeters(model);
        if (width > 0f && screenLongSidePx > 0) {
            return width / screenLongSidePx;
        }
        return metersPerPixel(xdpi, ydpi, densityDpi);
    }

    /**
     * Physical pixel size from the display's reported densities.
     *
     * <p>{@code xdpi}/{@code ydpi} are the measured values but some devices report nonsense, so they are averaged
     * and checked against {@code densityDpi} (the rounded bucket density, which is always set). If they disagree by
     * more than 30%, the bucket density is used: the lens centers then land a few millimeters off, which is still
     * usable.</p>
     */
    public static float metersPerPixel(float xdpi, float ydpi, int densityDpi) {
        float measured = (xdpi + ydpi) / 2f;
        float dpi = measured;
        if (densityDpi > 0 && (measured <= 0 || Math.abs(measured - densityDpi) / densityDpi > 0.3f)) {
            dpi = densityDpi;
        }
        if (dpi <= 0) {
            dpi = 420f; // A typical 6" 1080p+ phone.
        }
        return METERS_PER_INCH / dpi;
    }
}
