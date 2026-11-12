package com.limelight.vr;

import java.util.Locale;

/**
 * The VR picture settings: how HDR is brought down to the phone's SDR picture, and color and contrast for every
 * stream. They're applied once per video frame in the renderer's prefilter pass, so they cost nearly nothing.
 *
 * <ul>
 *   <li><b>HDR white level</b> ({@link #hdrWhiteNits}): the brightness in the HDR stream that becomes the phone's
 *   white. {@link #STANDARD_WHITE_NITS} nits is the standard (ITU-R BT.2408). A lower value makes the picture brighter but squeezes more of
 *   it into the highlight roll-off; a higher one keeps more contrast and color in bright areas, at a darker overall
 *   picture. Games often put their white higher than the standard when calibrated for a bright TV.</li>
 *   <li><b>Highlight compression</b> ({@link #hdrKnee}): the part of the picture, relative to white, that passes
 *   through unchanged before the roll-off starts. Higher keeps more punch, with less room for the brightest
 *   highlights.</li>
 *   <li><b>Color</b> ({@link #saturation}): like a TV's Color control; 1 is accurate.</li>
 *   <li><b>Contrast</b> ({@link #gamma}): the display gamma to imitate. The phone shows gamma 2.2; 2.4 (a TV in a
 *   dark room, BT.1886) gives deeper mid-tones and richer color.</li>
 * </ul>
 */
public final class PictureSettings {
    /** HDR reference white ("graphics white") per ITU-R BT.2408. */
    public static final float STANDARD_WHITE_NITS = 203f;
    /** Relative brightness (1.0 = white) above which highlights are compressed, unless the setting says otherwise. */
    public static final float STANDARD_KNEE = 0.75f;
    /** The gamma the phone's SDR picture is coded with. */
    public static final float PHONE_GAMMA = 2.2f;

    public static final PictureSettings STANDARD =
            new PictureSettings(STANDARD_WHITE_NITS, STANDARD_KNEE, 1f, PHONE_GAMMA);

    public final float hdrWhiteNits;
    public final float hdrKnee;
    public final float saturation;
    public final float gamma;
    /**
     * HDR highlights on the phone's HDR screen: the brightness whites are limited to, in nits, or 0 for as the game
     * sends them. In a headset, whites at several hundred nits glare through the lenses and make the rest of the
     * picture look pale, so a limit rolls the highlights off toward it and brightens the mid-tones a little
     * ({@link #hdrGain()}).
     */
    public final float hdrPeakNits;

    public PictureSettings(float hdrWhiteNits, float hdrKnee, float saturation, float gamma) {
        this(hdrWhiteNits, hdrKnee, saturation, gamma, 0f);
    }

    public PictureSettings(float hdrWhiteNits, float hdrKnee, float saturation, float gamma, float hdrPeakNits) {
        this.hdrWhiteNits = clamp(hdrWhiteNits, 50f, 1000f);
        this.hdrKnee = clamp(hdrKnee, 0.3f, 0.95f);
        this.saturation = clamp(saturation, 0f, 2f);
        this.gamma = clamp(gamma, 1.8f, 2.8f);
        this.hdrPeakNits = hdrPeakNits <= 0f || Float.isNaN(hdrPeakNits) ? 0f : clamp(hdrPeakNits, 100f, 4000f);
    }

    /**
     * How much the mid-tones are brightened when the highlights are limited: nothing without a limit, 20% at 400
     * nits, 40% at 250 nits and below, so the picture doesn't get dimmer overall as its whites come down.
     */
    public float hdrGain() {
        if (hdrPeakNits == 0f) {
            return 1f;
        }
        return 1f + 0.4f * clamp((533f - hdrPeakNits) / 283f, 0f, 1f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Float.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    /** Whether color and contrast are untouched, so the renderer can skip them. */
    public boolean colorIsNeutral() {
        return saturation == 1f && gamma == PHONE_GAMMA;
    }

    /** The exponent applied to the phone's gamma-coded values to imitate {@link #gamma}. */
    float contrastExponent() {
        return gamma / PHONE_GAMMA;
    }

    /**
     * Color and contrast on one gamma-coded pixel (0..1), as the shader does it: saturation around the pixel's luma,
     * clipped to 0..1, then the contrast exponent. Returns a new array.
     */
    public float[] adjust(float r, float g, float b) {
        float[] w = HdrToneMap.BT709_LUMA;
        float luma = w[0] * r + w[1] * g + w[2] * b;
        float[] out = {r, g, b};
        float e = contrastExponent();
        for (int i = 0; i < 3; i++) {
            float v = luma + (out[i] - luma) * saturation;
            v = Math.max(0f, Math.min(1f, v));
            out[i] = (float) Math.pow(v, e);
        }
        return out;
    }

    /** GLSL ES 1.0 for {@code vec3 adjustPicture(vec3 c)}, the same steps as {@link #adjust}. */
    String shaderFunction() {
        return "vec3 adjustPicture(vec3 c) {\n" +
                "    float luma = dot(c, " + HdrToneMap.glslVec3(HdrToneMap.BT709_LUMA) + ");\n" +
                "    c = clamp(vec3(luma) + (c - vec3(luma)) * " + glsl(saturation) + ", 0.0, 1.0);\n" +
                "    return pow(c, vec3(" + glsl(contrastExponent()) + "));\n" +
                "}\n";
    }

    /** A float literal GLSL ES 1.0 accepts: always a decimal point, never an exponent. */
    static String glsl(float v) {
        return String.format(Locale.US, "%.6f", v);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "HDR white %.0f nits, compression from %.2f, color %.0f%%, gamma %.1f%s",
                hdrWhiteNits, hdrKnee, saturation * 100, gamma,
                hdrPeakNits == 0f ? "" : String.format(Locale.US, ", HDR highlights to %.0f nits", hdrPeakNits));
    }
}
