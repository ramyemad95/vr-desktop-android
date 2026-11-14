package com.limelight.vr;

/**
 * The VR renderer's shader sources (GLSL ES 1.0), built from the settings. Kept apart from {@link VrRenderer} so
 * they can be built and checked without a GL context.
 */
final class VrShaders {
    private VrShaders() {
    }

    /**
     * Mip level bias for the frame at a Picture softness of 0, the sharpest the setting goes ({@link #lodBias} adds
     * the setting). Trilinear filtering picks a level whose texels match the pixel's footprint and then blends,
     * which is softer than a box over the footprint; half a level sharper keeps text crisp without visible shimmer.
     */
    static final float LOD_BIAS = -0.5f;
    /**
     * Sharpening that high-quality scaling adds by itself, on top of the Sharpness setting: none. It was 0.25 until
     * a side-by-side test in the headset, where the picture without it won; debug builds can still set it
     * ({@link VrDebugTuning}).
     */
    static final float HQ_SHARPENING = 0f;
    /**
     * Mip level offset of high-quality scaling's four samples, on top of the frame's bias. Half a level finer: a
     * whole level (the first tuning) left gaps between the samples, which showed as shimmer on fine detail.
     */
    static final float HQ_LOD = -0.5f;
    /** How far apart high-quality scaling's four samples are, as a multiple of their rotated-grid offsets. */
    static final float HQ_SPREAD = 1f;
    /** How many mip levels the Picture softness setting adds to {@link #LOD_BIAS} at 100%. */
    static final float SOFTNESS_LOD_RANGE = 2f;

    /** The frame's mip level bias for a Picture softness of 0 (as sharp as it goes) to 1. */
    static float lodBias(float softness) {
        return LOD_BIAS + SOFTNESS_LOD_RANGE * Math.max(0f, Math.min(1f, softness));
    }

    static final String PRECISION =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
            "precision highp float;\n" +
            "#else\n" +
            "precision mediump float;\n" +
            "#endif\n";

    static final String QUAD_VERTEX_SHADER =
            "attribute vec2 aPosition;\n" +
            "varying vec2 vUv;\n" +
            "void main() {\n" +
            "    vUv = aPosition * 0.5 + 0.5;\n" +
            "    gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
            "}\n";

    static String prefilterFragmentShader(boolean nativeColors, PictureSettings picture, boolean pqOutput) {
        boolean adjust = !picture.colorIsNeutral();
        if (pqOutput) {
            // HDR screen: HDR frames stay PQ BT.2020 (with the Color setting); SDR frames are converted to PQ with
            // their white at the HDR white level. The display does the tone mapping.
            return "#extension GL_OES_EGL_image_external : require\n" +
                    PRECISION +
                    "uniform samplerExternalOES uVideo;\n" +
                    "uniform mat4 uTexMatrix;\n" +
                    "uniform float uHdr;\n" +
                    "varying vec2 vUv;\n" +
                    "\n" +
                    HdrToneMap.pqShaderFunctions(picture, nativeColors) +
                    (adjust ? picture.shaderFunction() : "") +
                    "\n" +
                    "void main() {\n" +
                    "    vec3 c = texture2D(uVideo, (uTexMatrix * vec4(vUv, 0.0, 1.0)).xy).rgb;\n" +
                    "    if (uHdr > 0.5) {\n" +
                    "        c = hdrForPq(c);\n" +
                    "    }\n" +
                    "    else {\n" +
                    (adjust ? "        c = adjustPicture(c);\n" : "") +
                    "        c = sdrToPq(c);\n" +
                    "    }\n" +
                    "    gl_FragColor = vec4(c, 1.0);\n" +
                    "}\n";
        }
        return "#extension GL_OES_EGL_image_external : require\n" +
                PRECISION +
                "uniform samplerExternalOES uVideo;\n" +
                "uniform mat4 uTexMatrix;\n" +
                "uniform float uHdr;\n" +
                "varying vec2 vUv;\n" +
                "\n" +
                HdrToneMap.shaderFunction(nativeColors, picture) +
                (adjust ? picture.shaderFunction() : "") +
                "\n" +
                "void main() {\n" +
                // One output texel per video pixel, so a single tap reads each pixel exactly
                "    vec3 c = texture2D(uVideo, (uTexMatrix * vec4(vUv, 0.0, 1.0)).xy).rgb;\n" +
                "    if (uHdr > 0.5) {\n" +
                "        c = hdrToSdr(c);\n" +
                "    }\n" +
                (adjust ? "    c = adjustPicture(c);\n" : "") +
                "    gl_FragColor = vec4(c, 1.0);\n" +
                "}\n";
    }

    static final String EYE_VERTEX_SHADER =
            "attribute vec2 aPosition;\n" +
            "attribute vec2 aTan;\n" +
            "varying vec2 vTan;\n" +
            "void main() {\n" +
            "    vTan = aTan;\n" +
            "    gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
            "}\n";

    /** Where the HDR screen's color functions go in the eye shader (after the declarations, before main). */
    private static final String PQ_FUNCTIONS_PLACEHOLDER = "// PQ functions\n";

    private static final String EYE_FRAGMENT_SHADER_HEAD =
            PRECISION +
            "uniform sampler2D uFrame;\n" +
            "uniform mat3 uEyeToScene;\n" +
            "uniform vec3 uEyePos;\n" +
            "uniform vec2 uScreenHalfSize;\n" +
            "uniform float uScreenDistance;\n" +
            "uniform float uMaxTan2;\n" +
            "uniform float uHasFrame;\n" +
            "uniform float uSharpness;\n" +
            "uniform sampler2D uPanelTex;\n" +
            "uniform float uPanel;\n" +
            "uniform vec2 uPanelCenter;\n" +
            "uniform vec2 uPanelHalfSize;\n" +
            "#ifdef STEREO_3D\n" +
            // 3D Mode: this eye's picture in the frame, {offset, scale}, and how far to keep from its edges
            "uniform vec4 uEyeRegion;\n" +
            "uniform vec2 uRegionInset;\n" +
            "#endif\n" +
            "varying vec2 vTan;\n" +
            "\n" +
            PQ_FUNCTIONS_PLACEHOLDER +
            "void main() {\n" +
            "    float r2 = dot(vTan, vTan);\n" +
            "    float lensFade = 1.0 - smoothstep(uMaxTan2 * 0.85, uMaxTan2, r2);\n" +
            "    vec3 dir = uEyeToScene * vec3(vTan, -1.0);\n" +
            // A dim gradient around the horizon gives a sense of orientation.
            "    vec3 color = mix(vec3(0.015, 0.015, 0.02), vec3(0.05, 0.05, 0.065), smoothstep(-0.25, 0.25, normalize(dir).y));\n" +
            "#ifdef PQ_OUTPUT\n" +
            "    color = sdrToPq(color);\n" +
            "#endif\n" +
            // Intersect with the plane z = -distance, for every pixel (clamped when looking away from the screen).
            "    float dz = min(dir.z, -1e-4);\n" +
            "    float t = (-uScreenDistance - uEyePos.z) / dz;\n" +
            "    vec2 uv = (uEyePos.xy + t * dir.xy) / (2.0 * uScreenHalfSize) + 0.5;\n" +
            "#ifdef STEREO_3D\n" +
            // 3D Mode: sample only this eye's picture, stretched over the whole screen. The clamp keeps the mip
            // levels from blending in the other eye's picture at the seam. The coordinate's derivatives shrink with
            // the region, so the level and anisotropy still follow the texels each pixel covers.
            "    vec2 fuv = uEyeRegion.xy + clamp(uv, uRegionInset, 1.0 - uRegionInset) * uEyeRegion.zw;\n" +
            "#else\n" +
            "    vec2 fuv = uv;\n" +
            "#endif\n";

    /**
     * The part of the eye shader that reads the frame at {@code fuv} into {@code vec3 screen}. The numbers are
     * parameters only for {@link VrDebugTuning}; everything else passes the constants above.
     *
     * @param sharpness the GLSL expression for the Sharpness setting
     */
    private static String frameSampling(float lodBias, float hqLod, float hqSharpening, float hqSpread, String sharpness) {
        float near = 0.125f * hqSpread, far = 0.375f * hqSpread;
        return
            // The frame has mipmaps, so it's sampled for every pixel, in uniform control flow, where the hardware
            // can pick the level from the coordinate's derivatives. Outside the screen the result is unused.
            "#ifdef HQ_SCALING\n" +
            // High-quality scaling: four samples on a rotated grid inside the pixel's footprint, each HQ_LOD mip
            // levels finer than a single sample would use, plus HQ_SHARPENING of sharpening. One sample alone
            // keeps detail but aliases; four are closer to a careful (Lanczos) downscale.
            "    vec2 du = dFdx(fuv);\n" +
            "    vec2 dv = dFdy(fuv);\n" +
            "    vec3 screen = 0.25 * (texture2D(uFrame, fuv + " + near + " * du + " + far + " * dv, " + (lodBias + hqLod) + ").rgb +\n" +
            "            texture2D(uFrame, fuv - " + far + " * du + " + near + " * dv, " + (lodBias + hqLod) + ").rgb +\n" +
            "            texture2D(uFrame, fuv - " + near + " * du - " + far + " * dv, " + (lodBias + hqLod) + ").rgb +\n" +
            "            texture2D(uFrame, fuv + " + far + " * du - " + near + " * dv, " + (lodBias + hqLod) + ").rgb);\n" +
            "    float sharpen = " + sharpness + " + " + hqSharpening + ";\n" +
            "#else\n" +
            "    vec3 screen = texture2D(uFrame, fuv, " + lodBias + ").rgb;\n" +
            "    float sharpen = " + sharpness + ";\n" +
            "#endif\n" +
            "#ifdef ANTI_SCREEN_DOOR\n" +
            // Anti screen-door: brightness detail from the sharp sample, color from the next mip level. The Galaxy
            // S22 Ultra's diamond PenTile panel has half as many red and blue subpixels as green ones, so sharp color
            // detail sparkles and shows a colored mesh through the lenses; brightness (mostly green) stays sharp.
            // Sharpness then applies to brightness only, which also avoids color halos.
            "    vec3 wide = texture2D(uFrame, fuv, " + (lodBias + 1f) + ").rgb;\n" +
            "    float detail = dot(screen - wide, LUMA_WEIGHTS);\n" +
            "    screen = clamp(wide + vec3(detail * (1.0 + sharpen)), 0.0, 1.0);\n" +
            "#else\n" +
            // Unsharp mask against the next mip level: the lens and the downscale both soften text.
            "    if (sharpen > 0.0) {\n" +
            "        vec3 wide = texture2D(uFrame, fuv, " + (lodBias + 1f) + ").rgb;\n" +
            "        screen = clamp(screen + sharpen * (screen - wide), 0.0, 1.0);\n" +
            "    }\n" +
            "#endif\n";
    }

    private static final String EYE_FRAGMENT_SHADER_TAIL =
            "    if (uHasFrame < 0.5) {\n" +
            "        screen = vec3(0.10, 0.10, 0.12);\n" +
            "#ifdef PQ_OUTPUT\n" +
            "        screen = sdrToPq(screen);\n" +
            "#endif\n" +
            "    }\n" +
            "#ifdef DERIVATIVES\n" +
            // Anti-aliased screen edges: how far inside the screen this pixel is, in pixels.
            "    vec2 footprint = max(abs(dFdx(uv)) + abs(dFdy(uv)), vec2(1e-6));\n" +
            "    vec2 edge = min(uv, 1.0 - uv) / footprint;\n" +
            "    float coverage = clamp(min(edge.x, edge.y) + 0.5, 0.0, 1.0);\n" +
            "#else\n" +
            "    float coverage = (uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0) ? 1.0 : 0.0;\n" +
            "#endif\n" +
            "    if (dir.z < 0.0) {\n" +
            "        color = mix(color, screen, coverage);\n" +
            "    }\n" +
            // The stats panel lies in the same plane, below the screen. Its bitmap is premultiplied and has mipmaps;
            // the branch is on a uniform, so the texture's derivatives stay defined.
            "    if (uPanel > 0.5) {\n" +
            "        vec2 puv = (uEyePos.xy + t * dir.xy - uPanelCenter) / (2.0 * uPanelHalfSize) + 0.5;\n" +
            "        vec4 panel = texture2D(uPanelTex, vec2(puv.x, 1.0 - puv.y));\n" +
            "        float inside = (dir.z < 0.0 && puv.x >= 0.0 && puv.x <= 1.0 && puv.y >= 0.0 && puv.y <= 1.0) ? 1.0 : 0.0;\n" +
            "#ifdef PQ_OUTPUT\n" +
            "        color = color * (1.0 - panel.a * inside) + sdrToPq(panel.rgb) * inside;\n" +
            "#else\n" +
            "        color = color * (1.0 - panel.a * inside) + panel.rgb * inside;\n" +
            "#endif\n" +
            "    }\n" +
            "    gl_FragColor = vec4(color * lensFade, 1.0);\n" +
            "}\n";

    /**
     * The eye pass's fragment shader: anti-aliased screen edges with {@code derivatives}
     * (GL_OES_standard_derivatives), PQ output for the phone's HDR screen with {@code pqOutput}, and each eye's own
     * part of a 3D frame with {@code stereo} (see {@link StereoMode}).
     */
    static String eyeFragmentShader(boolean pqOutput, PictureSettings picture, boolean nativeColors,
                                    boolean antiScreenDoor, boolean stereo, boolean hqScaling, boolean derivatives) {
        return eyeFragmentShader(pqOutput, picture, nativeColors, antiScreenDoor, stereo, hqScaling, derivatives,
                LOD_BIAS, VrDebugTuning.NONE);
    }

    /**
     * The eye shader with a debug build's live tuning (see {@link VrDebugTuning}); with {@link VrDebugTuning#NONE}
     * it is the settings' shader exactly. {@code antiScreenDoor}, {@code hqScaling} and {@code lodBias} (from the
     * Picture softness setting, see {@link #lodBias}) are the real settings.
     */
    static String eyeFragmentShader(boolean pqOutput, PictureSettings picture, boolean nativeColors,
                                    boolean antiScreenDoor, boolean stereo, boolean hqScaling, boolean derivatives,
                                    float lodBias, VrDebugTuning tuning) {
        String tuned = frameSampling(tuning.lodBias(lodBias), tuning.hqLod(), tuning.hqSharpening(), tuning.hqSpread(),
                tuning.split && !Float.isNaN(tuning.sharp) ? String.valueOf(tuning.sharp) : "uSharpness");
        String sampling = tuned;
        boolean split = tuning.split;
        if (split) {
            // Debug split view: the real settings on the screen's left half, the tuned ones on its right half.
            // Both are computed for every pixel (texture reads must stay in uniform control flow), each in its own
            // block with its own HQ_SCALING and ANTI_SCREEN_DOOR.
            sampling = "    vec3 screenA;\n" +
                    "    vec3 screenB;\n" +
                    samplingDefines(hqScaling && derivatives, antiScreenDoor) +
                    "    {\n" +
                    frameSampling(lodBias, HQ_LOD, HQ_SHARPENING, HQ_SPREAD, "uSharpness") +
                    "    screenA = screen;\n" +
                    "    }\n" +
                    samplingDefines(tuning.hq(hqScaling) && derivatives, tuning.asd(antiScreenDoor)) +
                    "    {\n" +
                    tuned +
                    "    screenB = screen;\n" +
                    "    }\n" +
                    "    vec3 screen = uv.x < 0.5 ? screenA : screenB;\n";
        }
        else {
            antiScreenDoor = tuning.asd(antiScreenDoor);
            hqScaling = tuning.hq(hqScaling);
        }
        String eyeBody = EYE_FRAGMENT_SHADER_HEAD + sampling + EYE_FRAGMENT_SHADER_TAIL;
        String body = pqOutput ?
                "#define PQ_OUTPUT 1\n" + eyeBody.replace(PQ_FUNCTIONS_PLACEHOLDER,
                        HdrToneMap.pqShaderFunctions(picture, nativeColors)) :
                eyeBody;
        if (split) {
            // The blocks define ANTI_SCREEN_DOOR and HQ_SCALING themselves
            body = "#define LUMA_WEIGHTS " +
                    lumaWeights(pqOutput) + "\n" + body;
            antiScreenDoor = false;
            hqScaling = false;
        }
        if (antiScreenDoor) {
            body = "#define ANTI_SCREEN_DOOR 1\n#define LUMA_WEIGHTS " +
                    lumaWeights(pqOutput) + "\n" + body;
        }
        if (stereo) {
            body = "#define STEREO_3D 1\n" + body;
        }
        if (hqScaling && derivatives) {
            // The four samples are placed with the coordinate's derivatives
            body = "#define HQ_SCALING 1\n" + body;
        }
        return derivatives ? "#extension GL_OES_standard_derivatives : require\n#define DERIVATIVES 1\n" + body : body;
    }

    /** Luminance weights of the frame's colors: BT.2020 on the HDR screen, BT.709 otherwise. */
    private static String lumaWeights(boolean pqOutput) {
        return HdrToneMap.glslVec3(pqOutput ? HdrToneMap.BT2020_LUMA : HdrToneMap.BT709_LUMA);
    }

    /** Debug split view: sets HQ_SCALING and ANTI_SCREEN_DOOR for the block that follows. */
    private static String samplingDefines(boolean hqScaling, boolean antiScreenDoor) {
        return "#undef HQ_SCALING\n#undef ANTI_SCREEN_DOOR\n" +
                (hqScaling ? "#define HQ_SCALING 1\n" : "") +
                (antiScreenDoor ? "#define ANTI_SCREEN_DOOR 1\n" : "");
    }
}
