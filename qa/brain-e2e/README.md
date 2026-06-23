# Brain E2E harness (real Claude + mock device)

Tests the **real brain** against a **simulated phone**, on a PC. This is the only way to exercise the
brain's command logic without an arm64 device — the embedded arm64 `node` can't run on an x86_64
emulator (the emulator's ARM translator can't exec a standalone arm64 ELF; confirmed empirically by the
`EM_AARCH64 (183) instead of EM_X86_64 (62)` link error in the on-emulator brain diagnostic).

## What it covers
The brain authenticates on the **subscription** (`CLAUDE_CODE_OAUTH_TOKEN`), reasons with real Claude,
picks tools, and calls the mock app. `mock-app.mjs` implements every `LocalControlServer` endpoint,
logs each call, and is **stateful for the play flow** (search box → type → results → tap → playing) so
multi-step UI driving actually converges. `drive.mjs` fires a command battery and reports, per command,
the tools invoked + the final answer.

## Run
```powershell
# 1) mock device (terminal A)
$env:CLYDE_KEY="clyde-e2e-test-key-001"; $env:APP_PORT="8766"; node mock-app.mjs

# 2) brain (terminal B) — token via env, NEVER commit it; API key must be unset
$env:CLYDE_KEY="clyde-e2e-test-key-001"; $env:APP_PORT="8766"
$env:CLAUDE_CODE_OAUTH_TOKEN="<your subscription token from `claude setup-token`>"
Remove-Item Env:\ANTHROPIC_API_KEY -EA SilentlyContinue
cd ../../brain; npx tsx src/server.ts

# 3) battery (terminal C)
$env:CLYDE_KEY="clyde-e2e-test-key-001"; node drive.mjs            # all
node drive.mjs 8 14 26                                             # specific indices
```

## Last full run (28 commands)
All command categories behave correctly: trivial queries (model-routed to Haiku), device queries
(now_playing/notifications/contacts/calendar/apps), intents (launch/timer/alarm/web/settings),
**media incl. driving YouTube Music ReVanced search→play to completion**, **reminders** (relative +
absolute, action reminders, past-time clarification), **consequential confirm flow** (text/call/calendar
/DND/brightness all do confirm→token→action), **a11y screen driving**, and the **money hard-stop**
(no payment tool ever called). The only non-passes were harness artifacts (a static screen pre-fix; an
over-strict expectation), all resolved.
