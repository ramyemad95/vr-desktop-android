package com.limelight.vr;

/** Picks the display refresh rate the VR view asks for, from the "Refresh rate" setting. */
public final class RefreshRate {
    /** Setting value for "the highest rate the display offers". */
    public static final int HIGHEST = 0;

    private RefreshRate() {
    }

    /**
     * @param available the refresh rates the display offers at the current resolution; must not be empty
     * @param wantedHz  the rate from the setting, or {@link #HIGHEST}
     * @return the highest available rate for {@link #HIGHEST}, otherwise the available rate closest to
     * {@code wantedHz} (the higher one on a tie)
     */
    public static float choose(float[] available, int wantedHz) {
        float best = available[0];
        for (float rate : available) {
            if (wantedHz <= HIGHEST) {
                best = Math.max(best, rate);
                continue;
            }
            float diff = Math.abs(rate - wantedHz);
            float bestDiff = Math.abs(best - wantedHz);
            if (diff < bestDiff || (diff == bestDiff && rate > best)) {
                best = rate;
            }
        }
        return best;
    }

    /**
     * Whether {@code chosen} falls short of a specific rate the user asked for, which happens when the system
     * caps the app (on Samsung phones, Motion smoothness set to Standard limits every app to 60 Hz).
     */
    public static boolean isBelowWanted(float chosen, int wantedHz) {
        return wantedHz > HIGHEST && chosen < wantedHz - 1;
    }
}
