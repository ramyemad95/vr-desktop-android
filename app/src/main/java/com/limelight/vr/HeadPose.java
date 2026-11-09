package com.limelight.vr;

/**
 * Rotation math for head tracking. Matrices are 3x3, row-major, stored in float[9].
 *
 * <p>Frames used:</p>
 * <ul>
 *   <li><b>Device</b>: the phone's screen frame as displayed: x right, y up, z out of the screen toward the eyes.
 *       The eyes look along -z, which is also OpenGL's eye-space convention.</li>
 *   <li><b>Android world</b>: what the rotation-vector sensor reports: x east, y north, z up.</li>
 *   <li><b>Scene</b>: where the virtual screen lives: x right, y up, -z forward. "Forward" is the horizontal
 *       direction the user faced when they last re-centered, so the screen always sits level, at eye height.</li>
 * </ul>
 */
public final class HeadPose {
    private HeadPose() {
    }

    public static float[] identity() {
        return new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1};
    }

    /**
     * Builds the Android-world-to-scene rotation for a re-center, from the current device-to-world rotation.
     * Only the heading is kept: pitch and roll at the moment of re-centering don't tilt the scene.
     */
    public static float[] recenterFrame(float[] deviceToWorld) {
        // The direction the eyes look (device -z), in world coordinates.
        float fx = -deviceToWorld[2];
        float fy = -deviceToWorld[5];
        float fz = -deviceToWorld[8];
        float len = (float) Math.hypot(fx, fy);
        if (len < 0.1f) {
            // Looking almost straight up or down: the heading comes from the top edge of the screen instead.
            // Looking down, the top edge points forward; looking up, it points backward.
            float sign = fz < 0 ? 1f : -1f;
            fx = sign * deviceToWorld[1];
            fy = sign * deviceToWorld[4];
            len = (float) Math.hypot(fx, fy);
            if (len < 1e-6f) {
                return identityWorldToScene();
            }
        }
        fx /= len;
        fy /= len;

        // Rows are the scene axes expressed in world coordinates:
        // right = forward x up = (fy, -fx, 0), up = (0, 0, 1), back = -forward.
        return new float[] {
                fy, -fx, 0,
                0, 0, 1,
                -fx, -fy, 0,
        };
    }

    /** World-to-scene rotation for a user facing north. */
    private static float[] identityWorldToScene() {
        return new float[] {
                1, 0, 0,
                0, 0, 1,
                0, -1, 0,
        };
    }

    public static float[] multiply(float[] a, float[] b) {
        float[] out = new float[9];
        multiply(a, b, out);
        return out;
    }

    /** out = a * b. {@code out} must not alias {@code a} or {@code b}. */
    public static void multiply(float[] a, float[] b, float[] out) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
            }
        }
    }

    /** out = m * (x, y, z). */
    public static void transform(float[] m, float x, float y, float z, float[] out) {
        out[0] = m[0] * x + m[1] * y + m[2] * z;
        out[1] = m[3] * x + m[4] * y + m[5] * z;
        out[2] = m[6] * x + m[7] * y + m[8] * z;
    }

    /** Copies a row-major matrix into column-major order, as glUniformMatrix3fv expects. */
    public static void toColumnMajor(float[] m, float[] out) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                out[c * 3 + r] = m[r * 3 + c];
            }
        }
    }
}
