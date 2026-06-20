import { query } from "@anthropic-ai/claude-agent-sdk";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { config } from "./config";
import { AppClient } from "./appClient";
import { Safety } from "./safety";
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

/** For the kill switch. */
export function getSafety(): Safety {
  return safety;
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

  let finalText = "";
  try {
    for await (const msg of query({
      prompt: args.text,
      options: {
        systemPrompt: SYSTEM_PROMPT,
        model: config.model,
        mcpServers: { [CLYDE_SERVER_NAME]: server },
        allowedTools: [`mcp__${CLYDE_SERVER_NAME}__*`],
        permissionMode: "default",
        includePartialMessages: true,
        settingSources: [],
        maxTurns: 24,
        ...(prior ? { resume: prior } : {}),
      },
    })) {
      const m = msg as unknown as Record<string, unknown>;

      // capture the SDK session id for multi-turn continuity
      if (msg.type === "system" && (m.subtype as string) === "init") {
        const sid = m.session_id as string | undefined;
        if (sid) sessionMap.set(args.sessionId, sid);
      }

      // token-by-token assistant text (for streaming TTS)
      if (msg.type === "stream_event") {
        const ev = m.event as { type?: string; delta?: { type?: string; text?: string } } | undefined;
        if (ev?.type === "content_block_delta" && ev.delta?.type === "text_delta" && ev.delta.text) {
          emit({ type: "delta", text: ev.delta.text });
        }
      }

      // tool calls → action events for the status bubble / log
      if (msg.type === "assistant") {
        const content = (m.message as { content?: unknown[] } | undefined)?.content ?? [];
        for (const block of content as Array<Record<string, unknown>>) {
          if (block.type === "tool_use") {
            const name = String(block.name ?? "");
            emit({ type: "action", tool: stripPrefix(name), summary: summarize(name, block.input as Record<string, unknown>) });
          }
        }
      }

      // final answer
      if (msg.type === "result") {
        finalText = (m.result as string | undefined) ?? finalText;
      }
    }
  } catch (e) {
    emit({ type: "error", text: `agent error: ${String(e)}` });
    return;
  }

  emit({ type: "final", text: finalText || "Done." });
}

function stripPrefix(name: string): string {
  return name.replace(/^mcp__clyde__/, "");
}

function summarize(rawName: string, input: Record<string, unknown> = {}): string {
  const t = stripPrefix(rawName);
  const s = (k: string) => String(input?.[k] ?? "");
  switch (t) {
    case "send_sms": return `Text ${s("to")}: "${s("body")}"`;
    case "start_call": return `Call ${s("contact") || s("number")}`;
    case "launch_app": return `Open ${s("query") || s("package")}`;
    case "set_timer": return `Timer ${s("seconds")}s`;
    case "set_alarm": return `Alarm ${s("hour")}:${s("minutes").padStart(2, "0")}`;
    case "navigate_to": return `Navigate to ${s("destination")}`;
    case "open_url": return `Open ${s("url")}`;
    case "tap": return input.nodeId ? `Tap ${s("nodeId")}` : `Tap ${s("x")},${s("y")}`;
    case "type_text": return `Type "${s("text")}"`;
    case "shell": case "su_shell": return `Run: ${s("cmd")}`;
    case "confirm": return `Confirm: ${s("summary")}`;
    case "delegate_to_gemini": return `Ask Gemini: ${s("prompt")}`;
    default: return t.replace(/_/g, " ");
  }
}
