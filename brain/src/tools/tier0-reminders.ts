import { tool } from "@anthropic-ai/claude-agent-sdk";
import { z } from "zod";
import type { ToolCtx } from "../context";
import { text, err, json } from "./helpers";

/** Tier 0 — reminders. Stored in the app and fired by AlarmManager, so they survive the brain/app
 *  being closed and reboots. Time/date based (location triggers are a planned follow-up). */
export function makeTier0Reminders(ctx: ToolCtx) {
  return [
    tool(
      "set_reminder",
      'Set a time-based reminder. Give EITHER `atIso` (an absolute LOCAL time, e.g. "2026-06-23T17:00:00") OR `inSeconds` (relative to now). Resolve clock phrasing like "5pm" / "tomorrow 9am" / "tonight" to `atIso` using the current local time from your context. `action` is OPTIONAL — a command Clyde should actually run when it fires (e.g. "start my commute playlist", "set DND on"); omit it for a plain reminder. Location-based triggers are not supported yet.',
      {
        text: z.string().describe("what to remind the user about, in their words"),
        atIso: z.string().optional().describe("absolute local time, ISO 8601"),
        inSeconds: z.number().int().positive().optional().describe("fire this many seconds from now"),
        action: z.string().optional().describe("optional command for Clyde to run when it fires"),
      },
      async (a) => {
        let fireAt: number;
        if (a.atIso) {
          const t = Date.parse(a.atIso);
          if (Number.isNaN(t)) return err(`couldn't parse "${a.atIso}" — use ISO 8601 like 2026-06-23T17:00:00.`);
          fireAt = t;
        } else if (a.inSeconds) {
          fireAt = Date.now() + a.inSeconds * 1000;
        } else {
          return err("give either atIso (absolute time) or inSeconds (relative).");
        }
        if (fireAt < Date.now() - 1000) return err("that time is in the past — pick a future time.");
        const r = await ctx.app.reminderSet({ text: a.text, fireAt, action: a.action });
        if (!r.ok) return err(r.error ?? "couldn't set the reminder");
        const when = new Date(fireAt).toLocaleString();
        return text(`Reminder set for ${when}: "${a.text}".${a.action ? ` On fire it will: ${a.action}.` : ""}`);
      }
    ),

    tool(
      "list_reminders",
      "List the user's pending reminders (id, text, fireAt, optional action), soonest first.",
      {},
      async () => {
        const r = await ctx.app.reminderList();
        return r.ok ? json(r.result) : err(r.error ?? "couldn't read reminders");
      }
    ),

    tool(
      "cancel_reminder",
      "Cancel a pending reminder by its id (get ids from list_reminders).",
      { id: z.string() },
      async (a) => {
        const r = await ctx.app.reminderCancel(a.id);
        return r.ok ? text("Reminder cancelled.") : err(r.error ?? "couldn't cancel it");
      }
    ),
  ];
}
