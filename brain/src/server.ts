import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { config, assertSubscriptionAuth } from "./config";
import { runAgent, getSafety } from "./agent";
import type { AgentEvent } from "./types";

// Fail loud if an API key would hijack subscription billing.
assertSubscriptionAuth();

function authorized(req: IncomingMessage): boolean {
  if (!config.clydeKey) return true; // dev mode: no key configured
  return req.headers["x-clyde-key"] === config.clydeKey;
}

function readBody(req: IncomingMessage): Promise<string> {
  return new Promise((resolveP) => {
    let data = "";
    req.on("data", (c) => (data += c));
    req.on("end", () => resolveP(data));
  });
}

const server = createServer(async (req: IncomingMessage, res: ServerResponse) => {
  const url = req.url ?? "/";

  if (req.method === "GET" && url === "/healthz") {
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({ ok: true, service: "clyde-brain", auth: "subscription" }));
    return;
  }

  if (!authorized(req)) {
    res.writeHead(401, { "content-type": "application/json" });
    res.end(JSON.stringify({ ok: false, error: "bad or missing X-Clyde-Key" }));
    return;
  }

  if (req.method === "POST" && url === "/query") {
    const raw = await readBody(req);
    let parsed: { text?: unknown; sessionId?: unknown };
    try {
      parsed = JSON.parse(raw || "{}");
    } catch {
      res.writeHead(400, { "content-type": "application/json" });
      res.end(JSON.stringify({ ok: false, error: "bad json" }));
      return;
    }
    const text = String(parsed.text ?? "").trim();
    const sessionId = String(parsed.sessionId ?? "default");
    if (!text) {
      res.writeHead(400, { "content-type": "application/json" });
      res.end(JSON.stringify({ ok: false, error: "missing text" }));
      return;
    }

    res.writeHead(200, {
      "content-type": "application/x-ndjson",
      "cache-control": "no-cache",
      connection: "keep-alive",
    });
    const emit = (ev: AgentEvent) => res.write(JSON.stringify(ev) + "\n");
    try {
      await runAgent({ text, sessionId }, emit);
    } catch (e) {
      emit({ type: "error", text: String(e) });
    }
    res.end();
    return;
  }

  // kill switch: invalidate all outstanding confirm tokens
  if (req.method === "POST" && url === "/kill") {
    getSafety().invalidateAll();
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({ ok: true, killed: true }));
    return;
  }

  res.writeHead(404, { "content-type": "application/json" });
  res.end(JSON.stringify({ ok: false, error: "not found" }));
});

server.listen(config.brainPort, config.brainHost, () => {
  console.log(`[clyde] brain listening on http://${config.brainHost}:${config.brainPort}  (auth: subscription)`);
  if (!config.clydeKey) {
    console.warn("[clyde] WARNING: CLYDE_KEY not set — loopback API is unauthenticated (dev only).");
  }
});
