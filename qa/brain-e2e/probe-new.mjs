// Probe the NEW brain features against the real brain + mock: (1) follow-up suggestion chips
// (the [[followups]] trailer is stripped from `final` and re-emitted as a `suggestions` event), and
// (2) ask_user (a clarifying question round-trips through the app's /ask endpoint).
const BRAIN = "http://127.0.0.1:8765", MOCK = "http://127.0.0.1:8766", KEY = process.env.CLYDE_KEY ?? "test-key";
async function calls() { try { return await (await fetch(MOCK + "/__calls")).json(); } catch { return []; } }
async function run(text, model = "sonnet") {
  await calls(); // clear
  const evs = [];
  const res = await fetch(BRAIN + "/query", { method: "POST", headers: { "content-type": "application/json", "x-clyde-key": KEY }, body: JSON.stringify({ text, sessionId: "probe-" + Math.random().toString(36).slice(2), model }) });
  const rd = res.body.getReader(), dec = new TextDecoder(); let buf = "";
  for (;;) { const { done, value } = await rd.read(); if (done) break; buf += dec.decode(value, { stream: true }); let nl; while ((nl = buf.indexOf("\n")) >= 0) { const l = buf.slice(0, nl).trim(); buf = buf.slice(nl + 1); if (l) { try { evs.push(JSON.parse(l)); } catch {} } } }
  const c = await calls();
  const deltas = evs.filter(e => e.type === "delta").map(e => e.text).join("");
  const sugg = evs.find(e => e.type === "suggestions");
  const final = evs.find(e => e.type === "final")?.text ?? "";
  const actions = evs.filter(e => e.type === "action").map(e => e.tool);
  console.log(`\n=== ${text} ===`);
  console.log("actions:", actions.join(", ") || "(none)");
  console.log("/ask called:", c.some(x => x.uri === "/ask"));
  console.log("suggestions event:", sugg ? JSON.stringify(sugg.items) : "(none)");
  console.log("final leaks [[followups]]:", final.includes("[[followups]]"));
  console.log("raw delta stream contained the trailer (ok — app strips it):", deltas.includes("[[followups]]"));
  console.log("final:", final.slice(0, 220).replace(/\n/g, " "));
}
await run("What's the capital of France, and one interesting fact about it?"); // expect a clean answer + suggestions
await run("Remind me to do something");                                       // expect ask_user → /ask
await run("I need to send a message to someone");                             // expect ask_user → /ask
console.log("\n(done)");
