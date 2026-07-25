#!/usr/bin/env bash
# Fetch the prebuilt sherpa-onnx Android AAR (on-device Kokoro neural TTS — the assistant voice).
# It's a 54 MB binary bundling the JNI .so + Kotlin API, not committed to git. Run once before building.
set -euo pipefail
DIR="$(cd "$(dirname "$0")/.." && pwd)/app/libs"
mkdir -p "$DIR"
VER="1.13.3"
NAME="sherpa-onnx-$VER.aar"
DEST="$DIR/$NAME"
SHA256="243ad797a3b6e75ebbeaf7a2ab4aec0777e7d71b730685abb762a120940b07b6"

if [ -f "$DEST" ] && printf '%s  %s\n' "$SHA256" "$DEST" | sha256sum -c - >/dev/null 2>&1; then
  echo "verified existing $NAME -> $DIR"
  exit 0
fi

TMP="$DEST.part"
trap 'rm -f "$TMP"' EXIT
curl -fL --retry 3 -o "$TMP" \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$VER/$NAME"
printf '%s  %s\n' "$SHA256" "$TMP" | sha256sum -c -
mv "$TMP" "$DEST"
trap - EXIT
echo "fetched and verified $NAME -> $DIR"
