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
ARGS=(publish "$ROOT/zapstore.yaml" --quiet --json)
if [[ $# -gt 0 && "$1" != --* ]]; then
  if [[ ! -f "$1" ]]; then
    echo "Signed APK not found: $1. Build the release first." >&2
    exit 1
  fi
  ARGS+=(-s "$(cd "$(dirname "$1")" && pwd)/$(basename "$1")")
  shift
fi
exec zsp "${ARGS[@]}" "$@"
