// Shared data types for the brain. Keep dependency-light (no SDK imports here).

/** Live control tiers + permission/feature snapshot. */
export interface Capabilities {
  tier0: boolean; // always true (intents + termux-api)
  tier1: boolean; // accessibility service enabled
  tier2: boolean; // Shizuku / rish reachable (uid=shell)
  tier3: boolean; // root / su reachable
  accessibility: boolean;
  shizuku: boolean;
  root: boolean;
  overlay: boolean;
  perms: Record<string, boolean>; // sms, call, location, calendar, mic, ...
  defaultApps: Record<string, string>; // preferred app per category: {music, video, maps, browser, ...}
}

/** Raw shape the app's GET /caps returns. */
export interface AppCaps {
  accessibility?: boolean;
  shizuku?: boolean;
  root?: boolean;
  overlay?: boolean;
  perms?: Record<string, boolean>;
  defaultApps?: Record<string, string>;
}

/** Standard envelope every LocalControlServer endpoint returns. */
export interface AppResult<T = unknown> {
  ok: boolean;
  result?: T;
  error?: string;
}

export interface ConfirmResult {
  approved: boolean;
  token?: string;
}

/** Result of asking the user one multiple-choice question (answered by voice or tap, or dismissed). */
export interface AskResult {
  index: number; // chosen option index (0-based), or -1 if cancelled
  label: string; // the chosen option's label
  text: string; // the user's verbatim words (voice) or the tapped label
  via: string; // "voice" | "tap" | "dismiss" | "timeout"
  cancelled: boolean;
}

/** Result of running a shell command (rish / su / termux-*). */
export interface ShellResult {
  ok: boolean;
  code: number;
  stdout: string;
  stderr: string;
}

/** NDJSON events streamed back to the app over /query. */
export type AgentEvent =
  | { type: "status"; text: string }
  | { type: "delta"; text: string } // token-by-token assistant text (optional, for streaming TTS)
  | { type: "action"; tool: string; summary: string }
  | { type: "need_confirm"; summary: string; details?: string }
  | { type: "suggestions"; items: string[] } // tappable follow-up chips shown with the answer
  | { type: "final"; text: string }
  | { type: "error"; text: string; detail?: string }; // detail = short real reason, for diagnostics

export type Emit = (ev: AgentEvent) => void;
