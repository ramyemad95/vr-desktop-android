# Frame timing log

**Settings → VR Settings → Frame timing log** (on by default) records every video frame of a stream, from the
PC's capture to the phone's screen, and names a cause for every stutter. It's for finding out why one game
(EA SPORTS FC 27) stutters when another (Forza Horizon) doesn't: on the PC (the game, capture or encoder), on the
network, in the phone's decoder, or in the phone's pacing and drawing.

## Record and read a log
1. Play as usual. Each stream writes one file; the last 15 are kept (about 5–10 MB per hour of streaming).
2. Connect the phone over USB and, on the Mac:

   ```bash
   scripts/pull-frame-logs.sh
   ```

   It copies the logs to `frame-logs/` (ignored by git) and prints a report on the newest one. Add `--all` to list
   every stutter, or `--csv seconds.csv` for a per-second table. Another log:
   `scripts/analyze-frame-log.py frame-logs/frames-20261112-210000.log.gz`.
3. Live, during a stream: `adb logcat -s VrFrameLog` prints a one-line summary each second, the stutters (up to
   5 a second) and events.

The files are on the phone in `Android/data/io.github.ramyemad95.vrdesktop.debug/files/frame-logs/`
(`latest-summary.txt` holds the last stream's totals).

## What the report says
- **VERDICT**: the share of refreshes that repeated a frame (each one is a visible stutter), the most common cause,
  and the stalls.
- **Stutter** (a repeat shorter than 0.25 s), by cause:

  | Cause | Meaning | Where to look |
  |-------|---------|---------------|
  | `PC_GAP` | The PC sent the next frame late or not at all, with no frame lost: the game didn't present one in time, or capture missed it | PC: the game's frame pacing (PresentMon trace), its frame-rate cap and vsync, the display's refresh |
  | `PC_ENCODE` | Sunshine took longer than usual to capture and encode the frame | PC: encoder preset, two-pass, GPU load, HDR conversion |
  | `NETWORK` | The frame's first packet arrived late | Wi-Fi, USB tethering, router |
  | `TRANSFER` | The frame took long to arrive in full (a big frame such as an IDR, or packets lost and recovered) | Bitrate, Wi-Fi |
  | `NET_LOSS` | The frame was lost on the network | Wi-Fi, bitrate |
  | `PHONE_QUEUE` | The frame waited to get into the phone's decoder | The decoder is behind |
  | `PHONE_DECODE` | The phone's decoder took longer than usual | Resolution, heat (thermal status in the report) |
  | `DECODER_RESET` | The decoder was flushed or restarted | Decoder errors, IDR requests |
  | `GL_STALL` | The phone's renderer missed a refresh | GPU load (High-quality scaling, 120 Hz, HDR screen), heat |
  | `PACER` | The frame was on time but shown a refresh late | Smooth video's schedule |

- **Frozen video** (no frame for 0.25 s or more), with whether the PC sent nothing in that time (Sunshine
  restarting capture or the encoder, for instance on a display-mode change) or frames were held up on the way.
  With the host app running, each stall is matched to Sunshine's capture restarts (v2 `sunshine.lastRestartUnixMs`),
  and restarts that froze nothing on the phone are listed too.
- **Per frame, by stage**: p50/p95/p99/max of each stage. Stages measured across two clocks ("later than fastest")
  are against the fastest frame of the last 2 s, which cancels the clock offsets.
- **The PC's frame spacing**: the capture timestamps' spacing. At 60 fps nearly all should be 14–19.5 ms; anything
  in 25–40 ms is a frame the PC didn't capture.
- **The PC**: the display mode over time and, with the VR Desktop Host app running, the game's own presents
  (PresentMon), the GPU and its video encoder (docs/PROTOCOL.md, v2).
- **The phone**: thermal status, battery temperature, CPU clocks, the Wi-Fi link.
- **The 10 worst seconds**, with the game's numbers on the PC for the same second.

All times are printed as the phone's wall clock, so they line up with what you saw and with the PC's logs (Sunshine's
log, and the host app's PresentMon traces in `%LOCALAPPDATA%\VrDesktopHost\traces\`, whose `UnixMs` column is the
PC's wall clock). Both clocks follow internet time, usually within a few tens of milliseconds.

## How it works
`FrameLog` (pure Java, unit-tested in `FrameLogTest`) gets a call at each stage of each frame:
`MediaCodecDecoderRenderer` when a frame goes into the decoder, is queued, comes out, and is sent to the surface or
dropped; `VrRenderer` when it latches a frame and when a draw ends. A frame's PC capture time is its RTP timestamp,
its PC processing time is Sunshine's per-frame host latency, and its arrival times come from moonlight-common.
When a frame is latched more refreshes after the previous one than the frame rate allows, the next frame was late,
and the stage that added the most delay against the fastest frame of the last 2 s is blamed (see
`FrameLog.attribute`). `FrameLogRecorder` writes the lines to the file on a background thread, adds the phone's
state once a second, and polls the host app (`/vr/v1/mode` every 10 s, `/vr/v2/stats` every second).

## File format
A gzip text file, one line per record, flushed every second so a log cut short is still readable. The file starts
with this legend. Times are microseconds: phone times from the start (`H,start` has its wall clock), PC times from
the first frame on the PC's clock, `rx`/`cmp` from the first frame on moonlight-common's clock.

```
H,key,value                                     header: start, app, phone, stream, settings, display
F,frame,I|P,bytes,pc,host(0.1ms),rx,cmp,sub,in,out,fate,rel,latch
    fate: S shown, W overwritten on the surface, D dropped (newer frame due), O dropped (pacer full),
    X flushed with the decoder, N not tracked (no VR view)
R,start,draw,latched frame,held                 one per VR refresh
L,t,first,last                                  frames lost on the network
K,t,cause[(STALL)],repeats,prev,cur,late,pcGap=..,late=..,host=..,net=..,xfer=..,queue=..,decode=..,drawGap=..,fate=..
S,t,summary   T,t,totals   V,t,verdict   E,t,event
Y,t,phone state                                 unixMs, thermal, headroom, battC, cpuMHz, gpuMHz, wifi
P,t,path,status,rtt,body                        the PC's host app: v1 mode, v2 stats (raw JSON)
```
