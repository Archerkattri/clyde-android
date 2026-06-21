# Clyde — security & privacy model

Clyde controls the phone (SMS, calls, accessibility, Shizuku/root) and runs Claude on the
user's subscription. This documents the threat model and the controls. Audited by a 43-agent
adversarial review; findings fixed and re-verified.

## Threat model
- **Co-resident apps.** Android loopback is reachable by any app with INTERNET. Both servers
  (brain 8765, app 8766) treat every local caller as untrusted.
- **Prompt injection.** On-screen text, SMS, web pages, clipboard are untrusted DATA, never
  instructions. They must not be able to drive a consequential action.
- **Billing.** The brain must run on the Claude subscription, never a per-token credential.

## Controls
### Auth (loopback)
- Both servers bind **127.0.0.1 only** and require **`X-Clyde-Key`** on every request,
  compared **constant-time** (timingSafeEqual / MessageDigest.isEqual).
- 128-bit `SecureRandom` key, stored in app `MODE_PRIVATE` prefs + `brain/.env`. Surfaced in
  Home → "brain key" (Copy / one-tap Sync into `brain/.env`).
- **Fail closed**: the brain refuses to start without a key (there is **no** auth-bypass flag),
  and refuses non-loopback bind unless `CLYDE_ALLOW_NONLOOPBACK=1` (which then forces a key).

### Consequential-action gating
- Tools are **safe** (reads) or **consequential**. Default is consequential; only an explicit
  allowlist is safe. **Screen-driving** (`tap`/`type`) is additionally gated app-side: a user
  confirm is required when the foreground app or the tap/typed target looks payment/auth-sensitive
  (`SensitiveContext`) — defense-in-depth against on-screen prompt injection.
- `confirm({action, params})` shows a **brain-derived** summary (not model-authored) and returns
  a **one-time token bound to `{tool, sha256(args)}`**, short TTL (~75s).
- Enforced centrally in the brain's `canUseTool` (runs before every tool): halt check → hard
  stops → for consequential tools, consume the bound token (must match tool AND args).
  A benign approval **cannot** be replayed on a different/dangerous tool or with changed args.
- **Defense-in-depth on-device**: for `send_sms`/`start_call`/`add_calendar_event` the app
  independently validates the user-approved token against its own single-use store before
  firing the intent — it never trusts the brain to self-gate.

### Hard stops (even with a valid token)
- No payments / money movement (matches shell, typed text, taps, shares, links, Gemini).
- Clyde never grants permissions to itself (no model-controlled bypass).
- Links from messages: the full URL is shown in the confirm sheet.

### Subscription-only
- Brain refuses to start if `ANTHROPIC_API_KEY` **or** `ANTHROPIC_AUTH_TOKEN` is set; warns on
  `ANTHROPIC_BASE_URL`. `/auth/status` reflects both so the app's verify screen can't lie.

### Privacy
- Accessibility dump **masks password fields**; mic/camera captures go to Termux-private
  storage (not world-readable `/sdcard`).
- `/healthz` returns only `{ok:true}` (no service banner); server errors are generic.
- Cleartext HTTP permitted **only** for 127.0.0.1; everything else HTTPS.
- Gemini delegation **discloses cross-vendor egress** in the confirm sheet (code-enforced).

### Availability / abuse
- Kill switch (`/kill`) **halts + interrupts** the in-flight turn and drops all tokens (stops
  even ungated UI tools). `/query` has a 256 KB body cap and a concurrency limit.

### Build
- Release uses R8 minify+shrink. AGPL Clawd assets live in the **debug** sourceSet only (never
  shipped in a release APK; redraw `design/assets/clawd/gen-sprite.mjs` to distribute).

## Known limits
- Multi-step UI automation is ~40–70% reliable — Clyde is supervised, not autonomous.
- `tap`/`type`/`input_*` are not per-action confirmed (usability); they are bounded by hard
  stops + the halt flag, and the model is instructed to treat screen content as untrusted.
- Verified by static analysis, unit tests, and runtime auth checks; full device bring-up
  (voice, accessibility, Shizuku, overlay) is the user's on-device step.
