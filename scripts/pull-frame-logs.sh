#!/usr/bin/env bash
# Copies the phone's frame logs (Settings -> Frame timing log) to ./frame-logs and analyzes the newest one.
#   scripts/pull-frame-logs.sh [dest-dir] [analyzer options, e.g. --all --csv seconds.csv]
# PKG=io.github.ramyemad95.vrdesktop for a release build. See docs/FRAME-LOG.md.
set -euo pipefail
PKG="${PKG:-io.github.ramyemad95.vrdesktop.debug}"
ADB="${ADB:-adb}"
command -v "$ADB" >/dev/null 2>&1 || ADB="$HOME/Library/Android/sdk/platform-tools/adb"
DEST="${1:-frame-logs}"
shift || true
mkdir -p "$DEST"
SRC="/sdcard/Android/data/$PKG/files/frame-logs"
if files=$("$ADB" shell ls "$SRC" 2>/dev/null | tr -d '\r') && [ -n "$files" ]; then
  for f in $files; do
    # Finished logs don't change; the summary and the newest log may
    if [ ! -f "$DEST/$f" ] || [ "$f" = latest-summary.txt ] || [ "$f" = "$(echo "$files" | grep '^frames-' | sort | tail -1)" ]; then
      "$ADB" pull "$SRC/$f" "$DEST/" >/dev/null
    fi
  done
else
  # No external storage: the app's own files, readable for debug builds
  for f in $("$ADB" shell run-as "$PKG" ls files/frame-logs | tr -d '\r'); do
    "$ADB" exec-out run-as "$PKG" cat "files/frame-logs/$f" > "$DEST/$f"
  done
fi
newest=$(ls -1 "$DEST"/frames-*.log.gz 2>/dev/null | sort | tail -1)
if [ -z "$newest" ]; then
  echo "No frame logs on the phone yet: stream once with Settings -> Frame timing log on." >&2
  exit 1
fi
echo "$(ls -1 "$DEST"/frames-*.log.gz | wc -l | tr -d ' ') log(s) in $DEST"
python3 "$(dirname "$0")/analyze-frame-log.py" "$newest" "$@"
