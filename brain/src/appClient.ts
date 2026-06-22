import { config } from "./config";
import type { AppCaps, AppResult, ConfirmResult } from "./types";

/** Thin client for the app's LocalControlServer on 127.0.0.1:8766. */
export class AppClient {
  private readonly base = config.appBase;

  private headers(): Record<string, string> {
    return { "Content-Type": "application/json", "X-Clyde-Key": config.clydeKey };
  }

  private async req<T>(
    method: "GET" | "POST",
    path: string,
    body?: unknown,
    timeoutMs = 20000
  ): Promise<AppResult<T>> {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), timeoutMs);
    try {
      const res = await fetch(this.base + path, {
        method,
        headers: this.headers(),
        body: method === "POST" ? JSON.stringify(body ?? {}) : undefined,
        signal: ctrl.signal,
      });
      const data = (await res.json()) as AppResult<T>;
      return data;
    } catch (e) {
      return { ok: false, error: `app unreachable: ${String(e)}` };
    } finally {
      clearTimeout(timer);
    }
  }

  // ── capabilities ──
  caps() {
    return this.req<AppCaps>("GET", "/caps", undefined, 8000);
  }

  // ── Tier 1 accessibility ──
  a11yDump() {
    return this.req<{ nodes: unknown[] }>("POST", "/a11y/dump");
  }
  a11yTap(b: { nodeId?: string; x?: number; y?: number }) {
    return this.req("POST", "/a11y/tap", b);
  }
  a11yType(b: { nodeId?: string; text: string }) {
    return this.req("POST", "/a11y/type", b);
  }
  a11ySwipe(b: { x1: number; y1: number; x2: number; y2: number; ms?: number }) {
    return this.req("POST", "/a11y/swipe", b);
  }
  a11yGlobal(b: { action: string }) {
    return this.req("POST", "/a11y/global", b);
  }
  a11yScreenshot() {
    return this.req<{ pngBase64: string }>("POST", "/a11y/screenshot", {}, 25000);
  }

  // ── Tier 0 intents (fired by the app with its own permissions) ──
  intent(name: string, body: Record<string, unknown>) {
    return this.req<unknown>("POST", `/intent/${name}`, body);
  }

  // ── Tier 0 read queries (contacts, apps, calendar — return JSON) ──
  query(name: string, body: Record<string, unknown> = {}) {
    return this.req<unknown>("POST", `/query/${name}`, body);
  }

  // ── voice / overlay / gemini / confirm ──
  speak(text: string) {
    return this.req("POST", "/speak", { text });
  }
  overlayStatus(b: { text: string; state?: string }) {
    return this.req("POST", "/overlay/status", b);
  }
  /** Fire-and-forget hand-off to the Gemini app (no generated result is returned). */
  gemini(b: { prompt: string; want: "image" | "text" | "action"; token: string }) {
    return this.req<{ delegated: boolean }>("POST", "/gemini/delegate", b, 20000);
  }
  /** Blocks until the user approves/denies in the app. Returns a one-time token on approval.
   *  `action` lets the app bind the issued token to this tool (defense-in-depth). */
  confirm(b: { summary: string; details?: string; action: string; params?: Record<string, unknown> }) {
    return this.req<ConfirmResult>("POST", "/confirm", b, 120000);
  }
}
