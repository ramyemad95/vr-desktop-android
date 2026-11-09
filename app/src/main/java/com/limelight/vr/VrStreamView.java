package com.limelight.vr;

import android.annotation.SuppressLint;
import android.content.Context;
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
     */
    public void configure(VrConfig config, boolean headTracking, int refreshRateHz, boolean statsPanel,
                          Listener listener) {
        this.listener = listener;
        this.headTracking = headTracking;
        this.refreshRateHz = refreshRateHz;
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

    public boolean hasHeadTracking() {
        return tracker.isAvailable();
    }

    /** Any thread. New numbers for the stats panel; ignored when the panel is off. */
    public void updateStats(VrStats stats) {
        if (renderer != null) {
            renderer.postStats(stats);
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
