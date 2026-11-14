#!/usr/bin/env bash
# Fetch the latest upstream Moonlight Android so its fixes can be merged into this fork.
# The fork was created from Moonlight 12.2 with full history, so this is a normal merge.
set -euo pipefail
cd "$(dirname "$0")/.."
git remote get-url moonlight >/dev/null 2>&1 || git remote add moonlight https://github.com/moonlight-stream/moonlight-android.git
git fetch moonlight
echo "Upstream fetched. New upstream commits:"
git log --oneline HEAD..moonlight/master | head -20
echo
echo "To merge them:"
echo "  git merge moonlight/master"
echo "  git submodule update --init --recursive"
echo "Expect conflicts only where VR Desktop changed Moonlight files: Game.java, MediaCodecDecoderRenderer.java,"
echo "PreferenceConfiguration.java, preferences.xml, strings.xml, arrays.xml, AndroidManifest.xml, app/build.gradle."
