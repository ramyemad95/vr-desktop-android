package com.limelight.vr;

/**
 * Converts HDR10 video (SMPTE ST 2084 "PQ", BT.2020 primaries) to SDR for the VR view, which draws to an SDR
 * surface. Without this, an HDR stream shown as-is looks washed out.
 *
 * <p>The fragment shader does the same per pixel; {@link #shaderFunction(boolean)} builds its GLSL from the constants
 * here, and these Java versions exist so the math can be unit tested.</p>
 *
 * <ol>
 *   <li>PQ to linear light, in nits.</li>
 *   <li>BT.2020 to the output's primaries, scaled so SDR reference white ({@link #SDR_WHITE_NITS}, from ITU-R
 *   BT.2408) is 1.0. The output is BT.709 (sRGB) by default, or Display P3 for "native" colors (see below). Colors
 *   outside the output's range clip to 0.</li>
 *   <li>Everything up to {@link #KNEE} passes through unchanged; above it, the brightest channel rolls off smoothly
 *   toward 1.0 and the other two are scaled with it, so hues don't shift.</li>
 *   <li>Gamma 2.2 for the SDR display.</li>
 * </ol>
 *
 * <p><b>Native colors:</b> phones with a wide-gamut panel, such as the Galaxy S22 Ultra in its "Vivid" screen mode,
 * show an app's colors on the panel's own primaries, which are close to Display P3 (DCI-P3 with a D65 white). Converting
 * to Display P3 instead of BT.709 keeps the saturated HDR colors that BT.709 clips, like a TV's "Native" color space.
 * On a phone that color-manages the app as sRGB ("Natural" screen mode), these colors look too saturated.</p>
 */
public final class HdrToneMap {
    /** HDR reference white ("graphics white") per ITU-R BT.2408. */
    public static final float SDR_WHITE_NITS = 203f;
    /** Relative brightness (1.0 = SDR white) above which highlights are compressed. */
    public static final float KNEE = 0.75f;

    // SMPTE ST 2084 constants
    private static final double M1 = 0.1593017578125;
    private static final double M2 = 78.84375;
    private static final double C1 = 0.8359375;
    private static final double C2 = 18.8515625;
    private static final double C3 = 18.6875;
    private static final double PQ_MAX_NITS = 10000;

    /** BT.2020 to BT.709 linear RGB, row-major. */
    static final float[] BT2020_TO_BT709 = {
            1.6605f, -0.5876f, -0.0728f,
            -0.1246f, 1.1329f, -0.0083f,
            -0.0182f, -0.1006f, 1.1187f,
    };

    /** BT.2020 to Display P3 (D65) linear RGB, row-major. */
    static final float[] BT2020_TO_DISPLAY_P3 = {
            1.3436f, -0.2822f, -0.0614f,
            -0.0653f, 1.0758f, -0.0105f,
            0.0028f, -0.0196f, 1.0168f,
    };

    /** BT.709 to BT.2020 linear RGB, row-major (the inverse direction, for SDR content on an HDR screen). */
    static final float[] BT709_TO_BT2020 = {
            0.6274f, 0.3293f, 0.0433f,
            0.0691f, 0.9195f, 0.0114f,
            0.0164f, 0.0880f, 0.8956f,
    };
    /** Display P3 to BT.2020 linear RGB, row-major: SDR content read as Display P3 (the phone's Vivid look). */
    static final float[] DISPLAY_P3_TO_BT2020 = {
            0.7538f, 0.1986f, 0.0476f,
            0.0457f, 0.9418f, 0.0125f,
            -0.0012f, 0.0176f, 0.9836f,
    };
    /**
     * BT.2020 colors stretched to the phone's full range, the way a TV's Native color space or a phone's Vivid mode
     * shows them: BT.2020 to BT.709, those values read as Display P3, back to BT.2020. Grays don't change (rows sum
     * to 1); saturated colors get up to about 20% more saturated, keeping their hue.
     */
    static final float[] BT2020_NATIVE_STRETCH = {
            1.2261f, -0.2228f, -0.0034f,
            -0.0416f, 1.0388f, 0.0028f,
            -0.0221f, -0.0783f, 1.1003f,
    };
    /** BT.2020 luminance weights. */
    private static final float[] BT2020_LUMA = {0.2627f, 0.6780f, 0.0593f};

    private HdrToneMap() {
    }

    /** PQ code value (0..1) to nits. */
    public static float pqToNits(float pq) {
        double p = Math.pow(Math.max(0, Math.min(1, pq)), 1 / M2);
        return (float) (Math.pow(Math.max(p - C1, 0) / (C2 - C3 * p), 1 / M1) * PQ_MAX_NITS);
    }

    /** Nits to PQ code value (0..1), the inverse of {@link #pqToNits}. */
    public static float nitsToPq(float nits) {
        double y = Math.pow(Math.max(0, nits) / PQ_MAX_NITS, M1);
        return (float) Math.pow((C1 + C2 * y) / (1 + C3 * y), M2);
    }

    /** The highlight roll-off applied to the brightest channel, from the standard {@link #KNEE}. */
    public static float rollOff(float m) {
        return rollOff(m, KNEE);
    }

    /** The highlight roll-off applied to the brightest channel: unchanged up to {@code knee}, then toward 1.0. */
    public static float rollOff(float m, float knee) {
        if (m <= knee) {
            return m;
        }
        return (float) (knee + (1 - knee) * (1 - Math.exp(-(m - knee) / (1 - knee))));
    }

    private static float[] matrix(boolean nativeColors) {
        return nativeColors ? BT2020_TO_DISPLAY_P3 : BT2020_TO_BT709;
    }

    /** One HDR10 pixel (PQ-coded BT.2020 RGB) to an SDR pixel (gamma 2.2 BT.709 RGB), as the shader does it. */
    public static float[] toSdr(float r, float g, float b) {
        return toSdr(r, g, b, false);
    }

    /**
     * One HDR10 pixel (PQ-coded BT.2020 RGB) to an SDR pixel with gamma 2.2, in BT.709 RGB, or in Display P3 RGB with
     * {@code nativeColors}, as the shader does it.
     */
    public static float[] toSdr(float r, float g, float b, boolean nativeColors) {
        return toSdr(r, g, b, nativeColors, PictureSettings.STANDARD);
    }

    /** As {@link #toSdr(float, float, float, boolean)}, with the picture's white level and highlight compression. */
    public static float[] toSdr(float r, float g, float b, boolean nativeColors, PictureSettings picture) {
        float[] toOutput = matrix(nativeColors);
        float white = picture.hdrWhiteNits;
        float knee = picture.hdrKnee;
        float[] lin = {pqToNits(r) / white, pqToNits(g) / white, pqToNits(b) / white};
        float[] rgb = new float[3];
        for (int i = 0; i < 3; i++) {
            float v = toOutput[i * 3] * lin[0] + toOutput[i * 3 + 1] * lin[1] + toOutput[i * 3 + 2] * lin[2];
            rgb[i] = Math.max(v, 0f);
        }
        float m = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
        if (m > knee) {
            float scale = rollOff(m, knee) / m;
            for (int i = 0; i < 3; i++) {
                rgb[i] *= scale;
            }
        }
        for (int i = 0; i < 3; i++) {
            rgb[i] = (float) Math.pow(rgb[i], 1 / 2.2);
        }
        return rgb;
    }

    /** GLSL ES 1.0 for {@code vec3 hdrToSdr(vec3 pq)} with the standard white level and compression. */
    static String shaderFunction(boolean nativeColors) {
        return shaderFunction(nativeColors, PictureSettings.STANDARD);
    }

    /** GLSL ES 1.0 for {@code vec3 hdrToSdr(vec3 pq)}, the same steps as {@link #toSdr(float, float, float, boolean, PictureSettings)}. */
    static String shaderFunction(boolean nativeColors, PictureSettings picture) {
        float[] m = matrix(nativeColors);
        float knee = picture.hdrKnee;
        return "vec3 hdrToSdr(vec3 pq) {\n" +
                "    vec3 p = pow(clamp(pq, 0.0, 1.0), vec3(" + (float) (1 / M2) + "));\n" +
                "    vec3 lin = pow(max(p - " + (float) C1 + ", 0.0) / (" + (float) C2 + " - " + (float) C3 + " * p), vec3(" +
                (float) (1 / M1) + "));\n" +
                // GLSL's mat3 constructor takes columns
                "    mat3 toOutput = mat3(" + m[0] + ", " + m[3] + ", " + m[6] + ", " + m[1] + ", " + m[4] + ", " + m[7] + ", " +
                m[2] + ", " + m[5] + ", " + m[8] + ");\n" +
                "    vec3 rgb = max(toOutput * lin * " + (float) (PQ_MAX_NITS / picture.hdrWhiteNits) + ", 0.0);\n" +
                "    float m = max(max(rgb.r, rgb.g), rgb.b);\n" +
                "    if (m > " + knee + ") {\n" +
                "        rgb *= (" + knee + " + " + (1 - knee) + " * (1.0 - exp(-(m - " + knee + ") / " + (1 - knee) + "))) / m;\n" +
                "    }\n" +
                "    return pow(rgb, vec3(" + (float) (1 / 2.2) + "));\n" +
                "}\n";
    }

    // ---- HDR on the phone's screen: the view draws to a 10-bit BT.2020 PQ surface and the display does the rest ----

    /**
     * An SDR pixel (gamma 2.2 BT.709) as PQ-coded BT.2020 RGB, with SDR white at {@code whiteNits}: for SDR streams,
     * the stats panel and the surroundings when the view draws to the phone's HDR screen.
     */
    public static float[] sdrToPq(float r, float g, float b, float whiteNits) {
        return sdrToPq(r, g, b, whiteNits, false);
    }

    /** As {@link #sdrToPq(float, float, float, float)}; with {@code nativeColors}, the SDR values are read as Display P3. */
    public static float[] sdrToPq(float r, float g, float b, float whiteNits, boolean nativeColors) {
        float[] m = nativeColors ? DISPLAY_P3_TO_BT2020 : BT709_TO_BT2020;
        float[] lin = {(float) Math.pow(clamp01(r), 2.2), (float) Math.pow(clamp01(g), 2.2), (float) Math.pow(clamp01(b), 2.2)};
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            float v = m[i * 3] * lin[0] + m[i * 3 + 1] * lin[1] + m[i * 3 + 2] * lin[2];
            out[i] = nitsToPq(Math.max(v, 0f) * whiteNits);
        }
        return out;
    }

    /** Saturation of a PQ-coded BT.2020 pixel, done in linear light around its luminance: the Color setting. */
    public static float[] saturatePq(float r, float g, float b, float saturation) {
        return hdrForPq(r, g, b, false, saturation);
    }

    /**
     * An HDR pixel (PQ-coded BT.2020) for the HDR screen: with {@code nativeColors} stretched to the phone's full
     * color range ({@link #BT2020_NATIVE_STRETCH}), then the Color setting, both in linear light.
     */
    public static float[] hdrForPq(float r, float g, float b, boolean nativeColors, float saturation) {
        float[] lin = {pqToNits(r), pqToNits(g), pqToNits(b)};
        if (nativeColors) {
            float[] m = BT2020_NATIVE_STRETCH;
            float[] stretched = new float[3];
            for (int i = 0; i < 3; i++) {
                stretched[i] = Math.max(m[i * 3] * lin[0] + m[i * 3 + 1] * lin[1] + m[i * 3 + 2] * lin[2], 0f);
            }
            lin = stretched;
        }
        float luma = BT2020_LUMA[0] * lin[0] + BT2020_LUMA[1] * lin[1] + BT2020_LUMA[2] * lin[2];
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            out[i] = nitsToPq(Math.max(luma + (lin[i] - luma) * saturation, 0f));
        }
        return out;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /**
     * GLSL ES 1.0 for the HDR screen: {@code vec3 sdrToPq(vec3 c)}, and {@code vec3 hdrForPq(vec3 pq)} which applies
     * the Color setting to a PQ pixel (or passes it through at 100%). Same steps as {@link #sdrToPq} and
     * {@link #saturatePq}.
     */
    static String pqShaderFunctions(PictureSettings picture) {
        return pqShaderFunctions(picture, false);
    }

    /** As {@link #pqShaderFunctions(PictureSettings)}; {@code nativeColors} stretches colors like a TV's Native mode. */
    static String pqShaderFunctions(PictureSettings picture, boolean nativeColors) {
        float[] m = nativeColors ? DISPLAY_P3_TO_BT2020 : BT709_TO_BT2020;
        String functions =
                "vec3 pqToLinear(vec3 pq) {\n" +
                "    vec3 p = pow(clamp(pq, 0.0, 1.0), vec3(" + (float) (1 / M2) + "));\n" +
                "    return pow(max(p - " + (float) C1 + ", 0.0) / (" + (float) C2 + " - " + (float) C3 + " * p), vec3(" +
                (float) (1 / M1) + "));\n" +
                "}\n" +
                "vec3 linearToPq(vec3 l) {\n" +
                "    vec3 y = pow(clamp(l, 0.0, 1.0), vec3(" + (float) M1 + "));\n" +
                "    return pow((" + (float) C1 + " + " + (float) C2 + " * y) / (1.0 + " + (float) C3 + " * y), vec3(" +
                (float) M2 + "));\n" +
                "}\n" +
                "vec3 sdrToPq(vec3 c) {\n" +
                "    vec3 lin = pow(clamp(c, 0.0, 1.0), vec3(2.2));\n" +
                // GLSL's mat3 constructor takes columns
                "    lin = mat3(" + m[0] + ", " + m[3] + ", " + m[6] + ", " + m[1] + ", " + m[4] + ", " + m[7] + ", " +
                m[2] + ", " + m[5] + ", " + m[8] + ") * lin;\n" +
                "    return linearToPq(max(lin, 0.0) * " + (float) (picture.hdrWhiteNits / PQ_MAX_NITS) + ");\n" +
                "}\n";
        if (picture.saturation == 1f && !nativeColors) {
            return functions + "vec3 hdrForPq(vec3 pq) {\n    return pq;\n}\n";
        }
        float[] st = BT2020_NATIVE_STRETCH;
        return functions +
                "vec3 hdrForPq(vec3 pq) {\n" +
                "    vec3 lin = pqToLinear(pq);\n" +
                (nativeColors ?
                        "    lin = max(mat3(" + st[0] + ", " + st[3] + ", " + st[6] + ", " + st[1] + ", " + st[4] + ", " + st[7] +
                        ", " + st[2] + ", " + st[5] + ", " + st[8] + ") * lin, 0.0);\n" : "") +
                (picture.saturation == 1f ? "" :
                        "    float luma = dot(lin, vec3(" + BT2020_LUMA[0] + ", " + BT2020_LUMA[1] + ", " + BT2020_LUMA[2] + "));\n" +
                        "    lin = max(vec3(luma) + (lin - vec3(luma)) * " + PictureSettings.glsl(picture.saturation) + ", 0.0);\n") +
                "    return linearToPq(lin);\n" +
                "}\n";
    }
}
