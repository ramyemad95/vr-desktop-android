package com.limelight.vr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Runs the client against a tiny local stand-in for the host app's endpoint on 127.0.0.1:48100. */
public class PcModeClientTest {
    private ServerSocket server;
    private Thread serverThread;
    private final List<String> requestLines = new CopyOnWriteArrayList<>();

    /** Serves each request with the next body; a null body means "hold the connection" (a pending long poll). */
    private void serve(String... bodies) throws IOException {
        try {
            server = new ServerSocket(PcDisplayMode.PORT, 10, InetAddress.getByName("127.0.0.1"));
        } catch (IOException e) {
            Assume.assumeNoException("port " + PcDisplayMode.PORT + " is busy on this machine", e);
        }
        serverThread = new Thread(() -> {
            for (String body : bodies) {
                try (Socket s = server.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                    requestLines.add(in.readLine());
                    String line;
                    while ((line = in.readLine()) != null && !line.isEmpty()) {
                        // headers
                    }
                    if (body == null) {
                        Thread.sleep(60_000);
                        continue;
                    }
                    OutputStream out = s.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n" + body)
                            .getBytes(StandardCharsets.US_ASCII));
                } catch (IOException | InterruptedException e) {
                    return;
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @After
    public void tearDown() throws IOException {
        if (serverThread != null) {
            serverThread.interrupt();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    public void fetchReadsTheCurrentMode() throws Exception {
        serve("{\"width\":1920,\"height\":1080,\"refreshHz\":60,\"hdr\":true,\"seq\":4}");
        PcDisplayMode m = new PcModeClient("127.0.0.1").fetch(400, 800);
        assertEquals(1920, m.width);
        assertEquals(4, m.seq);
        assertEquals("GET /vr/v1/mode HTTP/1.1", requestLines.get(0));
    }

    @Test
    public void fetchFailsFastWithoutTheHostApp() {
        // Nothing listens on the port in this test
        long start = System.nanoTime();
        assertNull(new PcModeClient("127.0.0.1").fetch(400, 800));
        assertTrue(System.nanoTime() - start < 2_000_000_000L);
    }

    @Test
    public void watcherLongPollsFromTheLastSeqAndStops() throws Exception {
        serve("{\"width\":3840,\"height\":2160,\"refreshHz\":120,\"hdr\":false,\"seq\":5}",
                "{\"width\":1920,\"height\":1080,\"refreshHz\":60,\"hdr\":true,\"seq\":6}",
                null);
        CountDownLatch two = new CountDownLatch(2);
        List<PcDisplayMode> seen = new CopyOnWriteArrayList<>();
        PcModeClient client = new PcModeClient("127.0.0.1");
        client.startWatching(4, mode -> {
            seen.add(mode);
            two.countDown();
        });
        assertTrue(two.await(5, TimeUnit.SECONDS));
        assertEquals(3840, seen.get(0).width);
        assertEquals(1920, seen.get(1).width);
        // Each poll asks for changes after the last seq it saw
        long deadline = System.currentTimeMillis() + 3000;
        while (requestLines.size() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals("GET /vr/v1/mode?after=4 HTTP/1.1", requestLines.get(0));
        assertEquals("GET /vr/v1/mode?after=5 HTTP/1.1", requestLines.get(1));
        assertEquals("GET /vr/v1/mode?after=6 HTTP/1.1", requestLines.get(2));
        // stop() ends the poll that is waiting
        client.stop();
        Thread.sleep(200);
        assertEquals(2, seen.size());
    }
}
