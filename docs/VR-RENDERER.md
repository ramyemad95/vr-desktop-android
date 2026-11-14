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
   With **Halve 4K frames** on (VR Settings, off by default), a stream much larger than an eye's part of the screen
   is drawn at half size (`VrConfig.frameTargetSize`: a 4K stream on the S22 Ultra's 1544-pixel eyes becomes
   1920×1080, still more than the virtual screen covers, see "How many phone pixels the screen covers" below; one
   bilinear sample averages each 2×2 block). Measured at 4K HDR with high-quality scaling, the full-size frame cost
   about three quarters of the GPU's work: 75% busy at 421 MHz, and 99% with the VR view down to 51 fps once heat
   capped the GPU at 220 MHz, where a 1080p stream needed 63%. 3D modes keep the full size.
3. **Draw, per eye (every refresh):** one 40×40 grid mesh per eye covers half the screen. Each vertex holds the view
   angle the eye sees at that point through the lens (the lens correction is built in). For each pixel, the
   fragment shader:
   - turns that angle into a ray and rotates it by the head pose,
   - intersects it with the virtual screen plane (2 m away; its width is set in degrees in Settings),
   - samples the prefiltered frame there, once.

   That single sample in the final view is still sharper than the usual "render each eye to a buffer, then
   distort the buffer" approach, which resamples twice.
4. **Anti-aliasing:** the desktop usually has more pixels than the eye can see (a 4K stream on a 90–100° screen is
   2.1–2.5 desktop pixels per eye pixel). The mipmaps do the downscaling: one trilinear sample with a level bias
   set by Picture softness (`VrShaders.lodBias`: −0.5 at 0%, half a level sharper than the footprint, which keeps
   text crisp; +1.1 at the default 80%) and anisotropic filtering up to 4× for the stretched footprints toward the
   lens's edge. Sampling happens for every pixel, in uniform control flow, so
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
   On the HDR screen the picture settings work in linear light (`HdrToneMap.hdrForPq`): Contrast raises each
   pixel's luminance to `gamma / 2.2` around 18% of the standard white, Color scales saturation around BT.2020
   luminance, and **HDR highlights** rolls the brightest channel off toward a limit (400, 250 or 150 nits) from
   half of it, after a gain of up to 1.4 on everything, and reports that limit as the metadata's maximum. Whites at
   several hundred nits glare through a viewer's lenses and wash out the rest of the picture.
   **Picture settings** (`PictureSettings`): the HDR white level (the nits that become the phone's white, 203 by
   default) and where the roll-off starts (60/75/90% of white) feed the tone mapper; Color (saturation around each
   pixel's luma) and Contrast (an exponent that imitates gamma 2.3 or 2.4 on the phone's 2.2) apply to every
   stream, HDR or not. All of it is baked into the prefilter shader as constants, so it runs once per video frame.
   Screen brightness in VR sets the window's brightness while streaming: in a headset the light sensor is covered and
   automatic brightness dimmed an S22 Ultra to 28%.
   Moonlight restarts the decoder each time the PC switches HDR on or off ("Trying to restart decoder after
   CodecException" in the log); that's expected.
6. **High-quality scaling** (VR Settings, off by default): a single sample keeps detail but aliases when the
   stream has only a little more than one pixel per screen pixel, where it reads mip level 0 almost directly. With
   it on, the eye pass takes four samples on a rotated grid inside the pixel's footprint (placed with the
   coordinate's derivatives), each half a mip level finer than the single sample's (`VrShaders.HQ_LOD`), and adds
   no sharpening of its own (`HQ_SHARPENING` is 0; the Sharpness setting applies as usual). Both values were picked
   side by side in the headset on 12 Nov 2026 and haven't been measured since. The first tuning (a whole level
   finer, 0.25 of sharpening) was measured against a Lanczos-3 downscale in a CPU simulation: 3.3% RMS error
   instead of the single sample's 6.4% at 1.45 stream pixels per screen pixel and 5.7% instead of 6.9% at 2.9, with
   92–100% of the reference's detail. The Sharpness setting alone made the single sample's error worse (10.4% at
   60%), because it sharpens the aliasing too.
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
| cardoO VR Go | 64 mm | 45 mm | 0.34, 0.55 | 50° |
| No lens correction | 64 mm | 39 mm | 0, 0 | 60° |

These are the viewers' own values. The app's defaults then override two of them for every viewer: Lens distance
50 mm, picked by eye, and Screen to lens distance 50 mm, the cardoO VR Go's focus dial at its farthest
(`res/values/vr_defaults.xml`); "The viewer's own" in either setting
brings the table's value back.

**cardoO VR Go** ([specs](https://cardoo.co/vr-specs)): 40 mm aspherical lenses, 90–100° field of view, an IPD dial
and a focus dial. Measured on the viewer with a ruler: the lenses' edges are 23 to 37 mm apart, so their centers 63 to
77 mm, and the lenses sit 40 to 50 mm from the phone. The preset uses 64 mm (the user turns the IPD dial until
the two images merge, which brings the lenses to where the app draws them), the middle of the focus range, and a
half FOV from the stated field of view. cardoO doesn't publish distortion coefficients; the lens is the same class as
Cardboard v2's, so it uses those (inside a 70° screen, v1's and v2's coefficients differ by about 1% at the edge).

**Lens distance (IPD) setting:** overrides any preset's lens distance (50–77 mm), for viewers with an IPD dial
whose user needs it away from 64 mm. Each eye's image is centered on its lens, so a mismatch of 2 mm per eye
(a dial at 60 mm against the presets' 64 mm) would need the eyes to turn about 3° outward
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
- **FC 27 mode** (`PreferenceConfiguration.vrFc27Mode`, off by default): a fixed 1920×1080 HDR stream, with the bitrate from the
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
- **Streaming:** HEVC at 60 fps. With the defaults, the PC display's own resolution, with the bitrate from the
  table under "Resolution: match the PC" above; with FC 27 mode on, 1920×1080 HDR at 50 Mbps. How
  much of the stream's detail reaches the eye is under "How many phone pixels the screen covers" below. On the PC,
  150% Windows scaling keeps text large enough to read.
- **Viewer:** the S22 Ultra is a 6.8" phone (163 × 78 mm), so check that your viewer takes phones that size.
- **Heat:** an S22 Ultra closed inside a viewer gets warm. Lower the screen brightness (the lenses make it look
  brighter anyway). If the phone throttles, stream at 60 fps rather than 120.

## How many phone pixels the screen covers
The virtual screen's width on the phone, per eye, follows from the lens model: `2 · undistort(tan(width / 2)) ·
screen-to-lens distance / pixel size`. On the S22 Ultra at WQHD+ (each eye 1544 pixels wide):

| Screen to lens distance | 60° screen | 75° screen | 90° screen |
|---|---|---|---|
| 39 mm (Cardboard v2's own) | 789 px | 968 px | 1,140 px |
| 50 mm (the setting's default) | 1,011 px | 1,241 px | 1,461 px |
| 60 mm (the default until 12 Nov 2026) | 1,214 px | 1,490 px | 1,753 px |

So a 1080p stream has 1.9 pixels per screen pixel on the default 60° screen (2.4 at 39 mm, 1.6 at 60 mm) and a 4K
stream 3.8 (4.9 at 39 mm, 3.2 at 60 mm). Screenshots on 12 Nov 2026 measured about 790 at 60° and 970 at 75°: the phone's Screen to lens
distance was at the viewer's own 39 mm then.

## Picture softness, Halve 4K frames, Ultimate High Quality (12 Nov 2026)

Tuned side by side in the headset with the debug split view (below), on a native 4K desktop stream at a 75° screen:

- **Picture softness** (`seekbar_vr_softness`, default 80%) adds 0 to 2 mip levels to the frame's bias
  (`VrShaders.lodBias`: −0.5 at 0%, +1.1 at 80%). The user preferred +1.1 over −0.5, +0.3 and +0.8, and found +1.4
  too blurry.
- High-quality scaling no longer adds sharpening (`HQ_SHARPENING` 0.25 → 0), and its four samples are half a mip
  level finer than the bias instead of a whole one (`HQ_LOD` −1 → −0.5).
- **Halve 4K frames** (`checkbox_vr_halve_frame`, default off) is the halving of `VrConfig.frameTargetSize`, now a
  choice. Screenshots at 80% softness: halved and full frames differ by 0.34/255 on average; at 0% softness the
  full frame is sharper (Laplacian variance 2354 against 1867).
- **Ultimate High Quality** (`checkbox_vr_ultimate_quality`, default off) forces High-quality scaling and Anti
  screen-door on and the halving off (`PreferenceConfiguration`).

## Debug tuning: A/B picture tests over adb (debug builds)
A debug build's renderer reads the system properties `debug.vrdesktop.*` about twice a second on the GL thread
(`VrDebugTuning`, only when `BuildConfig.DEBUG`; a release build never looks) and applies a change while the
stream keeps running: no reinstall, no reconnect. An unset or empty property leaves the real setting alone.

| Property (`debug.vrdesktop.` + ) | Values | Effect |
|---|---|---|
| `frame_half` | 0 / 1 | Frame texture at the stream's size / halved, instead of what `VrConfig.frameTargetSize` decides. The texture and its framebuffer are recreated and the last frame is prefiltered again. |
| `hq` | 0 / 1 | High-quality scaling off / on |
| `asd` | 0 / 1 | Anti screen-door off / on |
| `sharp` | 0–100 | Sharpness, percent |
| `hq_sharp` | 0–100 | Sharpening built into high-quality scaling, percent (unset: `HQ_SHARPENING`, 0) |
| `lod_bias` | −4–4 | Mip level bias (unset: what Picture softness gives, `VrShaders.lodBias`); the sharpening sample stays one level above it |
| `hq_lod` | −4–4 | Extra level offset of the four high-quality samples (unset: `HQ_LOD`, −0.5) |
| `hq_spread` | 0–4 | Multiplier on the four samples' offsets (unset: `HQ_SPREAD`, 1) |
| `aniso` | 1–16 | Maximum anisotropy (unset: `MAX_ANISOTROPY`, 4), capped at the GPU's |
| `freeze_pose` | 1 | Ignore head tracking and look straight at the screen's centre, so screenshots repeat with the phone lying on a desk |
| `split` | 1 | The real settings on the screen's left half, the properties' on its right half. Shader settings only (`hq`, `asd`, `sharp`, `hq_sharp`, `lod_bias`, `hq_lod`, `hq_spread`): `frame_half` and `aniso` belong to the one frame texture and apply to both halves. Costs both variants' samples for every pixel. |
| `tag` | text | Only echoed in the log line. Scripts set it last (the app reads it first), so a line with the tag carries that tag's values. |

Each change logs one line, with what was understood (numbers clamped, invalid values dropped) and what it did:

    VR: debug tuning {tag=20261112-180000-3, frame_half=0, freeze_pose=1}: frame 3840x2160, anisotropy up to 4.0, frame texture recreated

The eye program is rebuilt only when its source changes (`, shader rebuilt`); if it doesn't compile, the previous
one stays and the line says `shader FAILED`. By hand: `adb shell setprop debug.vrdesktop.hq 0`, and
`adb shell setprop debug.vrdesktop.hq ''` to clear. The properties last until the phone restarts.

- `scripts/vr-ab.sh [variants.txt | -]` takes a screenshot per variant into `frame-logs/ab/<timestamp>/<name>.png`
  (plus `variants.txt`: name, properties, the app's log line), waits for each variant's log line before the
  screenshot, clears every property on exit, then runs the analyzer. Without an argument it runs a default list:
  the current settings, `frame_half` 0 and 1, `hq=0`, `asd=0`, `hq_sharp=25`, `lod_bias=0`, `hq_lod=0`, `aniso=1`
  and some combinations. A variants file has one `name: key=value key=value` per line. `BASE` (default
  `freeze_pose=1`) is added to every variant; `SETTLE`, `TIMEOUT`, `OUT`, `ANALYZE=0` and `ADB` are in the
  script's header. Show a still picture on the PC while it runs.
- `scripts/vr-ab-analyze.py <run folder> [--size 512] [--center X,Y] [--sheet-size 256] [--columns N]` crops the
  same square around the left eye's centre from every screenshot and prints, per variant: `grid` (mean difference
  between adjacent pixels' luma, horizontal plus vertical: pixel grid and aliasing), `gridR`/`gridB` (the same for
  red and blue: PenTile sparkle), `lapvar` (Laplacian variance: sharpness), `over%` (overshoot at strong edges, in
  percent of the step), `hf%` (share of spectral energy above 0.375 cycles/pixel) and `diff` (mean difference to
  the first variant; 0 means nothing changed). It also writes `contact-sheet.png`: the crops' middles at 3×
  (nearest neighbour), labelled. A screenshot is the picture before the panel's subpixels and the lenses, so these
  measure what the renderer sends, not what the eye sees.

## Next steps
- Head-pose prediction (extrapolate about 1 frame from the gyroscope) to cut motion-to-photon lag further.
- Viewer profiles from Cardboard QR codes.
- A gaze cursor or a mouse-pointer overlay, for using the phone without the PC's mouse.
- A curved-screen option for very wide screens.
