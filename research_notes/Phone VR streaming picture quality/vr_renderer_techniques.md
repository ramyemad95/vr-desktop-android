# VR renderer techniques for showing a video/desktop stream on a virtual screen

Verification legend used below:
- **[code]** = I read the source file myself (2026-11-12, default branch).
- **[page]** = I fetched and read the page.
- **[snippet]** = seen only in a search-result summary; the page itself was not opened. Treat as lower confidence.

## 1. What do these apps use to downscale / minify the video onto the eye buffer, and what does each cost?

### Takeaway
On Quest-class headsets the dominant pattern is not a custom scaler at all: the app hands the decoded frame to the system compositor as a quad/cylinder layer and asks the compositor for "supersampling" (2 or 4 taps, for minification) and/or "sharpening" (3-5 taps or SGSR-based MQSR, for magnification). The open-source clients I read (ALVR, Moonlight XR) use plain bilinear sampling with no mipmaps in their own shaders; ALVR's only quality filter is Qualcomm SGSR1, which is an upscale+sharpen filter and does nothing useful for a 3-4x downscale. The target renderer's mipmaps + trilinear + 4x aniso + 4-tap RGSS is already at or above what these apps do for minification.

### Cited Findings

**Meta compositor layer filtering (Quest; used by Moonlight XR, and available to Virtual Desktop/Skybox/DeoVR-type apps)**
- `XR_COMPOSITION_LAYER_SETTINGS_NORMAL_SUPER_SAMPLING_BIT_FB` is a two-tap filter that changes its sampling pattern every other frame to approximate a four-tap filter; Meta says "For most use cases, the quality is indistinguishable from the four-tap filter." [page] — [Meta: Composition Layer Filtering](https://developers.meta.com/horizon/documentation/native/android/mobile-openxr-composition-layer-filtering)
- `..._QUALITY_SUPER_SAMPLING_BIT_FB` is a four-tap filter that "performs conventional supersampling with no temporal component"; it costs more. [page] — [Meta: Composition Layer Filtering](https://developers.meta.com/horizon/documentation/native/android/mobile-openxr-composition-layer-filtering)
- `..._NORMAL_SHARPENING_BIT_FB` is a three-tap sharpening algorithm that alternates its pattern every other frame to approximate a five-tap filter; `..._QUALITY_SHARPENING_BIT_FB` is Meta Quest Super Resolution (MQSR), "a single-pass spatial upscaling and sharpening technique". [page] — [Meta: Composition Layer Filtering](https://developers.meta.com/horizon/documentation/native/android/mobile-openxr-composition-layer-filtering)
- Meta's stated purpose split: supersampling is for layers the compositor *undersamples* (minification) and reduces flicker on high-contrast edges; sharpening is for layers that are *upsampled* to display resolution and counters blur. The page gives no numeric ratio threshold. [page] — [Meta: Composition Layer Filtering](https://developers.meta.com/horizon/documentation/native/android/mobile-openxr-composition-layer-filtering)
- Auto filtering: the runtime applies the chosen filters "only when beneficial", weighing rendered pixels per degree, hardware pixels per degree, GPU utilization and layer visibility. All settings are suggestions the compositor may ignore; if both bits of a pair are set it falls back to the normal variant. [page] — [Meta: Composition Layer Filtering](https://developers.meta.com/horizon/documentation/native/android/mobile-openxr-composition-layer-filtering); flag definitions in the [Khronos OpenXR spec](https://registry.khronos.org/OpenXR/specs/1.1/man/html/XrCompositionLayerSettingsFlagBitsFB.html)
- Cost: filtering "requires more GPU resources" because of larger kernel footprints and can cause composition frame drops; no absolute timings are published on that page. [page] — [Meta: Composition Layer Filtering](https://developers.meta.com/horizon/documentation/native/android/mobile-openxr-composition-layer-filtering)
- A fullscreen compositor layer costs about 0.6 ms on Quest 2 at CPU/GPU level 4. [snippet] — [Meta: OS compositor layers](https://developers.meta.com/horizon/documentation/native/android/os-compositor-layers/)
- MQSR is "built upon Snapdragon Game Super Resolution"; it is "up to 2x faster than the 'quality' sharpening algorithm" it replaced (which was RCAS-based), and "reduces halo artifacts compared to our existing contrast adaptive methods". Its cost is content-dependent and it "will increase Timewarp GPU cost". Content *near display resolution* may see "increased temporal aliasing or flicker". YUV textures and cube maps are unsupported (fall back to bilinear). [page] — [Meta blog: Meta Quest Super Resolution](https://developers.meta.com/horizon/blog/vr-image-quality-meta-quest-super-resolution)
- After Quest v53 the normal sharpening "produces near identical output quality at much lower performance cost compared to the quality method". [page] — [Meta blog: Meta Quest Super Resolution](https://developers.meta.com/horizon/blog/vr-image-quality-meta-quest-super-resolution)

**Moonlight XR (Gilleece/moonlight-android-xr, a Moonlight Android fork for Quest) [code]**
- The picture is submitted as an OpenXR quad layer per eye, or `XrCompositionLayerCylinderKHR` when the screen is curved. — [xr_layers.c](https://github.com/Gilleece/moonlight-android-xr/blob/HEAD/app/src/main/jni/xr-renderer/xr_layers.c)
- Sharpening (off / normal / quality) and supersampling (off / normal / quality) are user modes mapped directly to the four `XR_COMPOSITION_LAYER_SETTINGS_*_BIT_FB` flags in `setLayerSettings()`. The source comment states the reasoning: both are "done in its sampling pass, so neither costs the app anything. Supersampling is the compositor filtering harder where a layer is drawn smaller than its texture, which a 4K picture on a headset usually is." — [xr_layers.c](https://github.com/Gilleece/moonlight-android-xr/blob/HEAD/app/src/main/jni/xr-renderer/xr_layers.c)
- Its own GL copy of the video (external OES texture into the swapchain) uses `GL_LINEAR` min/mag, no mipmaps on the video texture; anisotropy is only set up for the room/environment atlas. — [xr_gl.c](https://github.com/Gilleece/moonlight-android-xr/blob/HEAD/app/src/main/jni/xr-renderer/xr_gl.c)

**ALVR (client, current master) [code]**
- Two stages: a "staging" GLES pass copies the decoder's `samplerExternalOES` texture to an RGBA texture with a single `texture()` tap (plus optional limited-to-full range fix); then a wgpu "stream" pass draws it into the eye swapchain. — [staging_fragment.glsl](https://github.com/alvr-org/ALVR/blob/master/alvr/graphics/resources/staging_fragment.glsl), [stream.wgsl](https://github.com/alvr-org/ALVR/blob/master/alvr/graphics/resources/stream.wgsl)
- The stream sampler is `mag_filter: Linear, min_filter: Linear` with everything else default (no mip filter, no anisotropy). — [stream.rs](https://github.com/alvr-org/ALVR/blob/master/alvr/graphics/src/stream.rs)
- The only optional quality filter is an embedded port of Qualcomm SGSR1 (`ENABLE_UPSCALING`, with `UPSCALE_USE_EDGE_DIRECTION`, `UPSCALE_EDGE_THRESHOLD` default 4/255, `UPSCALE_EDGE_SHARPNESS` default 2.0). It is foveation-aware: the SGSR source resolution is scaled by the foveation edge ratio outside the centre region. No bicubic/Lanczos/FSR/CAS path exists in the client shader. — [stream.wgsl](https://github.com/alvr-org/ALVR/blob/master/alvr/graphics/resources/stream.wgsl)
- ALVR's fixed-foveated encoding squeezes the edges on the server and the client shader un-squeezes them; an ALVR contributor acknowledged the first implementation's resampling caused blur. [snippet] — [ALVR issue #39](https://github.com/alvr-org/ALVR/issues/39)

**Snapdragon Game Super Resolution 1 (SGSR1)**
- Single-pass upscale + sharpen: "a 12-tap Lanczos-like scaling filter and adaptive sharpening filter". BSD-3-Clause. GLSL (OpenGL/Vulkan) and HLSL versions. [page] — [SGSR v1 README](https://github.com/SnapdragonStudios/snapdragon-gsr/blob/main/sgsr/v1/README.md), [repo README](https://github.com/SnapdragonStudios/snapdragon-gsr)
- Measured cost for a 1240x576 input at max GPU frequency: 0.476 ms (SD888), 0.356 ms (SD 8 Gen 1, 2340x1080 device), 0.295 ms (SD 8 Gen 2); plain bilinear on the same setup 0.248 / 0.196 / 0.156 ms. Cost "scales lineally with the configured scaling ouput region". [page] — [SGSR v1 README](https://github.com/SnapdragonStudios/snapdragon-gsr/blob/main/sgsr/v1/README.md)
- Qualcomm recommends edge threshold 8/255 for mobile and 4/255 for VR; sharpness in [1.0, 2.0]. It says the single-pass design makes it "well suited for XR applications" because it "can be combined with other operations". [page] — [SGSR v1 README](https://github.com/SnapdragonStudios/snapdragon-gsr/blob/main/sgsr/v1/README.md)
- As implemented (ALVR port), the filter takes one bilinear tap plus up to four `textureGather`s of the green channel and adds a clamped luma delta (±23/255) to the bilinear colour; it only activates where a local edge vote exceeds the threshold. [code] — [stream.wgsl](https://github.com/alvr-org/ALVR/blob/master/alvr/graphics/resources/stream.wgsl)

**FSR 1 (EASU + RCAS) on mobile**
- FSR1 is a two-pass pipeline: EASU (edge-adaptive spatial upsampling) then RCAS (robust contrast-adaptive sharpening). [snippet] — [GPUOpen FSR1 manual](https://gpuopen.com/manuals/fidelityfx_sdk/techniques/super-resolution-spatial/)
- Measured on an iPhone 12 (not Adreno): stock EASU about 5.4 ms and RCAS about 0.9 ms; after switching to half precision and simplifying deringing, EASU about 1.8 ms; net added cost about 2.0 ms. [snippet] — [atyuwen: Optimizing AMD FSR for Mobiles](https://atyuwen.github.io/posts/optimizing-fsr)

**Virtual Desktop**
- Virtual Desktop integrated Snapdragon Game Super Resolution on the headset for streamed PC VR (Quest 2 / Pico era). [snippet] — [Road to VR](https://www.roadtovr.com/?p=112116)
- Community guides describe VD's "Sharpening" slider as AMD contrast-adaptive sharpening and recommend 70-100%. [snippet, community, not developer-confirmed] — [abolethvr substack](https://abolethvr.substack.com/p/more-on-ez-vd-setup), [scrapbox settings guide](https://scrapbox.io/zxc/Virtual_Desktop_%E3%81%8A%E3%81%99%E3%81%99%E3%82%81%E8%A8%AD%E5%AE%9A)

**Video players (DeoVR / HereSphere / Skybox)**
- DeoVR's developers posted in 2023 that their improvement plan was "Oculus new SDK integration with new sharpening feature", experimenting with eye texture scale, and a native (non-Unity) path; the thread is tagged `ovroverlay` (i.e. compositor overlay layers). No answers were posted. [page] — [Meta community forum thread](https://communityforums.atmeta.com/discussions/dev-general/vr-video-player-performance-question-and-pixel-based-rendering/1084775)
- HereSphere added an image-sharpening option in v0.7.4 for Quest 2. [snippet] — [HereSphere devlog](https://heresphere.itch.io/heresphere-vr-video-player-quest-2/devlog/473049/update-v074-released)

**Desktop reference scalers (mpv / libplacebo)**
- mpv's high-quality profile uses `dscale=mitchell` with `correct-downscaling=yes` and `linear-downscaling=yes`. [snippet] — [mpv builtin.conf mirror](https://git.tjdev.de/mirror/mpv/src/branch/rc/etc/builtin.conf), [Artoriuz: mpv upscaling evaluation](https://artoriuz.github.io/blog/mpv_upscaling.html)
- Encoding-community guidance: Mitchell is a "good neutral default"; Hermite is "often considered one of the best choices for downscaling" with minimal artifacts at the cost of slight blur; Lanczos is slightly sharper than Mitchell but rings more. [snippet] — [guide.encode.moe: Resampling](https://guide.encode.moe/encoding/resampling.html)

### Inferences
- No surveyed VR app uses bicubic/Lanczos/EWA for *minifying* video. The Quest ecosystem's answer to minification is 2-4 extra bilinear taps in the compositor (temporal 2-tap or static 4-tap). The target renderer's 4-tap rotated-grid supersample on top of trilinear mips is the same class of filter as Meta's QUALITY supersampling, and its mip chain makes each tap properly prefiltered, which the Quest path (single-level texture, 4 taps) is not at a 4x ratio. There is no evidence a fancier kernel would be an upgrade the user would see.
- SGSR1, FSR1-EASU and MQSR are magnification filters. At a 3-4x downscale they are the wrong tool: SGSR's kernel reads source-resolution neighbours, so at 4x minification it would sharpen aliasing rather than detail. Meta's own warning that MQSR adds flicker even at ~1:1 supports this. They become relevant only if the stream is switched to a resolution at or below the on-screen pixel count (see section 2).
- If a sharpening option is kept, the cheapest credible implementations to model on are RCAS/CAS-style 5-tap or Meta's "normal" 3-tap-alternating scheme; a full SGSR pass over ~2 x 970 px-wide regions would cost well under 1 ms on Adreno 730 going by Qualcomm's 0.36 ms figure for a larger output on the same SoC generation (extrapolation, not measured).
- Meta's "normal" modes show a trick the phone renderer could copy: alternate a 2-tap pattern each frame to get 4-tap quality at half the cost (the eye integrates at 60-120 Hz). It only works when the display refresh is steady.
- The user's preference for mip bias +1.1 and no sharpening is consistent with the "Hermite/Mitchell: soft, artifact-free downscale" school rather than the "sharp + ringing" school; trilinear with a positive bias approximates a wider, softer box/tent prefilter.

### Gaps
- Virtual Desktop, Bigscreen, Skybox, DeoVR and Steam Link are closed source; I found no primary statement of their exact sampling path (mipmaps vs layer supersampling). The VD "sharpening = CAS" claim is community-sourced.
- Quest Link / Air Link client-side resampling details beyond "Link Sharpening: Normal/Quality (MQSR)" were not found.
- No published per-millisecond cost for Meta's supersampling flags, and no FSR1/CAS timings on Adreno 730 specifically.
- libplacebo scaler GLSL ES ports: not investigated (ran out of budget); mpv's scalers are multi-pass separable filters and would need two extra passes per frame at 4K.

## 2. Relation between virtual screen angular size, source resolution and display pixels per degree; is a matched lower resolution better than 4K downscaled?

### Takeaway
I found no authoritative "1:1" rule from Virtual Desktop or Bigscreen. The evidence that exists points the other way from strict 1:1: text legibility on Quest 3 kept improving as the virtual monitor went from 1080p to 1440p to 1728p even though the screen covers far fewer display pixels, i.e. moderate oversampling (roughly 1.3-2x the on-screen pixel count) plus a good minification filter beats a matched resolution. 4K onto ~970 px is beyond that range, so most of its pixels (and bitrate) are discarded, and it is the regime where the minification filter matters most.

### Cited Findings
- A Reddit analysis of Quest 3 virtual monitors reported that legibility of small text continued to improve from 1080p to 1440p to 1728p in Virtual Desktop, estimated roughly 2000 display pixels across 100 degrees, and noted VD was updated to target a 1440p virtual monitor on Quest 3. [snippet; the mirror hosting the thread was unreachable when fetched] — [r/virtualreality thread (mirror)](https://red.bismuth.it/r/virtualreality/comments/1857125/how_much_resolution_can_quest_3_actually_display)
- For PC VR (projection layers, not the desktop screen), VD's developer is quoted second-hand as saying about 1.45x the display resolution ("Godlike" on Quest 3) is what gives "1:1 coverage at the center of your vision" after lens distortion, and that going beyond 100% render resolution brings minimal gains. [snippet, second-hand] — [abolethvr substack](https://abolethvr.substack.com/p/more-on-ez-vd-setup)
- Meta's developer tooling exposes, per compositor layer, the rendered PPD, the texture resolution and a "recommended texture resolution", and suggests a sharpening filter when rendered PPD drops as the user gets closer (magnification). [snippet] — [Meta blog: compositor layer functions in MQDH](https://developers.meta.com/horizon/blog/mqdh-compositor-layers-visibility-properties-functions-enhance-visual-quality-performance)
- Meta's help page for its own Remote Desktop says the default resolution settings "were selected for the best text quality" and that changing them may degrade quality. [snippet] — [Meta help: Remote Desktop](https://www.meta.com/help/quest/1370025034331518/)
- Carmack's VR-video guidance: do not exceed what the display can resolve; "you are probably better off spending the bitrate on better pixels at 5120" rather than going higher; a 2880x2880 30 fps base layer was "a really good trade" over 2048x2048 at 60 fps, which is "really obviously blurry". [page] — [Meta blog: Techniques for Improved VR Video w/ John Carmack](https://developers.meta.com/horizon/blog/techniques-for-improved-vr-video-w-john-carmack/)
- Bigscreen: no official recommended resolution found. A Bigscreen developer's explanation for blurry desktop text was that the big screen in rooms is a fixed size. [snippet] — [Steam discussion](https://steamcommunity.com/app/457550/discussions/0/1368380934276920918)
- Moonlight XR's author describes a 4K picture on a headset as usually being drawn smaller than its texture, and treats compositor supersampling as the remedy rather than lowering resolution. [code comment] — [xr_layers.c](https://github.com/Gilleece/moonlight-android-xr/blob/HEAD/app/src/main/jni/xr-renderer/xr_layers.c)

### Inferences
- For the target (screen ≈ 970 px per eye): a source of roughly 1440p-ish width (2560 wide ≈ 2.6x) or even 1080p (1920 wide ≈ 2x) is the range the Quest evidence supports; 3840 wide (≈ 4x) spends encoder bitrate and decoder time on detail that the mip chain averages away. A lower stream resolution at the same bitrate gives more bits per retained pixel (Carmack's "better pixels" argument) and a cheaper decode and mip generation.
- Strict 1:1 is not attainable anyway: the lens-distortion warp and head motion mean source texels never align with display pixels, so some oversampling (Nyquist margin) is what makes text edges stable. That is consistent with the 1080p → 1728p legibility result.
- The existing "Resolution: match the PC" behaviour is good for desktop sharpness on a monitor but not necessarily optimal for the headset; an option that picks a stream resolution of about 1.5-2x the on-screen pixel width would be the evidence-based default, with 4K kept for when the virtual screen is enlarged.
- Because the user prefers a soft picture (bias +1.1 ≈ sampling one mip lower, i.e. effectively ~1920-wide content from a 4K frame), a native ~1080p-1440p stream sampled with bias near 0 should give a similar look with less work — worth an A/B.

### Gaps
- No primary, quotable recommendation from Guy Godin (Virtual Desktop) or Bigscreen on desktop resolution versus screen size was reachable; the Reddit thread could only be seen via a search summary.
- No controlled comparison of "4K downscaled" versus "matched resolution at same bitrate" for a virtual screen was found; the above is inference from adjacent evidence.

## 3. Gamma-correct (linear-light) filtering, sRGB textures and mip generation

### Takeaway
Meta/Carmack explicitly recommend sRGB swapchains and framebuffers "to get gamma-correct filtering" for video, and mpv's quality profile downscales in linear light; but the open-source VR streaming clients I read mostly filter gamma-encoded values. The known visible effect of gamma-space downscaling is darkening/dimming of fine high-contrast detail (thin text strokes, 1-pixel patterns), which matters most at exactly the 3-4x minification the target renderer does.

### Cited Findings
- Carmack: "Use an sRGB format swapchain and framebuffer to get gamma-correct filtering." [page] — [Meta blog: Techniques for Improved VR Video](https://developers.meta.com/horizon/blog/techniques-for-improved-vr-video-w-john-carmack/)
- mpv `gpu-hq` enables `linear-downscaling` together with `correct-downscaling` and Mitchell. [snippet] — [mpv builtin.conf mirror](https://git.tjdev.de/mirror/mpv/src/branch/rc/etc/builtin.conf)
- A forum caveat: linear-light downscaling is advisable only with soft scalers (sharp kernels ring more visibly in linear light). [snippet, forum opinion] — [Plex forum thread](https://forums.plex.tv/t/video-playback-quality-options/836791)
- Downscaling in gamma space "can sometimes noticeably dim the image". [snippet] — [guide.encode.moe: Resampling](https://guide.encode.moe/encoding/resampling.html)
- Without gamma-correct mip generation a 1-pixel black/white pattern becomes a much darker grey at mip 1 and above instead of the correct mid-grey; the fix is decode to linear, filter, re-encode (sRGB texture formats do this in hardware for sampling and `glGenerateMipmap`). [snippet] — [Learning Modern 3D Graphics Programming: sRGB and Mipmaps](https://nicolbolas.github.io/oldtut/Texturing/Tut16%20Mipmaps%20and%20Linearity.html), [Real-Time Rendering blog](https://www.realtimerendering.com/blog/tag/mipmap/)
- Do not store linear values in 8 bits (banding in darks); keep sRGB-encoded 8-bit storage and let the sampler linearize. [snippet] — [demofox: Don't convert sRGB U8 to linear U8](https://blog.demofox.org/2018/03/10/dont-convert-srgb-u8-to-linear-u8/)
- Moonlight XR does **not** do linear-light filtering: with an sRGB swapchain it disables sRGB encode on write (`GL_EXT_sRGB_write_control`) because "The video frames are already gamma encoded", and samples the video as plain gamma-encoded values. [code] — [xr_gl.c](https://github.com/Gilleece/moonlight-android-xr/blob/HEAD/app/src/main/jni/xr-renderer/xr_gl.c)
- ALVR samples the gamma-encoded stream bilinearly and applies the sRGB-to-linear curve *after* sampling (`ENABLE_SRGB_CORRECTION`), so its own filtering and SGSR run on gamma-encoded values. [code] — [stream.wgsl](https://github.com/alvr-org/ALVR/blob/master/alvr/graphics/resources/stream.wgsl)

### Inferences
- For the target: making the intermediate RGBA texture `GL_SRGB8_ALPHA8` (SDR path) makes `glGenerateMipmap`, trilinear, aniso and the RGSS average all operate in linear light for free on GLES 3.x; the per-eye shader then outputs linear and re-encodes (or renders to an sRGB FBO). Expected visible change: light-on-dark thin text gets slightly bolder/brighter and dark-on-light text slightly thinner; overall fine detail no longer dims. With a soft filter (bias +1.1) there is no added ringing risk, matching the mpv forum caveat.
- For the HDR path (PQ/HLG in a 10-bit or float texture) there is no hardware sRGB decode; linear-light mips would require converting to linear into an RGBA16F texture before mip generation, which doubles texture memory/bandwidth at 4K. Whether that is affordable on Adreno 730 is untested.
- Since two shipping clients (ALVR, Moonlight XR) skip this, the effect is evidently not glaring on game content; it should be most visible on desktop text, which is the use case to A/B.

### Gaps
- No source measured the text-legibility effect of linear versus gamma mipmapping in a VR virtual screen specifically.
- Whether Virtual Desktop, Bigscreen, Skybox or DeoVR filter in linear light is unknown (closed source).
- Cost of `glGenerateMipmap` on a 4K sRGB8 versus RGBA8 texture on Adreno 730 not found; needs measuring.

## 4. Lens-distortion pass quality: mesh vs per-pixel, chromatic aberration, timewarp

### Takeaway
Google's Cardboard SDK distortion is a 40x40 vertex-displaced mesh sampled with a single `texture2D` tap in mediump: no chromatic-aberration correction, no reprojection, and an unavoidable second resampling of the eye texture. A custom renderer that samples the video directly inside the distortion pass (as the target does) avoids the double resample and can add per-channel distortion cheaply; that is something the Cardboard SDK does not do.

### Cited Findings
- Cardboard `DistortionMesh` uses `kResolution = 40` (40x40 vertices per eye); vertices are displaced by the *inverse* distortion "so that the mesh exactly covers the screen area that gets rendered to. Helps avoid visible aliasing in the vignette"; UVs are left regular. [code] — [distortion_mesh.cc](https://github.com/googlevr/cardboard/blob/master/sdk/distortion_mesh.cc), [distortion_mesh.h](https://github.com/googlevr/cardboard/blob/master/sdk/distortion_mesh.h)
- The GLES2 distortion fragment shader is `precision mediump float` and does one `texture2D(u_Texture, coords)` lookup of the app's eye texture; there is no per-channel offset and the word "chromatic" does not occur in the mesh, lens-distortion or renderer sources. [code] — [opengl_es2_distortion_renderer.cc](https://github.com/googlevr/cardboard/blob/master/sdk/rendering/opengl_es2_distortion_renderer.cc), [lens_distortion.cc](https://github.com/googlevr/cardboard/blob/master/sdk/lens_distortion.cc)
- The distortion model is a radial polynomial from the viewer's device parameters (`distortion_coefficients`), one set for all colour channels. [code] — [lens_distortion.cc](https://github.com/googlevr/cardboard/blob/master/sdk/lens_distortion.cc); API: [Cardboard Lens Distortion reference](https://developers.google.com/cardboard/reference/c/group/lens-distortion)
- The standard way to correct lateral chromatic aberration is to resample red, green and blue separately with slightly different distortion, which can be done on a displaced mesh or by offsetting the red/blue sample positions per pixel. [snippet] — [Improved pre-warping for wide angle HMDs (paper)](https://typeset.io/pdf/improved-pre-warping-for-wide-angle-head-mounted-displays-188i63csiv.pdf), [Meta forum: Gear VR distortion/CA](https://communityforums.atmeta.com/discussions/dev-general/how-does-the-gear-vr-runtime-handle-lens-distortion--chromatic-aberration/613517)
- Mesh (vertex-displacement) distortion avoids a per-pixel distortion computation; an alternative moves distortion into each object's vertex shader to remove the intermediate render target entirely. [snippet] — [Boris Smus: VR lens distortion](https://smus.com/vr-lens-distortion/), [ustwo: VR distortion correction using vertex displacement](https://ustwo.com/blog/vr-distortion-correction-using-vertex-displacement)
- "Cardboard does not support asynchronous reprojection"; Daydream had it always on, and it corrects rotation only. [snippet] — [Google VR: Asynchronous Reprojection](https://developers.google.com/vr/discover/async-reprojection)
- On Quest, quad/cylinder layers are reprojected by the compositor at display rate independent of the app's frame rate, which is why Carmack says "Display the video surface(s) on a compositor layer, don't draw them into your 3D world." [page] — [Meta blog: Techniques for Improved VR Video](https://developers.meta.com/horizon/blog/techniques-for-improved-vr-video-w-john-carmack/)
- ALVR's stream pass carries a `reprojection_transform` matrix applied to the quad vertices in the vertex shader. [code] — [stream.wgsl](https://github.com/alvr-org/ALVR/blob/master/alvr/graphics/resources/stream.wgsl)

### Inferences
- Per-channel CA correction is adoptable: either three texture lookups at radially scaled UVs in the per-eye fragment shader (3x the taps of whatever filter is in use — with 4-tap RGSS that is 12 taps, so apply CA to a single trilinear tap or scale the RGSS centre only), or three slightly different meshes. Cardboard viewer profiles do not carry per-channel coefficients, so the red/blue scale factors would have to be hand-tuned per viewer (typical single-element plastic lenses need on the order of a percent of radial scale difference — value not sourced, needs calibration with a test grid).
- A 40x40 mesh with linear interpolation of UVs between vertices is Google's accepted quality bar; a per-pixel polynomial (or a denser mesh) is only marginally better geometrically. The bigger quality win of a custom pass is single resampling: video → display in one filtered lookup, instead of video → eye buffer → distorted display (two bilinear resamples, each of which softens and aliases).
- mediump UV math in Cardboard's shader is a latent precision issue for a 3840-wide texture (mediump may be 16-bit float on some GPUs: ~10 bits of mantissa cannot address 3840 texels with sub-texel precision). The target renderer should keep UVs in highp.
- For a world-locked screen on a phone without compositor reprojection, the app-level equivalent of timewarp is simply to render the per-eye pass every vsync with the latest head pose regardless of video frame arrival (the distortion pass *is* the reprojection, since the scene is a single textured quad). This gives the same decoupling as Quest quad layers.

### Gaps
- No source quantifies CA magnitude for common Cardboard-style lenses, and no open-source Cardboard-class renderer with CA correction was located in this pass.
- How Gear VR / Daydream implemented CA in their distortion mesh (three UV sets per vertex is the commonly described approach) was only seen via forum snippet, not primary docs.

## 5. Temporal techniques: frame pacing, low persistence, temporal supersampling

### Takeaway
The well-sourced temporal advice is about pacing, not filtering: lock video presentation precisely to the real display rate and choose a refresh rate that matches the content. Meta's compositor does use frame-alternating sample patterns (2 taps alternating ≈ 4 taps; 3 alternating ≈ 5) as a cheap temporal supersample, which is the only shipping example of temporal jitter for a static layer I found. Low persistence on phones is device-specific and not app-controllable in general.

### Cited Findings
- Carmack: "Choose the correct VR refresh rate of 60 or 72 fps based on the video content"; sync to "the actual screen's refresh rate (which is not exactly 60.0 or 72.0 fps)"; "Precisely lock the video and audio rate to the display rate", resampling audio slightly to match; "Copy the decoded frames to a deep swapchain instead of directly using the Surface to mitigate small glitches." [page] — [Meta blog: Techniques for Improved VR Video](https://developers.meta.com/horizon/blog/techniques-for-improved-vr-video-w-john-carmack/)
- Meta's normal supersampling and normal sharpening alternate their tap pattern every other frame to approximate a larger kernel, and Meta says the result is indistinguishable from the static 4-tap filter for most uses. [page] — [Meta: Composition Layer Filtering](https://developers.meta.com/horizon/documentation/native/android/mobile-openxr-composition-layer-filtering)
- Google: "Advanced displays tuned for VR have a low persistence mode, which reduces motion blur"; a small number of users are sensitive to the flicker; users can turn it off in VR settings (Daydream-era system feature). [page] — [Google VR: Fundamental Concepts](https://developers.google.com/vr/discover/fundamentals)
- Most phone displays are sample-and-hold, which causes motion blur in VR; low-persistence was a Daydream-ready hardware/firmware feature (e.g. Zenfone AR's "ultra-low-persistence mode"). [snippet] — [Google VR fundamentals](https://developers.google.com/vr/discover/fundamentals), [TechRadar Zenfone AR review](https://www.techradar.com/reviews/asus-zenfone-ar-review/2)
- Android's VR mode is a per-device feature that must be checked for; not all devices support it. [snippet] — [Monado issue #103](https://gitlab.freedesktop.org/monado/monado/-/issues/103)
- SGSR2 is a temporal upscaler for Adreno aimed at reducing "aliasing, flicker, and ghosting" (needs motion vectors/jitter from a 3D renderer; not applicable to decoded video). [page] — [snapdragon-gsr README](https://github.com/SnapdragonStudios/snapdragon-gsr)

### Inferences
- Frame-alternating sample patterns are adoptable: instead of 4 RGSS taps per pixel per frame, take 2 taps and flip the pair each display frame. At 120 Hz the alternation fuses; at 60 Hz it may shimmer on 1-pixel text. It halves texture fetches in the per-eye pass, or alternatively allows 8 effective taps for the cost of 4.
- Software black-frame insertion on a 120 Hz phone panel (show frame, show black) would halve brightness and give 60 Hz flicker; OLED phones already have PWM-like emission but not VR-grade persistence. Nothing found suggests any current Cardboard-class app does this, and Samsung exposes no public low-persistence API that I could find (unverified).
- For a 60 fps stream on a 120 Hz panel, the pacing guidance implies presenting each video frame for exactly two refreshes while still re-running the distortion pass each refresh with a fresh head pose (see section 4), and steering the host frame rate to the panel's true rate to avoid periodic judder.
- True temporal supersampling with history (TAA-style) is unnecessary for a static screen: the mip chain already supplies a correct prefilter; temporal jitter only buys cheaper taps, not more information, and risks shimmer when the head is still.

### Gaps
- No source found on low-persistence or BFI support on Galaxy S22-class panels or on any app-level BFI in phone VR players.
- No published perceptual thresholds for frame-alternating filters at 60 Hz versus 120 Hz.
- Steam Link (Quest) pacing and filtering internals: nothing citable found.

## 6. Open-source implementations worth reading

### Takeaway
The most directly useful code is small: ALVR's `stream.wgsl` (a complete, parameterised SGSR1 port, BSD-3), Moonlight XR's `xr_layers.c` (how a Moonlight fork drives Meta's layer filters and cylinder layers), and Cardboard's `distortion_mesh.cc` (reference mesh distortion). None of them contains a better minification path than mipmaps + anisotropy + multi-tap.

### Cited Findings
- ALVR client stream shader with SGSR1 (edge-direction and non-edge-direction variants, threshold/sharpness as overrides), foveation un-warp and sRGB handling — [alvr/graphics/resources/stream.wgsl](https://github.com/alvr-org/ALVR/blob/master/alvr/graphics/resources/stream.wgsl); sampler and pipeline setup — [alvr/graphics/src/stream.rs](https://github.com/alvr-org/ALVR/blob/master/alvr/graphics/src/stream.rs); OES-to-RGBA staging — [staging_fragment.glsl](https://github.com/alvr-org/ALVR/blob/master/alvr/graphics/resources/staging_fragment.glsl) [code]
- ALVR server-side shaders (foveated compression, colour correction, RGB-to-YUV) — [alvr/server_openvr/cpp/alvr_server/shader](https://github.com/alvr-org/ALVR/tree/master/alvr/server_openvr/cpp/alvr_server/shader) [file listing only]
- Moonlight XR (Moonlight Android fork for Quest): native OpenXR renderer under [app/src/main/jni/xr-renderer](https://github.com/Gilleece/moonlight-android-xr/tree/HEAD/app/src/main/jni/xr-renderer) — `xr_layers.c` (quad/cylinder layers, `XR_FB_composition_layer_settings` flags), `xr_gl.c` (video blit, sRGB write control), plus `xr_grade.c`, `xr_ambilight.c`, `xr_glow.c` [code / file listing]
- Nightfall (Godot-based streaming client that cites Moonlight XR's performance work; has its own native OpenXR renderer and composition-layer manager) — [tB0nE/nightfall](https://github.com/tB0nE/nightfall) (e.g. `extensions/nightfall-xr/src/fast_xr_renderer.cpp`, `src/composition_layer_manager.gd`) [file listing only; release note via snippet: [v0.7.8](https://newreleases.io/project/github/tB0nE/nightfall/release/v0.7.8)]
- Moonlight for visionOS — [RikuKunMS2/moonlight-ios-vision](https://github.com/RikuKunMS2/moonlight-ios-vision) [snippet; not inspected]
- Cardboard SDK distortion: [sdk/distortion_mesh.cc](https://github.com/googlevr/cardboard/blob/master/sdk/distortion_mesh.cc), [sdk/polynomial_radial_distortion.cc](https://github.com/googlevr/cardboard/blob/master/sdk/polynomial_radial_distortion.cc), [sdk/rendering/opengl_es2_distortion_renderer.cc](https://github.com/googlevr/cardboard/blob/master/sdk/rendering/opengl_es2_distortion_renderer.cc), ES3 variant [opengl_es3_distortion_renderer.cc](https://github.com/googlevr/cardboard/blob/master/sdk/rendering/opengl_es3_distortion_renderer.cc) [code]
- Qualcomm SGSR reference shaders and docs (v1 spatial, v2 temporal), BSD-3 — [SnapdragonStudios/snapdragon-gsr](https://github.com/SnapdragonStudios/snapdragon-gsr) [page]
- FSR1 mobile optimisation write-up (half precision, simplified EASU) — [atyuwen: Optimizing AMD FSR for Mobiles](https://atyuwen.github.io/posts/optimizing-fsr) [snippet]
- PhoneVR (ALVR on Cardboard phones) exists as a Cardboard-class streaming client — [alvr-org/PhoneVR](https://www.github.com/alvr-org/PhoneVR/issues/99) [snippet; renderer not inspected]

### Inferences
- If an optional "sharp" mode is ever wanted for sub-2x streams, ALVR's WGSL port is the easiest thing to transcribe to GLSL ES 3.1+ (`textureGather` is core in ES 3.1; Adreno 730 supports it), and the foveation-style `source_resolution_multiplier` shows how to feed it the correct texel size.
- Moonlight XR's design (off / normal / quality for both sharpen and supersample, logged so A/B sessions are attributable) mirrors the target project's own A/B tooling and is a reasonable UI model: expose "supersampling: off / temporal 2-tap / 4-tap" rather than kernel names.

### Gaps
- PhoneVR's renderer (the closest analogue: stream onto a Cardboard phone) was not read; it likely uses the stock Cardboard distortion renderer (unverified).
- libplacebo/mpv GLSL scaler ports to GLES were not located or evaluated.
- Nightfall and Moonlight-visionOS rendering code was not read in detail.
