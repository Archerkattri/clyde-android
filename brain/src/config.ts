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

/**
 * The whole point of Clyde: run on the user's subscription, never the API.
 * ANTHROPIC_API_KEY silently overrides subscription auth and bills per token,
 * so we refuse to start if it is present.
 */
export function assertSubscriptionAuth(): void {
  if (process.env.ANTHROPIC_API_KEY && process.env.ANTHROPIC_API_KEY.trim() !== "") {
    console.error(
      "\n[clyde] FATAL: ANTHROPIC_API_KEY is set.\n" +
        "  Clyde must run on your Claude subscription (claude login), not the API.\n" +
        "  An API key switches billing to pay-per-token. Unset it and restart:\n" +
        "    unset ANTHROPIC_API_KEY\n"
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
} as const;
