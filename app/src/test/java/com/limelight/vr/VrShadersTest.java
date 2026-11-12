package com.limelight.vr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VrShadersTest {
    private static int count(String s, String what) {
        int n = 0;
        for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + 1)) {
            n++;
        }
        return n;
    }

    @Test
    public void preprocessorBlocksAreBalancedInEveryVariant() {
        for (boolean pq : new boolean[] {false, true}) {
            for (boolean anti : new boolean[] {false, true}) {
                for (boolean stereo : new boolean[] {false, true}) {
                    for (boolean deriv : new boolean[] {false, true}) {
                        String s = VrShaders.eyeFragmentShader(pq, PictureSettings.STANDARD, true, anti, stereo, false, deriv);
                        assertEquals(count(s, "#ifdef"), count(s, "#endif"));
                    }
                }
            }
        }
    }

    @Test
    public void stereoIsOnlyCompiledInWhenAskedFor() {
        String flat = VrShaders.eyeFragmentShader(false, PictureSettings.STANDARD, false, false, false, false, true);
        String stereo = VrShaders.eyeFragmentShader(true, PictureSettings.STANDARD, false, true, true, false, true);
        assertFalse(flat.contains("#define STEREO_3D"));
        assertTrue(stereo.contains("#define STEREO_3D 1"));
        // The uniforms are declared only inside the 3D block, and every frame sample reads the eye's coordinate
        assertTrue(stereo.indexOf("#ifdef STEREO_3D\nuniform vec4 uEyeRegion;") > 0);
        assertFalse(stereo.contains("texture2D(uFrame, uv"));
        // (one normal sample, two for sharpening, four for high-quality scaling)
        assertEquals(7, count(stereo, "texture2D(uFrame, fuv"));
        // The extension directive stays ahead of any code
        assertTrue(stereo.startsWith("#extension GL_OES_standard_derivatives"));
    }

    @Test
    public void highQualityScalingNeedsDerivativesAndTakesFourSamples() {
        String hq = VrShaders.eyeFragmentShader(false, PictureSettings.STANDARD, false, false, false, true, true);
        assertTrue(hq.contains("#define HQ_SCALING 1"));
        assertTrue(hq.contains(String.valueOf(VrShaders.LOD_BIAS + VrShaders.HQ_LOD)));
        assertTrue(hq.contains(String.valueOf(VrShaders.HQ_SHARPENING)));
        // Without derivatives the four samples can't be placed: the single sample is used
        assertFalse(VrShaders.eyeFragmentShader(false, PictureSettings.STANDARD, false, false, false, true, false)
                .contains("#define HQ_SCALING"));
        assertFalse(VrShaders.eyeFragmentShader(false, PictureSettings.STANDARD, false, false, false, false, true)
                .contains("#define HQ_SCALING"));
        for (boolean anti : new boolean[] {false, true}) {
            String s = VrShaders.eyeFragmentShader(true, PictureSettings.STANDARD, true, anti, true, true, true);
            assertEquals(count(s, "#ifdef"), count(s, "#endif"));
        }
    }

    @Test
    public void antiScreenDoorIsOnlyCompiledInWhenAskedFor() {
        String off = VrShaders.eyeFragmentShader(false, PictureSettings.STANDARD, false, false, false, false, true);
        String sdr = VrShaders.eyeFragmentShader(false, PictureSettings.STANDARD, false, true, false, false, true);
        String hdr = VrShaders.eyeFragmentShader(true, PictureSettings.STANDARD, false, true, false, false, true);
        assertFalse(off.contains("#define ANTI_SCREEN_DOOR"));
        assertTrue(sdr.contains("#define ANTI_SCREEN_DOOR 1"));
        assertTrue(sdr.contains("0.7152"));
        assertTrue(hdr.contains("#define LUMA_WEIGHTS " + HdrToneMap.glslVec3(HdrToneMap.BT2020_LUMA)));
        // The extension directive stays ahead of any code
        assertTrue(hdr.startsWith("#extension GL_OES_standard_derivatives"));
    }

    @Test
    public void pictureSoftnessMovesEverySampleCoarser() {
        assertEquals(VrShaders.LOD_BIAS, VrShaders.lodBias(0f), 0f);
        assertEquals(1.1f, VrShaders.lodBias(0.8f), 1e-6f);
        assertEquals(VrShaders.lodBias(1f), VrShaders.lodBias(3f), 0f);
        // At no softness the shader is the one without the setting
        assertEquals(VrShaders.eyeFragmentShader(false, PictureSettings.STANDARD, false, true, false, true, true),
                VrShaders.eyeFragmentShader(false, PictureSettings.STANDARD, false, true, false, true, true,
                        VrShaders.lodBias(0f), VrDebugTuning.NONE));
        String soft = VrShaders.eyeFragmentShader(false, PictureSettings.STANDARD, false, true, false, true, true,
                1f, VrDebugTuning.NONE);
        // The four samples half a level finer than the bias, the wide one a level coarser
        assertEquals(4, count(soft, ", 0.5).rgb"));
        assertEquals(2, count(soft, "texture2D(uFrame, fuv, 2.0)"));
        assertFalse(soft.contains(String.valueOf(VrShaders.LOD_BIAS)));
    }
}
