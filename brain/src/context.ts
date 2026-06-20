import type { AppClient } from "./appClient";
import type { Safety } from "./safety";
import type { Capabilities, Emit, ShellResult } from "./types";

/** Everything a tool handler needs. Built fresh per query so `emit` targets that stream. */
export interface ToolCtx {
  app: AppClient;
  safety: Safety;
  caps: Capabilities;
  emit: Emit;
  rish: (cmd: string) => Promise<ShellResult>;
  su: (cmd: string) => Promise<ShellResult>;
}
