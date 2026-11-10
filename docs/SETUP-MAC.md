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
4. Settings → Display → **Motion smoothness: Adaptive** (120 Hz head tracking).

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
- In the phone's VR Desktop **Settings → VR Settings**:
  - **Resolution: match the PC:** on by default. With the VR Desktop Host app running on the PC, the stream uses
    the PC screen's current resolution: a fullscreen game at 1080p streams at 1080p (about 50 Mbps), the desktop
    at 4K (about 100 Mbps). When a game switches resolution and stays there for 3 seconds, the stream reconnects
    at the new one, with a second or two of black. Borderless games keep the desktop's resolution. Without the host
    app, the Resolution and Bitrate settings apply.
  - **Virtual screen size:** 70° is the default. Go bigger to read small text, smaller to see the whole screen at once.
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
  - **Sharpness:** off by default. 30–50% makes text crisper through the lenses; too much adds halos.
  - **Head tracking:** on by default. Turn it off to keep the screen fixed in front of your eyes.
  - **Re-center up and down too:** on by default. A tap puts the screen where you look, higher or lower too
    (handy lying back). Off keeps it at eye level.
  - **Refresh rate:** "Highest available" by default (120 Hz). Pick 60 Hz to save battery and heat. Anything above
    60 Hz needs Motion smoothness set to Adaptive; if it isn't, the app shows a message and runs at 60 Hz.
  - **Stats panel:** off by default. Shows a panel below the virtual screen (look down): `NET OK/UNSTABLE/POOR`
    in green/yellow/red with the round-trip time, the frames arriving from the PC against the target
    (`in 33/60 fps` means the PC or the game only delivers 33), and the PC's encode, phone's decode and VR redraw
    times. Yellow or red on the last line means the PC or the phone needs most of a frame's time.
  - **VR mode:** turn it off to check the stream as a normal flat Moonlight stream.
- **HDR games:** turn on Settings → Advanced → **Enable HDR**. The PC then sends HDR when Windows is in HDR, and
  the VR view converts it to the phone's SDR picture with the right colors (the stats panel shows `HDR`). With HDR
  off in the app, an HDR game looks washed out: either turn this on, or turn HDR off in the game and Windows (or
  set Sunshine's display-device HDR option to automatic so Windows follows the app).
- Recommended stream settings for the S22 Ultra: **1080p, 60 FPS, 40–50 Mbps**, and 150% scaling in Windows.

## What to report back after the first test
- Does the picture appear, and is it upright?
- Does the screen stay still when you turn your head? Does it move the right way?
- Are straight lines straight through the lenses, and do the two eyes merge into one image?
- How readable is text at 70°?
- Does the phone get hot, and after how long?

## Working across the two laptops
- Run `git pull` before you start and `git push` when you're done.
- Protocol or port changes go in `docs/PROTOCOL.md` in **both** repos.
