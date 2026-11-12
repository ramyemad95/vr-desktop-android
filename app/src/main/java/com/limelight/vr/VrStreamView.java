package com.limelight.vr;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.Toast;

import com.limelight.LimeLog;
import com.limelight.R;

import java.util.Arrays;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * Full-screen stereo view that shows the stream on a virtual screen for a phone VR viewer.
 *
 * <p>It owns the decoder's output {@link Surface}: the stream starts once {@link Listener#onVideoSurfaceAvailable}
 * fires and must stop when {@link Listener#onVideoSurfaceDestroyed} fires. A tap anywhere (which is what the
 * Cardboard button does) re-centers the virtual screen in front of the user.</p>
 */
public class VrStreamView extends GLSurfaceView {
    public interface Listener {
        /** Main thread. Point the decoder at {@code surface} and start the stream. */
        void onVideoSurfaceAvailable(Surface surface);

        /** Main thread. The decoder's surface is going away: stop decoding now. */
        void onVideoSurfaceDestroyed();
    }

    private final HeadTracker tracker;
    private final GestureDetector gestures;
    private VrRenderer renderer;
    private Listener listener;
    private boolean headTracking = true;
    private int refreshRateHz = RefreshRate.HIGHEST;

    // Main thread state
    private boolean glSurfaceAlive;
    private boolean videoSurfaceReported;

    public VrStreamView(Context context) {
        super(context);
        tracker = new HeadTracker(context);
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                recenter();
                return true;
            }
        });
        setEGLContextClientVersion(2);
        // RGB888 without depth or stencil: the scene is one flat surface, and skipping the depth buffer saves
        // memory bandwidth at WQHD+ (3088x1440 on a Galaxy S22 Ultra).
        setEGLConfigChooser(8, 8, 8, 0, 0, 0);
        setPreserveEGLContextOnPause(true);
        setKeepScreenOn(true);
    }

    /**
     * Must be called once, before the view is attached to a window.
     *
     * @param headTracking  false keeps the virtual screen fixed straight ahead, whatever the head does
     * @param refreshRateHz the display refresh rate to ask for, or {@link RefreshRate#HIGHEST}
     * @param statsPanel    show network and latency stats on a panel below the virtual screen
     * @param hdrScreen     draw to the phone's HDR screen (a 10-bit BT.2020 PQ surface) instead of an SDR one
     */
    public void configure(VrConfig config, boolean headTracking, int refreshRateHz, boolean statsPanel,
                          boolean hdrScreen, Listener listener) {
        this.listener = listener;
        this.headTracking = headTracking;
        this.refreshRateHz = refreshRateHz;
        if (hdrScreen) {
            useHdrScreen();
        }
        renderer = new VrRenderer(config, tracker, statsPanel, (surface, recreated) -> post(() -> {
            if (!glSurfaceAlive) {
                return;
            }
            if (recreated && videoSurfaceReported) {
                // The GL context was lost and the decoder's surface with it. The decoder can't follow, so end the
                // stream the same way as when the window goes away.
                LimeLog.warning("VR: GL context lost during the stream");
                videoSurfaceReported = false;
                listener.onVideoSurfaceDestroyed();
                return;
            }
            if (!videoSurfaceReported) {
                videoSurfaceReported = true;
                listener.onVideoSurfaceAvailable(surface);
            }
        }));
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        if (!headTracking) {
            LimeLog.info("VR: head tracking is off in settings, the screen stays fixed to the view");
        }
        else if (!tracker.isAvailable()) {
            LimeLog.warning("VR: the screen will stay fixed to the view because this phone has no rotation sensor");
        }
    }

    // EGL_KHR_gl_colorspace and EGL_EXT_gl_colorspace_bt2020_pq
    private static final int EGL_GL_COLORSPACE_KHR = 0x309D;
    private static final int EGL_GL_COLORSPACE_BT2020_PQ_EXT = 0x3340;
    private static final int EGL_OPENGL_ES3_BIT_KHR = 0x40;

    /**
     * The phone's HDR screen: a GLES 3 context (for the 10-bit frame texture), a 10-bit RGBA config, and a window
     * surface in BT.2020 with the PQ curve, which Android composes as HDR10 like an HDR video. If the phone lacks any
     * of it, the view falls back to its usual SDR surface and the renderer tone-maps as before.
     */
    private void useHdrScreen() {
        setEGLContextClientVersion(3);
        setEGLConfigChooser((egl, display) -> {
            EGLConfig config = chooseConfig(egl, display, 10, 2);
            if (config == null) {
                LimeLog.warning("VR: no 10-bit EGL config, so no HDR screen");
                config = chooseConfig(egl, display, 8, 0);
            }
            if (config == null) {
                throw new IllegalArgumentException("VR: no usable EGL config");
            }
            return config;
        });
        setEGLWindowSurfaceFactory(new GLSurfaceView.EGLWindowSurfaceFactory() {
            @Override
            public EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display, EGLConfig config, Object nativeWindow) {
                int[] red = new int[1];
                egl.eglGetConfigAttrib(display, config, EGL10.EGL_RED_SIZE, red);
                String extensions = egl.eglQueryString(display, EGL10.EGL_EXTENSIONS);
                if (red[0] == 10 && extensions != null && extensions.contains("EGL_EXT_gl_colorspace_bt2020_pq")) {
                    int[] attribs = {EGL_GL_COLORSPACE_KHR, EGL_GL_COLORSPACE_BT2020_PQ_EXT, EGL10.EGL_NONE};
                    EGLSurface surface = egl.eglCreateWindowSurface(display, config, nativeWindow, attribs);
                    if (surface != null && surface != EGL10.EGL_NO_SURFACE) {
                        LimeLog.info("VR: drawing to the HDR screen (10-bit BT.2020 PQ surface)");
                        renderer.setPqOutput(true);
                        return surface;
                    }
                    LimeLog.warning("VR: the HDR surface failed (EGL error 0x" +
                            Integer.toHexString(egl.eglGetError()) + "), drawing SDR");
                }
                else {
                    LimeLog.warning("VR: this phone can't draw a BT.2020 PQ surface, drawing SDR");
                }
                renderer.setPqOutput(false);
                return egl.eglCreateWindowSurface(display, config, nativeWindow, null);
            }

            @Override
            public void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface) {
                egl.eglDestroySurface(display, surface);
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getHolder().setFormat(PixelFormat.RGBA_1010102);
        }
    }

    /** An RGB config of {@code bits} per color channel and {@code alphaBits} of alpha, without depth, for GLES 3. */
    private static EGLConfig chooseConfig(EGL10 egl, EGLDisplay display, int bits, int alphaBits) {
        int[] attribs = {
                EGL10.EGL_RED_SIZE, bits, EGL10.EGL_GREEN_SIZE, bits, EGL10.EGL_BLUE_SIZE, bits,
                EGL10.EGL_ALPHA_SIZE, alphaBits, EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT, EGL10.EGL_NONE,
        };
        int[] count = new int[1];
        if (!egl.eglChooseConfig(display, attribs, null, 0, count) || count[0] == 0) {
            return null;
        }
        EGLConfig[] configs = new EGLConfig[count[0]];
        egl.eglChooseConfig(display, attribs, configs, count[0], count);
        int[] value = new int[1];
        for (EGLConfig c : configs) {
            // eglChooseConfig returns configs with at least the sizes asked for: take an exact one
            egl.eglGetConfigAttrib(display, c, EGL10.EGL_RED_SIZE, value);
            int r = value[0];
            egl.eglGetConfigAttrib(display, c, EGL10.EGL_ALPHA_SIZE, value);
            if (r == bits && value[0] == alphaBits) {
                return c;
            }
        }
        return null;
    }

    public boolean hasHeadTracking() {
        return tracker.isAvailable();
    }

    /** Any thread. New numbers for the stats panel; ignored when the panel is off. */
    public void updateStats(VrStats stats) {
        if (renderer != null) {
            renderer.postStats(stats);
        }
    }

    /** Smooth video: shows the stream's frames through this pacer (see {@link FramePacer}); call after configure(). */
    public void setFramePacer(FramePacer pacer) {
        renderer.setFramePacer(pacer);
    }

    /**
     * Any thread. Whether the PC is sending HDR10; the renderer tone-maps it to SDR, or passes it to the HDR screen
     * along with the stream's HDR metadata ({@code hdrMetadata}, Sunshine's SS_HDR_METADATA bytes, or null).
     */
    public void setHdrMode(boolean enabled, byte[] hdrMetadata) {
        if (renderer != null) {
            renderer.setHdr(enabled, HdrMetadata.parse(hdrMetadata));
        }
    }

    /** Any thread. Moonlight's verdict on the connection, shown on the stats panel. */
    public void setConnectionPoor(boolean poor) {
        if (renderer != null) {
            renderer.setConnectionPoor(poor);
        }
    }

    public void recenter() {
        if (renderer != null) {
            renderer.requestRecenter();
        }
    }

    /** Call from the activity's onResume(). */
    public void startTracking() {
        if (headTracking) {
            // Without samples the renderer keeps its initial pose, so the screen stays straight ahead
            tracker.start();
        }
        recenter();
    }

    /** Call from the activity's onPause(). */
    public void stopTracking() {
        tracker.stop();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Touches never reach the PC in VR mode: with the phone in a viewer, they come from the viewer's button.
        gestures.onTouchEvent(event);
        return true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        glSurfaceAlive = true;
        requestRefreshRate(holder.getSurface());
        super.surfaceCreated(holder);
    }

    /**
     * Asks for the refresh rate from the settings, by default the display's highest at the current resolution
     * (120 Hz on a Galaxy S22 Ultra). Head motion is redrawn every refresh, so a higher rate means less lag and
     * smearing when turning the head, even though the desktop stream itself stays at its own frame rate; a lower
     * one saves battery and heat.
     */
    private void requestRefreshRate(Surface surface) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        Display display = getDisplay();
        if (display == null) {
            return;
        }
        Display.Mode current = display.getMode();
        Display.Mode[] modes = display.getSupportedModes();
        float[] available = new float[modes.length + 1];
        int count = 0;
        available[count++] = current.getRefreshRate();
        for (Display.Mode mode : modes) {
            if (mode.getPhysicalWidth() == current.getPhysicalWidth() &&
                    mode.getPhysicalHeight() == current.getPhysicalHeight()) {
                available[count++] = mode.getRefreshRate();
            }
        }
        float best = RefreshRate.choose(Arrays.copyOf(available, count), refreshRateHz);
        if (RefreshRate.isBelowWanted(best, refreshRateHz)) {
            LimeLog.warning("VR: " + refreshRateHz + " Hz isn't available, the system allows up to " + best +
                    " Hz (on Samsung, set Motion smoothness to Adaptive)");
            Toast.makeText(getContext(), getContext().getString(R.string.vr_refresh_rate_capped, refreshRateHz,
                    Math.round(best)), Toast.LENGTH_LONG).show();
        }
        LimeLog.info("VR: requesting " + best + " Hz" +
                (refreshRateHz == RefreshRate.HIGHEST ? " (highest available)" : " (setting: " + refreshRateHz + " Hz)"));
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                surface.setFrameRate(best, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface.CHANGE_FRAME_RATE_ALWAYS);
            }
            else {
                surface.setFrameRate(best, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            LimeLog.warning("VR: setFrameRate failed: " + e);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        Display display = getDisplay();
        if (display != null && renderer != null) {
            renderer.setDisplayRotation(display.getRotation());
        }
        super.surfaceChanged(holder, format, w, h);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        glSurfaceAlive = false;
        // Stop the decoder before the GL thread tears down the EGL surface.
        if (videoSurfaceReported) {
            videoSurfaceReported = false;
            listener.onVideoSurfaceDestroyed();
        }
        super.surfaceDestroyed(holder);
    }

    @Override
    protected void onDetachedFromWindow() {
        tracker.stop();
        super.onDetachedFromWindow(); // Waits for the GL thread to exit.
        if (renderer != null) {
            renderer.release();
        }
    }
}
