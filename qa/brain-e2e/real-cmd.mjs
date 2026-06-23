// POST one natural-language command to the REAL brain (which drives the REAL emulator app over the
// adb bridge) and print the tools it invoked + its final answer. Usage: node real-cmd.mjs "open settings"
const BRAIN = "http://127.0.0.1:8765";
const KEY = process.env.CLYDE_KEY ?? "clyde-e2e-test-key-001";
const text = process.argv.slice(2).join(" ");
const ctrl = new AbortController();
const timer = setTimeout(() => ctrl.abort(), 150000);
try {
  const res = await fetch(BRAIN + "/query", {
    method: "POST",
    headers: { "content-type": "application/json", "x-clyde-key": KEY },
    body: JSON.stringify({ text, sessionId: "real-" + (Date.now() % 100000), model: "sonnet" }),
    signal: ctrl.signal,
  });
  if (!res.ok) { console.log("HTTP", res.status); process.exit(0); }
  const reader = res.body.getReader(); const dec = new TextDecoder(); let buf = "";
  const events = [];
  for (;;) {
    const { done, value } = await reader.read(); if (done) break;
    buf += dec.decode(value, { stream: true }); let nl;
    while ((nl = buf.indexOf("\n")) >= 0) { const line = buf.slice(0, nl).trim(); buf = buf.slice(nl + 1); if (line) { try { events.push(JSON.parse(line)); } catch {} } }
  }
  console.log("CMD:", text);
  for (const e of events) {
    if (e.type === "action") console.log("  action:", e.tool, "—", e.summary ?? "");
    else if (e.type === "final") console.log("  FINAL:", (e.text ?? "").replace(/\n/g, " "));
    else if (e.type === "error") console.log("  ERROR:", e.detail ?? e.text);
  }
} catch (e) { console.log("FAILED:", String(e?.name === "AbortError" ? "timeout(150s)" : e)); }
finally { clearTimeout(timer); }
