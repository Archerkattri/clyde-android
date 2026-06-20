# Clawd mascot assets — provenance & licensing

**Clawd** is Anthropic's official Claude Code mascot (an 8-bit orange pixel crab).
Anthropic ships no public image asset; the only canonical art is procedural Unicode
in the Claude Code CLI (`clawd_body ≈ #D97757`), under Anthropic's proprietary license.

## What's here
- `gif/clawd-*.gif`, `svg/clawd-*.svg` — animated Clawd states from
  **clawd-on-desk** (https://github.com/rullerzhou-afk/clawd-on-desk), **AGPL-3.0**.
  Used here **as design reference and in the local mockup only.**
- `clyde-clawd.svg`, `clyde-clawd-blue.svg`, `gen-sprite.mjs` — our **own** pixel-crab
  sprite (original art, recolorable), the basis for a clean redraw.

## Licensing rule for the shipped app
The AGPL GIF/SVG assets must **not** be bundled in a distributed APK. For anything
public we ship our **own** sprite (`gen-sprite.mjs` output) or original Lottie
generated for the purpose. On a personal-only device build, the reference assets are
fine. See task→animation map in `_contactsheet.html`.
