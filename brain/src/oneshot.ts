// One-shot CLI entry: `tsx src/oneshot.ts "<query>"` runs a single agent turn and prints the
// final answer to stdout. Used as the non-streaming fallback when launched via Termux.
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
