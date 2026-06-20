// Model B — one-shot CLI entry. `tsx src/oneshot.ts "<query>"` runs one turn and prints
// the final answer to stdout. Used by the app's TermuxRunCommand fallback (Phase 1).
import { assertSubscriptionAuth } from "./config";
import { runAgent } from "./agent";
import type { AgentEvent } from "./types";

assertSubscriptionAuth();

const text = process.argv.slice(2).join(" ").trim();
if (!text) {
  console.error('usage: tsx src/oneshot.ts "<query>"');
  process.exit(1);
}

let finalText = "";
const emit = (ev: AgentEvent) => {
  if (ev.type === "final") finalText = ev.text;
  else if (ev.type === "action") process.stderr.write(`· ${ev.summary}\n`);
  else if (ev.type === "status") process.stderr.write(`· ${ev.text}\n`);
  else if (ev.type === "error") process.stderr.write(`! ${ev.text}\n`);
};

await runAgent({ text, sessionId: "oneshot" }, emit);
console.log(finalText);
