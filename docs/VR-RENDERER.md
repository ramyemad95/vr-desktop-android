# VR renderer

Code: [`app/src/main/java/com/limelight/vr/`](../app/src/main/java/com/limelight/vr/).

## Pipeline
1. **Decode:** Moonlight's `MediaCodecDecoderRenderer`, unchanged except that it now takes a `Surface` instead of a
   `SurfaceHolder`. In VR mode, that surface belongs to a `SurfaceTexture`, so decoded frames stay on the GPU.
   Qualcomm's low-latency decoders also get the maximum operating rate (`MediaCodecHelper`), which runs them at full
   clock speed: on a Galaxy S22 Ultra, a 1080p HDR frame then takes 7.1 ms in the decoder instead of 10.8 ms (the
   worst second 24 ms instead of 38 ms). The display's refresh rate made no difference (6.9 ms at 120 Hz, 7.1 ms at
   60 Hz).
2. **Latch and prefilter (once per video frame):** `VrRenderer.onDrawFrame` runs every display refresh (120 Hz on
   a Galaxy S22 Ultra) and latches every frame queued since the last draw. Only when a new frame arrived, it draws
   it 1:1 into an RGBA texture at the stream's resolution (tone-mapping HDR10 on the way, see below) and rebuilds
   that texture's mipmaps. Reading the decoder's YUV texture is the costly part of sampling, so this reads each
   video pixel once per video frame, instead of 9–13 times per display pixel per refresh as before (which pushed
   an S22 Ultra in a viewer to thermal throttling at 120 Hz with a 4K stream). A still desktop costs nothing here.
3. **Draw, per eye (every refresh):** one 40×40 grid mesh per eye covers half the screen. Each vertex holds the view
   angle the eye sees at that point through the lens (the lens correction is built in). For each pixel, the
   fragment shader:
   - turns that angle into a ray and rotates it by the head pose,
   - intersects it with the virtual screen plane (2 m away; its width is set in degrees in Settings),
   - samples the prefiltered frame there, once.

   That single sample in the final view is still sharper than the usual "render each eye to a buffer, then
   distort the buffer" approach, which resamples twice.
4. **Anti-aliasing:** the desktop usually has more pixels than the eye can see (a 4K stream on a 90–100° screen is
   2.1–2.5 desktop pixels per eye pixel). The mipmaps do the downscaling: one trilinear sample with a −0.5 level
   bias (half a level sharper than the footprint, which keeps text crisp) and anisotropic filtering up to 4× for
   the stretched footprints toward the lens's edge. Sampling happens for every pixel, in uniform control flow, so
   the hardware's level selection is defined. The screen's edges are anti-aliased too. Without NPOT mipmap
   support (GLES 2 without OES_texture_npot) the frame falls back to bilinear only.
5. **HDR:** with Moonlight's "Enable HDR" on, the PC sends HDR10 (PQ, BT.2020) whenever its display is in HDR,
   and Moonlight's `setHdrMode` tells the VR view. The view still draws to an SDR surface, so the prefilter
   tone-maps each decoded pixel (`HdrToneMap`): PQ to nits, BT.2020 to BT.709, SDR reference white at 203 nits
   (ITU-R BT.2408), a smooth roll-off above 75% of white on the brightest channel (keeps hues), then gamma 2.2.
   Without it an HDR desktop or game looks washed out. The stats panel's video line shows `HDR` while it's on.
   **Color space (HDR):** "Native" converts BT.2020 to Display P3 instead of BT.709 ("Auto", the default, converts
   to BT.709). Phones with a
   wide-gamut panel show an app's colors on the panel's own primaries when they aren't color-managing it (Samsung's
   "Vivid" screen mode, which SurfaceFlinger reports as `ColorMode::NATIVE`), and the S22 Ultra's are close to
   Display P3. So saturated colors that BT.709 would clip, such as a lit green pitch, keep their saturation, like a
   TV's "Native" color space. "Auto" converts to BT.709 (sRGB), which is correct when the phone color-manages the
   app as sRGB ("Natural" screen mode); there, "Native" looks too saturated. SDR video isn't affected.
   **HDR on the phone's screen** (VR Settings, off by default): instead of tone-mapping, the view draws to a
   10-bit window surface with `EGL_GL_COLORSPACE_BT2020_PQ_EXT` in a GLES 3 context, the window asks for
   `COLOR_MODE_HDR`, and Android composes the layer as HDR10, like an HDR video: the display does the tone mapping
   for its own brightness. HDR frames pass through the prefilter as PQ BT.2020 (with the Color setting applied in
   linear light around BT.2020 luminance) into a `GL_RGB10_A2` frame texture, since PQ in 8 bits bands. SDR frames,
   the stats panel and the surroundings are converted to PQ with their white at the HDR white level. While the
   HDR layer is up, SurfaceFlinger switches the display from `ColorMode::NATIVE` (Samsung's Vivid) to
   `DISPLAY_P3`, so colors are shown accurately and look weaker than a TV in its Native color space; Color space
   "Native" then stretches them in linear light (BT.2020 → BT.709, read as Display P3, back to BT.2020; SDR is read
   as Display P3), which is what the TV and Vivid do. If the phone
   has no 10-bit config or no BT.2020 PQ surface, the view falls back to SDR and tone-maps as above. The stream's
   HDR10 static metadata (Sunshine's `SS_HDR_METADATA`: the PC display's primaries and brightness range, and the
   content's light levels when the host has them) is set on the window surface with
   `EGL_EXT_surface_SMPTE2086_metadata` and `EGL_EXT_surface_CTA861_3_metadata` (`HdrMetadata`), as a video player
   does, so the display fits the picture to its brightness from the real values instead of assuming them.
   **Picture settings** (`PictureSettings`): the HDR white level (the nits that become the phone's white, 203 by
   default) and where the roll-off starts (60/75/90% of white) feed the tone mapper; Color (saturation around each
   pixel's luma) and Contrast (an exponent that imitates gamma 2.3 or 2.4 on the phone's 2.2) apply to every
   stream, HDR or not. All of it is baked into the prefilter shader as constants, so it runs once per video frame.
   Screen brightness in VR sets the window's brightness while streaming: in a headset the light sensor is covered and
   automatic brightness dimmed an S22 Ultra to 28%.
   Moonlight restarts the decoder each time the PC switches HDR on or off ("Trying to restart decoder after
   CodecException" in the log); that's expected.
6. **High-quality scaling** (VR Settings, off by default): the default single sample, half a mip level sharp, keeps
   detail but aliases (a 1080p stream on a 60° screen is 1.45 stream pixels per screen pixel, where it reads mip
   level 0 almost directly). With it on, the eye pass takes four samples on a rotated grid inside the pixel's
   footprint (placed with the coordinate's derivatives), each one mip level finer, and adds 0.25 of sharpening
   against the next mip level. Against a Lanczos-3 downscale in a CPU simulation, that is 3.3% RMS error instead of
   6.4% at 1.45× and 5.7% instead of 6.9% at 2.9× (4K), with 92–100% of the reference's detail. The Sharpness
   setting alone makes the default's error worse (10.4% at 60%), because it sharpens the aliasing too.
7. **Anti screen-door** (VR Settings, off by default): the S22 Ultra's diamond PenTile panel has a green subpixel
   per pixel but red and blue only on every other one, so color detail finer than two pixels aliases into a colored
   mesh and sparkle that make the pixel grid more visible through the lenses. With it on, the eye pass takes
   brightness detail from its usual sample and color from the next mip level (the sample sharpening already uses):
   `wide + luma(sharp - wide) * (1 + sharpness)`, with BT.709 or BT.2020 luminance weights. The black gaps
   between the subpixels can't be lit by software; a matte screen protector (a diffuser) or slight defocus hides
   them, and sharpening on brightness wins back detail.
8. **Sharpening:** VR Settings → Sharpness (0–100%, 30% by default) adds an unsharp mask against the next mip
   level (one more sample), to make up for the lens's and the downscale's softening.
9. **3D Mode** (`StereoMode`, VR Settings, Off by default): 3D movies and 3D game output (ReShade with
   SuperDepth3D, Geo-11) pack both eyes' pictures into one frame, squeezed side by side or one above the other
   ("half SBS" and "half TB"). With 3D Mode on, the eye pass maps each eye's screen coordinate into that eye's
   region of the frame (`offset + uv · scale`: SBS left eye `u` 0–0.5, TB left eye the top half), so each eye sees
   its own picture stretched over the whole virtual screen, and the depth comes from the content's parallax around
   the screen plane. The screen keeps the stream's aspect ratio: the PC's desktop is 16:9, so a fullscreen player
   always squeezes both pictures into one 16:9 frame, and "full" layouts never reach the phone. The coordinate is
   kept 4 frame texels away from the seam (`StereoMode.SEAM_INSET_TEXELS`), because the mip levels the eye pass
   reads (up to about level 3) would otherwise blend in the other eye's picture as a thin line on the screen's
   inner edge. The coordinate's derivatives shrink with the region, so mip level and anisotropy still follow the
   texels each pixel covers; each eye gets half the stream's pixels along the split (a 4K stream gives each eye
   1920×2160 in SBS). The prefilter and the frame texture don't change, and with 3D Mode off the shader has no 3D
   code (`STEREO_3D` isn't defined). The stats panel's video line shows `3D SBS`, `3D SBS R/L` or `3D TB`.

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
- Turned off in VR mode: picture-in-picture, the on-screen controller, and touch input to the PC (a tap
  re-centers instead). HDR stays on and is tone-mapped by the renderer.
- **Resolution: match the PC** (`PcModeClient`, `PcDisplayMode`, `ModeFollower`): before anything reads the
  stream settings, `Game.onCreate` asks the VR Desktop Host app for the display mode (`GET :48100/vr/v1/mode`,
  docs/PROTOCOL.md v1; 400 ms connect timeout, at most 1.5 s in all) and, if it's streamable (even, landscape, up
  to 4096×2304), replaces the resolution and bitrate (1080p 50, 1440p 80, 4K 100 Mbps at 60 fps, scaled by fps,
  capped at 150). Once connected, a thread long-polls `?after=<seq>`; `ModeFollower` restarts the stream when a
  new resolution has held for 3 s and isn't the stream's own (a match → menu → match cycle doesn't reconnect).
  The restart stops the connection, finishes `Game` and starts it again with the same intent; moonlight-common
  holds the new connection until the old one has stopped, and Sunshine resumes the session at the new size.
- **FC 27 mode** (`PreferenceConfiguration.vrFc27Mode`): a fixed 1920×1080 HDR stream, with the bitrate from the
  same table, read with the other preferences. It turns off "Resolution: match the PC", so `Game` neither asks the
  host for the display mode nor follows it.

## Galaxy S22 Ultra notes
- **Screen resolution:** set **WQHD+** (Settings → Display → Screen resolution). Each eye then gets 1544×1440
  pixels instead of 1158×1080, which makes text much sharper.
- **Refresh rate:** VR Settings → Refresh rate is 60 Hz by default (less heat). "Highest available" asks for 120 Hz,
  which cuts head-tracking lag; it needs **Motion smoothness → Adaptive**. With Motion smoothness on Standard,
  Samsung caps the app at 60 Hz (`frameRateOverride` in `dumpsys display`) and the display reports only 60 Hz
  modes. The refresh rate doesn't change decoding time (6.9 ms at 120 Hz, 7.1 ms at 60 Hz).
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
