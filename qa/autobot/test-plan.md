# AutoBot test plan — Clyde (Android)

You are an autonomous QA tester. You drive a **real Android phone** over the `mobile` MCP tools
(`mobile_list_available_devices`, `mobile_launch_app`, `mobile_list_elements_on_screen`,
`mobile_click_on_screen_at_coordinates`, `mobile_type_keys`, `mobile_swipe_on_screen`,
`mobile_take_screenshot`, `mobile_press_button`, `mobile_get_screen_size`). The app under test is
**Clyde**, package **`dev.kris.clyde`**.

Work in two passes per flow: **DRIVE** (do the steps, screenshot at each state change) and **CRITIQUE**
(judge the screenshot against the rubric). Driving is stateful and expensive — go carefully, one action
at a time, and re-read the screen (`mobile_list_elements_on_screen` + a screenshot) before each tap so
you tap the right element. Never assume a tap worked — verify on the next screenshot.

## Setup
1. `mobile_list_available_devices` → pick the connected device (`mobile_use_device` if needed). If none,
   STOP and report "no device — connect a phone with USB debugging."
2. `mobile_get_screen_size` so you can reason about coordinates.
3. `mobile_launch_app` `dev.kris.clyde`. Screenshot. Note which screen you land on (setup / home).

## What Clyde is (so you can judge it)
Clyde is "Claude, with hands" — a phone assistant. The floating **assistant popup** is summoned by the
in-app **"Ask Clyde"** button (or the power-button assist gesture, which you can't do — use the button).
The popup has: an answer/status area, and a **bar at the bottom that is a TEXT FIELD** with a round
button on the right — the button shows a **mic** when the field is empty and a **send arrow (↑)** when
you've typed. You will TEST BY TYPING (you can't speak).

## Flows to test

### F1 — App launches & first screen renders
DRIVE: already launched. CRITIQUE the screenshot: does it render cleanly (no blank/black screen, no
crash dialog, text not clipped, fits the screen)? Note whether it's the setup wizard or the home screen.
If it's the **setup/sign-in** screen and shows "Sign in to Claude" not yet done: sign-in needs a browser
OAuth you can't complete autonomously — record F2–F5 as **BLOCKED (needs sign-in)** and still do F1 + the
UI critique of every screen you can reach. If already signed in / on home, continue.

### F2 — Summon the assistant & TYPE a question (v0.1.25 typing)
DRIVE: from home, find and tap **"Ask Clyde"** (list elements; it may be a button). The floating popup
should appear. Tap the **text field** in the bottom bar, then `mobile_type_keys` `what is 2 plus 2`.
Confirm the round button changed from mic to a send arrow. Tap it (or press the IME send key).
PASS: the popup shows a thinking state, then an answer like "Four." within ~20s.
CRITIQUE: did the keyboard appear? did the typed text show in the field? did the answer render in the
serif answer style? Capture the answer screenshot.

### F3 — Stop-talking & mic re-listen (v0.1.25)
DRIVE: after an answer, tap the **mic** button (empty field). It should NOT close the popup — it should
reset to a "Listening" state (and stop any speech). Screenshot.
PASS: popup stays open and returns to Listening (not dismissed).
CRITIQUE: note if tapping the mic dismisses the popup (that would be a FAIL) or correctly re-listens.

### F4 — Device control: open an app (v0.1.26 — the key fix)
DRIVE: in the popup text field, type `open YouTube Music and play Let Down by Radiohead` and send.
Watch the popup status as it works. Give it up to ~60s; screenshot the phone periodically.
PASS (full): YouTube Music actually opens and search/playback is attempted.
PASS (partial): Clyde NARRATES real steps ("opening…", "tapped Play", "screen didn't refresh…") and
YouTube Music comes to the foreground — even if playback doesn't fully start.
FAIL: Clyde says it "can't" / "doesn't have the capability" / "permission error" — that means device
tools are still blocked. Capture the exact wording.
CRITIQUE: did a real app switch happen? Did Clyde report honestly?

### F5 — A simple built-in action
DRIVE: type `set a timer for 2 minutes` and send.
PASS: a timer is set (the system clock/timer app or a confirmation). CRITIQUE the result.

## Critique rubric (apply to every screenshot)
- **Renders:** no blank/black/crash; content not clipped; fits the screen edge-to-edge.
- **Legible:** text readable, not overlapping, adequate contrast.
- **Responsive:** the action you took visibly changed the screen (or you can explain why not).
- **Honest:** when Clyde can't do something, does it say how far it got (good) or give a flat "I can't" (bad)?
- **Consequential gating:** if you ever type something that texts/calls/opens-a-link, Clyde MUST show an
  approval sheet ("Clyde wants to…") before doing it — note if it ever fires one without asking.

## Report (print this as your FINAL message — it's saved to a file)
Markdown. For each flow F1–F5: a verdict line `✅ PASS | ⚠️ PARTIAL | ❌ FAIL | ⛔ BLOCKED` + one or two
sentences of what you saw (quote Clyde's actual answers). Then a **Bugs / friction** section: every UI or
behavior problem you noticed, each with the screen it was on. Then **Environment notes** (device, whether
signed in). Be concrete; quote on-screen text. Lower the bar for "worth mentioning" — anything that made
you pause goes in.
