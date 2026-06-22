import { createSdkMcpServer, type SdkMcpToolDefinition } from "@anthropic-ai/claude-agent-sdk";
import type { ToolCtx } from "../context";
import { makeMetaTools } from "./meta";
import { makeTier0Termux } from "./tier0-termuxapi";
import { makeTier1Accessibility } from "./tier1-accessibility";
import { makeTier2Shizuku } from "./tier2-shizuku";
import { makeTier3Root } from "./tier3-root";
import { makeGeminiTools } from "./gemini";

export const CLYDE_SERVER_NAME = "clyde";

/** Assemble exactly the tools whose tier is live, per capabilities(). */
export function buildClydeTools(ctx: ToolCtx): SdkMcpToolDefinition<any>[] {
  const tools: SdkMcpToolDefinition<any>[] = [
    ...makeMetaTools(ctx),
    ...makeTier0Termux(ctx),
    ...makeGeminiTools(ctx),
  ];
  if (ctx.caps.tier1) tools.push(...makeTier1Accessibility(ctx));
  if (ctx.caps.tier2) tools.push(...makeTier2Shizuku(ctx));
  if (ctx.caps.tier3) tools.push(...makeTier3Root(ctx));
  return tools;
}

export function buildClydeServer(ctx: ToolCtx) {
  return createSdkMcpServer({
    name: CLYDE_SERVER_NAME,
    version: "0.1.0",
    tools: buildClydeTools(ctx),
  });
}
