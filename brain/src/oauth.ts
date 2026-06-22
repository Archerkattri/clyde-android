import { createHash, randomBytes } from "node:crypto";
import { writeFileSync, mkdirSync, existsSync } from "node:fs";
import { resolve } from "node:path";

/**
 * Subscription sign-in done entirely in the brain (Node) — no terminal/PTY, which is why the
 * interactive `claude login` CLI can't run on-device. This replicates claude-code's OWN `login` OAuth
 * (same client + endpoints + scopes), but uses its MANUAL redirect rather than a localhost loopback.
 *
 * Why manual, not loopback: on a phone, claude.ai will not complete the post-approval hand-off back to
 * a `http://localhost:<port>/callback` server (the loopback only works on desktop). So we send users
 * through the same path `claude login` falls back to — redirect to platform.claude.com's code page,
 * which DISPLAYS the auth code; the user pastes it into Clyde and the brain exchanges it (PKCE) for a
 * full subscription token written to ~/.claude/.credentials.json (the file the Agent SDK reads).
 * Subscription token only; no API key. The app opens the URL, takes the pasted code, and polls status.
 */
const CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e";
const AUTHORIZE_URL = "https://claude.com/cai/oauth/authorize";
const TOKEN_URL = "https://platform.claude.com/v1/oauth/token";
// The manual redirect the CLI uses when no loopback is available: claude.ai sends the code here and the
// page shows it for copy-paste. This URL is registered for the client (the localhost loopback, with a
// port, is what claude.ai rejects after approval on mobile).
const MANUAL_REDIRECT_URL = "https://platform.claude.com/oauth/code/callback";
// EXACTLY what `claude setup-token` requests — a single `user:inference` scope (the CLI's
// inferenceOnly path). setup-token is the PROVEN headless subscription flow: claude.ai authorize +
// this manual redirect + this one scope → a long-lived token the Agent SDK runs on. The broader
// claude.ai scope set (sessions/mcp/file_upload/profile, and certainly org:create_api_key) gets the
// whole grant REFUSED at the Approve step on a normal subscription account, so we mirror setup-token
// verbatim instead. Clyde only needs inference anyway (subscription-only, never an API key).
const SCOPE = "user:inference";
// setup-token asks for a 1-year token (vs the default ~1h); sent on the token exchange.
const TOKEN_EXPIRES_IN = 31536000;

const b64url = (b: Buffer): string => b.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

interface Pending { verifier: string; state: string; redirectUri: string; }
let pending: Pending | null = null;
let lastError = "";

export function credsPath(): string {
  return resolve(process.env.HOME ?? "", ".claude/.credentials.json");
}
export function loginStatus(): { signedIn: boolean; pending: boolean; error: string } {
  return { signedIn: existsSync(credsPath()), pending: pending !== null, error: lastError };
}

/** Begin sign-in: returns the authorize URL for the app to open in the browser. No loopback server —
 *  after approval claude.ai shows a code the user pastes back via submitCode(). */
export async function startLogin(): Promise<{ url: string }> {
  lastError = "";
  const verifier = b64url(randomBytes(32));
  const challenge = b64url(createHash("sha256").update(verifier).digest());
  const state = b64url(randomBytes(16));
  pending = { verifier, state, redirectUri: MANUAL_REDIRECT_URL };

  const u = new URL(AUTHORIZE_URL);
  const params: Record<string, string> = {
    code: "true", client_id: CLIENT_ID, response_type: "code",
    redirect_uri: MANUAL_REDIRECT_URL, scope: SCOPE,
    code_challenge: challenge, code_challenge_method: "S256", state,
  };
  for (const k of Object.keys(params)) u.searchParams.set(k, params[k]);
  return { url: u.toString() };
}

/** Complete sign-in with the code the user copied from claude.ai's code page. The page shows the value
 *  as `code#state` (mirrors how the CLI parses it); we accept either `code` or `code#state`. */
export async function submitCode(input: string): Promise<{ ok: boolean; error?: string }> {
  const p = pending;
  if (!p) { lastError = "no sign-in in progress — start again"; return { ok: false, error: lastError }; }
  const trimmed = input.trim();
  const code = trimmed.split("#")[0];
  const gotState = trimmed.includes("#") ? trimmed.split("#")[1] : undefined;
  if (!code) { lastError = "no code in the pasted value"; return { ok: false, error: lastError }; }
  if (gotState && gotState !== p.state) { lastError = "state mismatch — start sign-in again"; return { ok: false, error: lastError }; }
  try {
    const tok = await exchange(code, p.verifier, p.redirectUri, p.state);
    writeCreds(tok);
    pending = null;
    lastError = "";
    return { ok: true };
  } catch (e) {
    lastError = `token exchange failed: ${(e as Error).message}`;
    return { ok: false, error: lastError };
  }
}

async function exchange(code: string, verifier: string, redirectUri: string, state: string): Promise<Record<string, unknown>> {
  // Body mirrors the CLI's exchange exactly — `state` is required alongside the PKCE verifier, and the
  // redirect_uri MUST match the one sent to /authorize (the manual redirect).
  const body = { grant_type: "authorization_code", code, redirect_uri: redirectUri, client_id: CLIENT_ID, code_verifier: verifier, state, expires_in: TOKEN_EXPIRES_IN };
  const r = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "content-type": "application/json", accept: "application/json" },
    body: JSON.stringify(body),
  });
  const txt = await r.text();
  if (!r.ok) throw new Error(`HTTP ${r.status}: ${txt.slice(0, 180)}`);
  return JSON.parse(txt) as Record<string, unknown>;
}

function writeCreds(tok: Record<string, unknown>): void {
  const dir = resolve(process.env.HOME ?? "", ".claude");
  mkdirSync(dir, { recursive: true });
  const expiresIn = Number(tok.expires_in) || 3600;
  const creds = {
    claudeAiOauth: {
      accessToken: tok.access_token,
      refreshToken: tok.refresh_token ?? null,
      expiresAt: Date.now() + expiresIn * 1000,
      scopes: String(tok.scope ?? SCOPE).split(" "),
      subscriptionType: tok.subscription_type ?? null,
      rateLimitTier: null,
    },
  };
  writeFileSync(resolve(dir, ".credentials.json"), JSON.stringify(creds), { mode: 0o600 });
}
