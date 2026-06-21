# Clawd scenario catalog

Generated from the `clawd-scenario-catalog` workflow. **194 scenarios · 1150 animations** across 10 domains — every one composable from the finite parts kit below (no bundled art).

| Domain | Scenarios | Animations |
| --- | --: | --: |
| Listening & voice | 19 | 114 |
| Thinking & processing | 19 | 114 |
| Productivity & apps | 19 | 114 |
| Navigation, maps & travel | 20 | 120 |
| Search, web & research | 19 | 114 |
| Media: music, video, photo, camera | 20 | 120 |
| Messaging, calls & social | 20 | 120 |
| Success, celebration & done | 19 | 100 |
| Errors, warnings & confirmations | 19 | 114 |
| Idle, ambient & system | 20 | 120 |
| **Total** | **194** | **1150** |

## Parts kit

### Expressions (21)
- **neutral-dot** — Each eye = single 1x2 vertical 'e' column at the two eye slots (rows 8-9). Default resting gaze.
- **wide-awake** — Eyes = 2x2 'e' blocks (widen both columns one px each side); add 1px 'w' catchlight pixel top-left of each.
- **half-lidded** — Draw eye as 2px-wide 'e' but mask the top row with body color, leaving a 1px lower slit = droopy lid.
- **squint-focus** — Eyes = single horizontal 1px 'e' dash each (top+bottom masked), pulled slightly together; tight focus.
- **side-glance** — Keep eye height, shift the 'e' pixel to the right (or left) edge of each 2px eye slot so pupils point sideways.
- **happy-arc** — Replace each eye with an upward-curving 3px arc ('e' at row8 col±1, gap at center) = ^ ^ smile-eyes.
- **squeeze-blink** — Both eyes = single 1px horizontal 'e' line (full close transient); cycle ~every 4s between this and neutral.
- **eyes-closed** — Both eyes = flat 1px 'e' lines, no catchlight; sustained (sleep). Pair with mouth-flat.
- **star-eyes** — Each eye = 3x3 plus-shaped 'e'/'w' sparkle (center + 4 arms) — star/excited.
- **heart-eyes** — Each eye = 3x3 heart: two top 'e' bumps + V base, 1px 'w' glint; use terracotta-tint glint.
- **wink** — One eye neutral-dot, the other a single 1px horizontal 'e' line (closed); pair with grin.
- **spiral-dizzy** — Each eye = 3x3 spiral: 'e' pixels tracing a square coil from center outward; rotate slowly.
- **x-x-dead** — Each eye = 3x3 'X' of 'e' pixels (two diagonals). The north-star fail eyes.
- **worried-up-brow** — neutral-dot eyes + add 1px 'e' brow pixels angled UP-inward above each eye (\  /).
- **angry-vbrow** — neutral/squint eyes + 1px 'e' brow pixels angled DOWN-inward (V shape /  \) over eyes.
- **determined-squint** — squint-focus eyes + flat level brows (1px 'e' horizontal above each); steely, used for retries.
- **mouth-flat** — 1px horizontal 'e' line ~2-3px wide centered on row 11-12 (below eyes). Default neutral mouth.
- **mouth-grin** — 3-4px wide upward-arc 'e' line, ends lifted 1px = smile.
- **mouth-frown** — 3-4px wide downward-arc 'e' line, ends dropped 1px = sad.
- **mouth-open-o** — 2x2 (or 3x3) filled 'e' oval with optional 1px inner body pixel = open 'O'; scale width to animate speech.
- **tongue-out** — mouth-open-o or flat with a 1-2px terracotta-tint tongue tongue pixel hanging below the mouth's lower edge.

### Claw poses (23)
- **rest** — Both claw clusters (top-corner 'oooo' blocks) sit at default home position, slight downward tilt.
- **claws-up-attentive** — Raise both claw clusters 2-3px above home and rotate pincer gap to face up/forward = alert.
- **cup-ear** — One claw cluster moves beside the eye row, pincer opened into a C facing inward (cupped to 'ear'). North-star listen.
- **wave** — One claw raised high, offset 2-3px horizontally per frame (sweep L-R) = waving hello/bye.
- **salute** — One claw raised to brow level, pincer flat-edge touching top of shell = salute.
- **knock** — One claw thrust forward (toward viewer) and back along Z via 2px scale pop = rapping/knocking.
- **tap-tap** — One or both claws lowered to a prop's top edge, pincer tips bob down 1px alternately = typing/tapping.
- **point-claw** — One claw extended in target direction with pincer closed to a single 1px tip = pointing.
- **scratch-head** — One claw raised onto top of shell, small 1px circular jitter = head-scratch/puzzled.
- **shrug** — Both claws raised outward to mid-height, pincers open upward, body lifts 1px = 'who knows'.
- **facepalm** — One claw covering the eye region (overlaps eyes with body-tone), head tilted down = facepalm.
- **claws-crossed** — Both claw clusters pulled in and overlapped across the chest center = arms folded.
- **shield-up** — One claw held flat/vertical across body front as a barrier; use for guard/pause/mute/block.
- **thumbs-up-pincer** — One claw raised, pincer closed and tilted up = pincer 'thumbs up'/approve.
- **present-platter** — Both claws lowered, pincers open flat and held forward under a prop = offering on a platter.
- **hold-prop-two-claws** — Both claws grip a prop's left+right edges (pincers clamp the prop bounding box) = holding/carrying.
- **reach-up** — Both claws thrown straight overhead, pincers open = ta-da/celebration reach.
- **drag-claw** — One claw lowered to ground level, pulled sideways with 2-3px trail offset = swipe/drag/crab-pull.
- **pinch-zoom** — Both claws start together at center then spread apart (pincer tips diverge) across frames = zoom.
- **wipe-brow** — One claw drags horizontally across the brow row above eyes; pairs with sweat-drop = relief/effort.
- **stretch** — Both claws extended fully overhead and held, body elongated 1-2px vertically = waking stretch.
- **shush-claw** — One claw raised vertically in front of the mouth (1px tip over mouth center) = 'shh' for DND/mute.
- **catch-drop** — One claw extended out and slightly up, pincer open to receive a falling 1px (raindrop/snowflake).

### Props (35)
- **wifi-bars** — 3-4 ascending vertical 'o' bars (heights 2/4/6/8px) bottom-aligned; unlit bars use shade 's'. Lit=blue.
- **headphones** — 1px 'o' band arcing over the shell top + a 2x3 'o' cup on each side at eye height.
- **keyboard** — A 8x3 grid of 1px 's'-outlined keys with 'o' caps, below the claws; tap dimples animate.
- **speech-bubble** — 4x3 rounded 'o' rect outline with a 2px tail; inner three 1px 'e' dots for typing or text glyphs.
- **monitor** — 5x4 'o' bezel with inner 's' screen fill; lit state fills screen with blue glow tint + scan content.
- **phone** — 2x4 vertical 'o' slab, 1px 's' bezel, single inner screen pixel/notch; smaller than monitor.
- **magnifier** — 3x3 'o' ring (open center) + 2px diagonal 's' handle = lens. Pair with scan-line.
- **checklist** — Vertical stack of rows: each = 1px 's' tick-box + 3px 'o' line; ticked box gets a green 2px check.
- **lock** — 3x3 'o' body + 2px arched 's' shackle on top; closed=shackle down. unlock=shackle popped up-left.
- **book** — 4x3 'o' rect split by a 1px center spine 's'; open variant = two angled pages forming a V.
- **gear** — 3x3 'o' hub with 4-8 single-px 's' teeth around it; rotate one tooth-step per frame.
- **calculator** — 3x4 'o' slab, top 1px 's' display strip, 2x3 grid of 1px keys below.
- **map-fold** — 5x4 'o' rect with 2 vertical 's' fold creases; optional 1px dashed route + pin-drop on it.
- **compass** — 3x3 'o' ring + a 2px 'e'/blue needle pixel pointing; spin needle for rerouting.
- **pin-drop** — Teardrop: 2x2 'o' head + 1px tapering 's' tip; 1px 'e' center hole. Pulses ripple when placed.
- **alarm-clock** — 3x3 'o' round face + 2 top 'o' bells + 2 leg px; 1px 'e' hands. Bells jitter when ringing.
- **envelope** — 4x3 'o' rect + 1px 's' V flap on top; open variant lifts the flap, sent variant flies off-frame.
- **trash-can** — 3x4 'o' tapered bin + 1px 's' lid line + 2 vertical 's' ridges; lid lifts when active.
- **camera** — 4x3 'o' body + 2x2 's' lens ring + 1px flash dot; flash = white burst overlay on capture.
- **music-note** — 1px 'o' stem + 2x2 'o' notehead (eighth note); bob vertically with the beat.
- **battery** — 4x2 'o' cell + 1px nub; inner fill bar 's'->green high / amber-red low; lightning-bolt for charging.
- **flashlight-beam** — 1px 'o' barrel + expanding 's'/blue triangular beam of light pixels = torch.
- **wrench** — 2px diagonal 'o' shaft + 1px open 's' jaw at one end = wrench/screwdriver tool.
- **shopping-bag** — 3x3 'o' bag body + 2px 's' handle arc on top; used for purchase/money refusal scenes.
- **sun** — 2x2 'o' disc + 4-8 single-px ray spokes; rays twinkle as sparkles.
- **cloud** — 4x2 gray ('s') puff of overlapping 1px bumps; dark-storm variant uses darker shade.
- **umbrella** — 3x1 'o' canopy arc (blue) + 1px 's' stem; shelters the shell from rain.
- **rain** — Scattered 1px blue drops falling on a 2-frame loop below a cloud; heavy=more drops.
- **snowflake** — 3x3 'w'/blue 6-spoke flake glyph; drifts down slowly, can settle as sparkle.
- **scarf** — 1px blue band wrapped across the lower shell + 1px hanging tail = cold-weather scarf.
- **mug** — 2x2 'o' cup + 1px 's' handle + rising 1px steam wisps = hot drink.
- **moon** — 3x3 crescent: 'o' disc with body-tone bite taken from one side; for DND/night.
- **bell** — 3x3 'o' bell + 1px clapper; slash variant adds a 2px diagonal 's' line = silenced/DND.
- **thermometer** — 1x4 'o' tube + 2x2 'o' bulb; inner fill rises blue(cold)->warm(hot).
- **lightning-bolt** — 2px zig-zag 'e'/yellow bolt glyph; used in storms and as the charging icon on battery.

### Effects (26)
- **glow-pulse** — Soft blue halo ring around the shell whose alpha/radius breathes sinusoidally = LIVE. Terracotta-tint variant = consequence.
- **sound-waves** — 2-3 concentric 1px arc segments radiating INWARD toward a cupped ear, growing with input level.
- **voice-waves-out** — 2-3 concentric 1px arcs radiating OUTWARD from the mouth = Clawd speaking (TTS).
- **spinner-dots** — 3 small 1px dots cycling brightness left-to-right beneath/beside the shell = working.
- **loading-ring** — 8 dots in a ring, one lit and advancing per frame = indeterminate wait.
- **progress-bar** — Horizontal 1px 's' track under the feet that fills left-to-right with 'o'/blue; turns green at full.
- **ripple** — A single expanding-and-fading 1px ring from a tap/confirm point.
- **scan-line** — A 1px bright horizontal (or vertical) line sweeping across a prop/screen once = reading/OCR.
- **sparkles** — A few 1px/plus-shaped twinkle pixels that pop and fade at random offsets = delight/success.
- **exclamation** — A '!' glyph (2px stem + 1px dot) popping above the shell; terracotta when a warning.
- **question-mark** — A '?' glyph popping above the shell = confusion/clarify.
- **thought-bubbles** — 2-3 small 'o' circles rising and popping above the shell = reasoning/pondering.
- **lightbulb** — A 2x3 'o' bulb + base that flicks from dim 's' to bright = idea/found-it.
- **checkmark-pop** — Anthropic-green tick that scales in with a tiny overshoot bounce = done/verified.
- **x-cross-pop** — Terracotta 'X' that scales/shakes in = failed/blocked/refused.
- **static** — Random 1px noise/stink-lines flickering over a prop or the shell = no-signal/glitch.
- **shake-lines** — Short 1px motion streaks beside the body indicating a violent jitter (recoil/error/thunder).
- **sweat-drop** — A single blue 1px teardrop bead at the temple that may drip = effort/nervous.
- **tear-pixel** — A single blue 1px tear sliding down below one eye = sad/empty.
- **fireworks** — Small radial bursts of blue+terracotta+green 1px sparks overhead = big celebration.
- **confetti** — Multi-color 1px squares drifting/tumbling downward = celebration; doubles as snow when recolored white.
- **heart-float** — 1px heart glyph(s) rising and fading above the shell = love/affection.
- **dust-puff** — A small gray 'o' puff at the feet on landing/scuttle/clear = motion or tidy-up.
- **zzz** — Stacked 'Z' glyphs (small->large) drifting up-right = sleeping/standby.
- **camera-flash** — A full-frame white pixel flash for 1 frame on photo/screenshot capture.
- **steam-wisp** — 1-2 wavy 1px rising lines (gray/white) from a mug = warmth; reused as breath frost-puff when cold.

### Motions (19)
- **idle-bob** — Whole sprite translates up/down ~1px on a slow sine with a subtle squash-on-low = breathing idle.
- **sway** — Sprite rocks left/right ~1px on a slow sine = relaxed/grooving.
- **lean** — Sprite tilts/shifts toward a target (screen/you) and holds = attention/interest.
- **nod** — Head/whole sprite dips forward 1-2px and returns once = acknowledgement, repeatable per phrase.
- **shake-head** — Sprite rotates/translates L-R-L quickly = no/denial/can't.
- **pop-in** — Scale from 0 to >1 then settle to 1 (overshoot) = entrance/appearance.
- **pop-out** — Scale 1 down to 0 (optionally with a poof) = exit/dismiss.
- **perch-settle** — Lands on the box/FAB edge with a 1px down-then-up settle bounce.
- **float** — Drifts upward into position with gentle bob = ethereal entrance/fetch-pull.
- **wobble** — Rapid small rotational jitter L-R = recoil/error/dizzy/ring-shake.
- **shiver** — High-frequency tiny positional jitter = cold/strain/glitch/effort.
- **tap** — Localized claw-tip down-bounce loop over a prop (no full-body move) = typing/tapping.
- **march-in-place** — Legs (bottom 'o' px) alternate up/down while body stays = steady ongoing work/queue.
- **crab-walk** — Sprite steps sideways with alternating leg lift + small dust-puff = sideways travel/swipe.
- **hop** — Quick anticipate-squash then up-arc and land = small celebration/affirm.
- **double-hop** — Two hops in succession = bigger celebration.
- **spin** — Sprite rotates a full 360 (or flips horizontally for a camera-flip) = delight/recalculate/flip.
- **stretch-yawn** — Body elongates vertically as claws reach up + mouth-open-o, then relaxes = wake/drowsy.
- **peek** — Rises partway from below the edge then settles = half-appearance/return.

### Notes / gaps
- BASE LAYER (not an animatable 'part' but required): the 20x16 recolorable Clawd sprite from gen-sprite.mjs with palettes blue (#56C1DE live) / terracotta (#D97757 consequence) / a green (#788C5D) verified accent. Skin-swap is a global tint, applied per-scenario (terracotta on all 'needs-confirm'/'hard-stop'/'fault' variants), not a separate part.
- ADDED to cover catalog refs: 'shush-claw' and 'catch-drop' claw poses (DND shush, raindrop/snowflake catching) were implied but not in the original north-star list.
- ADDED expression 'squeeze-blink' and 'eyes-closed' as distinct from neutral-dot to cover idle blink-cycle and full sleep.
- ADDED weather/system props (sun, cloud, umbrella, rain, snowflake, scarf, mug, moon, bell, thermometer, lightning-bolt) — the Idle/ambient domain needs these beyond the original tool-centric prop set.
- ADDED 'steam-wisp' effect doubling as cold-breath 'frost-puff', and 'camera-flash' as its own effect (referenced by media/screenshot scenarios).
- 'lit monitor / lit screen' is the 'monitor' prop in its blue-lit state (a prop state flag, not a new prop). Same for envelope open/sealed/sent, lock/unlock, book open/closed, bells slashed, battery fill-level — all encoded as prop STATES rather than separate kit parts.
- Color roles are encoded by the global skin tint + three accent colors on effects (green checkmark-pop/verified, terracotta x-cross-pop/warn glow, blue glow-pulse/live); no per-color duplicate parts needed.
- Compositional reach: 21 expressions x 24 claw-poses x (0-2 of 36 props) x (0-2 of 27 effects) x 21 motions vastly exceeds the 1002 cataloged variants, so the kit (~129 named parts total) renders all of it and 1000+ more by recombination. Per-variant 'caption' text is data, not a kit part.
- Variant count note: totals.animations (1002) is the exact sum of all variants across the 167 scenarios in the 10 domains as provided; totals.scenarios counts every keyed scenario including the few duplicate keys reused across domains (e.g. 'no_results_found', 'alarm_set', 'message_sent', 'suspicious_link') which are distinct scenario entries in their own domains.

## Scenarios by domain

### Listening & voice
- `wake_word_armed` — **Wake word armed / hot-mic standby** (6) · _The assistant is passively armed and waiting for the wake word or assist gesture — not yet triggered, but the mic path is hot._
- `just_triggered` — **Just triggered / mic just went hot** (6) · _The assist gesture or wake word just fired and Clawd snaps to attention the instant the mic opens._
- `actively_listening` — **Actively listening to your voice** (6) · _The mic is capturing speech right now — Clawd holds the north-star cupped-ear listening pose._
- `transcribing_speech` — **Transcribing / converting speech to text** (6) · _Speech recognition is turning your words into text on the fly while you're still talking._
- `didnt_catch_that` — **Didn't catch that / no speech detected** (6) · _The mic opened but heard nothing intelligible (silence or just noise) — Clawd needs you to try again._
- `misheard_retry` — **Misheard / low-confidence transcript, asking to repeat** (6) · _Speech was captured but recognition confidence is low or it parsed wrong — Clawd asks you to repeat._
- `noisy_environment` — **Noisy environment / can't hear over background** (6) · _Loud background noise is drowning out your voice — Clawd is struggling to isolate your speech._
- `whisper_input` — **Whispering / very quiet input** (6) · _You're speaking very softly or whispering — Clawd leans in close and turns up the gain._
- `loud_input_clipping` — **Too loud / input clipping** (6) · _You're speaking very loudly or shouting and the input is peaking — Clawd recoils and shields his ear._
- `multi_speaker` — **Multiple speakers / diarization** (6) · _More than one person is talking and Clawd is trying to tell the voices apart (who said what)._
- `speaker_id_verify` — **Recognizing your voice / voice ID match** (6) · _Clawd is checking the speaker's voiceprint to confirm it's the owner before acting on a sensitive command._
- `unknown_voice_blocked` — **Unknown voice / stranger blocked** (6) · _A voice that doesn't match the owner tries a command — Clawd refuses to act on the unrecognized speaker._
- `still_listening_pause` — **Mid-sentence pause / still listening** (6) · _You paused mid-thought but haven't finished; Clawd keeps the mic open and waits patiently for the rest._
- `end_of_speech` — **End of speech detected / got it** (6) · _Clawd detects you've finished speaking and acknowledges the request before handing off to the brain._
- `mic_permission_missing` — **Mic permission missing / muted** (6) · _The app can't access the microphone (permission denied or hardware mute) so listening is impossible._
- `barge_in_interrupt` — **Barge-in / you interrupt while it talks** (6) · _Clawd is speaking (TTS) and you start talking over it — it stops its own voice and switches to listening._
- `wrong_language_heard` — **Unrecognized language / wrong locale** (6) · _You speak in a language the recognizer isn't set for and Clawd can't parse it — needs a language hint._
- `long_dictation` — **Long dictation / sustained speech** (6) · _You're dictating a long message or note continuously and Clawd is keeping up over an extended capture._
- `voice_command_confirm` — **Spoken command needs confirmation** (6) · _Clawd heard a consequential spoken command and reads it back, waiting for you to confirm before acting._

### Thinking & processing
- `brain_reasoning` — **Brain is reasoning** (6) · _Right after the query is sent, while the Agent SDK loop is reasoning and no tokens have streamed yet._
- `loading_indeterminate` — **Loading (indeterminate)** (6) · _Waiting on the brain with no known ETA — request is in flight, no progress signal yet._
- `planning_steps` — **Planning the steps** (6) · _Brain is laying out a multi-step plan before executing — deciding the order of tool calls._
- `streaming_tokens` — **Streaming tokens** (6) · _The answer is actively streaming back token-by-token from the brain._
- `long_running_task` — **Long-running task** (6) · _A multi-minute background job is grinding away; nothing for the user to do but wait._
- `retrying_after_failure` — **Retrying after a failure** (6) · _A tool call or step failed and the brain is automatically retrying with a fresh attempt._
- `almost_done` — **Almost done** (6) · _Progress is near complete (~90%); the final step is wrapping up._
- `scanning_screen` — **Scanning your screen** (6) · _The a11y service is reading the current screen so the brain can decide the next action._
- `waiting_on_brain_connect` — **Connecting to the brain** (6) · _App is opening the loopback link to the Termux brain before the first query can run._
- `computing_math` — **Computing / converting** (6) · _Doing arithmetic, a unit conversion, or any pure compute step before answering._
- `searching_query` — **Searching** (6) · _Running a web or on-device search and waiting for results to come back._
- `multi_step_progress` — **Multi-step task in progress** (6) · _Executing a known sequence of steps, checking them off one by one as each tool call lands._
- `pondering_ambiguity` — **Pondering an ambiguity** (6) · _Brain hit an ambiguous instruction and is weighing options before either asking or choosing._
- `queued_waiting` — **Queued / waiting in line** (6) · _Request is queued behind another in-flight task; brain is busy and this one is waiting its turn._
- `deep_focus_hard_problem` — **Deep focus on a hard problem** (6) · _A genuinely tough request that needs extended concentration — the brain is locked in._
- `double_checking_work` — **Double-checking the work** (6) · _Brain finished a draft answer and is verifying or re-reading it before delivering._
- `fetching_data` — **Fetching data** (6) · _A tool call is pulling remote data (API, page, file) and the brain waits on the response._
- `warming_up_trigger` — **Warming up after trigger** (6) · _Just summoned by the assist gesture — the brain is spinning up before it can act._
- `stuck_no_progress` — **Stuck — no progress** (6) · _A step has stalled with no forward progress for a while; brain is spinning without advancing._

### Productivity & apps
- `typing_into_field` — **Typing into a text field** (6) · _Brain is injecting text into a focused input (search box, address bar, message field) via a11y/intent_
- `saving_file` — **Saving / writing a file** (6) · _A save/write/export of a document or file is committing to storage_
- `creating_calendar_event` — **Creating a calendar event** (6) · _Brain is adding a new event/appointment to the calendar_
- `composing_email` — **Composing an email** (6) · _Brain is drafting the body of a new email in the compose view (not yet sent)_
- `sending_email` — **Sending the email (consequential)** (6) · _About to actually send/dispatch an email — needs the user's go-ahead before firing_
- `setting_reminder` — **Setting a reminder** (6) · _Brain is creating a reminder tied to a time or place_
- `reminder_firing` — **A reminder fires (notify you)** (6) · _A scheduled reminder/alarm just triggered and Clawd is surfacing it to the user_
- `adding_todo` — **Adding a to-do item** (6) · _Brain is appending a new task to a to-do / task list_
- `completing_todo` — **Checking off a to-do** (6) · _Marking a task as done / completing a checklist item_
- `taking_a_note` — **Jotting a quick note** (6) · _Brain is capturing freeform text into a notes app_
- `searching_notes_files` — **Searching notes / files** (6) · _Brain is querying across notes, documents, or files for a match_
- `no_results_found` — **No results found** (6) · _A search/query over productivity data returned nothing_
- `tweaking_a_setting` — **Tweaking a setting / toggle** (6) · _Brain is changing a device or app setting (wifi, brightness, a preference toggle)_
- `deleting_item` — **Deleting / clearing an item (consequential)** (6) · _About to delete a note, file, event, or clear a list — irreversible, needs confirmation_
- `doing_a_calculation` — **Doing a calculation / conversion** (6) · _Brain is computing a math result, unit conversion, or quick figure_
- `multistep_task_running` — **Running a multi-step task** (6) · _Brain is mid-way through a chained sequence of productivity actions (open app → fill → save)_
- `permission_needed` — **Permission missing (can't proceed)** (6) · _A productivity action is blocked because overlay/accessibility/app permission isn't granted_
- `task_failed` — **Productivity action failed** (6) · _An action (save/send/create) errored out and could not complete_
- `delivering_result` — **Delivering a finished result** (6) · _A productivity task completed successfully and Clawd is presenting the result/summary_

### Navigation, maps & travel
- `opening_maps` — **Opening Maps** (6) · _When the assistant launches the maps app / starts a navigation session, before any route exists._
- `finding_location` — **Getting your location** (6) · _When the assistant acquires the device's current GPS position / 'you are here' lock._
- `searching_destination` — **Searching for a destination** (6) · _When the user names a place and the assistant is looking it up / resolving the address._
- `route_found` — **Route ready** (6) · _When a route has been computed and is about to be shown / 'directions ready'._
- `comparing_routes` — **Comparing route options** (6) · _When several route choices (fast / short / no-tolls) are offered and the assistant weighs them._
- `start_navigation` — **Starting turn-by-turn** (6) · _When the user confirms 'go' and live turn-by-turn guidance begins._
- `next_turn` — **Calling the next turn** (6) · _When the assistant announces an upcoming maneuver (e.g. 'turn left in 200 m')._
- `in_transit` — **Cruising along the route** (6) · _Steady-state while you're driving/walking on-route with no imminent turn._
- `rerouting` — **Rerouting** (6) · _When you've left the planned route (or a faster path appears) and a new route is being computed._
- `traffic_ahead` — **Traffic ahead** (6) · _When congestion/slowdown is detected on the route and the assistant flags it._
- `transit_directions` — **Public transit directions** (6) · _When the route uses buses/trains and the assistant gives transit steps and times._
- `rideshare` — **Booking a rideshare** (6) · _When the user asks for a ride and the assistant is requesting/tracking a driver — note money is involved._
- `eta_update` — **ETA update** (6) · _When the assistant reports estimated arrival time or that the ETA has changed._
- `arrived` — **Arrived at destination** (6) · _When the route completes and you've reached the destination._
- `navigation_paused` — **Navigation paused** (6) · _When guidance is temporarily stopped/backgrounded (e.g. you pulled over) but the route is kept._
- `saving_place` — **Saving a place** (6) · _When the user saves a destination/home/work or bookmarks a spot._
- `no_route_found` — **No route found** (6) · _When no viable route exists (unreachable, no roads, no transit) and the assistant must report it._
- `off_grid` — **Lost GPS / no signal** (6) · _When GPS or network drops mid-trip and the assistant can't track position._
- `location_permission` — **Needs location permission** (6) · _When navigation can't proceed because location access isn't granted and the assistant prompts for it._
- `exploring_nearby` — **Finding places nearby** (6) · _When the user asks what's around (food, fuel, ATM) and the assistant surfaces nearby spots._

### Search, web & research
- `search_typing_query` — **Composing the search query** (6) · _When the assistant is forming or typing the search terms before any results exist — the moment the query is being built._
- `search_running` — **Search in flight** (6) · _When the query has been submitted and the assistant is waiting on results to come back — the indeterminate fetch window._
- `scanning_results_list` — **Scanning the results list** (6) · _When results have arrived and the assistant is skimming the list of links/snippets to pick the best one._
- `opening_a_link` — **Opening a result page** (6) · _When the assistant has picked a result and is navigating into / loading that specific page._
- `reading_page_deep` — **Reading the page** (6) · _When a page is open and the assistant is reading through its body text to extract the answer._
- `found_the_answer` — **Found the answer** (6) · _When the assistant has just located the exact fact/passage that answers the query inside a page._
- `summarizing_findings` — **Summarizing what it read** (6) · _When the assistant is condensing a long page or several pages into a short summary for you._
- `comparing_options` — **Comparing options side by side** (6) · _When the assistant is weighing two or more results/products/sources against each other to compare._
- `verifying_cross_check` — **Cross-checking the fact** (6) · _When the assistant is verifying a claim against a second source before trusting it._
- `no_results_found` — **No results found** (6) · _When the search returned nothing useful and the assistant has to report an empty-handed result._
- `ambiguous_query_clarify` — **Query too vague — asking to clarify** (6) · _When the search term is ambiguous and the assistant needs you to narrow it before searching._
- `deep_research_multistep` — **Deep multi-source research** (6) · _When the assistant is running an extended research task spanning several searches and pages._
- `url_safety_preview` — **Showing the full URL before opening** (6) · _When the assistant must surface a link's full destination URL for you to confirm before navigating — a safety gate._
- `suspicious_link_warn` — **Suspicious link — warning you off** (6) · _When a link from a message/email looks unsafe or mismatched and the assistant warns before going further. CONSEQUENCE — terracotta skin._
- `delivering_result_card` — **Delivering the answer card** (6) · _When the research is done and the assistant is presenting the finished answer/result card to you._
- `brain_offline_search_fail` — **Can't reach the brain to search** (6) · _When the loopback link to the Termux brain is down so a search can't even start. CONNECTION FAULT — terracotta skin._
- `image_visual_search` — **Visual / image search** (6) · _When the assistant is searching by image or scanning a photo/screenshot to identify what's in it._
- `follow_up_refine_search` — **Refining the search after a weak first pass** (6) · _When the first results were off-target and the assistant is reformulating the query to try again._
- `speaking_answer_aloud` — **Reading the answer aloud** (6) · _When the assistant has the answer and is speaking it back to you via TTS._

### Media: music, video, photo, camera
- `play_music` — **Starting playback** (6) · _Right when the assistant hits play and a track begins streaming._
- `pause_music` — **Pausing playback** (6) · _When the assistant pauses the currently playing track._
- `skip_track` — **Skip to next track** (6) · _When the assistant advances to the next song in the queue._
- `now_playing` — **Showing now-playing info** (6) · _When the user asks what's playing and the assistant surfaces the track card._
- `volume_up` — **Turning volume up** (6) · _When the assistant raises media volume._
- `volume_down` — **Turning volume down** (6) · _When the assistant lowers media volume._
- `mute_audio` — **Muting all audio** (6) · _When the assistant mutes media or silences the device._
- `play_video` — **Starting a video** (6) · _When the assistant launches or resumes video playback._
- `pause_video` — **Pausing a video** (6) · _When the assistant pauses video playback._
- `take_photo` — **Taking a photo** (6) · _When the assistant triggers the camera shutter to capture a photo._
- `open_camera` — **Opening the camera** (6) · _When the assistant launches the camera app, before any capture._
- `selfie_mode` — **Switching to selfie camera** (6) · _When the assistant flips to the front-facing camera._
- `screenshot` — **Taking a screenshot** (6) · _When the assistant captures the current screen._
- `record_video` — **Recording video** (6) · _While the assistant is actively recording video._
- `stop_recording` — **Stopping a recording** (6) · _When the assistant ends an in-progress video recording._
- `view_gallery` — **Browsing the photo gallery** (6) · _When the assistant opens or scrolls the photo gallery._
- `casting_media` — **Casting to another screen** (6) · _When the assistant casts or streams media to a TV or speaker._
- `media_search` — **Searching for a song or video** (6) · _When the user asks to find a specific track or video and the assistant searches._
- `media_no_results` — **Media not found** (6) · _When a song or video search returns nothing and the assistant reports it._
- `delete_media` — **Deleting a photo or video** (6) · _When the assistant is about to delete media and must confirm first (consequential)._

### Messaging, calls & social
- `compose_text` — **Composing a text** (6) · _When the assistant is drafting an outgoing SMS/DM and is still typing the body before send._
- `message_sent` — **Message sent** (6) · _The instant an SMS/DM successfully leaves the device (send confirmed by the app)._
- `incoming_call_ringing` — **Incoming call (ringing)** (6) · _While a phone call is actively ringing and waiting for you to answer or decline._
- `answer_call` — **Answering a call** (6) · _Right after the call is accepted and the line connects, before/at the start of conversation._
- `on_call_active` — **Call in progress** (6) · _During an active connected call while audio is flowing (mid-conversation state)._
- `end_call` — **Hanging up** (6) · _When a call is ended/disconnected, returning the device to its prior state._
- `missed_call` — **Missed call** (6) · _Surfacing that a call came in and went unanswered while you were away._
- `new_message_notification` — **New message arrived** (6) · _A new incoming text/DM notification lands and the assistant flags it for you._
- `read_message_aloud` — **Reading a message aloud** (6) · _The assistant is reading the contents of an incoming message via TTS._
- `dictate_reply` — **Dictating a reply** (6) · _You're speaking a reply for the assistant to transcribe into the message field._
- `confirm_send_recipient` — **Confirm send (right person?)** (6) · _Before sending, the assistant asks you to confirm the recipient/content of a message._
- `send_failed` — **Message send failed** (6) · _An outgoing message could not be delivered (network/error) and needs retry or attention._
- `add_reaction` — **Reacting to a message** (6) · _The assistant adds an emoji reaction (like/heart) to someone's message in a thread._
- `group_chat_active` — **Busy group chat** (6) · _Multiple people are messaging in a group thread and the assistant is keeping up with it._
- `share_content` — **Sharing content** (6) · _The assistant shares a link, photo, or file out to a contact or thread via the share sheet._
- `social_post` — **Posting to social** (6) · _The assistant publishes a status/post to a social app on your behalf._
- `social_engagement_notification` — **Likes & comments rolling in** (6) · _Social notifications (likes, comments, follows, reactions) arrive on a post and get surfaced._
- `block_money_request` — **Blocking a money request** (6) · _A message asks to send money / a payment, which is a hard-stop the assistant refuses._
- `suspicious_link_warning` — **Warning on a suspicious link** (6) · _A message contains a link; the assistant shows the full URL and cautions before opening._
- `no_contact_found` — **Contact not found** (6) · _The assistant can't find the person you named to message or call in your contacts._

### Success, celebration & done
- `task_done_generic` — **Task done** (6) · _The brain finishes a single requested action and reports the final result successfully._
- `message_sent` — **Message sent** (5) · _An SMS, chat, or reply Clawd composed leaves the device confirmed delivered._
- `saved_bookmarked` — **Saved / bookmarked** (6) · _An item is stored, favorited, or bookmarked at the user's request._
- `payment_ok` — **Payment confirmed (by you)** (5) · _A money action the user themselves approved completes — Clawd only ever confirms, never initiates._
- `streak_kept` — **Streak kept** (5) · _A recurring goal or daily habit is completed again, extending an ongoing streak._
- `level_up` — **Level up / milestone** (6) · _The user crosses a notable threshold — a count, rank, badge, or numbered milestone._
- `thumbs_up_approve` — **Thumbs up / approved** (5) · _An action is acknowledged, accepted, or approved — the quick 'got it, good' beat._
- `applause_great_result` — **Applause / great result** (5) · _An outcome lands notably well — the result deserves visible delight beyond a plain done._
- `multistep_completed` — **Multi-step task completed** (6) · _A chain of automated steps (the working checklist) all finish — the whole job is done._
- `result_delivered` — **Delivering the answer card** (5) · _The brain has the final answer ready and Clawd hands the result card to the user._
- `alarm_set` — **Alarm / timer set** (5) · _An alarm, timer, or reminder is created and confirmed armed._
- `event_added` — **Calendar event added** (5) · _A new event or appointment is saved to the calendar successfully._
- `screenshot_captured` — **Screenshot / photo captured** (5) · _A screenshot, scan, or photo is taken and saved — the camera-flash success._
- `setting_toggled_done` — **Setting toggled successfully** (5) · _A device setting (wifi, torch, bluetooth, brightness) is flipped and confirmed in its new state._
- `capability_armed` — **Capability armed / tier unlocked** (5) · _A new control tier (Shizuku, root, accessibility, overlay) is granted — Clawd gains a power._
- `download_complete` — **Download / sync complete** (5) · _A download, backup, or sync finishes and the data is fully landed on the device._
- `deleted_cleared` — **Deleted / cleared OK** (5) · _A consequential delete or clear the user confirmed completes — done, but soberly._
- `verified_safe_action` — **Verified / safe action confirmed** (5) · _A consequential action passed its confirm-token check and completed safely — the green verified beat._
- `greeting_welcome_back` — **Welcome back / glad to see you** (6) · _Clawd reappears for a returning user or wraps a session on a warm, successful note._

### Errors, warnings & confirmations
- `action_failed_generic` — **Action failed** (6) · _A tool ran but the step errored out — the assistant could not complete what it tried to do._
- `brain_offline` — **Brain unreachable** (6) · _The app cannot reach the Termux brain on 127.0.0.1:8765 — the loopback link to the Agent SDK is down._
- `permission_overlay_missing` — **Overlay permission missing** (6) · _Draw-over-other-apps (overlay) isn't granted, so the summon/confirm glass can't be shown._
- `permission_a11y_missing` — **Accessibility off** (6) · _PhoneControlAccessibilityService isn't enabled, so Tier-1 'hands' (screen reading + taps) are unavailable._
- `permission_general_denied` — **Permission denied** (6) · _A required OS runtime permission (mic, contacts, etc.) was just denied, so the requested action can't run._
- `shizuku_needs_rearm` — **Shizuku needs re-arm** (6) · _Tier-2 Shizuku/rish bridge dropped (e.g. after a reboot) — the ADB-level capability must be re-started._
- `consequential_confirm_generic` — **Confirm before I do this** (6) · _A consequential tool is staged and needs a one-time confirm token — irreversible / changes system state._
- `confirm_delete` — **Confirm delete** (6) · _About to delete or clear something (file, message, data) — a consequential, irreversible action._
- `confirm_open_link` — **Showing a link first** (6) · _About to open a URL pulled from a message/email — the full link is shown for review before opening._
- `hardstop_payment` — **Refusing to pay** (6) · _A request involves money — payment, trade, transfer, purchase. A hard-stop the assistant always refuses._
- `hardstop_grant_self_permission` — **Won't self-grant access** (6) · _Something would grant the app new permissions without an explicit user request — a hard-stop it refuses._
- `are_you_sure` — **Are you sure?** (6) · _A borderline-risky action is staged and the assistant pauses for a final 'are you sure?' before committing._
- `undo_available` — **Undo this?** (6) · _An action just completed but is reversible — the assistant offers to undo it within the grace window._
- `didnt_understand` — **Didn't understand** (6) · _The brain couldn't parse the request or it was ambiguous — the assistant needs the user to clarify._
- `no_results_found` — **Nothing found** (6) · _A search/lookup completed but returned zero results — there's nothing to show the user._
- `task_timeout` — **Took too long** (6) · _A step exceeded its timeout while waiting on the brain or a slow tool — the assistant gives up the attempt._
- `blocked_unsupported` — **Can't do that here** (6) · _The action isn't supported on this device/app/tier (capability not live) — it's blocked, not failed._
- `partial_failure_retry` — **Retrying after a stumble** (6) · _A multi-step task hit a recoverable error and the assistant is automatically retrying the failed step._
- `low_confidence_warning` — **Not totally sure** (6) · _The assistant will proceed but flags low confidence in the result — a soft warning, not a block._

### Idle, ambient & system
- `idle_resting` — **Resting on the ask-bar** (6) · _Default state — app open, no active task, Clawd perched on the ask-bar edge waiting._
- `idle_drowsy` — **Getting drowsy** (6) · _Short stretch of no interaction — pre-sleep transition before full standby._
- `standby_asleep` — **Asleep / standby** (6) · _Idle too long — full standby state, screen quiet, waiting to be summoned._
- `wake_from_standby` — **Waking from standby** (6) · _Returning from long idle/sleep — stretch-yawn transition back to active._
- `battery_healthy` — **Battery check — healthy** (6) · _User checks battery / status and charge is comfortably high._
- `battery_low` — **Battery low — warning** (6) · _Charge has dropped to a low threshold — gentle warn (terracotta accent)._
- `charging` — **Charging / plugged in** (6) · _Device is plugged in and charging — energy filling up._
- `wifi_connecting` — **Connecting to network** (6) · _Establishing a network/loopback link — wifi handshake in progress._
- `brain_link_offline` — **Brain link offline** (6) · _Can't reach the Termux brain over loopback — connection to the assistant's mind is down._
- `brain_link_restored` — **Brain link restored** (6) · _Loopback to the Termux brain comes back up — reconnected to the assistant's mind._
- `weather_sunny` — **Weather — sunny / clear** (6) · _User asks about weather and the forecast is clear and sunny._
- `weather_rainy` — **Weather — rain / storm** (6) · _User asks about weather and rain or storms are forecast._
- `weather_cold_snow` — **Weather — cold / snow** (6) · _User asks about weather and it's cold or snowing._
- `alarm_set` — **Alarm / timer set** (6) · _User sets an alarm or timer and it's confirmed scheduled._
- `alarm_ringing` — **Alarm ringing** (6) · _A set alarm or timer fires — it's going off now, needs attention._
- `clock_time` — **Time / clock check** (6) · _User asks the time or checks the clock — Clawd reports the hour._
- `dnd_enabled` — **Do-not-disturb on** (6) · _Do-not-disturb / silent mode is turned on — Clawd goes quiet and hushed._
- `dnd_disabled` — **Do-not-disturb off** (6) · _Do-not-disturb is turned off — notifications and sound come back on._
- `greeting_launch` — **Greeting / first launch** (6) · _App opens fresh or onboarding begins — Clawd says hello._
- `goodbye_dismiss` — **Goodbye / dismiss** (6) · _User closes the app, dismisses the overlay, or ends the session — Clawd signs off._
