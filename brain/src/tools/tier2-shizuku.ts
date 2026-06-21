import { tool } from "@anthropic-ai/claude-agent-sdk";
import { z } from "zod";
import type { ToolCtx } from "../context";
import { text, err, image, gate } from "./helpers";

/** Tier 2 — Shizuku / rish (ADB-level powers, no root). Only registered when caps.tier2. */
export function makeTier2Shizuku(ctx: ToolCtx) {
  const run = async (cmd: string) => {
    const r = await ctx.rish(cmd);
    return r.ok ? text(r.stdout.trim() || "ok") : err(r.stderr.trim() || `exit ${r.code}`);
  };

  // Single-quote-escape UNTRUSTED text before it enters the device shell string. `rish -c "<cmd>"`
  // runs through a shell, so $(...), backticks and $VAR stay live inside double quotes — and the
  // model routinely types screen-derived (untrusted) text via input_text. Single quotes disable
  // all of it; the only escape needed is for a literal single quote.
  const sq = (s: string) => `'${s.replace(/'/g, `'\\''`)}'`;

  return [
    tool(
      "shell",
      "Run a shell command at ADB (shell-uid) privilege via Shizuku. Consequential.",
      { cmd: z.string(), token: z.string() },
      async (a) => {
        const g = gate(ctx, "shell", a);
        if (g) return g;
        return run(a.cmd);
      }
    ),

    tool("input_tap", "Tap screen coordinates via ADB input.", { x: z.number().int(), y: z.number().int() }, async (a) => run(`input tap ${a.x} ${a.y}`)),
    tool("input_text", "Type text via ADB input.", { text: z.string() }, async (a) => run(`input text ${sq(a.text)}`)),
    tool("input_key", "Send a key event via ADB input (numeric code or KEYCODE_* name).", { keycode: z.union([z.number().int(), z.string().regex(/^[A-Z0-9_]+$/)]) }, async (a) => run(`input keyevent ${a.keycode}`)),
    tool("uiautomator_dump", "Dump the UI hierarchy via uiautomator (when the accessibility tree is awkward).", {}, async () => run("uiautomator dump /sdcard/clyde_dump.xml >/dev/null 2>&1; cat /sdcard/clyde_dump.xml")),
    tool("screencap", "Capture the screen via screencap (returns image for vision).", {}, async () => {
      const r = await ctx.rish("screencap -p | base64 -w 0");
      return r.ok ? image(r.stdout.trim()) : err(r.stderr.trim() || "screencap failed");
    }),

    tool(
      "pm_grant",
      "Grant a runtime permission to a package. Consequential.",
      { pkg: z.string().regex(/^[A-Za-z0-9._]+$/), permission: z.string().regex(/^[A-Za-z0-9._]+$/), token: z.string() },
      async (a) => {
        const g = gate(ctx, "pm_grant", a);
        if (g) return g;
        return run(`pm grant ${a.pkg} ${a.permission}`);
      }
    ),
    tool(
      "settings_put",
      "Write a system/secure/global setting. Consequential.",
      { namespace: z.enum(["system", "secure", "global"]), key: z.string(), value: z.string(), token: z.string() },
      async (a) => {
        const g = gate(ctx, "settings_put", a);
        if (g) return g;
        return run(`settings put ${a.namespace} ${sq(a.key)} ${sq(a.value)}`);
      }
    ),
    tool("app_disable", "Disable an app for the current user. Consequential.", { pkg: z.string().regex(/^[A-Za-z0-9._]+$/), token: z.string() }, async (a) => {
      const g = gate(ctx, "app_disable", a);
      if (g) return g;
      return run(`pm disable-user --user 0 ${a.pkg}`);
    }),
    tool("force_stop", "Force-stop an app. Consequential.", { pkg: z.string().regex(/^[A-Za-z0-9._]+$/), token: z.string() }, async (a) => {
      const g = gate(ctx, "force_stop", a);
      if (g) return g;
      return run(`am force-stop ${a.pkg}`);
    }),
    tool(
      "disable_phantom_killer",
      "Disable Android's phantom-process killer so the brain stays alive. Consequential.",
      { token: z.string() },
      async (a) => {
        const g = gate(ctx, "disable_phantom_killer", a);
        if (g) return g;
        await ctx.rish("settings put global settings_enable_monitor_phantom_procs false");
        return run("device_config put activity_manager max_phantom_processes 2147483647");
      }
    ),
  ];
}
