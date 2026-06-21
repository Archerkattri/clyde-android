import { spawn } from "node:child_process";
import { config } from "./config";
import type { ShellResult } from "./types";

/** Run a command with argv (no shell interpolation). Resolves, never rejects. */
export function runShell(command: string, args: string[], timeoutMs = 15000): Promise<ShellResult> {
  return new Promise((resolveP) => {
    let stdout = "";
    let stderr = "";
    let child;
    try {
      child = spawn(command, args, { shell: false });
    } catch (e) {
      resolveP({ ok: false, code: -1, stdout: "", stderr: String(e) });
      return;
    }
    const timer = setTimeout(() => child.kill("SIGKILL"), timeoutMs);
    child.stdout?.on("data", (d) => (stdout += d.toString()));
    child.stderr?.on("data", (d) => (stderr += d.toString()));
    child.on("error", (e) => {
      clearTimeout(timer);
      resolveP({ ok: false, code: -1, stdout, stderr: stderr || String(e) });
    });
    child.on("close", (code) => {
      clearTimeout(timer);
      resolveP({ ok: code === 0, code: code ?? -1, stdout, stderr });
    });
  });
}

/** Tier 2 — Shizuku shell (uid=shell). */
export const rish = (cmd: string): Promise<ShellResult> => runShell(config.rishCmd, ["-c", cmd], 20000);

/** Tier 3 — root shell. */
export const su = (cmd: string): Promise<ShellResult> => runShell(config.suCmd, ["-c", cmd], 20000);

/** Tier 0 — a termux-* device command, e.g. termux("termux-battery-status"). */
export const termux = (bin: string, args: string[] = [], timeoutMs = 15000): Promise<ShellResult> =>
  runShell(bin, args, timeoutMs);
