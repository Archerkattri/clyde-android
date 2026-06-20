// Safe vs consequential classification, one-time confirm-token enforcement, and hard stops.
// Encodes the safety model from docs/architecture.md §7 and CLAUDE.md.

const CONSEQUENTIAL = new Set<string>([
  // Tier 0
  "start_call", "send_sms", "add_calendar_event", "mic_record", "camera_photo",
  "list_sms", "list_contacts", "list_call_log",
  // Tier 2
  "shell", "pm_grant", "settings_put", "app_disable", "force_stop", "disable_phantom_killer",
  // Tier 3
  "su_shell", "inject_event", "make_persistent", "grant_signature_perm",
  // router
  "delegate_to_gemini",
]);

interface TokenRecord {
  summary: string;
  exp: number;
  used: boolean;
}

export class Safety {
  private readonly tokens = new Map<string, TokenRecord>();
  private readonly ownPackage = "dev.kris.clyde";

  classOf(tool: string): "safe" | "consequential" {
    return CONSEQUENTIAL.has(tool) ? "consequential" : "safe";
  }
  isConsequential(tool: string): boolean {
    return CONSEQUENTIAL.has(tool);
  }

  /** Record a token that the APP issued via confirm(). Only these tokens are ever valid. */
  registerToken(token: string, summary: string, ttlMs = 120_000): void {
    this.tokens.set(token, { summary, exp: Date.now() + ttlMs, used: false });
  }

  /** One-time, time-bounded consume. */
  consumeToken(token: string | undefined): { ok: boolean; error?: string } {
    if (!token) return { ok: false, error: "missing confirmation token — call confirm() first and pass its token." };
    const rec = this.tokens.get(token);
    if (!rec) return { ok: false, error: "unknown token — tokens only come from confirm(); never invent one." };
    if (rec.used) return { ok: false, error: "token already used — call confirm() again for a fresh one." };
    if (Date.now() > rec.exp) {
      this.tokens.delete(token);
      return { ok: false, error: "token expired — call confirm() again." };
    }
    rec.used = true;
    return { ok: true };
  }

  /** Kill switch: invalidate every outstanding token. */
  invalidateAll(): void {
    this.tokens.clear();
  }

  /**
   * Hard stops that apply even with a valid token. Returns a reason string if blocked,
   * or null if allowed.
   */
  hardStop(tool: string, args: Record<string, unknown>): string | null {
    const blob = JSON.stringify(args ?? {}).toLowerCase();

    const moneyRe =
      /\b(payment|purchase|checkout|venmo|paypal|wire transfer|send money|transfer funds|\bpay\b|crypto|trading|place (an )?order|buy now)\b/;
    if ((tool === "shell" || tool === "su_shell") && moneyRe.test(blob)) {
      return "blocked: this looks like a payment or financial action. Clyde never moves money — do it yourself.";
    }

    if (tool === "pm_grant" || tool === "grant_signature_perm") {
      const pkg = String((args as { pkg?: unknown }).pkg ?? "");
      const userAsked = Boolean((args as { userAsked?: unknown }).userAsked);
      if (pkg.includes(this.ownPackage) && !userAsked) {
        return "blocked: refusing to grant Clyde itself new permissions without an explicit user request.";
      }
    }

    return null;
  }
}
