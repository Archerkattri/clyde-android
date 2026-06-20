// Tests for the brain's safety-critical logic (action-bound confirm tokens). `npm test`.
import { Safety, argsHash } from "../src/safety";

let failed = 0;
function check(name: string, cond: boolean) {
  console.log(`${cond ? "PASS" : "FAIL"}  ${name}`);
  if (!cond) failed++;
}

const s = new Safety();

// ── classification ──
check("send_sms is consequential", s.isConsequential("send_sms"));
check("su_shell is consequential", s.isConsequential("su_shell"));
check("open_url is consequential", s.isConsequential("open_url"));
check("clipboard_get is consequential", s.isConsequential("clipboard_get"));
check("ui_dump is safe", !s.isConsequential("ui_dump"));
check("tap is safe", !s.isConsequential("tap"));
check("launch_app is safe", !s.isConsequential("launch_app"));

// ── action-bound one-time tokens ──
const h = argsHash({ to: "mom", body: "on my way" });
s.registerToken("tokA", "send_sms", h);
check("valid bound token consumes", s.consumeToken("tokA", "send_sms", h).ok === true);
check("reuse rejected", s.consumeToken("tokA", "send_sms", h).ok === false);

// CRITICAL exploit the audit found: benign approval replayed on a dangerous tool
s.registerToken("tokB", "send_sms", h);
check("token for send_sms cannot run su_shell", s.consumeToken("tokB", "su_shell", argsHash({ cmd: "rm -rf /" })).ok === false);

// replay with changed args
s.registerToken("tokC", "send_sms", h);
check("changed args rejected", s.consumeToken("tokC", "send_sms", argsHash({ to: "mom", body: "DIFFERENT" })).ok === false);

check("unknown token rejected", s.consumeToken("nope", "send_sms", h).ok === false);
check("missing token rejected", s.consumeToken(undefined, "send_sms", h).ok === false);

// ── hard stops (explicit payment phrases on transaction tools; no over-block) ──
check("blocks payment in shell", s.hardStop("shell", { cmd: "open venmo and send money" }) !== null);
check("blocks payment link via open_url", s.hardStop("open_url", { url: "https://app/checkout?pay now" }) !== null);
check("does NOT over-block benign tap (Pay Kim)", s.hardStop("tap", { nodeId: "Pay Kim" }) === null);
check("does NOT over-block normal money text", s.hardStop("send_sms", { to: "mom", body: "can you send money for dinner" }) === null);
check("blocks self pm_grant", s.hardStop("pm_grant", { pkg: "dev.kris.clyde", permission: "X" }) !== null);
check("self pm_grant w/ userAsked STILL blocked", s.hardStop("pm_grant", { pkg: "dev.kris.clyde", permission: "X", userAsked: true }) !== null);
check("allows normal send_sms", s.hardStop("send_sms", { to: "mom", body: "hi" }) === null);

// ── argsHash properties (the binding is only as good as the hash) ──
check("argsHash is key-order independent", argsHash({ to: "mom", body: "hi" }) === argsHash({ body: "hi", to: "mom" }));
check("argsHash ignores the token field", argsHash({ to: "mom", body: "hi" }) === argsHash({ to: "mom", body: "hi", token: "tok_zzz" }));
check("argsHash differs on any value change", argsHash({ to: "mom", body: "hi" }) !== argsHash({ to: "mom", body: "ho" }));
check("argsHash differs when a key is added", argsHash({ to: "mom" }) !== argsHash({ to: "mom", body: "hi" }));
check("argsHash deterministic for nested params", argsHash({ a: { x: 1, y: 2 } }) === argsHash({ a: { x: 1, y: 2 } }));

// ── tool-binding is INDEPENDENT of args-binding, and a failed attempt must NOT burn the token ──
const hUrl = argsHash({ url: "https://example.com" });
s.registerToken("tokU", "open_url", hUrl);
check("same-hash token cannot cross tools", s.consumeToken("tokU", "share_text", hUrl).ok === false);
check("failed cross-tool attempt does not consume the token", s.consumeToken("tokU", "open_url", hUrl).ok === true);

// ── hard stops on cross-vendor egress / sharing tools ──
check("blocks payment via delegate_to_gemini", s.hardStop("delegate_to_gemini", { prompt: "go place an order on amazon" }) !== null);
check("allows benign delegate_to_gemini", s.hardStop("delegate_to_gemini", { prompt: "what is the weather" }) === null);
check("blocks payment via share_text", s.hardStop("share_text", { text: "please wire transfer the deposit" }) !== null);

// ── kill switch ──
s.registerToken("tokK", "send_sms", h);
s.invalidateAll();
check("invalidateAll clears tokens", s.consumeToken("tokK", "send_sms", h).ok === false);

if (failed > 0) {
  console.error(`\n${failed} test(s) FAILED`);
  process.exit(1);
} else {
  console.log("\n✓ All safety tests passed.");
}
