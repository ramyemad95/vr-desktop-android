package com.limelight.vr;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.view.Surface;

import com.limelight.LimeLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Draws the stream as a virtual screen in stereo, for a phone VR viewer.
 *
 * <p>Two passes:</p>
 * <ol>
 *   <li><b>Prefilter</b>, once per decoded frame (only when one arrives): the decoder's {@link SurfaceTexture} is
 *   drawn 1:1 into an RGBA texture at the stream's resolution, HDR10 is tone-mapped to SDR on the way, and the
 *   texture's mipmaps are rebuilt. Reading the decoder's YUV texture is the expensive part, so it happens once per
 *   video pixel per frame instead of many times per display pixel per refresh.</li>
 *   <li><b>Eyes</b>, every display refresh: each eye is one mesh that already contains the inverse lens distortion;
 *   for every pixel the fragment shader builds the view ray, rotates it by the head pose, intersects it with the
 *   virtual screen and takes one trilinear (and, if available, anisotropic) sample of the prefiltered frame. The
 *   mipmaps do the downscaling that used to take 9 taps.</li>
 * </ol>
 */
final class VrRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    interface SurfaceListener {
        /** Called on the GL thread when the decoder's output surface exists, or was recreated. */
        void onVideoSurfaceCreated(Surface surface, boolean recreated);
    }

    /** Distance from the head to the virtual screen. The screen's size is set by its angular width instead. */
    private static final float SCREEN_DISTANCE_METERS = 2.0f;
    private static final int MESH_GRID_SIZE = 40;
    /** The stats panel hangs below the virtual screen, this wide and this far under its bottom edge. */
    private static final float PANEL_WIDTH_DEGREES = 30f;
    private static final float PANEL_GAP_METERS = 0.06f;
    /**
     * Mip level bias for the frame. Trilinear filtering picks a level whose texels match the pixel's footprint and
     * then blends, which is softer than a box over the footprint; half a level sharper keeps text crisp without
     * visible shimmer.
     */
    private static final float LOD_BIAS = -0.5f;
    private static final float MAX_ANISOTROPY = 4f;
    private static final int GL_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE;
    private static final int GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF;

    private static final String PRECISION =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
            "precision highp float;\n" +
            "#else\n" +
            "precision mediump float;\n" +
            "#endif\n";

    private static final String QUAD_VERTEX_SHADER =
            "attribute vec2 aPosition;\n" +
            "varying vec2 vUv;\n" +
            "void main() {\n" +
            "    vUv = aPosition * 0.5 + 0.5;\n" +
            "    gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
            "}\n";

    private static final String PREFILTER_FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            PRECISION +
            "uniform samplerExternalOES uVideo;\n" +
            "uniform mat4 uTexMatrix;\n" +
            "uniform float uHdr;\n" +
            "varying vec2 vUv;\n" +
            "\n" +
            HdrToneMap.shaderFunction() +
            "\n" +
            "void main() {\n" +
            // One output texel per video pixel, so a single tap reads each pixel exactly
            "    vec3 c = texture2D(uVideo, (uTexMatrix * vec4(vUv, 0.0, 1.0)).xy).rgb;\n" +
            "    if (uHdr > 0.5) {\n" +
            "        c = hdrToSdr(c);\n" +
            "    }\n" +
            "    gl_FragColor = vec4(c, 1.0);\n" +
            "}\n";

    private static final String EYE_VERTEX_SHADER =
            "attribute vec2 aPosition;\n" +
            "attribute vec2 aTan;\n" +
            "varying vec2 vTan;\n" +
            "void main() {\n" +
            "    vTan = aTan;\n" +
            "    gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
            "}\n";

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
            "varying vec2 vTan;\n" +
            "\n" +
            "void main() {\n" +
            "    float r2 = dot(vTan, vTan);\n" +
            "    float lensFade = 1.0 - smoothstep(uMaxTan2 * 0.85, uMaxTan2, r2);\n" +
            "    vec3 dir = uEyeToScene * vec3(vTan, -1.0);\n" +
            // A dim gradient around the horizon gives a sense of orientation.
            "    vec3 color = mix(vec3(0.015, 0.015, 0.02), vec3(0.05, 0.05, 0.065), smoothstep(-0.25, 0.25, normalize(dir).y));\n" +
            // Intersect with the plane z = -distance, for every pixel (clamped when looking away from the screen).
            "    float dz = min(dir.z, -1e-4);\n" +
            "    float t = (-uScreenDistance - uEyePos.z) / dz;\n" +
            "    vec2 uv = (uEyePos.xy + t * dir.xy) / (2.0 * uScreenHalfSize) + 0.5;\n" +
            // The frame has mipmaps, so it's sampled for every pixel, in uniform control flow, where the hardware
            // can pick the level from the coordinate's derivatives. Outside the screen the result is unused.
            "    vec3 screen = texture2D(uFrame, uv, " + LOD_BIAS + ").rgb;\n" +
            // Unsharp mask against the next mip level: the lens and the downscale both soften text.
            "    if (uSharpness > 0.0) {\n" +
            "        vec3 wide = texture2D(uFrame, uv, " + (LOD_BIAS + 1f) + ").rgb;\n" +
            "        screen = clamp(screen + uSharpness * (screen - wide), 0.0, 1.0);\n" +
            "    }\n" +
            "    if (uHasFrame < 0.5) {\n" +
            "        screen = vec3(0.10, 0.10, 0.12);\n" +
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
            "        color = color * (1.0 - panel.a * inside) + panel.rgb * inside;\n" +
            "    }\n" +
            "    gl_FragColor = vec4(color * lensFade, 1.0);\n" +
            "}\n";

    private static final float[] QUAD = {-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f};

    private final VrConfig config;
    private final HeadTracker tracker;
    private final SurfaceListener surfaceListener;

    private volatile int displayRotation = Surface.ROTATION_90;
    private volatile boolean recenterRequested = true;

    // GL thread state: eye pass
    private int program;
    private int aPosition, aTan;
    private int uFrame, uEyeToScene, uEyePos, uScreenHalfSize, uScreenDistance, uMaxTan2, uHasFrame, uSharpness;

    // GL thread state: prefilter pass
    private int prefilterProgram;
    private int pPosition, pVideo, pTexMatrix, pHdr;
    private final FloatBuffer quad = ByteBuffer.allocateDirect(QUAD.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(QUAD);
    private int frameTexture, frameBuffer;
    private boolean frameMipmaps;

    private int videoTexture;
    private SurfaceTexture surfaceTexture;
    private Surface videoSurface;
    private final AtomicInteger pendingFrames = new AtomicInteger();
    private boolean hasFrame;
    private final float[] texMatrix = new float[16];

    private final FloatBuffer[] meshVertices = new FloatBuffer[2];
    private final ShortBuffer[] meshIndices = new ShortBuffer[2];
    private int viewWidth, viewHeight;

    private final float[] deviceToWorld = new float[9];
    private float[] worldToScene = HeadPose.identity();
    private float[] headToScene = HeadPose.identity();
    private final float[] headToSceneColumnMajor = new float[9];
    private final float[] eyePos = new float[3];

    // Stats panel: null when it's off in the settings
    private final VrStatsPanel statsPanel;
    private final AtomicReference<VrStats> pendingStats = new AtomicReference<>();
    private volatile boolean connectionPoor;
    private volatile boolean hdr;
    private boolean shownHdr;
    private int uPanelTex, uPanel, uPanelCenter, uPanelHalfSize;
    private int panelTexture;
    private VrStats shownStats;
    private boolean shownConnectionPoor;
    private boolean panelDirty;
    private long fpsWindowStartNanos;
    private int fpsWindowFrames;
    private float vrFps = -1f;

    VrRenderer(VrConfig config, HeadTracker tracker, boolean statsPanel, SurfaceListener surfaceListener) {
        this.config = config;
        this.tracker = tracker;
        this.statsPanel = statsPanel ? new VrStatsPanel() : null;
        this.surfaceListener = surfaceListener;
        quad.position(0);
    }

    /** Any thread. Shows {@code stats} on the stats panel at the next frame, if the panel is on. */
    void postStats(VrStats stats) {
        pendingStats.set(stats);
    }

    /** Any thread. Moonlight's own verdict on the connection (CONN_STATUS_POOR or OKAY). */
    void setConnectionPoor(boolean poor) {
        connectionPoor = poor;
    }

    /** Any thread. Whether the PC is sending HDR10 (PQ, BT.2020), which the prefilter then tone-maps to SDR. */
    void setHdr(boolean enabled) {
        hdr = enabled;
    }

    void setDisplayRotation(int rotation) {
        displayRotation = rotation;
    }

    /**
     * Puts the virtual screen straight ahead of where the user is looking now: level with the horizon, or also up or
     * down when {@link VrConfig#recenterVertically} is set.
     */
    void requestRecenter() {
        recenterRequested = true;
    }

    /** Releases the decoder's surface. Call once the GL thread has stopped. */
    void release() {
        if (videoSurface != null) {
            videoSurface.release();
            videoSurface = null;
        }
        if (surfaceTexture != null) {
            surfaceTexture.release();
            surfaceTexture = null;
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture st) {
        pendingFrames.incrementAndGet();
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig eglConfig) {
        // Called again only if the EGL context was lost, which also loses the old textures.
        boolean recreated = surfaceTexture != null;
        release();

        program = buildEyeProgram();
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        aTan = GLES20.glGetAttribLocation(program, "aTan");
        uFrame = GLES20.glGetUniformLocation(program, "uFrame");
        uEyeToScene = GLES20.glGetUniformLocation(program, "uEyeToScene");
        uEyePos = GLES20.glGetUniformLocation(program, "uEyePos");
        uScreenHalfSize = GLES20.glGetUniformLocation(program, "uScreenHalfSize");
        uScreenDistance = GLES20.glGetUniformLocation(program, "uScreenDistance");
        uMaxTan2 = GLES20.glGetUniformLocation(program, "uMaxTan2");
        uHasFrame = GLES20.glGetUniformLocation(program, "uHasFrame");
        uSharpness = GLES20.glGetUniformLocation(program, "uSharpness");
        uPanelTex = GLES20.glGetUniformLocation(program, "uPanelTex");
        uPanel = GLES20.glGetUniformLocation(program, "uPanel");
        uPanelCenter = GLES20.glGetUniformLocation(program, "uPanelCenter");
        uPanelHalfSize = GLES20.glGetUniformLocation(program, "uPanelHalfSize");

        prefilterProgram = linkProgram(QUAD_VERTEX_SHADER, PREFILTER_FRAGMENT_SHADER);
        if (prefilterProgram == 0) {
            throw new IllegalStateException("VR: could not build the prefilter shader");
        }
        pPosition = GLES20.glGetAttribLocation(prefilterProgram, "aPosition");
        pVideo = GLES20.glGetUniformLocation(prefilterProgram, "uVideo");
        pTexMatrix = GLES20.glGetUniformLocation(prefilterProgram, "uTexMatrix");
        pHdr = GLES20.glGetUniformLocation(prefilterProgram, "uHdr");

        if (statsPanel != null) {
            int[] panelTex = new int[1];
            GLES20.glGenTextures(1, panelTex, 0);
            panelTexture = panelTex[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, panelTexture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, statsPanel.draw(shownStats, shownConnectionPoor, vrFps, shownHdr), 0);
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
            panelDirty = false;
        }

        createFrameTarget();

        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        videoTexture = tex[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTexture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        surfaceTexture = new SurfaceTexture(videoTexture);
        surfaceTexture.setDefaultBufferSize(config.videoWidth, config.videoHeight);
        surfaceTexture.setOnFrameAvailableListener(this);
        videoSurface = new Surface(surfaceTexture);
        pendingFrames.set(0);
        hasFrame = false;
        android.opengl.Matrix.setIdentityM(texMatrix, 0);

        surfaceListener.onVideoSurfaceCreated(videoSurface, recreated);
    }

    /**
     * The RGBA texture the prefilter draws each frame into, at the stream's resolution, and its framebuffer. Mipmaps
     * of a non-power-of-two texture need GLES 3 or OES_texture_npot; without them the eye pass still works, with
     * bilinear filtering only.
     */
    private void createFrameTarget() {
        String version = GLES20.glGetString(GLES20.GL_VERSION);
        String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        if (extensions == null) {
            extensions = "";
        }
        frameMipmaps = (version != null && version.startsWith("OpenGL ES 3")) || extensions.contains("GL_OES_texture_npot");

        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        frameTexture = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameTexture);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, config.videoWidth, config.videoHeight, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                frameMipmaps ? GLES20.GL_LINEAR_MIPMAP_LINEAR : GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        float anisotropy = 1f;
        if (frameMipmaps && extensions.contains("GL_EXT_texture_filter_anisotropic")) {
            float[] max = new float[1];
            GLES20.glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, max, 0);
            anisotropy = Math.min(MAX_ANISOTROPY, max[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, anisotropy);
        }
        if (frameMipmaps) {
            // Allocates the mip chain
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        }

        GLES20.glGenFramebuffers(1, ids, 0);
        frameBuffer = ids[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
                frameTexture, 0);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("VR: frame buffer incomplete: 0x" + Integer.toHexString(status));
        }
        LimeLog.info("VR: prefiltered frame " + config.videoWidth + "x" + config.videoHeight +
                (frameMipmaps ? ", mipmaps, anisotropy " + anisotropy : ", no mipmaps (" + version + ")"));
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        viewWidth = width;
        viewHeight = height;
        for (int eye = LensDistortion.LEFT_EYE; eye <= LensDistortion.RIGHT_EYE; eye++) {
            LensDistortion.EyeMesh mesh = LensDistortion.buildEyeMesh(eye, width, height,
                    config.metersPerPixel, config.viewer, MESH_GRID_SIZE);
            meshVertices[eye] = ByteBuffer.allocateDirect(mesh.vertices.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            meshVertices[eye].put(mesh.vertices).position(0);
            meshIndices[eye] = ByteBuffer.allocateDirect(mesh.indices.length * 2)
                    .order(ByteOrder.nativeOrder()).asShortBuffer();
            meshIndices[eye].put(mesh.indices).position(0);
        }
        LimeLog.info("VR: surface " + width + "x" + height + ", " + (config.metersPerPixel * 1000 * width) +
                " mm wide, screen " + config.screenWidthDegrees + " degrees");
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        // Latch every frame the decoder queued since the last draw; the last one wins. Head motion is
        // drawn at the display's refresh rate even when the desktop isn't changing.
        int frames = pendingFrames.getAndSet(0);
        if (frames > 0 && surfaceTexture != null) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            for (int i = 0; i < frames; i++) {
                surfaceTexture.updateTexImage();
            }
            surfaceTexture.getTransformMatrix(texMatrix);
            prefilterFrame();
            hasFrame = true;
        }

        updateHeadPose();
        measureFrameRate();
        updateStatsPanel();

        GLES20.glViewport(0, 0, viewWidth, viewHeight);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (meshVertices[0] == null) {
            return;
        }

        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameTexture);
        GLES20.glUniform1i(uFrame, 0);
        HeadPose.toColumnMajor(headToScene, headToSceneColumnMajor);
        GLES20.glUniformMatrix3fv(uEyeToScene, 1, false, headToSceneColumnMajor, 0);
        float halfWidth = (float) (SCREEN_DISTANCE_METERS * Math.tan(Math.toRadians(config.screenWidthDegrees / 2.0)));
        GLES20.glUniform2f(uScreenHalfSize, halfWidth, halfWidth * config.videoHeight / config.videoWidth);
        GLES20.glUniform1f(uScreenDistance, SCREEN_DISTANCE_METERS);
        float maxTan = (float) Math.tan(Math.toRadians(config.viewer.halfFovDegrees));
        GLES20.glUniform1f(uMaxTan2, maxTan * maxTan);
        GLES20.glUniform1f(uHasFrame, hasFrame ? 1f : 0f);
        GLES20.glUniform1f(uSharpness, config.sharpness);
        GLES20.glUniform1i(uPanelTex, 1);
        if (statsPanel != null) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, panelTexture);
            float panelHalfWidth = (float) (SCREEN_DISTANCE_METERS * Math.tan(Math.toRadians(PANEL_WIDTH_DEGREES / 2.0)));
            float panelHalfHeight = panelHalfWidth * VrStatsPanel.HEIGHT / VrStatsPanel.WIDTH;
            float screenHalfHeight = halfWidth * config.videoHeight / config.videoWidth;
            GLES20.glUniform1f(uPanel, 1f);
            GLES20.glUniform2f(uPanelCenter, 0f, -(screenHalfHeight + PANEL_GAP_METERS + panelHalfHeight));
            GLES20.glUniform2f(uPanelHalfSize, panelHalfWidth, panelHalfHeight);
            // SurfaceTexture.updateTexImage() binds to the active unit, so leave unit 0 active for the next frame
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        }
        else {
            GLES20.glUniform1f(uPanel, 0f);
        }

        int eyeWidth = viewWidth / 2;
        for (int eye = LensDistortion.LEFT_EYE; eye <= LensDistortion.RIGHT_EYE; eye++) {
            float eyeOffset = (eye == LensDistortion.LEFT_EYE ? -0.5f : 0.5f) * config.viewer.interLensDistance;
            HeadPose.transform(headToScene, eyeOffset, 0f, 0f, eyePos);
            GLES20.glUniform3f(uEyePos, eyePos[0], eyePos[1], eyePos[2]);

            GLES20.glViewport(eye * eyeWidth, 0, eyeWidth, viewHeight);
            FloatBuffer vb = meshVertices[eye];
            vb.position(0);
            GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, LensDistortion.EyeMesh.FLOATS_PER_VERTEX * 4, vb);
            GLES20.glEnableVertexAttribArray(aPosition);
            vb.position(2);
            GLES20.glVertexAttribPointer(aTan, 2, GLES20.GL_FLOAT, false, LensDistortion.EyeMesh.FLOATS_PER_VERTEX * 4, vb);
            GLES20.glEnableVertexAttribArray(aTan);
            ShortBuffer ib = meshIndices[eye];
            ib.position(0);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, ib.capacity(), GLES20.GL_UNSIGNED_SHORT, ib);
        }
        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glDisableVertexAttribArray(aTan);

        // A thin line between the eyes helps to center the phone in the viewer.
        GLES20.glViewport(0, 0, viewWidth, viewHeight);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        int lineWidth = Math.max(2, viewWidth / 800);
        GLES20.glScissor(viewWidth / 2 - lineWidth / 2, 0, lineWidth, viewHeight / 8);
        GLES20.glClearColor(0.35f, 0.35f, 0.35f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

    /** Draws the latest decoded frame into the RGBA frame texture (tone-mapping HDR10) and rebuilds its mipmaps. */
    private void prefilterFrame() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer);
        GLES20.glViewport(0, 0, config.videoWidth, config.videoHeight);
        GLES20.glUseProgram(prefilterProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTexture);
        GLES20.glUniform1i(pVideo, 0);
        GLES20.glUniformMatrix4fv(pTexMatrix, 1, false, texMatrix, 0);
        GLES20.glUniform1f(pHdr, hdr ? 1f : 0f);
        quad.position(0);
        GLES20.glVertexAttribPointer(pPosition, 2, GLES20.GL_FLOAT, false, 0, quad);
        GLES20.glEnableVertexAttribArray(pPosition);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(pPosition);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        if (frameMipmaps) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameTexture);
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        }
    }

    /** How often this view is actually redrawn, over one-second windows. Shown on the stats panel. */
    private void measureFrameRate() {
        long now = System.nanoTime();
        if (fpsWindowStartNanos == 0) {
            fpsWindowStartNanos = now;
            return;
        }
        fpsWindowFrames++;
        long elapsed = now - fpsWindowStartNanos;
        if (elapsed >= 1_000_000_000L) {
            vrFps = fpsWindowFrames * 1e9f / elapsed;
            fpsWindowFrames = 0;
            fpsWindowStartNanos = now;
        }
    }

    /** Redraws the panel's texture when new stats arrive (about once a second) or the connection verdict flips. */
    private void updateStatsPanel() {
        if (statsPanel == null) {
            return;
        }
        VrStats stats = pendingStats.getAndSet(null);
        if (stats != null) {
            shownStats = stats;
            panelDirty = true;
        }
        boolean poor = connectionPoor;
        if (poor != shownConnectionPoor) {
            shownConnectionPoor = poor;
            panelDirty = true;
        }
        boolean hdrNow = hdr;
        if (hdrNow != shownHdr) {
            shownHdr = hdrNow;
            panelDirty = true;
        }
        if (!panelDirty) {
            return;
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, panelTexture);
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, statsPanel.draw(shownStats, shownConnectionPoor, vrFps, shownHdr));
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        panelDirty = false;
    }

    private void updateHeadPose() {
        if (!tracker.getDeviceToWorld(displayRotation, deviceToWorld)) {
            return;
        }
        if (recenterRequested) {
            recenterRequested = false;
            worldToScene = HeadPose.recenterFrame(deviceToWorld, config.recenterVertically);
        }
        HeadPose.multiply(worldToScene, deviceToWorld, headToScene);
    }

    private static int buildEyeProgram() {
        // Prefer the shader with anti-aliased screen edges; it needs derivatives, which a few old GPUs lack in
        // ESSL 1.0.
        int p = linkProgram(EYE_VERTEX_SHADER,
                "#extension GL_OES_standard_derivatives : require\n#define DERIVATIVES 1\n" + EYE_FRAGMENT_SHADER_BODY);
        if (p == 0) {
            LimeLog.warning("VR: derivatives unavailable, screen edges won't be anti-aliased");
            p = linkProgram(EYE_VERTEX_SHADER, EYE_FRAGMENT_SHADER_BODY);
        }
        if (p == 0) {
            throw new IllegalStateException("VR: could not build the renderer's shader");
        }
        return p;
    }

    private static int linkProgram(String vertexSource, String fragmentSource) {
        int vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (vs == 0 || fs == 0) {
            GLES20.glDeleteShader(vs);
            GLES20.glDeleteShader(fs);
            return 0;
        }
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, vs);
        GLES20.glAttachShader(p, fs);
        GLES20.glLinkProgram(p);
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            LimeLog.warning("VR: program link failed: " + GLES20.glGetProgramInfoLog(p));
            GLES20.glDeleteProgram(p);
            return 0;
        }
        return p;
    }

    private static int compileShader(int type, String source) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, source);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            LimeLog.warning("VR: shader compile failed: " + GLES20.glGetShaderInfoLog(s));
            GLES20.glDeleteShader(s);
            return 0;
        }
        return s;
    }
}
