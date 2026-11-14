# YouTube lane: what creators say about picture quality for Sunshine/Apollo + Moonlight streaming and for virtual screens in VR

Method and limits (read this first):

- Source: YouTube captions pulled with the `youtube-captions` skill (yt-dlp, captions only), 2026-11-12. 21 videos were read as text; 13 of them held usable claims and are cited below.
- YouTube rate-limited caption downloads (HTTP 429) part-way through. After the skill's one slow retry and two later paced retries, I stopped, as the skill requires. Videos that were found but could NOT be read are listed under Gaps in each section; nothing below comes from them.
- Timestamps are the start of the 30-second caption window where the statement occurs, so they are accurate to about +/-30 s.
- Most captions are auto-generated. Numbers I judged likely misheard are flagged "(auto-caption, check)".
- Labels used: **[demonstrated]** = shown on screen or measured in the video; **[asserted]** = stated without a test shown; **[opinion]** = personal preference.
- Arabic searches ("أفضل إعدادات Moonlight Sunshine", "نظارة الواقع الافتراضي للموبايل وضوح الصورة") returned mostly English videos with auto-translated titles. Two real Arabic candidates were found but could not be fetched before the rate limit (see Gaps).

Videos cited (title, channel, upload date):

| # | Title | Channel | Uploaded | Link |
|---|---|---|---|---|
| V1 | Configuring Sunshine v2025.122 | MikeTheTech | 2025-02-05 | https://www.youtube.com/watch?v=QaNXP2-hXlY |
| V2 | Goodbye Sunshine & Moonlight, Hello Apollo & Artemis (Setup Guide) | Joey's Retro Handhelds | 2025-01-17 | https://www.youtube.com/watch?v=H0jmqVIhwIA |
| V3 | Handheld Streaming Guide (Apollo, Moonlight, etc) | Retro Game Corps | 2026-04-23 | https://www.youtube.com/watch?v=RiggvcusEuA |
| V4 | Sunshine Deep Dive for Moonlight Game Streaming | Cloud Gaming Couch | 2025-06-23 | https://www.youtube.com/watch?v=BP6ZIJPTwU4 |
| V5 | 12 Common Moonlight & Sunshine Problems — Solved! | Cloud Gaming Couch | 2025-07-30 | https://www.youtube.com/watch?v=KFDwZKkWQyI |
| V6 | Apollo Artemis Set Up Guide For Lowest Latency with Playnite REDMAGIC Astra | Azawraith Gaming | 2025-07-25 | https://www.youtube.com/watch?v=LD8l6SccCZE |
| V7 | Best Virtual Desktop Setup + Settings [Wireless PCVR Gaming] | F4CEpa1m | 2025-12-18 | https://www.youtube.com/watch?v=0MXLMEUHRc0 |
| V8 | Steam Link + SteamVR Setup and Best Settings [Wireless PCVR Gaming] | F4CEpa1m | 2025-12-21 | https://www.youtube.com/watch?v=XOirjFoBGeo |
| V9 | PERFECT Virtual Desktop Settings for EXTREME Low Latency | Mac In VR | 2026-04-18 | https://www.youtube.com/watch?v=zUaxhvonsAg |
| V10 | my quick overview of Virtual Desktops new Snapdragon game super resolution | Virtually Real | 2023-04-29 | https://www.youtube.com/watch?v=sZWx3iJ0Ets |
| V11 | Quest 3 PC VR Setup for Incredible Clarity | VR ManCave | 2023-10-21 | https://www.youtube.com/watch?v=6TtpmkTgvEk |
| V12 | Quest PCVR Comparison: Air Link vs Steam Link vs Virtual Desktop vs ALVR | Fix My Oculus | 2025-08-30 | https://www.youtube.com/watch?v=wzRFJl2Dem0 |
| V13 | Using Quest 3 as a Monitor: Virtual vs Real Screen - Text Clarity Tests & Tips | SpookyFairy VR | 2024-06-04 | https://www.youtube.com/watch?v=zgckHCs9nfg |
| V14 | Is The Meta Quest 3 Blurry? 5 Reasons WHY, And Tips To Fix It! | Dr. EyeGuy | 2023-11-18 | https://www.youtube.com/watch?v=R3kr8FM2-HY |
| V15 | Mura Explained: The Tradeoff Behind VR Displays | Immersed Robot | 2026-06-16 | https://www.youtube.com/watch?v=M8sCcUuZAEU |
| V16 | OLED VR Headsets Won't Succeed Until This Is Fixed | OCmodshop | 2026-11-09 | https://www.youtube.com/watch?v=iaEJfDHwxYk |
| V17 | Best Ways to Watch Movies on Quest 3 at Max Quality | VR ManCave | 2026-03-27 | https://www.youtube.com/watch?v=sKfH2HO3wK0 |
| V18 | Moonlight XR - Free 3D PC streaming that actually works | Virtually Real | 2026-08-16 | https://www.youtube.com/watch?v=Kw0dCLmVdRo |

Read but nothing useful on picture quality: "I Tried Google Cardboard AGAIN So You Don't Have To..." (Wrighteous, 2025-03-10, https://www.youtube.com/watch?v=_8CuxzQA0K4), "the Samsung Gear VR in 2024? Let's Explore!" (jOhnZoid, 2024-02-11, https://www.youtube.com/watch?v=REobLsRVNsY), "Quest 3 is Changing Flat Gaming Forever" (BMFVR, 2025-10-02, https://www.youtube.com/watch?v=i7dTDwEgAHM).

---

## 1. Sunshine/Apollo + Moonlight "best settings": bitrate, codec, HDR, 10-bit, resolution/virtual display, NVENC preset/AQ, frame pacing, host refresh rate, common mistakes, 2025-2026 changes

### Takeaway
Creators agree on three things: make the host display match the client's resolution and refresh rate (Apollo's per-device virtual display, or Sunshine 2025's built-in "use client resolution/refresh rate"), leave the encoder near its fastest preset, and tune bitrate to the local network rather than chase a maximum. They disagree widely on the bitrate number (never above 40 Mbps vs 120 vs up to 300 Mbps), and none of them measured picture quality; almost every setting claim here is asserted, not tested.

### Cited Findings

**Host display must match the client (resolution and refresh rate)**
- [asserted] A host display that does not match the client's resolution and refresh rate is the cause of jitter, input lag and stutter; Apollo fixes this by creating a virtual display per client device that copies its resolution and refresh rate, replacing dummy HDMI plugs and resolution scripts. — [V2, Joey's Retro Handhelds, ~3:00-4:30](https://www.youtube.com/watch?v=H0jmqVIhwIA&t=180s)
- [demonstrated] After connecting, Windows display settings showed the new virtual display at 1080p 120 Hz named after the client device. — [V2, ~14:30](https://www.youtube.com/watch?v=H0jmqVIhwIA&t=870s)
- [asserted] In Apollo, each app (Desktop, Steam Big Picture, any added game) needs "always use virtual display" ticked or it will not use it. — [V2, ~9:00-9:30](https://www.youtube.com/watch?v=H0jmqVIhwIA&t=540s)
- [asserted] Sunshine (stable, since the early-2025 release) can now set the host display to the client's resolution and refresh rate by itself ("use resolution provided by the client", same for refresh rate), so QRes-style scripts are no longer needed; a manual override exists. — [V4, Cloud Gaming Couch, ~4:00-4:30](https://www.youtube.com/watch?v=BP6ZIJPTwU4&t=240s); same feature described in [V1, MikeTheTech, ~7:00](https://www.youtube.com/watch?v=QaNXP2-hXlY&t=420s)
- [asserted, creator says untested] Sunshine 2025.122 added resolution/refresh "remapping": when a client requests e.g. 1280x800@60 the host can run a higher resolution and 120 Hz instead. The creator states he had not tried it. — [V1, ~7:30-8:00](https://www.youtube.com/watch?v=QaNXP2-hXlY&t=450s)
- [asserted] Sunshine 2025.122 selects the display by a stable device ID (not the Windows display name) and can auto-activate it or make it primary; custom modes must exist in the virtual display driver's XML. — [V1, ~5:00-7:00](https://www.youtube.com/watch?v=QaNXP2-hXlY&t=300s)
- [opinion] Apollo is worth it mainly if you stream to several devices with different resolutions/aspect ratios; otherwise Sunshine is "the most stable and better supported". — [V5, Cloud Gaming Couch, ~2:00](https://www.youtube.com/watch?v=KFDwZKkWQyI&t=120s)
- [opinion] For Windows hosts, Apollo is recommended for its automatic virtual display. — [V3, Retro Game Corps, ~3:30](https://www.youtube.com/watch?v=RiggvcusEuA&t=210s)
- [asserted] Apollo option "double refresh rate for virtual display" should be ticked (explanation in the video is unclear; the creator ties it to keeping the virtual display's mode saved). — [V6, Azawraith Gaming, ~16:00](https://www.youtube.com/watch?v=LD8l6SccCZE&t=960s)
- [asserted] Stream at 120 fps rather than the tablet's 165 Hz unless the PC is top-end; he also adds a custom client resolution of 2560x1600. — [V6, ~31:30-32:00](https://www.youtube.com/watch?v=LD8l6SccCZE&t=1890s)

**Bitrate (creators conflict)**
- [opinion] Artemis default is 28 Mbps; raise in steps to 30/35/40 only if compression artifacts are visible, and he says he generally never goes above 40, because higher can hurt streaming performance. — [V3, Retro Game Corps, ~16:00-16:30](https://www.youtube.com/watch?v=RiggvcusEuA&t=960s)
- [opinion] Moonlight on Steam Deck defaults to 13 Mbps at 1280x800@90, which he considers too low; he sets 30-32. — [V3, ~24:30-25:00](https://www.youtube.com/watch?v=RiggvcusEuA&t=1470s)
- [opinion] Raise bitrate in 25 Mbps steps until the stream becomes unstable, then back off; he says he can sometimes run up to 300 Mbps (he says megabytes; means megabits). Only the home network matters, not the internet plan. — [V2, Joey's Retro Handhelds, ~12:30-13:30](https://www.youtube.com/watch?v=H0jmqVIhwIA&t=750s)
- [opinion] 120 Mbps at 2560x1600@120 on a Snapdragon tablet; the tablet could take 300 Mbps but he finds that less smooth. — [V6, Azawraith Gaming, ~32:00-33:00](https://www.youtube.com/watch?v=LD8l6SccCZE&t=1920s)
- Conflict: V3 (max ~40 Mbps) vs V6 (120 Mbps) vs V2 (up to 300 Mbps). None showed a quality or latency measurement.

**Codec**
- [asserted] With HEVC and AV1 left at Sunshine's default, Moonlight picks AV1 if both ends support it, else HEVC, else H.264; AV1 gives better quality at the same bitrate but needs recent hardware. — [V4, Cloud Gaming Couch, ~6:00-6:30](https://www.youtube.com/watch?v=BP6ZIJPTwU4&t=360s)
- [asserted] AV1 gives better quality for the size; leave AV1 on if supported. — [V1, MikeTheTech, ~11:30-12:00](https://www.youtube.com/watch?v=QaNXP2-hXlY&t=690s)
- [asserted] In Artemis choose "prefer AV1" with an RTX 40/50-series host (30-series cannot encode AV1; use automatic or HEVC there). The creator says "AV1 decoding" for the GPU; he means encoding. — [V6, Azawraith Gaming, ~41:30-42:30](https://www.youtube.com/watch?v=LD8l6SccCZE&t=2490s)

**NVENC / encoder settings on the host**
- [asserted] NVENC performance preset: start at P1, try P2; only P1-P3 are recommended for streaming; higher presets add latency and the quality gain at 60 fps is hard to notice. — [V1, MikeTheTech, ~13:00-13:30](https://www.youtube.com/watch?v=QaNXP2-hXlY&t=780s) and [~15:30](https://www.youtube.com/watch?v=QaNXP2-hXlY&t=930s)
- [asserted] Two-pass mode: keep the default quarter-resolution; full resolution is slower but looks better; disabled is fastest but not recommended because bitrate overshoot can cause encoding errors. — [V1, ~14:00](https://www.youtube.com/watch?v=QaNXP2-hXlY&t=840s)
- [asserted] Spatial AQ moves bitrate to the areas that need it and helps mainly at low bitrates. — [V1, ~14:30](https://www.youtube.com/watch?v=QaNXP2-hXlY&t=870s)
- [asserted] The encoder preset is the main host-side quality setting on every vendor; higher = more quality and more encode latency; NVENC is the best of the three. — [V4, Cloud Gaming Couch, ~7:30](https://www.youtube.com/watch?v=BP6ZIJPTwU4&t=450s)
- [asserted] Do not raise "minimum CPU thread count" to the CPU's thread count; fewer threads is more efficient. FEC percentage can be raised for packet loss. — [V1, ~10:30-11:00](https://www.youtube.com/watch?v=QaNXP2-hXlY&t=630s)
- [asserted] AMD: keep "ultra low latency" usage, VBR-latency rate control, and leave VBAQ on (it pushes artifacts into noisy textures where they are less visible). — [V1, ~17:00-18:30](https://www.youtube.com/watch?v=QaNXP2-hXlY&t=1020s)
- [asserted] Sunshine note relayed by the creator: NVIDIA drivers may freeze with HAGS on, realtime priority and VRAM near full; disabling the realtime-priority option avoids it at a small capture-performance cost. — [V1, ~15:00](https://www.youtube.com/watch?v=QaNXP2-hXlY&t=900s)

**HDR**
- [asserted] Sunshine can switch HDR on the display at connect; if HDR streams with wrong colours, the "high contrast workaround for HDR" option usually fixes or masks it. — [V1, MikeTheTech, ~7:30](https://www.youtube.com/watch?v=QaNXP2-hXlY&t=450s)
- [asserted] HDR in Artemis needs both host and client to support it; not every setup will work. — [V3, Retro Game Corps, ~17:30](https://www.youtube.com/watch?v=RiggvcusEuA&t=1050s)
- [opinion] HDR is not good for streaming; every time he tried it the picture looked washed out, so he leaves it off. — [V6, Azawraith Gaming, ~34:30-35:00](https://www.youtube.com/watch?v=LD8l6SccCZE&t=2070s)

**Frame pacing on the Android client (Artemis)**
- [opinion] Use frame pacing "Balanced": with "prefer lowest latency" or "Warp Drive" camera pans look jittery; Balanced makes it feel like a local monitor, and decode time stays about 0.5-0.6 ms on a Snapdragon 8 Elite tablet. Shown on his device, no frame-time capture. — [V6, Azawraith Gaming, ~33:00-34:00](https://www.youtube.com/watch?v=LD8l6SccCZE&t=1980s)
- [opinion] Contradicting V6: on a Snapdragon 8 Gen 2 handheld, "Warp Drive" / "Warp Drive 2 (experimental)" work well and he uses Warp 2; default "prefer lowest latency" also works for most. — [V3, Retro Game Corps, ~17:00](https://www.youtube.com/watch?v=RiggvcusEuA&t=1020s)
- [asserted] Both V3 and V6 say to enable Artemis "ultra low latency (experimental)" on Snapdragon 8 Gen 2 / Gen 3 / Elite devices. — [V3, ~17:30](https://www.youtube.com/watch?v=RiggvcusEuA&t=1050s); [V6, ~34:00](https://www.youtube.com/watch?v=LD8l6SccCZE&t=2040s)
- [demonstrated] Stats overlay showed 1080p 120 fps with decode time under 1 ms on the Odin 2 Portal even with the host on Wi-Fi. — [V3, ~21:00](https://www.youtube.com/watch?v=RiggvcusEuA&t=1260s)

**Network and common mistakes**
- [asserted] The number-one fix for latency is wiring the host PC to the router by Ethernet. — [V3, Retro Game Corps, ~2:30](https://www.youtube.com/watch?v=RiggvcusEuA&t=150s)
- [asserted] Turn on the performance stats overlay when first tuning. — [V2, ~14:00](https://www.youtube.com/watch?v=H0jmqVIhwIA&t=840s); [V3, ~17:30](https://www.youtube.com/watch?v=RiggvcusEuA&t=1050s)
- [asserted] NVIDIA GameStream is gone; use Sunshine. Steam Link has no advantage over Moonlight and generally worse performance (opinion, no test shown). — [V5, Cloud Gaming Couch, ~0:30-1:30](https://www.youtube.com/watch?v=KFDwZKkWQyI&t=30s)
- [asserted] Encrypting the stream on LAN is usually not needed and can reduce performance. — [V1, ~10:00](https://www.youtube.com/watch?v=QaNXP2-hXlY&t=600s)

**What changed in 2025-2026 (as described in these videos)**
- Apollo/Artemis appeared as forks with per-client virtual displays (V2, January 2025); Sunshine 2025.122 gained display-device-ID selection, client-driven resolution/refresh/HDR switching and mode remapping (V1, V4); Artemis exposes frame-pacing modes ("Balanced", "Warp Drive", "Warp Drive 2") and a Snapdragon-only ultra-low-latency toggle (V3, V6); "Moonlight XR" (2026) is a Moonlight build for Quest/Pico that shows the stream on a movable virtual screen and converts it to 3D on the headset, with adjustable screen distance/width and bitrate (V18, [~1:30-2:30](https://www.youtube.com/watch?v=Kw0dCLmVdRo&t=90s)).

### Inferences
- The "match host display to client" advice is the single most consistent recommendation across the streaming videos (V1, V2, V3, V4, V6), and it is presented as a smoothness fix, not a sharpness fix.
- No creator in this set discusses 10-bit, 4:4:4 chroma, or NVENC AQ strength for Moonlight/Sunshine; picture-quality tuning on YouTube is essentially "pick codec, raise bitrate". The wide bitrate spread (40 vs 120 vs 300 Mbps) reflects different devices and no measurement.
- The frame-pacing disagreement (V6 "Balanced" vs V3 "Warp Drive 2") suggests the best Artemis pacing mode is device-specific; both are on Snapdragon devices but different generations and panels.

### Gaps
- Could not read (HTTP 429): "I Tested Moonlight vs Steam's New PyroWave Codec at 4K (Lag + Image Quality)" (Know Follow Through, 2026-10-16, https://www.youtube.com/watch?v=5VHGS0-4Fik) and "I Finally Tested Moonlight vs Steam Link" (same channel, https://www.youtube.com/watch?v=oBZTD1WFOzY). These look like the only measured comparisons in the search results.
- Could not read: "Sunshine Server Setup for Moonlight on Windows 11 | Version 2026.516.143833" (SaltyFireBuff Gaming & Tech, 2026-08-23, https://www.youtube.com/watch?v=CCKDu8BI_wY), so nothing here covers a 2026 Sunshine build first-hand; and "Redmagic Astra - Moonlight / Apollo - Refresh Rate Issues / Potential Fix" (John Strickland, https://www.youtube.com/watch?v=DzM5-WPo7nI).
- Arabic: "Apollo: هل هو البديل الأفضل لـ Moonlight و Sunshine؟" (Eddy04tv, https://www.youtube.com/watch?v=iieVMFMfKVc) was found but not fetched; the title may be an auto-translation.
- No video found that tests 10-bit vs 8-bit, HDR tone, 4:4:4, or NVENC AQ on Moonlight with evidence.

---

## 2. Virtual Desktop / Steam Link / ALVR settings: sharpening, SSW, bitrate, codec, Snapdragon Game Super Resolution, desktop resolution

### Takeaway
For VR streamers the consistent advice is: 10-bit HEVC or AV1 for picture quality (H.264 only for lowest latency or very high bitrate), sharpening at or below 75% because sharpening is applied after decoding and exaggerates compression artifacts, SSW/reprojection off if the PC can hold frame rate, and bitrate set by testing rather than maxed. One creator measured that maxing bitrate makes latency worse.

### Cited Findings

**Sharpening**
- [demonstrated in-headset, described verbally] Virtual Desktop's sharpening runs on the already-compressed image, so at 100% it "brings out the compression" and looks blocky; the creator advises turning sharpening off (default was 75%) when Snapdragon Game Super Resolution (SGSR) is on. — [V10, Virtually Real, ~0:30-1:30](https://www.youtube.com/watch?v=sZWx3iJ0Ets&t=30s) (2023, feature still present in the 2025 guide V7)
- [opinion] Set VD sharpening to 75%; 100% "can look a bit too sharp". — [V7, F4CEpa1m, ~19:30](https://www.youtube.com/watch?v=0MXLMEUHRc0&t=1140s)
- [asserted] Raising VD sharpness can help make small text easier to read on the virtual desktop screen. — [V13, SpookyFairy VR, ~3:00](https://www.youtube.com/watch?v=zgckHCs9nfg&t=180s)
- Conflict: V10 says turn sharpening off (with SGSR); V7 says 75%; V13 says raising it can help text. Different content: V10/V7 are 3D games, V13 is desktop text.

**Snapdragon Game Super Resolution (SGSR)**
- [demonstrated in-headset] SGSR visibly sharpens a "Medium" quality stream; it gives little gain if you already stream at High/Ultra, and too little detail to work with at "Potato"; best used by lower-end PCs. — [V10, Virtually Real, ~0:00-1:30](https://www.youtube.com/watch?v=sZWx3iJ0Ets)
- [asserted] SGSR upscales on the headset at very low performance cost; on Quest 2/Pro it only works up to "High" and 90 fps, on Quest 3 at any setting; do not stack it with in-game upscaling (he enables SGSR and disables in-game upscalers). — [V7, F4CEpa1m, ~20:00-21:00](https://www.youtube.com/watch?v=0MXLMEUHRc0&t=1200s)

**Codec and 10-bit**
- [asserted] VD codec choice: H.264 / H.264+ for lowest latency in fast games; HEVC or HEVC 10-bit for better visuals in slower games; AV1 10-bit on Quest 3 with RTX 40 / RX 7000 or newer, somewhat heavier but best looking. Quality order at equal bitrate is the reverse of latency order. — [V7, F4CEpa1m, ~5:30-6:30](https://www.youtube.com/watch?v=0MXLMEUHRc0&t=330s)
- [opinion from use] H.264 shows noticeable colour banding and compression compared with AV1 10-bit, which is his preferred codec. — [V9, Mac In VR, ~8:30](https://www.youtube.com/watch?v=zUaxhvonsAg&t=510s)
- [asserted] AV1 on Quest 3 gives increased clarity; H.264 is still worth trying because it allows higher bitrate. — [V11, VR ManCave, ~4:00-4:30](https://www.youtube.com/watch?v=6TtpmkTgvEk&t=240s) (2023)
- [asserted] Steam Link uses HEVC with optional foveated encoding and 25-350 Mbps manual bandwidth; VD supports H.264/HEVC/AV1, 120 Hz, sharpening and gamma; ALVR exposes bitrate, codec, encoder presets, foveation and colour depth and also works wired over USB. — [V12, Fix My Oculus, ~2:30-5:30](https://www.youtube.com/watch?v=wzRFJl2Dem0&t=150s)

**Encoder options in the VD streamer**
- [asserted, relaying the VD Discord] Adaptive quantization improves quality, reduces smear in fast scenes and artifacts in dark scenes with no frame-rate cost; leave on for RTX 30-series and newer, turn off if it hurts the image on some 20-series cards. Two-pass encoding can improve quality but costs GPU headroom. — [V7, F4CEpa1m, ~6:30-7:00](https://www.youtube.com/watch?v=0MXLMEUHRc0&t=390s)
- [opinion] Leave "automatically adjust bitrate" off. — [V7, ~9:30](https://www.youtube.com/watch?v=0MXLMEUHRc0&t=570s). [demonstrated] V9 compared it on vs off at 200 Mbps AV1 10-bit and saw no difference in network latency. — [V9, ~3:00-3:30](https://www.youtube.com/watch?v=zUaxhvonsAg&t=180s)

**Bitrate**
- [asserted] VD maximum bitrate depends on codec: AV1 up to 200 Mbps, H.264+ up to 500 Mbps. — [V7, ~17:00-17:30](https://www.youtube.com/watch?v=0MXLMEUHRc0&t=1020s). V9 (April 2026) shows H.264+ at a 600 Mbps maximum — [V9, ~2:30](https://www.youtube.com/watch?v=zUaxhvonsAg&t=150s). The difference is probably a version change (unverified).
- [opinion] Start at half the codec's maximum bitrate, raise until problems appear, then back off slightly; if only a very low bitrate is playable, lower resolution/frame rate instead. — [V7, ~19:00](https://www.youtube.com/watch?v=0MXLMEUHRc0&t=1140s)
- [demonstrated, overlay numbers] H.264+ at 600 Mbps made total stream latency swing between about 42 ms and over 90 ms with encode and network latency in the warning zone. — [V9, Mac In VR, ~2:30-3:00](https://www.youtube.com/watch?v=zUaxhvonsAg&t=150s)
- [demonstrated] With AV1 10-bit and foveated streaming on, lowering bitrate reduced latency down to about 100 Mbps, below which there was little gain; at 10 Mbps he judged the picture unacceptable. His sweet spot: 100-120 Mbps for a stable 26-27 ms. He notes a slight loss of fidelity. Tested on a dedicated Wi-Fi 7 router. (The spoken bitrate steps are garbled in the auto-captions, check.) — [V9, ~4:00-4:30](https://www.youtube.com/watch?v=zUaxhvonsAg&t=240s)
- [demonstrated] VD foveated streaming at 200 Mbps cut overall latency from about 39 ms to 31 ms; fovea size (15-35%) changed latency by about 1 ms; he keeps the default 25%, where he says the foveated region is hard to notice. — [V9, ~3:30-6:00](https://www.youtube.com/watch?v=zUaxhvonsAg&t=210s)
- [opinion] Steam Link bitrate: no lower than 75 Mbps for sims, 100-150 Mbps for online shooters, up to 350 Mbps for slow story games. — [V8, F4CEpa1m, ~13:30](https://www.youtube.com/watch?v=XOirjFoBGeo&t=810s)
- [asserted] Steam Link "encoded video size": higher means a better image and less visible foveated-encoding falloff outside the centre (which he strongly dislikes), at more PC load and latency. — [V8, ~14:00](https://www.youtube.com/watch?v=XOirjFoBGeo&t=840s)
- [asserted] With a dedicated router, set VD bitrate to maximum for the crispest image. — [V11, VR ManCave, ~5:30](https://www.youtube.com/watch?v=6TtpmkTgvEk&t=330s) (2023). This conflicts with V9's 2026 measurement that maximum bitrate hurts latency.
- [asserted] For wired Link, the Oculus Debug Tool's encode bitrate (default about 200; he runs four times that, in 100-step increases) gives a marked increase in clarity; also sets distortion curvature low and dynamic bitrate off. — [V11, ~2:30-3:00](https://www.youtube.com/watch?v=6TtpmkTgvEk&t=150s) (2023; auto-caption numbers, check)

**SSW / reprojection, frame rate, buffering**
- [opinion] SSW (synchronous spacewarp) halves the PC's frame rate and synthesises the rest on the headset; it produces artifacts and never feels right to him, so he always disables it. — [V7, F4CEpa1m, ~19:30-20:00](https://www.youtube.com/watch?v=0MXLMEUHRc0&t=1170s). V9's lowest-latency recipe also has spacewarp off — [V9, ~8:00](https://www.youtube.com/watch?v=zUaxhvonsAg&t=480s)
- [demonstrated on his setup] VD video buffering added up to 15 ms latency in modded Skyrim VR; start with it off, use only as a last resort for stutter. — [V7, ~21:00](https://www.youtube.com/watch?v=0MXLMEUHRc0&t=1260s)
- [opinion] 72-80 fps is enough for slow games; 90 for fast ones; 120 only for light games. — [V7, ~16:00-16:30](https://www.youtube.com/watch?v=0MXLMEUHRc0&t=960s)
- [asserted] Latency targets on VD's overlay: under 50 ms acceptable and about 40 ms good for slow games; 30 ms or less for fast games. The overlay colours show which stage (game, encode, network, decode) is the bottleneck; orange on decode means too much data for the headset, so lower resolution, fps or bitrate. — [V7, ~25:00-26:30](https://www.youtube.com/watch?v=0MXLMEUHRc0&t=1500s)

**Other picture settings**
- [opinion] VD gamma 1.0; he enables "increase color vibrance" and "increase video nominal range" for darker darks and brighter brights. — [V7, ~19:30 and ~21:00](https://www.youtube.com/watch?v=0MXLMEUHRc0&t=1260s)
- [asserted] Keep SteamVR render resolution at 100% when using VD, because VD sets the resolution and SteamVR's multiplier stacks on top. — [V7, ~22:00](https://www.youtube.com/watch?v=0MXLMEUHRc0&t=1320s)
- [asserted] In Steam Link/SteamVR, the per-application resolution of "SteamVR Home" (and the media player) can be raised to make desktop windows and video sharper. — [V8, ~10:30](https://www.youtube.com/watch?v=XOirjFoBGeo&t=630s)

**Virtual desktop/monitor resolution and text**
- [demonstrated, subjective] On Quest 3 with a VD virtual screen about 60 cm (2 ft) away, 8 px text was not readable and 12 px was readable but not crisp; enlarging and curving the virtual screen helped more than anything else; raising desktop bitrate to maximum and changing frame rate over a wired link made no significant difference to text clarity. — [V13, SpookyFairy VR, ~1:30-2:30 and ~8:00](https://www.youtube.com/watch?v=zgckHCs9nfg&t=90s)
- [asserted] VD's desktop (non-game) streaming runs at a much lower bitrate than its game streaming, which is why local playback apps (Skybox, 4XVR) are his pick for best movie quality. — [V17, VR ManCave, ~4:30-5:30 and ~7:30](https://www.youtube.com/watch?v=sKfH2HO3wK0&t=270s)

### Inferences
- The reason creators turn sharpening down is specific and consistent: it is a post-decode filter, so it amplifies block edges and ringing from compression as much as real detail (V10 demonstrated; V7 agrees by preference). The one "turn it up" case is static, high-contrast desktop text (V13).
- V9's measurements argue against "max the bitrate": beyond roughly 100-120 Mbps (AV1 10-bit, Quest 3, Wi-Fi 7) more bits bought latency and instability rather than visible quality. This is one creator's setup and two games.
- For a virtual flat screen, V13 suggests perceived text clarity is limited by headset pixels per degree and screen angular size, not by bitrate, once bitrate is reasonable.

### Gaps
- No creator in the set gave a recommended desktop or virtual-monitor resolution in pixels for Quest-class headsets.
- Could not read (HTTP 429): "Comprehensive ALVR Setup For ANY Device" (Issac Dowling, 2023-08-12, https://www.youtube.com/watch?v=_5k9htTdpuI) and "How to optimize Virtual Desktop | Hardware + Best Settings Guide" (The Construct, 2024-03-10, https://www.youtube.com/watch?v=aN15KL0Ii-s). ALVR is therefore covered only by V12's overview; no ALVR settings values were obtained.
- Bigscreen and Skybox: only V17's general ranking was found; no video with their picture settings (resolution, sharpening, supersampling) was read.
- Arabic: "كيف ترفع من جودة الرسومات في نظارات ميتا كوست | Quest 3" (VR REALM, https://www.youtube.com/watch?v=4fPIEgtBhX4) was found but not fetched.

---

## 3. Phone VR / Cardboard / Gear VR: screen-door effect, lens upgrades, diffuser mods, phone display settings, video resolution for a ~1440p panel

### Takeaway
I found no reliable recent YouTube source on this. The 2024-2025 Cardboard and Gear VR videos that could be read are nostalgia/entertainment pieces with no claims about screen-door effect, lenses, diffusers, phone display settings or recommended video resolution. The only transferable material is general VR optics advice from Quest-focused videos.

### Cited Findings
- [demonstrated, incidental] On Gear VR the picture was blurry until the focus wheel was adjusted; that is the only clarity remark in the video. It also shows a Note 9 did not fit his Gear VR while a Galaxy S10e did, and that the Oculus software is unsupported and needs a workaround. — [jOhnZoid, "the Samsung Gear VR in 2024? Let's Explore!", ~2:00-4:30](https://www.youtube.com/watch?v=REobLsRVNsY&t=240s)
- The 2025 Cardboard video contains no picture-quality claims. — [Wrighteous, "I Tried Google Cardboard AGAIN So You Don't Have To..."](https://www.youtube.com/watch?v=_8CuxzQA0K4)
- [asserted, optometrist] General VR optics that also apply to phone headsets: the eye's pupil must sit on the lens's optical centre (IPD and vertical alignment) or you get blur, eye strain and a prismatic shift; bringing lenses closer to the eye can help clarity; smudges on a lens look large; VR lenses place focus at a fixed distance (about 1.2-1.3 m on Quest 2, by his account), so people who need distance glasses need them in VR. — [V14, Dr. EyeGuy, ~2:30-5:30 and ~11:00-13:00](https://www.youtube.com/watch?v=R3kr8FM2-HY&t=150s)
- [asserted] Quest 3 is about 25 pixels per degree vs about 20 on Quest 2, while the eye resolves about 60, so fine distant detail still looks pixelated on any current headset. — [V14, ~0:00 and ~1:30](https://www.youtube.com/watch?v=R3kr8FM2-HY&t=90s)
- [asserted] Fresnel lenses have a small sweet spot and ring artifacts (god rays); pancake lenses widen the sweet spot but add internal reflections and edge chromatic aberration. — [V14, ~0:30-1:30](https://www.youtube.com/watch?v=R3kr8FM2-HY&t=30s)

### Inferences
- The absence itself is a finding: current creators do not cover phone-in-headset picture tuning; advice for it has to be borrowed from standalone-headset and streaming videos or from non-YouTube sources.
- By V14's figures a phone headset with a ~1440p-wide panel split across two eyes over a wide field of view will be well under 20 pixels per degree; this is my arithmetic direction from his numbers, not a claim made in any video.

### Gaps
- No video read on: diffuser/anti-SDE film mods, lens swaps for Cardboard-style viewers, best phone display settings (resolution mode, brightness, colour mode), or recommended source/video resolution for a ~1440p panel.
- Found but not fetched before the rate limit: "Crystal Clear VR: Testing 25mm Bi-Convex Lenses for Google Cardboard" (Tech Compass Pro, https://www.youtube.com/watch?v=hYTH5Ip7qfM, 51 views, low authority), "DIY Biconvex Lens For Google Cardboard" (CREATOR GUY, https://www.youtube.com/watch?v=KKrdVBr01JE), "Destek V5 VR Headset - 1 Month Later" (Juan Camacho, https://www.youtube.com/watch?v=IMz53LON_do), "Which Matters More in VR – PPD or FOV?" (Lordsi VR, https://www.youtube.com/watch?v=VwH8T9DG3BA).
- Arabic phone-VR search results were short how-to clips (e.g. a Shinecon focus-adjustment video, https://www.youtube.com/watch?v=Jdrrluw_MpE) and were not read.

---

## 4. PenTile versus RGB-stripe in VR, screen-door effect, and what content looks worst

### Takeaway
No video that could be read compares PenTile and RGB-stripe subpixel layouts directly. What the readable videos do establish: screen-door effect (visible gaps between pixels) is distinct from mura (fixed per-pixel brightness noise on OLED), mura is worst in dark, near-black scenes, and high-contrast text and edges are the content that most exposes motion smear on OLED/micro-OLED headsets.

### Cited Findings
- [asserted, explainer] Screen-door effect is seeing the dark gaps between pixels; fixed-pattern grain on modern OLED headsets is mura, a different thing. In his words: "The fixed pattern grain ... is not the screen door effect." — [V15, Immersed Robot, "Mura Explained", ~1:00](https://www.youtube.com/watch?v=M8sCcUuZAEU&t=60s)
- [asserted] Mura comes from per-subpixel manufacturing variation in OLEDs and is most visible in dim, near-black content (space sims, horror, loading screens) because low drive voltages exaggerate the variation; bright content hides it. It stays fixed to the panel while the scene moves, which he likens to looking through gauze. — [V15, ~2:00-3:00](https://www.youtube.com/watch?v=M8sCcUuZAEU&t=120s)
- [asserted] Makers reduce mura with a per-panel factory calibration map applied in real time; this is imperfect even on PSVR2's OLED and harder on micro-OLED. LCDs are more uniform because of a shared backlight, though backlight unevenness (also called mura) is reported on Quest 3. — [V15, ~3:30-5:00](https://www.youtube.com/watch?v=M8sCcUuZAEU&t=210s)
- [asserted] Moving to micro-OLED removes screen-door effect but makes mura harder to solve. — [V15, ~4:30](https://www.youtube.com/watch?v=M8sCcUuZAEU&t=270s)
- [asserted, creator's own use of a micro-OLED headset] On head movement, text and high-contrast edges smear and ghost; bright objects on dark backgrounds trail. He attributes this to persistence (how long each frame stays lit), not pixel response, and says pancake lenses waste so much light that micro-OLED cannot easily strobe brightly enough for low persistence; LCD can flash its backlight briefly. — [V16, OCmodshop, ~0:30, ~4:30-5:30, ~10:00-11:30](https://www.youtube.com/watch?v=iaEJfDHwxYk&t=270s)
- [opinion] For virtual-monitor/productivity use, legibility of text during head movement matters as much as resolution. — [V16, ~9:00 and ~12:00](https://www.youtube.com/watch?v=iaEJfDHwxYk&t=540s)

### Inferences
- For a phone OLED in a headset, V15's mechanism implies dark, low-brightness scenes will show the most fixed-pattern grain, and V16's implies white text on black will show the most motion smear unless the panel runs low persistence. Neither video tested a phone panel; this is an extrapolation.
- None of the sources supports or refutes the common claim that PenTile looks worse than RGB-stripe at equal nominal resolution; that claim needs a non-YouTube source.

### Gaps
- Could not read (HTTP 429, three attempts): "valve picked the panel everyone already hates" (Joshua Keith, 2026-08-26, https://www.youtube.com/watch?v=r-g6XJAczaA), which from its title appeared in the PenTile search and may discuss panel/subpixel choice; unverified.
- Not fetched: "Why Not OLED? Are VR Headsets going BACKWARDS?" (Jacobfov, https://www.youtube.com/watch?v=7L644NA2Eks); "Pimax 8K vs 5K+ Through The Lens Comparison - Pixel Matrix Analysis" (SweViver, older, https://www.youtube.com/watch?v=6thHO5tR6VI), which is the only search hit that looked like a through-the-lens subpixel comparison.
- The query "pentile vs RGB stripe VR" returned zero results on YouTube search; no video naming which content looks worst on PenTile specifically was found.
