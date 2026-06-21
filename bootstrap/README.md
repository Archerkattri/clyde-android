# Clyde's embedded runtime — bespoke bootstrap build

This produces `app/src/main/assets/bootstrap-aarch64.zip`: a **Termux-style Linux bootstrap built
specifically for the `dev.kris.clyde` prefix**, containing only what Clyde's brain needs:

- **Node.js** (from `nodejs-lts`)
- **`termux-exec`** (`libtermux-exec-ld-preload.so`, termux-exec 2.x) — the W^X workaround so Android lets us `exec` Node
- the **JS `claude-code` CLI** (`@anthropic-ai/claude-code@2.1.112`, the last pure-JS release — the
  newer native binaries are glibc-only and won't run on Android)
- the **brain**, esbuild-bundled to a single `server.js` (no `tsx`/esbuild needed on device)

The Clyde app extracts this on first run (clean-room `EmbeddedRuntime.kt`) and launches the brain
in-process (`BrainRunner.kt`). No separate Termux app, no F-Droid, no `allow-external-apps`.

## Why a custom build (you can't reuse the official Termux bootstrap)
Termux's published binaries hardcode the path `/data/data/com.termux/files/usr` (interpreter, RPATH,
shebangs). Our package is `dev.kris.clyde`, so the prefix differs — every native binary (incl. Node)
must be **rebuilt from source** for our prefix. That's what `build.sh` drives.

## Prerequisites (NOT available on the Windows dev PC — needs Linux)
- Linux or **WSL2 Ubuntu**, with **Docker** (Termux's `run-docker.sh` build sandbox)
- ~10–15 GB free disk, ~8 GB RAM
- Several hours (Node compiles from source for arm64; first build only)
- Node + npm on the host (for the esbuild bundle + fetching the JS CLI)

## Run
```bash
bash bootstrap/build.sh          # forks termux-packages, builds, injects brain+CLI, writes the asset
```
Then build the APK normally (Android Studio / `gradlew :app:assembleDebug`). On an **arm64 device**,
first launch extracts the runtime and starts the brain; do `claude login` once (subscription).

## Output / size
- Asset `bootstrap-aarch64.zip`: ~80–120 MB (arm64 only). Gitignored — it's generated.
- On-device extracted prefix: ~150–200 MB.

See `../NOTICE.md` for the licenses of the bundled binaries.
