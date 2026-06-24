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
        // Send the params too so the app can independently bind the token to the exact args.
        const r = await ctx.app.confirm({ summary, details, action: a.action, params });
        if (!r.ok || !r.result) return err(`could not show the confirmation sheet: ${r.error ?? "no response"}`);
        const { approved, token } = r.result;
        if (!approved || !token) return text(JSON.stringify({ approved: false }));
        // bind the approval to THIS action + args; the action tool must match exactly
        ctx.safety.registerToken(token, a.action, argsHash(params));
        return text(JSON.stringify({ approved: true, token }));
      }
    ),

    tool(
      "ask_user",
      "Ask the user ONE multiple-choice question and wait for their answer. Clyde shows the options on screen; the user answers by voice or by tapping. Use this whenever you need them to choose or clarify (which app, which of several results, an ambiguous target) instead of asking in prose. Ask EXACTLY ONE question per call and wait for the reply before asking another — never bundle questions. Give 2–6 short options and make the LAST one a free-form catch-all like \"Something else\": if the user says something that matches none of the other options, Clyde selects that catch-all and returns their spoken words in `userSaid`. The result has `choice` (the chosen option's label) and `userSaid` (their verbatim words — important when they pick the catch-all); if `answered` is false the user dismissed the question, so don't keep pressing — proceed sensibly or stop.",
      {
        question: z.string().describe("the single question to ask, in the user's own terms"),
        options: z
          .array(z.string())
          .min(2)
          .max(6)
          .describe("2–6 short choices; make the LAST one a free-form catch-all like \"Something else\""),
      },
      async (a) => {
        const options = a.options.map((o) => String(o));
        const r = await ctx.app.ask({ question: a.question, options });
        if (!r.ok || !r.result) return err(`could not show the question: ${r.error ?? "no response"}`);
        const ans = r.result;
        if (ans.cancelled) return text(JSON.stringify({ answered: false }));
        return text(
          JSON.stringify({ answered: true, choice: ans.label, index: ans.index, userSaid: ans.text, via: ans.via })
        );
      }
    ),

    tool(
      "plan",
      'BEFORE starting a task that will take several steps (roughly 3+), call this ONCE with a short ordered list of the steps you intend, in plain user-facing language (e.g. ["Open YouTube Music", "Search for the song", "Play the top result"]). Clyde shows the plan to the user and checks each step off as you carry it out — so multi-step work is never a surprise. Skip it for one-shot answers or a single action.',
      { steps: z.array(z.string()).min(2).max(7).describe("the ordered steps you intend to take, short and user-facing") },
      async (a) => {
        ctx.emit({ type: "plan", steps: a.steps.map((s) => String(s)) });
        return text("Plan shown to the user. Now carry it out step by step.");
      }
    ),
  ];
}
