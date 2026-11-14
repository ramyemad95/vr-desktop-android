package com.limelight.vr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The color math for drawing to the phone's HDR screen (a BT.2020 PQ surface). */
public class HdrScreenMathTest {
    @Test
    public void sdrWhiteLandsAtTheWhiteLevelAndBlackAtZero() {
        float[] white = HdrToneMap.sdrToPq(1f, 1f, 1f, 203f);
        for (float c : white) {
            assertEquals(203f, HdrToneMap.pqToNits(c), 0.5f);
        }
        float[] brighter = HdrToneMap.sdrToPq(1f, 1f, 1f, 300f);
        assertEquals(300f, HdrToneMap.pqToNits(brighter[1]), 0.5f);
        for (float c : HdrToneMap.sdrToPq(0f, 0f, 0f, 203f)) {
            assertEquals(0f, c, 1e-6f);
        }
    }

    @Test
    public void sdrGraysStayGrayAndColorsKeepTheirOrder() {
        float[] gray = HdrToneMap.sdrToPq(0.5f, 0.5f, 0.5f, 203f);
        assertEquals(gray[0], gray[1], 1e-4f);
        assertEquals(gray[1], gray[2], 1e-4f);
        // Mid-gray at gamma 2.2 is 21.8% of white
        assertEquals(203f * (float) Math.pow(0.5, 2.2), HdrToneMap.pqToNits(gray[0]), 0.3f);
        float[] red = HdrToneMap.sdrToPq(1f, 0f, 0f, 203f);
        assertTrue(red[0] > red[1] && red[0] > red[2]);
    }

    @Test
    public void saturationAtOneLeavesHdrUnchangedAndKeepsGrays() {
        float r = HdrToneMap.nitsToPq(400f), g = HdrToneMap.nitsToPq(150f), b = HdrToneMap.nitsToPq(30f);
        float[] same = HdrToneMap.saturatePq(r, g, b, 1f);
        assertEquals(r, same[0], 1e-4f);
        assertEquals(g, same[1], 1e-4f);
        assertEquals(b, same[2], 1e-4f);
        float gray = HdrToneMap.nitsToPq(100f);
        float[] g2 = HdrToneMap.saturatePq(gray, gray, gray, 1.4f);
        assertEquals(gray, g2[0], 1e-4f);
        assertEquals(gray, g2[2], 1e-4f);
    }

    @Test
    public void moreColorSpreadsTheChannels() {
        float r = HdrToneMap.nitsToPq(400f), g = HdrToneMap.nitsToPq(150f), b = HdrToneMap.nitsToPq(30f);
        float[] more = HdrToneMap.saturatePq(r, g, b, 1.3f);
        assertTrue(more[0] > r);
        assertTrue(more[2] < b);
        float[] none = HdrToneMap.saturatePq(r, g, b, 0f);
        assertEquals(none[0], none[2], 1e-4f);
    }

    @Test
    public void nativeStretchKeepsGraysAndMakesColorsMoreSaturated() {
        for (float[] m : new float[][] {HdrToneMap.BT2020_NATIVE_STRETCH, HdrToneMap.DISPLAY_P3_TO_BT2020}) {
            for (int row = 0; row < 3; row++) {
                assertEquals(1f, m[row * 3] + m[row * 3 + 1] + m[row * 3 + 2], 2e-4f);
            }
        }
        float gray = HdrToneMap.nitsToPq(120f);
        float[] g = HdrToneMap.hdrForPq(gray, gray, gray, true, 1f);
        assertEquals(gray, g[0], 1e-4f);
        assertEquals(gray, g[2], 1e-4f);
        // A green like a lit pitch: the stretch spreads the channels (more saturated), green stays the strongest
        float r = HdrToneMap.nitsToPq(70f), gr = HdrToneMap.nitsToPq(107f), b = HdrToneMap.nitsToPq(38f);
        float[] accurate = HdrToneMap.hdrForPq(r, gr, b, false, 1f);
        float[] stretched = HdrToneMap.hdrForPq(r, gr, b, true, 1f);
        assertEquals(r, accurate[0], 1e-4f);
        assertTrue(stretched[0] < accurate[0]);
        assertTrue(stretched[2] < accurate[2]);
        assertTrue(stretched[1] > stretched[0] && stretched[1] > stretched[2]);
        // SDR read as Display P3 is more saturated than read as BT.709, and white stays white
        float[] sdr709 = HdrToneMap.sdrToPq(0.3f, 0.7f, 0.2f, 203f, false);
        float[] sdrP3 = HdrToneMap.sdrToPq(0.3f, 0.7f, 0.2f, 203f, true);
        assertTrue(sdrP3[1] - sdrP3[0] > sdr709[1] - sdr709[0]);
        assertEquals(203f, HdrToneMap.pqToNits(HdrToneMap.sdrToPq(1f, 1f, 1f, 203f, true)[0]), 0.5f);
    }

    private static float nits(float pq) {
        return HdrToneMap.pqToNits(pq);
    }

    @Test
    public void highlightLimitBringsWhitesDownAndLiftsMidtones() {
        PictureSettings softer = new PictureSettings(203f, 0.75f, 1f, 2.2f, 250f);
        assertEquals(1.4f, softer.hdrGain(), 1e-3f);
        assertEquals(1f, PictureSettings.STANDARD.hdrGain(), 0f);
        // A 700-nit white comes down to under the limit; an 80-nit mid-tone gets brighter
        float white = HdrToneMap.nitsToPq(700f);
        float[] w = HdrToneMap.hdrForPq(white, white, white, false, softer);
        assertTrue("white " + nits(w[0]), nits(w[0]) <= 250f && nits(w[0]) > 200f);
        float mid = HdrToneMap.nitsToPq(80f);
        float[] m = HdrToneMap.hdrForPq(mid, mid, mid, false, softer);
        assertEquals(112f, nits(m[0]), 1.5f);
        // Brighter in still means brighter out, and nothing passes the limit
        float last = 0f;
        for (float n = 1f; n <= 10000f; n *= 1.5f) {
            float v = HdrToneMap.nitsToPq(n);
            float out = nits(HdrToneMap.hdrForPq(v, v, v, false, softer)[0]);
            assertTrue(out >= last - 0.01f);
            assertTrue(out <= 250.5f);
            last = out;
        }
        // A saturated highlight keeps its channel order (its hue) when it is rolled off
        float[] c = HdrToneMap.hdrForPq(HdrToneMap.nitsToPq(900f), HdrToneMap.nitsToPq(300f), HdrToneMap.nitsToPq(60f), false, softer);
        assertTrue(c[0] > c[1] && c[1] > c[2]);
    }

    @Test
    public void contrastDeepensDarksAndLiftsBrightsAroundThePivot() {
        PictureSettings tv = new PictureSettings(203f, 0.75f, 1f, 2.4f);
        float dark = HdrToneMap.nitsToPq(5f), pivot = HdrToneMap.nitsToPq(HdrToneMap.HDR_CONTRAST_PIVOT_NITS),
                bright = HdrToneMap.nitsToPq(200f);
        assertTrue(nits(HdrToneMap.hdrForPq(dark, dark, dark, false, tv)[0]) < 5f);
        assertEquals(HdrToneMap.HDR_CONTRAST_PIVOT_NITS, nits(HdrToneMap.hdrForPq(pivot, pivot, pivot, false, tv)[0]), 0.3f);
        assertTrue(nits(HdrToneMap.hdrForPq(bright, bright, bright, false, tv)[0]) > 200f);
        // Black stays black
        assertEquals(0f, HdrToneMap.hdrForPq(0f, 0f, 0f, false, tv)[0], 1e-6f);
    }

    @Test
    public void metadataReportsTheHighlightLimit() {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(26).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int v : new int[] {35400, 14600, 8500, 39850, 6550, 2300, 15635, 16450, 700, 0, 0, 0, 0}) {
            b.putShort((short) v);
        }
        HdrMetadata m = HdrMetadata.parse(b.array());
        int[][] full = m.eglAttributes(0);
        int[][] limited = m.eglAttributes(250);
        assertEquals(700 * 50000, full[8][1]);
        assertEquals(250 * 50000, limited[8][1]);
        // The content's brightest pixel is now known too: the limit
        assertEquals(HdrMetadata.EGL_CTA861_3_MAX_CONTENT_LIGHT_LEVEL_EXT, limited[limited.length - 1][0]);
        assertEquals(250 * 50000, limited[limited.length - 1][1]);
    }

    @Test
    public void shaderFunctionsArePlainGlsl() {
        String neutral = HdrToneMap.pqShaderFunctions(PictureSettings.STANDARD);
        assertTrue(neutral.contains("vec3 sdrToPq(vec3 c)"));
        assertTrue(neutral.contains("vec3 hdrForPq(vec3 pq) {\n    return pq;"));
        String strong = HdrToneMap.pqShaderFunctions(new PictureSettings(300f, 0.75f, 1.2f, 2.2f));
        assertTrue(strong.contains("1.200000"));
        assertTrue(strong.contains(String.valueOf(300f / 10000f)));
        String vivid = HdrToneMap.pqShaderFunctions(PictureSettings.STANDARD, true);
        assertTrue(vivid.contains(String.valueOf(HdrToneMap.BT2020_NATIVE_STRETCH[0])));
        assertTrue(vivid.contains(String.valueOf(HdrToneMap.DISPLAY_P3_TO_BT2020[0])));
        String limited = HdrToneMap.pqShaderFunctions(new PictureSettings(203f, 0.75f, 1.2f, 2.4f, 250f), true);
        assertTrue(limited.contains("lin *= 1.400000;"));
        assertTrue(limited.contains("0.012500")); // half of 250 nits, relative to 10000
        assertFalse((neutral + strong + vivid + limited).matches("(?s).*\\d[eE]-?\\d.*"));
    }
}
