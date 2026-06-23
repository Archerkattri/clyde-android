// Mock of the Android app's LocalControlServer (127.0.0.1:8766) for testing the REAL brain on a PC
// (the arm64 embedded brain can't run on an x86_64 emulator, so we exercise the brain's logic against
// a simulated device). It implements every endpoint the brain calls, LOGS each one, and returns
// plausible data so multi-step flows proceed. Auto-approves confirms so consequential paths run.
import { createServer } from "node:http";

const KEY = process.env.CLYDE_KEY ?? "test-key";
const PORT = Number(process.env.APP_PORT ?? 8766);

const calls = []; // every request the brain made, in order
const reminders = []; // in-memory so set→list→cancel works

// A realistic installed-app inventory so app-picking (and "ReVanced" disambiguation) is exercised.
const APPS = [
  { label: "YouTube Music ReVanced", package: "app.revanced.android.apps.youtube.music" },
  { label: "YouTube ReVanced", package: "app.revanced.android.youtube" },
  { label: "ReVanced Manager", package: "app.revanced.manager.flutter" },
  { label: "Spotify", package: "com.spotify.music" },
  { label: "WhatsApp", package: "com.whatsapp" },
  { label: "Google Maps", package: "com.google.android.apps.maps" },
  { label: "Gmail", package: "com.google.android.gm" },
  { label: "Chrome", package: "com.android.chrome" },
  { label: "Clock", package: "com.google.android.deskclock" },
  { label: "Settings", package: "com.android.settings" },
  { label: "Phone", package: "com.google.android.dialer" },
  { label: "Camera", package: "com.android.camera2" },
];

// A STATEFUL fake screen so the play-a-song flow can actually converge: search box → (type) → results
// → (tap a result) → playing. This lets us verify the brain drives the UI to completion instead of
// looping on an unchanging screen.
const PKG = "app.revanced.android.apps.youtube.music";
let screen = "home"; // home | searched | playing
let query = "";
function dumpNodes() {
  if (screen === "searched")
    return { nodes: [
      { nodeId: "r0", role: "android.widget.TextView", text: `${query} — top result`, desc: "play", bounds: [40, 300, 1040, 380], clickable: true, editable: false, scrollable: false, focused: false, package: PKG },
      { nodeId: "r1", role: "android.widget.TextView", text: `${query} (live)`, desc: "", bounds: [40, 400, 1040, 480], clickable: true, editable: false, scrollable: false, focused: false, package: PKG },
    ] };
  if (screen === "playing")
    return { nodes: [
      { nodeId: "p0", role: "android.widget.Button", text: "Pause", desc: "Pause", bounds: [480, 1800, 600, 1920], clickable: true, editable: false, scrollable: false, focused: false, package: PKG },
      { nodeId: "p1", role: "android.widget.TextView", text: query, desc: "Now playing", bounds: [40, 1600, 1040, 1680], clickable: false, editable: false, scrollable: false, focused: false, package: PKG },
    ] };
  return { nodes: [
    { nodeId: "n0", role: "android.widget.EditText", text: "", desc: "Search", bounds: [40, 120, 1000, 200], clickable: true, editable: true, scrollable: false, focused: false, package: PKG },
    { nodeId: "n1", role: "android.widget.TextView", text: "Home", desc: "", bounds: [40, 300, 500, 360], clickable: true, editable: false, scrollable: false, focused: false, package: PKG },
  ] };
}

// 1x1 transparent PNG (base64) so screenshot returns a valid image payload.
const PNG_1x1 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

const ok = (result = {}) => ({ ok: true, result });
const err = (e) => ({ ok: false, error: e });

function handle(uri, body) {
  if (uri === "/caps")
    // Tier0 + Tier1 live (a11y goes through us, so it's testable). Tier2/3 are shell-on-device and
    // can't run on a PC, so leave them off to keep the tool set honest.
    return ok({
      accessibility: true, shizuku: false, root: false, overlay: true,
      perms: { contacts: true, calendar: true, sms: true, phone: true, location: true, notifications: true },
      defaultApps: { music: "app.revanced.android.apps.youtube.music" },
    });

  if (uri === "/a11y/dump") return ok(dumpNodes());
  if (uri === "/a11y/type") { if (String(body.text ?? "").trim()) { query = body.text; screen = "searched"; } return ok({}); }
  if (uri === "/a11y/tap") { if (String(body.nodeId ?? "").startsWith("r")) screen = "playing"; return ok({}); }
  if (uri === "/a11y/swipe" || uri === "/a11y/global") return ok({});
  if (uri === "/a11y/screenshot") return ok({ pngBase64: PNG_1x1 });

  if (uri.startsWith("/intent/")) {
    const name = uri.slice("/intent/".length);
    // play_media "opens the app but often doesn't play" (the honest behavior) → land on home, not playing.
    if (name === "play_media") { screen = "home"; if (body.query) query = body.query; }
    if (name === "launch_app") screen = "home";
    return ok({ fired: name });
  }

  if (uri.startsWith("/query/")) {
    const name = uri.slice("/query/".length);
    if (name === "list_apps") {
      const f = String(body.filter ?? "").toLowerCase();
      return ok({ apps: f ? APPS.filter((a) => a.label.toLowerCase().includes(f)) : APPS });
    }
    if (name === "find_contact") return ok({ contacts: [{ name: "Mom", number: "+1 555 0101" }, { name: "Sarah Lee", number: "+1 555 0144" }] });
    if (name === "list_calendar_events") return ok({ events: [{ title: "Dentist", startMillis: Date.now() + 3600_000, location: "Clinic" }] });
    if (name === "read_notifications") return ok({ notifications: [{ app: "com.whatsapp", title: "Mom", text: "call me" }] });
    if (name === "now_playing") {
      if (screen === "playing") return ok({ playing: true, app: PKG, title: query, artist: "", album: "" });
      return ok({ playing: true, app: "com.spotify.music", title: "Let Down", artist: "Radiohead", album: "OK Computer" });
    }
    return ok({});
  }

  if (uri === "/reminder/set") {
    const id = "r" + (reminders.length + 1);
    reminders.push({ id, text: body.text, fireAt: body.fireAt, action: body.action ?? "" });
    return ok({ id, text: body.text, fireAt: body.fireAt, action: body.action ?? "" });
  }
  if (uri === "/reminder/list") return ok({ reminders: [...reminders].sort((a, b) => a.fireAt - b.fireAt) });
  if (uri === "/reminder/cancel") {
    const i = reminders.findIndex((r) => r.id === body.id);
    if (i >= 0) reminders.splice(i, 1);
    return ok({ cancelled: i >= 0 });
  }

  if (uri === "/prefs/set_default_app") return ok({ category: body.category, package: body.package });
  if (uri === "/speak") return ok({});
  if (uri === "/overlay/status") return ok({});
  if (uri === "/confirm") return ok({ approved: true, token: "tok_mock_" + Math.floor(Math.random() * 1e9) });
  if (uri === "/gemini/delegate") return ok({ delegated: true });
  if (uri === "/ping") return ok({ pong: true });
  return err("not found: " + uri);
}

const server = createServer((req, res) => {
  const uri = (req.url ?? "").split("?")[0];

  // Side-channel for the driver: GET /__calls returns + clears the call log since the last fetch.
  // No key (loopback test only) so the driver stays simple.
  if (req.method === "GET" && uri === "/__calls") {
    const taken = calls.splice(0);
    screen = "home"; query = ""; // fresh screen per command
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify(taken));
    return;
  }

  if (req.headers["x-clyde-key"] !== KEY) {
    res.writeHead(401, { "content-type": "application/json" });
    res.end(JSON.stringify(err("bad key")));
    return;
  }
  let raw = "";
  req.on("data", (c) => (raw += c));
  req.on("end", () => {
    let body = {};
    try { body = raw ? JSON.parse(raw) : {}; } catch { /* ignore */ }
    let result;
    try { result = handle(uri, body); } catch (e) { result = err(String(e)); }
    // Log everything except the chatty status/speak/caps so the action trace stays readable.
    if (uri !== "/overlay/status" && uri !== "/speak" && uri !== "/caps") calls.push({ uri, body });
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify(result));
  });
});

server.listen(PORT, "127.0.0.1", () => console.error(`[mock-app] listening on 127.0.0.1:${PORT}`));
