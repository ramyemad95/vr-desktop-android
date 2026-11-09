package com.limelight.vr;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import com.limelight.LimeLog;

/**
 * Head orientation from the phone's rotation-vector sensor.
 *
 * <p>The game rotation vector (gyroscope + accelerometer, no magnetometer) is preferred: it doesn't jump when the
 * magnetometer is disturbed by the PC, speakers or a metal desk. Its heading drifts slowly instead, which a re-center
 * fixes.</p>
 */
final class HeadTracker implements SensorEventListener {
    private final SensorManager sensorManager;
    private final Sensor sensor;
    private HandlerThread sensorThread;

    private final Object lock = new Object();
    private final float[] rotationVector = new float[4];
    private boolean hasSample;

    private final float[] deviceToWorldNatural = new float[9];

    HeadTracker(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor s = null;
        if (sensorManager != null) {
            s = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (s == null) {
                s = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            }
        }
        sensor = s;
        if (sensor == null) {
            LimeLog.warning("VR: no rotation vector sensor, head tracking is off");
        }
    }

    boolean isAvailable() {
        return sensor != null;
    }

    void start() {
        if (sensor == null || sensorThread != null) {
            return;
        }
        sensorThread = new HandlerThread("VR head tracking");
        sensorThread.start();
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST, new Handler(sensorThread.getLooper()));
    }

    void stop() {
        if (sensorThread == null) {
            return;
        }
        sensorManager.unregisterListener(this);
        sensorThread.quitSafely();
        sensorThread = null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        synchronized (lock) {
            // Some devices report 5 values; getRotationMatrixFromVector() only needs the first 4 and
            // throws on certain older Samsung firmware when given more.
            int n = Math.min(event.values.length, 4);
            System.arraycopy(event.values, 0, rotationVector, 0, n);
            if (n < 4) {
                // The scalar part is optional in the API; derive it from the unit quaternion.
                float x = rotationVector[0], y = rotationVector[1], z = rotationVector[2];
                rotationVector[3] = (float) Math.sqrt(Math.max(0, 1 - x * x - y * y - z * z));
            }
            hasSample = true;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /**
     * Writes the latest device-to-world rotation (see {@link HeadPose}) into {@code out}, accounting for the display
     * rotation so that the device frame matches what is drawn on screen.
     *
     * @return false until the first sensor sample arrives
     */
    boolean getDeviceToWorld(int displayRotation, float[] out) {
        synchronized (lock) {
            if (!hasSample) {
                return false;
            }
            SensorManager.getRotationMatrixFromVector(deviceToWorldNatural, rotationVector);
        }

        switch (displayRotation) {
            case Surface.ROTATION_90:
                SensorManager.remapCoordinateSystem(deviceToWorldNatural, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, out);
                break;
            case Surface.ROTATION_180:
                SensorManager.remapCoordinateSystem(deviceToWorldNatural, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, out);
                break;
            case Surface.ROTATION_270:
                SensorManager.remapCoordinateSystem(deviceToWorldNatural, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, out);
                break;
            default:
                System.arraycopy(deviceToWorldNatural, 0, out, 0, 9);
                break;
        }
        return true;
    }
}
