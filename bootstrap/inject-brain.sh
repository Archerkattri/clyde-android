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
#    CRITICAL: the SDK (and Node's createRequire) read `import.meta.url`. esbuild's CJS output stubs
#    `import.meta` to `{}`, so `createRequire(import.meta.url)` becomes `createRequire(undefined)` and
#    the brain dies on boot. Define import.meta.url to the real file URL via a banner const.
( cd "$ROOT/brain" && npm install --no-audit --no-fund \
  && npx --yes esbuild src/server.ts \
       --bundle --platform=node --format=cjs --target=node20 \
       '--define:import.meta.url=__clyde_meta_url' \
       '--banner:js=const __clyde_meta_url = require("url").pathToFileURL(__filename).href;' \
       --outfile="$TMP/opt/clyde-brain/server.js" )
cp "$ROOT/brain/system-prompt.md" "$TMP/opt/clyde-brain/system-prompt.md"

# b) Install the pure-JS claude-code CLI (skip native optional deps like sharp).
#    Lands at lib/node_modules/@anthropic-ai/claude-code/cli.js → matches CLAUDE_CLI_PATH.
npm install --prefix "$TMP" --omit=optional --no-audit --no-fund \
  @anthropic-ai/claude-code@2.1.112

# c) APPEND any real symlinks (e.g. claude-code's node_modules/.bin) to the bootstrap's EXISTING
#    SYMLINKS.txt. CRITICAL: the Termux bootstrap ships its ~1600 base symlinks (bin/sh→dash, the
#    coreutils applet names, env, …) in THAT file as text, not as real files — so we must NOT delete
#    it (doing so leaves the runtime with `dash`/`coreutils` binaries but no `sh`/`env`/`ls`/… names,
#    breaking every shell-based tool). EmbeddedRuntime.kt recreates each entry with Os.symlink on
#    extract. (Format: `target←linkpath`.)
find . -type l | while read -r link; do
  printf '%s←%s\n' "$(readlink "$link")" "${link#./}" >> SYMLINKS.txt
  rm "$link"
done

# d) Re-zip
rm -f "$ZIP"
zip -q -r -y "$ZIP" . -x '*.DS_Store'
echo "✓ injected brain + claude-code CLI into $(basename "$ZIP")"
