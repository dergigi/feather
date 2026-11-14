#!/usr/bin/env bash
# Publish with zsp 2 and the existing remote signer / NIP-C1 claim.
# Optional first argument: signed APK; otherwise use the public GitHub release.
# Remaining arguments are forwarded verbatim, e.g. --overwrite-release.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export PATH="$HOME/bin:$PATH"

if ! command -v zsp >/dev/null 2>&1; then
  echo "Install zsp 2: https://github.com/zapstore/zsp/releases" >&2
  exit 1
fi
VERSION="$(zsp --version)"
if [[ ! "$VERSION" =~ ^v?2\. ]]; then
  echo "This script requires zsp 2; found $VERSION" >&2
  exit 1
fi

# zsp loads SIGN_WITH from the ignored, owner-only .env itself.
# Never load APK keystore credentials or renew the claim during publication.
export GITHUB_TOKEN="${GITHUB_TOKEN:-$(gh auth token 2>/dev/null || true)}"
# Keep the PNG icon as PNG, like the working Amethyst and Ditto listings.
# Screenshots are already cropped, optimized WebP assets in the repository.
ARGS=(publish "$ROOT/zapstore.yaml" --quiet --json --no-compress)
if [[ $# -gt 0 && "$1" != --* ]]; then
  if [[ ! -f "$1" ]]; then
    echo "Signed APK not found: $1. Build the release first." >&2
    exit 1
  fi
  "$ROOT/scripts/verify-release-icon.sh" "$1"
  ARGS+=(-s "$(cd "$(dirname "$1")" && pwd)/$(basename "$1")")
  shift
fi
exec zsp "${ARGS[@]}" "$@"
