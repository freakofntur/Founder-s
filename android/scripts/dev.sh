#!/usr/bin/env bash
# Fast local loop: sync Foundry's sources, build only what changed, install on every connected device and launch.
# Unlike build.sh, it doesn't reset the checkout or re-run bootstrap each time:
#   - android/src/ is copied with checksums, so unchanged files keep their timestamps and Gradle skips them;
#   - patches are re-applied (apply.sh) only when a patch, the Firefox revision or the mozconfig changed;
#   - bootstrap and `mach build` (prebuilt Gecko) run only the first time;
#   - Gradle installs straight onto every device adb sees (emulator and phone).
# Usage: android/scripts/dev.sh [--no-install]
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
src="$here/firefox"
objdir="$src/objdir-foundry"
stamp="$objdir/.foundry-patches"
install=1
[ "${1:-}" = "--no-install" ] && install=0

[ -d "$src/.git" ] || { echo "No Firefox checkout at $src: run fetch.sh first."; exit 1; }

sum="$(cat "$here"/patches/*.patch "$here/FIREFOX_REVISION" "$here/mozconfig" | shasum | cut -c1-40)"
if [ "$(cat "$stamp" 2>/dev/null)" != "$sum" ]; then
  "$here/scripts/apply.sh" "$src"
  mkdir -p "$objdir"
  echo "$sum" > "$stamp"
else
  rsync -a --checksum "$here/src/" "$src/"
  rsync -a --checksum "$here/overlay/" "$src/"
fi

cd "$src"
export MOZCONFIG="$src/mozconfig"
export MOZBUILD_STATE_PATH="${MOZBUILD_STATE_PATH:-$HOME/.mozbuild}"

# A stamp written only after success: a failed first build leaves objdir behind and must run again.
if [ ! -f "$objdir/.foundry-built" ]; then
  ./mach --no-interactive bootstrap --application-choice mobile_android_artifact_mode
  ./mach build
  touch "$objdir/.foundry-built"
fi

if [ "$install" = 1 ]; then
  ./mach gradle fenix:installDebug
  adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
  for serial in $("$adb" devices | awk 'NR>1 && $2=="device" {print $1}'); do
    "$adb" -s "$serial" shell am start -n org.mozilla.fenix.debug/org.mozilla.fenix.HomeActivity >/dev/null 2>&1 &&
      echo "Launched Foundry on $serial"
  done
else
  ./mach gradle fenix:assembleDebug
fi
