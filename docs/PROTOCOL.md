# Host ↔ client protocol (keep this file identical in both repos)

The standard Sunshine / Moonlight (GameStream) protocol, unmodified, plus two read-only endpoints served by the
VR Desktop Host app (v1, v2).

| Port   | Proto | Use                                   |
|--------|-------|---------------------------------------|
| 47984  | TCP   | HTTPS (pairing)                       |
| 47989  | TCP   | HTTP                                  |
| 47990  | TCP   | Sunshine web UI                       |
| 48010  | TCP   | RTSP                                  |
| 47998  | UDP   | Video                                 |
| 47999  | UDP   | Control                               |
| 48000  | UDP   | Audio                                 |
| 48100  | TCP   | VR Desktop Host: display mode (v1), stats (v2) |

Video settings: HEVC (Main10 offered, so HDR works), no B-frames, 60 fps. With "Resolution: match the PC" on the
phone, the stream follows the display mode below and the bitrate follows the resolution (1080p ≈ 50, 1440p ≈ 80,
4K ≈ 100 Mbps at 60 fps); otherwise the phone's own resolution and bitrate settings apply.

## v1: display mode

`GET http://<pc>:48100/vr/v1/mode` → `200`, `Content-Type: application/json`:

```json
{"width":1920,"height":1080,"refreshHz":60,"hdr":true,"seq":12}
```

- `width`, `height`: the active mode of the display Sunshine captures, in pixels. For an exclusive-fullscreen
  game this is the game's resolution; for a borderless game it stays the desktop's.
- `refreshHz`: that mode's refresh rate, rounded to an integer.
- `hdr`: Windows HDR (advanced color) is on for that display. Whether the stream itself is HDR is still signalled
  in the video stream.
- `seq`: starts at 1 when the host app starts and goes up by 1 whenever any other field changes.

`GET http://<pc>:48100/vr/v1/mode?after=<seq>` is a long poll: it answers at once if the current `seq` is greater
than `<seq>`, otherwise when the mode changes, or after 25 s with the unchanged mode.

- Any other path answers `404`. HTTP/1.1, one request per connection, no authentication (read-only, LAN only;
  the Windows Firewall rule is for the Private profile).
- Over USB the phone reaches it through `adb reverse tcp:48100 tcp:48100`, at the same address it streams from.
- The phone requests the stream at the reported `width`×`height`. During a stream it reconnects when a new mode
  has held for 3 s and differs from the stream's (about 1–2 s of black).
- Keep Sunshine's display-device options (`dd_configuration_option`, `dd_resolution_option`) disabled. If Sunshine
  set the display to the stream's mode, the phone would end up following its own request.

## v2: stats

`GET http://<pc>:48100/vr/v2/stats` → `200`, `Content-Type: application/json`: the last completed one-second
window, for the phone's frame log.

```json
{"v":2,"seq":42,"unixMs":1731420000123,"windowMs":1000,
 "display":{"width":1920,"height":1080,"refreshHz":60,"hdr":true,"vrr":false},
 "game":{"exe":"FC27.exe","presentMode":"Hardware: Independent Flip","presents":60,"presentMsAvg":16.7,"presentMsP95":17.9,"presentMsMax":33.5,"displayed":60,"displayMsMax":33.4,"dropped":0,"gpuBusyMsAvg":8.2,"lagMs":2000},
 "gpu":{"utilPercent":71,"encoderPercent":18,"clockMhz":2610,"tempC":66,"powerW":215},
 "cpu":{"totalPercent":41,"gamePercent":28,"sunshinePercent":3},
 "sunshine":{"captureRestarts":3,"lastRestartUnixMs":1731419990420}}
```

- The host computes a window once a second, also when nothing is streaming. Only `v`, `seq` and `unixMs` are
  always there: the host leaves out whatever it can't measure, and may add fields.
- `seq`: starts at 1 when the host app starts and goes up by 1 with every window. `unixMs`: the PC's wall clock
  at the end of the window. `windowMs`: the window's actual length.
- `display`: the same values as v1's mode. `vrr`: variable refresh rate is active on that display.
- `game`: the full-screen foreground process while a client is streaming, otherwise `null`. Its numbers come
  from PresentMon; without them only `exe` is there.
  - `presents`: `Present()` calls in the window. `presentMs*`: time between presents (`MsBetweenPresents`).
  - `displayed`: frames that reached the screen. `displayMsMax`: the longest time between display changes
    (`MsBetweenDisplayChange`). `dropped`: presents that were never displayed.
  - `presentMode`: PresentMon's present mode for the last frame. `gpuBusyMsAvg`: GPU time per frame.
  - `lagMs`: the game numbers are for the `windowMs` that ended `lagMs` (2000) before `unixMs`, because
    PresentMon delivers frames about a second late. `gpu` and `cpu` are for the window ending at `unixMs`.
- `gpu`: from `nvidia-smi` (NVIDIA only). `encoderPercent` is the video encoder's utilization.
- `cpu`: percent of all cores, for the whole PC, the game's process and Sunshine's.
- `sunshine`: from Sunshine's log. `captureRestarts`: how many times, since the host app started, Sunshine has
  re-created its capture and encoder while a client was connected (its "Starting async encoder teardown" line);
  each one is a second or more without video. `lastRestartUnixMs`: the log time of the latest one; left out
  until there is one. The host reads the log once a second, so a restart shows up to a second late.
- `503` during the host app's first second. A host older than v2 answers `404`.
- The phone polls once a second during a stream with a 1 s timeout, stops after a `404`, and appends the raw
  JSON with its own receive time to its frame log.
- Same rules as v1: HTTP/1.1, one request per connection, no authentication, reachable over
  `adb reverse tcp:48100 tcp:48100`. A query string is ignored.

While a client is streaming, the host also writes a per-frame PresentMon trace of the game and a once-a-second
`nvidia-smi` log next to it: `%LOCALAPPDATA%\VrDesktopHost\traces\<yyyyMMdd-HHmmss>-<exe>.csv` and
`…-<exe>.gpu.csv` (the last 10 are kept). The trace has PresentMon 2.x's columns plus a last column `UnixMs`:
the PC's wall clock when the frame was presented, on the same clock as the stats' `unixMs`.

## Change log
- v0: stock Sunshine/Moonlight.
- v1: `GET /vr/v1/mode` on TCP 48100, for "Resolution: match the PC".
- v2: `GET /vr/v2/stats` on TCP 48100, for the performance log.
