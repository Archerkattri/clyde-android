// Probe recurring reminders against the real brain + mock: a recurring request should reach
// /reminder/set with a `repeat` cadence in the body. (Firing/reschedule is AlarmManager on-device;
// here we verify the brain→app contract carries the cadence correctly.)
const BRAIN = "http://127.0.0.1:8765", MOCK = "http://127.0.0.1:8766", KEY = process.env.CLYDE_KEY ?? "test-key";
async function calls() { try { return await (await fetch(MOCK + "/__calls")).json(); } catch { return []; } }
async function run(text) {
  await calls();
  const res = await fetch(BRAIN + "/query", { method: "POST", headers: { "content-type": "application/json", "x-clyde-key": KEY }, body: JSON.stringify({ text, sessionId: "rec-" + Math.random().toString(36).slice(2), model: "sonnet" }) });
  const rd = res.body.getReader(), dec = new TextDecoder(); let buf = ""; const evs = [];
  for (;;) { const { done, value } = await rd.read(); if (done) break; buf += dec.decode(value, { stream: true }); let nl; while ((nl = buf.indexOf("\n")) >= 0) { const l = buf.slice(0, nl).trim(); buf = buf.slice(nl + 1); if (l) { try { evs.push(JSON.parse(l)); } catch {} } } }
  const c = await calls();
  const set = c.find(x => x.uri === "/reminder/set");
  const final = evs.find(e => e.type === "final")?.text ?? "";
  console.log(`\n=== ${text} ===`);
  console.log("/reminder/set body:", set ? JSON.stringify(set.body) : "(not called)");
  console.log("repeat cadence:", set?.body?.repeat ?? "(none)");
  console.log("final:", final.slice(0, 180).replace(/\n/g, " "));
}
await run("Every weekday at 8am, remind me to check the standup channel");
await run("Remind me to drink water every hour");
console.log("\n(done)");
