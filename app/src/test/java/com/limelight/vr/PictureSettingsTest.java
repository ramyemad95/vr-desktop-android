package com.limelight.vr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PictureSettingsTest {
    private static final float EPS = 1e-4f;

    @Test
    public void standardSettingsChangeNothing() {
        PictureSettings p = PictureSettings.STANDARD;
        assertTrue(p.colorIsNeutral());
        float[] out = p.adjust(0.2f, 0.5f, 0.9f);
        assertEquals(0.2f, out[0], EPS);
        assertEquals(0.5f, out[1], EPS);
        assertEquals(0.9f, out[2], EPS);
        // The HDR conversion with the standard settings is the one that was there before
        float pq = HdrToneMap.nitsToPq(400f);
        float[] a = HdrToneMap.toSdr(pq, pq * 0.9f, pq * 0.8f, false);
        float[] b = HdrToneMap.toSdr(pq, pq * 0.9f, pq * 0.8f, false, p);
        for (int i = 0; i < 3; i++) {
            assertEquals(a[i], b[i], 1e-6f);
        }
    }

    @Test
    public void colorLeavesGraysAloneAndStrengthensColors() {
        PictureSettings strong = new PictureSettings(203f, 0.75f, 1.3f, 2.2f);
        float[] gray = strong.adjust(0.4f, 0.4f, 0.4f);
        assertEquals(0.4f, gray[0], EPS);
        assertEquals(0.4f, gray[2], EPS);
        float[] green = strong.adjust(0.3f, 0.6f, 0.2f);
        assertTrue(green[1] - green[0] > 0.3f);
        assertTrue(green[1] - green[2] > 0.4f);
        for (float c : strong.adjust(0.05f, 0.95f, 0.05f)) {
            assertTrue(c >= 0f && c <= 1f);
        }
        float[] none = new PictureSettings(203f, 0.75f, 0f, 2.2f).adjust(0.3f, 0.6f, 0.2f);
        assertEquals(none[0], none[1], EPS);
    }

    @Test
    public void contrastDeepensMidtonesAndKeepsBlackAndWhite() {
        PictureSettings tv = new PictureSettings(203f, 0.75f, 1f, 2.4f);
        assertFalse(tv.colorIsNeutral());
        assertTrue(tv.adjust(0.5f, 0.5f, 0.5f)[0] < 0.5f);
        assertEquals(0f, tv.adjust(0f, 0f, 0f)[0], EPS);
        assertEquals(1f, tv.adjust(1f, 1f, 1f)[0], EPS);
    }

    @Test
    public void higherWhiteLevelDarkensMidtonesAndKeepsHighlightContrast() {
        float mid = HdrToneMap.nitsToPq(100f);
        float bright = HdrToneMap.nitsToPq(600f);
        float brighter = HdrToneMap.nitsToPq(900f);
        PictureSettings standard = PictureSettings.STANDARD;
        PictureSettings high = new PictureSettings(300f, 0.75f, 1f, 2.2f);
        assertTrue(HdrToneMap.toSdr(mid, mid, mid, false, high)[0] < HdrToneMap.toSdr(mid, mid, mid, false, standard)[0]);
        // The step from 600 to 900 nits stays more visible with the higher white level
        float stepStandard = HdrToneMap.toSdr(brighter, brighter, brighter, false, standard)[0] -
                HdrToneMap.toSdr(bright, bright, bright, false, standard)[0];
        float stepHigh = HdrToneMap.toSdr(brighter, brighter, brighter, false, high)[0] -
                HdrToneMap.toSdr(bright, bright, bright, false, high)[0];
        assertTrue("standard " + stepStandard + ", high " + stepHigh, stepHigh > stepStandard);
    }

    @Test
    public void punchierCompressionKeepsMoreOfTheUpperRange() {
        float v = HdrToneMap.nitsToPq(180f); // 0.89 of the standard white
        PictureSettings soft = new PictureSettings(203f, 0.6f, 1f, 2.2f);
        PictureSettings punchy = new PictureSettings(203f, 0.9f, 1f, 2.2f);
        assertTrue(HdrToneMap.toSdr(v, v, v, false, punchy)[0] > HdrToneMap.toSdr(v, v, v, false, soft)[0]);
        assertEquals(0.9f, HdrToneMap.rollOff(0.9f, 0.9f), 1e-6f);
        assertTrue(HdrToneMap.rollOff(50f, 0.9f) <= 1f);
    }

    @Test
    public void shadersCarryTheSettingsAsPlainLiterals() {
        PictureSettings p = new PictureSettings(300f, 0.9f, 1.25f, 2.4f);
        String hdr = HdrToneMap.shaderFunction(true, p);
        assertTrue(hdr.contains(String.valueOf(10000f / 300f)));
        assertTrue(hdr.contains("0.9"));
        String color = p.shaderFunction();
        assertTrue(color.contains("vec3 adjustPicture(vec3 c)"));
        assertTrue(color.contains("1.250000"));
        assertTrue(color.contains(PictureSettings.glsl(2.4f / 2.2f)));
        assertFalse((hdr + color).matches("(?s).*\\d[eE]-?\\d.*"));
    }

    @Test
    public void outOfRangeValuesAreClamped() {
        PictureSettings p = new PictureSettings(5f, 2f, -1f, 9f);
        assertEquals(50f, p.hdrWhiteNits, EPS);
        assertEquals(0.95f, p.hdrKnee, EPS);
        assertEquals(0f, p.saturation, EPS);
        assertEquals(2.8f, p.gamma, EPS);
    }
}
