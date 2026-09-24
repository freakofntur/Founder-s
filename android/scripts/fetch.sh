#!/usr/bin/env bash
# Downloads Firefox's source at the revision pinned in android/FIREFOX_REVISION.
# Usage: android/scripts/fetch.sh [dest]   (default: android/firefox)
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
dest="${1:-$here/firefox}"
rev="$(cat "$here/FIREFOX_REVISION")"

if [ ! -d "$dest/.git" ]; then
  git init -q "$dest"
  git -C "$dest" remote add origin https://github.com/mozilla-firefox/firefox
fi

# Shallow, blobless fetch of just the pinned commit: far smaller than a full clone.
git -C "$dest" fetch --depth 1 --filter=blob:none origin "$rev"
git -C "$dest" checkout -q --force FETCH_HEAD
echo "Firefox source at $rev in $dest"
