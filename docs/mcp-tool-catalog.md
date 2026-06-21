# 04 — Phone-control tool catalog (the "hands")

These are the tools the brain exposes to Claude (Agent SDK in-process tools, or an MCP server — same schemas either way). Each is tagged with its **tier**, **safety** class, and **route** (how the handler acts). The brain only registers tools whose tier is live per `capabilities()`.

Safety: **S** = safe (run freely) · **C** = consequential (requires `confirm()` token). Route: **app** = via LocalControlServer 8766 · **termux** = `termux-*` CLI · **rish** = Shizuku shell · **su** = root shell.

---

## Meta tools (always on)

```jsonc
// capabilities — S
{ "name": "capabilities",
  "description": "Report which control tiers and permissions are currently available. Call FIRST when choosing how to do something.",
  "input_schema": { "type":"object", "properties":{}, "required":[] } }
// returns: {tier0,tier1,tier2,tier3, perms:{sms,call,location,...}, accessibility, shizuku, root}

// confirm — S (but gates C tools)
{ "name":"confirm",
  "description":"Ask the user to approve a consequential action. Returns a one-time token to pass to the action tool.",
  "input_schema":{"type":"object","properties":{
    "summary":{"type":"string"},"details":{"type":"string"}},"required":["summary"]} }
// returns: {approved:boolean, token?:string}
```

---

## Tier 0 — Intents + Termux:API (no special perms)

### Android intents (route: app)
```jsonc
{ "name":"launch_app","safety":"S","input_schema":{"type":"object",
  "properties":{"package":{"type":"string"},"query":{"type":"string","description":"or fuzzy app name"}}}}

{ "name":"set_alarm","safety":"S","input_schema":{"type":"object",
  "properties":{"hour":{"type":"integer"},"minutes":{"type":"integer"},"message":{"type":"string"}},
  "required":["hour","minutes"]}}

{ "name":"set_timer","safety":"S","input_schema":{"type":"object",
  "properties":{"seconds":{"type":"integer"},"message":{"type":"string"}},"required":["seconds"]}}

{ "name":"start_call","safety":"C","input_schema":{"type":"object",
  "properties":{"number":{"type":"string"},"contact":{"type":"string"},"token":{"type":"string"}}}}

{ "name":"send_sms","safety":"C","input_schema":{"type":"object",
  "properties":{"to":{"type":"string"},"body":{"type":"string"},"token":{"type":"string"}},
  "required":["to","body","token"]}}

{ "name":"navigate_to","safety":"S","input_schema":{"type":"object",
  "properties":{"destination":{"type":"string"},"mode":{"enum":["drive","walk","transit","bike"]}},
  "required":["destination"]}}

{ "name":"add_calendar_event","safety":"C","input_schema":{"type":"object",
  "properties":{"title":{"type":"string"},"startIso":{"type":"string"},"endIso":{"type":"string"},
  "location":{"type":"string"},"token":{"type":"string"}},"required":["title","startIso"]}}

{ "name":"open_url","safety":"S","input_schema":{"type":"object",
  "properties":{"url":{"type":"string"}},"required":["url"]}}  // brain must show full URL in summary

{ "name":"share_text","safety":"S","input_schema":{"type":"object",
  "properties":{"text":{"type":"string"},"targetPackage":{"type":"string"}},"required":["text"]}}
```

### Termux:API device functions (route: termux)
One tool per useful `termux-*` command. All **S** except where noted.
```jsonc
{ "name":"get_battery" }                          // termux-battery-status
{ "name":"get_location" }                          // termux-location  (needs perm)
{ "name":"clipboard_get" } { "name":"clipboard_set","input_schema":{"properties":{"text":{}}}}
{ "name":"notify","input_schema":{"properties":{"title":{},"content":{},"id":{}}}}  // termux-notification
{ "name":"torch","input_schema":{"properties":{"on":{"type":"boolean"}}}}
{ "name":"vibrate","input_schema":{"properties":{"ms":{"type":"integer"}}}}
{ "name":"tts_speak","input_schema":{"properties":{"text":{}}}}    // or use app /speak
{ "name":"mic_record","safety":"C","input_schema":{"properties":{"seconds":{}}}}
{ "name":"camera_photo","safety":"C","input_schema":{"properties":{"camera":{}}}}
{ "name":"list_sms","safety":"C" }                 // termux-sms-list (privacy → confirm)
{ "name":"list_contacts","safety":"C" }
{ "name":"list_call_log","safety":"C" }
{ "name":"read_sensor","input_schema":{"properties":{"type":{}}}}
{ "name":"wifi_info" } { "name":"volume_set","input_schema":{"properties":{"stream":{},"level":{}}}}
{ "name":"download","input_schema":{"properties":{"url":{}}}}
```

---

## Tier 1 — Accessibility (no root; route: app → AccessibilityService)

```jsonc
{ "name":"ui_dump","safety":"S","description":"Return the on-screen accessibility node tree as structured text.",
  "input_schema":{"type":"object","properties":{}} }
// returns nodes: [{nodeId,role,text,desc,bounds:[l,t,r,b],clickable,editable,scrollable,focused,package}]

{ "name":"tap","safety":"S","input_schema":{"type":"object",
  "properties":{"nodeId":{"type":"string"},"x":{"type":"integer"},"y":{"type":"integer"}}}}

{ "name":"type_text","safety":"S","input_schema":{"type":"object",
  "properties":{"nodeId":{"type":"string"},"text":{"type":"string"}},"required":["text"]}}

{ "name":"swipe","safety":"S","input_schema":{"type":"object",
  "properties":{"x1":{},"y1":{},"x2":{},"y2":{},"ms":{"type":"integer"}},"required":["x1","y1","x2","y2"]}}

{ "name":"global_action","safety":"S","input_schema":{"type":"object",
  "properties":{"action":{"enum":["BACK","HOME","RECENTS","NOTIFICATIONS","QUICK_SETTINGS","LOCK","SCREENSHOT","POWER_DIALOG"]}},
  "required":["action"]}}

{ "name":"screenshot","safety":"S","description":"Capture the screen (fallback when ui_dump is empty: Compose/WebView/games). Returns image for Claude vision.",
  "input_schema":{"type":"object","properties":{}} }
```

---

## Tier 2 — Shizuku / rish (ADB powers, no root; route: rish)

Only registered when `capabilities().tier2`. The brain prefers Tier 0/1 and uses these when a clean intent/accessibility path doesn't exist.
```jsonc
{ "name":"shell","safety":"C","description":"Run a shell command at ADB (shell-UID) privilege via Shizuku. Consequential.",
  "input_schema":{"type":"object","properties":{"cmd":{"type":"string"},"token":{"type":"string"}},"required":["cmd","token"]}}

{ "name":"input_tap","safety":"S","input_schema":{"properties":{"x":{},"y":{}},"required":["x","y"]}}      // rish: input tap
{ "name":"input_text","safety":"S","input_schema":{"properties":{"text":{}},"required":["text"]}}        // rish: input text
{ "name":"input_key","safety":"S","input_schema":{"properties":{"keycode":{}}}}                          // rish: input keyevent
{ "name":"uiautomator_dump","safety":"S" }                                                              // rish: uiautomator dump
{ "name":"screencap","safety":"S" }                                                                     // rish: screencap -p

{ "name":"pm_grant","safety":"C","input_schema":{"properties":{"pkg":{},"permission":{},"token":{}},"required":["pkg","permission","token"]}}
{ "name":"settings_put","safety":"C","input_schema":{"properties":{"namespace":{"enum":["system","secure","global"]},"key":{},"value":{},"token":{}},"required":["namespace","key","value","token"]}}
{ "name":"app_disable","safety":"C","input_schema":{"properties":{"pkg":{},"token":{}}}}
{ "name":"force_stop","safety":"C","input_schema":{"properties":{"pkg":{},"token":{}}}}
{ "name":"disable_phantom_killer","safety":"C","description":"settings put global settings_enable_monitor_phantom_procs false (+ device_config). Keeps the brain alive.","input_schema":{"properties":{"token":{}}}}
```

---

## Tier 3 — Root / su (route: su)

Only when `capabilities().tier3`. Superset of Tier 2 plus:
```jsonc
{ "name":"su_shell","safety":"C","input_schema":{"properties":{"cmd":{},"token":{}},"required":["cmd","token"]}}
{ "name":"inject_event","safety":"C","description":"Low-level /dev/input injection — works even on secure screens.",
  "input_schema":{"properties":{"device":{},"events":{},"token":{}}}}
{ "name":"make_persistent","safety":"C","description":"Install the brain as a boot service immune to Doze/phantom-killer.","input_schema":{"properties":{"token":{}}}}
{ "name":"grant_signature_perm","safety":"C","description":"Grant a signature/privileged perm to a package (e.g., for VoiceInteractionService experiments).","input_schema":{"properties":{"pkg":{},"permission":{},"token":{}}}}
```

---

## Gemini router (route: app)
```jsonc
{ "name":"delegate_to_gemini","safety":"C","description":"Hand a prompt to the Gemini app for things Claude can't do natively (image/video gen, on-device Nano).",
  "input_schema":{"type":"object","properties":{"prompt":{"type":"string"},"want":{"enum":["image","text","action"]},"token":{"type":"string"}},"required":["prompt","want"]}}
```

---

## Tool-selection guidance (put in system-prompt.md)

1. Call `capabilities()` once per session; cache it.
2. Prefer the **lowest-friction correct tool**: a clean **Tier 0 intent** > **Tier 1 accessibility** > **Tier 2 input** > **Tier 3**. Don't tap a screen when an intent exists.
3. To act on arbitrary app UI: `ui_dump` → reason → `tap`/`type_text`; if the tree is empty, `screenshot` → vision → `tap {x,y}` (or Tier 2 `input_tap`).
4. Every **C** tool requires a fresh `confirm()` token. Never fabricate tokens.
5. Respect hard-stops in `safety.ts` (no payments, no self-permission escalation without explicit user ask, always show full URLs).
