#!/usr/bin/env bash
# Inject the brain (esbuild-bundled) + the JS claude-code CLI into a Termux bootstrap zip and
# regenerate SYMLINKS.txt. Usage: inject-brain.sh <bootstrap.zip> <repo-root>
set -euo pipefail

ZIP="$1"; ROOT="$2"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cd "$TMP"
unzip -q "$ZIP"   # entries are relative to $PREFIX (files/usr)

# a) Bundle the brain to a single CJS file — no tsx/esbuild needed on the device, which also avoids
#    esbuild's per-platform native binary (the main ABI-drift risk).
( cd "$ROOT/brain" && npm install --no-audit --no-fund \
  && npx --yes esbuild src/server.ts \
       --bundle --platform=node --format=cjs --target=node20 \
       --outfile="$TMP/opt/clyde-brain/server.js" )
cp "$ROOT/brain/system-prompt.md" "$TMP/opt/clyde-brain/system-prompt.md"

# b) Install the pure-JS claude-code CLI (skip native optional deps like sharp).
#    Lands at lib/node_modules/@anthropic-ai/claude-code/cli.js → matches CLAUDE_CLI_PATH.
npm install --prefix "$TMP" --omit=optional --no-audit --no-fund \
  @anthropic-ai/claude-code@2.1.112

# c) Regenerate SYMLINKS.txt (Termux convention: `target←linkpath`), replacing real symlinks so the
#    zip stays portable; EmbeddedRuntime.kt recreates them with Os.symlink on extract.
rm -f SYMLINKS.txt
find . -type l | while read -r link; do
  printf '%s←%s\n' "$(readlink "$link")" "${link#./}" >> SYMLINKS.txt
  rm "$link"
done

# d) Re-zip
rm -f "$ZIP"
zip -q -r -y "$ZIP" . -x '*.DS_Store'
echo "✓ injected brain + claude-code CLI into $(basename "$ZIP")"
