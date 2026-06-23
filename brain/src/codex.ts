import { spawn, type ChildProcess } from "node:child_process";
import { config, codexOffSubscriptionReasons } from "./config";
import type { Emit } from "./types";
import type { RunArgs } from "./agent";

/**
 * Codex backend — drives the OpenAI Codex CLI headless (`codex exec --json`) on the user's ChatGPT
 * SUBSCRIPTION (`codex login`; creds in ~/.codex/auth.json), NEVER a paid API key. Maps Codex's JSONL
 * event stream onto Clyde's AgentEvent contract. Clyde's device-control tools are exposed to Codex as
 * an MCP server (declared in ~/.codex/config.toml) — wired in a follow-up; this is the turn driver.
 *
 * NOTE: the codex binary is native (arm64-musl) and ships in the embedded bootstrap; on a PC without
 * it, this typechecks but won't run. The JSONL schema varies by codex version, so the mapping below is
 * deliberately tolerant (extract assistant text + surface tool calls/errors) and wants on-device tuning.
 */
let activeCodex: ChildProcess | null = null;

export function haltCodex(): void {
  try { activeCodex?.kill("SIGTERM"); } catch { /* already gone */ }
  activeCodex = null;
}

export async function runCodex(args: RunArgs, emit: Emit): Promise<void> {
  const offSub = codexOffSubscriptionReasons();
  if (offSub.length > 0) {
    emit({ type: "error", text: `Codex must run on your ChatGPT subscription — unset ${offSub.join(", ")}.` });
    return;
  }
  const bin = config.codexCliPath ?? "codex";
  const model = args.model ?? config.codexModel;
  const argv = ["exec", "--json", "--skip-git-repo-check"];
  if (model) argv.push("-m", model);
  argv.push(args.text);

  emit({ type: "status", text: "thinking…" });

  return new Promise<void>((resolveP) => {
    let child: ChildProcess;
    try {
      child = spawn(bin, argv, {
        // belt-and-suspenders: blank any API key in the child so codex uses subscription auth only
        env: { ...process.env, OPENAI_API_KEY: "", CODEX_API_KEY: "" },
      });
    } catch {
      emit({ type: "error", text: "Codex isn't available in this runtime yet." });
      resolveP();
      return;
    }
    activeCodex = child;
    let finalText = "";
    let buf = "";

    const handle = (ev: Record<string, unknown>) => {
      const t = String(ev.type ?? "");
      if (t === "thread.started" || t === "turn.started") {
        emit({ type: "status", text: "thinking…" });
      } else if (t === "item.started" || t === "item.completed") {
        const item = (ev.item ?? {}) as Record<string, unknown>;
        const it = String(item.type ?? "");
        if (it.includes("agent_message") || it.includes("assistant")) {
          const txt = item.text ?? item.content ?? "";
          if (typeof txt === "string" && txt.trim()) finalText = txt.trim();
        } else if (it.includes("command") || it.includes("tool") || it.includes("mcp")) {
          const name = String(item.name ?? item.command ?? it);
          emit({ type: "action", tool: name, summary: String(item.summary ?? name) });
        } else if (it.includes("reasoning")) {
          emit({ type: "status", text: "thinking…" });
        }
      } else if (t === "error" || t === "turn.failed") {
        emit({ type: "error", text: String(ev.message ?? ev.error ?? "Codex error") });
      }
    };

    child.stdout?.on("data", (chunk: Buffer) => {
      buf += chunk.toString();
      let nl: number;
      while ((nl = buf.indexOf("\n")) >= 0) {
        const line = buf.slice(0, nl).trim();
        buf = buf.slice(nl + 1);
        if (!line) continue;
        try { handle(JSON.parse(line) as Record<string, unknown>); } catch { /* non-JSON log */ }
      }
    });
    child.stderr?.on("data", () => { /* codex logs to stderr; ignore */ });
    child.on("error", () => { emit({ type: "error", text: "Couldn't launch Codex." }); });
    child.on("close", () => {
      activeCodex = null;
      emit({ type: "final", text: finalText || "Done." });
      resolveP();
    });
  });
}
