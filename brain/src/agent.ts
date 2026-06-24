import { query } from "@anthropic-ai/claude-agent-sdk";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { config } from "./config";
import { AppClient } from "./appClient";
import { Safety, argsHash } from "./safety";
import { describeAction } from "./describe";
import { probeCapabilities } from "./capabilities";
import { buildClydeServer, CLYDE_SERVER_NAME } from "./tools/index";
import { rish, su } from "./shell";
import type { ToolCtx } from "./context";
import type { Emit } from "./types";

const SYSTEM_PROMPT = readFileSync(resolve(process.cwd(), "system-prompt.md"), "utf8");

const MODEL_RANK: Record<string, number> = { haiku: 0, sonnet: 1, opus: 2 };
const RANK_MODEL = ["haiku", "sonnet", "opus"] as const;

/** Smart model routing. The user's chosen model is the CEILING (used for anything that might drive an
 *  app's UI or needs real reasoning). Only clearly-trivial turns — quick facts and clean one-shot API
 *  actions that never touch a third-party screen — are downgraded to Haiku to save the user's usage
 *  limits. Deliberately conservative: a short command like "play <song>" often means hard multi-step
 *  automation, so when in doubt we keep the ceiling rather than risk a weaker model on real work. */
export function pickModel(text: string, ceiling: string): string {
  const cap = MODEL_RANK[ceiling] ?? MODEL_RANK.sonnet;
  const t = text.toLowerCase().trim();
  const words = t.split(/\s+/).filter(Boolean).length;
  const trivial =
    words <= 12 &&
    /^(what('?s| is)?( the)? (time|date|day|battery|weather)|what time|what day|how much battery|set (a |an )?(timer|alarm)\b|remind me\b|cancel (my |the )?reminder|list (my )?reminders|what('?s| is) playing|flashlight|torch|turn (the )?(flashlight|torch)|define |how do you spell |convert |calculate |what('?s| is) \d|how many )/.test(t);
  const rank = trivial ? Math.min(MODEL_RANK.haiku, cap) : cap;
  return RANK_MODEL[rank];
}

/** Pull an optional `[[followups]] a | b | c` trailer the model may append to its final reply. Returns
 *  the answer with that line removed (so it's never spoken) plus up to 3 follow-up suggestions. */
export function extractFollowups(text: string): { clean: string; followups: string[] } {
  let followups: string[] = [];
  const kept: string[] = [];
  for (const line of text.split(/\r?\n/)) {
    const m = line.match(/\[\[followups\]\]\s*(.*)$/i);
    if (m) followups = m[1].split("|").map((s) => s.trim()).filter(Boolean).slice(0, 3);
    else kept.push(line);
  }
  return { clean: kept.join("\n").trim(), followups };
}

// Long-lived singletons shared across queries.
const app = new AppClient();
const safety = new Safety();
const sessionMap = new Map<string, string>(); // appSessionId -> SDK session_id
const noResume = new Set<string>(); // app sessions whose SDK resume failed once → stop trying to resume
let activeQuery: ReturnType<typeof query> | null = null;
let haltCurrent: (() => void) | null = null; // halts the CURRENT turn only (per-turn, not global)

/** Kill switch: drop tokens, halt the in-flight turn, and interrupt it (best-effort). */
export function haltActiveTurn(): void {
  safety.invalidateAll();
  haltCurrent?.();
  // interrupt() may reject if the control transport is closed (string-prompt turns) — swallow it.
  activeQuery?.interrupt()?.catch(() => {});
}

export interface RunArgs {
  text: string;
  sessionId: string;
  model?: string; // per-query model override (opus|sonnet|haiku); falls back to config.model
}

export async function runAgent(args: RunArgs, emit: Emit): Promise<void> {
  emit({ type: "status", text: "Thinking…" });

  const caps = await probeCapabilities(app);
  const ctx: ToolCtx = { app, safety, caps, emit, rish, su };
  const server = buildClydeServer(ctx);

  // Per-turn halt: a kill flips THIS turn's flag; a later turn has its own fresh flag,
  // so resuming never un-kills a still-running prior turn.
  let halted = false;
  haltCurrent = () => { halted = true; };

  // The agent has no inherent clock; give it the device's current local time so it can resolve
  // "5pm" / "tonight" / "in 2 hours" / "tomorrow 9am" into absolute times (reminders especially).
  const now = new Date();
  const systemPrompt =
    SYSTEM_PROMPT +
    `\n\n# Current time\nIt is now ${now.toString()} (epoch ms ${now.getTime()}). Use this for ALL time math.`;

  // Smart routing: keep the user's model as the ceiling for real work; downgrade only trivial turns.
  const ceiling = args.model ?? config.model ?? "sonnet";
  const effectiveModel = pickModel(args.text, ceiling);
  if (effectiveModel !== ceiling) console.error(`[clyde] routing this turn to ${effectiveModel} (ceiling ${ceiling})`);

  // One query attempt. `resumeId` continues a prior conversation; undefined starts fresh.
  const runOnce = async (resumeId: string | undefined): Promise<{ finalText: string; resultSubtype: string }> => {
    let finalText = "";
    let resultSubtype = "";
    const q = query({
      prompt: args.text,
      options: {
        systemPrompt,
        model: effectiveModel, // user's model is the ceiling; trivial turns auto-routed cheaper

        mcpServers: { [CLYDE_SERVER_NAME]: server },
        // The bundled CLI denies in-process MCP tools by default (it never routes them through our
        // canUseTool). Explicitly allow the Clyde server's tools so they actually run; safety is still
        // enforced — canUseTool below gates consequential tools, and the app independently validates
        // every consequential intent's confirm-token before firing it.
        allowedTools: [`mcp__${CLYDE_SERVER_NAME}`],
        permissionMode: "default",
        includePartialMessages: true,
        settingSources: [],
        maxTurns: 24,
        // Point the SDK at the JS claude-code CLI explicitly when CLAUDE_CLI_PATH is set (the
        // embedded/bundled runtime needs this; on a normal Termux install the CLI is on PATH so we
        // leave the SDK default). The native CLI has no Android build, so the JS release is required.
        // ALSO override `executable`: the SDK spawns the CLI as `node <cli.js>`, but it spawns the bare
        // string "node" from PATH — which doesn't exist in the embedded runtime (our node binary is
        // libnode.so in nativeLibraryDir, and exec'ing node from app storage is W^X-blocked anyway). So
        // the launch failed ("executable exists but failed to launch"). process.execPath is the exact
        // exec-allowed node the brain itself runs under, so spawning it + cli.js works on-device.
        ...(config.claudeCliPath
          ? { pathToClaudeCodeExecutable: config.claudeCliPath, executable: process.execPath as "node" }
          : {}),
        // Central enforcement — runs before EVERY tool call (safe and consequential).
        canUseTool: async (toolName, input) => {
          const t = toolName.replace(/^mcp__clyde__/, "");
          const a = (input ?? {}) as Record<string, unknown>;
          if (halted) return { behavior: "deny", message: "Clyde is stopped." };
          const stop = safety.hardStop(t, a);
          if (stop) return { behavior: "deny", message: stop };
          if (safety.isConsequential(t)) {
            // App-enforced tools: only VALIDATE here (don't burn) — the app burns the token on a
            // successful fire and preserves it on a recoverable failure, so the model can retry.
            // Every other consequential tool runs in-process in the brain with no app burn, so the
            // brain must CONSUME (single-use) here or one approval would authorise unlimited re-fires.
            const r = safety.isAppEnforced(t)
              ? safety.validateToken(a.token as string | undefined, t, argsHash(a))
              : safety.consumeToken(a.token as string | undefined, t, argsHash(a));
            if (!r.ok) return { behavior: "deny", message: r.error ?? "confirmation required" };
          }
          return { behavior: "allow" };
        },
        ...(resumeId ? { resume: resumeId } : {}),
      },
    });
    activeQuery = q;

    for await (const msg of q) {
      const m = msg as unknown as Record<string, unknown>;

      if (msg.type === "system" && (m.subtype as string) === "init") {
        const sid = m.session_id as string | undefined;
        if (sid) sessionMap.set(args.sessionId, sid);
      }

      if (msg.type === "stream_event") {
        const ev = m.event as { type?: string; delta?: { type?: string; text?: string } } | undefined;
        if (ev?.type === "content_block_delta" && ev.delta?.type === "text_delta" && ev.delta.text) {
          emit({ type: "delta", text: ev.delta.text });
        }
      }

      if (msg.type === "assistant") {
        const content = (m.message as { content?: unknown[] } | undefined)?.content ?? [];
        for (const block of content as Array<Record<string, unknown>>) {
          if (block.type === "tool_use") {
            const t = String(block.name ?? "").replace(/^mcp__clyde__/, "");
            emit({ type: "action", tool: t, summary: describeAction(t, block.input as Record<string, unknown>).summary });
          }
        }
      }

      if (msg.type === "result") {
        finalText = (m.result as string | undefined) ?? finalText;
        resultSubtype = (m.subtype as string | undefined) ?? resultSubtype;
      }
    }
    return { finalText, resultSubtype };
  };

  try {
    // Resuming a prior session can fail in the embedded runtime (the SDK can't always replay a
    // transcript that used an in-process MCP server) — which is why the SECOND command used to error
    // with "something went wrong". So: try to resume; if it throws, drop the session and retry as a
    // fresh turn (the command still works, just without prior context), and stop resuming this session.
    const prior = noResume.has(args.sessionId) ? undefined : sessionMap.get(args.sessionId);
    let res: { finalText: string; resultSubtype: string };
    try {
      res = await runOnce(prior);
    } catch (e) {
      if (prior && !halted) {
        console.error("[clyde] resume failed; retrying as a fresh session:", e);
        sessionMap.delete(args.sessionId);
        noResume.add(args.sessionId);
        emit({ type: "status", text: "Thinking…" });
        res = await runOnce(undefined);
      } else {
        throw e;
      }
    }

    // Don't fake a cheerful "Done." when the turn didn't actually finish (hit max turns / errored).
    const final = res.finalText.trim() ||
      (res.resultSubtype.includes("max_turns") ? "That got complicated — I stopped before finishing. Try a smaller step." :
        res.resultSubtype.startsWith("error") ? "Something went wrong while handling that." : "Done.");
    // Split off any "[[followups]] …" trailer so it's never spoken; surface it as tappable chips.
    const { clean, followups } = extractFollowups(final);
    emit({ type: "final", text: clean });
    if (followups.length) emit({ type: "suggestions", items: followups });
  } catch (e) {
    console.error("[clyde] agent error:", e); // full detail to logs
    emit({ type: "error", text: "Something went wrong while handling that.", detail: String((e as Error)?.message ?? e).slice(0, 240) });
  } finally {
    activeQuery = null;
    haltCurrent = null;
  }
}
