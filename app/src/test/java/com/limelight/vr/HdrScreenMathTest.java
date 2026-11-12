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
        assertFalse((neutral + strong + vivid).matches("(?s).*\\d[eE]-?\\d.*"));
    }
}
