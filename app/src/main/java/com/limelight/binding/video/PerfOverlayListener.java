package com.limelight.binding.video;

import com.limelight.vr.VrStats;

public interface PerfOverlayListener {
    void onPerfUpdate(final String text);

    /** Decoder thread, about once a second, only while the VR stats panel is on. */
    default void onVrStatsUpdate(VrStats stats) {
    }
}
