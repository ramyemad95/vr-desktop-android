package com.limelight.vr;

/**
 * 3D Mode: how a stereo 3D frame is split between the eyes.
 *
 * <p>3D movies and 3D game output pack the two eyes' pictures into one frame, squeezed side by side or one above the
 * other ("half SBS" and "half TB"). Each eye then samples only its own part of the frame, stretched back to the
 * whole virtual screen, which keeps the stream's aspect ratio. The PC's desktop is 16:9, so a player in fullscreen
 * always squeezes both pictures into one 16:9 frame; "full" layouts don't reach the phone.</p>
 */
public enum StereoMode {
    /** The normal flat picture: both eyes see the whole frame. */
    OFF("off", ""),
    /** The left eye's picture is the frame's left half. */
    SIDE_BY_SIDE("sbs", "3D SBS"),
    /** The right eye's picture is the frame's left half (cross-view or "RL" files). */
    SIDE_BY_SIDE_SWAPPED("sbs_rl", "3D SBS R/L"),
    /** The left eye's picture is the frame's top half. */
    TOP_BOTTOM("tb", "3D TB");

    /**
     * How far each eye's sample stays from the seam between the two pictures, in frame texels. The eye pass reads
     * mip levels up to about 3, and a bilinear sample there reaches half a level-3 texel (4 texels) past its
     * coordinate, so this keeps the other eye's picture from blending in as a thin line on the screen's inner edge.
     */
    static final float SEAM_INSET_TEXELS = 4f;

    private final String preferenceValue;
    private final String label;

    StereoMode(String preferenceValue, String label) {
        this.preferenceValue = preferenceValue;
        this.label = label;
    }

    /** The "3D Mode" setting's value: {@code off}, {@code sbs}, {@code sbs_rl} or {@code tb}. Anything else is off. */
    public static StereoMode fromPreference(String value) {
        for (StereoMode mode : values()) {
            if (mode.preferenceValue.equals(value)) {
                return mode;
            }
        }
        return OFF;
    }

    public boolean isStereo() {
        return this != OFF;
    }

    /** A short name for the log and the stats panel, empty when off. */
    public String label() {
        return label;
    }

    /**
     * Where {@code eye}'s picture is in the frame texture, as {@code {offsetU, offsetV, scaleU, scaleV}}: the eye's
     * screen coordinate {@code uv} (0 to 1, v = 1 at the top) samples the frame at {@code offset + uv * scale}. The
     * frame texture also has v = 1 at the picture's top, so in a top-and-bottom frame the top half is
     * {@code v} 0.5 to 1.
     *
     * @param eye {@link LensDistortion#LEFT_EYE} or {@link LensDistortion#RIGHT_EYE}
     */
    public void eyeRegion(int eye, float[] out) {
        boolean left = eye == LensDistortion.LEFT_EYE;
        switch (this) {
            case SIDE_BY_SIDE:
                set(out, left ? 0f : 0.5f, 0f, 0.5f, 1f);
                break;
            case SIDE_BY_SIDE_SWAPPED:
                set(out, left ? 0.5f : 0f, 0f, 0.5f, 1f);
                break;
            case TOP_BOTTOM:
                set(out, 0f, left ? 0.5f : 0f, 1f, 0.5f);
                break;
            default:
                set(out, 0f, 0f, 1f, 1f);
                break;
        }
    }

    /**
     * How far the eye's screen coordinate is kept from the edges of its picture, as {@code {u, v}} in that picture's
     * coordinates: {@link #SEAM_INSET_TEXELS} on the axis the frame is split along, 0 on the other (and when off).
     */
    public void regionInset(int videoWidth, int videoHeight, float[] out) {
        out[0] = 0f;
        out[1] = 0f;
        switch (this) {
            case SIDE_BY_SIDE:
            case SIDE_BY_SIDE_SWAPPED:
                out[0] = SEAM_INSET_TEXELS / (videoWidth * 0.5f);
                break;
            case TOP_BOTTOM:
                out[1] = SEAM_INSET_TEXELS / (videoHeight * 0.5f);
                break;
            default:
                break;
        }
    }

    private static void set(float[] out, float offsetU, float offsetV, float scaleU, float scaleV) {
        out[0] = offsetU;
        out[1] = offsetV;
        out[2] = scaleU;
        out[3] = scaleV;
    }
}
