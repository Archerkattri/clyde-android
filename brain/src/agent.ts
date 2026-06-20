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

// Long-lived singletons shared across queries.
const app = new AppClient();
const safety = new Safety();
const sessionMap = new Map<string, string>(); // appSessionId -> SDK session_id
let activeQuery: ReturnType<typeof query> | null = null;
let haltCurrent: (() => void) | null = null; // halts the CURRENT turn only (per-turn, not global)

export function getSafety(): Safety {
  return safety;
}

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
}

export async function runAgent(args: RunArgs, emit: Emit): Promise<void> {
  emit({ type: "status", text: "thinking…" });

  const caps = await probeCapabilities(app);
  const ctx: ToolCtx = { app, safety, caps, emit, rish, su };
  const server = buildClydeServer(ctx);
  const prior = sessionMap.get(args.sessionId);

  // Per-turn halt: a kill flips THIS turn's flag; a later turn has its own fresh flag,
  // so resuming never un-kills a still-running prior turn.
  let halted = false;
  haltCurrent = () => { halted = true; };

  let finalText = "";
  const q = query({
    prompt: args.text,
    options: {
      systemPrompt: SYSTEM_PROMPT,
      model: config.model,
      mcpServers: { [CLYDE_SERVER_NAME]: server },
      permissionMode: "default",
      includePartialMessages: true,
      settingSources: [],
      maxTurns: 24,
      // Central enforcement — runs before EVERY tool call (safe and consequential).
      canUseTool: async (toolName, input) => {
        const t = toolName.replace(/^mcp__clyde__/, "");
        const a = (input ?? {}) as Record<string, unknown>;
        if (halted) return { behavior: "deny", message: "Clyde is stopped." };
        const stop = safety.hardStop(t, a);
        if (stop) return { behavior: "deny", message: stop };
        if (safety.isConsequential(t)) {
          const r = safety.consumeToken(a.token as string | undefined, t, argsHash(a));
          if (!r.ok) return { behavior: "deny", message: r.error ?? "confirmation required" };
        }
        return { behavior: "allow" };
      },
      ...(prior ? { resume: prior } : {}),
    },
  });
  activeQuery = q;

  try {
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
      }
    }
  } catch (e) {
    emit({ type: "error", text: `agent error: ${String(e)}` });
    return;
  } finally {
    activeQuery = null;
    haltCurrent = null;
  }

  emit({ type: "final", text: finalText || "Done." });
}
