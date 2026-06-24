#!/usr/bin/env bash
# Fetch the prebuilt sherpa-onnx Android AAR (on-device Kokoro neural TTS — the assistant voice).
# It's a 54 MB binary bundling the JNI .so + Kotlin API, not committed to git. Run once before building.
set -e
DIR="$(cd "$(dirname "$0")/.." && pwd)/app/libs"
mkdir -p "$DIR"
VER="1.13.3"
curl -L -o "$DIR/sherpa-onnx-$VER.aar" \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$VER/sherpa-onnx-$VER.aar"
echo "fetched sherpa-onnx-$VER.aar -> $DIR"
