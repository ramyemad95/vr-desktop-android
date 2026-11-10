package com.limelight.vr;

/**
 * Converts HDR10 video (SMPTE ST 2084 "PQ", BT.2020 primaries) to SDR for the VR view, which draws to an SDR
 * surface. Without this, an HDR stream shown as-is looks washed out.
 *
 * <p>The fragment shader does the same per pixel; {@link #shaderFunction()} builds its GLSL from the constants here,
 * and these Java versions exist so the math can be unit tested.</p>
 *
 * <ol>
 *   <li>PQ to linear light, in nits.</li>
 *   <li>BT.2020 to BT.709 primaries, scaled so SDR reference white ({@link #SDR_WHITE_NITS}, from ITU-R BT.2408) is
 *   1.0. Colors outside BT.709 clip to 0.</li>
 *   <li>Everything up to {@link #KNEE} passes through unchanged; above it, the brightest channel rolls off smoothly
 *   toward 1.0 and the other two are scaled with it, so hues don't shift.</li>
 *   <li>Gamma 2.2 for the SDR display.</li>
 * </ol>
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

    /** The highlight roll-off applied to the brightest channel. */
    public static float rollOff(float m) {
        if (m <= KNEE) {
            return m;
        }
        return (float) (KNEE + (1 - KNEE) * (1 - Math.exp(-(m - KNEE) / (1 - KNEE))));
    }

    /** One HDR10 pixel (PQ-coded BT.2020 RGB) to an SDR pixel (gamma 2.2 BT.709 RGB), as the shader does it. */
    public static float[] toSdr(float r, float g, float b) {
        float[] lin = {pqToNits(r) / SDR_WHITE_NITS, pqToNits(g) / SDR_WHITE_NITS, pqToNits(b) / SDR_WHITE_NITS};
        float[] rgb = new float[3];
        for (int i = 0; i < 3; i++) {
            float v = BT2020_TO_BT709[i * 3] * lin[0] + BT2020_TO_BT709[i * 3 + 1] * lin[1] + BT2020_TO_BT709[i * 3 + 2] * lin[2];
            rgb[i] = Math.max(v, 0f);
        }
        float m = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
        if (m > KNEE) {
            float scale = rollOff(m) / m;
            for (int i = 0; i < 3; i++) {
                rgb[i] *= scale;
            }
        }
        for (int i = 0; i < 3; i++) {
            rgb[i] = (float) Math.pow(rgb[i], 1 / 2.2);
        }
        return rgb;
    }

    /** GLSL ES 1.0 for {@code vec3 hdrToSdr(vec3 pq)}, the same steps as {@link #toSdr}. */
    static String shaderFunction() {
        float[] m = BT2020_TO_BT709;
        return "vec3 hdrToSdr(vec3 pq) {\n" +
                "    vec3 p = pow(clamp(pq, 0.0, 1.0), vec3(" + (float) (1 / M2) + "));\n" +
                "    vec3 lin = pow(max(p - " + (float) C1 + ", 0.0) / (" + (float) C2 + " - " + (float) C3 + " * p), vec3(" +
                (float) (1 / M1) + "));\n" +
                // GLSL's mat3 constructor takes columns
                "    mat3 toBt709 = mat3(" + m[0] + ", " + m[3] + ", " + m[6] + ", " + m[1] + ", " + m[4] + ", " + m[7] + ", " +
                m[2] + ", " + m[5] + ", " + m[8] + ");\n" +
                "    vec3 rgb = max(toBt709 * lin * " + (float) (PQ_MAX_NITS / SDR_WHITE_NITS) + ", 0.0);\n" +
                "    float m = max(max(rgb.r, rgb.g), rgb.b);\n" +
                "    if (m > " + KNEE + ") {\n" +
                "        rgb *= (" + KNEE + " + " + (1 - KNEE) + " * (1.0 - exp(-(m - " + KNEE + ") / " + (1 - KNEE) + "))) / m;\n" +
                "    }\n" +
                "    return pow(rgb, vec3(" + (float) (1 / 2.2) + "));\n" +
                "}\n";
    }
}
