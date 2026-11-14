package com.limelight.vr;

/**
 * Radial lens distortion of a phone VR viewer, and the per-eye mesh that undoes it.
 *
 * <p>Radii are tangents of the angle from the lens axis. {@link #distort} maps a point on the phone screen to the
 * angle at which the eye sees it through the lens. The lens magnifies more toward its edge (pincushion
 * distortion), so the image drawn on the screen has to be barrel-shaped to look straight.</p>
 */
public final class LensDistortion {
    private LensDistortion() {
    }

    /** Eye-side radius for a screen-side radius: {@code r * (1 + k1 r^2 + k2 r^4)}. */
    public static float distort(float screenRadius, float k1, float k2) {
        float r2 = screenRadius * screenRadius;
        return screenRadius * (1f + k1 * r2 + k2 * r2 * r2);
    }

    /**
     * Screen-side radius for an eye-side radius (the inverse of {@link #distort}), by Newton's method.
     * For positive coefficients the polynomial is convex and increasing, so Newton converges from the starting guess.
     */
    public static float undistort(float eyeRadius, float k1, float k2) {
        if (eyeRadius <= 0f) {
            return 0f;
        }
        double r = eyeRadius;
        for (int i = 0; i < 32; i++) {
            double r2 = r * r;
            double f = r * (1 + k1 * r2 + k2 * r2 * r2) - eyeRadius;
            double df = 1 + 3 * k1 * r2 + 5 * k2 * r2 * r2;
            if (df <= 0) {
                break;
            }
            double step = f / df;
            r = Math.max(0, r - step);
            if (Math.abs(step) < 1e-9) {
                break;
            }
        }
        return (float) r;
    }

    /**
     * Grid mesh covering one eye's half of a landscape phone screen.
     *
     * <p>Each vertex holds 4 floats: its position in the eye viewport's normalized device coordinates (-1..1), and
     * the tangent of the view angle (x right, y up) that the eye sees through the lens at that point. The fragment
     * shader turns that tangent into a ray and intersects it with the virtual screen, so the video is sampled once,
     * directly, with no intermediate render target.</p>
     */
    public static final class EyeMesh {
        public static final int FLOATS_PER_VERTEX = 4;

        public final float[] vertices;
        public final short[] indices;

        EyeMesh(float[] vertices, short[] indices) {
            this.vertices = vertices;
            this.indices = indices;
        }
    }

    public static final int LEFT_EYE = 0;
    public static final int RIGHT_EYE = 1;

    /**
     * Builds the mesh for one eye.
     *
     * @param eye             {@link #LEFT_EYE} or {@link #RIGHT_EYE}
     * @param screenWidthPx   width of the whole landscape screen, in pixels
     * @param screenHeightPx  height of the screen, in pixels
     * @param metersPerPixel  physical size of one screen pixel
     * @param viewer          the headset's lens parameters
     * @param gridSize        vertices per side (at least 2); 40 keeps the linear interpolation error invisible
     */
    public static EyeMesh buildEyeMesh(int eye, int screenWidthPx, int screenHeightPx, float metersPerPixel,
                                       ViewerProfile viewer, int gridSize) {
        if (gridSize < 2 || gridSize * gridSize > 65536) {
            throw new IllegalArgumentException("gridSize out of range: " + gridSize);
        }

        float eyeWidthPx = screenWidthPx / 2f;
        float halfLensPx = viewer.interLensDistance / 2f / metersPerPixel;
        // Lens centers are placed symmetrically around the middle of the screen, at mid-height.
        float lensXPx = eye == LEFT_EYE ? eyeWidthPx - halfLensPx : halfLensPx;
        float lensYPx = screenHeightPx / 2f;
        float metersToTan = metersPerPixel / viewer.screenToLensDistance;

        float[] vertices = new float[gridSize * gridSize * EyeMesh.FLOATS_PER_VERTEX];
        int v = 0;
        for (int row = 0; row < gridSize; row++) {
            float fy = row / (float) (gridSize - 1);
            for (int col = 0; col < gridSize; col++) {
                float fx = col / (float) (gridSize - 1);

                float screenTanX = (fx * eyeWidthPx - lensXPx) * metersToTan;
                float screenTanY = (fy * screenHeightPx - lensYPx) * metersToTan;
                float r2 = screenTanX * screenTanX + screenTanY * screenTanY;
                float scale = 1f + viewer.k1 * r2 + viewer.k2 * r2 * r2; // distort(r) / r

                vertices[v++] = fx * 2f - 1f;
                vertices[v++] = fy * 2f - 1f;
                vertices[v++] = screenTanX * scale;
                vertices[v++] = screenTanY * scale;
            }
        }

        short[] indices = new short[(gridSize - 1) * (gridSize - 1) * 6];
        int i = 0;
        for (int row = 0; row < gridSize - 1; row++) {
            for (int col = 0; col < gridSize - 1; col++) {
                int a = row * gridSize + col;
                int b = a + 1;
                int c = a + gridSize;
                int d = c + 1;
                indices[i++] = (short) a;
                indices[i++] = (short) b;
                indices[i++] = (short) c;
                indices[i++] = (short) b;
                indices[i++] = (short) d;
                indices[i++] = (short) c;
            }
        }

        return new EyeMesh(vertices, indices);
    }
}
