# Mac setup and first test on the Galaxy S22 Ultra

The Mac builds the app and installs it on the phone. The stream itself goes straight from the Windows PC to the
phone; the Mac isn't involved.

## 1. Tools
```bash
brew install git
```
```bash
brew install --cask android-studio android-platform-tools
```
In Android Studio → Settings → Languages & Frameworks → Android SDK:
- **SDK Platforms:** Android API 37.
- **SDK Tools:** NDK (Side by side) **29.0.14206865** (tick "Show Package Details") and CMake.

Android Studio also downloads anything else the project asks for the first time it builds.

## 2. The repo
Moonlight's network core is a git submodule, so clone with `--recurse-submodules`:
```bash
git clone --recurse-submodules https://github.com/ramyemad95/vr-desktop-android.git
```
If you already cloned it without that flag:
```bash
git submodule update --init --recursive
```
Open the folder in Android Studio. In **Build Variants**, pick **nonRootDebug**.

## 3. The S22 Ultra
1. Settings → About phone → Software information: tap **Build number** 7 times to turn on Developer options.
2. Settings → Developer options: turn on **USB debugging**. Connect the phone to the Mac and tap **Allow** on the
   phone. It should then show up here:
   ```bash
   adb devices
   ```
3. Settings → Display → **Screen resolution: WQHD+** (sharper text in VR).
4. Settings → Display → **Motion smoothness: Adaptive** (needed for a Refresh rate above the default 60 Hz).

Press **Run** in Android Studio. The app is called **VR Desktop (Debug)** and installs next to Moonlight if you have it.

No Android Studio yet? Every push builds the APK on GitHub: open the repo's **Actions** tab → **Android build** →
the latest run → **Artifacts**. Then install it with:
```bash
adb install -r app-nonRoot-debug.apk
```

## 4. Connect to the PC
The Windows PC runs Sunshine, set up by the VR Desktop Host app from
[vr-desktop-host](https://github.com/ramyemad95/vr-desktop-host).

1. **Link:** the phone and PC must be on the same network. For the lowest latency, use a USB cable with
   **USB tethering** on the phone (Settings → Connections → Mobile Hotspot and Tethering → USB tethering). The host
   app lists the PC's addresses, best first.
2. **Add the PC:** in VR Desktop on the phone, tap **+** and type the address from the host app.
3. **Pair:** tap the PC. The phone shows a PIN. On the PC, click **Open Sunshine (pair phone)** in the host app and
   enter that PIN.
4. **Stream:** tap **Desktop**.

## 5. In the viewer
- Put the phone in the viewer and tap the screen (or press the viewer's button) to bring the virtual screen in front
  of you. Do this again any time it drifts.
- In the phone's VR Desktop **Settings → VR Settings**. The defaults are the settings tuned on a Galaxy S22 Ultra
  in a Cardboard v2-type viewer for EA SPORTS FC 27 (FC 27 mode, 60 Hz, 60° screen, Sharpness 30%, Picture softness
  80%, lens distance 50 mm, screen to lens distance 60 mm, Smooth video 1 frame, Color space Auto, and in the main
  settings 4K, H.265 and Enable HDR; the VR ones are all in `app/src/main/res/values/vr_defaults.xml`); change them
  to match your viewer and use.
  - **Resolution: match the PC:** on, but greyed out while FC 27 mode is on. With the VR Desktop Host app running on the PC, the stream uses
    the PC screen's current resolution: a fullscreen game at 1080p streams at 1080p (about 50 Mbps), the desktop
    at 4K (about 100 Mbps). When a game switches resolution and stays there for 3 seconds, the stream reconnects
    at the new one, with a second or two of black. Borderless games keep the desktop's resolution. Without the host
    app, the Resolution and Bitrate settings apply.
  - **FC 27 mode:** on by default. Streams at 1920×1080 with HDR (about 50 Mbps at 60 FPS) from the start, the
    way EA SPORTS FC 27 runs fullscreen, and doesn't follow the PC's resolution, so starting or leaving the game
    doesn't reconnect the stream (the 4K desktop is shown at 1080p meanwhile). It overrides Resolution, Bitrate and
    Enable HDR, and turns off "Resolution: match the PC". It doesn't change what the PC lets Sunshine capture: if
    the game hides its window from capture, the stream shows the desktop in any mode.
  - **Virtual screen size:** 60° is the default. Go bigger to read small text, smaller to see the whole screen at once.
  - **3D Mode:** Off by default. For 3D movies and games that the PC shows as two pictures, side by side or one
    above the other: each eye sees its own picture, so it has real depth. Keep it Off for the normal desktop (each
    eye would see only half of it). If the depth looks inside out (near things look far), pick "Side by side (right
    eye first)". It applies at the next connection.
  - **VR viewer:** if straight lines look bent, try another lens type. For the **cardoO VR Go**, pick that entry,
    center the phone on the line between the eyes, turn the IPD dial until the image is single, then the focus
    dial until text is sharp.
  - **Lens distance (IPD):** for viewers with an IPD dial. If your eyes need the lenses closer or wider than
    64 mm, set the dial there and the same distance here, or the two images won't merge. cardoO VR Go: 60 mm with
    the lenses at their closest, 70 mm at their widest; try ±1 mm around that until the image is single and sharp.
  - **Screen to lens distance (focus):** for viewers with a focus dial. Turn the dial until text is sharp (only
    the dial fixes blur), then pick the value here at which the screen stays still when you turn your head: if
    it drifts the way you turn, pick a bigger number; the other way, a smaller one. cardoO VR Go: about 37.5 mm
    with the lenses closest to the phone, 46.5 mm farthest. Judge it on the middle of the screen: with "No lens
    correction" the edges always drift a little, because the lens distorts more there.
  - **Ultimate High Quality:** off by default. Puts picture quality ahead of speed and battery: it turns on
    High-quality scaling and Anti screen-door and keeps 4K frames at full size, whatever those three switches say.
    Picture softness and Sharpness stay as you set them.
  - **High-quality scaling:** off by default. The stream has more pixels than the phone can show on the virtual
    screen (at 60° the screen covers about 1,200 of the phone's pixels per eye, fewer than a 1080p stream is wide),
    so the picture is shrunk to fit. On, each screen pixel reads the picture four times instead of once: less
    shimmer and false detail on grass, nets, the crowd and text. It adds no sharpening of its own; Sharpness works
    as usual. It costs more graphics work, so watch the heat.
  - **Anti screen-door:** off by default. The S22 Ultra's screen has half as many red and blue subpixels as green
    ones, so sharp color detail sparkles and shows a colored mesh through the lenses, which makes the pixel grid
    stand out. On, color detail is softened to what the red and blue subpixels can show while brightness (and so
    text) stays sharp, and Sharpness applies to brightness only. The black gaps between the pixels themselves are
    physical: a **matte (anti-glare) screen protector** works as a diffuser and hides them best (the Samsung Odyssey+
    headset used a diffusing layer for the same reason); turning the focus dial a hair past sharp also blurs the grid
    before the picture, since the grid is finer than any detail in it. Win back detail with Sharpness.
  - **Sharpness:** 30% by default. 30–50% makes text crisper through the lenses; too much adds halos.
  - **Picture softness:** 80% by default. How smoothly the stream is shrunk onto the virtual screen: higher is
    smoother, with a less visible pixel grid and less sparkle on fine detail; lower is crisper for small text (0% is
    the sharpest). Try 70–90% for games.
  - **Halve 4K frames:** off by default. Shrinks a 4K stream to half its size before drawing it: a quarter of the
    graphics work per video frame, for the same picture at higher Picture softness and a slightly softer one at
    low softness.
  - **Head tracking:** on by default. Turn it off to keep the screen fixed in front of your eyes.
  - **Re-center up and down too:** on by default. A tap puts the screen where you look, higher or lower too
    (handy lying back). Off keeps it at eye level.
  - **Refresh rate:** 60 Hz by default, which saves battery and heat; 120 Hz ("Highest available") makes head
    motion smoother but doesn't speed up decoding. Anything above 60 Hz needs Motion smoothness set to Adaptive; if
    it isn't, the app shows a message and runs at 60 Hz.
  - **Color space (HDR):** "Auto" (standard sRGB) by default. "Native" uses the phone screen's full color range for
    HDR, like a TV's Native color space, so the most saturated colors (a bright green pitch, red kits) aren't cut
    off; it needs the phone's Settings → Display → Screen mode on **Vivid**, and on Natural it looks too saturated.
    SDR streams look the same either way.
  - **HDR on the phone's screen:** off by default. On, HDR games are shown as real HDR on the phone's screen, the
    way a TV shows them, instead of being converted to a normal (SDR) picture: brighter highlights and the game's
    full colors. Converting always flattens HDR somewhat, because a normal picture at phone brightness can't hold
    its range. The screen stays in HDR mode for the whole stream, so it's brighter and runs warmer. In HDR mode the
    phone shows colors accurately (its Vivid boost is off), which looks less vivid than a TV: set **Color space to
    Native** to stretch them to the screen's full range like a TV's Native mode, and Color and Contrast for more.
    **HDR highlights** (Full by default) limits how bright whites get, to 400, 250 or 150 nits, and brightens the
    mid-tones a little: in a headset, whites at several hundred nits glare through the lenses and make the rest of
    the picture look pale. HDR white level sets how bright the desktop and the stats panel are; Highlight
    compression doesn't apply.
  - **Picture** (each applies at the next connection):
    - **Screen brightness in VR:** the headset covers the phone's light sensor, so automatic brightness dims the
      screen and colors look dull. 50–100% holds it while streaming (brighter means more heat).
    - **HDR white level:** for HDR games, how bright the game's white is (203 nits is the standard). Lower is
      brighter but flattens bright areas; 250–300 keeps more color and contrast in the sky, lit grass and the crowd
      for games set up for a bright TV.
    - **Highlight compression:** Normal by default. Punchy keeps more contrast; Soft keeps more detail in the very
      brightest highlights.
    - **Color:** saturation, like a TV's Color control (100% is accurate).
    - **Contrast:** gamma 2.2 is accurate for the phone; 2.4 gives deeper mid-tones, like a TV in a dark room.
  - **Stats panel:** off by default. Shows a panel below the virtual screen (look down): `NET OK/UNSTABLE/POOR`
    in green/yellow/red with the round-trip time, the frames arriving from the PC against the target
    (`in 33/60 fps` means the PC or the game only delivers 33), and the PC's encode, phone's decode and VR redraw
    times. Yellow or red on the last line means the PC or the phone needs most of a frame's time.
  - **Frame timing log:** on by default. Records how every frame gets from the PC to the screen, and why any
    stutters, to a file on the phone (the last 15 streams). `scripts/pull-frame-logs.sh` on the Mac copies them and
    prints a report: see [FRAME-LOG.md](FRAME-LOG.md).
  - **VR mode:** turn it off to check the stream as a normal flat Moonlight stream.
- **HDR games:** Settings → Advanced → **Enable HDR** is on by default. The PC then sends HDR when Windows is in
  HDR, and the VR view converts it to the phone's SDR picture with the right colors (the stats panel shows `HDR`). With HDR
  off in the app, an HDR game looks washed out: either turn this on, or turn HDR off in the game and Windows (or
  set Sunshine's display-device HDR option to automatic so Windows follows the app).
- **3D movies and games:** set **3D Mode** to match the content, then connect. Movies: play a Half-SBS (or
  Half-TB) 3D file fullscreen on the PC, or a YouTube 3D video in its side-by-side form. Games: ReShade with the
  SuperDepth3D shader, set to side by side, turns the game's depth into a 3D picture; some games have true 3D mods
  (Geo-11). Check that each eye sees one whole picture, that there's depth, and that there's no thin line at the
  screen's inner edges. With the stats panel on, the video line shows `3D SBS` or `3D TB`.
- Stream settings: with FC 27 mode (the default) the stream is 1080p HDR at 60 FPS and 50 Mbps; with it off, the
  PC screen's own resolution (1080p about 50, 1440p about 80, 4K about 100 Mbps). 150% scaling in Windows keeps
  text large enough to read.

## What to report back after the first test
- Does the picture appear, and is it upright?
- Does the screen stay still when you turn your head? Does it move the right way?
- Are straight lines straight through the lenses, and do the two eyes merge into one image?
- How readable is text at the default 60° screen size?
- Does the phone get hot, and after how long?

## Working across the two laptops
- Run `git pull` before you start and `git push` when you're done.
- Protocol or port changes go in `docs/PROTOCOL.md` in **both** repos.
