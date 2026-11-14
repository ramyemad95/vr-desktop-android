package com.limelight.vr;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * The HDR10 static metadata the PC sends with an HDR stream (Sunshine's {@code SS_HDR_METADATA}): the mastering
 * display's color primaries and brightness range (SMPTE ST 2086) and the content's light levels (CTA-861.3).
 *
 * <p>A display uses it to fit HDR to its own brightness: without it, it has to assume how bright the picture was
 * made to go. When the VR view draws to the phone's HDR screen, these values are set on its window surface, the way
 * a video player passes them along with HDR video.</p>
 */
public final class HdrMetadata {
    /** EGL_EXT_surface_SMPTE2086_metadata and EGL_EXT_surface_CTA861_3_metadata attribute names. */
    static final int EGL_SMPTE2086_DISPLAY_PRIMARY_RX_EXT = 0x3341;
    static final int EGL_SMPTE2086_DISPLAY_PRIMARY_RY_EXT = 0x3342;
    static final int EGL_SMPTE2086_DISPLAY_PRIMARY_GX_EXT = 0x3343;
    static final int EGL_SMPTE2086_DISPLAY_PRIMARY_GY_EXT = 0x3344;
    static final int EGL_SMPTE2086_DISPLAY_PRIMARY_BX_EXT = 0x3345;
    static final int EGL_SMPTE2086_DISPLAY_PRIMARY_BY_EXT = 0x3346;
    static final int EGL_SMPTE2086_WHITE_POINT_X_EXT = 0x3347;
    static final int EGL_SMPTE2086_WHITE_POINT_Y_EXT = 0x3348;
    static final int EGL_SMPTE2086_MAX_LUMINANCE_EXT = 0x3349;
    static final int EGL_SMPTE2086_MIN_LUMINANCE_EXT = 0x334A;
    static final int EGL_CTA861_3_MAX_CONTENT_LIGHT_LEVEL_EXT = 0x3360;
    static final int EGL_CTA861_3_MAX_FRAME_AVERAGE_LEVEL_EXT = 0x3361;
    /** EGL takes every value multiplied by this (EGL_METADATA_SCALING_EXT). */
    static final int EGL_METADATA_SCALING = 50000;

    /** Chromaticities in units of 0.00002 (which is 1 / {@link #EGL_METADATA_SCALING}): red, green, blue, white; x then y. */
    public final int[] chromaticity = new int[8];
    /** The mastering display's brightest white, in nits. */
    public final int maxDisplayNits;
    /** The mastering display's black level, in units of 0.0001 nits. */
    public final int minDisplayTenThousandthNits;
    /** The brightest pixel in the content (MaxCLL) and the brightest frame average (MaxFALL), in nits; 0 if unknown. */
    public final int maxContentNits, maxFrameAverageNits;

    private HdrMetadata(ByteBuffer b) {
        for (int i = 0; i < 8; i++) {
            chromaticity[i] = b.getShort() & 0xFFFF;
        }
        maxDisplayNits = b.getShort() & 0xFFFF;
        minDisplayTenThousandthNits = b.getShort() & 0xFFFF;
        maxContentNits = b.getShort() & 0xFFFF;
        maxFrameAverageNits = b.getShort() & 0xFFFF;
    }

    /** Parses the stream's metadata bytes (little-endian 16-bit values), or returns null if there are too few. */
    public static HdrMetadata parse(byte[] bytes) {
        if (bytes == null || bytes.length < 24) {
            return null;
        }
        return new HdrMetadata(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN));
    }

    /**
     * The surface attributes to set, as {attribute, value} pairs. Values the PC left at zero (unknown) are left out,
     * so the display keeps its own assumption for them.
     */
    public int[][] eglAttributes() {
        return eglAttributes(0);
    }

    /**
     * As {@link #eglAttributes()}; with {@code peakLimitNits} above 0 the view limits its highlights to that
     * brightness itself, so that is what the display is told the picture goes up to.
     */
    public int[][] eglAttributes(int peakLimitNits) {
        int maxDisplay = maxDisplayNits;
        int maxContent = maxContentNits;
        int maxAverage = maxFrameAverageNits;
        if (peakLimitNits > 0) {
            maxDisplay = peakLimitNits;
            maxContent = peakLimitNits;
            maxAverage = maxAverage == 0 ? 0 : Math.min(maxAverage, peakLimitNits);
        }
        int[] names = {
                EGL_SMPTE2086_DISPLAY_PRIMARY_RX_EXT, EGL_SMPTE2086_DISPLAY_PRIMARY_RY_EXT,
                EGL_SMPTE2086_DISPLAY_PRIMARY_GX_EXT, EGL_SMPTE2086_DISPLAY_PRIMARY_GY_EXT,
                EGL_SMPTE2086_DISPLAY_PRIMARY_BX_EXT, EGL_SMPTE2086_DISPLAY_PRIMARY_BY_EXT,
                EGL_SMPTE2086_WHITE_POINT_X_EXT, EGL_SMPTE2086_WHITE_POINT_Y_EXT,
        };
        java.util.ArrayList<int[]> out = new java.util.ArrayList<>();
        boolean primariesKnown = true;
        for (int c : chromaticity) {
            primariesKnown &= c != 0;
        }
        if (primariesKnown) {
            for (int i = 0; i < 8; i++) {
                // Already in 1/50000 units
                out.add(new int[] {names[i], chromaticity[i]});
            }
        }
        if (maxDisplay != 0) {
            out.add(new int[] {EGL_SMPTE2086_MAX_LUMINANCE_EXT, maxDisplay * EGL_METADATA_SCALING});
            // The value is in 0.0001 nits
            out.add(new int[] {EGL_SMPTE2086_MIN_LUMINANCE_EXT, minDisplayTenThousandthNits * (EGL_METADATA_SCALING / 10000)});
        }
        if (maxContent != 0) {
            out.add(new int[] {EGL_CTA861_3_MAX_CONTENT_LIGHT_LEVEL_EXT, maxContent * EGL_METADATA_SCALING});
        }
        if (maxAverage != 0) {
            out.add(new int[] {EGL_CTA861_3_MAX_FRAME_AVERAGE_LEVEL_EXT, maxAverage * EGL_METADATA_SCALING});
        }
        return out.toArray(new int[0][]);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "mastering display %.4f to %d nits, content up to %d nits (frame average %d)",
                minDisplayTenThousandthNits / 10000.0, maxDisplayNits, maxContentNits, maxFrameAverageNits);
    }
}
