import { tool } from "@anthropic-ai/claude-agent-sdk";
import { z } from "zod";
import type { ToolCtx } from "../context";
import { text, err, gate } from "./helpers";

/** Tier 3 — Root / su. Only registered when caps.tier3. Superset of Tier 2. */
export function makeTier3Root(ctx: ToolCtx) {
  const run = async (cmd: string) => {
    const r = await ctx.su(cmd);
    return r.ok ? text(r.stdout.trim() || "ok") : err(r.stderr.trim() || `exit ${r.code}`);
  };

  return [
    tool("su_shell", "Run a command as root. Consequential.", { cmd: z.string(), token: z.string() }, async (a) => {
      const g = gate(ctx, "su_shell", a);
      if (g) return g;
      return run(a.cmd);
    }),

    tool(
      "inject_event",
      "Low-level /dev/input injection — works even on secure screens. Consequential.",
      { device: z.string(), events: z.string().describe("space-separated sendevent args"), token: z.string() },
      async (a) => {
        const g = gate(ctx, "inject_event", a);
        if (g) return g;
        return run(`sendevent ${a.device} ${a.events}`);
      }
    ),

    tool(
      "make_persistent",
      "Install the brain as a boot service immune to Doze/phantom-killer. Consequential.",
      { token: z.string() },
      async (a) => {
        const g = gate(ctx, "make_persistent", a);
        if (g) return g;
        return run("sh ~/clyde/termux/boot/start-brain.sh && echo 'persistence script invoked'");
      }
    ),

    tool(
      "grant_signature_perm",
      "Grant a signature/privileged permission to a package (e.g. for VoiceInteractionService experiments). Consequential.",
      { pkg: z.string(), permission: z.string(), token: z.string(), userAsked: z.boolean().optional() },
      async (a) => {
        const g = gate(ctx, "grant_signature_perm", a);
        if (g) return g;
        return run(`pm grant ${a.pkg} ${a.permission}`);
      }
    ),
  ];
}
