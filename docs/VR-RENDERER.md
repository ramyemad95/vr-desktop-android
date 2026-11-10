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
   averages 4 taps inside each pixel's footprint, and a 3×3 box of 9 taps above 2 desktop pixels per eye pixel
   (a 4K stream on a 90–100° screen is 2.1–2.5). Where it's magnified, it takes 1 tap. The screen's edges are
   anti-aliased too.
5. **Sharpening:** VR Settings → Sharpness (0–100%, off by default) adds an unsharp mask at the scale of one
   phone pixel, against a 4-tap blur on the diagonals, to make up for the lens's and the downscale's softening.
   On a Galaxy S22 Ultra at 120 Hz with a 4K stream, 9 taps plus sharpening hold 120 fps with no late frames
   (GPU about 75% busy at 421 of 818 MHz).

## Lens model (`LensDistortion`, `ViewerProfile`)
This is Cardboard's viewer-profile model: `eye = screen · (1 + k1·r² + k2·r⁴)`, where both values are tangents
of the angle from the lens axis. The lens magnifies more toward its edge, so the image drawn on the phone is
barrel-shaped. Presets:

| Setting | Lens distance | Screen to lens | k1, k2 | Half FOV |
|---|---|---|---|---|
| Cardboard v2 / most plastic viewers (default) | 64 mm | 39 mm | 0.34, 0.55 | 60° |
| Cardboard v1 | 60 mm | 42 mm | 0.441, 0.156 | 40° |
| cardoO VR Go | 64 mm | 42 mm | 0.34, 0.55 | 50° |
| No lens correction | 64 mm | 39 mm | 0, 0 | 60° |

**cardoO VR Go** ([specs](https://cardoo.co/vr-specs)): 40 mm aspherical lenses, 90–100° field of view, an IPD dial
(60–70 mm) and a focus dial (37.5–46.5 mm screen to lens). The preset uses 64 mm (the user turns the IPD dial until
the two images merge, which brings the lenses to where the app draws them), the middle of the focus range, and a
half FOV from the stated field of view. cardoO doesn't publish distortion coefficients; the lens is the same class as
Cardboard v2's, so it uses those (inside a 70° screen, v1's and v2's coefficients differ by about 1% at the edge).

**Lens distance (IPD) setting:** overrides any preset's lens distance (50–75 mm), for viewers with an IPD dial
whose user needs it away from 64 mm. Each eye's image is centered on its lens, so a mismatch of 2 mm per eye
(the cardoO's dial at its 60 mm minimum against the presets' 64 mm) would need the eyes to turn about 3° outward
each to merge the images, which they can't: the picture doubles and looks out of focus. The setting also sets the
distance between the virtual eyes.

**Screen to lens distance setting:** overrides the preset's screen-to-lens distance (25–60 mm in 0.5 mm steps, since the
optical distance can differ from the dial's), for viewers with
a focus dial. The mesh turns a point on the phone into a viewing angle as offset / distance, so a mismatch scales
every angle: with the app's distance shorter than the real one, the picture moves less than the head turns and
the screen seems to follow the head; longer, it seems to move against it. Blur itself is optical and only the
dial fixes it. With "No lens correction" the lens's own distortion still grows toward the edges, so only the middle
of the view can be made perfectly still; calibrate on the middle of the screen.

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
- **Re-center** puts the screen where you're looking. With VR Settings → "Re-center up and down too" (on by
  default) it keeps your heading and pitch, so tapping while leaning back puts the screen above you, tilted to face
  you. Off, it keeps only the heading and the screen stays level at eye height. Roll is always dropped, so the
  screen's horizontal edges stay level.
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
  which cuts head-tracking lag compared with 60 Hz. With Motion smoothness on Standard, Samsung caps the app at
  60 Hz (`frameRateOverride` in `dumpsys display`) and the display reports only 60 Hz modes. VR Settings →
  Refresh rate picks a fixed rate (120, 96 or 60 Hz) instead of the highest.
- **Head tracking:** VR Settings → Head tracking off leaves the sensor stopped, so the renderer keeps its initial
  pose and the screen stays straight ahead (the same path as a phone without a rotation sensor).
- **Stats panel:** VR Settings → Stats panel. `MediaCodecDecoderRenderer` builds a `VrStats` from Moonlight's
  one-second counters (also when the 2D perf overlay is off) and Moonlight's poor-connection callback marks the
  network red. `VrStatsPanel` draws three lines into a 1024×256 bitmap; the renderer uploads it with mipmaps once
  a second and the fragment shader samples it on a 30°-wide rectangle in the screen's plane, below the screen.
  The panel's sampler stays on texture unit 1 even when the panel is off, because GLES 2 rejects draws where
  samplers of different types share a unit.
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
