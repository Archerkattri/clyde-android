import { tool } from "@anthropic-ai/claude-agent-sdk";
import { z } from "zod";
import type { ToolCtx } from "../context";
import { json, err } from "./helpers";

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
  ];
}
