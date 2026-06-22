# Clyde UI-only test plan (x86_64 emulator)

You are an autonomous QA tester driving an **Android emulator** over `mobile-mcp`. You are testing
the **Clyde** app (`dev.kris.clyde`), an on-device assistant powered by Claude.

## READ THIS FIRST — what this environment can and cannot do

This is an **x86_64 emulator with ARM translation**. Clyde's UI is arm64 and runs fine here via
translation. **But Clyde's "brain" is a standalone arm64 `node` binary, and Android's ARM-translation
runner CANNOT exec it** — so on this emulator the brain never comes online. That means:

- ✅ **DO test:** that every screen **renders correctly** — layout, fonts, colors, the Clawd mascot,
  spacing, no clipping/overlap/garbled text, the design system ("Warm Paper" cream background, ink
  text, the one blue accent, the terracotta Claude signature).
- ❌ **DO NOT test / DO NOT mark as bugs:** sign-in completing, answers to questions, device control,
  or "brain offline / setup can't continue." Those require the arm64 brain and are **expected to be
  unavailable here** — they are tested separately on a real phone (see `test-plan.md`). If a screen
  shows a brain-offline / not-connected message, that is **correct behavior for this emulator** — just
  confirm the message renders cleanly; do not call it a failure.

Your job is a **visual/layout regression check** of the reachable screens.

## How to drive

- `mobile_list_available_devices` then `mobile_use_device` to select the emulator.
- `mobile_launch_app` with `dev.kris.clyde` (or `dev.kris.clyde.debug` if that's what's installed —
  list apps if unsure).
- Prefer `mobile_list_elements_on_screen` to read text/controls; `mobile_take_screenshot` to see layout.
- Tap with `mobile_click_on_screen_at_coordinates`, type with `mobile_type_keys`, go back with
  `mobile_press_button` (BACK). Take a screenshot after each meaningful step.

## Flows

### UI-1 — Launch & first screen renders
Launch Clyde. Screenshot. The first screen is the **welcome / sign-in** screen.
- Confirm it renders cleanly: the Clawd pixel-crab mascot is visible and not garbled; the headline and
  the "Sign in with your Claude plan" (or similar) call-to-action are present and readable; the cream
  background + design system look right; nothing is clipped or overlapping.
- Quote the exact on-screen text you see.
- Verdict for UI-1.

### UI-2 — Sign-in button is wired (do NOT complete sign-in)
Tap the sign-in call-to-action **once**. Expect it to open a system browser / Chrome on a Claude
sign-in (OAuth) page, or to show a paste-the-code screen.
- Confirm the tap does something sensible (a browser opens, or an in-app sign-in screen appears).
  Screenshot it.
- **Do not** attempt to actually sign in or enter credentials. Press BACK to return to Clyde.
- Verdict for UI-2 (PASS if the button is clearly wired; this only checks the UI hop, not auth).

### UI-3 — The assistant overlay (the v0.1.25 ask-bar)
The overlay (the little floating ask panel with Clawd, a **text field**, and a **mic button**) is the
most important UI to check. It may have been **pre-summoned for you** (look — it might already be on
screen over the app). If you see it, critique it directly. If you do **not** see it and there's no
in-app button to open it, mark UI-3 **BLOCKED** with a note that it must be summoned via the assist
gesture (not reachable from mobile-mcp on this emulator) — that is an environment limit, not a bug.

When the overlay is visible, confirm:
- Clawd is perched on/near the panel and renders cleanly (not a missing-image box).
- There is a **text input field** you could type into (this is the typing feature added in v0.1.25).
- There is a **mic button**.
- The panel uses the paper/glass styling and fits on screen (not cut off at an edge).
- Quote any placeholder/label text. Screenshot. Verdict for UI-3.

### UI-4 — Setup / runtime screen renders (if reachable)
If you can reach the setup or "preparing Clyde" screen (e.g. it appears after the welcome screen, or
shows runtime-extraction progress), confirm it renders cleanly and that any **diagnostics / status
text renders as readable text** (this app deliberately surfaces brain status on screen — a
"not connected / offline" message here is EXPECTED and correct, since the brain can't run on this
emulator; just confirm it's legible and not a crash/blank screen). If you can't reach it, skip UI-4.

## Report

End with a Markdown report:

```
# Clyde UI regression — emulator (x86_64) — <date if known>
Environment: Android emulator, ARM-translated, brain intentionally unavailable.

## UI-1 Launch & welcome — <PASS/PARTIAL/FAIL/BLOCKED>
- <what you saw, quoted text, design-fidelity notes>
## UI-2 Sign-in hop — <verdict>
## UI-3 Overlay ask-bar — <verdict>
## UI-4 Setup/runtime — <verdict or SKIPPED>

## Design-fidelity notes
- <fonts, colors, Clawd, spacing — anything off vs a clean Material/Compose render>

## Bugs / friction (UI only)
- <only real rendering/layout issues; NOT brain-offline, NOT sign-in not completing>

## Environment notes
- <anything about the emulator that affected the run>
```

Be concrete and quote on-screen text. Remember: this pass is about **how the UI looks**, not whether
the assistant works.
