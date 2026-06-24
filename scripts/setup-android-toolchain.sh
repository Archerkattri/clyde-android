#!/usr/bin/env bash
# Portable, non-interactive Android build toolchain for this PC (no admin installer).
# Portable Temurin JDK 17 + Android cmdline-tools + platform/build-tools, then local.properties.
# Uses PowerShell Expand-Archive for unzip (Git Bash tar mis-parses C: paths). Safe to re-run.
set -u
# Toolchain root: override with CLYDE_TOOLCHAIN, else <home>/clyde-workspace/toolchain (USERPROFILE on Git-Bash/Windows).
ROOT="${CLYDE_TOOLCHAIN:-${USERPROFILE:-$HOME}/clyde-workspace/toolchain}"
ROOT="${ROOT//\\//}"   # normalize Windows backslashes (USERPROFILE is C:\Users\<you>)
REPO="$(cd "$(dirname "$0")/.." && pwd)"   # repo root, derived from this script's own location
JDK_DIR="$ROOT/jdk"
SDK_DIR="$ROOT/android-sdk"
LOG="$ROOT/setup.log"
mkdir -p "$ROOT"
exec > >(tee -a "$LOG") 2>&1
echo "=== toolchain setup $(date) ==="

unzip_ps() { # $1=zip  $2=dest
  powershell -NoProfile -Command "Expand-Archive -LiteralPath '$1' -DestinationPath '$2' -Force" \
    && echo "[unzip] ok -> $2"
}

# ---------- 1. JDK 17 ----------
if [ ! -x "$JDK_DIR/bin/java.exe" ]; then
  if [ ! -s "$ROOT/jdk.zip" ]; then
    echo "[jdk] downloading Temurin 17..."
    curl -sL -o "$ROOT/jdk.zip" "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"
  else
    echo "[jdk] reusing existing jdk.zip ($(du -h "$ROOT/jdk.zip" | cut -f1))"
  fi
  echo "[jdk] extracting..."
  rm -rf "$ROOT/jdk-extract"; mkdir -p "$ROOT/jdk-extract"
  unzip_ps "$ROOT/jdk.zip" "$ROOT/jdk-extract"
  inner="$(find "$ROOT/jdk-extract" -maxdepth 1 -type d -name 'jdk-17*' | head -1)"
  if [ -z "$inner" ]; then echo "[jdk] FAILED: no jdk-17* dir after extract"; ls -la "$ROOT/jdk-extract"; exit 1; fi
  rm -rf "$JDK_DIR"; mv "$inner" "$JDK_DIR"
  echo "[jdk] installed at $JDK_DIR"
else
  echo "[jdk] already present"
fi
export JAVA_HOME="$JDK_DIR"
export PATH="$JDK_DIR/bin:$PATH"
"$JDK_DIR/bin/java.exe" -version || { echo "[jdk] FAILED to run"; exit 1; }

# ---------- 2. Android cmdline-tools ----------
if [ ! -d "$SDK_DIR/cmdline-tools/latest/bin" ]; then
  if [ ! -s "$ROOT/cmdtools.zip" ]; then
    echo "[sdk] downloading cmdline-tools..."
    curl -sL -o "$ROOT/cmdtools.zip" "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
  fi
  rm -rf "$ROOT/cmdtools-extract"; mkdir -p "$ROOT/cmdtools-extract"
  unzip_ps "$ROOT/cmdtools.zip" "$ROOT/cmdtools-extract"
  mkdir -p "$SDK_DIR/cmdline-tools/latest"
  mv "$ROOT/cmdtools-extract/cmdline-tools/"* "$SDK_DIR/cmdline-tools/latest/"
  echo "[sdk] cmdline-tools installed"
else
  echo "[sdk] cmdline-tools already present"
fi

SDKMGR="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager.bat"
echo "[sdk] accepting licenses..."
yes | "$SDKMGR" --sdk_root="$SDK_DIR" --licenses >/dev/null 2>&1
echo "[sdk] installing platform-tools, platforms;android-35, build-tools;35.0.0 ..."
"$SDKMGR" --sdk_root="$SDK_DIR" "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# ---------- 3. local.properties ----------
WINSDK="$(echo "$SDK_DIR" | sed 's#/#\\\\#g')"
printf 'sdk.dir=%s\n' "$WINSDK" > "$REPO/local.properties"
echo "[ok] wrote local.properties -> $WINSDK"

echo "=== DONE. JAVA_HOME=$JDK_DIR  ANDROID_SDK=$SDK_DIR ==="
echo "TOOLCHAIN_SETUP_COMPLETE_OK"
