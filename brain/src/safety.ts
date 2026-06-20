// Safe vs consequential, action-bound one-time confirm tokens, hard stops, and a halt flag.
// Enforcement is centralized in agent.ts `canUseTool` (every tool call passes through it).
import { createHash } from "node:crypto";

/** Read-only / trivially-reversible tools. Everything NOT here is consequential. */
const SAFE = new Set<string>([
  "capabilities", "confirm",
  // perception + UI driving (gated by hardStop + halt, not by per-action confirm)
  "ui_dump", "screenshot", "tap", "type_text", "swipe", "global_action",
  "input_tap", "input_text", "input_key", "uiautomator_dump", "screencap",
  // benign device reads / trivial output
  "get_battery", "get_location", "clipboard_set", "notify", "torch", "vibrate",
  "tts_speak", "read_sensor", "wifi_info", "volume_set",
  // deterministic, low-risk intents
  "launch_app", "set_alarm", "set_timer", "navigate_to",
]);

/** Stable hash of a tool's args (excludes the token) — binds an approval to one concrete call. */
export function argsHash(args: Record<string, unknown> | undefined): string {
  const clean: Record<string, unknown> = {};
  for (const k of Object.keys(args ?? {}).sort()) {
    if (k === "token") continue;
    clean[k] = (args as Record<string, unknown>)[k];
  }
  return createHash("sha256").update(JSON.stringify(clean)).digest("hex").slice(0, 32);
}

interface TokenRecord {
  tool: string;
  hash: string;
  exp: number;
  used: boolean;
}

export class Safety {
  private readonly tokens = new Map<string, TokenRecord>();
  private readonly ownPackage = "dev.kris.clyde";
  private readonly ttlMs = 75_000; // ~ the app's confirm poll window + margin

  classOf(tool: string): "safe" | "consequential" {
    return SAFE.has(tool) ? "safe" : "consequential";
  }
  isConsequential(tool: string): boolean {
    return !SAFE.has(tool);
  }

  /** Record an app-issued token, BOUND to the exact tool + args it was approved for. */
  registerToken(token: string, tool: string, hash: string): void {
    this.tokens.set(token, { tool, hash, exp: Date.now() + this.ttlMs, used: false });
  }

  /** One-time consume, bound: the token must match this tool AND these args. */
  consumeToken(token: string | undefined, tool: string, hash: string): { ok: boolean; error?: string } {
    if (!token) return { ok: false, error: "missing confirmation token — call confirm({action, params}) first and pass its token." };
    const rec = this.tokens.get(token);
    if (!rec) return { ok: false, error: "unknown token — tokens only come from confirm(); never invent one." };
    if (rec.used) return { ok: false, error: "token already used — call confirm() again for a fresh one." };
    if (Date.now() > rec.exp) {
      this.tokens.delete(token);
      return { ok: false, error: "token expired — call confirm() again." };
    }
    if (rec.tool !== tool) return { ok: false, error: `that approval was for ${rec.tool}, not ${tool} — confirm the real action.` };
    if (rec.hash !== hash) return { ok: false, error: "the action's details changed since approval — confirm() again with the exact values." };
    rec.used = true;
    return { ok: true };
  }

  /** Kill switch: drop every outstanding token. */
  invalidateAll(): void {
    this.tokens.clear();
  }

  /** Hard stops that apply even with a valid token. Returns a reason if blocked. */
  hardStop(tool: string, args: Record<string, unknown>): string | null {
    const blob = JSON.stringify(args ?? {}).toLowerCase();
    // Explicit payment-ACTION phrases only (not bare "pay"/"payment") to avoid false
    // positives like "Pay Kim" contacts or a "Payment history" menu.
    const moneyRe =
      /\b(confirm payment|send money|transfer (funds|money)|place (an )?order|pay now|wire transfer|make (a )?payment|buy now|checkout now)\b/;
    // Scope to transaction-EXECUTING / egress tools, never perception/navigation.
    const moneyTools = new Set(["shell", "su_shell", "open_url", "share_text", "delegate_to_gemini"]);
    if (moneyTools.has(tool) && moneyRe.test(blob)) {
      return "blocked: this looks like a payment or financial action. Clyde never moves money — please do it yourself.";
    }
    // Never let Clyde grant ITSELF permissions (no model-controlled escape hatch).
    if (tool === "pm_grant" || tool === "grant_signature_perm") {
      const pkg = String((args as { pkg?: unknown }).pkg ?? "");
      if (pkg.includes(this.ownPackage)) {
        return "blocked: Clyde will not grant new permissions to itself.";
      }
    }
    return null;
  }
}
