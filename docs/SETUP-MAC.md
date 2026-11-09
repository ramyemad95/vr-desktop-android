# Mac setup

1. **Tools**
   ```bash
   brew install git
   brew install --cask android-studio android-platform-tools
   ```
   In Android Studio → SDK Manager, install: **NDK (Side by side)**, **CMake**, and Android SDK 34+.
2. **The repo**
   ```bash
   git clone https://github.com/ramyemad95/vr-desktop-android.git
   ```
3. **Phone:** turn on Developer options → USB debugging, connect it over USB, and check it shows up in `adb devices`.
   Use a 1440p+ phone if you can, because text readability is the hardest part.
4. **Import Moonlight:** run `scripts/import-moonlight.sh` (it pulls the upstream code into `app/` as a starting point).
5. **Testing against the PC:** the phone and the Windows PC must be on the same network, or the phone must be
   connected to the PC over USB tethering. The Mac only builds and installs the app.

## Working across the two laptops
- Sync with `git pull` before you start and `git push` when you're done.
- Protocol or port changes go in `docs/PROTOCOL.md` in **both** repos.
