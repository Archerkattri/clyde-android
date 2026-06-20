#!/usr/bin/env bash
# Build Clyde's bespoke embedded runtime → app/src/main/assets/bootstrap-aarch64.zip
# Run on Linux / WSL2 Ubuntu with Docker. See README.md. NOT runnable on the Windows dev PC.
set -euo pipefail

PKG="dev.kris.clyde"
ARCH="aarch64"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
# Heavy build artifacts: keep them off a slow /mnt/c mount when building under WSL by setting
# CLYDE_BUILD_WORK to a native-Linux path (e.g. /root/clyde-work). Defaults to bootstrap/.work.
WORK="${CLYDE_BUILD_WORK:-$HERE/.work}"
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

# 2) Trim the bootstrap to a MINIMAL node-only base. Stock build-bootstraps builds the full Termux
#    base (apt/dpkg/perl/X11/fonts/...) which Clyde never uses (no on-device package management) and
#    which is bloated + fragile (perl build). Override the base package list. Idempotent.
if ! grep -q 'CLYDE minimal' scripts/build-bootstraps.sh; then
  awk '/# Handle additional packages\./ && !d { print "\t\tPACKAGES=(\"termux-exec\" \"bash\" \"coreutils\") # CLYDE minimal"; d=1 } { print }' \
    scripts/build-bootstraps.sh > scripts/build-bootstraps.sh.tmp && mv scripts/build-bootstraps.sh.tmp scripts/build-bootstraps.sh
  chmod +x scripts/build-bootstraps.sh   # the rewrite drops the exec bit; the container execs it
fi

# 3) Clean only when asked. CLYDE_SKIP_CLEAN=1 reuses already-built package debs for a fast retry
#    (clean is only truly needed the first time / after a prefix change).
if [ "${CLYDE_SKIP_CLEAN:-0}" != "1" ]; then
  ./scripts/run-docker.sh ./clean.sh
fi
./scripts/run-docker.sh ./scripts/build-bootstraps.sh \
  --architectures "$ARCH" \
  --add nodejs-lts,ca-certificates
cp "bootstrap-$ARCH.zip" "$WORK/bootstrap-$ARCH.zip"

# 3) Inject the esbuild-bundled brain + the JS claude-code CLI; regenerate SYMLINKS.txt.
bash "$HERE/inject-brain.sh" "$WORK/bootstrap-$ARCH.zip" "$ROOT"

# 4) Ship it into the APK assets (gitignored; generated).
mkdir -p "$ROOT/app/src/main/assets"
cp "$WORK/bootstrap-$ARCH.zip" "$ROOT/app/src/main/assets/bootstrap-$ARCH.zip"
echo "✓ wrote app/src/main/assets/bootstrap-$ARCH.zip ($(du -h "$ROOT/app/src/main/assets/bootstrap-$ARCH.zip" | cut -f1))"
