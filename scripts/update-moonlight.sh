#!/usr/bin/env bash
# Pull upstream Moonlight Android into this repo as the base for the VR client, keeping its history in a remote.
set -euo pipefail
cd "$(dirname "$0")/.."
git remote get-url moonlight >/dev/null 2>&1 || git remote add moonlight https://github.com/moonlight-stream/moonlight-android.git
git fetch moonlight
echo "Upstream fetched. Merge it with:"
echo "  git merge moonlight/master --allow-unrelated-histories"
echo "Then run: git submodule update --init --recursive"
