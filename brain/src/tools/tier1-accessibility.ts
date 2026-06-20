import { tool } from "@anthropic-ai/claude-agent-sdk";
import { z } from "zod";
import type { ToolCtx } from "../context";
import { text, err, json, image } from "./helpers";

/** Tier 1 — Accessibility hands (route: app → AccessibilityService). */
export function makeTier1Accessibility(ctx: ToolCtx) {
  return [
    tool(
      "ui_dump",
      "Return the on-screen accessibility node tree as structured JSON: nodeId, role, text, desc, bounds, clickable, editable, scrollable, focused, package.",
      {},
      async () => {
        ctx.emit({ type: "status", text: "reading screen…" });
        const r = await ctx.app.a11yDump();
        return r.ok ? json(r.result) : err(r.error ?? "ui_dump failed (root window may be null). Retry, or use screenshot.");
      }
    ),

    tool(
      "tap",
      "Tap a node by id, or by screen coordinates.",
      { nodeId: z.string().optional(), x: z.number().int().optional(), y: z.number().int().optional() },
      async (a) => {
        const r = await ctx.app.a11yTap(a);
        return r.ok ? text("tapped") : err(r.error ?? "tap failed");
      }
    ),

    tool(
      "type_text",
      "Type text into a field — a specific node, or the currently focused field.",
      { nodeId: z.string().optional(), text: z.string() },
      async (a) => {
        const r = await ctx.app.a11yType({ nodeId: a.nodeId, text: a.text });
        return r.ok ? text("typed") : err(r.error ?? "type failed");
      }
    ),

    tool(
      "swipe",
      "Swipe or scroll between two points. Use for scrolling lists.",
      { x1: z.number().int(), y1: z.number().int(), x2: z.number().int(), y2: z.number().int(), ms: z.number().int().optional() },
      async (a) => {
        const r = await ctx.app.a11ySwipe(a);
        return r.ok ? text("swiped") : err(r.error ?? "swipe failed");
      }
    ),

    tool(
      "global_action",
      "Perform a global navigation action.",
      { action: z.enum(["BACK", "HOME", "RECENTS", "NOTIFICATIONS", "QUICK_SETTINGS", "LOCK", "SCREENSHOT", "POWER_DIALOG"]) },
      async (a) => {
        const r = await ctx.app.a11yGlobal(a);
        return r.ok ? text(a.action.toLowerCase()) : err(r.error ?? "global action failed");
      }
    ),

    tool(
      "screenshot",
      "Capture the screen for vision. Use as a fallback when ui_dump is empty (Compose, WebView, games, canvas).",
      {},
      async () => {
        ctx.emit({ type: "status", text: "taking a screenshot…" });
        const r = await ctx.app.a11yScreenshot();
        if (!r.ok || !r.result?.pngBase64) return err(r.error ?? "screenshot failed (the screen may be FLAG_SECURE).");
        return image(r.result.pngBase64);
      }
    ),
  ];
}
