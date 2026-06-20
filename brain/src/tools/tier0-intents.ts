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
      async (a) => fire("open_url", { url: a.url }, `Opening ${a.url}.`)
    ),

    tool(
      "share_text",
      "Share text to another app via the system share sheet. Consequential — confirm() first.",
      { content: z.string(), targetPackage: z.string().optional(), token: z.string().describe("token from confirm()") },
      async (a) => fire("share_text", { text: a.content, targetPackage: a.targetPackage }, "Shared.")
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
