package com.limelight.vr;

/**
 * The VR renderer's shader sources (GLSL ES 1.0), built from the settings. Kept apart from {@link VrRenderer} so
 * they can be built and checked without a GL context.
 */
final class VrShaders {
    private VrShaders() {
    }

    /**
     * Mip level bias for the frame. Trilinear filtering picks a level whose texels match the pixel's footprint and
     * then blends, which is softer than a box over the footprint; half a level sharper keeps text crisp without
     * visible shimmer.
     */
    static final float LOD_BIAS = -0.5f;
    /** Sharpening that high-quality scaling adds by itself, on top of the Sharpness setting. */
    static final float HQ_SHARPENING = 0.25f;

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

    private static final String EYE_FRAGMENT_SHADER_BODY =
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
            "#endif\n" +
            // The frame has mipmaps, so it's sampled for every pixel, in uniform control flow, where the hardware
            // can pick the level from the coordinate's derivatives. Outside the screen the result is unused.
            "#ifdef HQ_SCALING\n" +
            // High-quality scaling: four samples on a rotated grid inside the pixel's footprint, each one mip level
            // finer than a single sample would use, then a fixed amount of sharpening. One sample half a level sharp
            // (the default) keeps detail but aliases; this is closer to a careful (Lanczos) downscale: 3.3% error
            // against 6.4% for a 1080p stream on a 60 degree screen, 5.7% against 6.9% for 4K, in a CPU simulation.
            "    vec2 du = dFdx(fuv);\n" +
            "    vec2 dv = dFdy(fuv);\n" +
            "    vec3 screen = 0.25 * (texture2D(uFrame, fuv + 0.125 * du + 0.375 * dv, " + (LOD_BIAS - 1f) + ").rgb +\n" +
            "            texture2D(uFrame, fuv - 0.375 * du + 0.125 * dv, " + (LOD_BIAS - 1f) + ").rgb +\n" +
            "            texture2D(uFrame, fuv - 0.125 * du - 0.375 * dv, " + (LOD_BIAS - 1f) + ").rgb +\n" +
            "            texture2D(uFrame, fuv + 0.375 * du - 0.125 * dv, " + (LOD_BIAS - 1f) + ").rgb);\n" +
            "    float sharpen = uSharpness + " + HQ_SHARPENING + ";\n" +
            "#else\n" +
            "    vec3 screen = texture2D(uFrame, fuv, " + LOD_BIAS + ").rgb;\n" +
            "    float sharpen = uSharpness;\n" +
            "#endif\n" +
            "#ifdef ANTI_SCREEN_DOOR\n" +
            // Anti screen-door: brightness detail from the sharp sample, color from the next mip level. The Galaxy
            // S22 Ultra's diamond PenTile panel has half as many red and blue subpixels as green ones, so sharp color
            // detail sparkles and shows a colored mesh through the lenses; brightness (mostly green) stays sharp.
            // Sharpness then applies to brightness only, which also avoids color halos.
            "    vec3 wide = texture2D(uFrame, fuv, " + (LOD_BIAS + 1f) + ").rgb;\n" +
            "    float detail = dot(screen - wide, LUMA_WEIGHTS);\n" +
            "    screen = clamp(wide + vec3(detail * (1.0 + sharpen)), 0.0, 1.0);\n" +
            "#else\n" +
            // Unsharp mask against the next mip level: the lens and the downscale both soften text.
            "    if (sharpen > 0.0) {\n" +
            "        vec3 wide = texture2D(uFrame, fuv, " + (LOD_BIAS + 1f) + ").rgb;\n" +
            "        screen = clamp(screen + sharpen * (screen - wide), 0.0, 1.0);\n" +
            "    }\n" +
            "#endif\n" +
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
        String body = pqOutput ?
                "#define PQ_OUTPUT 1\n" + EYE_FRAGMENT_SHADER_BODY.replace(PQ_FUNCTIONS_PLACEHOLDER,
                        HdrToneMap.pqShaderFunctions(picture, nativeColors)) :
                EYE_FRAGMENT_SHADER_BODY;
        if (antiScreenDoor) {
            // Luminance weights of the frame's colors: BT.2020 on the HDR screen, BT.709 otherwise
            body = "#define ANTI_SCREEN_DOOR 1\n#define LUMA_WEIGHTS " +
                    (pqOutput ? "vec3(0.2627, 0.678, 0.0593)" : "vec3(0.2126, 0.7152, 0.0722)") + "\n" + body;
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
}
