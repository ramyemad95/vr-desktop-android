package com.limelight.vr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HdrToneMapTest {
    @Test
    public void pqMatchesReferenceCodeValues() {
        // ST 2084 end points, and the code values ITU-R BT.2408 lists (58% for 203 nits, 75% for 1000 nits)
        assertEquals(0f, HdrToneMap.pqToNits(0f), 1e-6f);
        assertEquals(10000f, HdrToneMap.pqToNits(1f), 0.5f);
        assertEquals(0.58f, HdrToneMap.nitsToPq(203f), 0.005f);
        assertEquals(0.75f, HdrToneMap.nitsToPq(1000f), 0.005f);
        assertEquals(0.508f, HdrToneMap.nitsToPq(100f), 0.002f);
    }

    @Test
    public void pqRoundTrips() {
        for (float nits : new float[] {0.1f, 1f, 50f, 203f, 1000f, 4000f}) {
            assertEquals(nits, HdrToneMap.pqToNits(HdrToneMap.nitsToPq(nits)), nits * 1e-3f);
        }
    }

    private static float[] gray(float nits) {
        float pq = HdrToneMap.nitsToPq(nits);
        return HdrToneMap.toSdr(pq, pq, pq);
    }

    @Test
    public void midtonesPassThroughUnchanged() {
        // 50 nits is a quarter of SDR white, below the knee: only the BT.2020 to BT.709 matrix (rows sum to 1 for
        // gray) and gamma apply.
        float expected = (float) Math.pow(50 / HdrToneMap.SDR_WHITE_NITS, 1 / 2.2);
        float[] out = gray(50f);
        for (float c : out) {
            assertEquals(expected, c, 2e-3f);
        }
    }

    @Test
    public void highlightsRollOffBelowFullWhiteAndStayInOrder() {
        float white = gray(HdrToneMap.SDR_WHITE_NITS)[0];
        float bright = gray(1000f)[0];
        float peak = gray(10000f)[0];
        assertTrue("SDR white " + white, white > 0.9f && white < 1f);
        assertTrue(bright > white);
        assertTrue(peak >= bright);
        assertTrue(peak <= 1f);
    }

    @Test
    public void rollOffIsContinuousAndFlattens() {
        float k = HdrToneMap.KNEE;
        assertEquals(k, HdrToneMap.rollOff(k), 1e-6f);
        assertEquals(k + 1e-3f, HdrToneMap.rollOff(k + 1e-3f), 1e-5f); // slope 1 at the knee
        assertTrue(HdrToneMap.rollOff(100f) <= 1f);
        assertTrue(HdrToneMap.rollOff(2f) > HdrToneMap.rollOff(1.5f));
    }

    @Test
    public void saturatedColorKeepsItsHue() {
        // A bright BT.2020 orange: after tone mapping the channel order and ratio direction stay the same
        float[] out = HdrToneMap.toSdr(HdrToneMap.nitsToPq(800f), HdrToneMap.nitsToPq(300f), HdrToneMap.nitsToPq(20f));
        assertTrue(out[0] > out[1] && out[1] > out[2]);
        for (float c : out) {
            assertTrue(c >= 0f && c <= 1f);
        }
    }

    @Test
    public void shaderFunctionUsesTheSameConstants() {
        String glsl = HdrToneMap.shaderFunction();
        assertTrue(glsl.contains("vec3 hdrToSdr(vec3 pq)"));
        assertTrue(glsl.contains(String.valueOf(HdrToneMap.KNEE)));
        assertTrue(glsl.contains(String.valueOf(10000f / HdrToneMap.SDR_WHITE_NITS)));
        // No exponent notation, which GLSL ES 1.0 float literals would still accept but is easy to get wrong
        assertTrue(!glsl.matches("(?s).*\\d[eE]-?\\d.*"));
    }
}
