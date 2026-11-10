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
 * Talks to the VR Desktop Host app's display-mode endpoint (docs/PROTOCOL.md, v1) over a plain socket: one short
 * request at stream start, and a long-poll thread during the stream that reports every change.
 */
public final class PcModeClient {
    public interface Listener {
        /** Called on the watcher thread whenever the PC reports its mode (changed, or unchanged after a long poll). */
        void onPcMode(PcDisplayMode mode);
    }

    /** Responses are tiny; anything bigger isn't this endpoint. */
    private static final int MAX_RESPONSE_BYTES = 16 * 1024;
    private static final int LONG_POLL_READ_TIMEOUT_MS = 30_000;
    private static final int RETRY_DELAY_MS = 5_000;

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

    private PcDisplayMode request(long afterSeq, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        String bare = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        try (Socket socket = new Socket()) {
            activeSocket = socket;
            if (stopped) {
                throw new IOException("stopped");
            }
            socket.connect(new InetSocketAddress(bare, PcDisplayMode.PORT), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);
            String hostHeader = bare.contains(":") ? "[" + bare + "]" : bare;
            OutputStream out = socket.getOutputStream();
            out.write(PcDisplayMode.httpRequest(hostHeader, afterSeq).getBytes(StandardCharsets.US_ASCII));
            out.flush();

            InputStream in = socket.getInputStream();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0) {
                response.write(buf, 0, n);
                if (response.size() > MAX_RESPONSE_BYTES) {
                    throw new IOException("response too large");
                }
            }
            return PcDisplayMode.parseHttpResponse(response.toString("UTF-8"));
        } finally {
            activeSocket = null;
        }
    }

    private void sleepQuietly(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            // stop() interrupts the watcher; the loop checks the flag
        }
    }
}
