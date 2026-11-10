package com.limelight.vr;

/**
 * Rotation math for head tracking. Matrices are 3x3, row-major, stored in float[9].
 *
 * <p>Frames used:</p>
 * <ul>
 *   <li><b>Device</b>: the phone's screen frame as displayed: x right, y up, z out of the screen toward the eyes.
 *       The eyes look along -z, which is also OpenGL's eye-space convention.</li>
 *   <li><b>Android world</b>: what the rotation-vector sensor reports: x east, y north, z up.</li>
 *   <li><b>Scene</b>: where the virtual screen lives: x right, y up, -z forward. "Forward" is the direction the
 *       user faced when they last re-centered: only its heading (the screen sits level, at eye height), or with its
 *       pitch too (the screen sits where they looked, tilted to face them). Either way x stays horizontal, so the
 *       screen never leans sideways.</li>
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
        return recenterFrame(deviceToWorld, false);
    }

    /**
     * Builds the Android-world-to-scene rotation for a re-center.
     *
     * @param followPitch false keeps only the heading (the screen stays at eye height); true also keeps the pitch,
     *                    so the screen goes where the user is looking, up or down. Roll is dropped either way.
     */
    public static float[] recenterFrame(float[] deviceToWorld, boolean followPitch) {
        if (followPitch) {
            return recenterFrameWithPitch(deviceToWorld);
        }
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

    /**
     * Scene forward is the gaze, scene right is horizontal and perpendicular to it, scene up completes the frame.
     * Looking almost straight up or down, the horizontal right comes from the phone's right edge instead.
     */
    private static float[] recenterFrameWithPitch(float[] deviceToWorld) {
        // Gaze (device -z) and the phone's right edge (device +x), in world coordinates.
        float fx = -deviceToWorld[2], fy = -deviceToWorld[5], fz = -deviceToWorld[8];
        float rx, ry;
        float len = (float) Math.hypot(fx, fy);
        if (len >= 0.1f) {
            // right = forward x worldUp
            rx = fy / len;
            ry = -fx / len;
        } else {
            rx = deviceToWorld[0];
            ry = deviceToWorld[3];
            float rlen = (float) Math.hypot(rx, ry);
            if (rlen < 1e-6f) {
                return identityWorldToScene();
            }
            rx /= rlen;
            ry /= rlen;
        }
        // Make right exactly perpendicular to the gaze (it already is in the first case), keeping it horizontal
        // as far as possible.
        float dot = rx * fx + ry * fy;
        float px = rx - dot * fx, py = ry - dot * fy, pz = -dot * fz;
        float plen = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (plen < 1e-6f) {
            return identityWorldToScene();
        }
        px /= plen;
        py /= plen;
        pz /= plen;
        // up = right x forward
        float ux = py * fz - pz * fy;
        float uy = pz * fx - px * fz;
        float uz = px * fy - py * fx;
        return new float[] {
                px, py, pz,
                ux, uy, uz,
                -fx, -fy, -fz,
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
