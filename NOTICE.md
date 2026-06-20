# Clyde — third-party notices

Clyde's own source (the Android app + the brain) is **clean-room**: the embedded-runtime extractor
and launcher were written from scratch from the documented bootstrap format, so **no Termux GPLv3
application code is copied or linked into Clyde**. Clyde's own code license is the project owner's
choice.

The embedded runtime (`app/src/main/assets/bootstrap-aarch64.zip`, produced by `bootstrap/`) bundles
prebuilt binaries that carry their own licenses. When distributing a build that includes that asset,
the obligations below apply.

## Bundled in the runtime bootstrap
- **Node.js** — MIT (plus its own bundled deps: OpenSSL, etc.).
- **Termux packages** (e.g. `bash`, `coreutils`, `dpkg`, `termux-exec`, `ca-certificates`) — various
  open-source licenses, several **GPLv2+/GPLv3** and MIT/BSD. These run as **separate executable
  processes** invoked over a process boundary (mere aggregation); they do not relicense Clyde's own
  code. For GPL components, **Corresponding Source** is available from the Termux packages project:
  https://github.com/termux/termux-packages (the exact versions are those produced by `bootstrap/build.sh`).
- Build tooling (`termux-packages` scripts) — Apache-2.0.

## Bundled / required by the brain
- **`@anthropic-ai/claude-code`** and **`@anthropic-ai/claude-agent-sdk`** — **proprietary, Anthropic
  licensed**. Bundling the CLI is intended for a **personal build**. Public redistribution of the
  Claude Code CLI may violate Anthropic's terms — review them before distributing.
- Other brain npm deps (`zod`, `tsx`, etc.) — MIT/Apache, see their packages.

## App assets
- **Clawd** mascot GIFs (`app/src/debug/assets/clawd/`) — **AGPL** (from `clawd-on-desk`); included in
  **debug/personal builds only**. Redraw before shipping (`design/assets/clawd/`).

This NOTICE is informational, not legal advice. For any public/Play distribution, audit the bundled
package licenses and Anthropic's terms first. (Note: a runtime that `exec`s binaries from app storage
is generally **not** Google-Play-distributable; Clyde is intended to be sideloaded.)
