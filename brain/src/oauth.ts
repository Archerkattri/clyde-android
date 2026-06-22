import { createHash, randomBytes } from "node:crypto";
import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import { writeFileSync, mkdirSync, existsSync } from "node:fs";
import { resolve } from "node:path";

/**
 * No-paste subscription sign-in, done entirely in the brain (Node) — no terminal/PTY needed, which is
 * why the interactive CLI can't do it on-device. This replicates claude-code's OWN loopback OAuth flow
 * (the same client + endpoints the CLI uses for `claude` login): start a localhost callback server,
 * open the authorize URL in the phone browser, the browser redirects back to the local server with an
 * auth code, we exchange it (PKCE) and write ~/.claude/.credentials.json — the exact file the Agent SDK
 * reads + auto-refreshes. Subscription token only; no API key. The app just opens the URL and polls.
 */
const CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e";
const AUTHORIZE_URL = "https://claude.com/cai/oauth/authorize";
const TOKEN_URL = "https://platform.claude.com/v1/oauth/token";
// Single `user:inference` scope — EXACTLY what `claude setup-token` requests (captured + verified
// end-to-end: this scope over the loopback redirect returns an auth code; the broader set that
// includes `org:create_api_key` makes claude.ai REJECT the grant after Approve with "invalid request
// format", because that scope is org-admin and most accounts can't grant it). Inference is all the
// brain needs (subscription-only; never an API key). The redirect MUST be the loopback below — the
// `platform.claude.com/oauth/code/callback` manual redirect also gets rejected on the claude.ai flow.
const SCOPE = "user:inference";

const b64url = (b: Buffer): string => b.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

interface Pending { server: Server; verifier: string; state: string; redirectUri: string; }
let pending: Pending | null = null;
let lastError = "";

export function credsPath(): string {
  return resolve(process.env.HOME ?? "", ".claude/.credentials.json");
}
export function loginStatus(): { signedIn: boolean; pending: boolean; error: string } {
  return { signedIn: existsSync(credsPath()), pending: pending !== null, error: lastError };
}

/** Start the loopback OAuth: returns the authorize URL for the app to open in the browser. */
export async function startLogin(): Promise<{ url: string }> {
  if (pending) { try { pending.server.close(); } catch { /* ignore */ } pending = null; }
  lastError = "";
  const verifier = b64url(randomBytes(32));
  const challenge = b64url(createHash("sha256").update(verifier).digest());
  // MUST be 32 bytes (→43-char base64url), matching `claude` CLI exactly. claude.ai's authorize
  // endpoint REJECTS a shorter state (the old 16-byte/22-char value) as "invalid request format"
  // AFTER the user taps Approve. This single difference was why every in-app sign-in failed while
  // the real CLI / setup-token (32-byte state) always worked. Verified by side-by-side URL diff.
  const state = b64url(randomBytes(32));

  return new Promise<{ url: string }>((resolveP, reject) => {
    const server = createServer((req, res) => { void handleCallback(req, res); });
    server.on("error", (e) => reject(e));
    server.listen(0, "127.0.0.1", () => {
      const a = server.address();
      const port = a && typeof a === "object" ? a.port : 0;
      const redirectUri = `http://localhost:${port}/callback`;
      pending = { server, verifier, state, redirectUri };
      const u = new URL(AUTHORIZE_URL);
      const params: Record<string, string> = {
        code: "true", client_id: CLIENT_ID, response_type: "code",
        redirect_uri: redirectUri, scope: SCOPE,
        code_challenge: challenge, code_challenge_method: "S256", state,
      };
      for (const k of Object.keys(params)) u.searchParams.set(k, params[k]);
      resolveP({ url: u.toString() });
    });
    // don't leak the listener if the user never finishes
    setTimeout(() => { if (pending?.server === server) { try { server.close(); } catch { /* ignore */ } pending = null; } }, 600_000);
  });
}

async function handleCallback(req: IncomingMessage, res: ServerResponse): Promise<void> {
  const p = pending;
  const url = new URL(req.url ?? "/", "http://localhost");
  const page = (msg: string): void => {
    res.writeHead(200, { "content-type": "text/html" });
    res.end(`<!doctype html><meta name=viewport content="width=device-width,initial-scale=1"><body style="font-family:system-ui;text-align:center;padding:3em;background:#FAF9F5;color:#141413"><h2>${msg}</h2><p style="color:#73706A">You can close this tab and return to Clyde.</p></body>`);
  };
  if (!url.pathname.startsWith("/callback")) { res.writeHead(404); res.end(); return; }
  const code = url.searchParams.get("code");
  const gotState = url.searchParams.get("state");
  if (!p || !code || gotState !== p.state) {
    lastError = "callback rejected (state mismatch or missing code)";
    page("Sign-in failed — please try again in Clyde.");
    return;
  }
  try {
    const tok = await exchange(code, p.verifier, p.redirectUri, p.state);
    writeCreds(tok);
    page("Signed in to Claude ✓");
  } catch (e) {
    lastError = `token exchange failed: ${(e as Error).message}`;
    page("Sign-in failed — please try again in Clyde.");
  } finally {
    try { p?.server.close(); } catch { /* ignore */ }
    pending = null;
  }
}

async function exchange(code: string, verifier: string, redirectUri: string, state: string): Promise<Record<string, unknown>> {
  // Body mirrors the CLI's exchange exactly — note `state` is required alongside the PKCE verifier.
  const body = { grant_type: "authorization_code", code, redirect_uri: redirectUri, client_id: CLIENT_ID, code_verifier: verifier, state };
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
