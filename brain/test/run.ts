// Tests for the brain's safety-critical logic (action-bound confirm tokens + subscription
// boot guard). `npm test`.
import { Safety, argsHash } from "../src/safety";
import { offSubscriptionReasons } from "../src/config";

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

// ── the token GATE: validate (non-burning) for APP-enforced tools so a recoverable failure can
//    retry; CONSUME (single-use) for brain-local tools so one approval can't authorise re-fires.
//    (Regression the goal-batch verification caught: validate-only had let brain-local tools re-fire.)
check("open_url is app-enforced", s.isAppEnforced("open_url") === true);
check("delegate_to_gemini is app-enforced", s.isAppEnforced("delegate_to_gemini") === true);
check("su_shell is NOT app-enforced (brain must burn it)", s.isAppEnforced("su_shell") === false);
check("mic_record is NOT app-enforced", s.isAppEnforced("mic_record") === false);
const hV = argsHash({ url: "https://ok.com" });
s.registerToken("tokV", "open_url", hV);
check("validateToken accepts a bound token", s.validateToken("tokV", "open_url", hV).ok === true);
check("validateToken does NOT burn — still valid (retry works)", s.validateToken("tokV", "open_url", hV).ok === true);
check("validateToken still rejects a cross-tool token", s.validateToken("tokV", "su_shell", argsHash({ cmd: "x" })).ok === false);
check("validateToken still rejects changed args", s.validateToken("tokV", "open_url", argsHash({ url: "https://evil.com" })).ok === false);
const hC = argsHash({ cmd: "echo hi" });
s.registerToken("tokC2", "su_shell", hC);
check("brain-local su_shell consumes once", s.consumeToken("tokC2", "su_shell", hC).ok === true);
check("brain-local su_shell cannot re-fire on one approval", s.consumeToken("tokC2", "su_shell", hC).ok === false);

// ── hard stops on cross-vendor egress / sharing tools ──
check("blocks payment via delegate_to_gemini", s.hardStop("delegate_to_gemini", { prompt: "go place an order on amazon" }) !== null);
check("allows benign delegate_to_gemini", s.hardStop("delegate_to_gemini", { prompt: "what is the weather" }) === null);
check("blocks payment via share_text", s.hardStop("share_text", { text: "please wire transfer the deposit" }) !== null);

// ── kill switch ──
s.registerToken("tokK", "send_sms", h);
s.invalidateAll();
check("invalidateAll clears tokens", s.consumeToken("tokK", "send_sms", h).ok === false);

// ── subscription-only boot guard: every SDK-honored off-subscription switch is detected ──
const OFF_KEYS = [
  "ANTHROPIC_API_KEY", "ANTHROPIC_AUTH_TOKEN", "CLAUDE_CODE_OAUTH_TOKEN", "ANTHROPIC_IDENTITY_TOKEN",
  "ANTHROPIC_IDENTITY_TOKEN_FILE", "ANTHROPIC_SERVICE_ACCOUNT_ID", "CLAUDE_CODE_USE_BEDROCK",
  "CLAUDE_CODE_USE_VERTEX", "CLAUDE_CODE_USE_FOUNDRY", "CLAUDE_CODE_USE_MANTLE",
  "CLAUDE_CODE_USE_ANTHROPIC_AWS", "ANTHROPIC_BASE_URL", "CLYDE_ALLOW_BASE_URL",
];
const clearOff = () => { for (const k of OFF_KEYS) delete process.env[k]; };

clearOff();
check("clean env → on subscription", offSubscriptionReasons().length === 0);
clearOff(); process.env.ANTHROPIC_API_KEY = "sk-xxx";
check("API key → off subscription", offSubscriptionReasons().includes("ANTHROPIC_API_KEY"));
clearOff(); process.env.CLAUDE_CODE_OAUTH_TOKEN = "tok";
check("CLAUDE_CODE_OAUTH_TOKEN is the SUBSCRIPTION token — NOT off-subscription", offSubscriptionReasons().length === 0);
clearOff(); process.env.ANTHROPIC_SERVICE_ACCOUNT_ID = "svc";
check("service account → off subscription", offSubscriptionReasons().includes("ANTHROPIC_SERVICE_ACCOUNT_ID"));
clearOff(); process.env.CLAUDE_CODE_USE_BEDROCK = "1";
check("Bedrock=1 → off subscription", offSubscriptionReasons().includes("CLAUDE_CODE_USE_BEDROCK"));
clearOff(); process.env.CLAUDE_CODE_USE_VERTEX = "true";
check("Vertex=true → off subscription", offSubscriptionReasons().includes("CLAUDE_CODE_USE_VERTEX"));
clearOff(); process.env.CLAUDE_CODE_USE_BEDROCK = "0";
check("Bedrock=0 → NOT off subscription", offSubscriptionReasons().length === 0);
clearOff(); process.env.CLAUDE_CODE_USE_BEDROCK = "false";
check("Bedrock=false → NOT off subscription", offSubscriptionReasons().length === 0);
clearOff(); process.env.ANTHROPIC_BASE_URL = "http://localhost:9000";
check("base URL → off subscription", offSubscriptionReasons().includes("ANTHROPIC_BASE_URL"));
clearOff(); process.env.ANTHROPIC_BASE_URL = "http://localhost:9000"; process.env.CLYDE_ALLOW_BASE_URL = "1";
check("base URL with explicit opt-in → NOT off subscription", offSubscriptionReasons().length === 0);
clearOff(); process.env.ANTHROPIC_API_KEY = "   ";
check("whitespace-only cred treated as unset", offSubscriptionReasons().length === 0);
clearOff();

if (failed > 0) {
  console.error(`\n${failed} test(s) FAILED`);
  process.exit(1);
} else {
  console.log("\n✓ All safety tests passed.");
}
