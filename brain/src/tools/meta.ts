import { tool } from "@anthropic-ai/claude-agent-sdk";
import { z } from "zod";
import type { ToolCtx } from "../context";
import { argsHash } from "../safety";
import { describeAction } from "../describe";
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
      "Approve a consequential action BEFORE running it. Pass the exact tool you will run as `action` and the exact args as `params` (no token). Clyde shows the user an approval sheet describing that action and returns a one-time token bound to it. Then call the action tool with the SAME params plus this token. The token only works for that exact action — you cannot reuse it for anything else.",
      {
        action: z.string().describe("the tool name you intend to run, e.g. \"send_sms\""),
        params: z.record(z.string(), z.any()).optional().describe("the exact args for that tool (omit the token)"),
      },
      async (a) => {
        const params = (a.params ?? {}) as Record<string, unknown>;
        const { summary, details } = describeAction(a.action, params);
        ctx.emit({ type: "need_confirm", summary, details });
        const r = await ctx.app.confirm({ summary, details, action: a.action });
        if (!r.ok || !r.result) return err(`could not show the confirmation sheet: ${r.error ?? "no response"}`);
        const { approved, token } = r.result;
        if (!approved || !token) return text(JSON.stringify({ approved: false }));
        // bind the approval to THIS action + args; the action tool must match exactly
        ctx.safety.registerToken(token, a.action, argsHash(params));
        return text(JSON.stringify({ approved: true, token }));
      }
    ),
  ];
}
