#!/usr/bin/env bash
# Builds a Foundry debug APK from a patched Firefox checkout.
# Usage: android/scripts/build.sh [firefox-dir]   (default: android/firefox)
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
src="${1:-$here/firefox}"
cd "$src"

export MOZCONFIG="$src/mozconfig"
export MOZBUILD_STATE_PATH="${MOZBUILD_STATE_PATH:-$HOME/.mozbuild}"

# Installs the Android SDK/JDK that this Firefox revision expects (no-op once present).
./mach --no-interactive bootstrap --application-choice mobile_android_artifact_mode

# Downloads prebuilt Gecko for this revision, then assembles the app.
./mach build
./mach gradle fenix:assembleDebug

find mobile/android/fenix/app/build/outputs/apk -name '*.apk' -print
