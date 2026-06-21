#!/usr/bin/env bash
# Build app/src/main/jniLibs/arm64-v8a from the embedded runtime asset.
#
# WHY: Android's W^X forbids exec'ing a binary out of app-private storage at targetSdk 29+
# (ProcessBuilder(node) → "error=13, Permission denied"). The one exec-allowed location is the app's
# nativeLibraryDir, populated from jniLibs. So `node` and the native .so it needs must ship as jniLibs.
# But Android only extracts files named lib*.so (no versioned sonames like libcrypto.so.3), so we copy
# node's NEEDED closure out of the bootstrap, rename to clean sonames, and patchelf the references.
#
# WHEN: after bootstrap/inject-brain.sh has produced app/src/main/assets/bootstrap-aarch64.zip. Re-run
# whenever that asset's node/libs change. Output is gitignored (regenerable). Requires: unzip, patchelf,
# binutils (readelf). Build the APK with `packaging { jniLibs { useLegacyPackaging = true } }` so the
# libs land on disk in nativeLibraryDir; BrainRunner execs $nativeLibraryDir/libnode.so.
set -eu
HERE="$(cd "$(dirname "$0")/.." && pwd)"
ASSET="$HERE/app/src/main/assets/bootstrap-aarch64.zip"
OUT="$HERE/app/src/main/jniLibs/arm64-v8a"
[ -f "$ASSET" ] || { echo "FATAL: missing $ASSET (run inject-brain.sh first)"; exit 1; }
command -v patchelf >/dev/null || { echo "installing patchelf..."; sudo apt-get update -qq && sudo apt-get install -y -qq patchelf; }
W=$(mktemp -d); cd "$W"
echo "extracting bootstrap (~200MB)..."
unzip -q "$ASSET"
# Recreate symlinks (target←linkpath) so versioned NEEDED names resolve to real files.
if [ -f SYMLINKS.txt ]; then
  while IFS= read -r line; do
    tgt="${line%%←*}"; lnk="${line#*←}"
    [ -z "$tgt" ] && continue
    mkdir -p "$(dirname "$lnk")" 2>/dev/null || true
    ln -sf "$tgt" "$lnk" 2>/dev/null || true
  done < SYMLINKS.txt
fi
LIBDIR="$W/lib"; NODE="$W/bin/node"
[ -f "$NODE" ] || { echo "FATAL: no bin/node in asset"; exit 1; }

is_system() { case "$1" in
  libc.so|libm.so|libdl.so|liblog.so|libandroid.so|ld-android.so|libnetd_client.so) return 0;; *) return 1;; esac; }
unver() { echo "$1" | sed -E 's/\.so\.[0-9].*$/.so/'; }   # libcrypto.so.3 -> libcrypto.so
needs_of() { readelf -d "$1" 2>/dev/null | awk -F'[][]' '/NEEDED/{print $2}'; }
soname_of() { readelf -d "$1" 2>/dev/null | awk -F'[][]' '/SONAME/{print $2}'; }

declare -A SEEN; declare -A NEEDMAP
queue=("$(readlink -f "$NODE")"); closure=()
while [ ${#queue[@]} -gt 0 ]; do
  cur="${queue[0]}"; queue=("${queue[@]:1}")
  [ -n "${SEEN[$cur]:-}" ] && continue
  SEEN[$cur]=1; closure+=("$cur")
  for need in $(needs_of "$cur"); do
    is_system "$need" && continue
    NEEDMAP["$need"]="$(unver "$need")"
    if [ -e "$LIBDIR/$need" ]; then
      rf=$(readlink -f "$LIBDIR/$need"); [ -n "${SEEN[$rf]:-}" ] || queue+=("$rf")
    else
      echo "WARN: NEEDED $need (by $(basename "$cur")) not in bootstrap — treating as system"
    fi
  done
done
echo "closure: ${#closure[@]} files"

mkdir -p "$OUT"; rm -f "$OUT"/*.so 2>/dev/null || true
NODE_REAL="$(readlink -f "$NODE")"
cp -f "$NODE_REAL" "$OUT/libnode.so"; chmod 644 "$OUT/libnode.so"
for f in "${closure[@]}"; do
  [ "$f" = "$NODE_REAL" ] && continue
  son=$(soname_of "$f"); base=$(basename "$f"); name=$(unver "${son:-$base}")
  cp -f "$f" "$OUT/$name"; chmod 644 "$OUT/$name"
done

echo "patching sonames + NEEDED..."
for so in "$OUT"/*.so; do
  b=$(basename "$so")
  [ "$b" != "libnode.so" ] && patchelf --set-soname "$b" "$so" 2>/dev/null || true
  for need in "${!NEEDMAP[@]}"; do patchelf --replace-needed "$need" "${NEEDMAP[$need]}" "$so" 2>/dev/null || true; done
  patchelf --set-rpath '$ORIGIN' "$so" 2>/dev/null || true
done

echo "=== VERIFY (every NEEDED must be system or present unversioned here) ==="; fail=0
for so in "$OUT"/*.so; do
  for need in $(needs_of "$so"); do
    is_system "$need" && continue
    [ -e "$OUT/$need" ] || { echo "MISSING: $(basename "$so") needs $need"; fail=1; }
  done
done
ls -la "$OUT" | awk '{print $5, $9}'; du -sh "$OUT"
[ $fail -eq 0 ] && echo "RESULT: ALL DEPS SATISFIED" || { echo "RESULT: MISSING DEPS"; exit 1; }
