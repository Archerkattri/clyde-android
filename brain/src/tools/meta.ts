import { tool } from "@anthropic-ai/claude-agent-sdk";
import { z } from "zod";
import type { ToolCtx } from "../context";
import { json, text, err } from "./helpers";

/** Always-on tools: capabilities() and confirm(). */
export function makeMetaTools(ctx: ToolCtx) {
  return [
    tool(
      "capabilities",
      "Report which control tiers and permissions are currently live. Call this first when deciding how to do something.",
      {},
      async () => json(ctx.caps)
    ),

    tool(
      "confirm",
      "Ask the user to approve a consequential action. Returns a one-time token to pass to the action tool. Make the summary specific and in the user's terms.",
      {
        summary: z.string().describe("e.g. \"Text Mom: 'on my way'?\""),
        details: z.string().optional().describe("extra context shown under the summary"),
      },
      async (a) => {
        ctx.emit({ type: "need_confirm", summary: a.summary, details: a.details });
        const r = await ctx.app.confirm({ summary: a.summary, details: a.details });
        if (!r.ok || !r.result) return err(`could not show the confirmation sheet: ${r.error ?? "no response"}`);
        const { approved, token } = r.result;
        if (!approved || !token) return text(JSON.stringify({ approved: false }));
        ctx.safety.registerToken(token, a.summary);
        return text(JSON.stringify({ approved: true, token }));
      }
    ),
  ];
}
