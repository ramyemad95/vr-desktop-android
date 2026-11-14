# Screen-door effect, aliasing and PenTile artefacts on a phone in a Cardboard-style viewer

Source-quality key used below:
- **[fetched]** = I opened the page and read it.
- **[snippet]** = taken from a search-result summary only; the page itself was not opened. Treat as lower confidence.
- **Measured / spec** vs **anecdote** is marked per line.

Access notes: Reddit could not be read (the only route found was a third-party mirror, which reset the connection; not pursued further). doc-ok.org returned a placeholder page ("We're so back, baby!") instead of its articles. The Springer/Scientific Reports SDE paper redirected to a sign-in page. No YouTube sources were consulted. These are real holes: the "what do Cardboard/Gear VR users report" evidence is thin.

## 1. What physically determines screen-door visibility, and the pixels-per-degree figures

### Takeaway
SDE is set by angular pixel pitch (pixels per degree), the unlit gap between emitters (fill factor) and subpixel layout; a ~500 ppi phone behind 40-45 mm lenses lands at roughly 13-15 PPD, well below Quest 2 (20) and Quest 3 (25) and far below the ~30 PPD level where sources say the grid stops being obvious. On diamond PenTile the red and blue emitters are half as numerous as green, so the grid is coarser still for red/blue content.

### Cited Findings
- Geometry (textbook): feature size s = d·tan(θ); with a lens the screen sits at the focal distance, so required density scales inversely with focal length. Worked example: a screen at 1.5 in focal distance needs ~2292 ppi for 60 pixels/degree (30 cycles/degree). [fetched] — [LaValle, Virtual Reality, sec. 5.4](https://lavalle.pl/vr/node149.html)
- Same source defines the screen-door effect as the pixel structure becoming visible, distinct from aliasing ("jagged" diagonals). [fetched] — [LaValle](https://lavalle.pl/vr/node149.html)
- Official Cardboard viewer spec: lens diameter 34 mm, designed circular FOV 80 degrees, eye relief 18 mm, nominal virtual image distance -667 mm, PMMA lenses. No focal length is stated by Google. [snippet] — [Google Cardboard manufacturer specifications](https://support.google.com/cardboard/manufacturers/answer/6323398)
- Aftermarket Cardboard lenses are sold as 34 mm diameter with 40 mm or 45 mm focal length (vendor claims; one buyer doubted the 45 mm figure). [snippet] — [Apexel listing](https://www.shopapexel.com/products/apexel-lens-set-pop-tech-optical-glass-lens-bi-convex-34mm-diameter-45mm-focal-length-lenses-for-google-cardboard-vr)
- A third-party copy of a Cardboard manufacturing guideline lists a 39.07 mm lens-to-screen distance. Unverified secondary document. [snippet] — [Scribd copy](https://www.scribd.com/document/327486084/Manufacturing-Guidelines)
- Quest 2: 1832x1920 per eye, 20 PPD. Quest 3: 2064x2208 per eye, 25 PPD. Retinal resolution "generally considered to be around 60 PPD". Spec, vendor-stated. [snippet] — [Meta blog, "Stacking the Optical Deck"](https://www.meta.com/blog/vr-display-optics-pancake-lenses-ppd/); [Road to VR spec comparison](https://roadtovr.com/quest-3s-quest-3-quest-2-specs-compared/)
- HTC Vive, Rift CV1 and PSVR are "all in the 10 to 15" PPD range. [snippet] — [xVR wiki, Pixel density](https://xvrwiki.org/wiki/Pixel_density)
- Gear VR FOV is quoted as 101 degrees (slightly less with the smaller S6/S7 phones); the panel is 2560x1440, i.e. 1280 px per eye horizontally. [snippet] — [New Atlas](https://newatlas.com/gear-vr-vs-oculus-rift-vs-htc-vive-experience/42213/)
- "Anything below ~30 PPD may show SDE under inspection" (rule of thumb from a commercial VR blog, not a measurement). [snippet] — [VR Vision Group](https://www.vrvisiongroup.com/?p=3374)
- Meta research notes PPD alone does not predict perceived resolution; fill factor, pixel layout and optical blur also matter. [snippet] — [Meta research, EI System MTF](https://research.facebook.com/file/764762761804389/EI-System-MTF-v4-JerryJia.pdf)
- Fill factor = share of each pixel that emits light; lower fill factor means wider black gaps. Quoted range 70-95% by technology (secondary source, unverified numbers). [snippet] — [RayNeo explainer](https://www.rayneo.com/blogs/news/what-is-the-screen-door-effect-and-why-does-it-happen); [displaymodule.com](https://displaymodule.com/blogs/knowledge/how-to-choose-a-display-for-ar-vr-micro-oled-vs-amoled-vs-lcd)
- PenTile RGBG: one green subpixel per pixel but only half a red and half a blue, shared with neighbours. [snippet] — [VR/AR wiki, PenTile](https://vrarwiki.com/wiki/PenTile)
- PenTile "lowers the effective fill factor for some colors and tends to show a more obvious grid"; RGB stripe shows less SDE. [snippet] — [Digital Trends](https://digitaltrends.com/?p=3296455)
- PSVR2 (PenTile OLED) reviewers report a slightly blurrier image than same-resolution LCD "especially in the red and blue areas". [snippet] — [MIXED](https://mixed-news.com/en/psvr-2-display-has-fewer-subpixels-per-pixel-than-the-psvr/)
- Forum analysis (2013, anecdote/argument, not measurement): green dominates perceived luminance, so the green emitter lattice is what the eye reads as the grid; on diamond PenTile (Galaxy S4) the green lattice gives a 2D grid. Another poster disputes this, saying the grid is visible on solid red, green and blue alike. [fetched] — [Oculus developer forum, "Screendoor effect: PenTile vs RGB"](https://communityforums.atmeta.com/discussions/dev-pcvr/screendoor-effect-pentile-vs-rgb/107684)
- Same thread: the eye is more sensitive to near-horizontal/vertical lines than diagonal ones, so a diagonal lattice can look less grid-like (argument, unmeasured). [fetched] — [same thread](https://communityforums.atmeta.com/discussions/dev-pcvr/screendoor-effect-pentile-vs-rgb/107684)

### Inferences (my arithmetic, not from a source)
- S22 Ultra: sqrt(3088^2 + 1440^2) / 6.8 in = ~501 ppi, pixel pitch ~0.0507 mm.
- Centre PPD with the screen at the lens focal plane = f·tan(1 deg) / pitch: **f = 40 mm -> ~13.8 PPD; f = 45 mm -> ~15.5 PPD**. The user's own figure (970 px across 75 degrees = 12.9 PPD average) is consistent once lens distortion is allowed for.
- Each full pixel therefore subtends ~4-4.6 arcminutes, about 4x coarser than the 1 arcminute (60 PPD) acuity figure. The grid is resolvable by a normal eye by a wide margin; no rendering change can hide it without removing detail at that scale.
- Diamond PenTile red and blue emitters sit on a lattice with half the pixel count, so their nearest-neighbour spacing is ~1.41x the pixel pitch: roughly 10-11 "PPD" for pure red or pure blue versus 13.8-15.5 for green. Saturated red/blue areas should therefore show a coarser dot pattern than white or green areas. This matches the PSVR2 red/blue reports but is not directly measured for phones.
- Gear VR with a 1440p phone: 1280 px / ~101 degrees = ~12.7 PPD. So an S22 Ultra in a Cardboard viewer is in the same class as Gear VR and the original Rift/Vive (10-15 PPD), roughly two-thirds of Quest 2 and a bit over half of Quest 3.
- Longer focal length lenses raise PPD proportionally (45 mm vs 40 mm = +12%) at the cost of FOV. This is the only optical lever that genuinely raises angular density.

### Gaps
- No measured fill factor for the S22 Ultra panel (or any Samsung diamond PenTile phone panel) was found.
- Doc-Ok.org's measured HMD resolution and PenTile analyses could not be read (site returned a placeholder).
- No published PPD figure for Gear VR was found; the 12.7 figure is my division.
- The Scientific Reports paper on SDE in PenTile OLED headsets ([link](https://link.springer.com/article/10.1038/s41598-022-24345-9)) is behind a sign-in redirect; contents unverified beyond the snippet that SDE is "particularly common" with PenTile OLED.

## 2. Physical mitigations and reported results

### Takeaway
Every shipped anti-SDE solution is an optical low-pass placed at or near the panel (Odyssey+, PSVR, Valve Index, PSVR2), and all trade some sharpness for the grid. DIY versions work in direction but quality varies sharply with the film: purpose-made optical film was reported good, laminating pouches blurry or sparkly.

### Cited Findings
- Samsung Odyssey+ "Anti-SDE AMOLED": "applying a grid that diffuses light coming from each pixel and replicating the picture to areas around each pixel". Physical panel stays 616 ppi, 1440x1600 per eye; Samsung claims a "perceived" 1233 ppi. Vendor claim, no independent measurement found. [snippet] — [GamesBeat](https://gamesbeat.com/samsungs-odyssey-vr-headset-claims-1233-ppi-user-perceived-resolution/); [Tom's Hardware](https://www.tomshardware.com/news/samsung-hmd-odyssey-vr-headset-specs,37963.html)
- The Odyssey+ diffuser "is controversial because it reduces the sharpness of the image compared to the original Odyssey"; "you of course sacrifice sharpness when using a diffuser". [snippet] — [Road to VR, Valve Index teardown](https://roadtovr.com/valve-index-teardown-disassembly-lenses-optics-diffuser-ipd-mechansim/)
- Valve Index teardown identified a screen diffuser. [snippet] — [Road to VR](https://roadtovr.com/valve-index-teardown-disassembly-lenses-optics-diffuser-ipd-mechansim/)
- PSVR (2016): single Samsung 1080p OLED; Road to VR hypothesises "a diffusing mesh of sorts, above a conventional RGB stripe layout", and links it to PSVR having perhaps the least noticeable SDE of its generation. Hypothesis, unconfirmed, not measured. [fetched] — [Road to VR PSVR teardown](https://www.roadtovr.com/playstation-vr-psvr-teardown-disassembly-samsung-display-lenses/)
- PSVR2: Sony applied a diffusion coating to reduce SDE "at a small cost in sharpness". [snippet] — [Digital Trends](https://digitaltrends.com/?p=3296455)
- Diffuser films in general "blend the light from the pixels into the unlit spaces between them". [fetched] — [Road to VR on FRL display shifting](https://roadtovr.com/?p=170180)
- DIY diffuser results on the Rift DK2 (PenTile OLED phone panel, anecdotes from one thread) [fetched] — [Oculus developer forum, "Screen door effect mod"](https://communityforums.atmeta.com/discussions/dev-pcvr/screen-door-effect-mod/137595):
  - Two laminating-pouch sheets: SDE removed "completely" but "very blurry".
  - One laminating sheet: judged worse than the SDE itself.
  - Fellowes thermal pouches: SDE nearly eliminated but with a sparkly, veil-like look.
  - 3M Vikuiti film, single layer: two users report reduced SDE with clarity kept; it darkens the image, so they raised gamma/contrast. This was the thread's preferred option.
  - One user damaged the panel opening the headset (not a risk for a phone, where the film goes on the outside glass).
- Matte phone protectors: poorly made ones "create a strong 'sparkle' or grainy effect, especially on high-resolution displays". General phone-use claim, not VR-tested. [snippet] — [BGR](https://bgr.com/2228664/does-matte-screen-protector-ruin-display/)
- DK2-era users tried scotch tape as a diffuser; a textured blurring film "blurs the entire picture and reduces sharpness to unacceptable levels" (forum comment). [snippet] — [IL-2 forum](https://forum.il2sturmovik.com/topic/39669-lense-mod-for-vive)
- Lens swap anecdote: Gear VR lenses fitted to a Vive gave a clearer image with "a little bit" of SDE remaining. [snippet] — [IL-2 forum](https://forum.il2sturmovik.com/topic/39669-lense-mod-for-vive)
- Anecdote: one user found a Note 5 in a cheap plastic-lens Cardboard "a lot crisper with barely any screen door effect" compared with the same phone in Gear VR. Single post, thread not readable; plausible explanation is lower magnification/FOV. [snippet] — [r/GearVR thread (via mirror listing)](https://pol1.lr.ggtyler.dev/r/GearVR/comments/3x5k29/note_5_with_google_cardboard_is_significantly)
- Mechanical pixel shifting (Facebook Reality Labs / University of Arizona): two piezo actuators move the whole display in a 10 micrometre circle at 120 Hz so emitters sweep the gaps; a "redundancy" mode on a 480 Hz display shows four shifted sub-frames each with the correct colour for its position. Qualitative result: reduced SDE with apparently equal sharpness, and apparently higher resolving power in redundancy mode. Camera could not resolve subpixels; evaluated by contrast ratio. Research prototype on micro-OLED. [fetched] — [Road to VR](https://roadtovr.com/?p=170180)
- Other hardware routes named: smaller field of view, or very high pixel density (HP Reverb G2 cited as near SDE-free). [fetched] — [Road to VR](https://roadtovr.com/?p=170180)

### Inferences
- A diffuser's blur radius grows with its distance from the emitters. On a phone the film sits on top of the cover glass (roughly 0.5-1 mm above the OLED layer), which is far thicker than the 50 micrometre pixel pitch, so even a weak matte film will blur by more than a pixel; strength must be chosen by trial. The DK2 results (film directly on a bare panel) therefore do not transfer one-to-one.
- "Sparkle" from a coarse matte texture is itself a fixed-pattern artefact when magnified ~6x by the lens; a fine-grain optical diffuser or privacy-type film is the documented better choice over generic matte protectors or laminate.
- Slight defocus (moving the phone a millimetre or two off the focal plane, or a viewer with a focus wheel) is optically equivalent to a mild diffuser and costs nothing to test; it also shifts the virtual image distance.
- Since this user already prefers a much softer downscale with no sharpening, they are evidently on the "trade sharpness for less grid" side of the Odyssey+ debate; an optical low-pass acts on the panel grid itself, which software softening cannot touch.
- Mechanical shifting cannot be reproduced in software: a rendered sub-pixel jitter moves the image, not the emitters, so the dark gaps stay where they are.

### Gaps
- No report was found of a diffuser/matte film tested specifically on a phone in a Cardboard or Gear VR viewer.
- No comparative data on BoboVR, Destek, Homido lenses (focal length, Fresnel vs aspheric) and SDE was found.
- No numbers on defocus amount versus SDE reduction.
- Whether PSVR really has a diffuser mesh remains a teardown hypothesis.

## 3. Software mitigations

### Takeaway
Software cannot fill the gaps between emitters; it can only (a) avoid making the grid more conspicuous (content colour/brightness, aliasing, sharpening halos) and (b) avoid adding its own shimmer. Sources consistently say higher render resolution helps "marginally" and the grid is a hardware property; evidence for dithering/grain, temporal jitter or PenTile-aware rendering reducing SDE on phones was not found.

### Cited Findings
- "You can't really fix it on a given headset. Higher render resolution helps marginally; the real solution is a newer headset." (commercial blog opinion) [snippet] — [VR Vision Group](https://www.vrvisiongroup.com/?p=3374)
- DK2 users reported downsampling (supersampling) plus colour adjustments "helped further" alongside a diffuser film (anecdote). [fetched] — [Oculus developer forum](https://communityforums.atmeta.com/discussions/dev-pcvr/screen-door-effect-mod/137595)
- Aliasing is a rendering/sampling artefact separate from the panel grid ("the LCD panels in the Quest do not do aliasing, that is a GPU thing" - forum user). [snippet] — [Meta community forum](https://communityforums.atmeta.com/other-troubleshooting-14/quest-3-crazy-aliasing-and-poor-image-quality-171065)
- Colour dependence (argument, not measurement): green carries most luminance so the green lattice defines the visible grid; counter-claim that SDE shows on solid red, green and blue alike. [fetched] — [Oculus developer forum, PenTile vs RGB](https://communityforums.atmeta.com/discussions/dev-pcvr/screendoor-effect-pentile-vs-rgb/107684)
- PenTile red/blue areas look blurrier/coarser (PSVR2 reviews). [snippet] — [MIXED](https://mixed-news.com/en/psvr-2-display-has-fewer-subpixels-per-pixel-than-the-psvr/)
- "Checkerboarding" is visible on PenTile in high-contrast areas (quoted review in forum thread). [fetched] — [Oculus developer forum, PenTile vs RGB](https://communityforums.atmeta.com/discussions/dev-pcvr/screendoor-effect-pentile-vs-rgb/107684)
- Sensitivity varies between people, and motion-heavy content makes the grid less noticeable. [snippet] — [Trusted Reviews](https://www.trustedreviews.com/explainer/what-is-the-screen-door-effect-4243633)
- A smaller field of view is a recognised way to reduce SDE (more pixels per degree for the same panel). [fetched] — [Road to VR](https://roadtovr.com/?p=170180)
- Low persistence at 60 Hz produces perceptible flicker that can cause fatigue. [snippet] — [LaValle, sec. 6.2.3](https://lavalle.pl/vr/node178.html)

### Inferences
- Content colour: on diamond PenTile, white/grey/green content lights the densest lattice (all green emitters plus red and blue), while saturated red or blue lights only half as many, larger-spaced emitters. Reducing saturation slightly (so green emitters stay partly lit everywhere) should make the dot pattern finer in red/blue regions. Plausible from geometry and the PSVR2 reports; untested here. An in-headset A/B with a saturation slider is cheap.
- Brightness: gaps are seen as contrast between lit emitters and black surround. Bright, flat, large areas (sky, pitch, desktop backgrounds) show the grid most; dark or textured/moving content hides it. Lower panel brightness reduces the eye's contrast sensitivity and acuity somewhat; no source quantified this.
- Sharpening adds high-contrast edges at pixel scale, which is where PenTile "checkerboarding" is reported; consistent with the user's preference for no sharpening.
- Soft downscale: at ~13-15 PPD, a virtual screen covering 970 px cannot carry more than ~485 cycles across; a well-prefiltered (mipmapped/area) downscale removes moire against the pixel lattice and shimmer during head motion. It does not affect the grid.
- Temporal sub-pixel jitter of the rendered image ("wobulation" in software) only dithers the image sampling; it can trade aliasing for slight blur, but the emitters and gaps do not move, so it is not an SDE fix (contrast with the FRL mechanical shift above).
- Fine film grain/noise may mask the regular grid perceptually (irregular texture masks regular texture), but no source supporting this for VR was found; treat as an experiment only.
- Screen size is the one software lever with a physical basis for sharpness, not for SDE: the grid's angular pitch is fixed by panel and lens regardless of the virtual screen's size. Shrinking the virtual screen lowers source detail per degree without changing the grid; enlarging it does the opposite. A dark surround removes large bright flat areas from peripheral view where lens aberrations and grid are both visible.
- PenTile-aware subpixel rendering needs the app to know the exact emitter layout and to bypass the panel driver's own subpixel rendering; Android gives apps no such access, and no project doing this for phone VR was found.

### Gaps
- No evidence (for or against) that dithering, grain or temporal jitter reduces perceived SDE on phone VR.
- No GitHub issues (googlevr/cardboard, ALVR) discussing SDE mitigations were located in this pass.
- No measurements of SDE visibility versus content luminance or saturation.
- No data on curved versus flat virtual screens and perceived grid.

## 4. Samsung-specific: S22 Ultra modes, low persistence, Gear VR heritage

### Takeaway
The S22 Ultra ships at FHD+ by default, so the first check is that the phone is actually set to WQHD+ (3088x1440); otherwise the panel is upscaling a 2316x1080 image. Gear VR's low-persistence display mode was part of the Oculus/Samsung runtime and the Android "VR high performance" feature set, neither of which exists on this phone any more.

### Cited Findings
- The S22 Ultra ships at FHD+ out of the box; options are HD+, FHD+ and WQHD+ (3088x1440) under Settings > Display > Screen resolution. [snippet] — [Android Headlines](https://www.androidheadlines.com/?p=987822); [SamMobile display tips](https://sammobile.com/news/how-to-get-best-display-experience-on-galaxy-s22-plus-ultra); [Tech Advisor review](https://www.techadvisor.com/article/723097/samsung-galaxy-s22-ultra-review.html)
- Measured brightness (GSMArena): 494 nits at 100% manual, 829 nits manual with "Extra brightness", 1266 nits max auto. Tom's Guide measured 1359 nits peak; DXOMARK 1777 nits (Exynos unit). [snippet] — [GSMArena S22 Ultra review](https://m.gsmarena.com/samsung_galaxy_s22_ultra-review-2382p3.php); [Tom's Guide](https://www.tomsguide.com/uk/reviews/samsung-galaxy-s22-ultra); [DXOMARK](https://www.dxomark.com/samsung-galaxy-s22-ultra-exynos-display-test)
- Refresh: Samsung claims 1-120 Hz adaptive for the Ultra. [snippet] — [GSMArena S22 review](https://www.gsmarena.com/samsung_galaxy_s22-review-2396p3.php)
- PWM: DXOMARK lists 240 Hz for the S22+ (Exynos); a Samsung community post says S22/S23 use 240 Hz PWM at any brightness (user claim). No lab figure for the Ultra found. [snippet] — [DXOMARK S22+](https://dxomark.com/?p=111907); [Samsung community](https://eu.community.samsung.com/t5/galaxy-s22-series/higher-pmw/m-p/7326406/highlight/true)
- Gear VR used the phone's OLED in a low-persistence mode, exposed to developers through the Oculus SDK (Unity `OVRDevice.SetLowPersistenceMode`, Unreal "Enable low persistence mode" node). [snippet] — [Game Developer, "Launching on Gear VR"](https://gamedeveloper.com/business/launching-on-gear-vr); [Unreal forum](https://forums.unrealengine.com/t/enabling-low-persistence-mode-for-gear-vr/354275); [Meta forum](https://communityforums.atmeta.com/discussions/dev-unity/setting-low-persistence-mode-with-latest-unity/443515)
- Android CDD 7.9 (Android 11 text): a device declaring `android.hardware.vr.high_performance` MUST support a low-persistence display mode of 5 ms or less, at least 60 Hz in VR mode, at least 1920x1080 (2560x1440 strongly recommended), motion-to-photon 28 ms or less, and `EGL_ANDROID_front_buffer_auto_refresh`. [fetched] — [Android CDD 7.9](https://android.googlesource.com/platform/compatibility/cdd/+/refs/tags/android-11.0.0_r43/7_hardware-compatibility/7_9_virtual-reality.md)
- Samsung dropped Daydream with One UI 2 (Android 10). [snippet] — [9to5Google](https://9to5google.com/2020/04/15/android-daydream-samsung/)
- Gear VR support was removed with One UI 4 / Android 12. The S22 series launched on Android 12. [snippet] — [XDA](https://www.xda-developers.com/samsung-gear-vr-stop-working-android-12-update/); [SlashGear](https://www.slashgear.com/galaxy-s10-one-ui-4-will-finally-retire-the-gear-vr-16699556)
- S22 series received One UI 8 (Android 16) as its last major update. [snippet] — [Notebookcheck](https://www.notebookcheck.net/Samsung-ends-major-OS-support-for-the-Galaxy-S22-Series-with-One-UI-8-update.1133103.0.html)

### Inferences
- If the phone is at FHD+, each eye gets 1158 not 1544 horizontal pixels and the display pipeline rescales to the 1440p panel, adding blur and possible scaling artefacts on top of PenTile. Confirm at runtime (the app can read the current display mode size) rather than assuming WQHD+.
- Low persistence is almost certainly unavailable to a third-party app on the S22 Ultra: the phone launched after both Daydream and Gear VR were dropped on Samsung, so it is very unlikely to declare `android.hardware.vr.high_performance`. Checking `PackageManager.hasSystemFeature` settles it. Low persistence addresses motion smear, not the pixel grid, and at 60 Hz would flicker, so this is not a loss for SDE.
- With 240 Hz PWM, the panel is already strobed several times per frame; this does not affect the static grid.
- "Vivid" mode drives saturated primaries harder (more pure red/blue emitter use) than "Natural"; by the lattice argument in section 3, Natural or a reduced-saturation picture setting is the lower-grid choice. Not verified by any source.
- Manual brightness tops out near 494 nits (829 with Extra brightness); behind lenses in a dark viewer this is ample, so lowering brightness to soften grid contrast costs little.

### Gaps
- No source confirmed how apps see the resolution setting on the S22 Ultra (render-buffer size versus panel-native); needs an on-device check.
- No Samsung statement about PenTile subpixel handling in Gear VR beyond low persistence.
- No source on Vivid versus Natural mode and SDE.
- No S22 Ultra-specific PWM lab measurement.

## 5. What people report as the single most effective change

### Takeaway
Across the sources the consistent answer is hardware: more pixels per degree (denser panel or less magnification), and failing that an optical diffuser at the panel; software changes are described as marginal. For this setup the candidates in order of evidence are: confirm WQHD+, add a fine optical diffuser film or slight defocus, use longer-focal-length lenses, then content saturation/brightness tweaks.

### Cited Findings
- "No subpixel arrangement fully eliminates screendoor. Higher panel resolution is the only real fix" (forum user). [fetched] — [Oculus developer forum](https://communityforums.atmeta.com/discussions/dev-pcvr/screendoor-effect-pentile-vs-rgb/107684)
- "Higher render resolution helps marginally; the real solution is a newer headset." [snippet] — [VR Vision Group](https://www.vrvisiongroup.com/?p=3374)
- Diffusers are the approach manufacturers actually shipped (Odyssey+, Valve Index, PSVR2), always with an acknowledged sharpness cost. [snippet] — [GamesBeat](https://gamesbeat.com/samsungs-odyssey-vr-headset-claims-1233-ppi-user-perceived-resolution/); [Road to VR](https://roadtovr.com/valve-index-teardown-disassembly-lenses-optics-diffuser-ipd-mechansim/); [Digital Trends](https://digitaltrends.com/?p=3296455)
- Best DIY result reported: a single layer of 3M Vikuiti film on the panel, SDE softened "without noticeable blur", needing a gamma/contrast lift (two users, DK2). [fetched] — [Oculus developer forum](https://communityforums.atmeta.com/discussions/dev-pcvr/screen-door-effect-mod/137595)
- A lower-magnification viewer made the same phone look crisper with far less SDE than Gear VR (single anecdote). [snippet] — [r/GearVR listing](https://pol1.lr.ggtyler.dev/r/GearVR/comments/3x5k29/note_5_with_google_cardboard_is_significantly)
- "Screendoor is less distracting than the low detail of low resolution" - a reminder that some users prefer the grid to blur. [fetched] — [Oculus developer forum](https://communityforums.atmeta.com/discussions/dev-pcvr/screendoor-effect-pentile-vs-rgb/107684)

### Inferences
- For a user who already prefers soft and is still bothered by the grid, the remaining complaint is the emitter lattice itself, which only optics can change. The app-side work is at its ceiling once downscaling is properly prefiltered and sharpening is off.
- Cheapest experiments, in order: (1) verify WQHD+; (2) deliberately defocus slightly; (3) try a fine-grain anti-glare/optical diffuser film on the phone glass (expect to test more than one; avoid coarse matte that sparkles); (4) lenses with longer focal length (45-50 mm) for +12-25% PPD at reduced FOV; (5) Natural colour mode / slightly reduced saturation and lower brightness.

### Gaps
- No survey-style or multi-user evidence from Cardboard/Gear VR communities was reachable; the "most effective" ranking rests on vendor practice, one DK2 forum thread and geometry.
