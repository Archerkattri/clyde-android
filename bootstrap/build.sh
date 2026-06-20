#!/usr/bin/env bash
# Build Clyde's bespoke embedded runtime → app/src/main/assets/bootstrap-aarch64.zip
# Run on Linux / WSL2 Ubuntu with Docker. See README.md. NOT runnable on the Windows dev PC.
set -euo pipefail

PKG="dev.kris.clyde"
ARCH="aarch64"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
WORK="$HERE/.work"
TP="$WORK/termux-packages"

command -v docker >/dev/null || { echo "!! Docker is required (see README.md)"; exit 1; }
mkdir -p "$WORK"

# 1) Fork termux-packages and point the whole build at OUR package id. This cascades to
#    TERMUX__PREFIX=/data/data/dev.kris.clyde/files/usr (so every binary is built for our prefix).
if [ ! -d "$TP" ]; then
  git clone --depth=1 https://github.com/termux/termux-packages "$TP"
fi
cd "$TP"
if grep -q '^TERMUX_APP__PACKAGE_NAME=' scripts/properties.sh; then
  sed -i "s|^TERMUX_APP__PACKAGE_NAME=.*|TERMUX_APP__PACKAGE_NAME=$PKG|" scripts/properties.sh
else
  echo "TERMUX_APP__PACKAGE_NAME=$PKG" >> scripts/properties.sh
fi

# 2) Clean (mandatory after a prefix change) and build a MINIMAL bootstrap (arm64 only).
./scripts/run-docker.sh ./clean.sh
./scripts/run-docker.sh ./scripts/build-bootstraps.sh \
  --architectures "$ARCH" \
  --add nodejs-lts,termux-exec,ca-certificates
cp "bootstrap-$ARCH.zip" "$WORK/bootstrap-$ARCH.zip"

# 3) Inject the esbuild-bundled brain + the JS claude-code CLI; regenerate SYMLINKS.txt.
bash "$HERE/inject-brain.sh" "$WORK/bootstrap-$ARCH.zip" "$ROOT"

# 4) Ship it into the APK assets (gitignored; generated).
mkdir -p "$ROOT/app/src/main/assets"
cp "$WORK/bootstrap-$ARCH.zip" "$ROOT/app/src/main/assets/bootstrap-$ARCH.zip"
echo "✓ wrote app/src/main/assets/bootstrap-$ARCH.zip ($(du -h "$ROOT/app/src/main/assets/bootstrap-$ARCH.zip" | cut -f1))"
