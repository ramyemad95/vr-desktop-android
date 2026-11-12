#!/usr/bin/env bash
# A/B screenshots of the VR picture from a debug build, switched live over adb (no reinstall, no stream restart).
# For each named variant: sets the debug.vrdesktop.* properties, waits for the app's "VR: debug tuning {...}" log
# line, and saves a screenshot to frame-logs/ab/<timestamp>/<name>.png. The properties are cleared on exit.
#
#   scripts/vr-ab.sh                  the default variants below
#   scripts/vr-ab.sh variants.txt     your own: one "name: key=value key=value" per line, # for comments
#   scripts/vr-ab.sh -                the same, from stdin
#
# Keys (unset = the real setting): frame_half 0/1, hq 0/1, asd 0/1, sharp 0..100, hq_sharp 0..100, lod_bias,
# hq_lod, hq_spread, aniso 1..16, freeze_pose 1, split 1. See docs/VR-RENDERER.md, "Debug tuning".
#
# Environment: BASE="freeze_pose=1" is added to every variant (BASE= for none); OUT=dir; SETTLE=0.5 seconds between
# the confirmation and the screenshot; TIMEOUT=8 seconds to wait for it; ANALYZE=0 skips scripts/vr-ab-analyze.py;
# ADB=path. Show a still picture on the PC (text, a desktop) and leave the phone lying still while it runs.
set -euo pipefail
ADB="${ADB:-adb}"
command -v "$ADB" >/dev/null 2>&1 || ADB="$HOME/Library/Android/sdk/platform-tools/adb"
PREFIX=debug.vrdesktop.
KEYS="frame_half hq asd sharp hq_sharp lod_bias hq_lod hq_spread aniso freeze_pose split"
BASE="${BASE-freeze_pose=1}"
SETTLE="${SETTLE:-0.5}"
TIMEOUT="${TIMEOUT:-8}"
RUN="$(date +%Y%m%d-%H%M%S)"
OUT="${OUT:-$(cd "$(dirname "$0")/.." && pwd)/frame-logs/ab/$RUN}"

DEFAULT_VARIANTS='
current:
frame_full: frame_half=0
frame_half: frame_half=1
hq_off: hq=0
asd_off: asd=0
hq_sharp_25: hq_sharp=25
lod_bias_0: lod_bias=0
hq_lod_0: hq_lod=0
aniso_1: aniso=1
hq_sharp_25_lod_bias_0: hq_sharp=25 lod_bias=0
frame_full_hq_sharp_25: frame_half=0 hq_sharp=25
frame_full_lod_bias_0: frame_half=0 lod_bias=0
frame_full_asd_off: frame_half=0 asd=0
plain: hq=0 asd=0 sharp=0
frame_full_plain: frame_half=0 hq=0 asd=0 sharp=0
'
case "${1:-}" in
  "") variants="$DEFAULT_VARIANTS" ;;
  -) variants="$(cat)" ;;
  -h|--help) sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  *) variants="$(cat "$1")" ;;
esac

# One adb call that sets every property: the keys given, the others empty (= unset), and the tag last. The app
# reads the tag first, so a log line with this tag always carries these values.
apply() { # tag, "key=value ..."
  local tag="$1" props="$2" cmd="" key value pair
  for key in $KEYS; do
    value=""
    for pair in $props; do
      [ "${pair%%=*}" = "$key" ] && value="${pair#*=}"
    done
    cmd="$cmd setprop $PREFIX$key '$value';"
  done
  "$ADB" shell "$cmd setprop ${PREFIX}tag '$tag'" </dev/null
}

cleanup() {
  apply "" "" >/dev/null 2>&1 || echo "Could not clear the debug.vrdesktop.* properties: run scripts/vr-ab.sh once more, or reboot the phone." >&2
}

"$ADB" get-state >/dev/null
mkdir -p "$OUT"
: > "$OUT/variants.txt"
trap cleanup EXIT
since="$("$ADB" shell "date +'%m-%d %H:%M:%S.000'" </dev/null | tr -d '\r')"
n=0
shots=0
while IFS= read -r line; do
  line="${line%%#*}"
  case "$line" in *:*) ;; *) continue ;; esac
  name="$(echo "${line%%:*}" | tr -d '[:space:]')"
  props="$(echo "$BASE ${line#*:}" | xargs)"
  if ! echo "$name" | grep -Eq '^[A-Za-z0-9_.-]+$'; then
    echo "Skipping \"$name\": names are letters, digits, _ . -" >&2
    continue
  fi
  bad=""
  for pair in $props; do
    key="${pair%%=*}"
    echo " $KEYS " | grep -q " $key " && echo "${pair#*=}" | grep -Eq '^[-+0-9.eE]+$' || bad="$pair"
  done
  if [ -n "$bad" ]; then
    echo "Skipping $name: \"$bad\" is not one of: $KEYS (with a number)" >&2
    continue
  fi
  n=$((n + 1))
  tag="$RUN-$n"
  apply "$tag" "$props"
  confirmed=""
  deadline=$(($(date +%s) + TIMEOUT))
  while [ -z "$confirmed" ] && [ "$(date +%s)" -le "$deadline" ]; do
    sleep 0.25
    confirmed="$("$ADB" logcat -d -T "$since" </dev/null 2>/dev/null | grep -F "VR: debug tuning {tag=$tag" | tail -1 | tr -d '\r' | sed 's/.*VR: debug tuning //' || true)"
  done
  if [ -z "$confirmed" ]; then
    echo "$name: no \"VR: debug tuning {tag=$tag...\" line within ${TIMEOUT}s." >&2
    if [ "$shots" = 0 ]; then
      echo "Is a debug build with the tuning harness streaming in VR? (logcat: \"VR: debug tuning\")" >&2
      exit 1
    fi
    continue
  fi
  case "$confirmed" in *FAILED*) echo "$name: $confirmed" >&2; continue ;; esac
  sleep "$SETTLE"
  "$ADB" exec-out screencap -p </dev/null > "$OUT/$name.png"
  shots=$((shots + 1))
  printf '%s\t%s\t%s\n' "$name" "$props" "$confirmed" >> "$OUT/variants.txt"
  echo "$name: $confirmed"
done <<VARIANTS
$variants
VARIANTS

echo "$shots screenshot(s) in $OUT"
if [ "$shots" -gt 0 ] && [ "${ANALYZE:-1}" != 0 ]; then
  python3 "$(dirname "$0")/vr-ab-analyze.py" "$OUT"
fi
