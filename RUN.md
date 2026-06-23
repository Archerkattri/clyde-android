# RUN.md — Build the Clyde embedded runtime (`bootstrap-aarch64.zip`)

Quick-start for a human at a Linux / WSL2 box. Copy-paste friendly.

---

## What this is

This builds Clyde's embedded runtime: a Termux-style Linux bootstrap with **Node.js compiled from
source for the `dev.kris.clyde` prefix**, plus `termux-exec`, the pure-JS
`@anthropic-ai/claude-code@2.1.112` CLI, and the esbuild-bundled brain. The single command
`bash bootstrap/build.sh` forks termux-packages, runs the heavy compile inside Docker, injects the
brain + CLI, and writes the result to `app/src/main/assets/bootstrap-aarch64.zip`. It is a
**multi-hour, CPU/RAM/disk-heavy** build (no GPU). The **only** thing you send back is that one zip
(~80–125 MB, arm64).

> The build performs **zero authentication**. It never reads or needs `ANTHROPIC_API_KEY` — it only
> *downloads* the public JS `claude-code` package from npm. Do **not** set that variable anywhere
> (hard project rule). Subscription auth happens later, on the device.

---

## What you need

- **Linux (x86_64)** or **WSL2 Ubuntu** on Windows. (macOS works but is the least-tested path.)
- **Docker** — running, and your user can run it **without `sudo`** (in the `docker` group).
- **Node 20 + npm**, **git**, **zip**, **unzip** on the host.
- **~16 GB RAM** (the Node-from-source compile is the OOM risk) and **~20–25 GB free disk** on a
  **native Linux filesystem** (ext4 — *not* a `/mnt/c` Windows mount).
- **Network** (git clone, npm, Docker image pull, source tarballs). Several hours of wall time.
- The repo obtained via **`git clone`** (not a raw Windows file copy — that injects CRLF and breaks
  the scripts).

---

## One-time setup

### Native Linux (Ubuntu/Debian)

```bash
# Docker (engine) + run it without sudo
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker "$USER"          # then log out/in (or: newgrp docker)
sudo systemctl enable --now docker

# Host build deps
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs git zip unzip

# Native work dir, owned by the in-container builder uid (1001)
sudo mkdir -p /opt/clyde-work
sudo chown -R 1001:1001 /opt/clyde-work
export CLYDE_BUILD_WORK=/opt/clyde-work

# Preflight (must all succeed)
docker info >/dev/null && echo DOCKER_OK
node -v && npm -v && git --version
command -v zip && command -v unzip
```

### WSL2 on Windows

**1. Raise the VM memory first** (the default ~7.8 GB ceiling OOM-kills the Node compile). On
Windows, edit `%UserProfile%\.wslconfig`:

```ini
[wsl2]
memory=16GB
swap=16GB
vmIdleTimeout=-1
```

Then from a **Windows** PowerShell/cmd prompt apply it with a cold restart:

```powershell
wsl --shutdown
```

Reopen the distro. Optionally keep the PC awake for the duration: `powercfg /change standby-timeout-ac 0`.

**2. Inside the WSL distro:**

```bash
# Docker (engine inside WSL — has no systemd by default, so start it per session)
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker "$USER"          # then re-open the shell
sudo service docker start                # run each session, OR enable systemd in /etc/wsl.conf

# Host build deps
sudo apt-get update && sudo apt-get install -y nodejs npm git zip unzip
# (if the apt node is too old, use nvm to install Node 20)

# Native ext4 work dir — NEVER under /mnt/c — owned by builder uid 1001
sudo mkdir -p /opt/clyde-work
sudo chown -R 1001:1001 /opt/clyde-work
export CLYDE_BUILD_WORK=/opt/clyde-work

# Preflight
free -h                                   # confirm ~16 GB total
docker info >/dev/null && echo DOCKER_OK
node -v && npm -v
```

> Keep the repo source wherever it is, but **always** point `CLYDE_BUILD_WORK` at the native ext4
> path. If it lives under `/mnt/c`, the default work dir runs the whole compile on the slow 9p mount
> and tends to fail in confusing ways.

---

## Run it

From the **repo root**, run under `tmux` (or `nohup`) so a disconnected session can't kill the
hours-long build, and **always tee to a log** (the prior run failed blind with no log):

```bash
unset ANTHROPIC_API_KEY                    # belt-and-suspenders; never needed by the build

tmux new -s clyde "set -o pipefail; \
  CLYDE_BUILD_WORK=/opt/clyde-work \
  bash bootstrap/build.sh 2>&1 | tee ~/build-bootstrap.log; \
  echo EXIT=\${PIPESTATUS[0]} | tee -a ~/build-bootstrap.log"
```

Watch progress from a second shell:

```bash
tail -f ~/build-bootstrap.log
# in parallel, keep an eye on headroom:
watch -n5 free -h
df -h /opt/clyde-work
```

Success ends with: `✓ wrote app/src/main/assets/bootstrap-aarch64.zip (<size>)` and `EXIT=0`.

---

## If it fails

The dependency `.deb`s from a prior run are reused, so **resume is cheap**. Re-run with
`CLYDE_SKIP_CLEAN=1` and the **same** `CLYDE_BUILD_WORK` path:

```bash
set -o pipefail
CLYDE_BUILD_WORK=/opt/clyde-work CLYDE_SKIP_CLEAN=1 \
  bash bootstrap/build.sh 2>&1 | tee ~/build-bootstrap.log
echo EXIT=${PIPESTATUS[0]}
```

Then triage by symptom (read the log — `set -euo pipefail` aborts on the first failed command):

- **Killed mid Node compile / opaque non-zero exit** → suspect **RAM**. Confirm with
  `dmesg | grep -i 'killed process'`. Raise WSL memory (above) or add swap on native Linux:
  ```bash
  sudo fallocate -l 16G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
  ```
  If it still OOMs at the V8 link step, cap parallelism: `export TERMUX_MAKE_PROCESSES=2`.
- **`exit 255` with no detail** → the prior classic. Almost always a detached WSL/terminal session
  dying mid-build. Run under `tmux` + `vmIdleTimeout=-1`, and keep the tee'd log so the real error
  is captured next time.
- **`docker` permission denied / daemon not reachable** → `docker info` fails. Fix group membership
  (`sudo usermod -aG docker $USER`, re-login) — don't wrap the build in `sudo`. On WSL,
  `sudo service docker start`.
- **Disk full / `ENOSPC`** → `df -h /opt/clyde-work` and Docker's data-root. `docker system prune -af`,
  ensure ≥25 GB free on a native path.
- **`bad interpreter: /usr/bin/env bash^M`** → CRLF crept in (raw Windows copy). Fix:
  `dos2unix bootstrap/*.sh` (or `sed -i 's/\r$//' bootstrap/*.sh`). Better: re-obtain via `git clone`.
- **Interrupted `termux-packages` clone** poisons reruns (the `if [ ! -d ... ]` guard skips it). Fix:
  `rm -rf "$CLYDE_BUILD_WORK/termux-packages"` and let it re-clone.
- **Inject step fails *after* the compile succeeded** (npm / esbuild / CLI fetch) → **don't recompile.**
  The compiled zip is preserved at `$CLYDE_BUILD_WORK/bootstrap-aarch64.zip`; re-run just the inject:
  ```bash
  bash bootstrap/inject-brain.sh "$CLYDE_BUILD_WORK/bootstrap-aarch64.zip" "$(pwd)"
  # then copy it into place:
  cp "$CLYDE_BUILD_WORK/bootstrap-aarch64.zip" app/src/main/assets/bootstrap-aarch64.zip
  ```

---

## How to know it worked

```bash
Z=app/src/main/assets/bootstrap-aarch64.zip

ls -la "$Z"
test "$(stat -c%s "$Z")" -gt 52428800 && echo SIZE_OK        # >50 MB (expect ~80–125 MB)
file "$Z"                                                      # "Zip archive data"
unzip -t "$Z" >/dev/null && echo ZIP_INTEGRITY_OK

# Required contents:
unzip -l "$Z" | grep -E 'bin/node$|(^| )node$'                 && echo NODE_PRESENT
unzip -l "$Z" | grep -E 'lib/libtermux-exec\.so$'              && echo TERMUX_EXEC_PRESENT
unzip -l "$Z" | grep -E 'lib/node_modules/@anthropic-ai/claude-code/(package\.json|cli\.js)' && echo CLI_PRESENT
unzip -l "$Z" | grep -E 'opt/clyde-brain/server\.js$'          && echo BRAIN_PRESENT
unzip -l "$Z" | grep -E 'SYMLINKS\.txt$'                       && echo SYMLINKS_PRESENT

# node is an aarch64 ELF built for OUR prefix:
T=$(mktemp -d); unzip -q "$Z" -d "$T"
file "$T/bin/node" 2>/dev/null || file "$T/node"               # "ELF 64-bit ... ARM aarch64"
strings "$T/bin/node" | grep -m1 'data/data/dev.kris.clyde/files/usr' && echo PREFIX_OK
```

All checks pass + `EXIT=0` in the log = good build.

---

## Send back

**One file only:**

```
app/src/main/assets/bootstrap-aarch64.zip
```

It's arm64, ~80–125 MB, and gitignored — so transfer it **out-of-band** (scp/upload), not via git.
Do **not** send any `$CLYDE_BUILD_WORK` scratch or other intermediates.
