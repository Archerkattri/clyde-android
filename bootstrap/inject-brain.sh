#!/usr/bin/env bash
# Inject the brain (esbuild-bundled) + the JS claude-code CLI into a Termux bootstrap zip and
# regenerate SYMLINKS.txt. Usage: inject-brain.sh <bootstrap.zip> <repo-root>
set -euo pipefail

ZIP="$1"; ROOT="$2"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cd "$TMP"
unzip -q "$ZIP"   # entries are relative to $PREFIX (files/usr)

# a) Bundle the brain to a single CJS file via brain/build-bundle.mjs (NOT the esbuild CLI).
#    The banner needs require("url") with its quotes intact; passing that banner as a shell CLI arg
#    strips the inner quotes → the bundle gets `require(url)` (an identifier) → the brain dies on boot
#    with "ERR_INVALID_ARG_TYPE: require received function url". The build script keeps the banner as a
#    JS string literal so the quotes always survive. It also defines import.meta.url (esbuild stubs
#    import.meta to {} in CJS, so createRequire(import.meta.url) would otherwise be createRequire(undefined)).
( cd "$ROOT/brain" && npm install --no-audit --no-fund \
  && node build-bundle.mjs "$TMP/opt/clyde-brain/server.js" )
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
