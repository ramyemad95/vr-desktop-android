# Sunshine / Apollo to Moonlight (Android, Artemis): picture-quality settings and real-user reports, 2025-2026

Research date: 2026-11-12. Reference setup: laptop RTX 2070 (Turing NVENC), Sunshine NVENC HEVC P4 + spatial AQ, 3840x2160 @ 60 fps, 150 Mbps, native 4K 120 Hz SDR desktop, FC 27 (EA anti-cheat) and Forza; client Galaxy S22 Ultra (Snapdragon 8 Gen 1, Qualcomm HEVC decoder), local Wi-Fi/USB.

Source-quality legend used below:
- **[read]** = I opened the primary page (GitHub issue/PR/README/wiki via the public GitHub API, or the official docs page).
- **[snippet]** = taken only from a web-search result summary; the page itself was not opened (Reddit mirrors returned HTTP 403 and Reddit was not reachable by a permitted route). Treat as unverified.

Housekeeping fact: the Sunshine repository now resolves to `SunshineStream/Sunshine` (the GitHub API returns that as the full name for `LizardByte/Sunshine`, and current release notes link there) — [GitHub API repo record](https://github.com/LizardByte/Sunshine) [read]. Old `LizardByte/Sunshine` URLs redirect.

---

## 1. What do users and maintainers recommend for bitrate, codec, HDR/SDR, 10-bit, colour range, 4:4:4, resolution, virtual displays, frame pacing and NVENC options?

### Takeaway
The official Sunshine defaults are tuned for latency (preset P1, quarter-res two-pass, spatial AQ off, VBV increase 0); the documented quality levers are a higher preset, spatial AQ, full-res two-pass and `nvenc_vbv_increase`, each with a stated latency/packet-loss cost. Apollo's maintainer recommends SDR over HDR for streaming, a frame limiter instead of V-sync, and YUV 4:4:4 is not available on Android clients.

### Cited Findings

**NVENC options (official Sunshine docs, current "latest")**
- `nvenc_preset`: default 1 (P1, fastest), range 1-7; higher presets improve compression at a given bitrate but add encode latency; docs suggest changing it mainly when the network or decoder is the bottleneck — [Sunshine configuration docs](https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2configuration.html) [read]
- `nvenc_twopass`: default `quarter_res`; choices `disabled`, `quarter_res`, `full_res`; two-pass improves motion detection and bitrate distribution; disabling it "can lead to occasional bitrate overshoot and subsequent packet loss" — [Sunshine configuration docs](https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2configuration.html) [read]
- `nvenc_spatial_aq`: default disabled; assigns higher QP to flat regions; recommended at lower bitrates; "Enabling this option may reduce performance" — [Sunshine configuration docs](https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2configuration.html) [read]
- `nvenc_vbv_increase`: default 0, range 0-400; relaxes the single-frame VBV/HRD limit (acts like low-latency VBR); "Can lead to network packet loss"; 400 allows frames up to 5x normal size — [Sunshine configuration docs](https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2configuration.html) [read]
- `nvenc_realtime_hags`: default enabled; realtime GPU priority under HAGS; NVIDIA drivers may freeze in the encoder when VRAM is near full; disabling avoids the freeze but can reduce capture performance under heavy GPU load — [Sunshine configuration docs](https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2configuration.html) [read]
- `nvenc_latency_over_power`: default enabled; disabling "not recommended" (can significantly increase encode latency) — [Sunshine configuration docs](https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2configuration.html) [read]
- The docs page exposes no AQ-strength, reference-frame-count or encoder colour-range/CSC option in the NVENC section (none were found on the page) — [Sunshine configuration docs](https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2configuration.html) [read]

**Bitrate**
- `max_bitrate` default 0 = use what Moonlight requests; `fec_percentage` default 20 (FEC overhead comes on top of video); `minimum_fps_target` default 0 = half the stream fps (Sunshine sends duplicate frames on static content) — [Sunshine configuration docs](https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2configuration.html) [read]
- A user in a Sunshine issue (Jan 2024) settled on "4k@60 fps HEVC at 150 Mbps", frame pacing "balanced + fps limit", host 4K60 V-sync on, for sim racing — [Sunshine #1998](https://github.com/SunshineStream/Sunshine/issues/1998) [read] (older, 2024)
- Community reports: 4K60 "needs at least 50 Mb/s", Moonlight auto-sets 80 Mbps for 4K60; a thread on 4K HDR HEVC at 500 Mbps notes Sunshine targets a bitrate, not a quality level, and suggests `nvenc_vbv_increase` = 400 so complex frames can use more; dense foliage/fog still showed artefacts — [r/MoonlightStreaming thread (mirror)](https://axebps-redlib.hf.space/r/MoonlightStreaming/comments/1jk2636/moonlightsunshine_doesnt_seem_to_do_well_with_ac) [snippet, unverified, ~Mar 2025]
- Dated guide (Dec 2022, RTX 2070 Super host, Shield client): 4K, 120 Mbps, CBR, FEC lowered to 5 on LAN, frame pacing "Prefer lowest latency" — [JasSuri gist](https://gist.github.com/JasSuri/fc14ff76daa0fc788a9754ddc8c8fe3e) [read] (old: 2022; its NVENC preset names `llhp`/`ll` predate the P1-P7 scheme)

**Codec**
- `hevc_mode` 0 = advertise by encoder capability (recommended); 2 = Main only; 3 = Main + Main10 (HDR). `av1_mode` likewise — [Sunshine configuration docs](https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2configuration.html) [read]
- AV1 is not an option on this host: Sunshine advertises codecs by encoder capability, and the README points to NVIDIA's support matrix for which cards encode what — [Apollo README](https://github.com/ClassicOldSong/Apollo) [read]. (That Turing has no AV1 encode is general knowledge, not confirmed from a page opened in this session.)
- `nvenc_h264_cavlc`: CAVLC needs ~10% more bitrate than CABAC for equal quality — [Sunshine configuration docs](https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2configuration.html) [read]
- Generic codec-efficiency figure (not streaming-specific): HEVC ~44% less bitrate than H.264 at equal VMAF; AV1 ~20% less than HEVC — [Fora Soft codec comparison](https://www.forasoft.com/learn/video-quality/articles-vqm/codec-comparison-real-content) [snippet, unverified]

**HDR vs SDR, 10-bit**
- Apollo maintainer (README, current): "Enabling HDR is generally not recommended with ANY streaming solutions at this moment"; "SDR actually provides much more stable color accuracy"; "Whether HDR streaming looks good, it depends completely on your client"; Apple devices "can be trusted", others "your luck depends" — [Apollo README, About HDR](https://github.com/ClassicOldSong/Apollo#about-hdr) [read]
- Maintainer on Android specifically (Aug 2025): "Most Android devices uses HLG which makes precisely calibrating HDR impossible"; SDR content looking less vivid inside an HDR stream is "very normal on Android devices. You have to live with it" — [Artemis #372](https://github.com/ClassicOldSong/moonlight-android/issues/372) [read]
- Sunshine logs "BT.2020 SDR colorspace expects 10-bit color depth, falling back to Rec. 709" for an 8-bit SDR session, i.e. SDR sessions are 8-bit Rec. 709 unless 10-bit is negotiated — log quoted in [Sunshine PR #5106](https://github.com/SunshineStream/Sunshine/pull/5106) [read]
- `dd_hdr_option` auto matches the client's HDR request; `dd_wa_hdr_toggle_delay` (0-3000 ms, suggested ~500) toggles HDR off/on as a workaround for virtual-display HDR colour issues, at the cost of stream start time — [Sunshine configuration docs](https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2configuration.html) [read]

**Colour range (full vs limited)**
- One open moonlight-android report says setting full-range colour in the client did not change its colour-degradation symptom — [moonlight-android #1357](https://github.com/moonlight-stream/moonlight-android/issues/1357) [read]. No maintainer recommendation for full vs limited was found.

**YUV 4:4:4**
- Sunshine's 4:4:4 is experimental, Windows hosts only, aimed at text clarity for remote desktop (v0.24-era changelog) — [Sunshine changelog mirror](https://sunshinestream.readthedocs.io/en/latest/history/changelog.html) [snippet, unverified]
- moonlight-qt lists "YUV 4:4:4 support (Sunshine only)"; a LizardByte discussion says recombined 4:4:4 decoding "won't be coming to moonlight-android any time soon" — [moonlight-qt README](https://cdn.jsdelivr.net/gh/moonlight-stream/moonlight-qt@master/README.md), [LizardByte discussion #220](https://github.com/orgs/LizardByte/discussions/220) [snippet, unverified; date of the discussion not checked]

**Stream resolution vs display resolution, virtual displays**
- Sunshine `dd_resolution_option` auto = switch the host display to the client's requested resolution (needs "Optimize game settings" in Moonlight); `dd_refresh_rate_option` auto = switch to the client's requested fps; `dd_mode_remapping` remaps requested modes — [Sunshine configuration docs](https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2configuration.html) [read]
- Apollo + its built-in virtual display (SudoVDA): the host "always matches the client"; physical monitor mode becomes irrelevant (user explanation, Jan 2026) — [Apollo #1329](https://github.com/ClassicOldSong/Apollo/issues/1329) [read]
- Virtual displays are a recurring stutter source; several users report stutter on SudoVDA that disappears on the physical display; maintainer calls a dummy HDMI plug "a workaround", not a fix — [Apollo #1448](https://github.com/ClassicOldSong/Apollo/issues/1448), [Apollo #899](https://github.com/ClassicOldSong/Apollo/issues/899) [read]
- Artemis adds custom resolutions, custom bitrates, Fit/Fill/Stretch scale mode, pan/zoom, and virtual-display integration with Apollo — [Artemis README](https://github.com/ClassicOldSong/moonlight-android) [read]

**Frame pacing / V-sync**
- Apollo wiki: a frame limiter (RTSS, Special K) "is always better than VSync. Disable VSync when you can limit your framerate"; when host and client rates match exactly, client V-sync does not raise decode latency — [Apollo Stuttering Clinic](https://github.com/ClassicOldSong/Apollo/wiki/Stuttering-Clinic) [read]
- With Special K you must also force V-sync off in its swap-chain settings (maintainer, Jun 2025) — [Apollo #899](https://github.com/ClassicOldSong/Apollo/issues/899) [read]
- Contradicting experience: in Sunshine #1998 (2024) any frame limiter (in-game, NVCP, RTSS) caused micro-stutter and the reporter settled on host V-sync on; another user needed either a 90 fps cap or in-game V-sync at 60 Hz — [Sunshine #1998](https://github.com/SunshineStream/Sunshine/issues/1998) [read]
- 2022 guide: with NVIDIA Fast Sync you must cap fps to the client rate or the stream stutters; RTSS suggested — [JasSuri gist](https://gist.github.com/JasSuri/fc14ff76daa0fc788a9754ddc8c8fe3e) [read] (old)

### Inferences
- The reference setup (P4, spatial AQ on, 150 Mbps at 4K60) is already well above the documented defaults and above what the few visible community reports use for 4K60 (80-150 Mbps). The remaining documented quality levers are `nvenc_twopass = full_res` and a non-zero `nvenc_vbv_increase`; both trade against latency/packet loss, the latter mostly a Wi-Fi concern rather than USB.
- Since 4:4:4 is not decodable in moonlight-android and AV1 is not encodable on a 2070, HEVC 4:2:0 is the only realistic codec path; chroma resolution is therefore half the luma resolution regardless of bitrate.
- For an SDR desktop, 8-bit Rec. 709 is what Sunshine sends; 10-bit only comes with the HDR path, which the Apollo maintainer advises against on Android.

### Gaps
- No maintainer statement found on an ideal 4K60 HEVC bitrate, nor any 2025-2026 measured comparison (VMAF/PSNR) of P1 vs P4 vs P7 or two-pass modes in Sunshine.
- No source found for NVENC reference-frame count or AQ strength being user-configurable in Sunshine/Apollo.
- No authoritative statement found on full vs limited range for moonlight-android in 2025-2026; the Moonlight wiki/FAQ was not reached.
- Reddit (r/MoonlightStreaming, r/cloudygamer) could not be read directly (mirrors returned HTTP 403); all Reddit-derived items are search snippets only.

---

## 2. What recent picture-quality problems are reported, and what fixed them?

### Takeaway
The dominant 2025-2026 complaints are HDR washout/black crush on Android clients (no general fix; SDR recommended), capture reinitialisation hiccups caused by displays or cables probing the GPU, virtual-display stutter (worse on AMD CPUs), an NVENC freeze under HAGS with near-full VRAM, and Samsung/One UI decoder and refresh-rate quirks.

### Cited Findings

**HDR washed out / black crush**
- Apollo #205 (Jan-Mar 2025): HDR stream "completely washed out" on all clients with SudoVDA in a Hyper-V GPU-partitioned VM; maintainer suspected client-side HDR-to-SDR mapping and said cross-client inconsistency is why HDR is not recommended; no single root cause identified — [Apollo #205](https://github.com/ClassicOldSong/Apollo/issues/205) [read]
- Artemis #372 (Aug 2025 - Aug 2026): HDR loses saturation on a RedMagic Astra with both Apollo/Artemis and Sunshine/Moonlight; enabling "Disable HW overlays" in Android developer options restored saturation but produced black crush; maintainer reproduced the same on a Lenovo Y700 Gen4; an Android 16 update later fixed colours with slight black crush remaining — [Artemis #372](https://github.com/ClassicOldSong/moonlight-android/issues/372) [read]
- Virtual-display peak brightness (EDID) does not change the streamed result according to the maintainer: SudoVDA hardcoded ~300 nits, MTT's driver 1000+; Windows HDR Calibration on Android clients tends to top out near 2200 nits; a user reported calibration did remove blown highlights/saturation issues — [Apollo #164](https://github.com/ClassicOldSong/Apollo/issues/164) [read] (Dec 2024)
- Upstream moonlight-android has an open report "Your PC GPU does not support streaming HDR. The stream will be SDR." (updated Mar 2025) — [moonlight-android #1456](https://github.com/moonlight-stream/moonlight-android/issues/1456) [title only, not opened]

**Blown-out whites in SDR (FP16 capture)**
- On some Windows 11 hosts an SDR desktop is captured as `R16G16B16A16_FLOAT` and the stream shows clipped whites; Sunshine merged an opt-in option `fp16_sdr_gamma_encoded` (default disabled) on 2026-10-09 to fix it. Reported host: Win11 25H2, RTX 5090, VDD-based virtual display advertising BT.2020 colourimetry, HEVC NVENC 8-bit Rec. 709 — [Sunshine PR #5106](https://github.com/SunshineStream/Sunshine/pull/5106) [read]. Triggers described as ACM (Auto Colour Management) and HDR-class virtual-display EDIDs — same PR, via search summary [snippet for the trigger list].

**Colour degradation on static scenes**
- Colours corrupt/whiten on still images at resolutions above 1080p with HEVC and AV1, on TCL TVs with AMD and NVIDIA hosts; bitrate, Windows colour format and Moonlight full-range setting made no difference; still open, last confirmed Mar 2026 — [moonlight-android #1357](https://github.com/moonlight-stream/moonlight-android/issues/1357) [read]

**Capture restarts / periodic hiccups**
- Apollo wiki: "Some monitors/fiber HDMI cables/HDMI switches probe connections actively, they'll cause the GPU to reinitialize context every time they do so, and stream will be reinitialized as well causing a big hiccup". Fixes: keep the problematic display on during the stream, power it off physically, pull its cable at the GPU side, or disable the monitor's "Auto Detect Input" — [Apollo Stuttering Clinic](https://github.com/ClassicOldSong/Apollo/wiki/Stuttering-Clinic) [read]
- Sunshine handles loss of the DXGI desktop-duplication object by reinitialising capture — [Sunshine PR #654 (mirror)](https://github.meowingcats01.workers.dev/LizardByte/Sunshine/pull/654) [snippet, unverified]
- Background software can cause severe stutter on a virtual-display stream (maintainer's example: Apple Music UWP); vendor "game optimiser" tools such as Lenovo Vantage are listed as a cause — [Apollo Stuttering Clinic](https://github.com/ClassicOldSong/Apollo/wiki/Stuttering-Clinic) [read]
- NVIDIA overlay suggested as a suspect by the maintainer (did not help the reporters) — [Apollo #956](https://github.com/ClassicOldSong/Apollo/issues/956) [read]

**NVENC freezes**
- With HAGS on, realtime NVENC priority and VRAM ~90% full produced `NvEnc: frame N encode wait timeout` freezes; setting `nvenc_realtime_hags` to disabled fixed it (Oct 2026 report) — [Sunshine #5956](https://github.com/SunshineStream/Sunshine/issues/5956) [read]; matches the documented caveat — [Sunshine configuration docs](https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2configuration.html) [read]

**Version regressions**
- Apollo 0.4.3 (Jul 2025) introduced heavy stutter, concluded by the maintainer to affect mainly AMD-CPU hosts; rolling back to v0.3.7-hotfix.1 or updating to v0.4.5 fixed it for several users — [Apollo #956](https://github.com/ClassicOldSong/Apollo/issues/956) [read]
- Sunshine v2025.628.4510 changed minimum frame time to be based strictly on client fps, causing frame doubling/judder when host fps was below stream fps; fixed in a later pre-release by the configurable minimum fps target (a user set it to 10) — [Sunshine #4055](https://github.com/SunshineStream/Sunshine/issues/4055) [read]

**Samsung / Android decoder quirks**
- One UI 8 regression (Dec 2025): AV1 decode time on Samsung Snapdragon phones rose from 2-4 ms to 10-14 ms; enabling Artemis's Snapdragon "Ultra Low Latency" mode brought it under 1 ms; Game Booster stopped detecting Moonlight — [Artemis #488](https://github.com/ClassicOldSong/moonlight-android/issues/488) [read] (user reports, no maintainer confirmation)
- Artemis's ultra-low-latency mode is advertised for Snapdragon 8 Gen 2 / 8(s) Gen 3 / 8 Elite — [Artemis release notes via newreleases.io](https://newreleases.io/project/github/ClassicOldSong/moonlight-android/release/v12.1.250415) [snippet, unverified]. Snapdragon 8 Gen 1 is not in that list.
- Galaxy Z Fold7 (Snapdragon 8 Elite): ultra-low-latency mode makes fps float 108-120; disabling it costs ~6 ms but is smoother; the panel reports 117.665 or 120.650 Hz at different times — [Artemis #473](https://github.com/ClassicOldSong/moonlight-android/issues/473) [read]
- Galaxy S24 Ultra: Artemis "Warp 2" frame pacing left the app at 60 fps until the screen was tapped; maintainer: "Samsung modified way too much things in their system and they're not controllable from user installed apps"; the user preferred Balanced pacing — [Artemis #376](https://github.com/ClassicOldSong/moonlight-android/issues/376) [read]
- Exynos Samsung devices (S22 Ultra Exynos, S24+, Tab S9 FE+) use `c2.exynos.hevc.decoder` with 10-20+ ms decode times versus ~1-4 ms in Parsec on the same phone — [moonlight-android #1285](https://github.com/moonlight-stream/moonlight-android/issues/1285), [Sunshine #5956](https://github.com/SunshineStream/Sunshine/issues/5956) [read]. This is Exynos, not the Snapdragon variant.
- Samsung DeX: "Catastrophic latency" report (Jul 2025) — [Artemis #286](https://github.com/ClassicOldSong/moonlight-android/issues/286) [title only, not opened]

**Wi-Fi-caused render drops**
- Rendering fps a few frames below incoming fps at 1440p120 on Wi-Fi 6 disappeared entirely on Ethernet (Jun 2025) — [Artemis #246](https://github.com/ClassicOldSong/moonlight-android/issues/246) [read]
- Wi-Fi adapter power saving causes "slow network" warnings and stutter-on-motion — [Apollo Stuttering Clinic](https://github.com/ClassicOldSong/Apollo/wiki/Stuttering-Clinic) [read]

### Inferences
- Repeated capture restarts in a session are, per the maintainer's wiki, most often a display-topology event (a monitor/cable/switch re-probing, or a display mode/HDR change) rather than an encoder problem; on a laptop the internal panel plus any external display or dock are the candidates to check.
- The FP16-SDR whiteout is tied to virtual displays advertising wide gamut/HDR-class EDIDs or ACM; a physical SDR desktop without ACM is not the described trigger.
- No report found describes a Qualcomm HEVC decoder producing banding or colour errors with Moonlight; the Samsung reports are about latency, refresh rate and Exynos decoders.

### Gaps
- No 2025-2026 primary source found on banding in dark gradients for 8-bit SDR HEVC streams or on motion blur specific to Sunshine; a search of Sunshine issues for "banding" returned only unrelated Linux capture issues.
- No report found for Snapdragon 8 Gen 1 / Galaxy S22 Ultra (Snapdragon) specifically.
- No source found explaining ~50 capture restarts in one game session; the EA anti-cheat angle is unconfirmed (see section 5).

---

## 3. What does a 120 Hz host with a 60 fps stream do to smoothness, and what do people set?

### Takeaway
The developers treat exact rate matching (host frame delivery = stream fps = client refresh) as the main cure for periodic micro-stutter, preferably enforced with a frame limiter rather than V-sync; a host refresh that is a higher multiple (120 for 60) is reported as helpful, and Apollo ships a "double refresh rate" option for the same purpose.

### Cited Findings
- "Periodic micro stutters: usually caused by frame rate mismatch, like requested 60fps but the client is only 59.94hz"; fix by overriding the requested rate, then V-sync or (better) limit the game to the requested refresh rate on the host — [Apollo Stuttering Clinic](https://github.com/ClassicOldSong/Apollo/wiki/Stuttering-Clinic) [read]
- Apollo workaround for AMD CPU + NVIDIA GPU stutter: enable "Double refresh rate" for the virtual display and limit the game to the requested rate with RTSS or NVCP — [Apollo Stuttering Clinic](https://github.com/ClassicOldSong/Apollo/wiki/Stuttering-Clinic) [read]; maintainer adds it is only a stutter workaround, not needed otherwise — [Apollo #1329](https://github.com/ClassicOldSong/Apollo/issues/1329) [read]
- Sunshine log line `Adjusted capture rate to 59.951fps to better match display` at a 59.951 Hz host; with the host at 100 or 120 Hz Sunshine adjusted to 59.999 fps, "which did reduce the stutter even while frame limiting" (2024 user observation) — [Sunshine #1998](https://github.com/SunshineStream/Sunshine/issues/1998) [read]
- Community advice: set the host above 60 Hz (75/90/120), then lock fps to the client refresh with an in-game limiter or RTSS; V-sync off on the host plus a limiter; one user said this did not necessarily help; switching Moonlight frame pacing from "Smoothest" to "Balanced with FPS limit" fixed another user's stutter — [r/cloudygamer "Common causes and solutions for stutters" (mirror, Apr 2025)](https://reddit.birdcat.cafe/r/cloudygamer/comments/1jtgsq1/common_causes_and_solutions_for_stutters) [snippet, unverified; page returned HTTP 403]. Its content appears to be the same text as the Apollo wiki above.
- Sunshine added `clientRefreshRateX100` so clients can request exact fractional rates (59.94, 119.88); log example shows "Display refresh rate [119.999Hz] / Requested frame rate [60000/1001 exactly 59.94 fps]"; merged 2025-10-11 — [Sunshine PR #4019](https://github.com/SunshineStream/Sunshine/pull/4019) [read]
- A 0.1% host/client rate surplus (120.00 vs 119.88) accumulates one extra frame every ~8 s, seen as periodic queue oscillation "that no client setting can remove" (Linux follow-up, merged 2026) — [Sunshine PR #5282](https://github.com/SunshineStream/Sunshine/pull/5282) [read]
- Some Android users deliberately set Moonlight to 120 fps while running the host at the real client rate (60/90) as a pacing workaround; the mid-2025 minimum-frame-time change broke this until the minimum fps target became configurable — [Sunshine #4055](https://github.com/SunshineStream/Sunshine/issues/4055) [read]
- If the client panel is slightly off nominal (e.g. 119.985-120.045 Hz), the maintainer advises measuring it at testufo.com/refreshrate and entering it as a custom refresh rate in Artemis — [Artemis #246](https://github.com/ClassicOldSong/moonlight-android/issues/246) [read]
- Open Sunshine report (Oct 2026): Samsung tablet renders a constant ~0.49 fps fewer than it receives (one drop every ~2 s) at any stream rate, pacing mode or host refresh; unchanged with Apollo + Artemis; unresolved — [Sunshine #5956](https://github.com/SunshineStream/Sunshine/issues/5956) [read]
- Maintainer on game-fps vs stream-fps: when the game runs uncapped below/around the stream rate, "using a fps limiter should solve the problem and provide you a more smooth gameplay" — [Artemis #376](https://github.com/ClassicOldSong/moonlight-android/issues/376) [read]

### Inferences
- A 120 Hz desktop with a 60 fps stream is an exact 2:1 ratio, which is the arrangement Apollo's "double refresh rate" option creates on purpose; the reports suggest it is benign or helpful provided the game itself is capped at exactly 60 so that every second refresh carries a new frame.
- An uncapped or 120 fps game on that desktop means Sunshine samples 60 of ~120 frames; any drift between game frame times and the capture cadence would show as judder. That mechanism is implied by the limiter advice but not stated outright in a source.
- The residual risk is the client side: Samsung panels and One UI are repeatedly reported as not holding an exact nominal refresh.

### Gaps
- No source directly measures smoothness of "120 Hz host, game capped at 60, 60 fps stream" versus "60 Hz host".
- No maintainer guidance found on NVIDIA Control Panel V-sync modes (Fast, Adaptive) for 2025-2026; the only mention is the 2022 gist.
- Conflicting user evidence on limiter vs V-sync (Apollo wiki says limiter; Sunshine #1998 users found V-sync smoother) is unresolved.

---

## 4. Are there Sunshine/Apollo forks or patches that improve quality?

### Takeaway
Apollo (host) and Artemis (Android client) are the main actively developed forks; their gains are virtual-display integration, per-client display matching, custom resolution/bitrate and latency modes rather than encoder-quality changes. No fork exposing AQ strength or better scaling was found.

### Cited Findings
- Apollo: built-in SudoVDA virtual display matched to the client's resolution/refresh, "Double refresh rate" option, display-mode override wiki, headless mode, server commands and clipboard sync with Artemis — [Apollo README](https://github.com/ClassicOldSong/Apollo), [Apollo Stuttering Clinic](https://github.com/ClassicOldSong/Apollo/wiki/Stuttering-Clinic), [Artemis README](https://github.com/ClassicOldSong/moonlight-android) [read]
- Artemis: custom resolutions, custom bitrates, Fit/Fill/Stretch video scale mode, pan/zoom, frame-rate-lock fix for some devices, Warp frame-pacing modes and a Snapdragon ultra-low-latency mode — [Artemis README](https://github.com/ClassicOldSong/moonlight-android), [Artemis #376](https://github.com/ClassicOldSong/moonlight-android/issues/376), [Artemis #488](https://github.com/ClassicOldSong/moonlight-android/issues/488) [read]
- Warp modes are "pure hacks that may or may not work on your device" and only Apollo handles them correctly, per the maintainer — [Artemis #376](https://github.com/ClassicOldSong/moonlight-android/issues/376) [read]
- Open feature request (Oct 2025) for HDR and 8/10-bit indication in the Artemis performance overlay, i.e. it does not show bit depth today — [Artemis #431](https://github.com/ClassicOldSong/moonlight-android/issues/431) [title only]
- Upstream Sunshine itself gained relevant fixes: fractional refresh-rate requests (Oct 2025), configurable minimum fps target (mid-2025), `fp16_sdr_gamma_encoded` (Oct 2026), accurate AV1/HEVC capability detection and FFmpeg 9 (v2026.914 release) — [Sunshine PR #4019](https://github.com/SunshineStream/Sunshine/pull/4019), [Sunshine #4055](https://github.com/SunshineStream/Sunshine/issues/4055), [Sunshine PR #5106](https://github.com/SunshineStream/Sunshine/pull/5106), [Sunshine releases](https://github.com/SunshineStream/Sunshine/releases) [read]
- WGC capture (beta) cannot run under the Sunshine/Apollo service and was reported limited to 60 fps to Artemis; a community utility auto-switches DDX/WGC — [Apollo #620](https://github.com/ClassicOldSong/Apollo/issues/620), [Sunshine configuration docs](https://docs.lizardbyte.dev/projects/sunshine/latest/md_docs_2configuration.html) [read]
- A moonlight-qt fork by Nonary (VRR builds) notes "SDR streams no longer come back washed out when VRR is enabled" — desktop client only — [newreleases.io entry](https://newreleases.io/project/github/Nonary/moonlight-qt/release/v6.1.0-vrr17.1) [snippet, unverified]
- Other named forks seen but not examined: `fsesma/artemis`, "Artemide" (mentioned by a user) — [fsesma/artemis](https://github.com/fsesma/artemis) [not opened], [Artemis #473](https://github.com/ClassicOldSong/moonlight-android/issues/473) [read]

### Inferences
- For a physical 4K panel already at the stream resolution, Apollo's main advantage (virtual display matching) adds little, and the virtual display is itself a documented stutter risk.

### Gaps
- No fork or patch found that adds NVENC AQ strength, temporal AQ, lookahead, reference-frame control or improved downscaling filters.
- No per-client encoder settings found in Sunshine; Apollo's per-client permissions/display features were not checked in detail.

---

## 5. Anything specific to FC / EA Sports FC or Forza streaming quality?

### Takeaway
Nothing streaming-specific was found for EA Sports FC or Forza. The only material is about local stutter attributed to EA anti-cheat, plus one older football-game streaming report on a Samsung phone.

### Cited Findings
- EA forum users attribute local PC stutter in FC 24/25/26 to EA Anti-Cheat (one claims 20-30% CPU time) and report that reinstalling it via `__Installer\EAAntiCheat\EAAntiCheat.Installer.exe` helped some and not others — [EA forums FC 25 stutter thread](https://forums.ea.com/discussions/fc-25-technical-issues-en/stuttering-on-pc-after-the-update/7914180), [hone.gg FC 26 guide](https://hone.gg/blog/fix-ea-fc-26-lag-stuttering/) [snippet, unverified, anecdotal]
- A football title (eFootball 2024) stuttered badly when streamed to an S22 Ultra (Exynos) at 1080p60 "Smoothest" pacing while a Windows client on the same network was fine; attributed by the reporter to the Exynos decoder's 20+ ms decode time (2023-2024) — [moonlight-android #1285](https://github.com/moonlight-stream/moonlight-android/issues/1285) [read] (older; Exynos, not Snapdragon)
- A sim-racing user chose 4K60 HEVC 150 Mbps with host V-sync for smoothness, accepting input lag (2024) — [Sunshine #1998](https://github.com/SunshineStream/Sunshine/issues/1998) [read]

### Inferences
- If the game itself hitches locally (anti-cheat CPU spikes), the stream will reproduce those hitches regardless of encoder settings; checking local frame times is the way to separate the two. This is reasoning, not a sourced finding.

### Gaps
- No source found on EA anti-cheat interacting with Sunshine/Apollo capture (e.g. blocking capture, forcing display mode changes or causing capture reinitialisation).
- No source found on Forza Horizon/Motorsport stream artefacts, HDR behaviour or recommended bitrate with Sunshine.
- FC 27 specifically returned no results.
