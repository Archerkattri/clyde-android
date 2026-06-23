import { tool } from "@anthropic-ai/claude-agent-sdk";
import { z } from "zod";
import type { ToolCtx } from "../context";
import { json, text, err } from "./helpers";

/** Tier 0 — read-only device queries via the app's content providers (contacts, apps, calendar). */
export function makeTier0Queries(ctx: ToolCtx) {
  const q = async (name: string, body: Record<string, unknown>) => {
    const r = await ctx.app.query(name, body);
    return r.ok ? json(r.result) : err(r.error ?? `${name} failed`);
  };

  return [
    tool(
      "find_contact",
      "Look up a contact by name and get their phone number(s). Use this to resolve a name to a number BEFORE calling or texting (start_call / send_sms take a number).",
      { name: z.string() },
      async (a) => q("find_contact", a)
    ),

    tool(
      "list_apps",
      "List installed apps (label + package name). Optional case-insensitive filter substring.",
      { filter: z.string().optional() },
      async (a) => q("list_apps", a)
    ),

    tool(
      "list_calendar_events",
      "List upcoming calendar events within the next N days (default 7).",
      { days: z.number().int().optional() },
      async (a) => q("list_calendar_events", a)
    ),

    tool(
      "read_notifications",
      "Read the user's current notifications (app, title, text), newest first. Use for 'what did I miss?'. Needs Notification access.",
      {},
      async () => q("read_notifications", {})
    ),

    tool(
      "now_playing",
      "What media is currently playing — title, artist, album, and app. Use to know or act on the current song/video/podcast. Needs Notification access.",
      {},
      async () => q("now_playing", {})
    ),

    tool(
      "set_default_app",
      "Remember the user's preferred app for a category so you use it automatically next time (e.g. category 'music' → a package). Call this once the user has picked/confirmed which app to use for that kind of task. Categories are free-form: music, video, maps, browser, mail, etc.",
      { category: z.string(), package: z.string().describe("exact package, from list_apps") },
      async (a) => {
        const r = await ctx.app.setDefaultApp(a.category, a.package);
        return r.ok ? text(`Got it — I'll use that for ${a.category} from now on.`) : err(r.error ?? "couldn't save the preference");
      }
    ),
  ];
}
