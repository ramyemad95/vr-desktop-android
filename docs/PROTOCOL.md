# Host ↔ client protocol (keep this file identical in both repos)

MVP: the standard Sunshine / Moonlight (GameStream) protocol, unmodified.

| Port   | Proto | Use              |
|--------|-------|------------------|
| 47984  | TCP   | HTTPS (pairing)  |
| 47989  | TCP   | HTTP             |
| 47990  | TCP   | Sunshine web UI  |
| 48010  | TCP   | RTSP             |
| 47998  | UDP   | Video            |
| 47999  | UDP   | Control          |
| 48000  | UDP   | Audio            |

Video settings: HEVC, no B-frames, 30–80 Mbps, 60 fps; the resolution matches the PC desktop.

## Change log
- v0: stock Sunshine/Moonlight.
