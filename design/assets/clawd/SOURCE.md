# Clawd mascot assets — provenance & licensing

**Clawd** is Anthropic's official Claude Code mascot (an 8-bit orange pixel crab).
Anthropic ships no public image asset; the only canonical art is procedural Unicode
in the Claude Code CLI (`clawd_body ≈ #D97757`), under Anthropic's proprietary license.

## What's here (in this public repo)
- `clyde-clawd.svg`, `clyde-clawd-blue.svg`, `gen-sprite.mjs` — our **own** pixel-crab
  sprite (original art, recolorable), the basis for the Compose-drawn mascot.

## Excluded from this public repo
The `gif/clawd-*.gif` and `svg/clawd-*.svg` animation references (and the `_contactsheet`
montage + the `clyde-mockups` render that embedded them) came from **clawd-on-desk**
(https://github.com/rullerzhou-afk/clawd-on-desk), **AGPL-3.0**, and were used **locally as
design reference only**. Per the rule below they are **not published here** and are **not**
bundled in any APK — the shipped mascot is our own `gen-sprite.mjs` / Compose art.

## Licensing rule for the shipped app
The AGPL reference assets must **not** be bundled in a distributed APK or any public repo.
For anything public we ship our **own** sprite (`gen-sprite.mjs` output / Compose-drawn).
On a personal-only device build, the reference assets are fine.
