package com.limelight.vr;

import java.util.Locale;

/**
 * The VR picture settings: how HDR is brought down to the phone's SDR picture, and color and contrast for every
 * stream. They're applied once per video frame in the renderer's prefilter pass, so they cost nearly nothing.
 *
 * <ul>
 *   <li><b>HDR white level</b> ({@link #hdrWhiteNits}): the brightness in the HDR stream that becomes the phone's
 *   white. 203 nits is the standard (ITU-R BT.2408). A lower value makes the picture brighter but squeezes more of
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
    public static final float STANDARD_WHITE_NITS = 203f;
    public static final float STANDARD_KNEE = 0.75f;
    public static final float PHONE_GAMMA = 2.2f;

    public static final PictureSettings STANDARD =
            new PictureSettings(STANDARD_WHITE_NITS, STANDARD_KNEE, 1f, PHONE_GAMMA);

    public final float hdrWhiteNits;
    public final float hdrKnee;
    public final float saturation;
    public final float gamma;

    public PictureSettings(float hdrWhiteNits, float hdrKnee, float saturation, float gamma) {
        this.hdrWhiteNits = clamp(hdrWhiteNits, 50f, 1000f);
        this.hdrKnee = clamp(hdrKnee, 0.3f, 0.95f);
        this.saturation = clamp(saturation, 0f, 2f);
        this.gamma = clamp(gamma, 1.8f, 2.8f);
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
        float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
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
                "    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));\n" +
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
        return String.format(Locale.US, "HDR white %.0f nits, compression from %.2f, color %.0f%%, gamma %.1f",
                hdrWhiteNits, hdrKnee, saturation * 100, gamma);
    }
}
