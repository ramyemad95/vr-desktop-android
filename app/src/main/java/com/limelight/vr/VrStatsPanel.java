package com.limelight.vr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Draws the stats panel's three lines into a bitmap that the renderer uploads as a texture. Power-of-two size, so
 * the texture can have mipmaps and the text stays clean when the panel is small in view.
 */
final class VrStatsPanel {
    static final int WIDTH = 1024;
    static final int HEIGHT = 256;

    private static final int COLOR_TEXT = 0xFFDDDDDD;
    private static final int COLOR_GOOD = 0xFF5CD65C;
    private static final int COLOR_WARN = 0xFFFFC107;
    private static final int COLOR_BAD = 0xFFFF5252;
    private static final float PADDING = 36f;
    private static final float LINE_HEIGHT = 64f;

    private final Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
    private final Canvas canvas = new Canvas(bitmap);
    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF(0, 0, WIDTH, HEIGHT);
    private final StereoMode stereo;

    /** @param stereo the 3D Mode setting, which stays the same for the whole stream */
    VrStatsPanel(StereoMode stereo) {
        this.stereo = stereo;
        background.setColor(0xD8101014);
        text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        text.setTextSize(40f);
    }

    /**
     * @param stats null until the first second of video has arrived
     * @return the panel's bitmap, redrawn in place
     */
    Bitmap draw(VrStats stats, boolean connectionPoor, float vrFps, boolean hdr) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        canvas.drawRoundRect(bounds, 28f, 28f, background);
        if (stats == null) {
            drawLine(0, "Waiting for video...", COLOR_TEXT);
            return bitmap;
        }
        drawLine(0, stats.networkLine(connectionPoor), colorOf(stats.network(connectionPoor)));
        drawLine(1, stats.videoLine(hdr, stereo), COLOR_TEXT);
        VrStats.Level processing = stats.processing();
        drawLine(2, stats.processingLine(vrFps), processing == VrStats.Level.GOOD ? COLOR_TEXT : colorOf(processing));
        return bitmap;
    }

    private void drawLine(int index, String line, int color) {
        float available = WIDTH - 2 * PADDING;
        float width = text.measureText(line);
        // Squeeze a line that's too long instead of cutting it off.
        text.setTextScaleX(width > available ? available / width : 1f);
        text.setColor(color);
        float baseline = (HEIGHT - 3 * LINE_HEIGHT) / 2f + (index + 1) * LINE_HEIGHT - 16f;
        canvas.drawText(line, PADDING, baseline, text);
        text.setTextScaleX(1f);
    }

    private static int colorOf(VrStats.Level level) {
        switch (level) {
            case BAD:
                return COLOR_BAD;
            case WARN:
                return COLOR_WARN;
            default:
                return COLOR_GOOD;
        }
    }
}
