package com.limelight.vr;

import com.limelight.LimeLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Talks to the VR Desktop Host app (docs/PROTOCOL.md) over a plain socket. The display mode (v1): one short request
 * at stream start, and a long-poll thread during the stream that reports every change. Any other endpoint (the v2
 * stats): {@link #get}.
 */
public final class PcModeClient {
    public interface Listener {
        /** Called on the watcher thread whenever the PC reports its mode (changed, or unchanged after a long poll). */
        void onPcMode(PcDisplayMode mode);
    }

    /** Responses are tiny; anything bigger isn't this endpoint. */
    private static final int MAX_RESPONSE_BYTES = 16 * 1024;
    /** Longer than the 25 s after which the host answers a long poll with the unchanged mode. */
    private static final int LONG_POLL_READ_TIMEOUT_MS = 30_000;
    /** How long to wait before asking a host app that didn't answer again. */
    static final int RETRY_DELAY_MS = 5_000;

    private final String host;
    private volatile boolean stopped;
    private volatile Socket activeSocket;
    private Thread watcher;

    /** @param host the address the stream connects to (an IPv6 literal may be bracketed or not) */
    public PcModeClient(String host) {
        this.host = host;
    }

    /**
     * Asks for the current mode once. Blocks for at most about {@code connectTimeoutMs + readTimeoutMs}.
     *
     * @return null if the host app isn't reachable or answers anything else
     */
    public PcDisplayMode fetch(int connectTimeoutMs, int readTimeoutMs) {
        try {
            return request(0, connectTimeoutMs, readTimeoutMs);
        } catch (IOException e) {
            LimeLog.info("VR: no display mode from the PC (" + e.getMessage() + ")");
            return null;
        }
    }

    /** Starts reporting changes after {@code afterSeq} to {@code listener}, until {@link #stop()}. */
    public synchronized void startWatching(long afterSeq, Listener listener) {
        if (watcher != null) {
            return;
        }
        watcher = new Thread("VR display mode watcher") {
            @Override
            public void run() {
                long seq = afterSeq;
                while (!stopped) {
                    try {
                        PcDisplayMode mode = request(seq, 1000, LONG_POLL_READ_TIMEOUT_MS);
                        if (stopped) {
                            return;
                        }
                        if (mode == null) {
                            sleepQuietly(RETRY_DELAY_MS);
                            continue;
                        }
                        seq = Math.max(seq, mode.seq);
                        listener.onPcMode(mode);
                    } catch (IOException e) {
                        if (stopped) {
                            return;
                        }
                        sleepQuietly(RETRY_DELAY_MS);
                    }
                }
            }
        };
        watcher.setDaemon(true);
        watcher.start();
    }

    /** Any thread. Ends the watcher, including a long poll that is waiting. */
    public void stop() {
        stopped = true;
        Socket s = activeSocket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
        Thread t;
        synchronized (this) {
            t = watcher;
        }
        if (t != null) {
            t.interrupt();
        }
    }

    /** An HTTP response from the host app: its status code and body. */
    public static final class Response {
        public final int status;
        public final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }

        /** Splits a whole HTTP/1.1 response as read from the socket; null if it isn't one. */
        static Response parse(String raw) {
            if (raw == null || !raw.startsWith("HTTP/1.")) {
                return null;
            }
            int lineEnd = raw.indexOf("\r\n");
            String[] parts = (lineEnd >= 0 ? raw.substring(0, lineEnd) : raw).split(" ");
            if (parts.length < 2) {
                return null;
            }
            int status;
            try {
                status = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
            int bodyStart = raw.indexOf("\r\n\r\n");
            return new Response(status, bodyStart >= 0 ? raw.substring(bodyStart + 4) : "");
        }
    }

    /**
     * {@code GET path} on the host app's port ({@link PcDisplayMode#PORT}), once, for other endpoints than the
     * display mode (the v2 stats). Blocks for at most about {@code connectTimeoutMs + readTimeoutMs}.
     *
     * @throws IOException if the host app isn't reachable or doesn't answer HTTP
     */
    public static Response get(String host, String path, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        return exchange(null, host, path, connectTimeoutMs, readTimeoutMs);
    }

    /**
     * One request and its response. With an {@code owner}, the socket is the one its {@link #stop()} closes.
     *
     * @throws IOException if the host app isn't reachable or doesn't answer HTTP
     */
    private static Response exchange(PcModeClient owner, String host, String path, int connectTimeoutMs,
                                     int readTimeoutMs) throws IOException {
        String bare = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        try (Socket socket = new Socket()) {
            if (owner != null) {
                owner.activeSocket = socket;
                if (owner.stopped) {
                    throw new IOException("stopped");
                }
            }
            socket.connect(new InetSocketAddress(bare, PcDisplayMode.PORT), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);
            String hostHeader = bare.contains(":") ? "[" + bare + "]" : bare;
            OutputStream out = socket.getOutputStream();
            out.write(PcDisplayMode.httpGet(hostHeader, path).getBytes(StandardCharsets.US_ASCII));
            out.flush();
            Response response = Response.parse(readAll(socket.getInputStream()));
            if (response == null) {
                throw new IOException("not an HTTP response");
            }
            return response;
        } finally {
            if (owner != null) {
                owner.activeSocket = null;
            }
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int n;
        while ((n = in.read(buf)) > 0) {
            response.write(buf, 0, n);
            if (response.size() > MAX_RESPONSE_BYTES) {
                throw new IOException("response too large");
            }
        }
        return response.toString("UTF-8");
    }

    private PcDisplayMode request(long afterSeq, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        Response response = exchange(this, host, PcDisplayMode.modePath(afterSeq), connectTimeoutMs, readTimeoutMs);
        return response.status == 200 ? PcDisplayMode.parseJson(response.body) : null;
    }

    private void sleepQuietly(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            // stop() interrupts the watcher; the loop checks the flag
        }
    }
}
