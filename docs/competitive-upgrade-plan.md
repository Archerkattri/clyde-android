# Clyde vs Gemini & Siri — competitive upgrade plan

> Goal: match and exceed Gemini (Android) and Siri/Apple Intelligence on UX and capability.
> Grounded in web research (late 2025 / 2026). Sources are in the research notes; key ones inline.

## TL;DR positioning
The wedge isn't out-featuring Google/Apple — it's **reliability + honesty + generality + privacy**:
*"The assistant that won't make things up, shows exactly what it's about to do, automates **any** app
(not a hand-picked few), helps with real technical work, and runs on **your** device under **your** Claude
subscription."*

## What the competition actually does (2026)
**Gemini (Android):** device actions via "Utilities", "Ask about this screen" + Circle-to-Search in the
overlay, **Gemini Live with camera/screen-share (free)**, cloud **Scheduled Actions** (recurring tasks,
cap 10), Connected Apps (Workspace + Spotify/GitHub; "@" invocation removed → natural routing), Nano-Banana
image gen, agentic multi-step (food/grocery/rideshare only, sandboxed, alert-before-purchase), Personal
Context (free Mar 2026). UX: pill overlay + multicolor→blue glow, live histogram, power-button summon.
**Weaknesses (= our openings):** false "Action failed" on alarms/timers (known issue), regressed basics
(calls/timers/follow-ups), slow + verbose (Google cut latency ~40% & trimmed verbosity in 2026),
power-button hijack resentment, thin 3rd-party ecosystem, unnatural barge-in "stutter", personalization
gated (18+, no work accounts, excluded in EEA/UK).

**Siri / Apple Intelligence:** App Intents/Shortcuts, Type-to-Siri, richer NLU + follow-ups, ChatGPT
handoff (opt-in), Visual Intelligence (camera + onscreen in iOS 26), Writing Tools/Genmoji, edge-glow UI.
The flagship **personal-context / onscreen-awareness / in-app-actions Siri was delayed 2+ years** and is
only reaching consumers fall 2026 (iOS 27), now **powered by Google Gemini in Private Cloud Compute**.
**Weaknesses:** fails trivial facts (date!), the "**here's what I found on the web**" deflection, limited
app actions, hallucinated notification summaries (pulled), credibility damage from perpetual delay.

## What Clyde already gets right (protect these — research confirms them)
- **Act only after the user stops talking** (no speculative early-finish) — matches "treat silence as
  end-of-utterance only when ASR confirms finality." `[[clyde-voice-no-early-finish]]`
- **`ask_user`: one question at a time, voice-or-tap, clarify-don't-cancel** — matches the validated
  "ask one specific clarifying question; users prefer clarification over cancellation." `[[clyde-ask-user-voice-or-tap]]`
- **`safe`/`consequential` + confirm tokens + show full URLs + no money movement** — risk-scaled friction.
- **General Android primitives + UI fallback, NO per-app scripts** — more general than Gemini's whitelist.
- **Flat "Warm Paper, Live Blue" + Clawd**, no heavy translucency — validated by the iOS "Liquid Glass"
  legibility backlash. Distinctive, anti-slop.

## Roadmap (prioritized)

### P0 — sharper, faster, more trustworthy (the basics Gemini/Siri fumble)
1. **Honest, terse, action-first answers** *(system prompt)* — never deflect to "here's what I found on
   the web", never fabricate device state/contacts, say "I'm not sure — want me to check?"; 1–2 sentence
   spoken answers; confirm actions concretely ("Alarm set for 7 AM"). Directly counters Gemini verbosity
   + Siri deflection. **[Batch 1 ✓]**
2. **Follow-up suggestion chips** under the answer (tap to ask next) — rich-response parity; turns a
   dead-end into a tap. Brain emits up to 3; overlay renders Blue chips. **[Batch 1 ✓]**
3. **Live voice amplitude** — the listening orb/voice-light reacts to real mic RMS (not a canned breathe),
   matching Gemini's histogram / Siri's reactive glow "alive" feel. **[Batch 1 ✓]**
4. **First-sentence streaming TTS** — speak each sentence as it streams (`delta`) instead of waiting for
   the whole answer; hit the 200–500 ms natural-feel window. **[Batch 2]**
5. **Reliable action confirmation** — verify device actions actually took effect and say so; never a
   false "done". (Clyde already returns tool results; tighten the verify-then-confirm loop.) **[Batch 2]**

### P1 — clear differentiation
6. **"What's on my screen?"** as a first-class quick action (Clyde already has a11y read + screenshot).
7. **Tiered auto-approval + plan preview** — auto-run safe+reversible silently, hard-gate only
   irreversible (payments/sends/deletes), show an editable step plan for multi-step tasks. Fixes the
   measured "93% rubber-stamp" over-prompting problem (Anthropic auto-mode telemetry).
8. **Undo affordance** for reversible device actions ("muted DND — undo?") instead of pre-confirming.
9. **On-device technical assistant** — read a log, explain a stack trace, write/run a Termux command
   (Claude-edge; Gemini/Siri structurally can't).
10. **Eyes-busy mode** — screen-off/driving → voice-only + terser + more implicit confirmation.

### P2 — high ceiling
11. **Memory + "What Clyde remembers" screen** (view/edit/delete, incognito session, cite memory inline).
12. **Opt-in proactive brief** (Pulse-style, finite, every suggestion explains its trigger).
13. **Live camera/screen-share Q&A** (Gemini Live parity) — large; needs continuous vision streaming.
14. **Confidence-signaled actions / deny-and-continue** safety polish.

## Status
- **Batch 1** (P0 #1–3) — shipped **v0.1.42**: honest/terse "answer-don't-deflect" answering, follow-up
  suggestion chips, live mic-amplitude voice light.
- **Batch 2** (P1 #6, #9) — shipped **v0.1.43**: general screen-awareness ("what's on my screen" /
  summarize / reply-to-this in any app) + on-device technical help (read logs, explain errors, run
  diagnostics, code help) — capabilities Gemini/Siri structurally lack. System-prompt depth.
- **Batch 3** (P0 #4 partial — "show your work") — shipped **v0.1.44**: a live step feed during multi-step
  tasks (prior steps done/green, current pulsing) — agentic transparency Gemini/Siri don't offer.
- **Batch 4** (P0 #4 — latency) — shipped **v0.1.45**: streaming TTS. Speaks the first sentence of a
  multi-sentence answer the instant it's ready (rest queued after), hitting the natural-feel window.
  Conservative: short answers unchanged; auto-listen rides the final chunk (fires once). **Needs
  on-device audio confirmation** (TTS timing/queueing can't be tested on PC).
- Next (need on-device iteration): tiered approval + plan-preview; recurring scheduled actions
  (reschedule-on-fire has alarm-loop risk → test firing on a real phone); barge-in (needs AEC).
