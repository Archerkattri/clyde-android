import type { ToolCtx } from "../context";

/** A tool result the SDK accepts (CallToolResult — carries an index signature). */
export interface ToolResult {
  content: Array<
    | { type: "text"; text: string }
    | { type: "image"; data: string; mimeType: string }
  >;
  isError?: boolean;
  [key: string]: unknown;
}

export const text = (t: string): ToolResult => ({ content: [{ type: "text", text: t }] });
export const err = (t: string): ToolResult => ({ content: [{ type: "text", text: t }], isError: true });
export const json = (o: unknown): ToolResult => ({ content: [{ type: "text", text: JSON.stringify(o) }] });
export const image = (b64: string, mime = "image/png"): ToolResult => ({
  content: [{ type: "image", data: b64, mimeType: mime }],
});

/**
 * Defense-in-depth guard used inside handlers: refuse while halted and apply hard stops.
 * The one-time, action-bound token check is enforced centrally in agent.ts `canUseTool`
 * (which runs before any tool), so handlers never consume tokens themselves.
 */
export function gate(ctx: ToolCtx, tool: string, args: Record<string, unknown>): ToolResult | null {
  const stop = ctx.safety.hardStop(tool, args);
  if (stop) return err(stop);
  return null;
}
