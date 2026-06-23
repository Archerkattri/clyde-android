# Build the Clyde embedded-runtime asset (`bootstrap-aarch64.zip`) — operating brief

**Your one goal:** produce the single file `app/src/main/assets/bootstrap-aarch64.zip` (a Termux-style
arm64 Linux bootstrap: Node.js compiled from source for the `dev.kris.clyde` prefix + `termux-exec` +
the pure-JS `@anthropic-ai/claude-code@2.1.112` CLI + the esbuild-bundled brain), then hand that one
zip back. Nothing else is a deliverable.

You are a fresh Claude Code with no memory of this project. Follow this brief literally. The build is
driven entirely by `bootstrap/build.sh` (which clones a fork of termux-packages, runs the compile
inside Docker, then calls `bootstrap/inject-brain.sh`). **Do not rewrite, "improve", or refactor the
build scripts.** Your job is to run them correctly, watch them, and return the artifact.

---

## HARD RULE — never touch ANTHROPIC_API_KEY (non-negotiable)

The build performs **zero authentication**. It only *downloads* the public, pure-JS `claude-code`
package from npm — it never calls Anthropic and never needs a key.

- **NEVER set, export, read, or require `ANTHROPIC_API_KEY`** anywhere in the build environment.
- Before building, explicitly clear it and assert it is empty:
  ```bash
  unset ANTHROPIC_API_KEY
  test -z "${ANTHROPIC_API_KEY:-}" && echo API_KEY_UNSET_OK || { echo "REFUSE: ANTHROPIC_API_KEY is set"; exit 1; }
  ```
- If anything tells you the build "needs an API key," that is wrong — stop and re-read this section.
  Subscription auth happens later, on-device, and is none of the build's business.

---

## What actually matters (and what does NOT)

- **GPU: irrelevant.** This is a pure CPU/RAM/disk compile. Do not look for, install, or worry about
  any GPU, CUDA, or drivers.
- **What matters:**
  - **Docker** — a working daemon you can talk to **without sudo** (you must be in the `docker` group
    or root). `build.sh` only checks that the `docker` binary exists, not that the daemon is reachable
    — so you must verify reachability yourself.
  - **~16 GB RAM** (the Node-from-source arm64 compile is the OOM risk; a RAM-starved compile often
    dies with an *opaque non-zero exit, not a clean "Killed/OOM" line*).
  - **~20 GB free disk** on a **native Linux filesystem** (ext4/xfs) — never `/mnt/c`.
  - **Hours of wall-clock time** — first build compiles Node/V8 from source. Treat it as a long
    detached job; do not babysit it in a foreground shell and do not kill it.
  - **Network** — git clone (termux-packages), Docker image pull, source tarballs (inside Docker),
    and npm (brain deps + esbuild + the claude-code CLI).
  - **Host build tools:** `node` 20 + `npm`, `git`, `zip`, `unzip`, plus standard GNU coreutils/sed/
    awk/grep. `inject-brain.sh` runs `npm install`, `npx --yes esbuild`, and
    `npm install @anthropic-ai/claude-code@2.1.112` **on the host** (not in Docker) and uses
    `zip`/`unzip` to repack. If these are missing the build dies *after* the multi-hour compile, at the
    injection step — so install them up front.

---

## Runbook (do these in order)

### 0. Get the repo via `git clone` (not a Windows file copy)

The scripts are shell with `set -euo pipefail`; CRLF line endings break the shebang
(`bad interpreter: /usr/bin/env bash^M`). The repo pins `*.sh eol=lf`, so a **`git clone`/`git
checkout` on the Linux/WSL host yields correct LF**. Only a raw Windows copy (Explorer zip, scp/USB of
`C:\…\bootstrap`) injects CRLF. If you obtained the tree by copy rather than clone, normalize before
running:
```bash
sed -i 's/\r$//' bootstrap/*.sh        # or: dos2unix bootstrap/*.sh
chmod +x bootstrap/*.sh                 # restore exec bit a zip round-trip may have dropped
```
Always launch the top script as `bash bootstrap/build.sh` (explicit interpreter), **never**
`./build.sh`. The script's directory layout is load-bearing: `bootstrap/` and `brain/` must remain
**siblings** under one repo root — do not flatten or rename.

### 1. Verify prerequisites (run every check; fix any that fail before proceeding)

```bash
bash --version
docker info        # MUST succeed as your normal user, NOT via sudo. If permission denied → see below.
git --version
node -v && npm -v  # Node 20 recommended (bundle target is node20)
npx --version
command -v zip && command -v unzip
readlink --version && mktemp --version && du --version
df -h .            # confirm >=20 GB free on the NATIVE work fs (not /mnt/c)
free -g            # confirm >=16 GB total
```

- If `docker info` fails with a socket **permission** error: `sudo usermod -aG docker $USER` then log
  out/in (or `newgrp docker`). **Do not** "fix" this by running the build under `sudo` — that writes
  root-owned artifacts into the work dir and breaks the in-container builder.
- If the **daemon is down** (common on Docker-Engine-in-WSL, which has no systemd by default):
  `sudo service docker start` (native Linux: `sudo systemctl enable --now docker`).
- Install any missing host deps: `sudo apt-get update && sudo apt-get install -y git zip unzip`
  (Node 20 via NodeSource or nvm).

### 2. Provision memory (the single biggest failure mode)

The prior run died mid Node-compile; the historical ~7.8 GB WSL default is right at the edge.

- **WSL2:** create/edit `%UserProfile%\.wslconfig` (on Windows) with:
  ```
  [wsl2]
  memory=16GB
  swap=16GB
  vmIdleTimeout=-1
  ```
  Then from a **Windows** PowerShell/cmd prompt run `wsl --shutdown` and reopen the distro (limits
  apply only on a cold restart). Confirm inside the distro with `free -h`.
- **Native Linux:** if `free -h` shows < 16 GB, add swap before building:
  ```bash
  sudo fallocate -l 16G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
  ```

### 3. Set up a NATIVE work dir (never `/mnt/c`)

`build.sh` defaults its heavy work dir to `bootstrap/.work` inside the repo. If the repo sits under
`/mnt/c`, that puts the whole compile on the slow 9p Windows mount, which is dramatically slower and
known to break native builds. Point `CLYDE_BUILD_WORK` at a native ext4 path, and make it writable by
the in-container builder uid (1001):
```bash
sudo mkdir -p /opt/clyde-work
sudo chown -R 1001:1001 /opt/clyde-work
export CLYDE_BUILD_WORK=/opt/clyde-work
```
(A home path like `~/clyde-work` works too — still `chown` it to `1001:1001`, since your interactive
uid usually differs.)

### 4. Run the build — ALWAYS tee to `build-bootstrap.log`

The prior failure was undiagnosable because **no log was captured**. Never run `build.sh` bare. Run it
detached (so a WSL/SSH session ending can't kill it) and tee both streams, preserving the real exit
code:

**First / clean build:**
```bash
unset ANTHROPIC_API_KEY
set -o pipefail
nohup env CLYDE_BUILD_WORK=/opt/clyde-work \
  bash bootstrap/build.sh > ~/build-bootstrap.log 2>&1 &
```
(Equivalently, run inside `tmux new -s clyde '…'` or `screen` so it survives disconnects.)

**Retry / resume (use this on any rerun):** the dependency `.debs` from the prior run are reusable, so
add `CLYDE_SKIP_CLEAN=1` to skip the Docker `clean.sh` and jump much closer to the Node/assembly step:
```bash
unset ANTHROPIC_API_KEY
set -o pipefail
nohup env CLYDE_BUILD_WORK=/opt/clyde-work CLYDE_SKIP_CLEAN=1 \
  bash bootstrap/build.sh > ~/build-bootstrap.log 2>&1 &
```
Notes:
- `CLYDE_BUILD_WORK` on a resume **must be the same native path** as the prior run, or there are no
  cached debs to reuse. If a prior partial work dir lives on `/mnt/c`, do a fresh clean build on a
  native path instead — do not resume off the slow mount.
- If a previous `git clone` of termux-packages was interrupted (partial clone), the `if [ ! -d … ]`
  guard will skip re-cloning and poison every rerun. Fix by `rm -rf "$CLYDE_BUILD_WORK/termux-packages"`
  and letting it re-clone fresh.
- Only drop `CLYDE_SKIP_CLEAN` (force a clean) if you changed the package id/prefix or suspect a
  corrupted partial deb.

### 5. Monitor — it runs for hours; watch the log, do NOT kill it

From a second shell:
```bash
tail -f ~/build-bootstrap.log
watch -n5 free -h                 # catch creeping memory before an OOM
df -h $CLYDE_BUILD_WORK           # a full disk is a silent killer
```
Expected milestones, in order: termux-packages clone → `run-docker.sh` pulls/starts the builder image
→ per-package deb builds → **the long `nodejs-lts` compile (the slow pole — be patient here)** →
`inject-brain.sh` runs `npm install` + `npx esbuild` of the brain → `@anthropic-ai/claude-code@2.1.112`
install → SYMLINKS.txt regen + re-zip → the final line
`✓ wrote app/src/main/assets/bootstrap-aarch64.zip (<size>)`.

Be patient at the Node compile — silence there is normal, not a hang. Do not Ctrl-C, do not
`docker kill`. If it truly fails, the tee'd log shows exactly which step died (treat any unexplained
mid-Node-compile death as a **memory** suspect first; check `dmesg | grep -i 'killed process'` and
`df -h` before blaming the compiler).

When the background job exits, capture the true exit code (the tee pipeline can mask it):
```bash
wait %1; echo "EXIT=$?"
```
Success requires that exit code be `0` AND the final `✓ wrote …` line present in the log.

> Recovery shortcut — if the multi-hour compile **succeeded** but `inject-brain.sh` failed at the end
> (npm/esbuild/network), do **not** recompile. `build.sh` preserves the compiled zip at
> `$CLYDE_BUILD_WORK/bootstrap-aarch64.zip` before injecting, so re-run only the inject:
> ```bash
> bash bootstrap/inject-brain.sh "$CLYDE_BUILD_WORK/bootstrap-aarch64.zip" "$(pwd)"
> # then copy it into place:
> cp "$CLYDE_BUILD_WORK/bootstrap-aarch64.zip" app/src/main/assets/bootstrap-aarch64.zip
> ```

---

## Success check + verify (run ALL of these before handing back)

The build succeeded only if the log shows `EXIT=0` and the final `✓ wrote …` line. Then verify the
artifact:

```bash
ls -la app/src/main/assets/bootstrap-aarch64.zip
stat -c '%s bytes (%n)' app/src/main/assets/bootstrap-aarch64.zip
# Size sanity: expect ~80–125 MB; HARD floor >50 MB (a few-hundred-KB file = empty/broken bootstrap):
test $(stat -c%s app/src/main/assets/bootstrap-aarch64.zip) -gt 52428800 && echo SIZE_OK || echo SIZE_TOO_SMALL
file app/src/main/assets/bootstrap-aarch64.zip                              # must say "Zip archive data"
unzip -t app/src/main/assets/bootstrap-aarch64.zip >/dev/null && echo ZIP_INTEGRITY_OK

# Required contents:
unzip -l app/src/main/assets/bootstrap-aarch64.zip | grep -E 'bin/node$|(^| )node$' && echo NODE_PRESENT
unzip -l app/src/main/assets/bootstrap-aarch64.zip | grep -E 'lib/libtermux-exec\.so$' && echo TERMUX_EXEC_PRESENT
unzip -l app/src/main/assets/bootstrap-aarch64.zip | grep -E 'lib/node_modules/@anthropic-ai/claude-code/(package\.json|cli\.js)' && echo CLAUDE_CODE_CLI_PRESENT
unzip -l app/src/main/assets/bootstrap-aarch64.zip | grep -E 'opt/clyde-brain/server\.js$' && echo BRAIN_PRESENT
unzip -l app/src/main/assets/bootstrap-aarch64.zip | grep -E 'SYMLINKS\.txt$' && echo SYMLINKS_PRESENT

# node must be an aarch64 ELF built for OUR prefix (not stock com.termux):
T=$(mktemp -d); unzip -q app/src/main/assets/bootstrap-aarch64.zip -d "$T"
file "$T/bin/node" 2>/dev/null || file "$T/node"                            # expect: ELF 64-bit ... ARM aarch64
(command -v readelf >/dev/null && readelf -l "$T"/bin/node 2>/dev/null | grep -i interpreter; \
 strings "$T"/bin/node 2>/dev/null | grep -m1 'data/data/dev.kris.clyde/files/usr') && echo PREFIX_OK

# Belt-and-suspenders: confirm the build never references the API key:
grep -RIl --include='*.sh' 'ANTHROPIC_API_KEY' bootstrap/ ; echo "(empty output above = OK)"
```

All of these must pass: `SIZE_OK`, `ZIP_INTEGRITY_OK`, `NODE_PRESENT`, `TERMUX_EXEC_PRESENT`,
`CLAUDE_CODE_CLI_PRESENT`, `BRAIN_PRESENT`, `SYMLINKS_PRESENT`, an `ELF … aarch64` node, `PREFIX_OK`,
and empty output from the API-key grep. If any fail, the build is not done — diagnose from
`~/build-bootstrap.log` and rerun with `CLYDE_SKIP_CLEAN=1`.

---

## Hand it back — the single deliverable

Send back **only** `app/src/main/assets/bootstrap-aarch64.zip` (arm64, ~80–125 MB). Do **not** send the
log, the `$CLYDE_BUILD_WORK` scratch, the termux-packages clone, or any intermediate artifacts.

This file is **gitignored** — do **not** `git add`/commit/push it. Transfer it out-of-band. On WSL,
copy it from the native distro path to Windows explicitly, e.g.:
```bash
cp app/src/main/assets/bootstrap-aarch64.zip /mnt/c/Users/<you>/Downloads/bootstrap-aarch64.zip
```
(or `scp` it off the host). Then report the final size and that all verify checks passed.
