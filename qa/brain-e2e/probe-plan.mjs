// Probe plan-preview: a multi-step task should emit a `plan` event (the up-front step list) before the
// actions fire. (The check-off UI is app-side; here we verify the brain announces a plan.)
const BRAIN = "http://127.0.0.1:8765", KEY = process.env.CLYDE_KEY ?? "test-key";
async function run(text) {
  const evs = [];
  const res = await fetch(BRAIN + "/query", { method: "POST", headers: { "content-type": "application/json", "x-clyde-key": KEY }, body: JSON.stringify({ text, sessionId: "plan-" + Math.random().toString(36).slice(2), model: "sonnet" }) });
  const rd = res.body.getReader(), dec = new TextDecoder(); let buf = "";
  for (;;) { const { done, value } = await rd.read(); if (done) break; buf += dec.decode(value, { stream: true }); let nl; while ((nl = buf.indexOf("\n")) >= 0) { const l = buf.slice(0, nl).trim(); buf = buf.slice(nl + 1); if (l) { try { evs.push(JSON.parse(l)); } catch {} } } }
  const plan = evs.find(e => e.type === "plan");
  const actions = evs.filter(e => e.type === "action").map(e => e.tool);
  console.log(`\n=== ${text} ===`);
  console.log("plan event:", plan ? JSON.stringify(plan.steps) : "(none)");
  console.log("actions:", actions.join(", "));
  console.log("final:", (evs.find(e => e.type === "final")?.text ?? "").slice(0, 160).replace(/\n/g, " "));
}
await run("Play despacito in YouTube Music ReVanced");
console.log("\n(done)");
