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

// ── hard stops ──
check("blocks payment shell", s.hardStop("shell", { cmd: "venmo pay $50" }) !== null);
check("blocks UI-driven payment (tap)", s.hardStop("tap", { nodeId: "Confirm payment" }) !== null);
check("blocks payment via typed text", s.hardStop("type_text", { text: "send money now" }) !== null);
check("blocks self pm_grant", s.hardStop("pm_grant", { pkg: "dev.kris.clyde", permission: "X" }) !== null);
check("self pm_grant w/ userAsked STILL blocked", s.hardStop("pm_grant", { pkg: "dev.kris.clyde", permission: "X", userAsked: true }) !== null);
check("allows normal send_sms", s.hardStop("send_sms", { to: "mom", body: "hi" }) === null);
check("allows normal tap", s.hardStop("tap", { x: 500, y: 1000 }) === null);

// ── halt / kill switch ──
s.setHalted(true);
check("halt blocks token consume", s.consumeToken("any", "send_sms", h).ok === false);
s.setHalted(false);
s.registerToken("tokK", "send_sms", h);
s.invalidateAll();
check("invalidateAll clears tokens", s.consumeToken("tokK", "send_sms", h).ok === false);

if (failed > 0) {
  console.error(`\n${failed} test(s) FAILED`);
  process.exit(1);
} else {
  console.log("\n✓ All safety tests passed.");
}
