import { tool } from "@anthropic-ai/claude-agent-sdk";
import { z } from "zod";
import type { ToolCtx } from "../context";
import { json, err, gate } from "./helpers";

/** Gemini router — delegate to the Gemini app for things Claude can't do natively. */
export function makeGeminiTools(ctx: ToolCtx) {
  return [
    tool(
      "delegate_to_gemini",
      "Hand a prompt to the Gemini app for things Claude can't do natively (image/video generation, on-device Nano features). Consequential.",
      { prompt: z.string(), want: z.enum(["image", "text", "action"]), token: z.string() },
      async (a) => {
        const g = gate(ctx, "delegate_to_gemini", a);
        if (g) return g;
        const r = await ctx.app.gemini({ prompt: a.prompt, want: a.want });
        return r.ok ? json(r.result) : err(r.error ?? "gemini delegate failed");
      }
    ),
  ];
}
