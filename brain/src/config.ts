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

/** Credentials that would switch the brain off the subscription onto pay-per-token billing. */
const OVERRIDE_CREDS = ["ANTHROPIC_API_KEY", "ANTHROPIC_AUTH_TOKEN"];

export function apiKeyPresent(): boolean {
  return OVERRIDE_CREDS.some((k) => (process.env[k] ?? "").trim() !== "");
}

/**
 * The whole point of Clyde: run on the user's subscription, never the API. Any credential
 * override (API key OR auth token) silently bills per token, so we refuse to start.
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
  if ((process.env.ANTHROPIC_BASE_URL ?? "").trim() !== "") {
    console.warn("[clyde] WARNING: ANTHROPIC_BASE_URL is set — requests may not go to Anthropic's subscription endpoint.");
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
        "    node -e \"console.log('CLYDE_KEY='+require('crypto').randomBytes(16).toString('hex'))\"\n" +
        "  (Dev-only escape hatch: CLYDE_DEV_NOAUTH=1.)\n"
    );
    process.exit(1);
  }
  if (!loopback && !config.clydeKey) {
    console.error("[clyde] FATAL: off-loopback bind requires CLYDE_KEY.");
    process.exit(1);
  }
}
