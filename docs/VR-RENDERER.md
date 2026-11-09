# VR renderer

Code: [`app/src/main/java/com/limelight/vr/`](../app/src/main/java/com/limelight/vr/).

## Pipeline
1. **Decode:** Moonlight's `MediaCodecDecoderRenderer`, unchanged except that it now takes a `Surface` instead of a
   `SurfaceHolder`. In VR mode, that surface belongs to a `SurfaceTexture`, so decoded frames stay on the GPU.
2. **Latch:** `VrRenderer.onDrawFrame` runs every display refresh (120 Hz on a Galaxy S22 Ultra). It latches every
   frame queued since the last draw, so the newest frame is shown, and redraws the head pose even when the desktop
   doesn't change.
3. **Draw, per eye:** one 40×40 grid mesh per eye covers half the screen. Each vertex holds the view angle the eye
   sees at that point through the lens (the lens correction is built in). For each pixel, the fragment shader:
   - turns that angle into a ray and rotates it by the head pose,
   - intersects it with the virtual screen plane (2 m away; its width is set in degrees in Settings),
   - samples the video there.

   The video is sampled once, straight from the decoder's texture. This is sharper than the usual
   "render each eye to a buffer, then distort the buffer" approach, which resamples twice.
4. **Anti-aliasing:** the desktop usually has more pixels than the eye can see (for example, 1920 desktop pixels
   across about 900 eye pixels). Decoder textures can't have mipmaps, so where the image is shrunk, the shader
   averages 4 taps inside each pixel's footprint. Where it's magnified, it takes 1 tap. The screen's edges are
   anti-aliased too.

## Lens model (`LensDistortion`, `ViewerProfile`)
This is Cardboard's viewer-profile model: `eye = screen · (1 + k1·r² + k2·r⁴)`, where both values are tangents
of the angle from the lens axis. The lens magnifies more toward its edge, so the image drawn on the phone is
barrel-shaped. Presets:

| Setting | Lens distance | Screen to lens | k1, k2 | Half FOV |
|---|---|---|---|---|
| Cardboard v2 / most plastic viewers (default) | 64 mm | 39 mm | 0.34, 0.55 | 60° |
| Cardboard v1 | 60 mm | 42 mm | 0.441, 0.156 | 40° |
| No lens correction | 64 mm | 39 mm | 0, 0 | 60° |

The lens centers sit symmetrically around the middle of the screen, at mid-height. Placing them correctly needs
the physical size of a pixel:
- **Galaxy S22 Ultra (SM-S908\*):** taken from the panel's known width, 156.4 mm. This is correct at both FHD+ and
  WQHD+.
- **Other phones:** from the reported DPI, checked against the density bucket.

**Why not the Cardboard SDK:** it isn't published as a Maven dependency (it has to be built from source with
protobuf), and it expects a QR-code viewer-pairing flow. The model above uses the same lens math, and its parameters
can be read from a viewer's Cardboard QR profile and added as a new preset later.

## Head tracking (`HeadTracker`, `HeadPose`)
- The sensor is `TYPE_GAME_ROTATION_VECTOR` (gyroscope and accelerometer), read on its own thread at the fastest
  rate. This sensor doesn't jump when the magnetometer is disturbed by the PC or a metal desk. Its heading drifts
  slowly instead, and a tap re-centers it.
- **Re-center** keeps only your heading. The screen always stays level and at eye height, even if you tap while
  looking down.
- The phone is locked to landscape in VR mode, so the display rotation is fixed.

## Integration with Moonlight (`Game.java`)
- In VR mode, `Game` adds a `VrStreamView` above the `StreamView`. The `StreamView` stays underneath and keeps
  keyboard and mouse focus. The connection starts when the VR texture's surface exists, and stops when it goes
  away, the same way Moonlight treats its own surface.
- Turned off in VR mode: HDR (the renderer is SDR), picture-in-picture, the on-screen controller, and touch input
  to the PC (a tap re-centers instead).

## Galaxy S22 Ultra notes
- **Screen resolution:** set **WQHD+** (Settings → Display → Screen resolution). Each eye then gets 1544×1440
  pixels instead of 1158×1080, which makes text much sharper.
- **Refresh rate:** set **Motion smoothness → Adaptive**. The VR view asks for the highest refresh rate (120 Hz),
  which cuts head-tracking lag compared with 60 Hz.
- **App category:** the app is marked as a productivity app, not a game, so Samsung's Game Booster/GOS doesn't
  throttle it or lower its resolution.
- **Streaming:** 1920×1080 at 60 fps, 40–50 Mbps HEVC. A higher desktop resolution adds detail the eye can't
  resolve through the lenses. On the PC, 150% Windows scaling keeps text large enough to read.
- **Viewer:** the S22 Ultra is a 6.8" phone (163 × 78 mm), so check that your viewer takes phones that size.
- **Heat:** an S22 Ultra closed inside a viewer gets warm. Lower the screen brightness (the lenses make it look
  brighter anyway). If the phone throttles, stream at 60 fps rather than 120.

## Next steps
- Head-pose prediction (extrapolate about 1 frame from the gyroscope) to cut motion-to-photon lag further.
- Viewer profiles from Cardboard QR codes, and an IPD setting.
- A gaze cursor or a mouse-pointer overlay, for using the phone without the PC's mouse.
- A curved-screen option for very wide screens.
