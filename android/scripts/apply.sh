#!/usr/bin/env bash
# Applies Foundry's patches (android/patches/*.patch, in name order) to a Firefox checkout, copies
# Foundry's own source files (android/src/) and the generated brand images (android/overlay/) over it,
# and installs the build config. Safe to re-run: the checkout is reset first.
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

cp -R "$here/src/." "$src/"
cp -R "$here/overlay/." "$src/"
cp "$here/mozconfig" "$src/mozconfig"
echo "Patched $src"
