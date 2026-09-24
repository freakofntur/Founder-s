#!/usr/bin/env bash
# Applies Foundry's patches (android/patches/*.patch, in name order) to a Firefox checkout, copies the
# files in android/overlay/ over it (brand images and other whole-file replacements), and installs the
# build config. Safe to re-run: the checkout is reset first.
# Usage: android/scripts/apply.sh [firefox-dir]   (default: android/firefox)
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
src="${1:-$here/firefox}"

git -C "$src" reset -q --hard
git -C "$src" clean -q -fd -e 'objdir-*' -e '.mozbuild'

shopt -s nullglob
for patch in "$here"/patches/*.patch; do
  echo "Applying $(basename "$patch")"
  git -C "$src" apply --whitespace=nowarn "$patch"
done

cp -R "$here/overlay/." "$src/"
cp "$here/mozconfig" "$src/mozconfig"
echo "Patched $src"
