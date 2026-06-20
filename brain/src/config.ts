import { readFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";

// Minimal zero-dependency .env loader (does not override already-set env vars).
function loadDotEnv(): void {
  const path = resolve(process.cwd(), ".env");
  if (!existsSync(path)) return;
  for (const line of readFileSync(path, "utf8").split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Za-z0-9_]+)\s*=\s*(.*)\s*$/);
    if (!m || line.trim().startsWith("#")) continue;
    const key = m[1];
    if (process.env[key] === undefined) {
      process.env[key] = m[2].replace(/^["']|["']$/g, "");
    }
  }
}
loadDotEnv();

// Credentials the bundled Agent SDK honors that would switch the brain off the subscription
// onto pay-per-token billing. The SDK (claude-agent-sdk) reads ALL of these from process.env
// and inherits them into its CLI subprocess, so any one of them defeats the subscription rule.
// NOTE: CLAUDE_CODE_OAUTH_TOKEN is deliberately NOT here — it's the SUBSCRIPTION's own headless
// token (from `claude setup-token`), the supported way to run the subscription without an
// interactive login. These are the real off-subscription overrides (API key / enterprise / proxy).
const OVERRIDE_CREDS = [
  "ANTHROPIC_API_KEY",
  "ANTHROPIC_AUTH_TOKEN",
  "ANTHROPIC_IDENTITY_TOKEN",
  "ANTHROPIC_IDENTITY_TOKEN_FILE",
  "ANTHROPIC_SERVICE_ACCOUNT_ID",
];

// Provider-route flags the SDK honors to route off api.anthropic.com onto a paid cloud provider.
const ROUTE_FLAGS = [
  "CLAUDE_CODE_USE_BEDROCK",
  "CLAUDE_CODE_USE_VERTEX",
  "CLAUDE_CODE_USE_FOUNDRY",
  "CLAUDE_CODE_USE_MANTLE",
  "CLAUDE_CODE_USE_ANTHROPIC_AWS",
];

// The SDK treats any of these as "on" — mirror it so a flag can't sneak past as "true"/"yes".
const TRUTHY = new Set(["1", "true", "yes", "on"]);
const isTruthy = (v: string | undefined): boolean => TRUTHY.has((v ?? "").trim().toLowerCase());
const routeFlagSet = (): string | null => ROUTE_FLAGS.find((k) => isTruthy(process.env[k])) ?? null;
const baseUrlOverridden = (): boolean =>
  (process.env.ANTHROPIC_BASE_URL ?? "").trim() !== "" && process.env.CLYDE_ALLOW_BASE_URL !== "1";

export function apiKeyPresent(): boolean {
  return OVERRIDE_CREDS.some((k) => (process.env[k] ?? "").trim() !== "");
}

/** Every way the env is currently configured to bill OFF the subscription (for /auth/status truth). */
export function offSubscriptionReasons(): string[] {
  const reasons = OVERRIDE_CREDS.filter((k) => (process.env[k] ?? "").trim() !== "");
  const rf = routeFlagSet();
  if (rf) reasons.push(rf);
  if (baseUrlOverridden()) reasons.push("ANTHROPIC_BASE_URL");
  return reasons;
}

/**
 * The whole point of Clyde: run on the user's subscription, never the API. Any credential
 * override, provider-route flag, or base-URL redirect silently bills per token, so we refuse
 * to start (fail loud) — there is no path that re-enables a key.
 */
export function assertSubscriptionAuth(): void {
  for (const k of OVERRIDE_CREDS) {
    if ((process.env[k] ?? "").trim() !== "") {
      console.error(
        `\n[clyde] FATAL: ${k} is set.\n` +
          "  Clyde must run on your Claude subscription (claude login), not the API.\n" +
          `  Unset it and restart:  unset ${k}\n`
      );
      process.exit(1);
    }
  }
  const rf = routeFlagSet();
  if (rf) {
    console.error(
      `\n[clyde] FATAL: ${rf} routes the agent off your subscription onto pay-per-token cloud billing.\n` +
        `  Unset it and restart:  unset ${rf}\n`
    );
    process.exit(1);
  }
  if (baseUrlOverridden()) {
    console.error(
      "\n[clyde] FATAL: ANTHROPIC_BASE_URL is set — refusing to redirect model traffic off the subscription endpoint.\n" +
        "  Unset it, or set CLYDE_ALLOW_BASE_URL=1 to override (traffic will be billed per token).\n"
    );
    process.exit(1);
  }
}

export const config = {
  brainHost: process.env.BRAIN_HOST ?? "127.0.0.1",
  brainPort: Number(process.env.BRAIN_PORT ?? 8765),
  appBase: `http://${process.env.APP_HOST ?? "127.0.0.1"}:${process.env.APP_PORT ?? 8766}`,
  clydeKey: process.env.CLYDE_KEY ?? "",
  rishCmd: process.env.RISH_CMD ?? "rish",
  suCmd: process.env.SU_CMD ?? "su",
  model: process.env.CLYDE_MODEL && process.env.CLYDE_MODEL.trim() !== "" ? process.env.CLYDE_MODEL : undefined,
  // Explicit path to the JS claude-code CLI (cli.js). The embedded runtime sets this to the bundled
  // copy; unset on a normal Termux install (SDK finds `claude` on PATH). The native CLI is glibc-only.
  claudeCliPath: process.env.CLAUDE_CLI_PATH && process.env.CLAUDE_CLI_PATH.trim() !== "" ? process.env.CLAUDE_CLI_PATH : undefined,
  devNoAuth: process.env.CLYDE_DEV_NOAUTH === "1",
  allowNonLoopback: process.env.CLYDE_ALLOW_NONLOOPBACK === "1",
} as const;

const LOOPBACK = new Set(["127.0.0.1", "::1", "localhost"]);

/** Fail closed: refuse to start without a key, or off-loopback, unless explicitly opted in. */
export function assertServerSafe(): void {
  const loopback = LOOPBACK.has(config.brainHost);
  if (!loopback && !config.allowNonLoopback) {
    console.error(
      `\n[clyde] FATAL: BRAIN_HOST=${config.brainHost} is not loopback. The brain drives your phone; do not expose it.\n` +
        "  Set BRAIN_HOST=127.0.0.1, or (only if you truly mean to) CLYDE_ALLOW_NONLOOPBACK=1 with a strong CLYDE_KEY.\n"
    );
    process.exit(1);
  }
  if (!config.clydeKey && !config.devNoAuth) {
    console.error(
      "\n[clyde] FATAL: CLYDE_KEY is not set — refusing to run an unauthenticated agent endpoint.\n" +
        "  Generate one and put it in brain/.env AND the Clyde app (Home → brain key):\n" +
        "    node -e \"console.log('CLYDE_KEY='+require('crypto').randomBytes(16).toString('hex'))\"\n"
    );
    process.exit(1);
  }
  if (!loopback && !config.clydeKey) {
    console.error("[clyde] FATAL: off-loopback bind requires CLYDE_KEY.");
    process.exit(1);
  }
}
