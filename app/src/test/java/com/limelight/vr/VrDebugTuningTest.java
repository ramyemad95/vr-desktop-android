package com.limelight.vr;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class VrDebugTuningTest {
    private static VrDebugTuning tuning(String... keysAndValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return VrDebugTuning.parse(map);
    }

    private static int count(String s, String what) {
        int n = 0;
        for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + 1)) {
            n++;
        }
        return n;
    }

    @Test
    public void nothingSetLeavesTheRealSettings() {
        VrDebugTuning t = VrDebugTuning.NONE;
        assertEquals("{}", t.toString());
        assertTrue(t.hq(true));
        assertFalse(t.hq(false));
        assertTrue(t.asd(true));
        assertFalse(t.asd(false));
        assertEquals(0.3f, t.sharpness(0.3f), 0f);
        assertEquals(0.3f, t.sharpnessUniform(0.3f), 0f);
        assertEquals(VrShaders.LOD_BIAS, t.lodBias(VrShaders.LOD_BIAS), 0f);
        assertEquals(VrShaders.HQ_LOD, t.hqLod(), 0f);
        assertEquals(VrShaders.HQ_SHARPENING, t.hqSharpening(), 0f);
        assertEquals(1f, t.hqSpread(), 0f);
        assertEquals(4f, t.anisotropy(4f), 0f);
        assertFalse(t.freezePose);
        assertFalse(t.split);
        int[] decided = {1920, 1080};
        assertArrayEquals(decided, t.frameSize(decided, 3840, 2160));
        assertEquals(t, tuning("hq", "", "asd", "  ", "sharp", "abc", "lod_bias", "NaN", "aniso", "Infinity", "frame_half", "2"));
    }

    @Test
    public void theShaderIsTheSettingsShaderWithNothingSet() {
        for (boolean pq : new boolean[] {false, true}) {
            for (boolean anti : new boolean[] {false, true}) {
                for (boolean hq : new boolean[] {false, true}) {
                    for (boolean deriv : new boolean[] {false, true}) {
                        assertEquals(VrShaders.eyeFragmentShader(pq, PictureSettings.STANDARD, true, anti, pq, hq, deriv),
                                VrShaders.eyeFragmentShader(pq, PictureSettings.STANDARD, true, anti, pq, hq, deriv,
                                        VrShaders.LOD_BIAS, tuning("tag", "x", "freeze_pose", "1", "frame_half", "0", "aniso", "8", "sharp", "40")));
                    }
                }
            }
        }
    }

    @Test
    public void valuesAreParsedAndClamped() {
        VrDebugTuning t = tuning("tag", "run 7;rm", "frame_half", "0", "hq", "0", "asd", "1", "sharp", "150",
                "hq_sharp", "10", "lod_bias", "-9", "hq_lod", " 0.5 ", "hq_spread", "2", "aniso", "64",
                "freeze_pose", "1", "split", "1");
        assertEquals("run7rm", t.tag);
        assertFalse(t.hq(true));
        assertTrue(t.asd(false));
        assertEquals(1f, t.sharpness(0f), 0f);
        // Split view: the uniform stays the left half's real setting
        assertEquals(0.2f, t.sharpnessUniform(0.2f), 0f);
        assertEquals(0.1f, t.hqSharpening(), 1e-6f);
        assertEquals(-4f, t.lodBias(VrShaders.LOD_BIAS), 0f);
        assertEquals(0.5f, t.hqLod(), 0f);
        assertEquals(2f, t.hqSpread(), 0f);
        assertEquals(16f, t.anisotropy(4f), 0f);
        assertTrue(t.freezePose);
        assertTrue(t.split);
        assertEquals("{tag=run7rm, frame_half=0, hq=0, asd=1, sharp=100.0, hq_sharp=10.0, lod_bias=-4.0, hq_lod=0.5, " +
                "hq_spread=2.0, aniso=16.0, freeze_pose=1, split=1}", t.toString());
        assertNotEquals(t, VrDebugTuning.NONE);
        assertEquals(0.4f, tuning("sharp", "40").sharpnessUniform(0f), 0f);
    }

    @Test
    public void frameHalfForcesTheFrameTexturesSize() {
        int[] halved = {1920, 1080};
        int[] whole = {3840, 2160};
        assertArrayEquals(whole, tuning("frame_half", "0").frameSize(halved, 3840, 2160));
        assertArrayEquals(halved, tuning("frame_half", "1").frameSize(whole, 3840, 2160));
        assertArrayEquals(new int[] {960, 540}, tuning("frame_half", "1").frameSize(new int[] {1920, 1080}, 1920, 1080));
        // An odd size can't be halved exactly
        assertArrayEquals(new int[] {1921, 1080}, tuning("frame_half", "1").frameSize(new int[] {1921, 1080}, 1921, 1080));
    }

    @Test
    public void theNumbersReachTheShader() {
        String s = VrShaders.eyeFragmentShader(false, PictureSettings.STANDARD, false, false, false, false, true,
                VrShaders.LOD_BIAS, tuning("hq", "1", "asd", "1", "lod_bias", "0", "hq_lod", "-0.75", "hq_sharp", "10", "hq_spread", "2"));
        assertTrue(s.contains("#define HQ_SCALING 1"));
        assertTrue(s.contains("#define ANTI_SCREEN_DOOR 1"));
        assertTrue(s.contains("fuv + 0.25 * du + 0.75 * dv, -0.75)"));
        assertTrue(s.contains("uSharpness + 0.1;"));
        assertTrue(s.contains("texture2D(uFrame, fuv, 1.0)"));
        assertFalse(s.contains("-1.5"));
        // Switched off over the real settings
        s = VrShaders.eyeFragmentShader(false, PictureSettings.STANDARD, false, true, false, true, true,
                VrShaders.LOD_BIAS, tuning("hq", "0", "asd", "0"));
        assertFalse(s.contains("#define HQ_SCALING"));
        assertFalse(s.contains("#define ANTI_SCREEN_DOOR"));
    }

    @Test
    public void splitViewHoldsBothVariants() {
        for (boolean pq : new boolean[] {false, true}) {
            for (boolean deriv : new boolean[] {false, true}) {
                String s = VrShaders.eyeFragmentShader(pq, PictureSettings.STANDARD, false, true, pq, true, deriv,
                        VrShaders.LOD_BIAS, tuning("split", "1", "hq", "0", "asd", "0", "sharp", "40", "lod_bias", "0"));
                assertEquals(count(s, "#ifdef"), count(s, "#endif"));
                assertTrue(s.contains("vec3 screen = uv.x < 0.5 ? screenA : screenB;"));
                // Left: the real settings with the uniform; right: the tuned ones with their own sharpness
                assertEquals(deriv ? 1 : 0, count(s, "#define HQ_SCALING 1"));
                assertEquals(1, count(s, "#define ANTI_SCREEN_DOOR 1"));
                assertTrue(s.indexOf("#define ANTI_SCREEN_DOOR 1") < s.indexOf("screenA = screen;"));
                assertTrue(s.contains("float sharpen = 0.4;"));
                assertTrue(s.contains("texture2D(uFrame, fuv, 0.0)"));
                assertTrue(s.contains("texture2D(uFrame, fuv, -0.5)"));
                assertTrue(s.contains("#define LUMA_WEIGHTS"));
                if (deriv) {
                    assertTrue(s.startsWith("#extension GL_OES_standard_derivatives"));
                }
            }
        }
    }

    @Test
    public void getpropOutputIsParsed() {
        Map<String, String> map = VrDebugTuning.parseGetprop(
                "[debug.vrdesktop.hq]: [0]\n[debug.vrdesktop.lod_bias]: [-0.25]\r\n[debug.vrdesktop.asd]: []\n[ro.product.model]: [SM-S908B]\n");
        assertEquals(3, map.size());
        assertEquals("0", map.get("hq"));
        assertEquals("-0.25", map.get("lod_bias"));
        assertEquals("", map.get("asd"));
        assertEquals("{hq=0, lod_bias=-0.25}", VrDebugTuning.parse(map).toString());
    }

    @Test
    public void thePollerReportsOnlyChangesAndAtMostTwiceASecond() {
        // android.os.SystemProperties doesn't exist here, and getprop's result (if any) arrives later: no change
        VrDebugTuning.Poller poller = new VrDebugTuning.Poller();
        poller.poll(0);
        assertEquals(null, poller.poll(VrDebugTuning.Poller.INTERVAL_NANOS - 1));
    }
}
