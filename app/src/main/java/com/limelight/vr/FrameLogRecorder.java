package com.limelight.vr;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import com.limelight.BuildConfig;
import com.limelight.LimeLog;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/**
 * Writes a stream's {@link FrameLog} to a gzipped file on the phone, with the phone's state once a second
 * (thermal status, CPU clocks, battery temperature, Wi-Fi link) and the PC's own stats from the VR Desktop Host app
 * (docs/PROTOCOL.md: v1 display mode, v2 stats). Summaries, stutters and events also go to logcat under
 * {@link #TAG}.
 *
 * <p>Files: {@code Android/data/<app id>/files/frame-logs/frames-<date>-<time>.log.gz}; the newest
 * {@link #KEEP_FILES} are kept, and {@code latest-summary.txt} holds the last stream's totals. See
 * docs/FRAME-LOG.md for reading them.</p>
 */
public final class FrameLogRecorder {
    public static final String TAG = "VrFrameLog";
    static final int KEEP_FILES = 15;
    private static final int MAX_QUEUED_LINES = 200_000;
    /** How often the PC's display mode and its stats are asked for, and how long a request may take. */
    private static final int MODE_POLL_MS = 10_000, STATS_POLL_MS = 1000;
    private static final int PC_CONNECT_TIMEOUT_MS = 500, PC_READ_TIMEOUT_MS = 1000;

    private final FrameLog log;
    private final File file;
    private final File dir;
    private final Context context;
    private final String host;
    private final long t0Ns;
    private final LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>(MAX_QUEUED_LINES);
    private final List<String> totals = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile boolean stopped;
    private volatile int droppedLines;
    private Thread writer, pcPoller;

    /**
     * Starts a log for a stream that's about to start.
     *
     * @param host     the PC's address, for the host app's stats, or null
     * @param bufferNs the smooth-video buffer, 0 without it
     * @param vrView   the VR view reports what it shows, so stutters can be counted
     * @return null if the file can't be created
     */
    public static FrameLogRecorder start(Context context, String host, int fps, long bufferNs, boolean vrView) {
        File dir = context.getExternalFilesDir("frame-logs");
        if (dir == null) {
            dir = new File(context.getFilesDir(), "frame-logs");
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            LimeLog.warning("Frame log: can't create " + dir);
            return null;
        }
        String name = "frames-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".log.gz";
        try {
            FrameLogRecorder recorder = new FrameLogRecorder(context, dir, new File(dir, name), host, fps, bufferNs, vrView);
            recorder.startThreads();
            return recorder;
        } catch (IOException e) {
            LimeLog.warning("Frame log: can't write " + name + ": " + e.getMessage());
            return null;
        }
    }

    private final Writer out;

    private FrameLogRecorder(Context context, File dir, File file, String host, int fps, long bufferNs, boolean vrView)
            throws IOException {
        this.context = context.getApplicationContext();
        this.dir = dir;
        this.file = file;
        this.host = host;
        // syncFlush: every flush() leaves a readable file, even if the app is killed later
        out = new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(new FileOutputStream(file), 64 * 1024, true), StandardCharsets.UTF_8), 64 * 1024);
        t0Ns = System.nanoTime();
        long unixMs = System.currentTimeMillis();
        log = new FrameLog(fps, bufferNs, vrView, t0Ns, new FrameLog.Sink() {
            @Override
            public void write(String line) {
                if (!lines.offer(line)) {
                    droppedLines++;
                }
            }

            @Override
            public void report(String line) {
                Log.i(TAG, line);
                if (line.startsWith("T,") || line.startsWith("V,")) {
                    totals.add(line);
                }
                write(line);
            }
        });
        for (String line : FORMAT_HELP) {
            lines.offer(line);
        }
        header("format", "vr-frame-log 1");
        header("start", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(new Date(unixMs)) +
                " unixMs=" + unixMs + " (phone times count from here)");
        header("app", BuildConfig.APPLICATION_ID + " " + BuildConfig.VERSION_NAME + (BuildConfig.DEBUG ? " debug" : ""));
        header("phone", Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + "), Android " +
                Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        header("stream", fps + " fps, smooth video buffer " + bufferNs / 1_000_000 + " ms, VR view " + vrView);
        // What the analyzer needs to read the log the way it was written
        header("timing", "windowMs=" + FrameLog.WINDOW_NS / 1_000_000 + " stallMs=" + FrameLog.STALL_NS / 1_000_000);
        LimeLog.info("Frame log: " + file);
    }

    private static final String[] FORMAT_HELP = {
            "# VR Desktop frame log (docs/FRAME-LOG.md). Times in microseconds: phone times from the start (H,start),",
            "# PC times from the first frame (PC clock), rx/cmp from the first frame (moonlight-common's clock).",
            "# H,key,value                 header",
            "# F,frame,I|P,bytes,pc,host(0.1ms),rx,cmp,sub,in,out,fate,rel,latch",
            "#     fate: " + FrameLog.SHOWN + " shown, " + FrameLog.OVERWRITTEN + " overwritten on the surface, " +
                    FrameLog.SUPERSEDED + " dropped (newer frame due), " + FrameLog.OVERFLOW + " dropped (pacer full),",
            "#     " + FrameLog.FLUSHED + " flushed with the decoder, " + FrameLog.UNTRACKED + " not tracked (no VR view)",
            "# R,start,draw,latched frame,held      one per VR refresh",
            "# L,t,first,last             frames lost on the network",
            "# K,t,cause[(STALL)],repeats,prev,cur,late,details   a refresh that repeated a frame, and why",
            "# S,t,summary   T,t,totals   V,t,verdict   E,t,event",
            "# Y,t,phone state            P,t,path,status,rtt,body   the PC's host app (v1 mode, v2 stats)",
    };

    /** The log to hand to the decoder and the VR view. */
    public FrameLog log() {
        return log;
    }

    /** A header line: the stream's settings, the decoder, the PC's display. Any thread. */
    public void header(String key, String value) {
        if (!lines.offer("H," + key + "," + value)) {
            droppedLines++;
        }
    }

    /** Ends the log: the totals, then the file is finished in the background. Any thread; later calls do nothing. */
    public synchronized void close() {
        if (stopped) {
            return;
        }
        log.close(System.nanoTime());
        stopped = true;
        Thread p = pcPoller;
        if (p != null) {
            p.interrupt();
        }
        // The writer drains what's queued and closes the file
        Thread w = writer;
        if (w != null) {
            w.interrupt();
        }
    }

    private void startThreads() {
        writer = new Thread(this::writeLoop, "Frame log writer");
        writer.setDaemon(true);
        writer.setPriority(Thread.MIN_PRIORITY);
        writer.start();
        if (host != null) {
            pcPoller = new Thread(this::pollPc, "Frame log PC stats");
            pcPoller.setDaemon(true);
            pcPoller.start();
        }
    }

    private void writeLoop() {
        long nextSample = SystemClock.uptimeMillis();
        List<String> batch = new ArrayList<>(512);
        try {
            while (true) {
                boolean ending = stopped;
                try {
                    String first = lines.poll(200, TimeUnit.MILLISECONDS);
                    if (first != null) {
                        batch.add(first);
                        lines.drainTo(batch);
                    }
                } catch (InterruptedException e) {
                    lines.drainTo(batch);
                    ending = true;
                }
                for (String line : batch) {
                    out.write(line);
                    out.write('\n');
                }
                batch.clear();
                long now = SystemClock.uptimeMillis();
                if (now >= nextSample && !ending) {
                    nextSample = now + 1000;
                    try {
                        log.note('Y', System.nanoTime(), samplePhone());
                    } catch (RuntimeException e) {
                        // The log must never take the stream down
                        log.note('Y', System.nanoTime(), "sample failed: " + e);
                    }
                    out.flush();
                }
                if (ending && lines.isEmpty()) {
                    break;
                }
            }
            if (droppedLines > 0) {
                out.write("E,," + droppedLines + " lines were dropped (the writer fell behind)\n");
            }
            out.close();
            writeSummary();
            LimeLog.info("Frame log: finished " + file + " (" + file.length() / 1024 + " KiB)");
        } catch (IOException e) {
            LimeLog.warning("Frame log: write failed: " + e.getMessage());
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
        prune();
    }

    private void writeSummary() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(dir, "latest-summary.txt")),
                StandardCharsets.UTF_8)) {
            w.write(file.getName() + "\n");
            for (String line : totals) {
                w.write(line + "\n");
            }
        } catch (IOException ignored) {
        }
    }

    /** Keeps the newest {@link #KEEP_FILES} logs. */
    private void prune() {
        File[] files = dir.listFiles((d, name) -> name.startsWith("frames-") && name.endsWith(".log.gz"));
        if (files == null || files.length <= KEEP_FILES) {
            return;
        }
        // The names sort by date
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        for (int i = 0; i < files.length - KEEP_FILES; i++) {
            if (!files[i].delete()) {
                LimeLog.warning("Frame log: can't delete " + files[i]);
            }
        }
    }

    // ---- The phone's state, once a second, on the writer thread

    private static final String[] CPU_FREQ_FILES = {
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq",
            "/sys/devices/system/cpu/cpu4/cpufreq/scaling_cur_freq",
            "/sys/devices/system/cpu/cpu7/cpufreq/scaling_cur_freq",
    };
    private static final String[] GPU_FREQ_FILES = {
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
    };
    private static final String GPU_BUSY_FILE = "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage";
    private final boolean[] unreadable = new boolean[CPU_FREQ_FILES.length + GPU_FREQ_FILES.length + 1];
    private int lastThermal = -1;

    private String samplePhone() {
        StringBuilder sb = new StringBuilder(160);
        sb.append("unixMs=").append(System.currentTimeMillis());
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int thermal = pm.getCurrentThermalStatus();
            sb.append(",thermal=").append(thermal);
            if (thermal != lastThermal && lastThermal >= 0) {
                log.event(System.nanoTime(), "phone thermal status " + lastThermal + " -> " + thermal +
                        " (0 none, 1 light, 2 moderate, 3 severe: throttling)");
            }
            lastThermal = thermal;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                float headroom = pm.getThermalHeadroom(10);
                if (!Float.isNaN(headroom)) {
                    // 1.0 is where the phone starts throttling hard
                    sb.append(String.format(Locale.US, ",headroom=%.2f", headroom));
                }
            }
        }
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int tenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (tenths != Integer.MIN_VALUE) {
                sb.append(String.format(Locale.US, ",battC=%.1f", tenths / 10f));
            }
        }
        sb.append(",cpuMHz=");
        for (int i = 0; i < CPU_FREQ_FILES.length; i++) {
            long khz = readLong(CPU_FREQ_FILES[i], i);
            sb.append(i > 0 ? "/" : "").append(khz > 0 ? Long.toString(khz / 1000) : "-");
        }
        for (int i = 0; i < GPU_FREQ_FILES.length; i++) {
            long hz = readLong(GPU_FREQ_FILES[i], CPU_FREQ_FILES.length + i);
            if (hz > 0) {
                sb.append(",gpuMHz=").append(hz / 1_000_000);
                break;
            }
        }
        long busy = readLong(GPU_BUSY_FILE, unreadable.length - 1);
        if (busy >= 0) {
            sb.append(",gpuBusy=").append(busy).append('%');
        }
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            @SuppressWarnings("deprecation")
            WifiInfo info = wifi.getConnectionInfo();
            if (info != null && info.getNetworkId() != -1) {
                sb.append(",wifi=").append(info.getRssi()).append("dBm/").append(info.getLinkSpeed()).append("Mbps");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    sb.append('/').append(info.getFrequency()).append("MHz");
                }
            }
        }
        return sb.toString();
    }

    /** A number from a sysfs file, or -1; a file that can't be read isn't tried again. */
    private long readLong(String path, int slot) {
        if (unreadable[slot]) {
            return -1;
        }
        try (RandomAccessFile f = new RandomAccessFile(path, "r")) {
            String line = f.readLine();
            if (line != null) {
                // "gpu_busy_percentage" reads "37 %"
                return Long.parseLong(line.trim().split("\\s+")[0]);
            }
        } catch (IOException | NumberFormatException | SecurityException ignored) {
        }
        unreadable[slot] = true;
        return -1;
    }

    // ---- The PC's host app: its display mode every MODE_POLL_MS, its v2 stats every STATS_POLL_MS

    private void pollPc() {
        boolean v2 = true;
        boolean reportedUnreachable = false;
        long nextMode = 0;
        while (!stopped) {
            long loopStart = SystemClock.uptimeMillis();
            try {
                if (loopStart >= nextMode) {
                    nextMode = loopStart + MODE_POLL_MS;
                    fetchPc(PcDisplayMode.PATH);
                }
                if (v2) {
                    PcModeClient.Response r = fetchPc(PcDisplayMode.STATS_PATH);
                    if (r.status == 404) {
                        v2 = false;
                        log.event(System.nanoTime(), "the PC's host app has no " + PcDisplayMode.STATS_PATH + " (older than v2)");
                    }
                }
                if (reportedUnreachable) {
                    reportedUnreachable = false;
                    log.event(System.nanoTime(), "the PC's host app answers again");
                }
            } catch (IOException | RuntimeException e) {
                if (!reportedUnreachable) {
                    reportedUnreachable = true;
                    log.event(System.nanoTime(), "the PC's host app doesn't answer on port " + PcDisplayMode.PORT +
                            " (" + e.getMessage() + "); no PC stats until it does");
                }
                nextMode = 0;
                sleep(PcModeClient.RETRY_DELAY_MS);
                continue;
            }
            sleep(Math.max(STATS_POLL_MS - (SystemClock.uptimeMillis() - loopStart), 50));
        }
    }

    private PcModeClient.Response fetchPc(String path) throws IOException {
        long start = System.nanoTime();
        PcModeClient.Response r = PcModeClient.get(host, path, PC_CONNECT_TIMEOUT_MS, PC_READ_TIMEOUT_MS);
        long end = System.nanoTime();
        // Stamped half-way through the request: the PC's numbers are from about then
        log.note('P', (start + end) / 2, path + "," + r.status + "," + (end - start) / 1_000_000 + "ms," +
                r.body.trim().replace('\r', ' ').replace('\n', ' '));
        return r;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            // close() interrupts; the loop checks the flag
        }
    }
}
