package com.limelight.vr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class HdrMetadataTest {
    /** Sunshine's SS_HDR_METADATA: 13 little-endian 16-bit values. */
    private static byte[] bytes(int... values) {
        ByteBuffer b = ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int v : values) {
            b.putShort((short) v);
        }
        return b.array();
    }

    // BT.2020 primaries and D65 white in 1/50000 units, a 1000-nit display with 0.005-nit blacks
    private static final byte[] TV = bytes(35400, 14600, 8500, 39850, 6550, 2300, 15635, 16450, 1000, 50, 800, 400, 600);

    @Test
    public void parsesTheStreamsValues() {
        HdrMetadata m = HdrMetadata.parse(TV);
        assertEquals(35400, m.chromaticity[0]);
        assertEquals(16450, m.chromaticity[7]);
        assertEquals(1000, m.maxDisplayNits);
        assertEquals(50, m.minDisplayTenThousandthNits);
        assertEquals(800, m.maxContentNits);
        assertEquals(400, m.maxFrameAverageNits);
        assertTrue(m.toString().contains("0.0050 to 1000 nits"));
    }

    @Test
    public void valuesAbove32767AreUnsigned() {
        HdrMetadata m = HdrMetadata.parse(bytes(35400, 14600, 8500, 39850, 6550, 2300, 15635, 16450, 40000, 1, 0, 0, 0));
        assertEquals(40000, m.maxDisplayNits);
        assertEquals(39850, m.chromaticity[3]);
    }

    @Test
    public void eglValuesAreScaledBy50000() {
        int[][] a = HdrMetadata.parse(TV).eglAttributes();
        assertEquals(12, a.length);
        assertEquals(HdrMetadata.EGL_SMPTE2086_DISPLAY_PRIMARY_RX_EXT, a[0][0]);
        assertEquals(35400, a[0][1]); // 0.708 * 50000
        assertEquals(HdrMetadata.EGL_SMPTE2086_MAX_LUMINANCE_EXT, a[8][0]);
        assertEquals(1000 * 50000, a[8][1]);
        assertEquals(HdrMetadata.EGL_SMPTE2086_MIN_LUMINANCE_EXT, a[9][0]);
        assertEquals(250, a[9][1]); // 0.005 nits * 50000
        assertEquals(HdrMetadata.EGL_CTA861_3_MAX_CONTENT_LIGHT_LEVEL_EXT, a[10][0]);
        assertEquals(800 * 50000, a[10][1]);
        assertEquals(400 * 50000, a[11][1]);
    }

    @Test
    public void unknownValuesAreLeftToTheDisplay() {
        // A host that reports the display but no content light levels
        int[][] a = HdrMetadata.parse(bytes(35400, 14600, 8500, 39850, 6550, 2300, 15635, 16450, 1000, 50, 0, 0, 0)).eglAttributes();
        assertEquals(10, a.length);
        // Nothing at all
        assertEquals(0, HdrMetadata.parse(new byte[26]).eglAttributes().length);
    }

    @Test
    public void tooFewBytesGiveNothing() {
        assertNull(HdrMetadata.parse(null));
        assertNull(HdrMetadata.parse(new byte[10]));
    }
}
