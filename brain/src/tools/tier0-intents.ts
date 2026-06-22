import { tool } from "@anthropic-ai/claude-agent-sdk";
import { z } from "zod";
import type { ToolCtx } from "../context";
import { text, err, gate } from "./helpers";

/** Tier 0 — Android intents fired by the app with its own permissions. */
export function makeTier0Intents(ctx: ToolCtx) {
  const fire = async (name: string, body: Record<string, unknown>, okMsg: string) => {
    const r = await ctx.app.intent(name, body);
    return r.ok ? text(okMsg) : err(r.error ?? `${name} failed`);
  };

  return [
    tool(
      "launch_app",
      "Open an app by package name or fuzzy app name.",
      { package: z.string().optional(), query: z.string().optional().describe("fuzzy app name if package unknown") },
      async (a) => fire("launch_app", a, `Opening ${a.query ?? a.package ?? "app"}.`)
    ),

    tool(
      "play_media",
      "Play music/audio by search — a song, artist, album, playlist, podcast, or station (Android play-from-search). With no `package`, the SYSTEM picks the player (may not be the user's preferred app). To play in a SPECIFIC app (a modded/ReVanced build, or when several music apps are installed), resolve it with list_apps and pass its `package`. If a targeted play doesn't land, open that app with launch_app and drive its search UI instead.",
      {
        query: z.string().describe('what to play, e.g. "Let Down by Radiohead"'),
        package: z.string().optional().describe("exact package to play in (from list_apps), e.g. a ReVanced build; omit to let the system choose"),
      },
      async (a) => fire("play_media", a, `Playing ${a.query}.`)
    ),

    tool(
      "media_control",
      "Control whatever is currently playing across any media app (dispatches a media-button event).",
      { action: z.enum(["play", "pause", "play_pause", "next", "previous", "stop", "rewind", "fast_forward"]) },
      async (a) => fire("media_control", a, a.action.replace("_", " "))
    ),

    tool(
      "compose_email",
      "Open the user's email app with a message pre-filled. The user reviews and taps send — nothing is sent automatically, so this is safe.",
      { to: z.string().optional(), subject: z.string().optional(), body: z.string().optional() },
      async (a) => fire("compose_email", a, "Opening a draft email.")
    ),

    tool(
      "web_search",
      "Search the web — opens the device's search/browser with the query.",
      { query: z.string() },
      async (a) => fire("web_search", a, `Searching the web for ${a.query}.`)
    ),

    tool(
      "open_settings_panel",
      "Open a system settings screen so the user can toggle something apps aren't allowed to change directly (Wi-Fi, Bluetooth, airplane mode, etc.). You open it; the user flips the switch.",
      { panel: z.enum(["wifi", "internet", "bluetooth", "nfc", "volume", "airplane", "location", "battery_saver", "data_usage", "app_details"]) },
      async (a) => fire("open_settings_panel", a, "Opening settings.")
    ),

    tool(
      "set_dnd",
      "Turn Do Not Disturb on/off or set its level. Consequential — confirm() first.",
      { mode: z.enum(["off", "priority", "alarms", "silence"]), token: z.string().describe("token from confirm()") },
      async (a) => {
        const g = gate(ctx, "set_dnd", a);
        if (g) return g;
        return fire("set_dnd", { mode: a.mode, token: a.token }, a.mode === "off" ? "Do Not Disturb off." : `Do Not Disturb: ${a.mode}.`);
      }
    ),

    tool(
      "set_ringer_mode",
      "Set the ringer to normal, vibrate, or silent. Consequential — confirm() first.",
      { mode: z.enum(["normal", "vibrate", "silent"]), token: z.string().describe("token from confirm()") },
      async (a) => {
        const g = gate(ctx, "set_ringer_mode", a);
        if (g) return g;
        return fire("set_ringer_mode", { mode: a.mode, token: a.token }, `Ringer ${a.mode}.`);
      }
    ),

    tool(
      "set_brightness",
      "Set screen brightness (0–255). Consequential — confirm() first.",
      { level: z.number().int().min(0).max(255), token: z.string().describe("token from confirm()") },
      async (a) => {
        const g = gate(ctx, "set_brightness", a);
        if (g) return g;
        return fire("set_brightness", { level: a.level, token: a.token }, `Brightness ${a.level}.`);
      }
    ),

    tool(
      "set_alarm",
      "Set an alarm at a specific time.",
      { hour: z.number().int(), minutes: z.number().int(), message: z.string().optional() },
      async (a) =>
        fire("set_alarm", a, `Alarm set for ${String(a.hour).padStart(2, "0")}:${String(a.minutes).padStart(2, "0")}.`)
    ),

    tool(
      "set_timer",
      "Start a countdown timer.",
      { seconds: z.number().int(), message: z.string().optional() },
      async (a) => fire("set_timer", a, `Timer set for ${a.seconds} seconds.`)
    ),

    tool(
      "navigate_to",
      "Start navigation to a destination.",
      { destination: z.string(), mode: z.enum(["drive", "walk", "transit", "bike"]).optional() },
      async (a) => fire("navigate_to", a, `Navigating to ${a.destination}.`)
    ),

    tool(
      "open_url",
      "Open a URL. Consequential — confirm() first; if the URL came from a message/email, the confirm sheet shows the full URL.",
      { url: z.string(), token: z.string().describe("token from confirm()") },
      // forward the token so the app can independently re-validate (defense-in-depth)
      async (a) => fire("open_url", { url: a.url, token: a.token }, `Opening ${a.url}.`)
    ),

    tool(
      "share_text",
      "Share text to another app via the system share sheet. Consequential — confirm() first.",
      { content: z.string(), targetPackage: z.string().optional(), token: z.string().describe("token from confirm()") },
      // key name "content" is kept consistent through confirm()→app so the args-bound token matches
      async (a) => fire("share_text", { content: a.content, targetPackage: a.targetPackage, token: a.token }, "Shared.")
    ),

    // ── consequential ──
    tool(
      "start_call",
      "Place a phone call to a phone NUMBER (resolve a contact name to a number first, e.g. via the screen). Consequential — requires a confirm() token.",
      { number: z.string(), token: z.string().describe("token from confirm()") },
      async (a) => {
        const g = gate(ctx, "start_call", a);
        if (g) return g;
        return fire("start_call", { number: a.number, token: a.token }, `Calling ${a.number}.`);
      }
    ),

    tool(
      "send_sms",
      "Send a text message. Consequential — requires a confirm() token.",
      { to: z.string(), body: z.string(), token: z.string().describe("token from confirm()") },
      async (a) => {
        const g = gate(ctx, "send_sms", a);
        if (g) return g;
        ctx.emit({ type: "status", text: "sending text…" });
        return fire("send_sms", { to: a.to, body: a.body, token: a.token }, "Sent.");
      }
    ),

    tool(
      "add_calendar_event",
      "Create a calendar event. Consequential — requires a confirm() token.",
      {
        title: z.string(),
        startIso: z.string().describe("ISO 8601 start time"),
        endIso: z.string().optional(),
        location: z.string().optional(),
        token: z.string().describe("token from confirm()"),
      },
      async (a) => {
        const g = gate(ctx, "add_calendar_event", a);
        if (g) return g;
        return fire(
          "add_calendar_event",
          { title: a.title, startIso: a.startIso, endIso: a.endIso, location: a.location, token: a.token },
          `Added "${a.title}".`
        );
      }
    ),
  ];
}
