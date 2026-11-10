# Host ↔ client protocol (keep this file identical in both repos)

The standard Sunshine / Moonlight (GameStream) protocol, unmodified, plus one read-only endpoint served by the
VR Desktop Host app (v1).

| Port   | Proto | Use                                   |
|--------|-------|---------------------------------------|
| 47984  | TCP   | HTTPS (pairing)                       |
| 47989  | TCP   | HTTP                                  |
| 47990  | TCP   | Sunshine web UI                       |
| 48010  | TCP   | RTSP                                  |
| 47998  | UDP   | Video                                 |
| 47999  | UDP   | Control                               |
| 48000  | UDP   | Audio                                 |
| 48100  | TCP   | VR Desktop Host: display mode (v1)    |

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

## Change log
- v0: stock Sunshine/Moonlight.
- v1: `GET /vr/v1/mode` on TCP 48100, for "Resolution: match the PC".
