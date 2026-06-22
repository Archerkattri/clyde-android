import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { createHash, timingSafeEqual } from "node:crypto";
import { config, assertSubscriptionAuth, assertServerSafe, apiKeyPresent, offSubscriptionReasons } from "./config";
import { runAgent, haltActiveTurn } from "./agent";
import { startLogin, loginStatus } from "./oauth";
import type { AgentEvent } from "./types";

// Fail loud if a credential override would hijack subscription billing, and fail closed
// if the loopback secret / bind host aren't safe.
assertSubscriptionAuth();
assertServerSafe();

// Don't let a rejected interrupt()/stray promise crash the brain.
process.on("unhandledRejection", (e) => console.error("[clyde] unhandledRejection:", e));

const MAX_BODY = 256 * 1024;
const MAX_INFLIGHT = 1; // one supervised turn at a time (also prevents cross-turn halt races)
const ALLOWED_MODELS = new Set(["opus", "sonnet", "haiku"]); // app-selectable assistant models
let inFlight = 0;

function safeEqual(a: string, b: string): boolean {
  // Compare fixed-length digests so neither the timing nor a length pre-check leaks the key's length.
  const ah = createHash("sha256").update(a).digest();
  const bh = createHash("sha256").update(b).digest();
  return timingSafeEqual(ah, bh);
}

function authorized(req: IncomingMessage): boolean {
  if (!config.clydeKey) return false; // no key configured → reject (the app always provisions one)
  const h = req.headers["x-clyde-key"];
  return typeof h === "string" && safeEqual(h, config.clydeKey);
}

function readBody(req: IncomingMessage): Promise<string> {
  return new Promise((resolveP, reject) => {
    let data = "";
    let len = 0;
    req.on("data", (c: Buffer) => {
      len += c.length;
      if (len > MAX_BODY) {
        req.destroy();
        reject(new Error("body too large"));
        return;
      }
      data += c;
    });
    req.on("end", () => resolveP(data));
    req.on("error", () => resolveP(data));
  });
}

const server = createServer(async (req: IncomingMessage, res: ServerResponse) => {
  const url = req.url ?? "/";

  // Minimal liveness (no service banner). Auth-gated endpoints below.
  if (req.method === "GET" && url === "/healthz") {
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({ ok: true }));
    return;
  }

  if (!authorized(req)) {
    res.writeHead(401, { "content-type": "application/json" });
    res.end(JSON.stringify({ ok: false, error: "bad or missing X-Clyde-Key" }));
    return;
  }

  if (req.method === "GET" && url === "/auth/status") {
    const present = apiKeyPresent();
    // subscription is true only if NOTHING (cred / route flag / base-url) bills off-subscription.
    const offSub = offSubscriptionReasons();
    res.writeHead(200, { "content-type": "application/json" });
    res.end(
      JSON.stringify({
        ok: true,
        subscription: offSub.length === 0,
        apiKeyPresent: present,
        offSubscription: offSub,
        plan: process.env.CLYDE_PLAN ?? null,
      })
    );
    return;
  }

  if (req.method === "POST" && url === "/kill") {
    haltActiveTurn();
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({ ok: true, killed: true }));
    return;
  }

  // No-paste loopback sign-in: returns the authorize URL the app opens in the browser; the brain's own
  // localhost callback completes it and writes ~/.claude creds. The app then polls /auth/login/status.
  if (req.method === "POST" && url === "/auth/login/start") {
    try {
      const { url: authUrl } = await startLogin();
      res.writeHead(200, { "content-type": "application/json" });
      res.end(JSON.stringify({ ok: true, url: authUrl }));
    } catch (e) {
      res.writeHead(500, { "content-type": "application/json" });
      res.end(JSON.stringify({ ok: false, error: (e as Error).message }));
    }
    return;
  }
  if (req.method === "GET" && url === "/auth/login/status") {
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({ ok: true, ...loginStatus() }));
    return;
  }

  if (req.method === "POST" && url === "/query") {
    if (inFlight >= MAX_INFLIGHT) {
      res.writeHead(429, { "content-type": "application/json" });
      res.end(JSON.stringify({ ok: false, error: "busy" }));
      return;
    }
    let raw: string;
    try {
      raw = await readBody(req);
    } catch {
      res.writeHead(413, { "content-type": "application/json" });
      res.end(JSON.stringify({ ok: false, error: "request too large" }));
      return;
    }
    let parsed: { text?: unknown; sessionId?: unknown; model?: unknown };
    try {
      parsed = JSON.parse(raw || "{}");
    } catch {
      res.writeHead(400, { "content-type": "application/json" });
      res.end(JSON.stringify({ ok: false, error: "bad json" }));
      return;
    }
    const text = String(parsed.text ?? "").trim();
    const sessionId = String(parsed.sessionId ?? "default");
    // Allowlist the model so a request can't smuggle an arbitrary string into the SDK option.
    const model =
      typeof parsed.model === "string" && ALLOWED_MODELS.has(parsed.model) ? parsed.model : undefined;
    if (!text) {
      res.writeHead(400, { "content-type": "application/json" });
      res.end(JSON.stringify({ ok: false, error: "missing text" }));
      return;
    }

    res.writeHead(200, { "content-type": "application/x-ndjson", "cache-control": "no-cache", connection: "keep-alive" });
    const emit = (ev: AgentEvent) => res.write(JSON.stringify(ev) + "\n");
    inFlight++;
    try {
      await runAgent({ text, sessionId, model }, emit);
    } catch (e) {
      console.error("[clyde] query error:", e);
      emit({ type: "error", text: "Something went wrong while handling that.", detail: String((e as Error)?.message ?? e).slice(0, 240) });
    } finally {
      inFlight--;
      res.end();
    }
    return;
  }

  res.writeHead(404, { "content-type": "application/json" });
  res.end(JSON.stringify({ ok: false, error: "not found" }));
});

// If the port is briefly held by a previous (dying or orphaned) brain instance, RETRY binding for a
// few seconds instead of crashing with EADDRINUSE — otherwise a stale process keeps the brain offline.
let bindRetries = 0;
server.on("error", (e: NodeJS.ErrnoException) => {
  if (e.code === "EADDRINUSE" && bindRetries < 15) {
    bindRetries++;
    console.error(`[clyde] ${config.brainHost}:${config.brainPort} busy; retry ${bindRetries}/15 in 1s`);
    setTimeout(() => { try { server.close(); } catch { /* ignore */ } server.listen(config.brainPort, config.brainHost); }, 1000);
  } else {
    console.error("[clyde] server error:", e);
    process.exit(1);
  }
});
server.listen(config.brainPort, config.brainHost, () => {
  console.log(`[clyde] brain listening on http://${config.brainHost}:${config.brainPort}  (auth: shared-key)`);
});
