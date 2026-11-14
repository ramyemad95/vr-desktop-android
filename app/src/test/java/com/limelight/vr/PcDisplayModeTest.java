package com.limelight.vr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PcDisplayModeTest {
    @Test
    public void parsesTheProtocolExample() {
        PcDisplayMode m = PcDisplayMode.parseJson("{\"width\":1920,\"height\":1080,\"refreshHz\":60,\"hdr\":true,\"seq\":12}");
        assertEquals(1920, m.width);
        assertEquals(1080, m.height);
        assertEquals(60, m.refreshHz);
        assertTrue(m.hdr);
        assertEquals(12, m.seq);
    }

    @Test
    public void toleratesOrderWhitespaceAndExtraFields() {
        PcDisplayMode m = PcDisplayMode.parseJson("{ \"seq\" : 3, \"hdr\": false,\n \"extra\": {\"a\":1},"
                + " \"height\": 2160, \"width\": 3840 }");
        assertEquals(3840, m.width);
        assertEquals(2160, m.height);
        assertFalse(m.hdr);
        assertEquals(3, m.seq);
        assertEquals(0, m.refreshHz);
    }

    @Test
    public void rejectsBodiesWithoutAResolution() {
        assertNull(PcDisplayMode.parseJson("{\"width\":1920}"));
        assertNull(PcDisplayMode.parseJson("not json"));
        assertNull(PcDisplayMode.parseJson(null));
        assertNull(PcDisplayMode.parseJson("{\"width\":0,\"height\":1080}"));
    }

    @Test
    public void parsesOnlySuccessfulHttpResponses() {
        String ok = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n"
                + "{\"width\":2560,\"height\":1440,\"refreshHz\":120,\"hdr\":false,\"seq\":7}";
        PcDisplayMode m = PcDisplayMode.parseHttpResponse(ok);
        assertEquals(2560, m.width);
        assertEquals(120, m.refreshHz);
        assertNull(PcDisplayMode.parseHttpResponse("HTTP/1.1 404 Not Found\r\n\r\n{\"width\":1,\"height\":1}"));
        assertNull(PcDisplayMode.parseHttpResponse("garbage"));
        assertNull(PcDisplayMode.parseHttpResponse(""));
    }

    @Test
    public void requestIsALongPollOnlyAfterASeq() {
        assertTrue(PcDisplayMode.httpRequest("192.168.1.142", 0).startsWith("GET /vr/v1/mode HTTP/1.1\r\n"));
        assertTrue(PcDisplayMode.httpRequest("192.168.1.142", 12).startsWith("GET /vr/v1/mode?after=12 HTTP/1.1\r\n"));
        assertTrue(PcDisplayMode.httpRequest("192.168.1.142", 12).contains("Connection: close\r\n"));
        assertTrue(PcDisplayMode.httpRequest("192.168.1.142", 12).endsWith("\r\n\r\n"));
    }

    @Test
    public void onlyDecoderFriendlyModesAreStreamable() {
        assertTrue(mode(1920, 1080).isStreamable());
        assertTrue(mode(3840, 2160).isStreamable());
        assertFalse(mode(5120, 1440).isStreamable()); // wider than the decoder setup
        assertFalse(mode(1080, 1920).isStreamable()); // portrait
        assertFalse(mode(1365, 768).isStreamable());  // odd width
        assertFalse(mode(320, 240).isStreamable());
    }

    @Test
    public void bitrateFollowsResolutionAndFrameRate() {
        assertEquals(50_000, PcDisplayMode.bitrateKbps(1920, 1080, 60));
        assertEquals(80_000, PcDisplayMode.bitrateKbps(2560, 1440, 60));
        assertEquals(100_000, PcDisplayMode.bitrateKbps(3840, 2160, 60));
        assertEquals(25_000, PcDisplayMode.bitrateKbps(1920, 1080, 30));
        assertEquals(100_000, PcDisplayMode.bitrateKbps(1920, 1080, 120));
        assertEquals(150_000, PcDisplayMode.bitrateKbps(3840, 2160, 120)); // capped
    }

    private static PcDisplayMode mode(int w, int h) {
        return new PcDisplayMode(w, h, 60, false, 1);
    }
}
