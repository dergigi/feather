#!/usr/bin/env bash
# Check what Zapstore actually extracts from the signed release APK.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PATH="$HOME/bin:$PATH"
if [[ $# -ne 1 || ! -f "$1" ]]; then
  echo "Usage: $0 signed-release.apk" >&2
  exit 1
fi
command -v magick >/dev/null || { echo "Install ImageMagick to verify the release icon." >&2; exit 1; }
ICON_DIR="$(mktemp -d)"
trap 'rm -rf "$ICON_DIR"' EXIT
zsp utils extract-apk "$1" --icon-path "$ICON_DIR/icon.png" > "$ICON_DIR/apk.json"
if ! magick compare -metric AE "$ROOT/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" \
    "$ICON_DIR/icon.png" null: 2> "$ICON_DIR/difference.txt"; then
  echo "APK launcher icon differs from Feather's expected artwork. Publication stopped." >&2
  exit 1
fi
echo "Verified: Zapstore extracts Feather's launcher icon." >&2
