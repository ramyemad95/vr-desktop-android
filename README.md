# vr-desktop-android

The Android VR client for **VR Desktop**: it receives the PC stream from
[vr-desktop-host](https://github.com/ramyemad95/vr-desktop-host) and renders it as a virtual screen in
Google Cardboard-style stereo VR.

## Approach
Fork [Moonlight Android](https://github.com/moonlight-stream/moonlight-android) (GPL-3.0, so this repo must stay
open source) and replace its 2D video surface with a VR renderer:

```
MediaCodec (low-latency HEVC) → SurfaceTexture (GL_TEXTURE_EXTERNAL_OES)
  → OpenGL ES: textured quad in 3D space, drawn once per eye
  → Cardboard SDK: lens distortion + head tracking (rotation vector)
```

Stack: Kotlin + C++/NDK. React Native won't work for this pipeline.

## Layout
| Path       | Purpose                                         |
|------------|-------------------------------------------------|
| `docs/`    | Mac setup guide, protocol notes, VR renderer design |
| `scripts/` | Helper shell scripts (fetching the Moonlight fork) |
| `app/`     | Android project (added when the Moonlight fork is imported) |

Start with [docs/SETUP-MAC.md](docs/SETUP-MAC.md).
