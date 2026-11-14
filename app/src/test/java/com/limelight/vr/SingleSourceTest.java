package com.limelight.vr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A value that has to be written in two languages (Java and a resource file, a script or a doc) can't have one
 * source, so these tests fail when the copies drift apart. Everything else is defined once and read from there.
 */
public class SingleSourceTest {
    /** Gradle runs unit tests from the module's folder (app/). */
    private static String read(String path) throws IOException {
        File file = new File(path);
        assertTrue(file.getAbsolutePath() + " not found", file.isFile());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String find(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
        assertTrue("no match for " + regex, m.find());
        return m.group(1);
    }

    private static String vrDefault(String name) throws IOException {
        return find(read("src/main/res/values/vr_defaults.xml"), "name=\"" + name + "\"[^>]*>([^<]*)<");
    }

    private static List<String> arrayValues(String name) throws IOException {
        String body = find(read("src/main/res/values/arrays.xml"), "<string-array name=\"" + name + "\"[^>]*>(.*?)</string-array>");
        List<String> values = new ArrayList<>();
        Matcher m = Pattern.compile("<item>([^<]*)</item>").matcher(body);
        while (m.find()) {
            values.add(m.group(1));
        }
        return values;
    }

    @Test
    public void standardPictureIsTheSettingsDefault() throws IOException {
        assertEquals(PictureSettings.STANDARD_WHITE_NITS, Float.parseFloat(vrDefault("default_list_vr_hdr_white")), 0f);
        assertEquals(PictureSettings.STANDARD_KNEE, Float.parseFloat(vrDefault("default_list_vr_hdr_compression")), 0f);
        assertEquals(PictureSettings.PHONE_GAMMA, Float.parseFloat(vrDefault("default_list_vr_contrast")), 0f);
        assertEquals(100f * PictureSettings.STANDARD.saturation, Float.parseFloat(vrDefault("default_seekbar_vr_color")), 0f);
    }

    @Test
    public void everyListDefaultIsOneOfItsChoices() throws IOException {
        String prefs = read("src/main/res/xml/preferences.xml");
        Matcher m = Pattern.compile("<ListPreference(.*?)/>", Pattern.DOTALL).matcher(prefs);
        int checked = 0;
        while (m.find()) {
            String pref = m.group(1);
            Matcher def = Pattern.compile("android:defaultValue=\"@string/(default_\\w+)\"").matcher(pref);
            if (!def.find()) {
                continue;
            }
            String values = find(pref, "android:entryValues=\"@array/(\\w+)\"");
            assertTrue(def.group(1) + " isn't in " + values, arrayValues(values).contains(vrDefault(def.group(1))));
            checked++;
        }
        assertTrue(checked >= 10);
    }

    @Test
    public void noVrSettingWritesItsDefaultTwice() throws IOException {
        String vr = find(read("src/main/res/xml/preferences.xml"), "android:key=\"category_vr_settings\">(.*?)</PreferenceCategory>");
        Matcher m = Pattern.compile("android:defaultValue=\"([^\"]*)\"").matcher(vr);
        while (m.find()) {
            assertTrue("a VR setting's default is written in preferences.xml instead of vr_defaults.xml: " + m.group(1),
                    m.group(1).startsWith("@"));
        }
    }

    /** "Reset VR settings to recommended" forgets the stored values, so every VR setting needs a default to fall back to. */
    @Test
    public void everyVrSettingHasADefaultToResetTo() throws IOException {
        String vr = find(read("src/main/res/xml/preferences.xml"), "android:key=\"category_vr_settings\">(.*?)</PreferenceCategory>");
        Matcher m = Pattern.compile("<[\\w.]*Preference\\b(.*?)/>", Pattern.DOTALL).matcher(vr);
        int settings = 0;
        while (m.find()) {
            String key = find(m.group(1), "android:key=\"(\\w+)\"");
            if (key.equals("reset_vr_settings")) {
                continue;
            }
            assertTrue(key + " has no default", m.group(1).contains("android:defaultValue=\"@"));
            assertNotNull(vrDefault("default_" + key));
            settings++;
        }
        assertTrue(settings >= 28);
    }

    @Test
    public void smoothVideoChoicesFitThePacer() throws IOException {
        int max = 0;
        for (String v : arrayValues("vr_smooth_video_values")) {
            max = Math.max(max, Integer.parseInt(v));
        }
        assertEquals(FramePacer.MAX_BUFFER_FRAMES, max);
    }

    @Test
    public void bitrateSliderEndsAtTheMatchedBitratesCap() throws IOException {
        String slider = find(read("src/main/res/xml/preferences.xml"), "android:key=\"seekbar_bitrate_kbps\"(.*?)/>");
        assertEquals(PcDisplayMode.MAX_BITRATE_KBPS, Integer.parseInt(find(slider, "android:max=\"(\\d+)\"")));
    }

    @Test
    public void frameLogTextsNameTheNumberOfFilesKept() throws IOException {
        String kept = "last " + FrameLogRecorder.KEEP_FILES;
        assertTrue(find(read("src/main/res/values/strings.xml"), "name=\"summary_checkbox_frame_log\">([^<]*)<").contains(kept));
        assertTrue(read("../docs/FRAME-LOG.md").contains(kept));
        assertTrue(read("../docs/SETUP-MAC.md").contains(kept));
    }

    @Test
    public void scriptsUseTheAppsNames() throws IOException {
        String ab = read("../scripts/vr-ab.sh");
        assertEquals(VrDebugTuning.PREFIX, find(ab, "\nPREFIX=(\\S+)"));
        List<String> keys = new ArrayList<>(Arrays.asList(VrDebugTuning.KEYS));
        keys.remove("tag");
        assertEquals(keys, Arrays.asList(find(ab, "\nKEYS=\"([^\"]*)\"").split(" ")));

        // The analyzer's values for logs from before the header carried them
        String analyzer = read("../scripts/analyze-frame-log.py");
        assertEquals(FrameLog.WINDOW_NS / 1000, Long.parseLong(find(analyzer, "\nWINDOW_US = ([\\d_]+)").replace("_", "")));
        assertEquals(FrameLog.STALL_NS / 1e9, Double.parseDouble(find(analyzer, "\nSTALL_S = ([\\d.]+)")), 0);
    }

    @Test
    public void protocolDocNamesThePortAndPaths() throws IOException {
        String protocol = read("../docs/PROTOCOL.md");
        assertNotNull(protocol);
        assertTrue(protocol.contains(":" + PcDisplayMode.PORT + PcDisplayMode.PATH));
        assertTrue(protocol.contains(":" + PcDisplayMode.PORT + PcDisplayMode.STATS_PATH));
    }
}
